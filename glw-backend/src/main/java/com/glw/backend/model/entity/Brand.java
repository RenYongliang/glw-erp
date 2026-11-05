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

/** 品牌主数据表（brand）：品牌属于原厂，供应商通过 supplier_brand 关联可供品牌。 */
@Data
@TableName(value = "brand", resultMap = "com.glw.backend.mapper.BrandMapper.BaseResultMap")
public class Brand {
    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 品牌编码；全局唯一。 */
    @TableField("code")
    private String code;

    /** 品牌名称；全局唯一。 */
    @TableField("name")
    private String name;

    /** 原厂/生产厂商。 */
    @TableField("manufacturer")
    private String manufacturer;

    /** 是否启用；停用后不再出现在业务选择列表中。 */
    @TableField("enabled")
    private Boolean enabled;

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
