package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.auth.UserContext;

import com.glw.backend.mapper.FinAuditMapper;
import com.glw.backend.model.dto.FinanceAuditQueryDTO;
import com.glw.backend.model.entity.FinAudit;
import com.glw.backend.model.vo.FinanceAuditVO;
import com.glw.backend.service.FinanceAuditService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Financial audit trail. */
@Service
@Profile("!mock")
public class FinanceAuditServiceImpl extends ServiceImpl<FinAuditMapper, FinAudit>
        implements FinanceAuditService {

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String type, Long id, String action, String detail) {
        Long actor = UserContext.userId();
        baseMapper.audit(type, id, action, actor, detail);
    }

    @Override
    public IPage<FinanceAuditVO> audits(FinanceAuditQueryDTO query) {
        long pageNo = query.getPageNo() == null || query.getPageNo() < 1 ? 1 : query.getPageNo();
        long pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 20 : Math.min(query.getPageSize(), 200);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        Page<FinanceAuditVO> page = new Page<>(pageNo, pageSize);
        page.setRecords(baseMapper.audits(query));
        page.setTotal(baseMapper.countAudits(query));
        return page;
    }
}
