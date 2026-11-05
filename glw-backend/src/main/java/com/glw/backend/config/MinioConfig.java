package com.glw.backend.config;

import io.minio.MinioClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** MinIO 客户端；连接在首次上传/下载时建立，不阻塞应用启动。 */
@Configuration
public class MinioConfig {
    @Bean
    public MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }
}
