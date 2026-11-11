package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.finance.FinanceModels.Query;
import com.glw.backend.finance.FinanceModels.Page;
import com.glw.backend.finance.FinanceModels.Payment;
import com.glw.backend.finance.FinanceModels.Partner;
import com.glw.backend.finance.FinanceModels.PaymentInput;
import com.glw.backend.finance.FinanceModels.PaymentDetail;
import com.glw.backend.mapper.FinPaymentMapper;
import com.glw.backend.mapper.FinAllocationMapper;
import com.glw.backend.model.entity.FinPayment;
import com.glw.backend.service.FinancePaymentService;
import com.glw.backend.service.FinanceProfileService;
import com.glw.backend.service.FinanceAuditService;
import com.glw.backend.service.FinanceLockService;
import com.glw.backend.service.DocumentNumberService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import static com.glw.backend.finance.FinanceSupport.*;

/** Cash receipt/payment registration and cancellation. */
@Service
@Profile("!mock")
@RequiredArgsConstructor
public class FinancePaymentServiceImpl extends ServiceImpl<FinPaymentMapper, FinPayment>
        implements FinancePaymentService {
    private final FinPaymentMapper finPaymentMapper;
    private final FinAllocationMapper finAllocationMapper;
    private final FinanceLockService lockService;
    private final FinanceAuditService auditService;
    private final FinanceProfileService profileService;
    private final DocumentNumberService numbers;

    @Transactional
    @Override
    public PaymentDetail createPayment(PaymentInput input) {
        lockService.lock();
        input.setBankReference(input.getBankReference().trim());
        Payment existing = finPaymentMapper.paymentByKey(input.getRequestKey());
        if (existing != null) {
            if (!Objects.equals(existing.getDirection(), input.getDirection()) || !Objects.equals(existing.getPartnerId(), input.getPartnerId())
                    || !Objects.equals(existing.getOrderId(), input.getOrderId())
                    || existing.getAmount().compareTo(input.getAmount()) != 0 || !Objects.equals(existing.getPaymentDate(), input.getPaymentDate())
                    || !Objects.equals(existing.getBankReference(), input.getBankReference()) || !Objects.equals(existing.getRemark(), input.getRemark()))
                throw conflict("重复请求标识对应了不同收付款内容");
            return payment(existing.getId());
        }
        Partner partner = profileService.partner(input.getDirection(), input.getPartnerId());
        var p = new Payment();
        p.setDirection(input.getDirection()); p.setOrderId(input.getOrderId());
        p.setPaymentType("NORMAL"); p.setPartnerId(partner.getId()); p.setPartnerName(partner.getName());
        p.setRequestKey(input.getRequestKey()); p.setAmount(input.getAmount()); p.setPaymentDate(input.getPaymentDate());
        p.setBankReference(input.getBankReference().trim()); p.setRemark(input.getRemark());
        p.setPaymentNo(numbers.next("AR".equals(input.getDirection()) ? DocumentNumberService.Kind.RECEIPT : DocumentNumberService.Kind.PAYMENT, input.getPaymentDate().atStartOfDay()));
        finPaymentMapper.insertPayment(p);
        auditService.record("PAYMENT", p.getId(), "CREATE", p.getAmount().toPlainString());
        return payment(p.getId());
    }

    @Override
    public Page<Payment> payments(Query q) { return new Page<>(finPaymentMapper.payments(q), finPaymentMapper.countPayments(q)); }

    @Override
    public PaymentDetail payment(Long id) { return new PaymentDetail(requirePayment(id), finAllocationMapper.allocations(id, null)); }

    @Transactional
    @Override
    public void voidPayment(Long id) {
        lockService.lock(); Payment p = requirePayment(id);
        if ("VOID".equals(p.getStatus())) return;
        if (p.getAllocatedAmount().signum() != 0) throw bad("请先反核销全部核销记录，再作废收付款单");
        changed(finPaymentMapper.voidPayment(id)); auditService.record("PAYMENT", id, "VOID", "");
    }

    @Override
    public Payment requirePayment(Long id) { Payment row = finPaymentMapper.payment(id); if (row == null) throw missing(); return row; }
}
