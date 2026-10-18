package com.glw.backend;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.glw.backend.common.BusinessException;
import com.glw.backend.mapper.DocumentNumberMapper;
import com.glw.backend.service.DocumentNumberService;
import com.glw.backend.service.DocumentNumberService.Kind;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DocumentNumberServiceTest {
    @Test
    void kindsAndDatesHaveIndependentSequencesAndUseBusinessDate() {
        var mapper = mock(DocumentNumberMapper.class);
        var service = new DocumentNumberService(mapper);
        var day = LocalDate.of(2026, 10, 18);
        for (var entry : java.util.Map.of(Kind.SALES, "SO", Kind.PURCHASE, "GL", Kind.INBOUND, "IN", Kind.OUTBOUND, "OUT").entrySet()) {
            assertThat(service.next(entry.getKey(), day.atTime(23, 59))).isEqualTo(entry.getValue() + "20261018001");
            verify(mapper).updateCounter(entry.getValue(), day, 1);
        }
        assertThat(service.next(Kind.INBOUND, day.plusDays(1).atStartOfDay())).isEqualTo("IN20261019001");
        verify(mapper).updateCounter("IN", day.plusDays(1), 1);
    }

    @Test
    void continuesAfterImportedNumbersAndNeverReusesPreviouslyAllocatedSequence() {
        var mapper = mock(DocumentNumberMapper.class);
        var service = new DocumentNumberService(mapper);
        var day = LocalDate.of(2026, 10, 18);
        when(mapper.lockCounter("IN", day)).thenReturn(2, 9);
        when(mapper.existingMax("inbound_order", "inbound_order_no", "IN20261018")).thenReturn(7, 3);
        assertThat(service.next(Kind.INBOUND, day.atStartOfDay())).isEqualTo("IN20261018008");
        assertThat(service.next(Kind.INBOUND, day.atStartOfDay())).isEqualTo("IN20261018010");
        var ordered = inOrder(mapper);
        ordered.verify(mapper).ensureCounter("IN", day);
        ordered.verify(mapper).lockCounter("IN", day);
        ordered.verify(mapper).existingMax("inbound_order", "inbound_order_no", "IN20261018");
        ordered.verify(mapper).updateCounter("IN", day, 8);
    }

    @Test
    void rejectsDailyOverflow() {
        var mapper = mock(DocumentNumberMapper.class);
        when(mapper.lockCounter(any(), any())).thenReturn(999);
        assertThatThrownBy(() -> new DocumentNumberService(mapper).next(Kind.INBOUND, LocalDate.of(2026, 10, 18).atStartOfDay()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("999");
        verify(mapper, never()).updateCounter(any(), any(), anyInt());
    }
}
