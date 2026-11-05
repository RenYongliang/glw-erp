package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
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

    /** 客户签收日期，用于计算应收的账单归属与账期。 */
    @TableField("sign_time")
    private LocalDateTime signTime;

    @TableField("shipping_address")
    private String shippingAddress;
    @TableField("shipping_recipient")
    private String shippingRecipient;
    @TableField("shipping_phone")
    private String shippingPhone;
    @TableField("shipping_sender")
    private String shippingSender;
    @TableField("shipping_sender_phone")
    private String shippingSenderPhone;
    @TableField("shipping_method")
    private String shippingMethod;
    @TableField("shipping_carrier")
    private String shippingCarrier;
    @TableField("shipping_tracking_number")
    private String shippingTrackingNumber;
    @TableField("shipping_purchase_number")
    private String shippingPurchaseNumber;
    @TableField("shipping_remark")
    private String shippingRemark;

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
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private Long createdBy;

    /** 更新人。 */
    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
}
