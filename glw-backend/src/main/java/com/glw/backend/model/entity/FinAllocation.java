package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** fin_allocation persistence record. */
@Data
@TableName(value = "fin_allocation", resultMap = "com.glw.backend.mapper.FinAllocationMapper.BaseResultMap")
public class FinAllocation {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("request_key")
    private String requestKey;

    @TableField("payment_id")
    private Long paymentId;

    @TableField("open_item_id")
    private Long openItemId;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("status")
    private String status;

    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField("reversed_at")
    private LocalDateTime reversedAt;
}
