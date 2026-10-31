package com.glw.backend.finance.statement;

import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把画布布局直接渲染成 XLSX。
 *
 * 与 {@link XlsxStatementRenderer} 的区别：布局模板不存 Excel 文件，导出时按布局和真实数据实时生成，
 * 因此明细行数不受设计时样例行数限制。
 */
@Component
public class LayoutStatementRenderer {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.]+)\\s*}}");
    private static final double MM_TO_POINTS = 72 / 25.4;
    private static final Set<String> TYPES = Set.of("text", "field", "table", "signature");
    private static final Set<String> SCALARS = Set.of(
            "company.name", "company.taxNo", "company.bankName", "company.bankAccount",
            "company.phone", "company.contact",
            "partner.name", "partner.contact", "partner.phone", "partner.address",
            "header.statementNo", "header.direction", "header.period", "header.periodLabel",
            "header.periodStart", "header.periodEnd", "header.dueDate", "header.dueDateLabel",
            "header.statementDate", "header.statementDateLabel", "header.currency",
            "header.currencyLabel", "header.taxRateLabel", "header.status", "header.remark",
            // 通用单据字段：对账单、合同、送货单共用同一套设计器。
            "header.title", "header.no", "header.dateLabel", "header.partnerLabel",
            "header.paymentTerms", "header.deliveryDate", "header.deliveryPlace", "header.orderNo",
            "header.purchaseNo", "header.handler", "header.shippingSummary",
            "totals.totalQuantity", "totals.totalGross", "totals.totalNet", "totals.totalTax",
            "totals.totalAmount");
    private static final Set<String> ITEM_FIELDS = Set.of(
            "seq", "code", "bizDate", "bizDateText", "dueDate", "dueDateText", "sourceNo", "orderNo",
            "partnerOrderNo", "brand", "partNo", "packageType", "quantity", "unit", "unitPrice",
            "unitPriceNet", "taxIncluded", "taxRate", "amountGross", "amountNet", "taxAmount", "remark",
            "productName", "spec");

    private final ObjectMapper json;

    public LayoutStatementRenderer(ObjectMapper json) {
        this.json = json;
    }

    public byte[] render(StatementRenderModel model, String layoutJson) {
        return render(scalarValues(model),
                model.items().stream().map(LayoutStatementRenderer::lineValues).toList(), layoutJson);
    }

    /** 通用渲染入口：单据类型只决定样例数据，布局本身完全一致。 */
    public byte[] render(Map<String, Object> scalars, List<Map<String, Object>> rows, String layoutJson) {
        StatementLayout layout = parse(layoutJson);
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("单据");
            applyPage(sheet, layout.page(), extraRows(layout, rows.size()));
            double grid = layout.page().grid();
            List<StatementLayout.Component> components = layout.components();
            for (StatementLayout.Component component : components) {
                double shift = shiftFor(component, components, rows.size());
                draw(sheet, workbook, shiftedY(component, shift), rows, scalars, grid);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "生成文档失败");
        }
    }

    /** 保存前校验，避免把无法渲染的布局存进模板。 */
    public StatementLayout validate(String layoutJson) {
        if (layoutJson == null || layoutJson.isBlank()) throw bad("布局内容不能为空");
        if (layoutJson.length() > 512 * 1024) throw bad("模板布局过大，请精简组件");
        StatementLayout layout = parse(layoutJson);
        if (!"A4".equals(layout.page().paper())) throw bad("暂只支持 A4 纸张");
        if (layout.components() == null || layout.components().isEmpty()) throw bad("请至少添加一个组件");
        if (layout.components().size() > 200) throw bad("组件数量不能超过 200 个");
        double pageW = layout.page().widthMm();
        double pageH = layout.page().heightMm();
        boolean table = false;
        List<StatementLayout.Component> placed = new ArrayList<>();
        for (StatementLayout.Component c : layout.components()) {
            if (!TYPES.contains(c.kind())) throw bad("不支持的组件类型：" + c.type());
            if (c.w() <= 0 || c.h() <= 0) throw bad("组件宽高必须大于 0");
            if (c.x() < 0 || c.y() < 0 || c.x() + c.w() > pageW + 0.5 || c.y() + c.h() > pageH + 0.5)
                throw bad("组件超出纸张范围，请调整位置");
            for (StatementLayout.Component other : placed) {
                if (intersects(c, other))
                    throw bad("组件位置重叠：" + label(c) + " 与 " + label(other));
            }
            placed.add(c);
            if ("table".equals(c.kind())) {
                table = true;
                if (c.columns() == null || c.columns().isEmpty()) throw bad("明细表格至少需要一列");
                if (c.columns().size() > 20) throw bad("明细列不能超过 20 列");
                for (StatementLayout.Column column : c.columns()) {
                    if (!column.key().startsWith("item.") || !ITEM_FIELDS.contains(column.key().substring(5)))
                        throw bad("明细列字段不存在：" + column.key());
                }
                List<StatementLayout.FooterCell> footer = c.summary();
                if (footer.size() > c.columns().size()) throw bad("合计行单元格不能超过明细列数");
                int spans = footer.stream().mapToInt(StatementLayout.FooterCell::spanCount).sum();
                if (spans > c.columns().size())
                    throw bad("合计行跨列数（" + spans + "）超过明细列数（" + c.columns().size() + "）");
                for (StatementLayout.FooterCell cell : footer) {
                    if (!cell.key().isEmpty()) checkScalar(cell.key());
                    checkText(cell.label());
                }
            }
            if ("field".equals(c.kind())) checkScalar(c.field());
            if ("text".equals(c.kind())) checkText(c.text());
        }
        if (!table) throw bad("请添加明细表格组件，并至少绑定一列明细字段");
        return layout;
    }

    public StatementLayout parse(String layoutJson) {
        try {
            StatementLayout layout = json.readValue(layoutJson, StatementLayout.class);
            if (layout == null) throw bad("布局内容不能为空");
            if (layout.page() == null)
                layout = new StatementLayout(new StatementLayout.Page("A4", "PORTRAIT", 10.0, 3.0), layout.components());
            return layout;
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw bad("模板布局格式不正确");
        }
    }

    /** 明细表格按真实行数向下撑开，表格下方组件跟着下移，避免盖住明细。 */
    private double shiftFor(StatementLayout.Component component, List<StatementLayout.Component> components,
                            int itemCount) {
        double shift = 0;
        for (StatementLayout.Component table : components) {
            if (!"table".equals(table.kind()) || table == component) continue;
            if (component.y() + 0.01 < table.y() + table.h()) continue;
            double rendered = table.headerMm() + Math.max(itemCount, 1) * table.rowMm() + table.footerMm();
            shift += Math.max(0, rendered - table.h());
        }
        return shift;
    }

    private static StatementLayout.Component shiftedY(StatementLayout.Component c, double shift) {
        if (shift == 0) return c;
        return new StatementLayout.Component(c.id(), c.type(), c.x(), c.y() + shift, c.w(), c.h(),
                c.text(), c.label(), c.field(), c.fontSize(), c.bold(), c.align(), c.border(), c.color(),
                c.headerHeight(), c.rowHeight(), c.headerBold(), c.columns(), c.footer());
    }

    private void draw(Sheet sheet, XSSFWorkbook workbook, StatementLayout.Component component,
                      List<Map<String, Object>> rows, Map<String, Object> scalars, double grid) {
        switch (component.kind()) {
            case "text" -> box(sheet, workbook, component, replace(component.text(), scalars), null, false, grid);
            case "field" -> {
                Object value = scalarValue(component.field(), scalars);
                boolean hasLabel = component.label() != null && !component.label().isBlank();
                box(sheet, workbook, component, fieldText(component, scalars), hasLabel ? null : value, false, grid);
            }
            case "signature" -> box(sheet, workbook, component, replace(component.text(), scalars), null, true, grid);
            case "table" -> table(sheet, workbook, component, rows, scalars, grid);
            default -> { }
        }
    }

    private void box(Sheet sheet, XSSFWorkbook workbook, StatementLayout.Component component,
                     String text, Object numeric, boolean boxed, double grid) {
        CellStyle style = style(workbook, component, false);
        if (numeric instanceof BigDecimal) money(workbook, style);
        if (boxed || component.boxed()) border(style);
        write(sheet, component, text, numeric, style, grid);
    }

    private void table(Sheet sheet, XSSFWorkbook workbook, StatementLayout.Component component,
                       List<Map<String, Object>> rows, Map<String, Object> scalars, double grid) {
        List<StatementLayout.Column> columns = component.columns();
        double totalWidth = columns.stream().mapToDouble(StatementLayout.Column::widthMm).sum();
        double scale = totalWidth <= 0 ? 1 : component.w() / totalWidth;

        CellStyle header = style(workbook, component, component.strongHeader());
        header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        border(header);

        int headerRow = row(component.y(), grid);
        int headerEndRow = Math.max(headerRow, row(component.y() + component.headerMm(), grid) - 1);
        double cursor = component.x();
        for (StatementLayout.Column column : columns) {
            double width = column.widthMm() * scale;
            CellStyle cellStyle = style(workbook, component, component.strongHeader());
            cellStyle.cloneStyleFrom(header);
            cellStyle.setAlignment(alignment(column.horizontal()));
            writeRange(sheet, headerRow, headerEndRow,
                    col(cursor, grid), col(cursor + width, grid) - 1, column.heading(), null, cellStyle);
            cursor += width;
        }

        int rowCount = Math.max(rows.size(), 1);
        for (int i = 0; i < rowCount; i++) {
            double top = component.y() + component.headerMm() + i * component.rowMm();
            Map<String, Object> values = i < rows.size() ? rows.get(i) : Map.of();
            cursor = component.x();
            for (StatementLayout.Column column : columns) {
                double width = column.widthMm() * scale;
                CellStyle cellStyle = style(workbook, component, false);
                cellStyle.setAlignment(alignment(column.horizontal()));
                if (values.get(column.key().substring(5)) instanceof BigDecimal) money(workbook, cellStyle);
                border(cellStyle);
                Object value = values.get(column.key().substring(5));
                writeRange(sheet, row(top, grid), row(top + component.rowMm(), grid) - 1,
                        col(cursor, grid), col(cursor + width, grid) - 1, display(value), numeric(value), cellStyle);
                cursor += width;
            }
        }

        // 合计行：与明细共用列宽，按 span 跨列合并，保证导出后仍和表格是一个整体。
        double footerTop = component.y() + component.headerMm() + rowCount * component.rowMm();
        int footerRow = row(footerTop, grid);
        int footerEndRow = Math.max(footerRow, row(footerTop + component.rowMm(), grid) - 1);
        int column = 0;
        for (StatementLayout.FooterCell cell : component.summary()) {
            int span = Math.min(cell.spanCount(), columns.size() - column);
            if (span <= 0) break;
            CellStyle cellStyle = style(workbook, component, cell.strong());
            cellStyle.setAlignment(alignment(cell.horizontal()));
            border(cellStyle);
            double left = component.x();
            double right = component.x();
            for (int i = 0; i < columns.size(); i++) {
                double width = columns.get(i).widthMm() * scale;
                if (i < column) left += width;
                if (i < column + span) right += width;
            }
            Object value = cell.key().isEmpty() ? null : scalarValue(cell.key(), scalars);
            if (value instanceof BigDecimal) money(workbook, cellStyle);
            String text = replace(cell.label(), scalars) + display(value);
            writeRange(sheet, footerRow, footerEndRow,
                    col(left, grid), col(right, grid) - 1, text, numeric(value), cellStyle);
            column += span;
        }
    }

    private void write(Sheet sheet, StatementLayout.Component component, String text,
                       Object numeric, CellStyle style, double grid) {
        writeRange(sheet, row(component.y(), grid), row(component.y() + component.h(), grid) - 1,
                col(component.x(), grid), col(component.x() + component.w(), grid) - 1, text, numeric, style);
    }

    private void writeRange(Sheet sheet, int firstRow, int lastRow, int firstCol, int lastCol,
                            String text, Object numeric, CellStyle style) {
        int r2 = Math.max(firstRow, lastRow);
        int c2 = Math.max(firstCol, lastCol);
        Row row = sheet.getRow(firstRow);
        if (row == null) row = sheet.createRow(firstRow);
        Cell cell = row.getCell(firstCol);
        if (cell == null) cell = row.createCell(firstCol);
        if (numeric instanceof BigDecimal decimal) cell.setCellValue(decimal.doubleValue());
        else if (numeric instanceof Number number) cell.setCellValue(number.doubleValue());
        else if (text != null && !text.isEmpty()) cell.setCellValue(text);
        else cell.setBlank();
        cell.setCellStyle(style);
        if (r2 > firstRow || c2 > firstCol)
            sheet.addMergedRegion(new CellRangeAddress(firstRow, r2, firstCol, c2));
    }

    private CellStyle style(XSSFWorkbook workbook, StatementLayout.Component component, boolean bold) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("宋体");
        font.setFontHeightInPoints((short) component.font());
        font.setBold(bold || component.strong());
        style.setFont(font);
        style.setAlignment(alignment(component.horizontal()));
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    private static HorizontalAlignment alignment(String value) {
        return switch (value == null ? "" : value) {
            case "center" -> HorizontalAlignment.CENTER;
            case "right" -> HorizontalAlignment.RIGHT;
            default -> HorizontalAlignment.LEFT;
        };
    }

    private static void money(XSSFWorkbook workbook, CellStyle style) {
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
    }

    private static void border(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    /** 明细撑开需要的额外行数；预先建好才能保持统一的 3mm 行高。 */
    private int extraRows(StatementLayout layout, int itemCount) {
        int extra = 0;
        for (StatementLayout.Component component : layout.components()) {
            if (!"table".equals(component.kind())) continue;
            double rendered = component.headerMm() + Math.max(itemCount, 1) * component.rowMm()
                    + component.footerMm();
            extra = Math.max(extra, (int) Math.ceil(Math.max(0, rendered - component.h()) / layout.page().grid()));
        }
        return extra;
    }

    private void applyPage(Sheet sheet, StatementLayout.Page page, int extraRows) {
        int columns = (int) Math.ceil(page.widthMm() / page.grid());
        // 多留 200 行给页面之外的落款/签章；这些行只在有内容时才进入打印区域，不产生额外分页。
        int rows = (int) Math.ceil(page.heightMm() / page.grid()) + Math.max(200, extraRows + 20);
        int width = (int) Math.round((((page.grid() / 25.4 * 96) - 5) / 7) * 256);
        for (int i = 0; i < columns; i++) sheet.setColumnWidth(i, Math.max(1, width));
        short height = (short) Math.round(page.grid() * MM_TO_POINTS * 20);
        for (int i = 0; i < rows; i++) {
            Row row = sheet.createRow(i);
            row.setHeight(height);
        }
        sheet.setDefaultRowHeight(height);
        PrintSetup print = sheet.getPrintSetup();
        print.setPaperSize(PrintSetup.A4_PAPERSIZE);
        print.setLandscape(page.landscape());
        print.setFitWidth((short) 1);
        print.setFitHeight((short) 0);
        sheet.setFitToPage(true);
        sheet.setMargin(Sheet.LeftMargin, page.margin() / 25.4);
        sheet.setMargin(Sheet.RightMargin, page.margin() / 25.4);
        sheet.setMargin(Sheet.TopMargin, page.margin() / 25.4);
        sheet.setMargin(Sheet.BottomMargin, page.margin() / 25.4);
    }

    private int row(double mm, double grid) { return Math.max(0, (int) Math.round(mm / grid)); }
    private int col(double mm, double grid) { return Math.max(0, (int) Math.round(mm / grid)); }

    String fieldText(StatementLayout.Component component, Map<String, Object> scalars) {
        String label = component.label() == null ? "" : component.label();
        Object value = scalarValue(component.field(), scalars);
        return label + display(value);
    }

    String replace(String text, Map<String, Object> scalars) {
        if (text == null || text.isEmpty()) return "";
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            Object value = scalarValue(matcher.group(1), scalars);
            matcher.appendReplacement(result, Matcher.quoteReplacement(display(value)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private Object scalarValue(String field, Map<String, Object> scalars) {
        if (field == null || field.isBlank()) return "";
        Object value = scalars.get(field.strip());
        return value == null ? "" : value;
    }

    private void checkScalar(String field) {
        if (field == null || !SCALARS.contains(field.strip()))
            throw bad("单值字段不存在：" + field);
    }

    private void checkText(String text) {
        if (text == null) return;
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find())
            if (!SCALARS.contains(matcher.group(1)))
                throw bad("占位符不存在：" + matcher.group(1) + "（明细字段请使用表格组件）");
    }

    private static boolean intersects(StatementLayout.Component a, StatementLayout.Component b) {
        return a.x() < b.x() + b.w() && a.x() + a.w() > b.x()
                && a.y() < b.y() + b.h() && a.y() + a.h() > b.y();
    }

    private static String label(StatementLayout.Component component) {
        if (component.label() != null && !component.label().isBlank()) return component.label();
        if (component.text() != null && !component.text().isBlank()) return component.text();
        return component.kind();
    }

    static Map<String, Object> scalarValues(StatementRenderModel model) {
        Map<String, Object> values = new LinkedHashMap<>();
        put(values, "company.name", model.company().name());
        put(values, "company.taxNo", model.company().taxNo());
        put(values, "company.bankName", model.company().bankName());
        put(values, "company.bankAccount", model.company().bankAccount());
        put(values, "company.phone", model.company().phone());
        put(values, "company.contact", model.company().contact());
        put(values, "partner.name", model.partner().name());
        put(values, "partner.contact", model.partner().contact());
        put(values, "partner.phone", model.partner().phone());
        put(values, "header.statementNo", model.header().statementNo());
        put(values, "header.direction", model.header().direction());
        put(values, "header.period", model.header().period());
        put(values, "header.periodLabel", model.header().periodLabel());
        put(values, "header.periodStart", model.header().periodStart());
        put(values, "header.periodEnd", model.header().periodEnd());
        put(values, "header.dueDate", model.header().dueDate());
        put(values, "header.dueDateLabel", model.header().dueDateLabel());
        put(values, "header.statementDate", model.header().statementDate());
        put(values, "header.statementDateLabel", model.header().statementDateLabel());
        put(values, "header.currency", model.header().currency());
        put(values, "header.currencyLabel", model.header().currencyLabel());
        put(values, "header.taxRateLabel", model.header().taxRateLabel());
        put(values, "header.status", model.header().status());
        put(values, "header.remark", model.header().remark());
        // 通用别名：同一套布局组件可以复用到合同、送货单等单据。
        put(values, "header.title", "AP".equals(model.header().direction())
                ? "对账单（应付）" : "对账单（应收）");
        put(values, "header.no", model.header().statementNo());
        put(values, "header.dateLabel", model.header().statementDateLabel());
        put(values, "header.partnerLabel", "AP".equals(model.header().direction()) ? "供应商：" : "客户：");
        put(values, "partner.address", "");
        put(values, "header.paymentTerms", "");
        put(values, "header.deliveryDate", "");
        put(values, "header.deliveryPlace", "");
        put(values, "header.orderNo", "");
        put(values, "header.purchaseNo", "");
        put(values, "header.handler", "");
        put(values, "header.shippingSummary", "");
        put(values, "totals.totalQuantity", model.totals().totalQuantity());
        put(values, "totals.totalGross", model.totals().totalGross());
        put(values, "totals.totalNet", model.totals().totalNet());
        put(values, "totals.totalTax", model.totals().totalTax());
        put(values, "totals.totalAmount", model.totals().totalGross());
        return values;
    }

    static Map<String, Object> lineValues(StatementRenderModel.Line line) {
        Map<String, Object> values = new LinkedHashMap<>();
        put(values, "seq", line.seq());
        put(values, "bizDate", line.bizDate());
        put(values, "bizDateText", line.bizDateText());
        put(values, "dueDate", line.dueDate());
        put(values, "dueDateText", line.dueDateText());
        put(values, "sourceNo", line.sourceNo());
        put(values, "orderNo", line.orderNo());
        put(values, "partnerOrderNo", line.partnerOrderNo());
        put(values, "brand", line.brand());
        put(values, "partNo", line.partNo());
        put(values, "packageType", line.packageType());
        put(values, "quantity", line.quantity());
        put(values, "unit", line.unit());
        put(values, "unitPrice", line.unitPrice());
        put(values, "unitPriceNet", line.unitPriceNet());
        put(values, "taxIncluded", line.taxIncluded());
        put(values, "taxRate", line.taxRate());
        put(values, "amountGross", line.amountGross());
        put(values, "amountNet", line.amountNet());
        put(values, "taxAmount", line.taxAmount());
        put(values, "remark", line.remark());
        put(values, "productName", line.partNo());
        put(values, "spec", line.packageType());
        return values;
    }

    private static void put(Map<String, Object> values, String key, Object value) {
        values.put(key, value == null ? "" : value);
    }

    private static Object numeric(Object value) {
        return value instanceof BigDecimal || value instanceof Integer || value instanceof Long
                ? value : null;
    }

    static String display(Object value) {
        if (value == null) return "";
        if (value instanceof BigDecimal decimal) {
            NumberFormat format = NumberFormat.getNumberInstance(Locale.CHINA);
            format.setMinimumFractionDigits(2);
            format.setMaximumFractionDigits(2);
            return format.format(decimal.setScale(2, RoundingMode.HALF_UP));
        }
        return String.valueOf(value);
    }

    private static BusinessException bad(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }
}
