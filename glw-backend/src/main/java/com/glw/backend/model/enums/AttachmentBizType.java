package com.glw.backend.model.enums;

import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;

/** 附件所属业务类型；新增业务模块时在这里扩展。 */
public enum AttachmentBizType {
    SALES_ORDER,
    PURCHASE_ORDER;

    public static AttachmentBizType of(String value) {
        for (var type : values()) {
            if (type.name().equalsIgnoreCase(value)) return type;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的附件业务类型：" + value);
    }

    /** 对象键和统计使用的小写标识。 */
    public String key() {
        return name().toLowerCase();
    }
}
