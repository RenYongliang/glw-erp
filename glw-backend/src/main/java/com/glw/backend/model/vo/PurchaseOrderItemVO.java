package com.glw.backend.model.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PurchaseOrderItemVO {
    private Long id;
    private Long purchaseOrderId;
    private String purchaseOrderNo;
    private Long productId;
    private String brand;
    private String partNo;
    private String packageType;
    private BigDecimal unitPrice;
    private Integer taxRate;
    private Integer quantity;
    private Integer pendingQuantity;
    private BigDecimal amount;
}
