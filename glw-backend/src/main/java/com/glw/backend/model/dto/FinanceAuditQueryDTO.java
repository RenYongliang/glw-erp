package com.glw.backend.model.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 财务审计日志查询条件。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FinanceAuditQueryDTO extends PageDTO {
    private String entityType;
    private String action;
    private Long actorId;
    /** 匹配操作摘要或单据 ID。 */
    private String keyword;
    private String startDate;
    private String endDate;

    public long getOffset() {
        Long no = getPageNo();
        Long size = getPageSize();
        long page = no == null || no < 1 ? 1 : no;
        long rows = size == null || size < 1 ? 20 : size;
        return (page - 1) * rows;
    }
}
