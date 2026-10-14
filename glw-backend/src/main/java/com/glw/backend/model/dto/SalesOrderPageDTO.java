package com.glw.backend.model.dto;

import com.glw.backend.model.enums.SettlementType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class SalesOrderPageDTO extends PageDTO {

    private Long id;

    private String orderNo;

    private String customerOrderNo;

    private String orderTimeStart;

    private String orderTimeEnd;

    private Long customerId;

    private SettlementType settleType;

    private String deliveryTime;

    private String orderStatus;

    private String paymentStatus;


    private String brand;

    private String partNo;

    private String packageType;




}
