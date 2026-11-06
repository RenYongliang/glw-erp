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
    /** 客户签收时间；为空表示尚未签收，应收记录暂不进入对账单。 */
    private String signTime;
    /** 关联对账单状态：DRAFT/SENT/CONFIRMED/VOID；为空表示未出对账单。 */
    private String statementStatus;
    private com.glw.backend.model.dto.OutboundShippingDTO shipping;
    private Integer version;
    private List<OutboundSalesOrderVO> salesOrders = new ArrayList<>();
    private List<OutboundOrderItemVO> items = new ArrayList<>();
}
