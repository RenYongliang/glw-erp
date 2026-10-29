package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;
import com.glw.backend.finance.FinanceModels.StatementTemplate;
import com.glw.backend.finance.statement.DocumentLayoutDefaults;
import com.glw.backend.finance.statement.DocumentSampleData;
import com.glw.backend.finance.statement.DocumentType;
import com.glw.backend.finance.statement.LayoutPdfRenderer;
import com.glw.backend.finance.statement.LayoutStatementRenderer;
import com.glw.backend.finance.statement.StatementFile;
import com.glw.backend.finance.statement.StatementLayoutDetail;
import com.glw.backend.finance.statement.StatementTemplateLayoutInput;
import com.glw.backend.finance.statement.StatementTemplateStorage;
import com.glw.backend.mapper.FinStatementTemplateMapper;
import com.glw.backend.model.entity.FinStatementTemplate;
import com.glw.backend.service.FinanceAuditService;
import com.glw.backend.service.FinanceLockService;
import com.glw.backend.service.FinanceStatementTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 通用文档模板解析与版本管理。模板文件不可变，改版即新增 version。 */
@Service
@Profile("!mock")
@RequiredArgsConstructor
public class FinanceStatementTemplateServiceImpl implements FinanceStatementTemplateService {
    private static final Set<String> FORMATS = Set.of("XLSX", "PDF");
    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;

    private final FinStatementTemplateMapper finStatementTemplateMapper;
    private final StatementTemplateStorage storage;
    private final FinanceLockService lockService;
    private final FinanceAuditService auditService;
    private final LayoutStatementRenderer layoutRenderer;
    private final LayoutPdfRenderer layoutPdfRenderer;
    private final ObjectMapper json;

    @Override
    public List<DocumentType.Option> documentTypes() {
        return DocumentType.options();
    }

    @Override
    public FinStatementTemplate resolve(String direction, Long partnerId, String requestedFormat) {
        String type = DocumentType.STATEMENT.name();
        String dir = DocumentType.STATEMENT.normalizeDirection(direction);
        if (requestedFormat == null || requestedFormat.isBlank())
            return finStatementTemplateMapper.resolveAny(type, dir, partnerId);
        String format = requestedFormat.strip().toUpperCase(Locale.ROOT);
        if ("CSV".equals(format)) return null;
        if (!FORMATS.contains(format)) throw bad("不支持的对账单格式：" + requestedFormat);
        return finStatementTemplateMapper.resolve(type, dir, partnerId, format);
    }

    @Override
    public FinStatementTemplate snapshotFor(String direction, Long partnerId) {
        return finStatementTemplateMapper.resolveAny(DocumentType.STATEMENT.name(),
                DocumentType.STATEMENT.normalizeDirection(direction), partnerId);
    }

    @Override
    public FinStatementTemplate version(Long id, Integer version) {
        if (id == null) return null;
        return version == null ? finStatementTemplateMapper.template(id)
                : finStatementTemplateMapper.templateVersion(id, version);
    }

    @Transactional
    @Override
    public List<StatementTemplate> templates(String rawDocType, String rawDirection, Long partnerId) {
        DocumentType type = DocumentType.of(rawDocType);
        String direction = type.normalizeDirection(rawDirection);
        ensureDefault(type, direction);
        return finStatementTemplateMapper.templates(type.name(), direction, partnerId);
    }

    @Transactional
    @Override
    public void ensureDefault(String rawDocType, String rawDirection) {
        ensureDefault(DocumentType.of(rawDocType),
                DocumentType.of(rawDocType).normalizeDirection(rawDirection));
    }

    private void ensureDefault(DocumentType type, String direction) {
        if (hasGlobal(type, direction)) return;
        lockService.lock();
        if (hasGlobal(type, direction)) return;

        String layoutJson = DocumentLayoutDefaults.layout(type, direction);
        layoutRenderer.validate(layoutJson);
        byte[] sample = renderLayout(layoutJson, "XLSX", type, direction);
        String suffix = type.directional()
                ? ("AR".equals(direction) ? "（应收）" : "（应付）") : "";
        save(type.name(), direction, 0L, "XLSX", "系统默认" + type.label() + suffix,
                true, "系统默认" + type.label() + suffix + ".xlsx", sample, layoutJson, "SEED");
    }

    private boolean hasGlobal(DocumentType type, String direction) {
        Long count = finStatementTemplateMapper.selectCount(new LambdaQueryWrapper<FinStatementTemplate>()
                .eq(FinStatementTemplate::getDocType, type.name())
                .eq(FinStatementTemplate::getDirection, direction)
                .eq(FinStatementTemplate::getPartnerId, 0L)
                .eq(FinStatementTemplate::getDeleted, 0));
        return count != null && count > 0;
    }

    @Transactional
    @Override
    public StatementTemplate saveLayout(StatementTemplateLayoutInput input) {
        DocumentType type = DocumentType.of(input.docType());
        String direction = type.normalizeDirection(input.direction());
        String format = input.format() == null || input.format().isBlank()
                ? "XLSX" : format(input.format());
        String layoutJson = writeJson(input.layout());
        layoutRenderer.validate(layoutJson);
        byte[] preview = renderLayout(layoutJson, format, type, direction);
        return save(type.name(), direction, input.partnerId(), format, input.name(),
                input.isDefault() == null || input.isDefault(), input.name() + extension(format),
                preview, layoutJson, "LAYOUT");
    }

    @Override
    public StatementLayoutDetail layout(Long id) {
        FinStatementTemplate template = finStatementTemplateMapper.template(id);
        if (template == null) throw new BusinessException(ErrorCode.NOT_FOUND, "模板不存在");
        String layoutJson = template.getLayoutJson();
        if (layoutJson == null || layoutJson.isBlank())
            throw bad("该模板不支持可视化编辑");
        try {
            return new StatementLayoutDetail(view(template), json.readTree(layoutJson));
        } catch (RuntimeException e) {
            throw bad("模板布局内容已损坏，请重新保存");
        }
    }

    @Override
    public StatementFile previewLayout(JsonNode layout, String rawFormat,
                                       String rawDocType, String rawDirection) {
        DocumentType type = DocumentType.of(rawDocType);
        String direction = type.normalizeDirection(rawDirection);
        String format = rawFormat == null || rawFormat.isBlank()
                ? "XLSX" : format(rawFormat);
        String layoutJson = writeJson(layout);
        layoutRenderer.validate(layoutJson);
        return new StatementFile(type.label() + "预览" + extension(format), contentType(format),
                renderLayout(layoutJson, format, type, direction));
    }

    private StatementTemplate save(String docType, String direction, Long rawPartnerId, String rawFormat,
                                   String rawName, boolean defaultRequested, String rawFileName,
                                   byte[] content, String layoutJson, String action) {
        lockService.lock();
        DocumentType type = DocumentType.of(docType);
        direction = type.normalizeDirection(direction);
        Long partnerId = rawPartnerId == null ? 0L : rawPartnerId;
        if (partnerId < 0) throw bad("往来单位不正确");
        String format = format(rawFormat);
        if (content == null || content.length == 0) throw bad("请选择模板文件");
        if (content.length > MAX_FILE_BYTES) throw bad("模板文件不能超过 10MB");

        String fileName = rawFileName == null ? "" : rawFileName.strip();
        String name = rawName == null || rawName.isBlank()
                ? stripExtension(fileName) : rawName.strip();
        if (name.isBlank()) name = type.label() + "模板";
        if (name.length() > 128) throw bad("模板名称不能超过 128 个字符");

        String fileKey = storage.store(content, extension(format));
        int version = finStatementTemplateMapper.nextVersion(type.name(), direction, partnerId, format);
        boolean fallbackDefault = finStatementTemplateMapper.resolve(
                type.name(), direction, partnerId, format) == null;
        boolean isDefault = defaultRequested || fallbackDefault;
        if (isDefault)
            finStatementTemplateMapper.clearDefault(type.name(), direction, partnerId, format);

        FinStatementTemplate template = new FinStatementTemplate();
        template.setDocType(type.name());
        template.setDirection(direction);
        template.setPartnerId(partnerId);
        template.setFormat(format);
        template.setName(name);
        template.setVersion(version);
        template.setFileKey(fileKey);
        template.setFileName(fileName.isBlank() ? name + extension(format) : fileName);
        template.setLayoutJson(layoutJson);
        template.setIsDefault(isDefault ? 1 : 0);
        template.setEnabled(1);
        template.setDeleted(0);
        finStatementTemplateMapper.insert(template);
        auditService.record("STATEMENT_TEMPLATE", template.getId(), action,
                type.name() + ":" + direction + ":" + partnerId + ":" + format + ":v" + version);
        FinStatementTemplate saved = finStatementTemplateMapper.template(template.getId());
        return view(saved == null ? template : saved);
    }

    @Transactional
    @Override
    public StatementTemplate setDefault(Long id) {
        lockService.lock();
        FinStatementTemplate template = finStatementTemplateMapper.template(id);
        if (template == null) throw new BusinessException(ErrorCode.NOT_FOUND, "模板不存在");
        finStatementTemplateMapper.clearDefault(docType(template), template.getDirection(),
                template.getPartnerId(), template.getFormat());
        template.setIsDefault(1);
        template.setEnabled(1);
        finStatementTemplateMapper.updateById(template);
        auditService.record("STATEMENT_TEMPLATE", id, "DEFAULT",
                docType(template) + ":" + template.getDirection() + ":" + template.getPartnerId()
                        + ":" + template.getFormat());
        return view(template);
    }

    @Transactional
    @Override
    public StatementTemplate delete(Long id) {
        lockService.lock();
        FinStatementTemplate template = finStatementTemplateMapper.template(id);
        if (template == null) throw new BusinessException(ErrorCode.NOT_FOUND, "模板不存在");
        if (Long.valueOf(0L).equals(template.getPartnerId())
                && Integer.valueOf(1).equals(template.getIsDefault()))
            throw bad("系统默认模板不能删除，请先编辑它或把其他模板设为默认");
        template.setDeleted(1);
        template.setIsDefault(0);
        template.setEnabled(0);
        finStatementTemplateMapper.updateById(template);
        auditService.record("STATEMENT_TEMPLATE", id, "DELETE",
                docType(template) + ":" + template.getDirection() + ":" + template.getPartnerId()
                        + ":" + template.getFormat());
        return view(template);
    }

    private byte[] renderLayout(String layoutJson, String format, DocumentType type, String direction) {
        var scalars = DocumentSampleData.scalars(type, direction);
        var rows = DocumentSampleData.rows(type);
        return "PDF".equals(format)
                ? layoutPdfRenderer.render(scalars, rows, layoutJson)
                : layoutRenderer.render(scalars, rows, layoutJson);
    }

    private static String format(String value) {
        String format = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        if (!FORMATS.contains(format)) throw bad("模板格式必须为 XLSX 或 PDF");
        return format;
    }

    private static String docType(FinStatementTemplate template) {
        return template.getDocType() == null || template.getDocType().isBlank()
                ? DocumentType.STATEMENT.name() : template.getDocType();
    }

    private static String extension(String format) { return "XLSX".equals(format) ? ".xlsx" : ".pdf"; }

    private static String contentType(String format) {
        return "XLSX".equals(format)
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : "application/pdf";
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }

    private static StatementTemplate view(FinStatementTemplate source) {
        StatementTemplate view = new StatementTemplate();
        view.setId(source.getId());
        view.setDocType(docType(source));
        view.setDirection(source.getDirection());
        view.setPartnerId(source.getPartnerId());
        view.setFormat(source.getFormat());
        view.setName(source.getName());
        view.setFileName(source.getFileName());
        view.setVersion(source.getVersion());
        view.setIsDefault(source.getIsDefault());
        view.setEnabled(source.getEnabled());
        view.setHasLayout(source.getLayoutJson() != null && !source.getLayoutJson().isBlank());
        view.setCreatedAt(source.getCreatedAt());
        return view;
    }

    private String writeJson(JsonNode layout) {
        try {
            return json.writeValueAsString(layout);
        } catch (RuntimeException e) {
            throw bad("模板布局格式不正确");
        }
    }

    private static BusinessException bad(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }
}
