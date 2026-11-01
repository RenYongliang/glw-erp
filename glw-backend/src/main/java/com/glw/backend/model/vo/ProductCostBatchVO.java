package com.glw.backend.model.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ProductCostBatchVO {
    private Long inventoryId;
    private String batchNo;
    private String dateCode;
    private String lotCode;
    /** 入库日期；无来源入库单时为空。 */
    private LocalDateTime inboundTime;
    private Integer quantity;
    private BigDecimal unitCost;
}
