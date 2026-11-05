package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.time.LocalDateTime;

/** 业务附件表（biz_attachment）：记录附件与业务单据的归属及 MinIO 对象位置。 */
@Data
@TableName(
        value = "biz_attachment",
        resultMap = "com.glw.backend.mapper.BizAttachmentMapper.BaseResultMap")
public class BizAttachment {
    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 业务类型：SALES_ORDER/PURCHASE_ORDER。 */
    @TableField("biz_type")
    private String bizType;

    /** 业务主键ID，如 sales_order.id、purchase_order.id。 */
    @TableField("biz_id")
    private Long bizId;

    /** 附件类型编码，如 SALES_CONTRACT、DELIVERY_RECEIPT。 */
    @TableField("category")
    private String category;

    /** 展示与下载文件名；由服务端按统一规则生成。 */
    @TableField("file_name")
    private String fileName;

    /** MinIO 桶名。 */
    @TableField("bucket")
    private String bucket;

    /** MinIO 桶内对象路径，替代完整 URL。 */
    @TableField("object_key")
    private String objectKey;

    /** 文件字节数。 */
    @TableField("file_size")
    private Long fileSize;

    /** 文件 MIME 类型。 */
    @TableField("content_type")
    private String contentType;

    /** 软删除标记；列表只返回未删除的附件。 */
    @TableField("is_removed")
    private Boolean isRemoved;

    /** 上传人。 */
    @TableField("created_by")
    private Long createdBy;

    /** 上传时间。 */
    @TableField(
            value = "created_at",
            insertStrategy = FieldStrategy.NEVER,
            updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}
