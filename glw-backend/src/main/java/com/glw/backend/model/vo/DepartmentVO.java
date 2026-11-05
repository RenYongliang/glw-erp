package com.glw.backend.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 部门树节点。 */
@Data
public class DepartmentVO {
    private Long id;
    private String name;
    private Long parentId;
    private Integer level;
    private Long leaderId;
    private String leaderName;
    private Integer sortNo;
    private Boolean enabled;
    private String remark;
    private List<DepartmentVO> children = new ArrayList<>();
}
