package com.glw.backend.model.dto;

import com.glw.backend.model.enums.SettlementType;
import com.glw.backend.model.enums.TaxIncluded;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import lombok.Data;

import java.util.List;

/** 销售订单新增/修改入参。客户名称、订单号与订单状态由服务端生成。 */
@Data
public class SalesOrderCreateDTO {
    @NotNull
    private Long customerId;

    private String orderTime;

    private String deliveryTime;

    /** 客户采购单号。 */
    @Size(max = 64)
    private String customerOrderNo;

    /** 结算类型，不填默认现金。 */
    private SettlementType settleType;

    /** 单价口径，不填默认含税；整张订单统一，明细不再逐行选择。 */
    private TaxIncluded taxIncluded;

    @Size(max = 256)
    private String remark;

    /** 乐观锁版本号；新增不填，修改需回传列表查询到的版本。 */
    @PositiveOrZero
    private Integer version;

    @Valid
    @NotEmpty
    private List<SalesOrderItemCreateDTO> items;
}
