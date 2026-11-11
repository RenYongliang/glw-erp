package com.glw.backend.finance;

import java.math.BigDecimal;

/** Ledger-derived payment status returned to order lists. */
public final class FinanceOrderStatus {
    private FinanceOrderStatus() {}
    public record OrderStatus(Long orderId, String paymentStatus) {}
    /** 订单下已登记但尚未核销的预付款余额。 */
    public record Prepayment(Long orderId, BigDecimal amount) {}
}
