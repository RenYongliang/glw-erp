package com.glw.backend.finance;

import com.glw.backend.finance.FinanceModels.OpenItem;
import com.glw.backend.finance.FinanceModels.Payment;
import com.glw.backend.mapper.FinLockMapper;
import com.glw.backend.mapper.FinProfileMapper;
import com.glw.backend.mapper.FinOpenItemMapper;
import com.glw.backend.mapper.FinStatementMapper;
import com.glw.backend.mapper.FinStatementItemMapper;
import com.glw.backend.mapper.FinPaymentMapper;
import com.glw.backend.mapper.FinAllocationMapper;
import com.glw.backend.mapper.FinAuditMapper;
import com.glw.backend.service.FinanceAuditService;
import com.glw.backend.service.FinanceLockService;
import com.glw.backend.service.DocumentNumberService;
import com.glw.backend.service.FinanceStatementTemplateService;
import com.glw.backend.service.impl.FinanceOpenItemServiceImpl;
import com.glw.backend.service.impl.FinanceStatementServiceImpl;
import com.glw.backend.service.impl.FinancePaymentServiceImpl;
import com.glw.backend.service.impl.FinanceAllocationServiceImpl;
import com.glw.backend.service.impl.FinanceProfileServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.mockito.Mockito.mock;

abstract class FinanceTestFixture {
    final FinOpenItemMapper finOpenItem = mock(FinOpenItemMapper.class);
    final FinStatementMapper finStatement = mock(FinStatementMapper.class);
    final FinStatementItemMapper finStatementItem = mock(FinStatementItemMapper.class);
    final FinPaymentMapper finPayment = mock(FinPaymentMapper.class);
    final FinAllocationMapper finAllocation = mock(FinAllocationMapper.class);
    final FinProfileMapper finProfile = mock(FinProfileMapper.class);
    final FinAuditMapper finAudit = mock(FinAuditMapper.class);
    final FinLockMapper finLock = mock(FinLockMapper.class);
    final FinanceLockService lock = mock(FinanceLockService.class);
    final FinanceAuditService audit = mock(FinanceAuditService.class);
    final DocumentNumberService numbers = mock(DocumentNumberService.class);
    final FinanceStatementTemplateService statementTemplates = mock(FinanceStatementTemplateService.class);
    final FinanceProfileServiceImpl profile = new FinanceProfileServiceImpl(finProfile, lock, audit);
    final FinanceOpenItemServiceImpl open = new FinanceOpenItemServiceImpl(finOpenItem, finProfile, lock, audit);
    final FinanceStatementServiceImpl statements = new FinanceStatementServiceImpl(finStatement, finOpenItem, finStatementItem, finAllocation, lock, audit, open, statementTemplates, numbers);
    final FinancePaymentServiceImpl cash = new FinancePaymentServiceImpl(finPayment, finAllocation, lock, audit, profile, numbers);
    final FinanceAllocationServiceImpl clearing = new FinanceAllocationServiceImpl(finAllocation, finPayment, finOpenItem, lock, audit, open, cash);
    OpenItem item() {
        var o = new OpenItem(); o.setId(1L); o.setDirection("AR"); o.setPartnerId(6L); o.setCurrency("CNY");
        o.setSourceType("OUTBOUND"); o.setSourceLineId(10L); o.setSourceNo("OUT20261028001");
        o.setPeriod("2026-10"); o.setBizDate(LocalDate.of(2026,10,10)); o.setQuantity(100);
        o.setUnitPrice(new BigDecimal("1.1300")); o.setGrossAmount(new BigDecimal("113.00"));
        o.setTaxIncluded(1); o.setReconcileStatus("RECONCILED"); o.setSettledAmount(BigDecimal.ZERO);
        return o;
    }
    Payment payment() {
        var p = new Payment(); p.setId(1L); p.setDirection("AR"); p.setPartnerId(6L); p.setCurrency("CNY");
        p.setStatus("RECEIVED"); p.setAmount(new BigDecimal("200.00")); p.setAllocatedAmount(BigDecimal.ZERO); return p;
    }
}
