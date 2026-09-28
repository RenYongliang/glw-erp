package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 样品申请表（sample_apply）。 */
@TableName(
        value = "sample_apply",
        resultMap = "com.glw.backend.mapper.SampleApplyMapper.BaseResultMap")
public class SampleApply {
    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 客户id。 */
    @TableField("customer_id")
    private Long customerId;

    /** 申请时间。 */
    @TableField("apply_time")
    private LocalDateTime applyTime;

    /** 客户项目应用。 */
    @TableField("project")
    private String project;

    /** 送样产品id。 */
    @TableField("product_id")
    private Long productId;

    /** 送样数量。 */
    @TableField("quantity")
    private Integer quantity;

    /** 是否送样 0未送样 1已送样。 */
    @TableField("is_delivered")
    private Integer isDelivered;

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

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long value) {
        this.customerId = value;
    }

    public LocalDateTime getApplyTime() {
        return applyTime;
    }

    public void setApplyTime(LocalDateTime value) {
        this.applyTime = value;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String value) {
        this.project = value;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long value) {
        this.productId = value;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer value) {
        this.quantity = value;
    }

    public Integer getIsDelivered() {
        return isDelivered;
    }

    public void setIsDelivered(Integer value) {
        this.isDelivered = value;
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
