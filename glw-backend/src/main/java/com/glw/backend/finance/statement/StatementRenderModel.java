package com.glw.backend.finance.statement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 对账单渲染模型：所有客户模板共用这一份字段契约。
 * 模板占位符只允许引用这里的字段，客户差异体现在模板文件里，而不是代码分支。
 */
public record StatementRenderModel(Company company, Partner partner, Header header,
                                   List<Line> items, Totals totals) {

    /** 我方抬头，来自 app.finance.company 配置。 */
    public record Company(String name, String taxNo, String bankName, String bankAccount,
                          String phone, String contact) {}

    /** 往来单位信息，联系人/电话来自客户或供应商档案。 */
    public record Partner(String name, String contact, String phone) {}

    public record Header(String statementNo, String direction, String period, String periodLabel,
                         LocalDate periodStart, LocalDate periodEnd,
                         LocalDate statementDate, String statementDateLabel,
                         LocalDate dueDate, String dueDateLabel,
                         String currency, String currencyLabel, String taxRateLabel,
                         String status, String remark) {}

    public record Line(int seq, LocalDate bizDate, String bizDateText,
                       LocalDate dueDate, String dueDateText,
                       String sourceNo, String orderNo, String partnerOrderNo,
                       String brand, String partNo, String packageType,
                       int quantity, String unit,
                       BigDecimal unitPrice, BigDecimal unitPriceNet,
                       Integer taxIncluded, Integer taxRate,
                       BigDecimal amountGross, BigDecimal amountNet, BigDecimal taxAmount,
                       String remark) {}

    public record Totals(int totalQuantity, BigDecimal totalGross,
                         BigDecimal totalNet, BigDecimal totalTax) {}
}
