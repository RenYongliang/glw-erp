package com.glw.backend.finance;

import com.glw.backend.finance.FinanceModels.Allocation;
import com.glw.backend.finance.FinanceModels.Allocate;
import com.glw.backend.service.impl.FinanceAllocationServiceImpl;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinanceAllocationServiceTest extends FinanceTestFixture {
    @Test void rejectsWrongPartnerCurrencyUnconfirmedAndExcessiveAllocations() {
        var o = item(); var p = payment(); FinanceAllocationServiceImpl.validateAllocation(p, o, new BigDecimal("50"));
        assertThatThrownBy(() -> FinanceAllocationServiceImpl.validateAllocation(p, o, new BigDecimal("113.01"))).hasMessageContaining("超过");
        o.setPartnerId(7L); assertThatThrownBy(() -> FinanceAllocationServiceImpl.validateAllocation(p, o, BigDecimal.ONE)).hasMessageContaining("往来单位");
        o.setPartnerId(6L); o.setReconcileStatus("OPEN"); assertThatThrownBy(() -> FinanceAllocationServiceImpl.validateAllocation(p, o, BigDecimal.ONE)).hasMessageContaining("确认后");
        o.setReconcileStatus("RECONCILED"); o.setCurrency("USD"); assertThatThrownBy(() -> FinanceAllocationServiceImpl.validateAllocation(p, o, BigDecimal.ONE)).hasMessageContaining("币种");
        o.setCurrency("CNY"); p.setAllocatedAmount(new BigDecimal("199.50")); assertThatThrownBy(() -> FinanceAllocationServiceImpl.validateAllocation(p, o, BigDecimal.ONE)).hasMessageContaining("超过");
    }

    @Test void allocationReplayDoesNotDoubleDeductButChangedPayloadIsRejected() {
        var row = new Allocation(); row.setId(2L); row.setPaymentId(1L); row.setOpenItemId(3L); row.setAmount(BigDecimal.TEN);
        when(finAllocation.allocationByKey("once")).thenReturn(row);
        var input = new Allocate(); input.setRequestKey("once"); input.setPaymentId(1L); input.setOpenItemId(3L); input.setAmount(BigDecimal.TEN);
        assertThat(clearing.allocate(input)).isSameAs(row); verify(finOpenItem, never()).settle(any(), any());
        input.setAmount(BigDecimal.ONE); assertThatThrownBy(() -> clearing.allocate(input)).hasMessageContaining("不同核销");
    }

    @Test void reverseRestoresBothBalancesAndKeepsOriginalRecord() {
        var row = new Allocation(); row.setId(3L); row.setPaymentId(1L); row.setOpenItemId(2L); row.setAmount(BigDecimal.TEN); row.setStatus("ACTIVE");
        when(finAllocation.allocation(3L)).thenReturn(row); when(finPayment.allocate(1L, BigDecimal.TEN.negate())).thenReturn(1);
        when(finOpenItem.settle(2L, BigDecimal.TEN.negate())).thenReturn(1); when(finAllocation.reverse(3L)).thenReturn(1);
        clearing.reverse(3L); verify(finAllocation).reverse(3L); verify(finOpenItem).settle(2L, BigDecimal.TEN.negate());
        row.setStatus("REVERSED"); clearing.reverse(3L); verify(finPayment, times(1)).allocate(any(), any());
    }
}
