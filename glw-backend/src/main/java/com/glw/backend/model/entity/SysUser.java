package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String passwordHash;
    private String name;
    private String role;
    private String department;
    private Boolean enabled;
    @TableLogic private Integer deleted;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long value) {
        this.id = value;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String value) {
        this.username = value;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String value) {
        this.passwordHash = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String value) {
        this.name = value;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String value) {
        this.role = value;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String value) {
        this.department = value;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean value) {
        this.enabled = value;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer value) {
        this.deleted = value;
    }

    public java.time.LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.LocalDateTime value) {
        this.createdAt = value;
    }

    public java.time.LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(java.time.LocalDateTime value) {
        this.updatedAt = value;
    }
}
