package com.glw.backend.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.glw.backend.model.entity.FinOpenItem;
import com.glw.backend.finance.FinanceModels.Query;
import com.glw.backend.finance.FinanceModels.Page;
import com.glw.backend.finance.FinanceModels.OpenItem;
import com.glw.backend.finance.FinanceModels.TaxInput;
import com.glw.backend.finance.FinanceModels.Summary;
import java.util.List;

/** Receivable/payable snapshots and tax confirmation. */
public interface FinanceOpenItemService extends IService<FinOpenItem> {
    int project(String direction, Long sourceId);
    int rebuild();
    Page<OpenItem> openItems(Query q);
    OpenItem resolveTax(Long id, TaxInput input);
    Summary summary(Query q);
    List<OpenItem> preview(Query q);
    OpenItem requireOpen(Long id);
}
