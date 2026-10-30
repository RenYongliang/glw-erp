package com.glw.backend.finance;

import com.glw.backend.finance.FinanceModels.OpenItem;
import com.glw.backend.finance.FinanceModels.Profile;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 账单周期计算。
 *
 * <p>账单月：配置截止日 C 时，基准日在 C 日（含）之前归当月，之后归下月；未配置时按自然月。
 * <p>账单期间：配置截止日时，账单月 M 的区间为 (M-1) 月 C+1 日到 M 月 C 日（超出月长取月末）；
 * 未配置时为 M 月整月。周期跨 N 个月时，期间取首月区间起点到末月区间终点。
 * <p>出单：出单月 = 账单结束月 + statementLagMonths，出单日 = statementDay。
 * <p>到期：固定天数（从账单期末/出单日/出单月末起算）或约定日历日（账单结束月 + 偏移月，指定日或月末）。
 */
public final class BillingCalendar {
    private BillingCalendar() {}

    /** 某个账单月的记账区间。 */
    public record Band(LocalDate start, LocalDate end) {}

    /** 一份账单的期间、出单与到期。 */
    public record Plan(YearMonth startMonth, YearMonth endMonth, Band period,
                       YearMonth issueMonth, LocalDate issueDate, LocalDate dueDate) {}

    /** 一个账单周期及其应包含的记录。 */
    public record Cycle(YearMonth startMonth, YearMonth endMonth, LocalDate periodStart, LocalDate periodEnd,
                        YearMonth issueMonth, LocalDate issueDate, LocalDate dueDate, List<OpenItem> items) {
        public String periodLabel() { return BillingCalendar.periodLabel(startMonth, endMonth); }
    }

    /** 基准日所属账单月。 */
    public static YearMonth billingMonth(LocalDate basisDate, Integer cutoffDay) {
        YearMonth month = YearMonth.from(basisDate);
        if (cutoffDay == null || cutoffDay <= 0) return month;
        return basisDate.getDayOfMonth() <= cutoffDay ? month : month.plusMonths(1);
    }

    /** 某个账单月的记账区间。 */
    public static Band monthBand(YearMonth month, Integer cutoffDay) {
        if (cutoffDay == null || cutoffDay <= 0) return new Band(month.atDay(1), month.atEndOfMonth());
        YearMonth previous = month.minusMonths(1);
        return new Band(previous.atDay(clamp(cutoffDay + 1, previous)), month.atDay(clamp(cutoffDay, month)));
    }

    /** 从起始账单月算起、跨 {@code months} 个月的账单计划。 */
    public static Plan plan(Profile profile, YearMonth startMonth, int months) {
        int span = Math.max(1, months);
        YearMonth endMonth = startMonth.plusMonths(span - 1);
        Integer cutoff = profile == null ? null : profile.getCutoffDay();
        Band period = new Band(monthBand(startMonth, cutoff).start(), monthBand(endMonth, cutoff).end());
        YearMonth issueMonth = endMonth.plusMonths(profile == null ? 1 : Math.max(0, profile.getStatementLagMonths()));
        LocalDate issueDate = issueMonth.atDay(clamp(profile == null ? 1 : profile.getStatementDay(), issueMonth));
        return new Plan(startMonth, endMonth, period, issueMonth, issueDate,
                dueDate(profile, period.end(), issueDate, endMonth));
    }

    /** 到期日：固定天数或约定日历日。 */
    public static LocalDate dueDate(Profile profile, LocalDate periodEnd, LocalDate issueDate, YearMonth periodEndMonth) {
        if (profile == null) return periodEnd;
        if ("CALENDAR".equals(profile.getDueRule())) {
            YearMonth target = periodEndMonth.plusMonths(Math.max(0, profile.getDueMonthOffset()));
            int day = profile.getDueDay();
            return target.atDay(day <= 0 ? target.lengthOfMonth() : clamp(day, target));
        }
        LocalDate base = switch (profile.getCreditBasis() == null ? "PERIOD_END" : profile.getCreditBasis()) {
            case "ISSUE_DATE" -> issueDate;
            case "ISSUE_MONTH_END" -> YearMonth.from(issueDate).atEndOfMonth();
            default -> periodEnd;
        };
        return base.plusDays(Math.max(0, profile.getCreditDays()));
    }

    /**
     * 结算档案的规则自检，返回第一条问题，合法时返回 {@code null}。
     *
     * <p>关键约束：出单日必须晚于账单期末（自然月、截止 31 日的账单只能次月及以后出单；
     * 截止 1–30 日时当月出单要求出单日在截止日之后）；约定付款日不能早于出单月，
     * 同一月内还要晚于出单日。
     */
    public static String profileProblem(Profile p) {
        if (p == null) return "结算档案不能为空";
        int cutoff = p.getCutoffDay() == null ? 0 : p.getCutoffDay();
        int lag = p.getStatementLagMonths();
        int day = p.getStatementDay();
        if (cutoff < 0 || cutoff > 31) return "账单截止日应为 1–31 日，留空按自然月";
        if (p.getCycleMonths() < 1 || p.getCycleMonths() > 12) return "账单周期应为 1–12 个月";
        if (lag < 0 || lag > 12) return "出单月偏移应为 0–12 个月";
        if (day < 1 || day > 31) return "出单日应为 1–31 日";
        if (lag == 0) {
            if (cutoff <= 0) return "自然月账单要到月末才结束，出单月请选次月或更晚";
            if (cutoff >= 31) return "截止日 31 日时账单期末即月末，出单月请选次月或更晚";
            if (day <= cutoff) return "出单日必须晚于截止日：截止 " + cutoff + " 日时出单日至少 " + (cutoff + 1) + " 日";
        }
        String rule = p.getDueRule() == null ? "CREDIT_DAYS" : p.getDueRule();
        if ("CALENDAR".equals(rule)) {
            int offset = p.getDueMonthOffset();
            int dueDay = p.getDueDay();
            if (offset < 0 || offset > 12) return "约定付款月偏移应为 0–12 个月";
            if (dueDay < 0 || dueDay > 31) return "约定付款日应为 0–31 日，0 表示月末";
            if (offset < lag) return "约定付款日不能早于出单月";
            if (offset == lag && (dueDay <= 0 ? 31 : dueDay) <= day) return "同一月内约定付款日要晚于出单日";
        } else if ("CREDIT_DAYS".equals(rule)) {
            int creditDays = p.getCreditDays();
            if (creditDays < 0 || creditDays > 3650) return "账期天数应为 0–3650 天";
            if (!java.util.Set.of("PERIOD_END", "ISSUE_DATE", "ISSUE_MONTH_END")
                    .contains(p.getCreditBasis() == null ? "PERIOD_END" : p.getCreditBasis()))
                return "账期起算方式不正确";
        } else {
            return "到期规则不正确";
        }
        return null;
    }

    /** 对账单期间标签：单月为 {@code 2026-09}，跨月为 {@code 2026-08~2026-09}。 */
    public static String periodLabel(YearMonth start, YearMonth end) {
        return start.equals(end) ? end.toString() : start + "~" + end;
    }

    /**
     * 把记录切分为账单周期：以最早一条记录的账单月起算，覆盖 cycleMonths 个月；
     * 超出该窗口的下一批记录从它自己的账单月重新起算，中间没有记录的周期直接跳过。
     */
    public static List<Cycle> cycles(List<OpenItem> items, Profile profile) {
        int span = profile == null ? 1 : Math.max(1, profile.getCycleMonths());
        Integer cutoff = profile == null ? null : profile.getCutoffDay();
        List<OpenItem> sorted = items.stream()
                .sorted(Comparator.comparing(OpenItem::getBasisDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(OpenItem::getId))
                .toList();
        List<Cycle> result = new ArrayList<>();
        int cursor = 0;
        while (cursor < sorted.size()) {
            YearMonth start = settleMonth(sorted.get(cursor), cutoff);
            YearMonth end = start.plusMonths(span - 1);
            List<OpenItem> group = new ArrayList<>();
            int next = cursor;
            while (next < sorted.size() && !settleMonth(sorted.get(next), cutoff).isAfter(end)) {
                group.add(sorted.get(next++));
            }
            Plan plan = plan(profile, start, span);
            result.add(new Cycle(start, end, plan.period().start(), plan.period().end(),
                    plan.issueMonth(), plan.issueDate(), plan.dueDate(), List.copyOf(group)));
            cursor = next;
        }
        return result;
    }

    /** 单条记录的账单月、期间与到期日；基准日缺失时标记为待签收。 */
    public static void applyItem(OpenItem item, Profile profile) {
        Integer cutoff = profile == null ? null : profile.getCutoffDay();
        if (item.getBasisDate() == null) {
            item.setBasisReady(0);
            item.setPeriod(null); item.setSettleMonth(null);
            item.setPeriodStart(null); item.setPeriodEnd(null); item.setDueDate(null);
            return;
        }
        YearMonth month = billingMonth(item.getBasisDate(), cutoff);
        Plan plan = plan(profile, month, 1);
        item.setBasisReady(1);
        item.setPeriod(month.toString());
        item.setSettleMonth(month.toString());
        item.setPeriodStart(plan.period().start());
        item.setPeriodEnd(plan.period().end());
        item.setDueDate(plan.dueDate());
    }

    private static YearMonth settleMonth(OpenItem item, Integer cutoff) {
        String settled = item.getSettleMonth();
        if (settled != null && !settled.isBlank()) return YearMonth.parse(settled);
        if (item.getBasisDate() == null) throw new IllegalStateException("结算基准日缺失，无法归入账单月");
        return billingMonth(item.getBasisDate(), cutoff);
    }

    private static int clamp(int day, YearMonth month) {
        return Math.min(Math.max(day, 1), month.lengthOfMonth());
    }
}
