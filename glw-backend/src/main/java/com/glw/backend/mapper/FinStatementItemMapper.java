package com.glw.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glw.backend.model.entity.FinStatementItem;
import com.glw.backend.finance.FinanceModels.OpenItem;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface FinStatementItemMapper extends BaseMapper<FinStatementItem> {
    void snapshot(@Param("statementId") Long statementId, @Param("id") Long id);
    List<OpenItem> statementItems(Long id);
}
