package com.glw.backend.converter;

import com.glw.backend.model.entity.OutboundOrder;
import com.glw.backend.model.entity.OutboundOrderItem;
import com.glw.backend.model.entity.OutboundOrderBatchItem;
import com.glw.backend.model.vo.OutboundOrderVO;
import com.glw.backend.model.vo.OutboundOrderItemVO;
import com.glw.backend.model.vo.OutboundOrderBatchItemVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OutboundOrderConverter {
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "customerShortName", ignore = true)
    OutboundOrderVO toVO(OutboundOrder order);
    @Mapping(target = "batches", ignore = true)
    OutboundOrderItemVO toItemVO(OutboundOrderItem item);
    OutboundOrderBatchItemVO toBatchVO(OutboundOrderBatchItem batch);
}
