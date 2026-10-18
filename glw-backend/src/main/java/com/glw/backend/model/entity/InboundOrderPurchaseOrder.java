package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName(value = "inbound_order_purchase_order", resultMap = "com.glw.backend.mapper.InboundOrderPurchaseOrderMapper.BaseResultMap")
public class InboundOrderPurchaseOrder {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("inbound_order_id")
    private Long inboundOrderId;
    @TableField("purchase_order_id")
    private Long purchaseOrderId;
    @TableField("purchase_order_no")
    private String purchaseOrderNo;
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
    @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
