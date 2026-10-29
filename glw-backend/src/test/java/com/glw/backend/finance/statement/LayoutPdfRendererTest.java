package com.glw.backend.finance.statement;

import com.glw.backend.common.BusinessException;
import com.glw.backend.config.FinanceStatementProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LayoutPdfRendererTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final LayoutPdfRenderer renderer = new LayoutPdfRenderer(
            new LayoutStatementRenderer(json), new FinanceStatementProperties(null, null, null, null));

    @Test void rendersChineseTextAndTable() throws Exception {
        assumeFont();
        try (PDDocument document = Loader.loadPDF(renderer.render(StatementSampleData.sample(), layout()))) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("客户").contains("深圳市铭泰电子信息科技有限公司")
                    .contains("LM358DR").contains("21,710.00").contains("客户确认");
        }
    }

    @Test void paginatesLongTableAndKeepsHeaderAndSignature() throws Exception {
        assumeFont();
        byte[] bytes = renderer.render(modelWithItems(60), layout());
        try (PDDocument document = Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(2);
            stripper.setEndPage(2);
            assertThat(stripper.getText(document)).contains("型号").contains("LM358DR");
            int last = document.getNumberOfPages();
            stripper.setStartPage(last);
            stripper.setEndPage(last);
            assertThat(stripper.getText(document)).contains("客户确认").contains("合计金额");
        }
    }

    @Test void rejectsMultipleTables() {
        assumeFont();
        String twoTables = layout().replace("\"type\":\"signature\"", "\"type\":\"table\"");
        assertThatThrownBy(() -> renderer.render(StatementSampleData.sample(), twoTables))
                .isInstanceOf(BusinessException.class).hasMessageContaining("一个明细表格");
    }

    private static void assumeFont() {
        Assumptions.assumeTrue(
                LayoutPdfRenderer.fontCandidates(null).stream().anyMatch(Files::isRegularFile),
                "本机没有可用的中文字体，跳过 PDF 渲染测试");
    }

    private StatementRenderModel modelWithItems(int count) {
        StatementRenderModel sample = StatementSampleData.sample();
        List<StatementRenderModel.Line> lines = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            StatementRenderModel.Line line = sample.items().get(i % sample.items().size());
            lines.add(new StatementRenderModel.Line(i + 1, line.bizDate(), line.bizDateText(), line.dueDate(),
                    line.dueDateText(), line.sourceNo() + i, line.orderNo(), line.partnerOrderNo(),
                    line.brand(), line.partNo(), line.packageType(), line.quantity(), line.unit(),
                    line.unitPrice(), line.unitPriceNet(), line.taxIncluded(), line.taxRate(),
                    line.amountGross(), line.amountNet(), line.taxAmount(), line.remark()));
        }
        BigDecimal gross = lines.stream().map(StatementRenderModel.Line::amountGross)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new StatementRenderModel(sample.company(), sample.partner(), sample.header(), lines,
                new StatementRenderModel.Totals(lines.stream().mapToInt(StatementRenderModel.Line::quantity).sum(),
                        gross, gross, BigDecimal.ZERO));
    }

    private static String layout() {
        return """
                {
                  "page": {"size":"A4","orientation":"PORTRAIT","marginMm":10,"gridMm":3},
                  "components": [
                    {"id":"title","type":"text","x":15,"y":10,"w":180,"h":10,
                     "text":"对 账 单","fontSize":18,"bold":true,"align":"center"},
                    {"id":"partner","type":"field","x":15,"y":30,"w":105,"h":6,
                     "label":"客户：","field":"partner.name"},
                    {"id":"table","type":"table","x":15,"y":42,"w":180,"h":36,
                     "headerHeight":8,"rowHeight":7,"fontSize":9,
                     "columns":[
                       {"title":"序号","field":"item.seq","width":15,"align":"center"},
                       {"title":"型号","field":"item.partNo","width":65},
                       {"title":"数量","field":"item.quantity","width":45,"align":"right"},
                       {"title":"金额","field":"item.amountGross","width":55,"align":"right"}
                     ]},
                    {"id":"total","type":"field","x":95,"y":84,"w":100,"h":6,
                     "label":"合计金额：","field":"totals.totalGross","fontSize":10,"bold":true,"align":"right"},
                    {"id":"sign","type":"signature","x":15,"y":250,"w":180,"h":30,
                     "text":"客户确认（盖章）：                              供应商确认（盖章）：","border":true}
                  ]
                }""";
    }
}
