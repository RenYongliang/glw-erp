package com.glw.backend.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.glw.backend.model.entity.FinLock;


/** Serializes financial mutations within the current transaction. */
public interface FinanceLockService extends IService<FinLock> {
    void lock();
}
