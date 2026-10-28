package com.glw.backend.finance;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.glw.backend.finance.FinanceModels.Query;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class FinanceMapperTest {
    private MybatisConfiguration configuration() throws Exception {
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(mock(DataSource.class));
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(config);
        var resources = new java.util.ArrayList<org.springframework.core.io.Resource>();
        resources.addAll(List.of(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/Fin*Mapper.xml")));
        resources.add(new org.springframework.core.io.ClassPathResource("mapper/DocumentNumberMapper.xml"));
        factory.setMapperLocations(resources.toArray(org.springframework.core.io.Resource[]::new));
        return (MybatisConfiguration) factory.getObject().getConfiguration();
    }

    @Test void financeQueriesBindFiltersAndSnapshotIdsCannotBeConfusedWithDetailIds() throws Exception {
        var config = configuration();
        var q = new Query(); q.setKeyword("test' OR 1=1"); q.setPeriod("2026-10"); q.setPartnerId(6L);
        var bound = config.getMappedStatement("com.glw.backend.mapper.FinOpenItemMapper.openItems")
                .getBoundSql(Map.of("q", q, "paged", true));
        assertThat(bound.getSql()).contains("LOCATE(?", "partner_id=?", "LIMIT ? OFFSET ?").doesNotContain(q.getKeyword());
        var detail = config.getMappedStatement("com.glw.backend.mapper.FinStatementItemMapper.statementItems").getBoundSql(1L).getSql();
        assertThat(detail).contains("i.open_item_id id").doesNotContain("i.*");
        var statuses = config.getMappedStatement("com.glw.backend.mapper.FinanceOrderStatusMapper.orderStatuses")
                .getBoundSql(Map.of("direction", "AR", "ids", List.of(1L, 2L))).getSql();
        assertThat(statuses).contains("sales_order_item", "cancelled_quantity", "SUM(f.settled_amount)", "IN").doesNotContain("${");
    }

    @Test void statusFilteringAndEmptyOrderListsAreBoundInXml() throws Exception {
        var config = configuration();
        var mapped = config.getMappedStatement("com.glw.backend.mapper.FinanceOrderStatusMapper.orderIdsByStatus");
        var bound = mapped.getBoundSql(Map.of("direction", "AP", "status", "PAID' OR 1=1"));
        assertThat(bound.getSql()).contains("purchase_order_item").doesNotContain("sales_order_item", "PAID' OR 1=1");
        var empty = config.getMappedStatement("com.glw.backend.mapper.FinanceOrderStatusMapper.orderStatuses")
                .getBoundSql(Map.of("direction", "AR", "ids", List.of())).getSql();
        assertThat(empty).contains("1=0").doesNotContain("IN ()");
    }

    @Test void everyFinanceTableHasAnEntityMapAndGeneratedCrud() throws Exception {
        var config = configuration();
        for (String entity : List.of("FinLock", "FinAudit", "FinProfile", "FinOpenItem", "FinStatement",
                "FinStatementItem", "FinPayment", "FinAllocation")) {
            String namespace = "com.glw.backend.mapper." + entity + "Mapper";
            assertThat(config.hasResultMap(namespace + ".BaseResultMap")).as(entity).isTrue();
            assertThat(config.hasStatement(namespace + ".insert")).as(entity).isTrue();
            assertThat(config.hasStatement(namespace + ".selectById")).as(entity).isTrue();
        }
    }

    @Test void customFinanceStatementsAreDefinedInXmlRatherThanAnnotations() throws Exception {
        var config = configuration();
        for (String mapper : List.of("FinLockMapper", "FinAuditMapper", "FinProfileMapper", "FinOpenItemMapper",
                "FinStatementMapper", "FinStatementItemMapper", "FinPaymentMapper", "FinAllocationMapper",
                "FinanceOrderStatusMapper", "DocumentNumberMapper")) {
            var type = Class.forName("com.glw.backend.mapper." + mapper);
            for (var method : type.getDeclaredMethods()) {
                assertThat(method.getDeclaredAnnotations()).as(mapper + "." + method.getName()).isEmpty();
                assertThat(config.hasStatement(type.getName() + "." + method.getName()))
                        .as(mapper + "." + method.getName()).isTrue();
            }
        }
    }
}
