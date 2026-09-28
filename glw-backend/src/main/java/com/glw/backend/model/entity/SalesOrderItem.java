package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 销售订单商品表（sales_order_item）。 */
@Data
@TableName(
        value = "sales_order_item",
        resultMap = "com.glw.backend.mapper.SalesOrderItemMapper.BaseResultMap")
public class SalesOrderItem {
    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 销售订单id。 */
    @TableField("sales_order_id")
    private Long salesOrderId;

    /** 客户采购单号。 */
    @TableField("customer_order_no")
    private String customerOrderNo;

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

    /** 单价。 */
    @TableField("unit_price")
    private BigDecimal unitPrice;

    /** 是否含税 0未税 1含税。 */
    @TableField("tax_included")
    private Integer taxIncluded;

    /** 增值税税率。 */
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
    @TableField("created_by")
    private Long createdBy;

    /** 更新人。 */
    @TableField("updated_by")
    private Long updatedBy;
}
