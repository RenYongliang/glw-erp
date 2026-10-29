package com.glw.backend.finance.statement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 模板设计器预览和保存时使用的样例数据，不依赖数据库。 */
public final class DocumentSampleData {
    private DocumentSampleData() {
    }

    public static Map<String, Object> scalars(DocumentType type, String direction) {
        if (type == DocumentType.STATEMENT) {
            Map<String, Object> values = LayoutStatementRenderer.scalarValues(StatementSampleData.sample());
            values.put("header.title", type.title(direction));
            values.put("partner.address", "深圳市南山区科技园 8 号");
            return values;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        put(values, "company.name", "深圳光联微电子有限公司");
        put(values, "company.taxNo", "91440300MA5XXXXXXX");
        put(values, "company.bankName", "招商银行深圳分行");
        put(values, "company.bankAccount", "7559 1234 5678 901");
        put(values, "company.phone", "0755-8888 6666");
        put(values, "company.contact", "张经理");
        put(values, "partner.name", type == DocumentType.PURCHASE_CONTRACT
                ? "深圳市华芯电子材料有限公司" : "深圳市铭泰电子信息科技有限公司");
        put(values, "partner.contact", "李小姐");
        put(values, "partner.phone", "0755-2666 8899");
        put(values, "partner.address", "深圳市南山区科技园 8 号");
        String prefix = switch (type) {
            case SALES_CONTRACT -> "XSHT";
            case PURCHASE_CONTRACT -> "CGHT";
            case DELIVERY_NOTE -> "SH";
            default -> "D";
        };
        put(values, "header.title", type.title(direction));
        put(values, "header.no", prefix + "202610001");
        put(values, "header.dateLabel", "2026年10月29日");
        put(values, "header.partnerLabel", "往来单位：");
        put(values, "header.periodLabel", "2026-10");
        put(values, "header.currencyLabel", "RMB");
        put(values, "header.remark", "以上货物按约定时间交付，质量按双方确认的规格书执行。");
        put(values, "header.paymentTerms", "月结 30 天，验收合格后付款");
        put(values, "header.deliveryDate", "2026年11月15日前");
        put(values, "header.deliveryPlace", "深圳市宝安区光联微电子仓库");
        put(values, "header.orderNo", "SO202610001");
        put(values, "totals.totalQuantity", 20800);
        put(values, "totals.totalGross", new BigDecimal("21710.00"));
        put(values, "totals.totalNet", new BigDecimal("19212.39"));
        put(values, "totals.totalTax", new BigDecimal("2497.61"));
        put(values, "totals.totalAmount", new BigDecimal("21710.00"));
        return values;
    }

    public static List<Map<String, Object>> rows(DocumentType type) {
        if (type == DocumentType.STATEMENT) {
            return StatementSampleData.sample().items().stream()
                    .map(LayoutStatementRenderer::lineValues)
                    .toList();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row(1, "运算放大器", "SOP-8", "LM358DR", "SOP-8", 5000,
                new BigDecimal("1.2500"), new BigDecimal("6250.0000"), "SO202610001"));
        rows.add(row(2, "移位寄存器", "SOP-16", "SN74HC595DR", "SOP-16", 3000,
                new BigDecimal("0.8600"), new BigDecimal("2580.0000"), "SO202610001"));
        rows.add(row(3, "线性稳压器", "SOT-223", "AMS1117-3.3", "SOT-223", 12000,
                new BigDecimal("0.4200"), new BigDecimal("5040.0000"), "SO202610002"));
        rows.add(row(4, "微控制器", "LQFP-48", "STM32F103C8T6", "LQFP-48", 800,
                new BigDecimal("9.8000"), new BigDecimal("7840.0000"), "SO202610002"));
        return rows;
    }

    private static Map<String, Object> row(int seq, String productName, String spec, String partNo,
                                           String packageType, int quantity, BigDecimal unitPrice,
                                           BigDecimal amount, String orderNo) {
        Map<String, Object> values = new LinkedHashMap<>();
        put(values, "seq", seq);
        put(values, "productName", productName);
        put(values, "spec", spec);
        put(values, "partNo", partNo);
        put(values, "brand", "LONGSHENG");
        put(values, "packageType", packageType);
        put(values, "quantity", quantity);
        put(values, "unit", "pcs");
        put(values, "unitPrice", unitPrice);
        put(values, "unitPriceNet", unitPrice);
        put(values, "amountGross", amount);
        put(values, "amountNet", amount.divide(new BigDecimal("1.13"), 4, java.math.RoundingMode.HALF_UP));
        put(values, "taxAmount", amount.subtract(
                amount.divide(new BigDecimal("1.13"), 4, java.math.RoundingMode.HALF_UP)));
        put(values, "remark", "");
        put(values, "orderNo", orderNo);
        put(values, "sourceNo", orderNo);
        return values;
    }

    private static void put(Map<String, Object> values, String key, Object value) {
        values.put(key, value == null ? "" : value);
    }
}
