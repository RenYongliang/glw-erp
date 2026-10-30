package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.finance.BillingCalendar;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
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

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public int refreshBasis(String direction, Long sourceId) {
        lockService.lock();
        String sourceType = "AP".equals(direction) ? "INBOUND" : "OUTBOUND";
        Map<Long, OpenItem> fresh = finOpenItemMapper.sources(direction, sourceId).stream()
                .collect(Collectors.toMap(OpenItem::getSourceLineId, item -> item));
        int updated = 0;
        for (OpenItem existing : finOpenItemMapper.openItemsBySource(sourceType, sourceId)) {
            OpenItem source = fresh.get(existing.getSourceLineId());
            // 已进入对账单或已确认的记录不再回改，避免破坏已出账口径。
            if (source == null || existing.getStatementId() != null
                    || !("OPEN".equals(existing.getReconcileStatus()) || "PENDING_TAX".equals(existing.getReconcileStatus())))
                continue;
            if (Objects.equals(source.getBasisDate(), existing.getBasisDate())
                    && Objects.equals(source.getBasisReady(), existing.getBasisReady())) continue;
            existing.setBasisDate(source.getBasisDate());
            existing.setBasisReady(source.getBasisReady());
            BillingCalendar.applyItem(existing, finProfileMapper.profile(direction, existing.getPartnerId()));
            changed(finOpenItemMapper.updateBilling(existing));
            auditService.record("OPEN_ITEM", existing.getId(), "REFRESH_BASIS",
                    existing.getBasisDate() == null ? "PENDING" : existing.getBasisDate().toString());
            updated++;
        }
        return updated;
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
                item.setNetAmount(item.getGrossAmount().divide(BigDecimal.ONE.add(BigDecimal.valueOf(rate).movePointLeft(2)), 4, RoundingMode.HALF_UP));
                item.setTaxAmount(item.getGrossAmount().subtract(item.getNetAmount()));
            } else {
                item.setNetAmount(null);
                item.setTaxAmount(null);
            }
        }
        BillingCalendar.applyItem(item, profile);
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
        // 待签收记录尚未确定账单月，不参与出单预览。
        return finOpenItemMapper.openItems(q, false).stream()
                .filter(item -> item.getBasisReady() == null || item.getBasisReady() == 1)
                .toList();
    }

    @Override
    public OpenItem requireOpen(Long id) { OpenItem row = finOpenItemMapper.openItem(id); if (row == null) throw missing(); return row; }
}
