package com.glw.backend.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class InboundOrderItemVO {
    private Long id;
    private Long inboundOrderId;
    private Long productId;
    private String brand;
    private String partNo;
    private String packageType;
    private Integer inboundQuantity;
    private List<InboundOrderItemAllocVO> allocations = new ArrayList<>();
}
