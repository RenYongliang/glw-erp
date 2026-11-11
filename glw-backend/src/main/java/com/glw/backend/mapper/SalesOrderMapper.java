package com.glw.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glw.backend.model.entity.SalesOrder;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Param;

/** 销售订单表数据访问。由非 mock 模式的 MapperScan 注册。 */
public interface SalesOrderMapper extends BaseMapper<SalesOrder> {
    /** 订单明细金额合计，用于控制订单收款不超过订单金额。 */
    BigDecimal orderTotal(@Param("orderId") Long orderId);
}
