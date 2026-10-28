package com.glw.backend.model.vo;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class ProductCostBatchVO {
    private Long inventoryId;
    private String batchNo;
    private String dateCode;
    private String lotCode;
    private Integer quantity;
    private BigDecimal unitCost;
}
