package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.time.LocalDateTime;

/** 出库单与销售订单关联（outbound_order_sales_order）。 */
@Data
@TableName(
        value = "outbound_order_sales_order",
        resultMap = "com.glw.backend.mapper.OutboundOrderSalesOrderMapper.BaseResultMap")
public class OutboundOrderSalesOrder {
    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 出库单id。 */
    @TableField("outbound_order_id")
    private Long outboundOrderId;

    /** 销售订单id。 */
    @TableField("sales_order_id")
    private Long salesOrderId;

    /** 销售订单编号快照。 */
    @TableField("sales_order_no")
    private String salesOrderNo;

    /** 客户采购单编号快照。 */
    @TableField("customer_order_no")
    private String customerOrderNo;

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
}
