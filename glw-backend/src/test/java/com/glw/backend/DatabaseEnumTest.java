package com.glw.backend;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.glw.backend.model.enums.*;

import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.TypeHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import tools.jackson.databind.json.JsonMapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.stream.Stream;

import javax.sql.DataSource;

class DatabaseEnumTest {
    private static SqlSessionFactory factory;
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeAll
    static void loadXml() throws Exception {
        var bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(mock(DataSource.class));
        bean.setConfiguration(new MybatisConfiguration());
        var global = new GlobalConfig();
        global.setDbConfig(new GlobalConfig.DbConfig());
        bean.setGlobalConfig(global);
        bean.setMapperLocations(
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:mapper/*Mapper.xml"));
        factory = bean.getObject();
    }

    @TestFactory
    Stream<DynamicTest> jsonUsesDatabaseCodesAndRejectsUnknownValues() {
        return List.of(
                        TaxIncluded.class,
                        SampleDeliveryStatus.class,
                        InboundType.class,
                        OutboundType.class,
                        InboundStatus.class,
                        OutboundStatus.class,
                        PurchaseOrderStatus.class,
                        SalesOrderStatus.class,
                        SalesDeliveryStatus.class,
                        PurchasePaymentStatus.class,
                        SalesPaymentStatus.class,
                        SettlementType.class)
                .stream()
                .map(
                        type ->
                                DynamicTest.dynamicTest(
                                        type.getSimpleName(),
                                        () -> {
                                            for (Object value : type.getEnumConstants()) {
                                                Object code =
                                                        type.getMethod("getCode").invoke(value);
                                                String encoded = json.writeValueAsString(value);
                                                assertThat(encoded)
                                                        .isEqualTo(json.writeValueAsString(code));
                                                assertThat(json.readValue(encoded, type))
                                                        .isSameAs(value);
                                            }
                                            assertThat(json.readValue("null", type)).isNull();
                                            String bad =
                                                    type.getDeclaredField("code").getType()
                                                                    == Integer.class
                                                            ? "99"
                                                            : "\"UNKNOWN\"";
                                            assertThatThrownBy(() -> json.readValue(bad, type))
                                                    .isInstanceOf(Exception.class);
                                        }));
    }

    @TestFactory
    Stream<DynamicTest> xmlTypeHandlersRoundTripEveryEnumCode() {
        return List.of(
                        "CustomerVisit",
                        "PurchaseOrderItem",
                        "SalesOrderItem",
                        "SampleApply",
                        "InboundOrder",
                        "OutboundOrder",
                        "PurchaseOrder",
                        "SalesOrder")
                .stream()
                .flatMap(
                        entity ->
                                factory
                                        .getConfiguration()
                                        .getResultMap(
                                                "com.glw.backend.mapper."
                                                        + entity
                                                        + "Mapper.BaseResultMap")
                                        .getResultMappings()
                                        .stream()
                                        .filter(mapping -> mapping.getJavaType().isEnum())
                                        .map(
                                                mapping ->
                                                        DynamicTest.dynamicTest(
                                                                entity
                                                                        + "."
                                                                        + mapping.getProperty(),
                                                                () -> roundTrip(mapping))));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void roundTrip(ResultMapping mapping) throws Exception {
        TypeHandler handler = mapping.getTypeHandler();
        Class<?> type = mapping.getJavaType();
        Class codeType = type.getDeclaredField("code").getType();
        for (Object value : type.getEnumConstants()) {
            Object code = type.getMethod("getCode").invoke(value);
            var statement = mock(PreparedStatement.class);
            handler.setParameter(statement, 1, value, mapping.getJdbcType());
            verify(statement).setObject(1, code, mapping.getJdbcType().TYPE_CODE);
            var result = mock(ResultSet.class);
            when(result.getObject(mapping.getColumn(), codeType)).thenReturn(code);
            assertThat(handler.getResult(result, mapping.getColumn())).isSameAs(value);
        }
        var result = mock(ResultSet.class);
        when(result.wasNull()).thenReturn(true);
        assertThat(handler.getResult(result, mapping.getColumn())).isNull();
    }
}
