package com.glw.backend.finance.statement;

import com.glw.backend.common.BusinessException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LayoutStatementRendererTest {
    private final LayoutStatementRenderer renderer = new LayoutStatementRenderer(JsonMapper.builder().build());

    @Test void rendersTextFieldsTableAndTotals() throws Exception {
        byte[] bytes = renderer.render(StatementSampleData.sample(), layout());
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(texts(sheet)).contains("对 账 单").contains("客户：深圳市铭泰电子信息科技有限公司")
                    .contains("型号").contains("LM358DR");
            assertThat(texts(sheet)).anyMatch(text -> text.startsWith("合计金额"))
                    .anyMatch(text -> text.contains("21,710.00"));
            assertThat(findText(sheet, "合计金额").getRow().getRowNum()).isEqualTo(28);
            assertThat(sheet.getNumMergedRegions()).isGreaterThan(10);
        }
    }

    @Test void flowComponentsBelowTableAndUseRealItemCount() throws Exception {
        byte[] bytes = renderer.render(modelWithItems(8), layout());
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            // 表格高 36mm 只容纳 4 行；8 行时下方合计整体下移 28mm，约 9 个 3mm 网格行。
            assertThat(findText(sheet, "合计金额").getRow().getRowNum()).isGreaterThan(35);
            assertThat(texts(sheet)).contains("LM358DR");
        }
    }

    @Test void rejectsOverlappingAndTablelessLayouts() {
        String overlap = layout().replace("\"y\":22", "\"y\":12");
        assertThatThrownBy(() -> renderer.validate(overlap)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("重叠");
        String noTable = """
                {"page":{"size":"A4","orientation":"PORTRAIT","marginMm":10,"gridMm":3},
                 "components":[{"id":"t","type":"text","x":10,"y":10,"w":50,"h":10,"text":"hi"}]}""";
        assertThatThrownBy(() -> renderer.validate(noTable)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("明细表格");
    }

    @Test void rejectsUnknownField() {
        String invalid = layout().replace("\"field\":\"partner.name\"", "\"field\":\"partner.unknown\"");
        assertThatThrownBy(() -> renderer.validate(invalid)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("单值字段不存在");
    }

    private static StatementRenderModel modelWithItems(int count) {
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

    private static Cell findText(Sheet sheet, String value) {
        for (Row row : sheet) for (Cell cell : row)
            if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().contains(value)) return cell;
        throw new AssertionError("cell not found: " + value);
    }

    private static List<String> texts(Sheet sheet) {
        List<String> values = new ArrayList<>();
        for (Row row : sheet) for (Cell cell : row)
            if (cell.getCellType() == CellType.STRING) values.add(cell.getStringCellValue());
        return values;
    }

    private static String layout() {
        return """
                {
                  "page": {"size":"A4","orientation":"PORTRAIT","marginMm":10,"gridMm":3},
                  "components": [
                    {"id":"title","type":"text","x":15,"y":10,"w":180,"h":10,
                     "text":"对 账 单","fontSize":18,"bold":true,"align":"center"},
                    {"id":"company","type":"field","x":15,"y":22,"w":105,"h":6,
                     "field":"company.name","fontSize":10,"bold":true},
                    {"id":"period","type":"field","x":130,"y":22,"w":65,"h":6,
                     "label":"期间：","field":"header.periodLabel","align":"right"},
                    {"id":"partner","type":"field","x":15,"y":30,"w":105,"h":6,
                     "label":"客户：","field":"partner.name"},
                    {"id":"date","type":"field","x":130,"y":30,"w":65,"h":6,
                     "label":"对账日期：","field":"header.statementDateLabel","align":"right"},
                    {"id":"table","type":"table","x":15,"y":42,"w":180,"h":36,
                     "headerHeight":8,"rowHeight":7,"fontSize":9,
                     "columns":[
                       {"title":"序号","field":"item.seq","width":12,"align":"center"},
                       {"title":"送货日期","field":"item.bizDateText","width":25,"align":"center"},
                       {"title":"送货单号","field":"item.sourceNo","width":35},
                       {"title":"客户订单号","field":"item.partnerOrderNo","width":33},
                       {"title":"型号","field":"item.partNo","width":30},
                       {"title":"数量","field":"item.quantity","width":15,"align":"right"},
                       {"title":"单价","field":"item.unitPrice","width":15,"align":"right"},
                       {"title":"金额","field":"item.amountGross","width":15,"align":"right"}
                     ]},
                    {"id":"total-label","type":"text","x":15,"y":84,"w":70,"h":6,
                     "text":"合计（大写）：","fontSize":10,"bold":true},
                    {"id":"total","type":"field","x":95,"y":84,"w":100,"h":6,
                     "label":"合计金额：","field":"totals.totalGross","fontSize":10,"bold":true,"align":"right"},
                    {"id":"sign","type":"signature","x":15,"y":250,"w":180,"h":30,
                     "text":"客户确认（盖章）：                              供应商确认（盖章）：","border":true}
                  ]
                }""";
    }
}
