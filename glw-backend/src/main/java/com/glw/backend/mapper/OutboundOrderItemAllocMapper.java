package com.glw.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glw.backend.model.entity.OutboundOrderItemAlloc;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 出库产品行-销售行分配数据访问。由非 mock 模式的 MapperScan 注册。 */
public interface OutboundOrderItemAllocMapper extends BaseMapper<OutboundOrderItemAlloc> {
    record ShippedQuantity(Long salesOrderItemId, Long quantity) {}

    List<ShippedQuantity> sumCompletedBySalesOrderIds(@Param("orderIds") List<Long> orderIds);
}
