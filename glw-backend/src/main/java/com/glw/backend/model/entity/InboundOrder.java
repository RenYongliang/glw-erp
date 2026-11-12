package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.glw.backend.model.enums.InboundStatus;
import com.glw.backend.model.enums.InboundType;

import lombok.Data;

import java.time.LocalDateTime;

/** 入库表（inbound_order）。 */
@Data
@TableName(
        value = "inbound_order",
        resultMap = "com.glw.backend.mapper.InboundOrderMapper.BaseResultMap")
public class InboundOrder {
    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 一次实际收货的入库单号。 */
    @TableField("inbound_order_no")
    private String inboundOrderNo;

    /** 供应商id。 */
    @TableField("supplier_id")
    private Long supplierId;

    /** 供应商名称。 */
    @TableField("supplier_name")
    private String supplierName;

    /** 入库类型, PURCHASE:采购入库。 */
    @TableField("inbound_type")
    private InboundType inboundType;

    /** 仓库编码；当前固定 SZYT。 */
    @TableField("warehouse_code")
    private String warehouseCode;

    /** 仓库名称快照；当前固定深圳盐田仓。 */
    @TableField("warehouse_name")
    private String warehouseName;

    /** 创建幂等键；重复提交时返回原入库单。 */
    @TableField("request_key")
    private String requestKey;

    /** 状态, PENDING:待入库; COMPLETED:已入库;。 */
    @TableField("status")
    private InboundStatus status;

    /** 入库时间。 */
    @TableField("inbound_time")
    private LocalDateTime inboundTime;

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

    @TableField(exist = false)
    private String inboundTimeStart;

    @TableField(exist = false)
    private String inboundTimeEnd;
}
