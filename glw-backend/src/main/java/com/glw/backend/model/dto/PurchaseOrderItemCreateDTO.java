package com.glw.backend.model.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

import java.math.BigDecimal;

/** 采购订单产品明细。品牌、型号、封装由产品档案生成，不接受前端传入。 */
@Data
public class PurchaseOrderItemCreateDTO {
    @NotNull
    private Long productId;

    @NotNull
    @DecimalMin("0.0000")
    @DecimalMax("10000000.0000")
    @Digits(integer = 8, fraction = 4)
    private BigDecimal unitPrice;

    /** 增值税税率；未税单价填写税率后可换算含税库存成本。 */
    @Min(0)
    @Max(100)
    private Integer taxRate;

    @NotNull
    @Min(1)
    @Max(100000000)
    private Integer quantity;
}
