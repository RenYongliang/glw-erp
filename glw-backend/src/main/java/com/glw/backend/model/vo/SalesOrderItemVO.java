package com.glw.backend.model.vo;

import com.glw.backend.model.enums.TaxIncluded;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class SalesOrderItemVO {
    private Long id;
    private Long salesOrderId;
    private String customerOrderNo;
    private Long productId;
    private String brand;
    private String partNo;
    private String packageType;
    /** 客户物料编码快照；客户未提供时为空。 */
    private String customerPartNo;
    private BigDecimal unitPrice;
    private TaxIncluded taxIncluded;
    private Integer taxRate;
    private Integer quantity;
    private Integer pendingQuantity;
    private Integer cancelledQuantity;
    private BigDecimal amount;
}
