package com.glw.backend.model.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import com.glw.backend.model.enums.InboundStatus;
import com.glw.backend.model.enums.InboundType;

@EqualsAndHashCode(callSuper = true)
@Data
public class InboundOrderPageDTO extends PageDTO {
    private Long id;
    private Long purchaseOrderId;
    private String purchaseOrderNo;
    private Long supplierId;
    private String supplierName;
    private InboundType inboundType;
    private InboundStatus status;
    private String inboundTimeStart;
    private String inboundTimeEnd;
}
