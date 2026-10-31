package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.finance.BillingCalendar;
import com.glw.backend.finance.FinanceModels;
import com.glw.backend.finance.FinanceModels.OpenItem;
import com.glw.backend.finance.FinanceModels.Partner;
import com.glw.backend.mapper.FinOpenItemMapper;
import com.glw.backend.mapper.FinProfileMapper;
import com.glw.backend.model.entity.FinProfile;
import com.glw.backend.service.FinanceProfileService;
import com.glw.backend.service.FinanceAuditService;
import com.glw.backend.service.FinanceLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;
import static com.glw.backend.finance.FinanceSupport.*;

/**
 * Partner settlement terms.
 *
 * <p>{@code partner_id = 0} 是公司统一结算方案；客户档案的 {@code plan_source = DEFAULT}
 * 表示沿用该方案：保存客户行时从默认行复制，保存默认行时在同一事务里同步给所有 DEFAULT 客户，
 * 但各客户自己的自动出单开关不受影响。
 */
@Service
@Profile("!mock")
@RequiredArgsConstructor
public class FinanceProfileServiceImpl extends ServiceImpl<FinProfileMapper, FinProfile>
        implements FinanceProfileService {
    /** 公司默认方案行使用的 partner_id。 */
    public static final long COMPANY_PARTNER_ID = 0L;

    private final FinProfileMapper finProfileMapper;
    private final FinOpenItemMapper finOpenItemMapper;
    private final FinanceLockService lockService;
    private final FinanceAuditService auditService;

    @Override
    public List<Partner> partners(String direction) { return finProfileMapper.partners(direction(direction)); }

    @Override
    public List<FinanceModels.Profile> profiles(String direction) { return finProfileMapper.profiles(direction(direction)); }

    @Transactional
    @Override
    public FinanceModels.SaveProfileResult saveProfile(FinanceModels.Profile input) {
        String direction = direction(input.getDirection());
        input.setDirection(direction);
        if (input.getPartnerId() == null) throw bad("请选择往来单位");
        lockService.lock();

        boolean company = input.getPartnerId() == COMPANY_PARTNER_ID;
        if (company) {
            // 默认行只是方案模板，本身不参与出单。
            input.setPlanSource("CUSTOM");
            input.setAutoGenerate(false);
            input.setPartnerName("公司默认");
        } else {
            partner(direction, input.getPartnerId());
            if ("DEFAULT".equals(input.getPlanSource())) {
                FinanceModels.Profile fallback = finProfileMapper.companyProfile(direction);
                if (fallback == null) throw bad("请先在「公司默认」里配置统一的结算方案");
                copyPlan(fallback, input);
            } else {
                input.setPlanSource("CUSTOM");
            }
        }
        String problem = BillingCalendar.profileProblem(input);
        if (problem != null) throw bad(problem);
        finProfileMapper.saveProfile(input);

        String rule = input.getDueRule() == null ? "CREDIT_DAYS" : input.getDueRule();
        auditService.record("PROFILE", input.getPartnerId(), "SAVE", direction + ":" + input.getPlanSource()
                + ":" + rule + ":" + ("CALENDAR".equals(rule)
                        ? input.getDueMonthOffset() + "m" + input.getDueDay() + "d"
                        : input.getCreditBasis() + "+" + input.getCreditDays() + "d"));

        int syncedPartners = 0;
        int recalculated = 0;
        if (company) {
            List<Long> partners = finProfileMapper.defaultPartnerIds(direction);
            finProfileMapper.syncDefault(direction);
            for (Long partnerId : partners) recalculated += recalcPending(direction, partnerId);
            syncedPartners = partners.size();
            if (syncedPartners > 0)
                auditService.record("PROFILE", COMPANY_PARTNER_ID, "SYNC_DEFAULT",
                        direction + " partners=" + syncedPartners + " items=" + recalculated);
        } else {
            recalculated = recalcPending(direction, input.getPartnerId());
        }
        return new FinanceModels.SaveProfileResult(syncedPartners, recalculated);
    }

    /**
     * 按当前档案重算未出账记录：账单月、期间、到期日在投影时算好存下来，档案一改就会过期。
     * 只处理未进入任何对账单、仍在待对账状态的记录，已出账的口径保持不变。
     */
    private int recalcPending(String direction, Long partnerId) {
        FinanceModels.Profile profile = finProfileMapper.profile(direction, partnerId);
        int updated = 0;
        for (OpenItem item : finOpenItemMapper.pendingBilling(direction, partnerId)) {
            BillingCalendar.applyItem(item, profile);
            updated += finOpenItemMapper.updateBilling(item);
        }
        if (updated > 0) auditService.record("OPEN_ITEM", partnerId, "RECALC_BILLING",
                direction + " items=" + updated);
        return updated;
    }

    /** 只复制方案字段，自动出单开关始终由客户自己控制。 */
    private static void copyPlan(FinanceModels.Profile from, FinanceModels.Profile to) {
        to.setCutoffDay(from.getCutoffDay());
        to.setCycleMonths(from.getCycleMonths());
        to.setStatementLagMonths(from.getStatementLagMonths());
        to.setStatementDay(from.getStatementDay());
        to.setDueRule(from.getDueRule());
        to.setCreditDays(from.getCreditDays());
        to.setCreditBasis(from.getCreditBasis());
        to.setDueMonthOffset(from.getDueMonthOffset());
        to.setDueDay(from.getDueDay());
        to.setReconcileBasis(from.getReconcileBasis());
    }

    @Override
    public Partner partner(String direction, Long id) { return partners(direction).stream().filter(p -> p.getId().equals(id)).findFirst().orElseThrow(() -> bad("往来单位不存在")); }

    private String direction(String value) { if (!Set.of("AR", "AP").contains(value)) throw bad("方向必须为 AR 或 AP"); return value; }
}
