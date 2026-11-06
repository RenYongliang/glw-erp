package com.glw.backend.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.glw.backend.model.entity.FinOpenItem;
import com.glw.backend.finance.FinanceModels.Query;
import com.glw.backend.finance.FinanceModels.Page;
import com.glw.backend.finance.FinanceModels.OpenItem;
import com.glw.backend.finance.FinanceModels.Summary;
import java.util.List;

/** Receivable/payable snapshots. */
public interface FinanceOpenItemService extends IService<FinOpenItem> {
    int project(String direction, Long sourceId);
    /** 签收等结算基准变化后，重算来源单据对应的应收/应付账单归属。 */
    int refreshBasis(String direction, Long sourceId);
    int rebuild();

    /** 作废未进入对账单、未核销的来源应收/应付；仅用于未签收出库撤销等回滚场景。 */
    int discard(String direction, Long sourceId);
    Page<OpenItem> openItems(Query q);
    Summary summary(Query q);
    List<OpenItem> preview(Query q);
    OpenItem requireOpen(Long id);
}
