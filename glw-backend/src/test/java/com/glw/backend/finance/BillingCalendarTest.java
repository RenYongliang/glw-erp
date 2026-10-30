package com.glw.backend.finance;

import com.glw.backend.finance.FinanceModels.OpenItem;
import com.glw.backend.finance.FinanceModels.Profile;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class BillingCalendarTest {
    private static Profile profile(Integer cutoff, int cycleMonths, int lag, int statementDay) {
        var p = new Profile();
        p.setCutoffDay(cutoff); p.setCycleMonths(cycleMonths);
        p.setStatementLagMonths(lag); p.setStatementDay(statementDay);
        p.setCreditBasis("PERIOD_END"); p.setCreditDays(0);
        return p;
    }

    private static OpenItem item(long id, LocalDate basisDate) {
        var o = new OpenItem(); o.setId(id); o.setBasisDate(basisDate);
        o.setDirection("AR"); o.setPartnerId(6L); o.setCurrency("CNY");
        o.setQuantity(1); o.setGrossAmount(java.math.BigDecimal.ONE);
        return o;
    }

    @Test void cutoffMovesLaterBusinessIntoNextBillingMonth() {
        assertThat(BillingCalendar.billingMonth(LocalDate.of(2026, 10, 25), 25)).isEqualTo(YearMonth.of(2026, 10));
        assertThat(BillingCalendar.billingMonth(LocalDate.of(2026, 10, 26), 25)).isEqualTo(YearMonth.of(2026, 11));
        assertThat(BillingCalendar.billingMonth(LocalDate.of(2026, 7, 28), 25)).isEqualTo(YearMonth.of(2026, 8));
        assertThat(BillingCalendar.billingMonth(LocalDate.of(2026, 10, 26), null)).isEqualTo(YearMonth.of(2026, 10));
    }

    @Test void twoMonthCycleCoversBothCutoffBands() {
        var plan = BillingCalendar.plan(profile(25, 2, 1, 1), YearMonth.of(2026, 8), 2);
        assertThat(plan.period().start()).isEqualTo(LocalDate.of(2026, 7, 26));
        assertThat(plan.period().end()).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(plan.issueDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(plan.dueDate()).isEqualTo(LocalDate.of(2026, 9, 25));
    }

    @Test void calendarDueDateFollowsAgreedPaymentDay() {
        var p = profile(null, 1, 1, 1);
        p.setDueRule("CALENDAR"); p.setDueMonthOffset(3); p.setDueDay(15);
        var january = BillingCalendar.plan(p, YearMonth.of(2026, 1), 1);
        assertThat(january.period().start()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(january.period().end()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(january.issueDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(january.dueDate()).isEqualTo(LocalDate.of(2026, 4, 15));

        var february = BillingCalendar.plan(p, YearMonth.of(2026, 2), 1);
        assertThat(february.dueDate()).isEqualTo(LocalDate.of(2026, 5, 15));
        var march = BillingCalendar.plan(p, YearMonth.of(2026, 3), 1);
        assertThat(march.dueDate()).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test void creditDaysStartFromPeriodEnd() {
        var p = profile(null, 1, 1, 1); p.setCreditDays(30);
        var plan = BillingCalendar.plan(p, YearMonth.of(2026, 9), 1);
        assertThat(plan.dueDate()).isEqualTo(LocalDate.of(2026, 10, 30));
    }

    @Test void gapRestartsCycleAtTheResumedOrderMonth() {
        var items = List.of(
                item(1, LocalDate.of(2026, 1, 10)),
                item(2, LocalDate.of(2026, 2, 12)),
                item(3, LocalDate.of(2026, 6, 8)));
        var cycles = BillingCalendar.cycles(items, profile(25, 2, 1, 1));
        assertThat(cycles).hasSize(2);
        assertThat(cycles.get(0).items()).extracting(OpenItem::getId).containsExactly(1L, 2L);
        assertThat(cycles.get(0).periodStart()).isEqualTo(LocalDate.of(2025, 12, 26));
        assertThat(cycles.get(0).periodEnd()).isEqualTo(LocalDate.of(2026, 2, 25));
        assertThat(cycles.get(1).items()).extracting(OpenItem::getId).containsExactly(3L);
        assertThat(cycles.get(1).periodStart()).isEqualTo(LocalDate.of(2026, 5, 26));
        assertThat(cycles.get(1).periodEnd()).isEqualTo(LocalDate.of(2026, 7, 25));
    }

    @Test void profileRejectsIssueDateBeforePeriodEnd() {
        assertThat(BillingCalendar.profileProblem(profile(null, 1, 0, 1))).contains("自然月");
        assertThat(BillingCalendar.profileProblem(profile(31, 1, 0, 1))).contains("31");
        assertThat(BillingCalendar.profileProblem(profile(25, 1, 0, 20))).contains("晚于截止日");
        assertThat(BillingCalendar.profileProblem(profile(25, 1, 0, 26))).isNull();
        assertThat(BillingCalendar.profileProblem(profile(25, 1, 1, 1))).isNull();
    }

    @Test void profileRejectsDueDateBeforeIssue() {
        var p = profile(null, 1, 1, 1);
        p.setDueRule("CALENDAR"); p.setDueMonthOffset(1); p.setDueDay(1);
        assertThat(BillingCalendar.profileProblem(p)).contains("晚于出单日");
        p.setDueDay(15);
        assertThat(BillingCalendar.profileProblem(p)).isNull();
        p.setDueMonthOffset(0);
        assertThat(BillingCalendar.profileProblem(p)).contains("不能早于出单月");
        p.setDueMonthOffset(1); p.setDueDay(0);
        assertThat(BillingCalendar.profileProblem(p)).isNull();
    }

    @Test void pendingSignatureItemsCarryNoBillingPeriod() {
        var o = item(1, null);
        BillingCalendar.applyItem(o, profile(25, 1, 1, 1));
        assertThat(o.getBasisReady()).isZero();
        assertThat(o.getSettleMonth()).isNull();
        assertThat(o.getPeriodStart()).isNull();
    }
}
