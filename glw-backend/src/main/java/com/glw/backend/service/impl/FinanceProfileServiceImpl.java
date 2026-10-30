package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.finance.FinanceModels;
import com.glw.backend.finance.FinanceModels.Partner;
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

/** Partner settlement terms. */
@Service
@Profile("!mock")
@RequiredArgsConstructor
public class FinanceProfileServiceImpl extends ServiceImpl<FinProfileMapper, FinProfile>
        implements FinanceProfileService {
    private final FinProfileMapper finProfileMapper;
    private final FinanceLockService lockService;
    private final FinanceAuditService auditService;

    @Override
    public List<Partner> partners(String direction) { return finProfileMapper.partners(direction(direction)); }

    @Override
    public List<FinanceModels.Profile> profiles(String direction) { return finProfileMapper.profiles(direction(direction)); }

    @Transactional
    @Override
    public void saveProfile(FinanceModels.Profile input) {
        lockService.lock(); partner(input.getDirection(), input.getPartnerId());
        String problem = com.glw.backend.finance.BillingCalendar.profileProblem(input);
        if (problem != null) throw bad(problem);
        finProfileMapper.saveProfile(input);
        String rule = input.getDueRule() == null ? "CREDIT_DAYS" : input.getDueRule();
        auditService.record("PROFILE", input.getPartnerId(), "SAVE", input.getDirection() + ":" + rule
                + ":" + ("CALENDAR".equals(rule)
                        ? input.getDueMonthOffset() + "m" + input.getDueDay() + "d"
                        : input.getCreditBasis() + "+" + input.getCreditDays() + "d"));
    }

    @Override
    public Partner partner(String direction, Long id) { return partners(direction).stream().filter(p -> p.getId().equals(id)).findFirst().orElseThrow(() -> bad("往来单位不存在")); }

    private String direction(String value) { if (!Set.of("AR", "AP").contains(value)) throw bad("方向必须为 AR 或 AP"); return value; }
}
