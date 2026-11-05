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

/** 供应商品牌表（supplier_brand）：一个供应商可以有多个品牌。 */
@Data
@TableName(value = "supplier_brand", resultMap = "com.glw.backend.mapper.SupplierBrandMapper.BaseResultMap")
public class SupplierBrand {
    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 供应商id。 */
    @TableField("supplier_id")
    private Long supplierId;

    /** 品牌id；指向品牌主数据 brand.id。 */
    @TableField("brand_id")
    private Long brandId;

    /** 备注。 */
    @TableField("remark")
    private String remark;

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
