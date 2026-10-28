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
import org.mapstruct.MappingTarget;
import org.mapstruct.BeanMapping;
import org.mapstruct.InheritInverseConfiguration;
import com.glw.backend.model.dto.OutboundShippingDTO;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OutboundOrderConverter {
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "salesOrders", ignore = true)
    @Mapping(target = "customerShortName", ignore = true)
    @Mapping(target = "shipping", expression = "java(toShipping(order))")
    OutboundOrderVO toVO(OutboundOrder order);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "shippingAddress", source = "address")
    @Mapping(target = "shippingRecipient", source = "recipient")
    @Mapping(target = "shippingPhone", source = "phone")
    @Mapping(target = "shippingSender", source = "sender")
    @Mapping(target = "shippingSenderPhone", source = "senderPhone")
    @Mapping(target = "shippingMethod", source = "method")
    @Mapping(target = "shippingCarrier", source = "carrier")
    @Mapping(target = "shippingTrackingNumber", source = "trackingNumber")
    @Mapping(target = "shippingPurchaseNumber", source = "purchaseNumber")
    @Mapping(target = "shippingRemark", source = "remark")
    void applyShipping(OutboundShippingDTO shipping, @MappingTarget OutboundOrder order);

    @InheritInverseConfiguration(name = "applyShipping")
    OutboundShippingDTO toShipping(OutboundOrder order);

    @Mapping(target = "allocations", ignore = true)
    OutboundOrderItemVO toItemVO(OutboundOrderItem item);

    @Mapping(target = "outboundQuantity", source = "allocatedQuantity")
    @Mapping(target = "batches", ignore = true)
    OutboundOrderItemAllocVO toAllocVO(OutboundOrderItemAlloc alloc);

    OutboundOrderItemBatchVO toBatchVO(OutboundOrderItemBatch batch);
}
