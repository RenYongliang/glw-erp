package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.glw.backend.model.enums.OutboundStatus;
import com.glw.backend.model.enums.OutboundType;

import lombok.Data;

import java.time.LocalDateTime;

/** 出库表（outbound_order）。 */
@Data
@TableName(
        value = "outbound_order",
        resultMap = "com.glw.backend.mapper.OutboundOrderMapper.BaseResultMap")
public class OutboundOrder {
    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 出库单编号。 */
    @TableField("outbound_order_no")
    private String outboundOrderNo;

    /** 销售订单id。 */
    @TableField("sales_order_id")
    private Long salesOrderId;

    /** 客户采购单编号。 */
    @TableField("customer_order_no")
    private String customerOrderNo;

    /** 客户id。 */
    @TableField("customer_id")
    private Long customerId;

    /** 客户名称。 */
    @TableField("customer_name")
    private String customerName;

    /** 出库类型, SALE:销售出库。 */
    @TableField("outbound_type")
    private OutboundType outboundType;

    /** 状态, PENDING:待出库; COMPLETED:已出库;。 */
    @TableField("status")
    private OutboundStatus status;

    /** 出库时间。 */
    @TableField("outbound_time")
    private LocalDateTime outboundTime;

    /** 乐观锁版本号；新增默认 0，更新时携带查询得到的版本。 */
    @Version
    @TableField("version")
    private Integer version;

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
