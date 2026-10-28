package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.finance.FinanceModels;
import com.glw.backend.finance.FinanceModels.Query;
import com.glw.backend.finance.FinanceModels.Page;
import com.glw.backend.finance.FinanceModels.OpenItem;
import com.glw.backend.finance.FinanceModels.TaxInput;
import com.glw.backend.finance.FinanceModels.Summary;
import com.glw.backend.mapper.FinProfileMapper;
import com.glw.backend.mapper.FinOpenItemMapper;
import com.glw.backend.model.entity.FinOpenItem;
import com.glw.backend.service.FinanceOpenItemService;
import com.glw.backend.service.FinanceAuditService;
import com.glw.backend.service.FinanceLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import static com.glw.backend.finance.FinanceSupport.*;

/** Receivable/payable snapshots and tax confirmation. */
@Service
@Profile("!mock")
@RequiredArgsConstructor
public class FinanceOpenItemServiceImpl extends ServiceImpl<FinOpenItemMapper, FinOpenItem>
        implements FinanceOpenItemService {
    private final FinOpenItemMapper finOpenItemMapper;
    private final FinProfileMapper finProfileMapper;
    private final FinanceLockService lockService;
    private final FinanceAuditService auditService;

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public int project(String direction, Long sourceId) {
        lockService.lock();
        int added = 0;
        for (OpenItem item : finOpenItemMapper.sources(direction, sourceId)) {
            if (finOpenItemMapper.bySource(item.getSourceType(), item.getSourceLineId()) != null) continue;
            prepare(item, finProfileMapper.profile(direction, item.getPartnerId()));
            finOpenItemMapper.insertOpen(item);
            auditService.record("OPEN_ITEM", item.getId(), "PROJECT", item.getSourceNo());
            added++;
        }
        return added;
    }

    @Transactional
    @Override
    public int rebuild() { return project("AR", null) + project("AP", null); }

    public static void prepare(OpenItem item, FinanceModels.Profile profile) {
        if (!"CNY".equals(item.getCurrency())) throw bad("第一版仅支持人民币，请检查来源单据币种");
        if (item.getQuantity() == null || item.getQuantity() <= 0 || item.getGrossAmount() == null
                || item.getGrossAmount().signum() < 0 || item.getUnitPrice() == null || item.getUnitPrice().signum() < 0)
            throw bad("来源数量或金额无效，不能生成财务记录");
        BigDecimal amount = item.getGrossAmount();
        Integer rate = item.getTaxRate();
        if (rate != null && (rate < 0 || rate > 100)) throw bad("来源税率无效");
        if (!Objects.equals(item.getTaxIncluded(), 0) && !Objects.equals(item.getTaxIncluded(), 1)) throw bad("来源含税标志无效");
        item.setReconcileStatus("OPEN");
        if (Objects.equals(item.getTaxIncluded(), 0)) {
            item.setNetAmount(money(amount));
            if (rate == null) {
                item.setTaxAmount(null); item.setGrossAmount(null); item.setReconcileStatus("PENDING_TAX");
            } else {
                item.setTaxAmount(money(item.getNetAmount().multiply(BigDecimal.valueOf(rate)).movePointLeft(2)));
                item.setGrossAmount(item.getNetAmount().add(item.getTaxAmount()));
            }
        } else {
            item.setGrossAmount(money(amount));
            if (rate != null) {
                item.setNetAmount(item.getGrossAmount().divide(BigDecimal.ONE.add(BigDecimal.valueOf(rate).movePointLeft(2)), 2, RoundingMode.HALF_UP));
                item.setTaxAmount(item.getGrossAmount().subtract(item.getNetAmount()));
            } else {
                item.setNetAmount(null);
                item.setTaxAmount(null);
            }
        }
        item.setPeriod(YearMonth.from(item.getBizDate()).toString());
        LocalDate base = profile != null && "MONTH_END".equals(profile.getTermBasis())
                ? YearMonth.from(item.getBizDate()).atEndOfMonth() : item.getBizDate();
        item.setDueDate(base.plusDays(profile == null ? 0 : profile.getTermDays()));
    }

    @Override
    public Page<OpenItem> openItems(Query q) { return new Page<>(finOpenItemMapper.openItems(q, true), finOpenItemMapper.countOpen(q)); }

    @Transactional
    @Override
    public OpenItem resolveTax(Long id, TaxInput input) {
        lockService.lock(); OpenItem item = requireOpen(id);
        if (!"PENDING_TAX".equals(item.getReconcileStatus())) throw conflict("仅税率待确认的记录可以补税率");
        item.setTaxRate(input.taxRate());
        item.setTaxAmount(money(item.getNetAmount().multiply(BigDecimal.valueOf(input.taxRate())).movePointLeft(2)));
        item.setGrossAmount(item.getNetAmount().add(item.getTaxAmount()));
        changed(finOpenItemMapper.resolveTax(item)); auditService.record("OPEN_ITEM", id, "RESOLVE_TAX", input.taxRate().toString());
        return requireOpen(id);
    }

    @Override
    public Summary summary(Query q) { return finOpenItemMapper.summary(q); }

    @Override
    public List<OpenItem> preview(Query q) {
        q.setStatus("OPEN");
        return finOpenItemMapper.openItems(q, false);
    }

    @Override
    public OpenItem requireOpen(Long id) { OpenItem row = finOpenItemMapper.openItem(id); if (row == null) throw missing(); return row; }
}
