package com.glw.backend.finance.statement;

import com.glw.backend.common.BusinessException;
import com.glw.backend.config.FinanceStatementProperties;
import com.glw.backend.model.entity.FinStatementTemplate;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.DateUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XlsxStatementRendererTest {
    private final FinanceStatementProperties properties = new FinanceStatementProperties(
            null, "pcs", new FinanceStatementProperties.Company(
            "深圳光联微电子有限公司", "91440300349827035R", "中国工商银行深圳深圳湾支行",
            "4000027709200986297", "13713956403", "黄宇"), null);
    private final XlsxStatementRenderer renderer = new XlsxStatementRenderer(new StatementTemplateStorage(properties));

    @Test void fillsHeaderDetailAndTotals() throws Exception {
        byte[] bytes = renderer.render(model(2), template());
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(text(sheet, 0, 1)).isEqualTo("深圳光联微电子有限公司");
            assertThat(text(sheet, 1, 1)).isEqualTo("2026年9月对账单");
            assertThat(text(sheet, 3, 6)).isEqualTo("对账日期：10月26日");
            assertThat(text(sheet, 4, 1)).isEqualTo("客户名称：深圳市勤泰智能信息技术有限公司");
            assertThat(text(sheet, 19, 1)).contains("币  别：RMB").contains("税  率：13%");
            assertThat(sheet.getRow(8).getCell(1).getNumericCellValue()).isEqualTo(1);
            assertThat(text(sheet, 8, 5)).isEqualTo("ET0");
            assertThat(DateUtil.isCellDateFormatted(sheet.getRow(8).getCell(2))).isTrue();
            assertThat(sheet.getRow(8).getCell(2).getLocalDateTimeCellValue().toLocalDate())
                    .isEqualTo(LocalDate.of(2026, 9, 7));
            assertThat(sheet.getRow(17).getCell(6).getNumericCellValue()).isEqualTo(200);
            assertThat(sheet.getRow(17).getCell(9).getNumericCellValue()).isEqualTo(200);
            assertNoPlaceholder(sheet);
        }
    }

    @Test void insertsRowsAndMovesTotalsAndFooterWhenDetailOverflows() throws Exception {
        byte[] bytes = renderer.render(model(10), template());
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(text(sheet, 17, 5)).isEqualTo("ET9");
            // 原合计行 18、签名区 22 各下移一行
            assertThat(text(sheet, 18, 5)).isEqualTo("合计数量：");
            assertThat(sheet.getRow(18).getCell(6).getNumericCellValue()).isEqualTo(1000);
            assertThat(text(sheet, 22, 1)).startsWith("1.以上对账明细");
            assertNoPlaceholder(sheet);
        }
    }

    @Test void rendersMingtaiTemplate() throws Exception {
        byte[] bytes = renderer.render(model(2), template("classpath:statement-templates/mingtai-AR.xlsx"));
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(text(sheet, 0, 0)).isEqualTo("深圳光联微电子有限公司");
            assertThat(text(sheet, 1, 0)).isEqualTo("2026年9月对账单");
            assertThat(text(sheet, 3, 0)).isEqualTo("客户名称：");
            assertThat(text(sheet, 3, 1)).isEqualTo("深圳市勤泰智能信息技术有限公司");
            assertThat(text(sheet, 3, 7)).isEqualTo("10月26日");
            assertThat(sheet.getRow(9).getCell(0).getNumericCellValue()).isEqualTo(1);
            assertThat(text(sheet, 9, 1)).isEqualTo("2026/9/7");
            assertThat(text(sheet, 9, 2)).isEqualTo("GL0");
            assertThat(text(sheet, 9, 4)).isEqualTo("ET0");
            assertThat(sheet.getRow(16).getCell(5).getNumericCellValue()).isEqualTo(200);
            assertThat(sheet.getRow(16).getCell(8).getNumericCellValue()).isEqualTo(200);
            assertThat(text(sheet, 20, 0)).startsWith("1.以上对账明细");
            assertNoPlaceholder(sheet);
        }
    }

    @Test void builtinTemplatesAreValid() {
        var storage = new StatementTemplateStorage(properties);
        assertThat(StatementBuiltins.list("AR")).isNotEmpty();
        for (var builtin : StatementBuiltins.list("AR")) {
            var definition = StatementBuiltins.find(builtin.key()).orElseThrow();
            assertThatCode(() -> XlsxStatementRenderer.validate(storage.read(definition.fileKey())))
                    .doesNotThrowAnyException();
        }
    }

    @Test void rejectsTemplateWithoutDetailMarker() {
        assertThatThrownBy(() -> XlsxStatementRenderer.validate(new byte[]{1, 2, 3}))
                .isInstanceOf(BusinessException.class);
    }

    private FinStatementTemplate template() {
        return template("classpath:statement-templates/qintai-AR.xlsx");
    }

    private FinStatementTemplate template(String fileKey) {
        var template = new FinStatementTemplate();
        template.setId(1L);
        template.setFormat("XLSX");
        template.setVersion(1);
        template.setFileKey(fileKey);
        return template;
    }

    private StatementRenderModel model(int count) {
        var company = new StatementRenderModel.Company("深圳光联微电子有限公司", "91440300349827035R",
                "中国工商银行深圳深圳湾支行", "4000027709200986297", "13713956403", "黄宇");
        var partner = new StatementRenderModel.Partner("深圳市勤泰智能信息技术有限公司", "张三", "0755-23283548");
        var header = new StatementRenderModel.Header("AR202609001", "AR", "2026-09", "2026年9月",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                LocalDate.of(2026, 10, 26), "10月26日",
                LocalDate.of(2026, 10, 31), "2026/10/31",
                "CNY", "RMB", "13%", "DRAFT", "");
        List<StatementRenderModel.Line> lines = IntStream.range(0, count).mapToObj(i ->
                new StatementRenderModel.Line(i + 1, LocalDate.of(2026, 9, 7), "2026/9/7",
                        LocalDate.of(2026, 10, 31), "2026/10/31", "GL" + i, "SO" + i, "QT" + i,
                        "品牌", "ET" + i, "SOP8", 100, "pcs", new BigDecimal("1.0000"),
                        new BigDecimal("0.8850"), 1, 13, new BigDecimal("100.0000"),
                        new BigDecimal("88.5000"), new BigDecimal("11.5000"), "")).toList();
        var totals = new StatementRenderModel.Totals(count * 100, BigDecimal.valueOf(count * 100L),
                BigDecimal.valueOf(count * 8850L, 2), BigDecimal.valueOf(count * 1150L, 2));
        return new StatementRenderModel(company, partner, header, lines, totals);
    }

    private static String text(Sheet sheet, int row, int col) {
        return sheet.getRow(row).getCell(col).getStringCellValue();
    }

    private static void assertNoPlaceholder(Sheet sheet) {
        for (Row row : sheet) for (Cell cell : row)
            if (cell.getCellType() == CellType.STRING)
                assertThat(cell.getStringCellValue()).doesNotContain("{{");
    }
}
