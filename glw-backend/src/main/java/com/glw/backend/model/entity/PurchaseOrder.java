package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 采购订单表（purchase_order）。 */
@TableName(
        value = "purchase_order",
        resultMap = "com.glw.backend.mapper.PurchaseOrderMapper.BaseResultMap")
public class PurchaseOrder {
    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 采购单编号。 */
    @TableField("purchase_order_no")
    private String purchaseOrderNo;

    /** 下单时间。 */
    @TableField("order_time")
    private LocalDateTime orderTime;

    /** 供应商id。 */
    @TableField("supplier_id")
    private Long supplierId;

    /** 供应商名称。 */
    @TableField("supplier_name")
    private String supplierName;

    /** 交付日期。 */
    @TableField("delivery_time")
    private LocalDateTime deliveryTime;

    /** 订单状态, PENDING_RECEIPT:待收货; PARTIALLY_RECEIVED:部分收货; COMPLETED:已完成。 */
    @TableField("order_status")
    private String orderStatus;

    /** 付款状态, UNPAID:待付款; PARTIALLY_PAID:部分付款; PAID:已付款。 */
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

    public String getPurchaseOrderNo() {
        return purchaseOrderNo;
    }

    public void setPurchaseOrderNo(String value) {
        this.purchaseOrderNo = value;
    }

    public LocalDateTime getOrderTime() {
        return orderTime;
    }

    public void setOrderTime(LocalDateTime value) {
        this.orderTime = value;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long value) {
        this.supplierId = value;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String value) {
        this.supplierName = value;
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
