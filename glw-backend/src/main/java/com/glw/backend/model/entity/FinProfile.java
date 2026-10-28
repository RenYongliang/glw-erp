package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.Data;
import java.time.LocalDateTime;

/** fin_profile persistence record. */
@Data
@TableName(value = "fin_profile", resultMap = "com.glw.backend.mapper.FinProfileMapper.BaseResultMap")
public class FinProfile {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("direction")
    private String direction;

    @TableField("partner_id")
    private Long partnerId;

    @TableField("term_days")
    private Integer termDays;

    @TableField("term_basis")
    private String termBasis;

    @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
