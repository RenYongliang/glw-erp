package com.glw.backend.model.vo;

import com.glw.backend.model.enums.TaxIncluded;

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
    private TaxIncluded taxIncluded;
    private Integer taxRate;
    private Integer quantity;
    private BigDecimal amount;
}
