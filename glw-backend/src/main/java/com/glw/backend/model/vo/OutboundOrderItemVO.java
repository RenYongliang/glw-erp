package com.glw.backend.model.vo;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class OutboundOrderItemVO {
    private Long id;
    private Long outboundOrderId;
    private Long salesOrderId;
    private String customerOrderNo;
    private Long salesOrderItemId;
    private Integer outboundQuantity;
    private List<OutboundOrderBatchItemVO> batches = new ArrayList<>();
}
