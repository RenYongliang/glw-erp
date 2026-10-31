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

    /** 截止日 1-31；NULL 表示按自然月切分。 */
    @TableField("cutoff_day")
    private Integer cutoffDay;

    /** 账单周期长度（月）。 */
    @TableField("cycle_months")
    private Integer cycleMonths;

    /** 出单月 = 账单结束月 + N。 */
    @TableField("statement_lag_months")
    private Integer statementLagMonths;

    /** 出单日（1-31）。 */
    @TableField("statement_day")
    private Integer statementDay;

    /** 到期日算法：CREDIT_DAYS / CALENDAR。 */
    @TableField("due_rule")
    private String dueRule;

    /** 固定天数到期算法的天数。 */
    @TableField("credit_days")
    private Integer creditDays;

    /** 固定天数到期算法的起算点。 */
    @TableField("credit_basis")
    private String creditBasis;

    /** CALENDAR：出单月后第 N 个月。 */
    @TableField("due_month_offset")
    private Integer dueMonthOffset;

    /** CALENDAR：第几天，0 表示月末。 */
    @TableField("due_day")
    private Integer dueDay;

    /** 是否自动生成对账单。 */
    @TableField("auto_generate")
    private Integer autoGenerate;

    /** 对账依据：AR 可为 SIGNED/INBOUND，AP 固定 INBOUND。 */
    @TableField("reconcile_basis")
    private String reconcileBasis;

    /** 方案来源：DEFAULT=复制公司默认方案（只读），CUSTOM=自定义。 */
    @TableField("plan_source")
    private String planSource;

    @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
