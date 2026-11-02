package com.glw.backend.model.vo;

import com.glw.backend.model.enums.SalesOrderStatus;
import com.glw.backend.model.enums.SalesPaymentStatus;
import com.glw.backend.model.enums.SettlementType;
import com.glw.backend.model.enums.TaxIncluded;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class SalesOrderVO {


    private Long id;

    private String orderNo;

    private String customerOrderNo;

    private String orderTime;

    private Long customerId;

    private String customerName;
    private String customerShortName;

    private SettlementType settleType;

    /** 单价口径, EXCLUDED:未税; INCLUDED:含税。 */
    private TaxIncluded taxIncluded;

    private String deliveryTime;

    private SalesOrderStatus orderStatus;

    private SalesPaymentStatus paymentStatus;

    private String remark;

    private Integer version;
    private List<SalesOrderItemVO> items = new ArrayList<>();


}
