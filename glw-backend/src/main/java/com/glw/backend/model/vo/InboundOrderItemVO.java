package com.glw.backend.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class InboundOrderItemVO {
    private Long id;
    private Long inboundOrderId;
    private Long purchaseOrderId;
    private String purchaseOrderNo;
    private Long purchaseOrderItemId;
    private Integer inboundQuantity;
    private List<InboundOrderBatchItemVO> batches = new ArrayList<>();
}
