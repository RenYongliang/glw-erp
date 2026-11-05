package com.glw.backend.controller;

import com.glw.backend.common.ApiResponse;
import com.glw.backend.model.vo.AttachmentVO;
import com.glw.backend.service.BizAttachmentService;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 订单附件接口：上传、列表、删除。 */
@RestController
@RequestMapping("/api/attachment")
@AllArgsConstructor
@Profile("!mock")
public class BizAttachmentController {
    private final BizAttachmentService attachmentService;

    @GetMapping
    public ApiResponse<List<AttachmentVO>> list(
            @RequestParam String bizType, @RequestParam Long bizId) {
        return ApiResponse.ok(attachmentService.listAttachments(bizType, bizId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AttachmentVO> upload(
            @RequestParam String bizType,
            @RequestParam Long bizId,
            @RequestParam String category,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(attachmentService.upload(bizType, bizId, category, file));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        attachmentService.deleteAttachment(id);
        return ApiResponse.ok(null);
    }
}
