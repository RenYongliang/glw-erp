package com.glw.backend.converter;

import com.glw.backend.model.entity.InboundOrder;
import com.glw.backend.model.entity.InboundOrderItem;
import com.glw.backend.model.entity.InboundOrderBatchItem;
import com.glw.backend.model.vo.InboundOrderBatchItemVO;
import com.glw.backend.model.vo.InboundOrderItemVO;
import com.glw.backend.model.vo.InboundOrderVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.Mapping;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface InboundOrderConverter {
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "purchaseOrders", ignore = true)
    InboundOrderVO toVO(InboundOrder inboundOrder);

    @Mapping(target = "batches", ignore = true)
    @Mapping(target = "productId", ignore = true)
    @Mapping(target = "brand", ignore = true)
    @Mapping(target = "partNo", ignore = true)
    @Mapping(target = "packageType", ignore = true)
    @Mapping(target = "unitPrice", ignore = true)
    InboundOrderItemVO toItemVO(InboundOrderItem inboundOrderItem);

    InboundOrderBatchItemVO toBatchItemVO(InboundOrderBatchItem inboundOrderBatchItem);
}
