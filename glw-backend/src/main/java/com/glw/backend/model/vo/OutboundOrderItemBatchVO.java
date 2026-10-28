package com.glw.backend.model.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OutboundOrderItemBatchVO {
    private Long id;
    private Long allocId;
    private Long inventoryId;
    private Integer quantity;
    private BigDecimal unitCost;
}
