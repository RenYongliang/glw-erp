package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.auth.UserContext;
import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;
import com.glw.backend.config.MinioProperties;
import com.glw.backend.mapper.BizAttachmentMapper;
import com.glw.backend.mapper.PurchaseOrderMapper;
import com.glw.backend.mapper.SalesOrderMapper;
import com.glw.backend.model.entity.BizAttachment;
import com.glw.backend.model.enums.AttachmentBizType;
import com.glw.backend.model.enums.AttachmentCategory;
import com.glw.backend.model.vo.AttachmentVO;
import com.glw.backend.service.BizAttachmentService;
import com.glw.backend.storage.AttachmentStorage;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** 业务附件服务实现：对象存 MinIO，元数据存 biz_attachment。 */
@Service
@Profile("!mock")
public class BizAttachmentServiceImpl extends ServiceImpl<BizAttachmentMapper, BizAttachment>
        implements BizAttachmentService {
    private static final DateTimeFormatter NAME_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter OBJECT_MONTH = DateTimeFormatter.ofPattern("yyyy/MM");
    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern ILLEGAL_NAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\s]+");
    private static final Pattern SEQUENCE_SUFFIX = Pattern.compile("_\\d{2}$");
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final SalesOrderMapper salesOrderMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final com.glw.backend.mapper.InboundOrderMapper inboundOrderMapper;
    private final com.glw.backend.mapper.OutboundOrderMapper outboundOrderMapper;
    private final AttachmentStorage storage;
    private final MinioProperties properties;

    public BizAttachmentServiceImpl(
            SalesOrderMapper salesOrderMapper,
            PurchaseOrderMapper purchaseOrderMapper,
            com.glw.backend.mapper.InboundOrderMapper inboundOrderMapper,
            com.glw.backend.mapper.OutboundOrderMapper outboundOrderMapper,
            AttachmentStorage storage,
            MinioProperties properties) {
        this.salesOrderMapper = salesOrderMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.inboundOrderMapper = inboundOrderMapper;
        this.outboundOrderMapper = outboundOrderMapper;
        this.storage = storage;
        this.properties = properties;
    }

    @Override
    public List<AttachmentVO> listAttachments(String bizType, Long bizId) {
        var type = AttachmentBizType.of(bizType);
        return list(
                        Wrappers.<BizAttachment>lambdaQuery()
                                .eq(BizAttachment::getBizType, type.name())
                                .eq(BizAttachment::getBizId, bizId)
                                .eq(BizAttachment::getIsRemoved, false)
                                .orderByAsc(BizAttachment::getId))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional
    public AttachmentVO upload(
            String bizType, Long bizId, String categoryCode, MultipartFile file) {
        var type = AttachmentBizType.of(bizType);
        var category = AttachmentCategory.of(categoryCode);
        if (!category.supports(type)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该附件类型不适用于当前业务单据");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择要上传的文件");
        }
        if (file.getSize() > properties.maxFileSize().toBytes()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "文件不能超过 " + properties.maxFileSize().toMegabytes() + "MB");
        }
        String original = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename());
        String extension = extensionOf(original);
        if (!allowed(extension)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST, "不支持的文件类型：" + (extension.isEmpty() ? "无扩展名" : extension));
        }
        var order = resolveOrder(type, bizId);
        var existing = activeRows(type, bizId, category.name());
        if (!category.multiple() && !existing.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "该附件类型只能上传一份，请先删除原附件");
        }

        String bucket = properties.bucket();
        String objectKey = objectKey(type, order.orderNo(), category, extension);
        String contentType =
                file.getContentType() == null ? DEFAULT_CONTENT_TYPE : file.getContentType();
        try (InputStream stream = file.getInputStream()) {
            storage.put(bucket, objectKey, contentType, file.getSize(), stream);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "附件读取失败，请重试");
        }

        var row = new BizAttachment();
        row.setBizType(type.name());
        row.setBizId(bizId);
        row.setCategory(category.name());
        row.setFileName(provisionalName(order, category, extension));
        row.setBucket(bucket);
        row.setObjectKey(objectKey);
        row.setFileSize(file.getSize());
        row.setContentType(contentType);
        row.setIsRemoved(false);
        row.setCreatedBy(UserContext.userId());
        try {
            save(row);
        } catch (RuntimeException e) {
            storage.remove(bucket, objectKey);
            throw e;
        }
        renumber(activeRows(type, bizId, category.name()));
        return toVO(getById(row.getId()));
    }

    @Override
    @Transactional
    public void deleteAttachment(Long id) {
        var row = getById(id);
        if (row == null || Boolean.TRUE.equals(row.getIsRemoved())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件不存在或已删除");
        }
        row.setIsRemoved(true);
        updateById(row);
        renumber(
                activeRows(
                        AttachmentBizType.of(row.getBizType()),
                        row.getBizId(),
                        row.getCategory()));
    }

    /** 同一类型只有一份时去掉序号，多份时按 _01、_02 重新编号。 */
    private void renumber(List<BizAttachment> rows) {
        int total = rows.size();
        for (int i = 0; i < total; i++) {
            var row = rows.get(i);
            String current = row.getFileName();
            String extension = extensionOf(current);
            String base =
                    SEQUENCE_SUFFIX.matcher(current.substring(0, current.length() - extension.length()))
                            .replaceFirst("");
            String next = base + (total > 1 ? "_" + String.format("%02d", i + 1) : "") + extension;
            if (!next.equals(current)) {
                row.setFileName(next);
                updateById(row);
            }
        }
    }

    private List<BizAttachment> activeRows(
            AttachmentBizType type, Long bizId, String category) {
        return list(
                Wrappers.<BizAttachment>lambdaQuery()
                        .eq(BizAttachment::getBizType, type.name())
                        .eq(BizAttachment::getBizId, bizId)
                        .eq(BizAttachment::getCategory, category)
                        .eq(BizAttachment::getIsRemoved, false)
                        .orderByAsc(BizAttachment::getId));
    }

    private OrderInfo resolveOrder(AttachmentBizType type, Long bizId) {
        if (bizId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少业务单据ID");
        }
        return switch (type) {
            case SALES_ORDER -> {
                var order = salesOrderMapper.selectById(bizId);
                if (order == null) throw new BusinessException(ErrorCode.NOT_FOUND, "销售订单不存在");
                yield new OrderInfo(order.getOrderNo(), order.getCustomerName(), "客户");
            }
            case PURCHASE_ORDER -> {
                var order = purchaseOrderMapper.selectById(bizId);
                if (order == null) throw new BusinessException(ErrorCode.NOT_FOUND, "采购订单不存在");
                yield new OrderInfo(order.getPurchaseOrderNo(), order.getSupplierName(), "供应商");
            }
            case INBOUND_ORDER -> {
                var order = inboundOrderMapper.selectById(bizId);
                if (order == null) throw new BusinessException(ErrorCode.NOT_FOUND, "入库单不存在");
                yield new OrderInfo(order.getInboundOrderNo(), order.getSupplierName(), "供应商");
            }
            case OUTBOUND_ORDER -> {
                var order = outboundOrderMapper.selectById(bizId);
                if (order == null) throw new BusinessException(ErrorCode.NOT_FOUND, "出库单不存在");
                yield new OrderInfo(order.getOutboundOrderNo(), order.getCustomerName(), "客户");
            }
        };
    }

    private String provisionalName(OrderInfo order, AttachmentCategory category, String extension) {
        String name =
                clean(order.orderNo(), "DRAFT")
                        + "_"
                        + clean(category.label(), "附件")
                        + "_"
                        + clean(order.partner(), order.partnerFallback())
                        + "_"
                        + LocalDate.now().format(NAME_DATE)
                        + extension;
        return truncate(name, 200);
    }

    private String objectKey(
            AttachmentBizType type, String orderNo, AttachmentCategory category, String extension) {
        return type.key()
                + "/"
                + YearMonth.now().format(OBJECT_MONTH)
                + "/"
                + clean(orderNo, "DRAFT")
                + "/"
                + category.name()
                + "/"
                + UUID.randomUUID()
                + extension;
    }

    private boolean allowed(String extension) {
        String plain = extension.startsWith(".") ? extension.substring(1) : extension;
        return properties.allowedExtensions().stream()
                .map(item -> item.trim().replaceFirst("^\\.", ""))
                .anyMatch(item -> item.equalsIgnoreCase(plain));
    }

    private AttachmentVO toVO(BizAttachment row) {
        var vo = new AttachmentVO();
        vo.setId(row.getId());
        vo.setBizType(row.getBizType());
        vo.setBizId(row.getBizId());
        vo.setCategory(row.getCategory());
        vo.setCategoryLabel(labelOf(row.getCategory()));
        vo.setFileName(row.getFileName());
        vo.setFileSize(row.getFileSize());
        vo.setContentType(row.getContentType());
        vo.setDownloadUrl(storage.presignedUrl(row.getBucket(), row.getObjectKey()));
        vo.setCreatedAt(
                row.getCreatedAt() == null ? null : row.getCreatedAt().format(DISPLAY_TIME));
        return vo;
    }

    private String labelOf(String category) {
        try {
            return AttachmentCategory.of(category).label();
        } catch (BusinessException e) {
            return category;
        }
    }

    /** 文件名不允许空白和路径分隔符，避免下载后被系统截断。 */
    private static String clean(String value, String fallback) {
        String cleaned = ILLEGAL_NAME_CHARS.matcher(value == null ? "" : value).replaceAll("");
        return cleaned.isBlank() ? fallback : cleaned;
    }

    private static String extensionOf(String name) {
        int index = name == null ? -1 : name.lastIndexOf('.');
        return index > 0 ? name.substring(index).toLowerCase(Locale.ROOT) : "";
    }

    private static String truncate(String name, int max) {
        if (name.length() <= max) return name;
        String extension = extensionOf(name);
        return name.substring(0, max - extension.length()) + extension;
    }

    private record OrderInfo(String orderNo, String partner, String partnerFallback) {}
}
