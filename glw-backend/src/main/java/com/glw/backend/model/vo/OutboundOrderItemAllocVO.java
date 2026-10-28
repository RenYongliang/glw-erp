package com.glw.backend.model.vo;

import com.glw.backend.model.enums.TaxIncluded;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class OutboundOrderItemAllocVO {
    private Long id;
    private Long outboundOrderItemId;
    private Long salesOrderId;
    private String customerOrderNo;
    private Long salesOrderItemId;
    private Integer outboundQuantity;
    private BigDecimal unitPrice;
    private TaxIncluded taxIncluded;
    private Integer taxRate;
    private BigDecimal amount;
    private String currency;
    private List<OutboundOrderItemBatchVO> batches = new ArrayList<>();
}
