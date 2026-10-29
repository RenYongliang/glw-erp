package com.glw.backend.finance.statement;

import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;
import com.glw.backend.config.FinanceStatementProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 对账单模板文件存储。
 * file_key 以 classpath: 开头时读取随应用发布的样例模板，否则读取模板目录下的文件。
 */
@Component
public class StatementTemplateStorage {
    private static final String CLASSPATH = "classpath:";
    private final Path root;

    public StatementTemplateStorage(FinanceStatementProperties properties) {
        String dir = properties.templateDir();
        if (dir == null || dir.isBlank()) dir = System.getProperty("user.home") + "/glw/statement-templates";
        this.root = Paths.get(dir).toAbsolutePath().normalize();
    }

    public byte[] read(String fileKey) {
        try {
            if (fileKey.startsWith(CLASSPATH)) {
                try (InputStream in = new ClassPathResource(fileKey.substring(CLASSPATH.length())).getInputStream()) {
                    return in.readAllBytes();
                }
            }
            Path file = resolve(fileKey);
            if (!Files.isRegularFile(file)) throw new BusinessException(ErrorCode.NOT_FOUND, "对账单模板文件不存在");
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取对账单模板失败");
        }
    }

    /** 保存上传的模板文件，返回 file_key。 */
    public String store(byte[] content, String extension) {
        try {
            Files.createDirectories(root);
            String key = UUID.randomUUID() + extension;
            Files.write(resolve(key), content);
            return key;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存对账单模板失败");
        }
    }

    private Path resolve(String fileKey) {
        Path file = root.resolve(fileKey).normalize();
        if (!file.startsWith(root)) throw new BusinessException(ErrorCode.BAD_REQUEST, "模板文件路径不合法");
        return file;
    }
}
