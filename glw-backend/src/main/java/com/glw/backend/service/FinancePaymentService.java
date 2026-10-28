package com.glw.backend.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.glw.backend.model.entity.FinPayment;
import com.glw.backend.finance.FinanceModels.Query;
import com.glw.backend.finance.FinanceModels.Page;
import com.glw.backend.finance.FinanceModels.Payment;
import com.glw.backend.finance.FinanceModels.PaymentInput;
import com.glw.backend.finance.FinanceModels.PaymentDetail;

/** Cash receipt/payment registration and cancellation. */
public interface FinancePaymentService extends IService<FinPayment> {
    PaymentDetail createPayment(PaymentInput input);
    Page<Payment> payments(Query q);
    PaymentDetail payment(Long id);
    void voidPayment(Long id);
    Payment requirePayment(Long id);
}
