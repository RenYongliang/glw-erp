package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 库存表（inventory）。 */
@Data
@TableName(value = "inventory", resultMap = "com.glw.backend.mapper.InventoryMapper.BaseResultMap")
public class Inventory {
    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 产品id。 */
    @TableField("product_id")
    private Long productId;

    /** 仓库编码；当前固定 SZYT。 */
    @TableField("warehouse_code")
    private String warehouseCode;

    /** 仓库名称快照；当前固定深圳盐田仓。 */
    @TableField("warehouse_name")
    private String warehouseName;

    /** 原厂日期码。 */
    @TableField("date_code")
    private String dateCode;

    /** 原厂批次号。 */
    @TableField("lot_code")
    private String lotCode;

    /** 库存批次号：BAT + 实际入库日期yyMMdd + 五位产品ID + 同产品当天三位流水号。 */
    @TableField("batch_no")
    private String batchNo;

    /** 来源入库明细id。 */
    @TableField("inbound_order_item_id")
    private Long inboundOrderItemId;

    /** 批次含税单位成本，保留四位小数。 */
    @TableField("unit_cost")
    private BigDecimal unitCost;

    /** 初始入库数量。 */
    @TableField("initial_quantity")
    private Integer initialQuantity;

    /** 当前库存数量。 */
    @TableField("quantity")
    private Integer quantity;

    /** 已预占数量（已下单未发货），数据库默认值为 0。 */
    @TableField("reserved_quantity")
    private Integer reservedQuantity;

    /** 乐观锁版本号；新增默认 0，更新时携带查询得到的版本。 */
    @Version
    @TableField("version")
    private Integer version;

    /** 创建时间。 */
    @TableField(
            value = "created_at",
            insertStrategy = FieldStrategy.NEVER,
            updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    /** 更新时间。 */
    @TableField(
            value = "updated_at",
            insertStrategy = FieldStrategy.NEVER,
            updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;

    /** 创建人。 */
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private Long createdBy;

    /** 更新人。 */
    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
}
