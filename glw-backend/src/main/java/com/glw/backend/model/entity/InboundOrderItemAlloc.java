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

/** 入库产品行-采购行分配（inbound_order_item_alloc）。 */
@Data
@TableName(
        value = "inbound_order_item_alloc",
        resultMap = "com.glw.backend.mapper.InboundOrderItemAllocMapper.BaseResultMap")
public class InboundOrderItemAlloc {
    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 入库单id。 */
    @TableField("inbound_order_id")
    private Long inboundOrderId;

    /** 入库产品行id。 */
    @TableField("inbound_order_item_id")
    private Long inboundOrderItemId;

    /** 采购单id。 */
    @TableField("purchase_order_id")
    private Long purchaseOrderId;

    /** 采购单编号快照。 */
    @TableField("purchase_order_no")
    private String purchaseOrderNo;

    /** 采购商品表id。 */
    @TableField("purchase_order_item_id")
    private Long purchaseOrderItemId;

    /** 原厂日期码。 */
    @TableField("date_code")
    private String dateCode;

    /** 原厂批次号。 */
    @TableField("lot_code")
    private String lotCode;

    /** 分配到该采购行的数量。 */
    @TableField("allocated_quantity")
    private Integer allocatedQuantity;

    /** 采购单价快照。 */
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
