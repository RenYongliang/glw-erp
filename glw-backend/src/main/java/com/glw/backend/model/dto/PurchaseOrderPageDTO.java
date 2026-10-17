package com.glw.backend.model.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import com.glw.backend.model.enums.PurchaseOrderStatus;
import com.glw.backend.model.enums.PurchasePaymentStatus;

@EqualsAndHashCode(callSuper = true)
@Data
public class PurchaseOrderPageDTO extends PageDTO {
    private Long id;
    private String purchaseOrderNo;
    private Long supplierId;
    private String supplierName;
    private String orderTimeStart;
    private String orderTimeEnd;
    private PurchaseOrderStatus orderStatus;
    private PurchasePaymentStatus paymentStatus;
    private String brand;
    private String partNo;
    private String packageType;
}
