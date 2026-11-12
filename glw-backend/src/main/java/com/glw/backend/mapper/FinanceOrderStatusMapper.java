package com.glw.backend.mapper;

import com.glw.backend.finance.FinanceOrderStatus.OrderStatus;
import com.glw.backend.finance.FinanceOrderStatus.Prepayment;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** Ledger-derived payment status for business order lists. */
public interface FinanceOrderStatusMapper {
    List<OrderStatus> orderStatuses(@Param("direction") String direction, @Param("ids") List<Long> ids);
    List<Long> orderIdsByStatus(@Param("direction") String direction, @Param("status") String status);
    /** 按订单汇总尚未核销的预付款余额。 */
    List<Prepayment> orderPrepayments(@Param("direction") String direction, @Param("ids") List<Long> ids);
    /** 订单下有效（未作废）的收付款记录数。 */
    long activePaymentCount(@Param("direction") String direction, @Param("orderId") Long orderId);
}
