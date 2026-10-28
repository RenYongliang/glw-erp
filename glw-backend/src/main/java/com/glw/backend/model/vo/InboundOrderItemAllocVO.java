package com.glw.backend.model.vo;

import com.glw.backend.model.enums.TaxIncluded;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InboundOrderItemAllocVO {
    private Long id;
    private Long inboundOrderItemId;
    private Long purchaseOrderId;
    private String purchaseOrderNo;
    private Long purchaseOrderItemId;
    private String dateCode;
    private String lotCode;
    private Integer inboundQuantity;
    private BigDecimal unitPrice;
    private TaxIncluded taxIncluded;
    private Integer taxRate;
    private BigDecimal amount;
    private String currency;
}
