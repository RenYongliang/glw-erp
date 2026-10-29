package com.glw.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 对账单模板目录、默认单位和我方抬头信息。 */
@ConfigurationProperties("app.finance")
public record FinanceStatementProperties(String templateDir, String defaultUnit, Company company,
                                         String pdfFont) {

    public record Company(String name, String taxNo, String bankName, String bankAccount,
                          String phone, String contact) {}
}
