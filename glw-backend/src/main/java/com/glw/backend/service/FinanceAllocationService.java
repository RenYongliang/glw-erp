package com.glw.backend.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.glw.backend.model.entity.FinAllocation;
import com.glw.backend.finance.FinanceModels.Payment;
import com.glw.backend.finance.FinanceModels.Allocation;
import com.glw.backend.finance.FinanceModels.Allocate;

/** Payment clearing and reversal. */
public interface FinanceAllocationService extends IService<FinAllocation> {
    Allocation allocate(Allocate input);
    void reverse(Long id);
}
