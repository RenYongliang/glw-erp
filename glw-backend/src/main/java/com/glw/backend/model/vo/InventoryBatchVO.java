package com.glw.backend.model.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class InventoryBatchVO {
    private Long inventoryId;
    private Long productId;
    private String dateCode;
    private String lotCode;
    private String batchNo;
    private Long quantity;
    private Long availableQuantity;
    private BigDecimal unitCost;
    private BigDecimal stockValue;
    private LocalDateTime inboundTime;
}
