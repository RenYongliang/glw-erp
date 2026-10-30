package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** fin_open_item persistence record. */
@Data
@TableName(value = "fin_open_item", resultMap = "com.glw.backend.mapper.FinOpenItemMapper.BaseResultMap")
public class FinOpenItem {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("direction")
    private String direction;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_id")
    private Long sourceId;

    @TableField("source_line_id")
    private Long sourceLineId;

    @TableField("source_no")
    private String sourceNo;

    @TableField("order_id")
    private Long orderId;

    @TableField("order_no")
    private String orderNo;

    @TableField("partner_order_no")
    private String partnerOrderNo;

    @TableField("partner_id")
    private Long partnerId;

    @TableField("partner_name")
    private String partnerName;

    @TableField("product_id")
    private Long productId;

    @TableField("brand")
    private String brand;

    @TableField("part_no")
    private String partNo;

    @TableField("package_type")
    private String packageType;

    @TableField("quantity")
    private Integer quantity;

    @TableField("unit_price")
    private BigDecimal unitPrice;

    @TableField("tax_included")
    private Integer taxIncluded;

    @TableField("tax_rate")
    private Integer taxRate;

    @TableField("net_amount")
    private BigDecimal netAmount;

    @TableField("tax_amount")
    private BigDecimal taxAmount;

    @TableField("gross_amount")
    private BigDecimal grossAmount;

    @TableField("currency")
    private String currency;

    @TableField("biz_date")
    private LocalDate bizDate;

    /** 结算基准日：应收取客户签收日，应付取入库日。 */
    @TableField("basis_date")
    private LocalDate basisDate;

    /** 基准是否就绪，0=待签收。 */
    @TableField("basis_ready")
    private Integer basisReady;

    @TableField("period")
    private String period;

    @TableField("period_start")
    private LocalDate periodStart;

    @TableField("period_end")
    private LocalDate periodEnd;

    @TableField("settle_month")
    private String settleMonth;

    @TableField("due_date")
    private LocalDate dueDate;

    @TableField("reconcile_status")
    private String reconcileStatus;

    @TableField("statement_id")
    private Long statementId;

    @TableField("settled_amount")
    private BigDecimal settledAmount;

    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}
