package com.glw.backend.storage;

import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;
import com.glw.backend.config.MinioProperties;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/** 附件对象存储；当前实现为 MinIO，接口保持简单以便将来替换。 */
@Component
public class AttachmentStorage {
    private final MinioClient minioClient;
    /** 生成下载地址时使用浏览器可访问的地址；上传/删除仍走内部地址。 */
    private final MinioClient presignClient;
    private final MinioProperties properties;

    private volatile boolean bucketReady;

    public AttachmentStorage(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
        String presignEndpoint = properties.presignEndpoint();
        this.presignClient = presignEndpoint.equals(properties.endpoint())
                ? minioClient
                : MinioClient.builder()
                        .endpoint(presignEndpoint)
                        .credentials(properties.accessKey(), properties.secretKey())
                        .build();
    }

    /** 上传文件并返回桶内对象键由调用方生成；桶不存在时自动创建（保持私有）。 */
    public void put(String bucket, String objectKey, String contentType, long size, InputStream stream) {
        try {
            ensureBucket(bucket);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .contentType(contentType)
                            .stream(stream, size, -1)
                            .build());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "附件上传失败，请稍后重试");
        }
    }

    /** 生成临时下载地址；不落库，避免对象存储地址变更后历史数据失效。 */
    public String presignedUrl(String bucket, String objectKey) {
        try {
            return presignClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry((int) properties.presignTtl().toSeconds(), TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "附件下载地址生成失败");
        }
    }

    /** 仅用于上传后落库失败时清理刚写入的对象。 */
    public void remove(String bucket, String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception ignored) {
            // 清理失败不覆盖原始异常，遗留对象由后续运维清理。
        }
    }

    private void ensureBucket(String bucket) throws Exception {
        if (bucketReady) return;
        synchronized (this) {
            if (bucketReady) return;
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            bucketReady = true;
        }
    }
}
