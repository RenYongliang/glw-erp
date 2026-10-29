package com.glw.backend.config;

import com.glw.backend.finance.statement.DocumentType;
import com.glw.backend.service.FinanceStatementTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 启动时确保每类单据都有一份系统全局默认模板。
 *
 * 模板是可视化布局，用户可以直接编辑；补种幂等，已有模板的维度不会重复插入。
 */
@Component
@Profile("!mock")
@RequiredArgsConstructor
public class DocumentTemplateSeeder implements ApplicationRunner {
    private final FinanceStatementTemplateService templateService;

    @Override
    public void run(ApplicationArguments args) {
        for (DocumentType type : DocumentType.values()) {
            if (type.directional()) {
                templateService.ensureDefault(type.name(), "AR");
                templateService.ensureDefault(type.name(), "AP");
            } else {
                templateService.ensureDefault(type.name(), null);
            }
        }
    }
}
