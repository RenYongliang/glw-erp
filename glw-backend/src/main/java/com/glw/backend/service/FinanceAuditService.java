package com.glw.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.glw.backend.model.dto.FinanceAuditQueryDTO;
import com.glw.backend.model.entity.FinAudit;
import com.glw.backend.model.vo.FinanceAuditVO;


/** Financial audit trail. */
public interface FinanceAuditService extends IService<FinAudit> {
    void record(String type, Long id, String action, String detail);

    IPage<FinanceAuditVO> audits(FinanceAuditQueryDTO query);
}
