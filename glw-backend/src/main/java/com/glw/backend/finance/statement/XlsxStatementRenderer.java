package com.glw.backend.finance.statement;

import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;
import com.glw.backend.model.entity.FinStatementTemplate;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按 Excel 模板渲染对账单。
 *
 * 模板约定：
 * 1. 抬头/合计等单值字段用 {@code {{company.name}}}、{@code {{header.periodLabel}}} 这类占位符；
 * 2. 明细行放 {@code {{item.partNo}}} 等占位符，连续多行会作为固定明细区；
 * 3. 明细超出模板行数时自动插入行并复制最后一行样式，合计区随之下移。
 */
@Component
@RequiredArgsConstructor
public class XlsxStatementRenderer implements StatementRenderer {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.]+)\\s*}}");
    private static final DateTimeFormatter DATE_TEXT = DateTimeFormatter.ofPattern("yyyy/M/d", Locale.CHINA);
    private static final String ITEM_PREFIX = "item.";
    private static final int MAX_ABSORBED_BLANK_ROWS = 100;

    private final StatementTemplateStorage storage;

    @Override
    public String format() { return "XLSX"; }

    @Override
    public String contentType() { return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; }

    @Override
    public String extension() { return ".xlsx"; }

    @Override
    public byte[] render(StatementRenderModel model, FinStatementTemplate template) {
        if (template == null) throw bad("该往来单位未配置 Excel 对账单模板");
        byte[] source = storage.read(template.getFileKey());
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(source));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.getSheetAt(0);
            DetailBlock block = findDetailBlock(sheet);
            if (block == null) throw bad("模板缺少 {{item.xxx}} 明细占位符");
            expand(sheet, block, model.items().size());
            fillItems(sheet, block, model.items());
            replaceScalars(sheet, scalarValues(model));
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "生成 Excel 对账单失败");
        }
    }

    /** 上传时校验模板可读且包含明细占位符。 */
    public static void validate(byte[] content) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                if (findDetailBlock(workbook.getSheetAt(i)) != null) return;
            }
            throw bad("Excel 模板缺少 {{item.xxx}} 明细占位符");
        } catch (IOException | RuntimeException e) {
            if (e instanceof BusinessException be) throw be;
            throw bad("文件不是有效的 Excel 模板");
        }
    }

    /** markerRows 是真正带占位符的行数，rowCount 还包含其后只保留样式的空行。 */
    private record DetailBlock(int firstRow, int rowCount, int markerRows) {}

    private static DetailBlock findDetailBlock(Sheet sheet) {
        int first = -1;
        for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum(); r++) {
            if (hasItemMarker(sheet.getRow(r))) { first = r; break; }
        }
        if (first < 0) return null;
        int markerRows = 0;
        while (hasItemMarker(sheet.getRow(first + markerRows))) markerRows++;
        int count = markerRows;
        // 模板常把明细区画成固定行：marker 行后面只保留样式的空行也吸收进明细区。
        int absorbed = 0;
        while (absorbed < MAX_ABSORBED_BLANK_ROWS && isBlankRow(sheet.getRow(first + count))) {
            count++;
            absorbed++;
        }
        return new DetailBlock(first, count, markerRows);
    }

    private static boolean hasItemMarker(Row row) {
        if (row == null) return false;
        for (Cell cell : row) {
            if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().contains("{{" + ITEM_PREFIX))
                return true;
        }
        return false;
    }

    private static boolean isBlankRow(Row row) {
        if (row == null) return true;
        for (Cell cell : row) {
            if (cell.getCellType() == CellType.STRING && !cell.getStringCellValue().isBlank()) return false;
            if (cell.getCellType() == CellType.NUMERIC) return false;
            if (cell.getCellType() == CellType.BOOLEAN) return false;
            if (cell.getCellType() == CellType.FORMULA) return false;
        }
        return true;
    }

    private static void expand(Sheet sheet, DetailBlock block, int items) {
        int needed = Math.max(items, block.rowCount());
        int extra = needed - block.rowCount();
        if (extra <= 0) return;
        int at = block.firstRow() + block.rowCount();
        int prototype = block.firstRow() + block.markerRows() - 1;
        insertRows(sheet, at, extra, prototype);
    }

    /** POI 的 shiftRows 不会移动合并单元格，这里补上，保证落款区随明细下移。 */
    private static void insertRows(Sheet sheet, int at, int count, int prototypeRowIndex) {
        Row prototype = sheet.getRow(prototypeRowIndex);
        List<CellRangeAddress> moved = new ArrayList<>();
        for (int i = sheet.getNumMergedRegions() - 1; i >= 0; i--) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.getFirstRow() >= at) {
                moved.add(region.copy());
                sheet.removeMergedRegion(i);
            }
        }
        int last = sheet.getLastRowNum();
        if (last >= at) sheet.shiftRows(at, last, count, true, false);
        for (CellRangeAddress region : moved) {
            region.setFirstRow(region.getFirstRow() + count);
            region.setLastRow(region.getLastRow() + count);
            sheet.addMergedRegion(region);
        }
        for (int i = 0; i < count; i++) {
            Row row = sheet.getRow(at + i);
            if (row == null) row = sheet.createRow(at + i);
            if (prototype != null) {
                row.setHeight(prototype.getHeight());
                for (int c = prototype.getFirstCellNum(); c < prototype.getLastCellNum(); c++) {
                    Cell src = prototype.getCell(c);
                    if (src == null) continue;
                    Cell dst = row.getCell(c);
                    if (dst == null) dst = row.createCell(c);
                    dst.setCellStyle(src.getCellStyle());
                    // 新行需要继承占位符文本，fillItems 才能继续按列填充。
                    if (src.getCellType() == CellType.STRING) dst.setCellValue(src.getStringCellValue());
                    else if (src.getCellType() == CellType.NUMERIC) dst.setCellValue(src.getNumericCellValue());
                }
            }
        }
    }

    private void fillItems(Sheet sheet, DetailBlock block, List<StatementRenderModel.Line> items) {
        int rows = Math.max(items.size(), block.rowCount());
        for (int i = 0; i < rows; i++) {
            Row row = sheet.getRow(block.firstRow() + i);
            if (row == null) continue;
            Map<String, Object> values = i < items.size() ? itemValues(items.get(i)) : Map.of();
            for (Cell cell : row) {
                String text = stringCell(cell);
                if (text == null || !text.contains("{{" + ITEM_PREFIX)) continue;
                if (i < items.size()) fill(cell, text, values);
                else cell.setBlank();
            }
        }
    }

    private void replaceScalars(Sheet sheet, Map<String, Object> values) {
        for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (Cell cell : row) {
                String text = stringCell(cell);
                if (text == null || !text.contains("{{")) continue;
                if (text.contains("{{" + ITEM_PREFIX))
                    throw bad("明细占位符只能出现在连续明细行中：" + cell.getAddress());
                fill(cell, text, values);
            }
        }
    }

    private static void fill(Cell cell, String text, Map<String, Object> values) {
        Matcher exact = PLACEHOLDER.matcher(text);
        if (exact.matches()) {
            setValue(cell, resolve(values, exact.group(1)));
            return;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find())
            matcher.appendReplacement(result, Matcher.quoteReplacement(textValue(resolve(values, matcher.group(1)))));
        matcher.appendTail(result);
        setText(cell, result.toString());
    }

    private static Object resolve(Map<String, Object> values, String key) {
        if (!values.containsKey(key)) throw bad("模板占位符不存在：" + key);
        return values.get(key);
    }

    private static void setValue(Cell cell, Object value) {
        switch (value) {
            case null -> cell.setBlank();
            case LocalDate date -> cell.setCellValue(date);
            case BigDecimal decimal -> cell.setCellValue(decimal.doubleValue());
            case Number number -> cell.setCellValue(number.doubleValue());
            default -> setText(cell, textValue(value));
        }
    }

    /**
     * 部分工具（如 openpyxl）把文本存成 inlineStr，POI 直接 setCellValue(String)
     * 不会覆盖原有的 &lt;is&gt; 内容；先清空再写入才能可靠替换。
     */
    private static void setText(Cell cell, String text) {
        cell.setBlank();
        cell.setCellValue(text);
    }

    private static String textValue(Object value) {
        return switch (value) {
            case null -> "";
            case LocalDate date -> date.format(DATE_TEXT);
            case BigDecimal decimal -> decimal.stripTrailingZeros().toPlainString();
            default -> value.toString();
        };
    }

    private static String stringCell(Cell cell) {
        if (cell.getCellType() != CellType.STRING) return null;
        String text = cell.getStringCellValue();
        return text.contains("{{") ? text : null;
    }

    private static Map<String, Object> itemValues(StatementRenderModel.Line line) {
        Map<String, Object> map = new HashMap<>();
        map.put("item.seq", line.seq());
        map.put("item.bizDate", line.bizDate());
        map.put("item.bizDateText", line.bizDateText());
        map.put("item.dueDate", line.dueDate());
        map.put("item.dueDateText", line.dueDateText());
        map.put("item.sourceNo", line.sourceNo());
        map.put("item.orderNo", line.orderNo());
        map.put("item.partnerOrderNo", line.partnerOrderNo());
        map.put("item.brand", line.brand());
        map.put("item.partNo", line.partNo());
        map.put("item.packageType", line.packageType());
        map.put("item.quantity", line.quantity());
        map.put("item.unit", line.unit());
        map.put("item.unitPrice", line.unitPrice());
        map.put("item.unitPriceNet", line.unitPriceNet());
        map.put("item.taxIncluded", line.taxIncluded());
        map.put("item.taxRate", line.taxRate());
        map.put("item.amountGross", line.amountGross());
        map.put("item.amountNet", line.amountNet());
        map.put("item.taxAmount", line.taxAmount());
        map.put("item.remark", line.remark());
        return map;
    }

    /** 与模板占位符一一对应的字段字典。 */
    public static Map<String, Object> scalarValues(StatementRenderModel model) {
        StatementRenderModel.Company c = model.company();
        StatementRenderModel.Partner p = model.partner();
        StatementRenderModel.Header h = model.header();
        StatementRenderModel.Totals t = model.totals();
        Map<String, Object> map = new HashMap<>();
        map.put("company.name", c.name());
        map.put("company.taxNo", c.taxNo());
        map.put("company.bankName", c.bankName());
        map.put("company.bankAccount", c.bankAccount());
        map.put("company.phone", c.phone());
        map.put("company.contact", c.contact());
        map.put("partner.name", p.name());
        map.put("partner.contact", p.contact());
        map.put("partner.phone", p.phone());
        map.put("header.statementNo", h.statementNo());
        map.put("header.direction", h.direction());
        map.put("header.period", h.period());
        map.put("header.periodLabel", h.periodLabel());
        map.put("header.periodStart", h.periodStart());
        map.put("header.periodEnd", h.periodEnd());
        map.put("header.dueDate", h.dueDate());
        map.put("header.dueDateLabel", h.dueDateLabel());
        map.put("header.statementDate", h.statementDate());
        map.put("header.statementDateLabel", h.statementDateLabel());
        map.put("header.currency", h.currency());
        map.put("header.currencyLabel", h.currencyLabel());
        map.put("header.taxRateLabel", h.taxRateLabel());
        map.put("header.status", h.status());
        map.put("header.remark", h.remark());
        map.put("totals.totalQuantity", t.totalQuantity());
        map.put("totals.totalGross", t.totalGross());
        map.put("totals.totalNet", t.totalNet());
        map.put("totals.totalTax", t.totalTax());
        return map;
    }

    private static BusinessException bad(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }
}
