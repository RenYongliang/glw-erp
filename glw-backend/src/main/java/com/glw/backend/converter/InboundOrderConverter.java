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
    InboundOrderVO toVO(InboundOrder inboundOrder);

    @Mapping(target = "batches", ignore = true)
    InboundOrderItemVO toItemVO(InboundOrderItem inboundOrderItem);

    InboundOrderBatchItemVO toBatchItemVO(InboundOrderBatchItem inboundOrderBatchItem);
}
