package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.finance.FinanceModels.Query;
import com.glw.backend.finance.FinanceModels.Page;
import com.glw.backend.finance.FinanceModels.OpenItem;
import com.glw.backend.finance.FinanceModels.Statement;
import com.glw.backend.finance.FinanceModels.Generate;
import com.glw.backend.finance.FinanceModels.StatementDetail;
import com.glw.backend.mapper.FinOpenItemMapper;
import com.glw.backend.mapper.FinStatementMapper;
import com.glw.backend.mapper.FinStatementItemMapper;
import com.glw.backend.mapper.FinAllocationMapper;
import com.glw.backend.model.entity.FinStatement;
import com.glw.backend.model.entity.FinStatementTemplate;
import com.glw.backend.service.FinanceOpenItemService;
import com.glw.backend.service.FinanceStatementService;
import com.glw.backend.service.FinanceStatementTemplateService;
import com.glw.backend.service.FinanceAuditService;
import com.glw.backend.service.FinanceLockService;
import com.glw.backend.service.DocumentNumberService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.Locale;
import static com.glw.backend.finance.FinanceSupport.*;

/** Statement lifecycle and immutable statement snapshots. */
@Service
@Profile("!mock")
@RequiredArgsConstructor
public class FinanceStatementServiceImpl extends ServiceImpl<FinStatementMapper, FinStatement>
        implements FinanceStatementService {
    private final FinStatementMapper finStatementMapper;
    private final FinOpenItemMapper finOpenItemMapper;
    private final FinStatementItemMapper finStatementItemMapper;
    private final FinAllocationMapper finAllocationMapper;
    private final FinanceLockService lockService;
    private final FinanceAuditService auditService;
    private final FinanceOpenItemService openItemService;
    private final FinanceStatementTemplateService templateService;
    private final DocumentNumberService numbers;

    @Transactional
    @Override
    public StatementDetail generate(Generate input) {
        lockService.lock();
        var ids = input.getOpenItemIds().stream().distinct().sorted().toList();
        if (ids.size() != input.getOpenItemIds().size()) throw bad("不可重复选择对账项");
        List<OpenItem> items = ids.stream().map(openItemService::requireOpen).toList();
        OpenItem first = items.getFirst();
        for (OpenItem item : items) {
            if (!Objects.equals(item.getDirection(), first.getDirection()) || !Objects.equals(item.getPartnerId(), first.getPartnerId())
                    || !Objects.equals(item.getPeriod(), first.getPeriod()) || !Objects.equals(item.getCurrency(), first.getCurrency()))
                throw bad("同一对账单只能包含同方向、同往来单位、同月份、同币种的记录");
            if (!"OPEN".equals(item.getReconcileStatus())) throw conflict("对账项已被其他对账单使用，请刷新");
        }
        var statement = new Statement();
        statement.setDirection(first.getDirection()); statement.setPartnerId(first.getPartnerId());
        statement.setPartnerName(first.getPartnerName()); statement.setPeriod(first.getPeriod()); statement.setCurrency(first.getCurrency());
        statement.setGrossAmount(items.stream().map(OpenItem::getGrossAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        statement.setRemark(input.getRemark() == null ? "" : input.getRemark());
        FinStatementTemplate template = templateService.snapshotFor(first.getDirection(), first.getPartnerId());
        if (template != null) {
            statement.setTemplateId(template.getId());
            statement.setTemplateVersion(template.getVersion());
        }
        statement.setStatementNo(numbers.next("AR".equals(first.getDirection()) ? DocumentNumberService.Kind.STATEMENT_AR : DocumentNumberService.Kind.STATEMENT_AP, LocalDate.now().atStartOfDay()));
        finStatementMapper.insertStatement(statement);
        for (OpenItem item : items) {
            changed(finOpenItemMapper.setReconciliation(item.getId(), "OPEN", "IN_STATEMENT", statement.getId()));
            finStatementItemMapper.snapshot(statement.getId(), item.getId());
        }
        auditService.record("STATEMENT", statement.getId(), "CREATE", "items=" + ids.size());
        return statement(statement.getId());
    }

    @Override
    public Page<Statement> statements(Query q) { return new Page<>(finStatementMapper.statements(q), finStatementMapper.countStatements(q)); }

    @Override
    public StatementDetail statement(Long id) {
        return new StatementDetail(requireStatement(id), finStatementItemMapper.statementItems(id), finAllocationMapper.allocations(null, id));
    }

    @Transactional
    @Override
    public StatementDetail transition(Long id, String action) {
        lockService.lock();
        Statement s = requireStatement(id);
        if ("void".equals(action) && "VOID".equals(s.getStatus())) return statement(id);
        String expected, next;
        switch (action) {
            case "send" -> { expected = "DRAFT"; next = "SENT"; }
            case "confirm" -> { expected = "SENT"; next = "CONFIRMED"; }
            case "void" -> {
                if (!Set.of("DRAFT", "SENT").contains(s.getStatus())) throw bad("仅草稿或已发送对账单可以作废");
                expected = s.getStatus(); next = "VOID";
            }
            default -> throw bad("未知操作");
        }
        if (next.equals(s.getStatus())) return statement(id);
        if (!expected.equals(s.getStatus())) throw conflict("对账单状态已变化，请刷新");
        changed(finStatementMapper.statementStatus(id, expected, next));
        if ("CONFIRMED".equals(next) || "VOID".equals(next)) {
            for (OpenItem item : finStatementItemMapper.statementItems(id))
                changed(finOpenItemMapper.setReconciliation(item.getId(), "IN_STATEMENT", "VOID".equals(next) ? "OPEN" : "RECONCILED", "VOID".equals(next) ? null : id));
        }
        auditService.record("STATEMENT", id, action.toUpperCase(Locale.ROOT), "");
        return statement(id);
    }

    private Statement requireStatement(Long id) { Statement row = finStatementMapper.statement(id); if (row == null) throw missing(); return row; }
}
