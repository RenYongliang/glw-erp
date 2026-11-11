package com.glw.backend.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.glw.backend.model.entity.FinAllocation;
import com.glw.backend.finance.FinanceModels.Payment;
import com.glw.backend.finance.FinanceModels.Allocation;
import com.glw.backend.finance.FinanceModels.Allocate;
import com.glw.backend.finance.FinanceModels.SettleOrderInput;
import com.glw.backend.finance.FinanceModels.SettleOrderResult;

/** Payment clearing and reversal. */
public interface FinanceAllocationService extends IService<FinAllocation> {
    Allocation allocate(Allocate input);
    /** 从订单直接收付款：创建收付款单并自动核销该订单已对账确认的开项。 */
    SettleOrderResult settleOrder(SettleOrderInput input);
    void reverse(Long id);
}
