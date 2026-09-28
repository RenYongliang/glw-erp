package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 销售订单商品表（sales_order_item）。 */
@TableName(
        value = "sales_order_item",
        resultMap = "com.glw.backend.mapper.SalesOrderItemMapper.BaseResultMap")
public class SalesOrderItem {
    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 销售订单id。 */
    @TableField("sales_order_id")
    private Long salesOrderId;

    /** 客户采购单号。 */
    @TableField("customer_order_no")
    private String customerOrderNo;

    /** 产品id。 */
    @TableField("product_id")
    private Long productId;

    /** 品牌。 */
    @TableField("brand")
    private String brand;

    /** 型号。 */
    @TableField("part_no")
    private String partNo;

    /** 封装。 */
    @TableField("package_type")
    private String packageType;

    /** 单价。 */
    @TableField("unit_price")
    private BigDecimal unitPrice;

    /** 是否含税 0未税 1含税。 */
    @TableField("tax_included")
    private Integer taxIncluded;

    /** 增值税税率。 */
    @TableField("tax_rate")
    private Integer taxRate;

    /** 数量。 */
    @TableField("quantity")
    private Integer quantity;

    /** 金额。 */
    @TableField("amount")
    private BigDecimal amount;

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

    public Long getId() {
        return id;
    }

    public void setId(Long value) {
        this.id = value;
    }

    public Long getSalesOrderId() {
        return salesOrderId;
    }

    public void setSalesOrderId(Long value) {
        this.salesOrderId = value;
    }

    public String getCustomerOrderNo() {
        return customerOrderNo;
    }

    public void setCustomerOrderNo(String value) {
        this.customerOrderNo = value;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long value) {
        this.productId = value;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String value) {
        this.brand = value;
    }

    public String getPartNo() {
        return partNo;
    }

    public void setPartNo(String value) {
        this.partNo = value;
    }

    public String getPackageType() {
        return packageType;
    }

    public void setPackageType(String value) {
        this.packageType = value;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal value) {
        this.unitPrice = value;
    }

    public Integer getTaxIncluded() {
        return taxIncluded;
    }

    public void setTaxIncluded(Integer value) {
        this.taxIncluded = value;
    }

    public Integer getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(Integer value) {
        this.taxRate = value;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer value) {
        this.quantity = value;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal value) {
        this.amount = value;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime value) {
        this.createdAt = value;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime value) {
        this.updatedAt = value;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long value) {
        this.createdBy = value;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long value) {
        this.updatedBy = value;
    }
}
