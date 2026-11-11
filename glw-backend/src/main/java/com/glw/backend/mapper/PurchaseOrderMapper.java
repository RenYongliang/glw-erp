package com.glw.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glw.backend.model.entity.PurchaseOrder;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Param;

/** 采购订单表数据访问。由非 mock 模式的 MapperScan 注册。 */
public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrder> {
    /** 订单明细金额合计，用于控制订单付款不超过订单金额。 */
    BigDecimal orderTotal(@Param("orderId") Long orderId);
}
