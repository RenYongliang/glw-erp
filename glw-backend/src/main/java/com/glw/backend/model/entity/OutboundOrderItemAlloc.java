package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.glw.backend.model.enums.TaxIncluded;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 出库产品行-销售行分配（outbound_order_item_alloc）。 */
@Data
@TableName(
        value = "outbound_order_item_alloc",
        resultMap = "com.glw.backend.mapper.OutboundOrderItemAllocMapper.BaseResultMap")
public class OutboundOrderItemAlloc {
    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 出库单id。 */
    @TableField("outbound_order_id")
    private Long outboundOrderId;

    /** 出库产品行id。 */
    @TableField("outbound_order_item_id")
    private Long outboundOrderItemId;

    /** 销售订单id。 */
    @TableField("sales_order_id")
    private Long salesOrderId;

    /** 销售订单编号快照。 */
    @TableField("sales_order_no")
    private String salesOrderNo;

    /** 客户采购单编号快照。 */
    @TableField("customer_order_no")
    private String customerOrderNo;

    /** 销售商品表id。 */
    @TableField("sales_order_item_id")
    private Long salesOrderItemId;

    /** 分配到该销售行的数量。 */
    @TableField("allocated_quantity")
    private Integer allocatedQuantity;

    /** 销售单价快照。 */
    @TableField("unit_price")
    private BigDecimal unitPrice;

    /** 是否含税。 */
    @TableField("tax_included")
    private TaxIncluded taxIncluded;

    /** 增值税税率快照。 */
    @TableField("tax_rate")
    private Integer taxRate;

    /** 金额快照。 */
    @TableField("amount")
    private BigDecimal amount;

    /** 币种。 */
    @TableField("currency")
    private String currency;

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
}
