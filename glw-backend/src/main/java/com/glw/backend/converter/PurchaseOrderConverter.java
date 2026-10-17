package com.glw.backend.converter;

import com.glw.backend.model.entity.PurchaseOrder;
import com.glw.backend.model.entity.PurchaseOrderItem;
import com.glw.backend.model.vo.PurchaseOrderItemVO;
import com.glw.backend.model.vo.PurchaseOrderVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.Mapping;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PurchaseOrderConverter {
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "supplierShortName", ignore = true)
    PurchaseOrderVO toVO(PurchaseOrder purchaseOrder);
    @Mapping(target = "pendingQuantity", ignore = true)
    PurchaseOrderItemVO toItemVO(PurchaseOrderItem purchaseOrderItem);
}
