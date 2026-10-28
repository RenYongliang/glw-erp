package com.glw.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glw.backend.model.entity.FinStatement;
import com.glw.backend.finance.FinanceModels.Query;
import com.glw.backend.finance.FinanceModels.Statement;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface FinStatementMapper extends BaseMapper<FinStatement> {
    int insertStatement(Statement statement);
    List<Statement> statements(@Param("q") Query query);
    long countStatements(@Param("q") Query query);
    Statement statement(Long id);
    int statementStatus(@Param("id") Long id, @Param("expected") String expected, @Param("next") String next);
}
