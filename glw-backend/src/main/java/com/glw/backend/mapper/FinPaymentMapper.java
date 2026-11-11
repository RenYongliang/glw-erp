package com.glw.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glw.backend.model.entity.FinPayment;
import com.glw.backend.finance.FinanceModels.Query;
import com.glw.backend.finance.FinanceModels.Payment;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface FinPaymentMapper extends BaseMapper<FinPayment> {
    int insertPayment(Payment payment);
    Payment payment(Long id);
    Payment paymentByKey(String key);
    List<Payment> payments(@Param("q") Query query);
    long countPayments(@Param("q") Query query);
    int allocate(@Param("id") Long id, @Param("amount") java.math.BigDecimal amount);
    int voidPayment(Long id);
    int updateOrderAndType(@Param("id") Long id, @Param("orderId") Long orderId, @Param("paymentType") String paymentType);
}
