package com.glw.backend.mapper;

import com.glw.backend.finance.FinanceOrderStatus.OrderStatus;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** Ledger-derived payment status for business order lists. */
public interface FinanceOrderStatusMapper {
    List<OrderStatus> orderStatuses(@Param("direction") String direction, @Param("ids") List<Long> ids);
    List<Long> orderIdsByStatus(@Param("direction") String direction, @Param("status") String status);
}
