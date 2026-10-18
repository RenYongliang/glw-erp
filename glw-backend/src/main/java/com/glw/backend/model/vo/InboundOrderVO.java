package com.glw.backend.model.vo;

import com.glw.backend.model.enums.InboundStatus;
import com.glw.backend.model.enums.InboundType;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class InboundOrderVO {
    private Long id;
    private String inboundOrderNo;
    private Long supplierId;
    private String supplierName;
    private InboundType inboundType;
    private InboundStatus status;
    private String inboundTime;
    private Integer version;
    private List<InboundOrderItemVO> items = new ArrayList<>();
    private List<InboundPurchaseOrderVO> purchaseOrders = new ArrayList<>();
}
