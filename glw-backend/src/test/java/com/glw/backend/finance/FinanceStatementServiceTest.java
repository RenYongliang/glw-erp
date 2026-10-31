package com.glw.backend.finance;

import com.glw.backend.common.BusinessException;
import com.glw.backend.finance.FinanceModels.Statement;
import com.glw.backend.finance.FinanceModels.Generate;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinanceStatementServiceTest extends FinanceTestFixture {
    @Test void blocksVoidingConfirmedStatements() {
        var s = new Statement(); s.setId(1L); s.setStatus("CONFIRMED"); when(finStatement.statement(1L)).thenReturn(s);
        assertThatThrownBy(() -> statements.transition(1L, "void")).isInstanceOf(BusinessException.class);
    }

    @Test void rejectsMixedPartnerStatementAndDuplicateSources() {
        var one = item(); one.setReconcileStatus("OPEN"); var two = item(); two.setId(2L); two.setPartnerId(7L); two.setReconcileStatus("OPEN");
        when(finOpenItem.openItem(1L)).thenReturn(one); when(finOpenItem.openItem(2L)).thenReturn(two);
        var input = new Generate(); input.setOpenItemIds(List.of(1L,2L));
        assertThatThrownBy(() -> statements.generate(input)).hasMessageContaining("往来单位");
        input.setOpenItemIds(List.of(1L,1L)); assertThatThrownBy(() -> statements.generate(input)).hasMessageContaining("重复");
        verify(finStatement, never()).insertStatement(any());
    }

    @Test void blocksManualGenerationWithoutProfile() {
        var one = item(); one.setReconcileStatus("OPEN");
        when(finOpenItem.openItem(1L)).thenReturn(one);
        var input = new Generate(); input.setOpenItemIds(List.of(1L));
        assertThatThrownBy(() -> statements.generate(input)).hasMessageContaining("结算档案");
        verify(finStatement, never()).insertStatement(any());
    }

    @Test void voidDraftReleasesActualOpenItemIdsAndCanBeRetried() {
        var s = new Statement(); s.setId(7L); s.setStatus("DRAFT");
        var item = item(); item.setId(93L); item.setReconcileStatus("IN_STATEMENT");
        when(finStatement.statement(7L)).thenReturn(s); when(finStatementItem.statementItems(7L)).thenReturn(List.of(item));
        when(finStatement.statementStatus(7L,"DRAFT","VOID")).thenReturn(1);
        when(finOpenItem.setReconciliation(93L,"IN_STATEMENT","OPEN",null)).thenReturn(1);
        statements.transition(7L,"void");
        verify(finOpenItem).setReconciliation(93L,"IN_STATEMENT","OPEN",null);
        s.setStatus("VOID"); statements.transition(7L,"void");
        verify(finStatement,times(1)).statementStatus(any(),any(),any());
    }
}
