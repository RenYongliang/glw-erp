package com.glw.backend.finance.statement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 设计器预览/布局模板样例渲染使用的固定数据，不依赖数据库。 */
public final class StatementSampleData {
    private StatementSampleData() {}

    public static StatementRenderModel sample() {
        LocalDate date = LocalDate.of(2026, 9, 25);
        List<StatementRenderModel.Line> lines = new ArrayList<>();
        lines.add(line(1, date, "XM202609001", "MT-PO-260901", "LM358DR", 5000,
                new BigDecimal("1.2500"), new BigDecimal("6250.0000")));
        lines.add(line(2, date.plusDays(2), "XM202609002", "MT-PO-260905", "SN74HC595DR", 3000,
                new BigDecimal("0.8600"), new BigDecimal("2580.0000")));
        lines.add(line(3, date.plusDays(5), "XM202609003", "MT-PO-260910", "AMS1117-3.3", 12000,
                new BigDecimal("0.4200"), new BigDecimal("5040.0000")));
        lines.add(line(4, date.plusDays(8), "XM202609004", "MT-PO-260918", "STM32F103C8T6", 800,
                new BigDecimal("9.8000"), new BigDecimal("7840.0000")));
        int quantity = lines.stream().mapToInt(StatementRenderModel.Line::quantity).sum();
        BigDecimal gross = lines.stream().map(StatementRenderModel.Line::amountGross)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = gross.divide(new BigDecimal("1.13"), 4, java.math.RoundingMode.HALF_UP);
        BigDecimal tax = gross.subtract(net);
        return new StatementRenderModel(
                new StatementRenderModel.Company("深圳光联微电子有限公司", "91440300MA5XXXXXXX",
                        "招商银行深圳分行", "7559 1234 5678 901", "0755-8888 6666", "张经理"),
                new StatementRenderModel.Partner("深圳市铭泰电子信息科技有限公司", "李小姐", "0755-2666 8899"),
                new StatementRenderModel.Header("AR202609001", "AR", "2026-09", "2026年9月",
                        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                        LocalDate.of(2026, 10, 8), "10月8日",
                        LocalDate.of(2026, 10, 31), "2026/10/31",
                        "CNY", "RMB", "13%", "CONFIRMED", ""),
                lines,
                new StatementRenderModel.Totals(quantity, gross, net, tax));
    }

    private static StatementRenderModel.Line line(int seq, LocalDate date, String sourceNo,
                                                  String partnerOrderNo, String partNo, int quantity,
                                                  BigDecimal unitPrice, BigDecimal amount) {
        return new StatementRenderModel.Line(seq, date, text(date), date.plusDays(30), text(date.plusDays(30)),
                sourceNo, "SO202609" + String.format("%03d", seq), partnerOrderNo, "LONGSHENG",
                partNo, "SMD", quantity, "pcs", unitPrice, unitPrice, 1, 13,
                amount, amount.divide(new BigDecimal("1.13"), 4, java.math.RoundingMode.HALF_UP),
                amount.subtract(amount.divide(new BigDecimal("1.13"), 4, java.math.RoundingMode.HALF_UP)), "");
    }

    private static String text(LocalDate date) {
        return date.getYear() + "/" + date.getMonthValue() + "/" + date.getDayOfMonth();
    }
}
