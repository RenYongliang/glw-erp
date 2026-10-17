package com.glw.backend.model.dto;

import com.glw.backend.model.enums.OutboundStatus;
import com.glw.backend.model.enums.OutboundType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OutboundOrderPageDTO extends PageDTO {
    private Long id;
    private Long salesOrderId;
    private String customerOrderNo;
    private Long customerId;
    private String customerName;
    private OutboundType outboundType;
    private OutboundStatus status;
    private String outboundTimeStart;
    private String outboundTimeEnd;
}
