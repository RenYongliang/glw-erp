package com.glw.backend.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.glw.backend.model.entity.FinStatement;
import com.glw.backend.finance.FinanceModels.Query;
import com.glw.backend.finance.FinanceModels.Page;
import com.glw.backend.finance.FinanceModels.Statement;
import com.glw.backend.finance.FinanceModels.Generate;
import com.glw.backend.finance.FinanceModels.StatementDetail;

import java.util.List;

/** Statement lifecycle and immutable statement snapshots. */
public interface FinanceStatementService extends IService<FinStatement> {
    /** 按客户账单周期分组出单；跨周期选择会生成多张对账单。 */
    List<Statement> generate(Generate input);
    /** 定时自动出单，返回本次生成的对账单数量。 */
    int autoGenerate();
    Page<Statement> statements(Query q);
    StatementDetail statement(Long id);
    StatementDetail transition(Long id, String action);

    /** 撤销出库前作废未确认的对账单；已确认的对账单拒绝作废。 */
    void voidForReversal(Long statementId);
}
