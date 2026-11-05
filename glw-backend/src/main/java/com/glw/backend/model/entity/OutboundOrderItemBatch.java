package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 出库分配行批次（outbound_order_item_batch）。 */
@Data
@TableName(
        value = "outbound_order_item_batch",
        resultMap = "com.glw.backend.mapper.OutboundOrderItemBatchMapper.BaseResultMap")
public class OutboundOrderItemBatch {
    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 出库单id。 */
    @TableField("outbound_order_id")
    private Long outboundOrderId;

    /** 出库分配行id。 */
    @TableField("alloc_id")
    private Long allocId;

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
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private Long createdBy;

    /** 更新人。 */
    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
}
