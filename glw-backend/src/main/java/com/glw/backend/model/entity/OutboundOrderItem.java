package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.time.LocalDateTime;

/** 出库产品行（outbound_order_item）。 */
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

    /** 产品id。 */
    @TableField("product_id")
    private Long productId;

    /** 品牌快照。 */
    @TableField("brand")
    private String brand;

    /** 型号快照。 */
    @TableField("part_no")
    private String partNo;

    /** 封装快照。 */
    @TableField("package_type")
    private String packageType;

    /** 产品出库总数量。 */
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
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private Long createdBy;

    /** 更新人。 */
    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
}
