package com.glw.backend.model.vo;

import com.glw.backend.model.enums.OutboundStatus;
import com.glw.backend.model.enums.OutboundType;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class OutboundOrderVO {
    private Long id;
    private String outboundOrderNo;
    private Long customerId;
    private String customerName;
    private String customerShortName;
    private OutboundType outboundType;
    private OutboundStatus status;
    private String outboundTime;
    private com.glw.backend.model.dto.OutboundShippingDTO shipping;
    private Integer version;
    private List<OutboundSalesOrderVO> salesOrders = new ArrayList<>();
    private List<OutboundOrderItemVO> items = new ArrayList<>();
}
