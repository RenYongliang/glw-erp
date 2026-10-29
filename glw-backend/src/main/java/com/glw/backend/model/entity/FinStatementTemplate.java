package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** fin_statement_template：往来单位维度的对账单版式，文件本身存磁盘/对象存储。 */
@Data
@TableName(value = "fin_statement_template", resultMap = "com.glw.backend.mapper.FinStatementTemplateMapper.BaseResultMap")
public class FinStatementTemplate {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("direction")
    private String direction;

    /** STATEMENT / SALES_CONTRACT / PURCHASE_CONTRACT / DELIVERY_NOTE */
    @TableField("doc_type")
    private String docType;

    /** 0 表示全局默认模板。 */
    @TableField("partner_id")
    private Long partnerId;

    /** XLSX / PDF */
    @TableField("format")
    private String format;

    @TableField("name")
    private String name;

    @TableField("version")
    private Integer version;

    /** 模板目录下的文件键；classpath: 前缀表示随应用发布的内置模板。 */
    @TableField("file_key")
    private String fileKey;

    @TableField("file_name")
    private String fileName;

    /** 可视化设计器保存的画布 JSON；为空表示传统 Excel 文件模板。 */
    @TableField("layout_json")
    private String layoutJson;

    @TableField("is_default")
    private Integer isDefault;

    @TableField("enabled")
    private Integer enabled;

    /** 软删除标记；列表和解析都只使用未删除的模板。 */
    @TableField("deleted")
    private Integer deleted;

    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}
