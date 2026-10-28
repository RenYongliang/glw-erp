package com.glw.backend.finance;

import com.glw.backend.finance.FinanceModels.PaymentInput;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancePaymentServiceTest extends FinanceTestFixture {
    @Test void allocatedPaymentMustBeReversedBeforeVoiding() {
        var payment = payment();
        payment.setAllocatedAmount(BigDecimal.ONE);
        when(finPayment.payment(1L)).thenReturn(payment);

        assertThatThrownBy(() -> cash.voidPayment(1L)).hasMessageContaining("先反核销");
        verify(finPayment, never()).voidPayment(any());
    }

    @Test void paymentReplayIsIdempotentButCannotChangeItsAmount() {
        var payment = payment();
        payment.setPaymentDate(LocalDate.of(2026, 10, 28));
        payment.setBankReference("BANK-1");
        payment.setRemark("");
        when(finPayment.paymentByKey("same-request")).thenReturn(payment);
        when(finPayment.payment(payment.getId())).thenReturn(payment);

        var input = new PaymentInput();
        input.setRequestKey("same-request");
        input.setDirection(payment.getDirection());
        input.setPartnerId(payment.getPartnerId());
        input.setPaymentDate(payment.getPaymentDate());
        input.setAmount(payment.getAmount());
        input.setBankReference(" BANK-1 ");

        assertThat(cash.createPayment(input).payment()).isSameAs(payment);
        verify(finPayment, never()).insertPayment(any());

        input.setAmount(BigDecimal.ONE);
        assertThatThrownBy(() -> cash.createPayment(input)).hasMessageContaining("不同收付款");
    }
}
