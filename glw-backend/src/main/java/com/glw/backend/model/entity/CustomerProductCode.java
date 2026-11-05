package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import lombok.Data;

import java.time.LocalDateTime;

/** 客户物料编码对照表（customer_product_code）。 */
@Data
@TableName(
        value = "customer_product_code",
        resultMap = "com.glw.backend.mapper.CustomerProductCodeMapper.BaseResultMap")
public class CustomerProductCode {
    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 客户id。 */
    @TableField("customer_id")
    private Long customerId;

    /** 产品id。 */
    @TableField("product_id")
    private Long productId;

    /** 客户系统内的物料编码。 */
    @TableField("customer_code")
    private String customerCode;

    /** 来源：MANUAL / PURCHASE_CONTRACT / IMPORT。 */
    @TableField("source")
    private String source;

    /** 是否启用。 */
    @TableField("enabled")
    private Boolean enabled;

    /** 备注。 */
    @TableField("remark")
    private String remark;

    /** 乐观锁版本号。 */
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
