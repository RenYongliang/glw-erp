package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 入库表（inbound_order）。 */
@TableName(
        value = "inbound_order",
        resultMap = "com.glw.backend.mapper.InboundOrderMapper.BaseResultMap")
public class InboundOrder {
    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 采购单id。 */
    @TableField("purchase_order_id")
    private Long purchaseOrderId;

    /** 采购单编号。 */
    @TableField("purchase_order_no")
    private String purchaseOrderNo;

    /** 供应商id。 */
    @TableField("supplier_id")
    private Long supplierId;

    /** 供应商名称。 */
    @TableField("supplier_name")
    private String supplierName;

    /** 入库类型, PURCHASE:采购入库。 */
    @TableField("inbound_type")
    private String inboundType;

    /** 状态, PENDING:待入库; COMPLETED:已入库;。 */
    @TableField("status")
    private String status;

    /** 入库时间。 */
    @TableField("inbound_time")
    private LocalDateTime inboundTime;

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

    public Long getPurchaseOrderId() {
        return purchaseOrderId;
    }

    public void setPurchaseOrderId(Long value) {
        this.purchaseOrderId = value;
    }

    public String getPurchaseOrderNo() {
        return purchaseOrderNo;
    }

    public void setPurchaseOrderNo(String value) {
        this.purchaseOrderNo = value;
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

    public String getInboundType() {
        return inboundType;
    }

    public void setInboundType(String value) {
        this.inboundType = value;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String value) {
        this.status = value;
    }

    public LocalDateTime getInboundTime() {
        return inboundTime;
    }

    public void setInboundTime(LocalDateTime value) {
        this.inboundTime = value;
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
