package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.finance.FinanceModels.OpenItem;
import com.glw.backend.finance.FinanceModels.Payment;
import com.glw.backend.finance.FinanceModels.Allocation;
import com.glw.backend.finance.FinanceModels.Allocate;
import com.glw.backend.finance.FinanceModels.PaymentDetail;
import com.glw.backend.finance.FinanceModels.PaymentInput;
import com.glw.backend.finance.FinanceModels.SettleOrderInput;
import com.glw.backend.finance.FinanceModels.SettleOrderResult;
import com.glw.backend.mapper.FinOpenItemMapper;
import com.glw.backend.mapper.FinPaymentMapper;
import com.glw.backend.mapper.FinAllocationMapper;
import com.glw.backend.mapper.SalesOrderMapper;
import com.glw.backend.mapper.PurchaseOrderMapper;
import com.glw.backend.model.entity.FinAllocation;
import com.glw.backend.model.entity.SalesOrder;
import com.glw.backend.model.entity.PurchaseOrder;
import com.glw.backend.model.enums.SalesOrderStatus;
import com.glw.backend.model.enums.PurchaseOrderStatus;
import com.glw.backend.service.FinanceOpenItemService;
import com.glw.backend.service.FinancePaymentService;
import com.glw.backend.service.FinanceAllocationService;
import com.glw.backend.service.FinanceAuditService;
import com.glw.backend.service.FinanceLockService;
import com.glw.backend.service.SalesOrderService;
import com.glw.backend.service.PurchaseOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
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
    private final PurchaseOrderService purchaseOrderService;
    private final SalesOrderMapper salesOrderMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;

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
        auditService.record("ALLOCATION", row.getId(), "ALLOCATE",
                input.getAmount().toPlainString() + " paymentId=" + p.getId() + " openItemId=" + o.getId());
        if ("AR".equals(o.getDirection()) && o.getOrderId() != null)
            salesOrderService.refreshCompletionStatus(o.getOrderId());
        else if ("AP".equals(o.getDirection()) && o.getOrderId() != null)
            purchaseOrderService.refreshCompletionStatus(o.getOrderId());
        return finAllocationMapper.allocation(row.getId());
    }

    public static void validateAllocation(Payment p, OpenItem o, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 4) throw bad("核销金额须大于零且最多四位小数");
        if ("VOID".equals(p.getStatus())) throw bad("已作废收付款不能核销");
        if (!Objects.equals(p.getDirection(), o.getDirection()) || !Objects.equals(p.getPartnerId(), o.getPartnerId()) || !Objects.equals(p.getCurrency(), o.getCurrency()))
            throw bad("只能核销同方向、同往来单位、同币种的记录");
        boolean prepaymentForOrder = "PREPAYMENT".equals(p.getPaymentType()) && p.getOrderId() != null;
        if (prepaymentForOrder && !Objects.equals(o.getOrderId(), p.getOrderId()))
            throw bad("AR".equals(p.getDirection()) ? "预收款只能核销关联订单的应收" : "预付款只能核销关联订单的应付");
        boolean allocatable = "RECONCILED".equals(o.getReconcileStatus())
                || (prepaymentForOrder && "OPEN".equals(o.getReconcileStatus()));
        if (!allocatable) throw bad("对账确认后才能核销");
        if (amount.compareTo(p.getAmount().subtract(p.getAllocatedAmount())) > 0 || amount.compareTo(o.getGrossAmount().subtract(o.getSettledAmount())) > 0)
            throw bad("核销金额超过未分配余额或未结金额");
    }

    @Transactional
    @Override
    public SettleOrderResult settleOrder(SettleOrderInput input) {
        lockService.lock();
        long partnerId;
        if ("AR".equals(input.getDirection())) {
            SalesOrder order = salesOrderMapper.selectById(input.getOrderId());
            if (order == null) throw missing();
            if (order.getOrderStatus() == SalesOrderStatus.CANCELLED) throw bad("已取消的销售订单不能收款");
            partnerId = order.getCustomerId();
            // 先款后货：收款上限是订单金额扣除已登记收款后的余额，不要求先发货产生应收。
            if (finPaymentMapper.paymentByKey(input.getRequestKey()) == null) {
                BigDecimal orderTotal = salesOrderMapper.orderTotal(input.getOrderId());
                if (orderTotal != null && orderTotal.signum() > 0) {
                    BigDecimal paid = finPaymentMapper.orderPaymentTotal("AR", input.getOrderId());
                    BigDecimal payable = orderTotal.subtract(paid == null ? BigDecimal.ZERO : paid);
                    if (payable.signum() <= 0) throw bad("该销售订单已收清，不能再收款");
                    if (input.getAmount().compareTo(payable) > 0)
                        throw bad("收款金额不能超过订单未收金额（可收 " + payable.stripTrailingZeros().toPlainString() + " 元）");
                }
            }
        } else {
            PurchaseOrder order = purchaseOrderMapper.selectById(input.getOrderId());
            if (order == null) throw missing();
            if (order.getOrderStatus() == PurchaseOrderStatus.CANCELLED) throw bad("已取消的采购订单不能付款");
            partnerId = order.getSupplierId();
            // 先款后货：付款上限是订单金额扣除已登记付款后的余额，不要求先入库产生应付。
            if (finPaymentMapper.paymentByKey(input.getRequestKey()) == null) {
                BigDecimal orderTotal = purchaseOrderMapper.orderTotal(input.getOrderId());
                if (orderTotal != null && orderTotal.signum() > 0) {
                    BigDecimal paid = finPaymentMapper.orderPaymentTotal("AP", input.getOrderId());
                    BigDecimal payable = orderTotal.subtract(paid == null ? BigDecimal.ZERO : paid);
                    if (payable.signum() <= 0) throw bad("该采购订单已付清，不能再付款");
                    if (input.getAmount().compareTo(payable) > 0)
                        throw bad("付款金额不能超过订单未付金额（可付 " + payable.stripTrailingZeros().toPlainString() + " 元）");
                }
            }
        }

        PaymentInput paymentInput = new PaymentInput();
        paymentInput.setRequestKey(input.getRequestKey());
        paymentInput.setDirection(input.getDirection());
        paymentInput.setOrderId(input.getOrderId());
        paymentInput.setPartnerId(partnerId);
        paymentInput.setPaymentDate(input.getPaymentDate());
        paymentInput.setAmount(input.getAmount());
        paymentInput.setBankReference(input.getBankReference());
        paymentInput.setRemark(input.getRemark());
        Payment payment = paymentService.createPayment(paymentInput).payment();

        List<OpenItem> items = finOpenItemMapper.openItemsByOrder(input.getDirection(), input.getOrderId());
        BigDecimal outstanding = items.stream()
                .map(item -> item.getGrossAmount().subtract(item.getSettledAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = input.getAmount();
        // 按业务日期从早到晚核销已对账确认的开项；未确认的保留为未分配余额。
        for (OpenItem item : items) {
            if (!"RECONCILED".equals(item.getReconcileStatus())) continue;
            BigDecimal available = item.getGrossAmount().subtract(item.getSettledAmount());
            if (available.signum() <= 0) continue;
            BigDecimal amount = remaining.min(available);
            if (amount.signum() <= 0) break;
            Allocate allocate = new Allocate();
            allocate.setRequestKey(input.getRequestKey() + ":" + item.getId());
            allocate.setPaymentId(payment.getId());
            allocate.setOpenItemId(item.getId());
            allocate.setAmount(amount);
            allocate(allocate);
            remaining = remaining.subtract(amount);
        }

        BigDecimal allocated = input.getAmount().subtract(remaining);
        // 没有可核销的已确认应付，或付款超过可核销金额时，剩余部分作为预付款挂在订单上，后续手工冲应付。
        if (remaining.signum() > 0) {
            finPaymentMapper.updateOrderAndType(payment.getId(), input.getOrderId(), "PREPAYMENT");
            auditService.record("PAYMENT", payment.getId(), "MARK_PREPAYMENT",
                    "orderId=" + input.getOrderId() + " unallocated=" + remaining.toPlainString());
        }
        PaymentDetail detail = paymentService.payment(payment.getId());
        SettleOrderResult result = new SettleOrderResult();
        result.setPayment(detail.payment());
        result.setAllocations(detail.allocations());
        result.setOrderOutstanding(outstanding);
        result.setAllocatedAmount(allocated);
        result.setUnallocatedAmount(remaining);
        return result;
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
        changed(finAllocationMapper.reverse(id)); auditService.record("ALLOCATION", id, "REVERSE",
                row.getAmount().toPlainString() + " paymentId=" + row.getPaymentId() + " openItemId=" + row.getOpenItemId());
        if (o != null && "AR".equals(o.getDirection()) && o.getOrderId() != null)
            salesOrderService.refreshCompletionStatus(o.getOrderId());
        if (o != null && "AP".equals(o.getDirection()) && o.getOrderId() != null)
            purchaseOrderService.refreshCompletionStatus(o.getOrderId());
    }
}
