package com.glw.backend.finance;

import com.glw.backend.finance.FinanceModels.Profile;

import com.glw.backend.finance.FinanceModels.TaxInput;
import com.glw.backend.service.impl.FinanceOpenItemServiceImpl;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinanceOpenItemServiceTest extends FinanceTestFixture {
    @Test void unknownTaxDoesNotBecomeZeroTaxAndDueDateUsesSnapshotTerms() {
        var o = item(); var profile = new Profile(); profile.setTermBasis("MONTH_END"); profile.setTermDays(30);
        FinanceOpenItemServiceImpl.prepare(o, profile);
        assertThat(o.getGrossAmount()).isEqualByComparingTo("113");
        assertThat(o.getTaxAmount()).isNull(); assertThat(o.getNetAmount()).isNull();
        assertThat(o.getDueDate()).isEqualTo(LocalDate.of(2026,11,30));
        o.setTaxRate(13); FinanceOpenItemServiceImpl.prepare(o, null);
        assertThat(o.getNetAmount()).isEqualByComparingTo("100"); assertThat(o.getTaxAmount()).isEqualByComparingTo("13");
    }

    @Test void exclusiveTaxWaitsForConfirmationAndRoundingIsExplicit() {
        var o = item(); o.setTaxIncluded(0);
        FinanceOpenItemServiceImpl.prepare(o, null);
        assertThat(o.getGrossAmount()).isNull(); assertThat(o.getReconcileStatus()).isEqualTo("PENDING_TAX");
        assertThat(o.getNetAmount()).isEqualByComparingTo("113.00");
        o.setTaxRate(13); o.setGrossAmount(new BigDecimal("100.0050")); FinanceOpenItemServiceImpl.prepare(o, null);
        assertThat(o.getNetAmount()).isEqualByComparingTo("100.01"); assertThat(o.getGrossAmount()).isEqualByComparingTo("113.01");
    }

    @Test void historicalRebuildOnlyInsertsMissingSnapshots() {
        var o = item(); when(finOpenItem.sources("AR", null)).thenReturn(List.of(o)); when(finOpenItem.sources("AP", null)).thenReturn(List.of());
        when(finOpenItem.bySource("OUTBOUND", 10L)).thenReturn(o);
        assertThat(open.rebuild()).isZero(); verify(finOpenItem, never()).insertOpen(any());
        when(finOpenItem.bySource("OUTBOUND", 10L)).thenReturn(null);
        assertThat(open.rebuild()).isEqualTo(1); verify(finOpenItem).insertOpen(o);
    }

    @Test void taxConfirmationCalculatesFromFrozenNetAndPreservesDueDate() {
        var o = item(); o.setReconcileStatus("PENDING_TAX"); o.setGrossAmount(null); o.setTaxIncluded(0);
        o.setNetAmount(new BigDecimal("100.00")); o.setDueDate(LocalDate.of(2026,11,30));
        when(finOpenItem.openItem(1L)).thenReturn(o); when(finOpenItem.resolveTax(o)).thenReturn(1);
        open.resolveTax(1L,new TaxInput(13));
        assertThat(o.getGrossAmount()).isEqualByComparingTo("113.00");
        assertThat(o.getDueDate()).isEqualTo(LocalDate.of(2026,11,30));
        verify(finOpenItem).resolveTax(o);
    }
}
