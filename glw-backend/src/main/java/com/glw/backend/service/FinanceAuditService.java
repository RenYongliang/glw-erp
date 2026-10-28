package com.glw.backend.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.glw.backend.model.entity.FinAudit;


/** Financial audit trail. */
public interface FinanceAuditService extends IService<FinAudit> {
    void record(String type, Long id, String action, String detail);
}
