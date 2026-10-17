package com.glw.backend.model.vo;

import com.glw.backend.model.enums.PurchaseOrderStatus;
import com.glw.backend.model.enums.PurchasePaymentStatus;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PurchaseOrderVO {
    private Long id;
    private String purchaseOrderNo;
    private String orderTime;
    private Long supplierId;
    private String supplierName;
    private String deliveryTime;
    private PurchaseOrderStatus orderStatus;
    private PurchasePaymentStatus paymentStatus;
    private String remark;
    private Integer version;
    private List<PurchaseOrderItemVO> items = new ArrayList<>();
}
