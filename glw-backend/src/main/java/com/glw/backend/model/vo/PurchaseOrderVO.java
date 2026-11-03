package com.glw.backend.model.vo;

import com.glw.backend.model.enums.PurchaseOrderStatus;
import com.glw.backend.model.enums.PurchasePaymentStatus;
import com.glw.backend.model.enums.PurchaseReceiptStatus;
import com.glw.backend.model.enums.TaxIncluded;

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
    private String supplierShortName;
    private String deliveryTime;

    /** 单价口径, EXCLUDED:未税; INCLUDED:含税；整张订单统一。 */
    private TaxIncluded taxIncluded;

    private PurchaseOrderStatus orderStatus;
    private PurchaseReceiptStatus receiptStatus;
    private PurchasePaymentStatus paymentStatus;
    private String remark;
    private Integer version;
    private List<PurchaseOrderItemVO> items = new ArrayList<>();
}
