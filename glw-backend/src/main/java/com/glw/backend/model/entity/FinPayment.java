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

/** fin_payment persistence record. */
@Data
@TableName(value = "fin_payment", resultMap = "com.glw.backend.mapper.FinPaymentMapper.BaseResultMap")
public class FinPayment {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("payment_no")
    private String paymentNo;

    @TableField("request_key")
    private String requestKey;

    @TableField("direction")
    private String direction;

    /** 关联业务订单ID；从订单收付款时写入，用于预付款归集。 */
    @TableField("order_id")
    private Long orderId;

    /** NORMAL/预付款 PREPAYMENT。 */
    @TableField("payment_type")
    private String paymentType;

    @TableField("partner_id")
    private Long partnerId;

    @TableField("partner_name")
    private String partnerName;

    @TableField("currency")
    private String currency;

    @TableField("payment_date")
    private LocalDate paymentDate;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("allocated_amount")
    private BigDecimal allocatedAmount;

    @TableField("bank_reference")
    private String bankReference;

    @TableField("remark")
    private String remark;

    @TableField("status")
    private String status;

    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}
