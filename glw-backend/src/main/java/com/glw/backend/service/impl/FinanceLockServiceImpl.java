package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;

import com.glw.backend.mapper.FinLockMapper;
import com.glw.backend.model.entity.FinLock;
import com.glw.backend.service.FinanceLockService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Serializes financial mutations within the current transaction. */
@Service
@Profile("!mock")
public class FinanceLockServiceImpl extends ServiceImpl<FinLockMapper, FinLock>
        implements FinanceLockService {

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void lock() { baseMapper.lock(); }
}
