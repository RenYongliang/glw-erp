package com.glw.backend.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.glw.backend.model.entity.BizAttachment;
import com.glw.backend.model.vo.AttachmentVO;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 业务附件服务：文件存 MinIO，元数据存 biz_attachment。 */
public interface BizAttachmentService extends IService<BizAttachment> {
    /** 查询某张业务单据下未删除的附件。 */
    List<AttachmentVO> listAttachments(String bizType, Long bizId);

    /** 上传附件并生成统一文件名；固定类型只允许一份。 */
    AttachmentVO upload(String bizType, Long bizId, String category, MultipartFile file);

    /** 软删除附件记录，保留对象存储中的原始文件。 */
    void deleteAttachment(Long id);
}
