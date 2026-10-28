package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.auth.AuthPrincipal;

import com.glw.backend.mapper.FinAuditMapper;
import com.glw.backend.model.entity.FinAudit;
import com.glw.backend.service.FinanceAuditService;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContextHolder;
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
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Long actor = auth != null && auth.getPrincipal() instanceof AuthPrincipal p ? Long.valueOf(p.user().id()) : null;
        baseMapper.audit(type, id, action, actor, detail);
    }
}
