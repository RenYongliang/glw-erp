package com.glw.backend.finance;

import com.glw.backend.finance.FinanceModels.OpenItem;
import com.glw.backend.finance.FinanceModels.Profile;

import com.glw.backend.service.impl.FinanceOpenItemServiceImpl;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinanceOpenItemServiceTest extends FinanceTestFixture {
    @Test void inclusiveWithoutRateKeepsGrossAndLeavesNetTaxEmpty() {
        var o = item(); var profile = new Profile(); profile.setCreditBasis("PERIOD_END"); profile.setCreditDays(30);
        FinanceOpenItemServiceImpl.prepare(o, profile);
        assertThat(o.getGrossAmount()).isEqualByComparingTo("113");
        assertThat(o.getTaxAmount()).isNull(); assertThat(o.getNetAmount()).isNull();
        assertThat(o.getReconcileStatus()).isEqualTo("OPEN");
        assertThat(o.getDueDate()).isEqualTo(LocalDate.of(2026,11,30));
        o.setTaxRate(13); FinanceOpenItemServiceImpl.prepare(o, null);
        assertThat(o.getNetAmount()).isEqualByComparingTo("100"); assertThat(o.getTaxAmount()).isEqualByComparingTo("13");
    }

    @Test void exclusiveWithoutRateBillsTheQuotedAmountAndRoundingIsExplicit() {
        var o = item(); o.setTaxIncluded(0);
        FinanceOpenItemServiceImpl.prepare(o, null);
        assertThat(o.getReconcileStatus()).isEqualTo("OPEN");
        assertThat(o.getNetAmount()).isEqualByComparingTo("113.00");
        assertThat(o.getGrossAmount()).isEqualByComparingTo("113.00");
        assertThat(o.getTaxAmount()).isNull();
        o.setTaxRate(13); o.setGrossAmount(new BigDecimal("100.0050")); FinanceOpenItemServiceImpl.prepare(o, null);
        assertThat(o.getNetAmount()).isEqualByComparingTo("100.0050"); assertThat(o.getGrossAmount()).isEqualByComparingTo("113.0057");
    }

    @Test void batchesOfSameReceiptLineMergeIntoOnePayable() {
        var first = item();
        first.setDirection("AP"); first.setSourceType("INBOUND"); first.setSourceId(30L);
        first.setSourceLineId(38L); first.setOrderLineId(1L);
        first.setQuantity(3000); first.setGrossAmount(new BigDecimal("2100"));
        var second = item();
        second.setDirection("AP"); second.setSourceType("INBOUND"); second.setSourceId(30L);
        second.setSourceLineId(39L); second.setOrderLineId(1L);
        second.setQuantity(6000); second.setGrossAmount(new BigDecimal("4200"));
        when(finOpenItem.sources("AR", null)).thenReturn(List.of());
        when(finOpenItem.sources("AP", null)).thenReturn(List.of(first, second));
        when(finOpenItem.bySource("INBOUND", 38L)).thenReturn(null);
        assertThat(open.rebuild()).isEqualTo(1);
        var captor = org.mockito.ArgumentCaptor.forClass(OpenItem.class);
        verify(finOpenItem).insertOpen(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualTo(9000);
        assertThat(captor.getValue().getGrossAmount()).isEqualByComparingTo("6300");
    }

    @Test void historicalRebuildOnlyInsertsMissingSnapshots() {
        var o = item(); when(finOpenItem.sources("AR", null)).thenReturn(List.of(o)); when(finOpenItem.sources("AP", null)).thenReturn(List.of());
        when(finOpenItem.bySource("OUTBOUND", 10L)).thenReturn(o);
        assertThat(open.rebuild()).isZero(); verify(finOpenItem, never()).insertOpen(any());
        when(finOpenItem.bySource("OUTBOUND", 10L)).thenReturn(null);
        assertThat(open.rebuild()).isEqualTo(1); verify(finOpenItem).insertOpen(o);
    }
}
