package com.glw.backend.finance.statement;

import com.glw.backend.config.FinanceStatementProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 铭泰 PDF 版对账单的版式回归：纸质回传件 → 可视化布局模板。 */
class MingtaiPdfTemplateTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final LayoutStatementRenderer layouts = new LayoutStatementRenderer(json);
    private final LayoutPdfRenderer renderer = new LayoutPdfRenderer(
            layouts, new FinanceStatementProperties(null, null, null, null));

    @Test void validatesMingtaiPdfLayout() throws Exception {
        StatementLayout layout = layouts.validate(layoutJson());
        List<StatementLayout.Component> tables = layout.components().stream()
                .filter(c -> "table".equals(c.kind())).toList();
        assertThat(tables).hasSize(1);
        assertThat(tables.get(0).columns()).extracting(StatementLayout.Column::heading)
                .containsExactly("序号", "送货日期", "销售送货单号", "客户订单号", "型号",
                        "数量", "单位", "单价", "含税金额", "备注");
    }

    @Test void rendersMingtaiPdfLikeReference() throws Exception {
        Assumptions.assumeTrue(
                LayoutPdfRenderer.fontCandidates(null).stream().anyMatch(Files::isRegularFile),
                "本机没有可用的中文字体，跳过 PDF 渲染测试");
        byte[] bytes = renderer.render(StatementSampleData.sample(), layoutJson());
        Path sample = Path.of("target", "mingtai-AR.pdf");
        Files.write(sample, bytes);
        try (PDDocument document = Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            String text = new PDFTextStripper().getText(document);
            assertThat(text)
                    .contains("深圳光联微电子有限公司")
                    .contains("2026年9月对账单")
                    .contains("对账日期：10月8日")
                    .contains("客户名称：深圳市铭泰电子信息科技有限公司")
                    .contains("供应商：深圳光联微电子有限公司")
                    .contains("合计数量：")
                    .contains("对账金额")
                    .contains("RMB")
                    .contains("LM358DR")
                    .contains("21,710.00")
                    .contains("客户确定（请签名盖公章）：");
            assertThat(text).doesNotContain("{{");
        }
    }

    @Test void rendersMingtaiFooterInXlsx() throws Exception {
        byte[] bytes = layouts.render(StatementSampleData.sample(), layoutJson());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            List<String> texts = new ArrayList<>();
            for (Row sheetRow : sheet) {
                for (Cell cell : sheetRow) {
                    String text = new DataFormatter().formatCellValue(cell);
                    if (!text.isBlank()) texts.add(text);
                }
            }
            assertThat(texts).contains("合计数量：", "对账金额", "21,710.00");
        }
    }

    private static String layoutJson() throws Exception {
        try (InputStream in = MingtaiPdfTemplateTest.class.getResourceAsStream(
                "/statement-templates/mingtai-AR.pdf.json")) {
            assertThat(in).as("内置铭泰 PDF 模板").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
