package com.glw.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glw.backend.model.entity.FinOpenItem;
import com.glw.backend.finance.FinanceModels.Query;
import com.glw.backend.finance.FinanceModels.OpenItem;
import com.glw.backend.finance.FinanceModels.Summary;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface FinOpenItemMapper extends BaseMapper<FinOpenItem> {
    List<OpenItem> sources(@Param("direction") String direction, @Param("sourceId") Long sourceId);
    int insertOpen(OpenItem item);
    List<OpenItem> openItems(@Param("q") Query query, @Param("paged") boolean paged);
    long countOpen(@Param("q") Query query);
    Summary summary(@Param("q") Query query);
    OpenItem openItem(Long id);
    int resolveTax(OpenItem item);
    OpenItem bySource(@Param("type") String type, @Param("lineId") Long lineId);
    int setReconciliation(@Param("id") Long id, @Param("expected") String expected, @Param("status") String status, @Param("statementId") Long statementId);
    int settle(@Param("id") Long id, @Param("amount") java.math.BigDecimal amount);
}
