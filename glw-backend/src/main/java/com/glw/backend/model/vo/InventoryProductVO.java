package com.glw.backend.model.vo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class InventoryProductVO {
    private Long productId;
    private String brand;
    private String partNo;
    private String packageType;
    private Long availableQuantity;
    private Long batchCount;
    private BigDecimal averageCost;
    private BigDecimal minCost;
    private BigDecimal maxCost;
    private BigDecimal stockValue;
    private List<InventoryBatchVO> batches = new ArrayList<>();
}
