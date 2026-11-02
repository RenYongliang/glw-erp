package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.glw.backend.model.enums.PurchaseOrderStatus;
import com.glw.backend.model.enums.PurchasePaymentStatus;
import com.glw.backend.model.enums.TaxIncluded;

import lombok.Data;

import java.time.LocalDateTime;

/** 采购订单表（purchase_order）。 */
@Data
@TableName(
        value = "purchase_order",
        resultMap = "com.glw.backend.mapper.PurchaseOrderMapper.BaseResultMap")
public class PurchaseOrder {
    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 采购单编号。 */
    @TableField("purchase_order_no")
    private String purchaseOrderNo;

    /** 下单时间。 */
    @TableField("order_time")
    private LocalDateTime orderTime;

    /** 供应商id。 */
    @TableField("supplier_id")
    private Long supplierId;

    /** 供应商名称。 */
    @TableField("supplier_name")
    private String supplierName;

    /** 交付日期。 */
    @TableField("delivery_time")
    private LocalDateTime deliveryTime;

    /** 单价口径, EXCLUDED:未税; INCLUDED:含税；整张订单统一。 */
    @TableField("tax_included")
    private TaxIncluded taxIncluded;

    /** 订单状态, PENDING_RECEIPT:待收货; PARTIALLY_RECEIVED:部分收货; COMPLETED:已完成。 */
    @TableField("order_status")
    private PurchaseOrderStatus orderStatus;

    /** 付款状态, UNPAID:待付款; PARTIALLY_PAID:部分付款; PAID:已付款。 */
    @TableField("payment_status")
    private PurchasePaymentStatus paymentStatus;

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
    @TableField("created_by")
    private Long createdBy;

    /** 更新人。 */
    @TableField("updated_by")
    private Long updatedBy;

    @TableField(exist = false)
    private String orderTimeStart;

    @TableField(exist = false)
    private String orderTimeEnd;
}
