package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.finance.FinanceModels.OpenItem;
import com.glw.backend.finance.FinanceModels.Payment;
import com.glw.backend.finance.FinanceModels.Allocation;
import com.glw.backend.finance.FinanceModels.Allocate;
import com.glw.backend.mapper.FinOpenItemMapper;
import com.glw.backend.mapper.FinPaymentMapper;
import com.glw.backend.mapper.FinAllocationMapper;
import com.glw.backend.model.entity.FinAllocation;
import com.glw.backend.service.FinanceOpenItemService;
import com.glw.backend.service.FinancePaymentService;
import com.glw.backend.service.FinanceAllocationService;
import com.glw.backend.service.FinanceAuditService;
import com.glw.backend.service.FinanceLockService;
import com.glw.backend.service.SalesOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Objects;
import static com.glw.backend.finance.FinanceSupport.*;

/** Payment clearing and reversal. */
@Service
@Profile("!mock")
@RequiredArgsConstructor
public class FinanceAllocationServiceImpl extends ServiceImpl<FinAllocationMapper, FinAllocation>
        implements FinanceAllocationService {
    private final FinAllocationMapper finAllocationMapper;
    private final FinPaymentMapper finPaymentMapper;
    private final FinOpenItemMapper finOpenItemMapper;
    private final FinanceLockService lockService;
    private final FinanceAuditService auditService;
    private final FinanceOpenItemService openItemService;
    private final FinancePaymentService paymentService;
    private final SalesOrderService salesOrderService;

    @Transactional
    @Override
    public Allocation allocate(Allocate input) {
        lockService.lock();
        Allocation existing = finAllocationMapper.allocationByKey(input.getRequestKey());
        if (existing != null) {
            if (!Objects.equals(existing.getPaymentId(), input.getPaymentId()) || !Objects.equals(existing.getOpenItemId(), input.getOpenItemId()) || existing.getAmount().compareTo(input.getAmount()) != 0)
                throw conflict("重复请求标识对应了不同核销内容");
            return existing;
        }
        Payment p = paymentService.requirePayment(input.getPaymentId()); OpenItem o = openItemService.requireOpen(input.getOpenItemId());
        validateAllocation(p, o, input.getAmount());
        changed(finPaymentMapper.allocate(p.getId(), input.getAmount()));
        changed(finOpenItemMapper.settle(o.getId(), input.getAmount()));
        var row = new Allocation(); row.setRequestKey(input.getRequestKey()); row.setPaymentId(p.getId());
        row.setOpenItemId(o.getId()); row.setAmount(input.getAmount());
        finAllocationMapper.insertAllocation(row);
        auditService.record("ALLOCATION", row.getId(), "ALLOCATE", input.getAmount().toPlainString());
        if ("AR".equals(o.getDirection()) && o.getOrderId() != null)
            salesOrderService.refreshCompletionStatus(o.getOrderId());
        return finAllocationMapper.allocation(row.getId());
    }

    public static void validateAllocation(Payment p, OpenItem o, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 4) throw bad("核销金额须大于零且最多四位小数");
        if ("VOID".equals(p.getStatus())) throw bad("已作废收付款不能核销");
        if (!Objects.equals(p.getDirection(), o.getDirection()) || !Objects.equals(p.getPartnerId(), o.getPartnerId()) || !Objects.equals(p.getCurrency(), o.getCurrency()))
            throw bad("只能核销同方向、同往来单位、同币种的记录");
        if (!"RECONCILED".equals(o.getReconcileStatus())) throw bad("对账确认后才能核销");
        if (amount.compareTo(p.getAmount().subtract(p.getAllocatedAmount())) > 0 || amount.compareTo(o.getGrossAmount().subtract(o.getSettledAmount())) > 0)
            throw bad("核销金额超过未分配余额或未结金额");
    }

    @Transactional
    @Override
    public void reverse(Long id) {
        lockService.lock();
        Allocation row = finAllocationMapper.allocation(id);
        if (row == null) throw missing();
        if ("REVERSED".equals(row.getStatus())) return;
        OpenItem o = finOpenItemMapper.openItem(row.getOpenItemId());
        changed(finPaymentMapper.allocate(row.getPaymentId(), row.getAmount().negate()));
        changed(finOpenItemMapper.settle(row.getOpenItemId(), row.getAmount().negate()));
        changed(finAllocationMapper.reverse(id)); auditService.record("ALLOCATION", id, "REVERSE", row.getAmount().toPlainString());
        if (o != null && "AR".equals(o.getDirection()) && o.getOrderId() != null)
            salesOrderService.refreshCompletionStatus(o.getOrderId());
    }
}
