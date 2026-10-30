package com.glw.backend.finance;

import com.glw.backend.service.FinanceStatementService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每天按客户结算档案自动生成到期对账单（草稿态，需人工发送）。 */
@Component
@Profile("!mock")
@RequiredArgsConstructor
public class FinanceStatementScheduler {
    private static final Logger log = LoggerFactory.getLogger(FinanceStatementScheduler.class);

    private final FinanceStatementService statementService;

    @Scheduled(cron = "${app.finance.auto-statement-cron:0 40 1 * * ?}")
    public void generateStatements() {
        try {
            int created = statementService.autoGenerate();
            if (created > 0) log.info("自动生成对账单 {} 张", created);
        } catch (RuntimeException ex) {
            // 定时任务自行兜底，避免异常中断调度线程。
            log.error("自动生成对账单失败", ex);
        }
    }
}
