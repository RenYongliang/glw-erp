package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import lombok.Data;

import java.time.LocalDateTime;

/** 部门表（sys_department）：parent_id=0 表示顶级部门。 */
@Data
@TableName("sys_department")
public class SysDepartment {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 部门名称；同一上级下唯一。 */
    @TableField("name")
    private String name;

    /** 上级部门ID；0 表示顶级。 */
    @TableField("parent_id")
    private Long parentId;

    /** 层级，顶级为 1。 */
    @TableField("level")
    private Integer level;

    /** 部门负责人，指向 sys_user.id。 */
    @TableField("leader_id")
    private Long leaderId;

    /** 同级排序。 */
    @TableField("sort_no")
    private Integer sortNo;

    /** 是否启用；停用后不出现在选择列表。 */
    @TableField("enabled")
    private Boolean enabled;

    @TableField("remark")
    private String remark;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    @Version
    @TableField("version")
    private Integer version;

    @TableField(
            value = "created_at",
            insertStrategy = FieldStrategy.NEVER,
            updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(
            value = "updated_at",
            insertStrategy = FieldStrategy.NEVER,
            updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;

    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
}
