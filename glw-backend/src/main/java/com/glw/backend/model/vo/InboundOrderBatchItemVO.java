package com.glw.backend.model.vo;

import lombok.Data;

@Data
public class InboundOrderBatchItemVO {
    private Long id;
    private Long inboundOrderItemId;
    private Long productId;
    private String dateCode;
    private String lotCode;
    private Integer inboundQuantity;
}
