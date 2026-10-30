package com.glw.backend.finance.statement;

import com.glw.backend.config.FinanceStatementProperties;
import com.glw.backend.config.FinanceStatementProperties.Company;
import com.glw.backend.finance.FinanceModels.OpenItem;
import com.glw.backend.finance.FinanceModels.PartnerInfo;
import com.glw.backend.finance.FinanceModels.Statement;
import com.glw.backend.mapper.FinProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/** 把对账单快照转换成模板无关的渲染模型。 */
@Component
@Profile("!mock")
@RequiredArgsConstructor
public class StatementRenderModelAssembler {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy/M/d", Locale.CHINA);
    private static final DateTimeFormatter HEADER_DATE = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA);

    private final FinanceStatementProperties properties;
    private final FinProfileMapper finProfileMapper;

    public StatementRenderModel assemble(Statement statement, List<OpenItem> items) {
        return new StatementRenderModel(company(), partner(statement), header(statement, items),
                lines(items), totals(items));
    }

    private StatementRenderModel.Company company() {
        Company c = properties.company();
        if (c == null) return new StatementRenderModel.Company("", "", "", "", "", "");
        return new StatementRenderModel.Company(nz(c.name()), nz(c.taxNo()), nz(c.bankName()),
                nz(c.bankAccount()), nz(c.phone()), nz(c.contact()));
    }

    private StatementRenderModel.Partner partner(Statement statement) {
        PartnerInfo info = finProfileMapper.partnerInfo(statement.getDirection(), statement.getPartnerId());
        // 名称用对账单快照，往来单位改名不影响历史对账单；联系人和电话取最新档案。
        String name = statement.getPartnerName() != null && !statement.getPartnerName().isBlank()
                ? statement.getPartnerName() : info == null ? "" : nz(info.getName());
        if (info == null) return new StatementRenderModel.Partner(nz(name), "", "");
        return new StatementRenderModel.Partner(nz(name), nz(info.getContact()), nz(info.getPhone()));
    }

    private StatementRenderModel.Header header(Statement statement, List<OpenItem> items) {
        LocalDate issueDate = statement.getSentAt() != null ? statement.getSentAt().toLocalDate()
                : statement.getCreatedAt() != null ? statement.getCreatedAt().toLocalDate() : LocalDate.now();
        return new StatementRenderModel.Header(statement.getStatementNo(), nz(statement.getDirection()),
                nz(statement.getPeriod()), periodLabel(statement.getPeriod()),
                statement.getPeriodStart(), statement.getPeriodEnd(),
                issueDate, issueDate.format(HEADER_DATE),
                statement.getDueDate(), dateText(statement.getDueDate()),
                nz(statement.getCurrency()), currencyLabel(statement.getCurrency()),
                taxRateLabel(items), nz(statement.getStatus()), nz(statement.getRemark()));
    }

    private List<StatementRenderModel.Line> lines(List<OpenItem> items) {
        String unit = properties.defaultUnit() == null || properties.defaultUnit().isBlank()
                ? "pcs" : properties.defaultUnit();
        return java.util.stream.IntStream.range(0, items.size()).mapToObj(i -> {
            OpenItem o = items.get(i);
            LocalDate bizDate = o.getBizDate();
            LocalDate dueDate = o.getDueDate();
            return new StatementRenderModel.Line(i + 1, bizDate, dateText(bizDate),
                    dueDate, dateText(dueDate),
                    nz(o.getSourceNo()), nz(o.getOrderNo()), nz(o.getPartnerOrderNo()),
                    nz(o.getBrand()), nz(o.getPartNo()), nz(o.getPackageType()),
                    o.getQuantity() == null ? 0 : o.getQuantity(), unit,
                    o.getUnitPrice(), netUnitPrice(o), o.getTaxIncluded(), o.getTaxRate(),
                    o.getGrossAmount(), o.getNetAmount(), o.getTaxAmount(), "");
        }).toList();
    }

    private StatementRenderModel.Totals totals(List<OpenItem> items) {
        int quantity = items.stream().map(OpenItem::getQuantity).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).sum();
        return new StatementRenderModel.Totals(quantity,
                sum(items, OpenItem::getGrossAmount), sum(items, OpenItem::getNetAmount),
                sum(items, OpenItem::getTaxAmount));
    }

    private static BigDecimal sum(List<OpenItem> items, java.util.function.Function<OpenItem, BigDecimal> field) {
        return items.stream().map(field).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal netUnitPrice(OpenItem o) {
        BigDecimal price = o.getUnitPrice();
        if (price == null) return null;
        Integer included = o.getTaxIncluded();
        Integer rate = o.getTaxRate();
        if (included != null && included == 1 && rate != null)
            return price.divide(BigDecimal.ONE.add(BigDecimal.valueOf(rate).movePointLeft(2)), 4, RoundingMode.HALF_UP);
        return price;
    }

    /** 单月显示为“2026年9月”，跨月期间显示为“2026年8月~9月”或“2025年12月~2026年2月”。 */
    private static String periodLabel(String period) {
        if (period == null || period.isBlank()) return "";
        if (period.matches("\\d{4}-\\d{2}")) return monthLabel(period);
        String[] range = period.split("~", 2);
        if (range.length != 2) return period;
        String start = monthLabel(range[0].trim());
        String[] end = range[1].trim().split("-", 2);
        if (end.length != 2) return start + "~" + range[1].trim();
        boolean sameYear = range[0].trim().startsWith(end[0]);
        return start + "~" + (sameYear ? Integer.parseInt(end[1]) + "月" : monthLabel(range[1].trim()));
    }

    private static String monthLabel(String month) {
        if (!month.matches("\\d{4}-\\d{2}")) return nz(month);
        String[] parts = month.split("-");
        return parts[0] + "年" + Integer.parseInt(parts[1]) + "月";
    }

    private static String currencyLabel(String currency) {
        if (currency == null) return "";
        return switch (currency.toUpperCase(Locale.ROOT)) {
            case "CNY" -> "RMB";
            default -> currency;
        };
    }

    private static String taxRateLabel(List<OpenItem> items) {
        String rates = items.stream().map(OpenItem::getTaxRate).filter(Objects::nonNull).distinct()
                .sorted(Comparator.naturalOrder()).map(r -> r + "%").collect(Collectors.joining("/"));
        return rates;
    }

    private static String dateText(LocalDate date) {
        return date == null ? "" : date.format(DATE);
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
