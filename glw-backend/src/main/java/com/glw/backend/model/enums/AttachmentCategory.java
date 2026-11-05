package com.glw.backend.model.enums;

import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;

/** 订单附件类型；与前端 domain/attachments.ts 的 key 保持一致。 */
public enum AttachmentCategory {
    SALES_CONTRACT("销售合同", AttachmentBizType.SALES_ORDER, false),
    COUNTERSIGNED_CONTRACT("客户盖章合同", AttachmentBizType.SALES_ORDER, false),
    CUSTOMER_PO("客户采购订单", AttachmentBizType.SALES_ORDER, false),
    DELIVERY_RECEIPT("发货回签", AttachmentBizType.SALES_ORDER, true),
    STATEMENT("对账单", AttachmentBizType.SALES_ORDER, true),
    STATEMENT_SIGNED("对账单回签", AttachmentBizType.SALES_ORDER, true),
    PURCHASE_CONTRACT("采购合同", AttachmentBizType.PURCHASE_ORDER, false),
    SUPPLIER_COUNTERSIGNED("供应商盖章合同", AttachmentBizType.PURCHASE_ORDER, false),
    SUPPLIER_QUOTE("供应商报价单", AttachmentBizType.PURCHASE_ORDER, false),
    RECEIPT_SIGN("收货回签", AttachmentBizType.PURCHASE_ORDER, true),
    INVOICE("发票", AttachmentBizType.PURCHASE_ORDER, true),
    /** 销售、采购共用的自由附件。 */
    OTHER("其他", null, true);

    private final String label;
    private final AttachmentBizType bizType;
    private final boolean multiple;

    AttachmentCategory(String label, AttachmentBizType bizType, boolean multiple) {
        this.label = label;
        this.bizType = bizType;
        this.multiple = multiple;
    }

    public String label() {
        return label;
    }

    /** true 表示同类可以上传多份；false 表示固定只有一份。 */
    public boolean multiple() {
        return multiple;
    }

    public boolean supports(AttachmentBizType type) {
        return bizType == null || bizType == type;
    }

    public static AttachmentCategory of(String code) {
        for (var category : values()) {
            if (category.name().equalsIgnoreCase(code)) return category;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的附件类型：" + code);
    }
}
