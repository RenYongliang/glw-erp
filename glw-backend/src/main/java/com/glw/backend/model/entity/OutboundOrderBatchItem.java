package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 出库商品批次表（outbound_order_batch_item）。 */
@Data
@TableName(
        value = "outbound_order_batch_item",
        resultMap = "com.glw.backend.mapper.OutboundOrderBatchItemMapper.BaseResultMap")
public class OutboundOrderBatchItem {
    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 出库订单明细id。 */
    @TableField("outbound_order_item_id")
    private Long outboundOrderItemId;

    /** 库存批次id。 */
    @TableField("inventory_id")
    private Long inventoryId;

    /** 实际出库数量。 */
    @TableField("quantity")
    private Integer quantity;

    /** 出库成本单价快照。 */
    @TableField("unit_cost")
    private BigDecimal unitCost;

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
