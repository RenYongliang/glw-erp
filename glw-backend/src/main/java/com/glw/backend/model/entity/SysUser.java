package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;
import lombok.ToString;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    @ToString.Exclude private String passwordHash;
    private String name;
    private String role;
    private String department;
    private Boolean enabled;
    @TableLogic private Integer deleted;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
}
