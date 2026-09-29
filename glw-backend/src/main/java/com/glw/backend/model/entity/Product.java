package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 产品信息表（product）。 */
@Data
@TableName(value = "product", resultMap = "com.glw.backend.mapper.ProductMapper.BaseResultMap")
public class Product {
    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 供应商id。 */
    @TableField("supplier_id")
    private Long supplierId;

    /** 品牌。 */
    @TableField("brand")
    private String brand;

    /** 型号。 */
    @TableField("part_no")
    private String partNo;

    /** 封装。 */
    @TableField("package_type")
    private String packageType;

    /** 基础价格。 */
    @TableField("base_price")
    private BigDecimal basePrice;

    /** 增值税税率。 */
    @TableField("tax_rate")
    private Integer taxRate;

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
