package com.glw.backend.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OutboundOrderItemVO {
    private Long id;
    private Long outboundOrderId;
    private Long productId;
    private String brand;
    private String partNo;
    private String packageType;
    private Integer outboundQuantity;
    private List<OutboundOrderItemAllocVO> allocations = new ArrayList<>();
}
