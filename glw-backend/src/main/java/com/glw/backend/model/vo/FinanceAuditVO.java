package com.glw.backend.model.vo;

import lombok.Data;

/** 财务审计日志展示对象。 */
@Data
public class FinanceAuditVO {
    private Long id;
    private String entityType;
    private Long entityId;
    private String action;
    private Long actorId;
    private String actorName;
    private String departmentName;
    private String detail;
    private String createdAt;
}
