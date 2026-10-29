package com.glw.backend.finance.statement;

import com.glw.backend.model.entity.FinStatementTemplate;

/**
 * 按输出格式渲染对账单。实现类不允许出现客户判断，
 * 客户差异只能来自 {@code template} 指向的模板文件。
 */
public interface StatementRenderer {
    /** CSV / XLSX / PDF */
    String format();

    String contentType();

    String extension();

    byte[] render(StatementRenderModel model, FinStatementTemplate template);
}
