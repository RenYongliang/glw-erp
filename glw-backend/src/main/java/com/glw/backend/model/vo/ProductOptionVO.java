package com.glw.backend.model.vo;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class ProductOptionVO {
    private Long id;
    private String brand;
    private String partNo;
    private String packageType;
    private Long stockQuantity;
    private BigDecimal estimatedCost;
    private BigDecimal minCost;
    private BigDecimal maxCost;
}
