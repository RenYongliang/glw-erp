package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 销售订单表（sales_order）。 */
@TableName(
        value = "sales_order",
        resultMap = "com.glw.backend.mapper.SalesOrderMapper.BaseResultMap")
public class SalesOrder {
    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 客户采购单号。 */
    @TableField("customer_order_no")
    private String customerOrderNo;

    /** 下单时间。 */
    @TableField("order_time")
    private LocalDateTime orderTime;

    /** 客户id。 */
    @TableField("customer_id")
    private Long customerId;

    /** 客户名称。 */
    @TableField("customer_name")
    private String customerName;

    /** 结算类型, CASH:现金; MONTHLY:月结; PREPAID:预付; COD:货到付款。 */
    @TableField("settle_type")
    private String settleType;

    /** 交付日期。 */
    @TableField("delivery_time")
    private LocalDateTime deliveryTime;

    /** 订单状态, PENDING_SHIPMENT:待发货; PARTIALLY_SHIPPED:部分发货; COMPLETED:已完成。 */
    @TableField("order_status")
    private String orderStatus;

    /** 收款状态, UNPAID:待收款; PARTIALLY_PAID:部分收款; PAID:已收款。 */
    @TableField("payment_status")
    private String paymentStatus;

    /** 备注。 */
    @TableField("remark")
    private String remark;

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

    public Long getId() {
        return id;
    }

    public void setId(Long value) {
        this.id = value;
    }

    public String getCustomerOrderNo() {
        return customerOrderNo;
    }

    public void setCustomerOrderNo(String value) {
        this.customerOrderNo = value;
    }

    public LocalDateTime getOrderTime() {
        return orderTime;
    }

    public void setOrderTime(LocalDateTime value) {
        this.orderTime = value;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long value) {
        this.customerId = value;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String value) {
        this.customerName = value;
    }

    public String getSettleType() {
        return settleType;
    }

    public void setSettleType(String value) {
        this.settleType = value;
    }

    public LocalDateTime getDeliveryTime() {
        return deliveryTime;
    }

    public void setDeliveryTime(LocalDateTime value) {
        this.deliveryTime = value;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String value) {
        this.orderStatus = value;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String value) {
        this.paymentStatus = value;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String value) {
        this.remark = value;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime value) {
        this.createdAt = value;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime value) {
        this.updatedAt = value;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long value) {
        this.createdBy = value;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long value) {
        this.updatedBy = value;
    }
}
