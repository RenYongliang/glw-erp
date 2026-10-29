package com.glw.backend.service.impl;

import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;
import com.glw.backend.finance.FinanceModels.Statement;
import com.glw.backend.finance.FinanceModels.StatementDetail;
import com.glw.backend.finance.statement.LayoutPdfRenderer;
import com.glw.backend.finance.statement.LayoutStatementRenderer;
import com.glw.backend.finance.statement.StatementFile;
import com.glw.backend.finance.statement.StatementRenderModel;
import com.glw.backend.finance.statement.StatementRenderModelAssembler;
import com.glw.backend.finance.statement.StatementRenderer;
import com.glw.backend.model.entity.FinStatementTemplate;
import com.glw.backend.service.FinanceStatementExportService;
import com.glw.backend.service.FinanceStatementService;
import com.glw.backend.service.FinanceStatementTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/** 导出时优先使用出单时锁定的模板版本，模板改版后旧对账单仍按原版式重导。 */
@Service
@Profile("!mock")
@RequiredArgsConstructor
public class FinanceStatementExportServiceImpl implements FinanceStatementExportService {
    private final FinanceStatementService financeStatementService;
    private final FinanceStatementTemplateService templateService;
    private final StatementRenderModelAssembler assembler;
    private final LayoutStatementRenderer layoutRenderer;
    private final LayoutPdfRenderer layoutPdfRenderer;
    private final List<StatementRenderer> renderers;

    @Override
    public StatementFile export(Long statementId, String format) {
        StatementDetail detail = financeStatementService.statement(statementId);
        Statement statement = detail.statement();
        FinStatementTemplate template = select(statement, format);
        StatementRenderModel model = assembler.assemble(statement, detail.items());
        if (template != null && template.getLayoutJson() != null && !template.getLayoutJson().isBlank()) {
            if ("PDF".equals(template.getFormat()))
                return new StatementFile(statement.getStatementNo() + ".pdf", "application/pdf",
                        layoutPdfRenderer.render(model, template.getLayoutJson()));
            return new StatementFile(statement.getStatementNo() + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    layoutRenderer.render(model, template.getLayoutJson()));
        }
        String effective = template == null ? "CSV" : template.getFormat();
        StatementRenderer renderer = renderers.stream()
                .filter(r -> r.format().equals(effective)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                        "上传的 " + effective + " 模板无法自动填入数据，请用可视化设计器创建模板"));
        byte[] content = renderer.render(model, template);
        return new StatementFile(statement.getStatementNo() + renderer.extension(),
                renderer.contentType(), content);
    }

    private FinStatementTemplate select(Statement statement, String format) {
        boolean explicit = format != null && !format.isBlank();
        String requested = explicit ? format.strip().toUpperCase(Locale.ROOT) : null;
        FinStatementTemplate snapshot = templateService.version(statement.getTemplateId(), statement.getTemplateVersion());
        if (!explicit) {
            if (snapshot != null) return snapshot;
            return templateService.resolve(statement.getDirection(), statement.getPartnerId(), null);
        }
        if ("CSV".equals(requested)) return null;
        if (snapshot != null && snapshot.getFormat().equals(requested)) return snapshot;
        FinStatementTemplate current = templateService.resolve(statement.getDirection(), statement.getPartnerId(), requested);
        if (current == null)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该往来单位未配置 " + requested + " 对账单模板");
        return current;
    }
}
