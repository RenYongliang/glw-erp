package com.glw.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glw.backend.model.entity.InboundOrderItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 入库商品表数据访问。由非 mock 模式的 MapperScan 注册。 */
public interface InboundOrderItemMapper extends BaseMapper<InboundOrderItem> {
    record ReceivedQuantity(Long purchaseOrderItemId, Long quantity) {}

    List<ReceivedQuantity> sumCompletedByPurchaseOrderIds(@Param("orderIds") List<Long> orderIds);
}
