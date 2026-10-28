package com.glw.backend.finance;

/** Ledger-derived payment status returned to order lists. */
public final class FinanceOrderStatus {
    private FinanceOrderStatus() {}
    public record OrderStatus(Long orderId, String paymentStatus) {}
}
