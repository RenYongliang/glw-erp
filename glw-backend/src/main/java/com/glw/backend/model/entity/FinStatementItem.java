package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/** fin_statement_item persistence record. */
@Data
@TableName(value = "fin_statement_item", resultMap = "com.glw.backend.mapper.FinStatementItemMapper.BaseResultMap")
public class FinStatementItem {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("statement_id")
    private Long statementId;

    @TableField("open_item_id")
    private Long openItemId;

    @TableField("source_no")
    private String sourceNo;

    @TableField("order_no")
    private String orderNo;

    @TableField("partner_order_no")
    private String partnerOrderNo;

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

    @TableField("biz_date")
    private LocalDate bizDate;

    @TableField("due_date")
    private LocalDate dueDate;
}
