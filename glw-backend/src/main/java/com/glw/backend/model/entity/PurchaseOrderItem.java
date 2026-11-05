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

/** 采购订单商品表（purchase_order_item）。 */
@Data
@TableName(
        value = "purchase_order_item",
        resultMap = "com.glw.backend.mapper.PurchaseOrderItemMapper.BaseResultMap")
public class PurchaseOrderItem {
    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 采购订单id。 */
    @TableField("purchase_order_id")
    private Long purchaseOrderId;

    /** 采购订单编号。 */
    @TableField("purchase_order_no")
    private String purchaseOrderNo;

    /** 产品id。 */
    @TableField("product_id")
    private Long productId;

    /** 品牌。 */
    @TableField("brand")
    private String brand;

    /** 型号。 */
    @TableField("part_no")
    private String partNo;

    /** 封装。 */
    @TableField("package_type")
    private String packageType;

    /** 单价；口径见订单表的 tax_included。 */
    @TableField("unit_price")
    private BigDecimal unitPrice;

    /** 增值税税率；未税单价按此税率换算含税库存成本。 */
    @TableField("tax_rate")
    private Integer taxRate;

    /** 数量。 */
    @TableField("quantity")
    private Integer quantity;

    /** 金额。 */
    @TableField("amount")
    private BigDecimal amount;

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
