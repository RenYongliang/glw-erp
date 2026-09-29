package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.glw.backend.model.enums.TaxIncluded;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 客户拜访记录表（customer_visit）。 */
@Data
@TableName(
        value = "customer_visit",
        resultMap = "com.glw.backend.mapper.CustomerVisitMapper.BaseResultMap")
public class CustomerVisit {
    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 客户id。 */
    @TableField("customer_id")
    private Long customerId;

    /** 拜访时间。 */
    @TableField("visit_time")
    private LocalDateTime visitTime;

    /** 客户项目应用。 */
    @TableField("project")
    private String project;

    /** 客户项目原来用的物料型号。 */
    @TableField("original_part_no")
    private String originalPartNo;

    /** 推荐产品id。 */
    @TableField("product_id")
    private Long productId;

    /** 客户项目用量。 */
    @TableField("demand_quantity")
    private Integer demandQuantity;

    /** 产品报价。 */
    @TableField("unit_price")
    private BigDecimal unitPrice;

    /** 是否含税 0未税 1含税。 */
    @TableField("tax_included")
    private TaxIncluded taxIncluded;

    /** 进度。 */
    @TableField("progress")
    private String progress;

    /** 详细内容。 */
    @TableField("detail")
    private String detail;

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
    @TableField("created_by")
    private Long createdBy;

    /** 更新人。 */
    @TableField("updated_by")
    private Long updatedBy;
}
