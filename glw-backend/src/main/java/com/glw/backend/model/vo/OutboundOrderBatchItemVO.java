package com.glw.backend.model.vo;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class OutboundOrderBatchItemVO {
    private Long id;
    private Long outboundOrderItemId;
    private Long inventoryId;
    private Integer quantity;
    private BigDecimal unitCost;
}
