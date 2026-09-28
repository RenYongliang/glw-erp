package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.time.LocalDateTime;

/** 入库商品表（inbound_order_item）。 */
@Data
@TableName(
        value = "inbound_order_item",
        resultMap = "com.glw.backend.mapper.InboundOrderItemMapper.BaseResultMap")
public class InboundOrderItem {
    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 入库单id。 */
    @TableField("inbound_order_id")
    private Long inboundOrderId;

    /** 采购单id。 */
    @TableField("purchase_order_id")
    private Long purchaseOrderId;

    /** 采购单编号。 */
    @TableField("purchase_order_no")
    private String purchaseOrderNo;

    /** 采购商品表id。 */
    @TableField("purchase_order_item_id")
    private Long purchaseOrderItemId;

    /** 入库数量。 */
    @TableField("inbound_quantity")
    private Integer inboundQuantity;

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
}
