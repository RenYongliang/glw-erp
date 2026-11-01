package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import lombok.Data;

import java.time.LocalDateTime;

/** 供应商表（supplier）。 */
@Data
@TableName(value = "supplier", resultMap = "com.glw.backend.mapper.SupplierMapper.BaseResultMap")
public class Supplier {
    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 供应商编码；全局唯一，便于采购/对账引用。 */
    @TableField("code")
    private String code;

    /** 供应商名称。 */
    @TableField("name")
    private String name;

    /** 供应商简称。 */
    @TableField("short_name")
    private String shortName;

    /** 供应商分类，如原厂、代理、贸易。 */
    @TableField("category")
    private String category;

    /** 是否启用；停用后不再出现在业务选择列表中。 */
    @TableField("enabled")
    private Boolean enabled;

    /** 供应商地址。 */
    @TableField("address")
    private String address;

    /** 联系人。 */
    @TableField("contact")
    private String contact;

    /** 联系人手机/座机。 */
    @TableField("contact_phone")
    private String contactPhone;

    /** 税号。 */
    @TableField("tax_no")
    private String taxNo;

    /** 开户行。 */
    @TableField("bank_name")
    private String bankName;

    /** 银行账号。 */
    @TableField("bank_account")
    private String bankAccount;

    /** 开户户名。 */
    @TableField("account_name")
    private String accountName;

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
    @TableField("created_by")
    private Long createdBy;

    /** 更新人。 */
    @TableField("updated_by")
    private Long updatedBy;
}
