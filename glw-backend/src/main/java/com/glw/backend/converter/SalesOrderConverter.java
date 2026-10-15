package com.glw.backend.converter;

import com.glw.backend.model.entity.SalesOrder;
import com.glw.backend.model.vo.SalesOrderVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SalesOrderConverter {

    SalesOrderVO toVO(SalesOrder salesOrder);
}
