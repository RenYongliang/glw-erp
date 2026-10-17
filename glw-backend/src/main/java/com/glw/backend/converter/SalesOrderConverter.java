package com.glw.backend.converter;

import com.glw.backend.model.entity.SalesOrder;
import com.glw.backend.model.vo.SalesOrderVO;
import com.glw.backend.model.entity.SalesOrderItem;
import com.glw.backend.model.vo.SalesOrderItemVO;
import org.mapstruct.Mapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SalesOrderConverter {

    @Mapping(target = "items", ignore = true)
    @Mapping(target = "customerShortName", ignore = true)
    SalesOrderVO toVO(SalesOrder salesOrder);
    @Mapping(target = "pendingQuantity", ignore = true)
    SalesOrderItemVO toItemVO(SalesOrderItem item);
}
