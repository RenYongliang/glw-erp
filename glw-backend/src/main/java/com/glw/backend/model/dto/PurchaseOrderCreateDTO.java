package com.glw.backend.model.dto;

import com.glw.backend.model.enums.TaxIncluded;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import lombok.Data;

import java.util.List;

/** 新增或修改采购订单。 */
@Data
public class PurchaseOrderCreateDTO {
    @NotNull
    private Long supplierId;

    /** 下单日期，格式 YYYY-MM-DD 或 YYYY-MM-DD HH:mm:ss。 */
    private String orderTime;

    /** 交付日期，格式 YYYY-MM-DD 或 YYYY-MM-DD HH:mm:ss。 */
    private String deliveryTime;

    /** 单价口径，不填默认含税；整张订单统一，明细不再逐行选择。 */
    private TaxIncluded taxIncluded;

    /** 结算期限等备注。 */
    @Size(max = 256)
    private String remark;

    /** 修改时携带查询得到的版本号，新增时无需填写。 */
    @PositiveOrZero
    private Integer version;

    @Valid
    @NotEmpty
    private List<PurchaseOrderItemCreateDTO> items;
}
