package com.glw.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glw.backend.model.entity.InboundOrderItemAlloc;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 入库产品行-采购行分配数据访问。由非 mock 模式的 MapperScan 注册。 */
public interface InboundOrderItemAllocMapper extends BaseMapper<InboundOrderItemAlloc> {
    record ReceivedQuantity(Long purchaseOrderItemId, Long quantity) {}

    List<ReceivedQuantity> sumCompletedByPurchaseOrderIds(@Param("orderIds") List<Long> orderIds);
}
