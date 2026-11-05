package com.glw.backend.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/** 附件对象存储配置；桶保持私有，下载使用短时效预签名地址。 */
@Validated
@ConfigurationProperties("app.minio")
public record MinioProperties(
        @NotBlank String endpoint,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @NotBlank String bucket,
        @NotNull Duration presignTtl,
        @NotNull DataSize maxFileSize,
        @NotEmpty List<String> allowedExtensions) {}
