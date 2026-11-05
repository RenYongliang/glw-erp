package com.glw.backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/** 部门新增/修改入参；parentId 为空或 0 表示顶级部门。 */
@Data
public class DepartmentInput {
    @NotBlank(message = "部门名称不能为空")
    @Size(max = 64, message = "部门名称不能超过 64 个字符")
    private String name;

    private Long parentId;

    /** 部门负责人用户ID，可为空。 */
    private Long leaderId;

    private Integer sortNo;

    /** 缺省为启用。 */
    private Boolean enabled;

    @Size(max = 255, message = "备注不能超过 255 个字符")
    private String remark;
}
