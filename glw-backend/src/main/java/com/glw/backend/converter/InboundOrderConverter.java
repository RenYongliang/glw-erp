package com.glw.backend.converter;

import com.glw.backend.model.entity.InboundOrder;
import com.glw.backend.model.entity.InboundOrderItem;
import com.glw.backend.model.entity.InboundOrderItemAlloc;
import com.glw.backend.model.vo.InboundOrderItemAllocVO;
import com.glw.backend.model.vo.InboundOrderItemVO;
import com.glw.backend.model.vo.InboundOrderVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface InboundOrderConverter {
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "purchaseOrders", ignore = true)
    InboundOrderVO toVO(InboundOrder inboundOrder);

    @Mapping(target = "allocations", ignore = true)
    InboundOrderItemVO toItemVO(InboundOrderItem inboundOrderItem);

    @Mapping(target = "inboundQuantity", source = "allocatedQuantity")
    InboundOrderItemAllocVO toAllocVO(InboundOrderItemAlloc inboundOrderItemAlloc);
}
