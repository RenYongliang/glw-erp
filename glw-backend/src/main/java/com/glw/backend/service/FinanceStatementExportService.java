package com.glw.backend.service;

import com.glw.backend.finance.statement.StatementFile;

/** 对账单导出：解析客户模板并交给对应格式的渲染器。 */
public interface FinanceStatementExportService {
    /**
     * @param format CSV / XLSX / PDF；为空时按对账单快照或客户默认模板选择
     */
    StatementFile export(Long statementId, String format);
}
