package com.glw.backend.model.vo;

import lombok.Data;

/** 附件列表项；downloadUrl 为短时效预签名地址，不落库。 */
@Data
public class AttachmentVO {
    private Long id;
    private String bizType;
    private Long bizId;
    private String category;
    private String categoryLabel;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private String downloadUrl;
    private String createdAt;
}
