package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** fin_statement persistence record. */
@Data
@TableName(value = "fin_statement", resultMap = "com.glw.backend.mapper.FinStatementMapper.BaseResultMap")
public class FinStatement {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("statement_no")
    private String statementNo;

    @TableField("direction")
    private String direction;

    @TableField("partner_id")
    private Long partnerId;

    @TableField("partner_name")
    private String partnerName;

    @TableField("period")
    private String period;

    @TableField("currency")
    private String currency;

    @TableField("status")
    private String status;

    @TableField("gross_amount")
    private BigDecimal grossAmount;

    @TableField("remark")
    private String remark;

    @TableField("sent_at")
    private LocalDateTime sentAt;

    @TableField("confirmed_at")
    private LocalDateTime confirmedAt;

    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}
