package com.glw.backend.finance.statement;

import com.glw.backend.common.CsvExport;
import com.glw.backend.model.entity.FinStatementTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 内置兜底格式：客户未配置模板时沿用原有 CSV 导出，行为与历史版本一致。 */
@Component
public class CsvStatementRenderer implements StatementRenderer {
    @Override
    public String format() { return "CSV"; }

    @Override
    public String contentType() { return "text/csv;charset=UTF-8"; }

    @Override
    public String extension() { return ".csv"; }

    @Override
    public byte[] render(StatementRenderModel model, FinStatementTemplate template) {
        List<List<?>> rows = new ArrayList<>();
        rows.add(List.of("对账单号", "方向", "往来单位", "对账月份", "币种", "状态", "业务日期", "出入库单号",
                "订单号", "客户采购单号", "品牌", "型号", "封装", "数量", "单价", "含税", "税率",
                "未税金额", "税额", "价税合计", "到期日"));
        StatementRenderModel.Header h = model.header();
        for (StatementRenderModel.Line l : model.items())
            rows.add(Arrays.asList(h.statementNo(), h.direction(), model.partner().name(), h.period(),
                    h.currency(), h.status(), l.bizDate(), l.sourceNo(), l.orderNo(), l.partnerOrderNo(),
                    l.brand(), l.partNo(), l.packageType(), l.quantity(), l.unitPrice(), l.taxIncluded(),
                    l.taxRate(), l.amountNet(), l.taxAmount(), l.amountGross(), l.dueDate()));
        return CsvExport.encode(rows);
    }
}
