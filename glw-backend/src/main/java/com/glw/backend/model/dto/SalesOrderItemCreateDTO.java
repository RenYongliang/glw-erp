package com.glw.backend.model.dto;

import com.glw.backend.model.enums.TaxIncluded;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

import java.math.BigDecimal;

/** 销售订单产品明细。品牌、型号、封装由产品档案生成，不接受前端传入。 */
@Data
public class SalesOrderItemCreateDTO {
    @NotNull
    private Long productId;

    @NotNull
    @DecimalMin("0.0000")
    @DecimalMax("10000000.0000")
    @Digits(integer = 8, fraction = 4)
    private BigDecimal unitPrice;

    /** 客户系统内的物料编码；为空时按客户客件对照自动带出。 */
    @Size(max = 64)
    private String customerPartNo;

    @NotNull
    private TaxIncluded taxIncluded;

    /** 增值税税率；含税单价需填写，未税单价可不填。 */
    @Min(0)
    @Max(100)
    private Integer taxRate;

    @NotNull
    @Min(1)
    @Max(100000000)
    private Integer quantity;
}
