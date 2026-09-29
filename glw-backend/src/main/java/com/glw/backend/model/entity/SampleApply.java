package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.glw.backend.model.enums.SampleDeliveryStatus;

import lombok.Data;

import java.time.LocalDateTime;

/** 样品申请表（sample_apply）。 */
@Data
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
    private SampleDeliveryStatus isDelivered;

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
