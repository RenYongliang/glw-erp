package com.glw.backend.model.vo;

import com.glw.backend.model.enums.SalesOrderStatus;
import com.glw.backend.model.enums.SalesPaymentStatus;
import com.glw.backend.model.enums.SettlementType;
import lombok.Data;

@Data
public class SalesOrderVO {


    private Long id;

    private String orderNo;

    private String customerOrderNo;

    private String orderTime;

    private Long customerId;

    private String customerName;

    private SettlementType settleType;

    private String deliveryTime;

    private SalesOrderStatus orderStatus;

    private SalesPaymentStatus paymentStatus;

    private String remark;

    private Integer version;


}
