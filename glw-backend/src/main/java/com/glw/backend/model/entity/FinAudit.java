package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.Data;
import java.time.LocalDateTime;

/** fin_audit persistence record. */
@Data
@TableName(value = "fin_audit", resultMap = "com.glw.backend.mapper.FinAuditMapper.BaseResultMap")
public class FinAudit {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("entity_type")
    private String entityType;

    @TableField("entity_id")
    private Long entityId;

    @TableField("action")
    private String action;

    @TableField("actor_id")
    private Long actorId;

    @TableField("detail")
    private String detail;

    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}
