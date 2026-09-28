package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.time.LocalDateTime;

/** 出库商品表（outbound_order_item）。 */
@Data
@TableName(
        value = "outbound_order_item",
        resultMap = "com.glw.backend.mapper.OutboundOrderItemMapper.BaseResultMap")
public class OutboundOrderItem {
    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 出库单id。 */
    @TableField("outbound_order_id")
    private Long outboundOrderId;

    /** 销售单id。 */
    @TableField("sales_order_id")
    private Long salesOrderId;

    /** 客户采购单编号。 */
    @TableField("customer_order_no")
    private String customerOrderNo;

    /** 销售商品表id。 */
    @TableField("sales_order_item_id")
    private Long salesOrderItemId;

    /** 出库数量。 */
    @TableField("outbound_quantity")
    private Integer outboundQuantity;

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
