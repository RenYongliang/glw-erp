package com.glw.backend.model.vo;

import lombok.Data;

@Data
public class InboundOrderItemVO {
    private Long id;
    private Long inboundOrderId;
    private Long purchaseOrderId;
    private String purchaseOrderNo;
    private Long purchaseOrderItemId;
    private Integer inboundQuantity;
}
