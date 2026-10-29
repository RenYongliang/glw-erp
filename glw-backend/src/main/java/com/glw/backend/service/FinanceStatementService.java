package com.glw.backend.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.glw.backend.model.entity.FinStatement;
import com.glw.backend.finance.FinanceModels.Query;
import com.glw.backend.finance.FinanceModels.Page;
import com.glw.backend.finance.FinanceModels.Statement;
import com.glw.backend.finance.FinanceModels.Generate;
import com.glw.backend.finance.FinanceModels.StatementDetail;

/** Statement lifecycle and immutable statement snapshots. */
public interface FinanceStatementService extends IService<FinStatement> {
    StatementDetail generate(Generate input);
    Page<Statement> statements(Query q);
    StatementDetail statement(Long id);
    StatementDetail transition(Long id, String action);
}
