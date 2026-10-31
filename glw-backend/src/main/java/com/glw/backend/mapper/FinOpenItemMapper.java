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
    List<OpenItem> openItemsBySource(@Param("type") String type, @Param("sourceId") Long sourceId);
    /** 可参与出单的记录：已签收/已入库、未对账、未进入任何对账单。 */
    List<OpenItem> statementCandidates(@Param("direction") String direction, @Param("partnerId") Long partnerId);
    /** 结算档案变更后需要按新口径重算期间的未出账记录。 */
    List<OpenItem> pendingBilling(@Param("direction") String direction, @Param("partnerId") Long partnerId);
    OpenItem bySource(@Param("type") String type, @Param("lineId") Long lineId);
    int updateBilling(OpenItem item);
    /** 出单时把行到期日对齐到账单到期日，保证对账单内口径一致。 */
    int setDueDate(@Param("id") Long id, @Param("dueDate") java.time.LocalDate dueDate);
    int setReconciliation(@Param("id") Long id, @Param("expected") String expected, @Param("status") String status, @Param("statementId") Long statementId);
    int settle(@Param("id") Long id, @Param("amount") java.math.BigDecimal amount);
}
