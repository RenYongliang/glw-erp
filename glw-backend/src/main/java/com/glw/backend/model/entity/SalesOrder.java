package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.glw.backend.model.enums.SalesDeliveryStatus;
import com.glw.backend.model.enums.SalesOrderStatus;
import com.glw.backend.model.enums.SalesPaymentStatus;
import com.glw.backend.model.enums.SettlementType;
import com.glw.backend.model.enums.TaxIncluded;

import lombok.Data;

import java.time.LocalDateTime;

/** 销售订单表（sales_order）。 */
@Data
@TableName(
        value = "sales_order",
        resultMap = "com.glw.backend.mapper.SalesOrderMapper.BaseResultMap")
public class SalesOrder {
    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 销售订单号。 */
    @TableField(value = "order_no")
    private String orderNo;

    /** 客户采购单号。 */
    @TableField("customer_order_no")
    private String customerOrderNo;

    /** 下单时间。 */
    @TableField("order_time")
    private LocalDateTime orderTime;

    /** 客户id。 */
    @TableField("customer_id")
    private Long customerId;

    /** 客户名称。 */
    @TableField("customer_name")
    private String customerName;

    /** 结算类型, CASH:现金; MONTHLY:月结; PREPAID:预付; COD:货到付款。 */
    @TableField("settle_type")
    private SettlementType settleType;

    /** 单价口径, EXCLUDED:未税; INCLUDED:含税；整张订单统一。 */
    @TableField("tax_included")
    private TaxIncluded taxIncluded;

    /** 交付日期。 */
    @TableField("delivery_time")
    private LocalDateTime deliveryTime;

    /** 订单状态, PENDING_APPROVAL:待审核（预留）; CONFIRMED:执行中; COMPLETED:已完结; CANCELLED:已取消。 */
    @TableField("order_status")
    private SalesOrderStatus orderStatus;

    /** 发货状态, PENDING_SHIPMENT:未发货; PARTIALLY_SHIPPED:部分发货; SHIPPED:全部发货。 */
    @TableField("delivery_status")
    private SalesDeliveryStatus deliveryStatus;

    /** 收款状态, UNPAID:待收款; PARTIALLY_PAID:部分收款; PAID:已收款。 */
    @TableField("payment_status")
    private SalesPaymentStatus paymentStatus;

    /** 备注。 */
    @TableField("remark")
    private String remark;

    /** 乐观锁版本号；新增默认 0，更新时携带查询得到的版本。 */
    @Version
    @TableField("version")
    private Integer version;

    /** 创建时间。 */
    @TableField(
            value = "created_at",
            insertStrategy = FieldStrategy.NEVER,
            updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    /** 更新时间。 */
    @TableField(
            value = "updated_at",
            insertStrategy = FieldStrategy.NEVER,
            updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;

    /** 创建人。 */
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private Long createdBy;

    /** 更新人。 */
    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;


    @TableField(exist = false)
    private String orderTimeStart;

    @TableField(exist = false)
    private String orderTimeEnd;
}
