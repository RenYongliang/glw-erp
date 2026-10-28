package com.glw.backend.converter;

import com.glw.backend.model.entity.OutboundOrder;
import com.glw.backend.model.entity.OutboundOrderItem;
import com.glw.backend.model.entity.OutboundOrderItemAlloc;
import com.glw.backend.model.entity.OutboundOrderItemBatch;
import com.glw.backend.model.vo.OutboundOrderVO;
import com.glw.backend.model.vo.OutboundOrderItemVO;
import com.glw.backend.model.vo.OutboundOrderItemAllocVO;
import com.glw.backend.model.vo.OutboundOrderItemBatchVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OutboundOrderConverter {
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "salesOrders", ignore = true)
    @Mapping(target = "customerShortName", ignore = true)
    OutboundOrderVO toVO(OutboundOrder order);

    @Mapping(target = "allocations", ignore = true)
    OutboundOrderItemVO toItemVO(OutboundOrderItem item);

    @Mapping(target = "outboundQuantity", source = "allocatedQuantity")
    @Mapping(target = "batches", ignore = true)
    OutboundOrderItemAllocVO toAllocVO(OutboundOrderItemAlloc alloc);

    OutboundOrderItemBatchVO toBatchVO(OutboundOrderItemBatch batch);
}
