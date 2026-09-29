package com.glw.backend;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.glw.backend.config.MybatisPlusConfig;
import com.glw.backend.mapper.UserMapper;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import javax.sql.DataSource;

class OptimisticLockTest {
    private static SqlSessionFactory factory;
    private static OptimisticLockerInnerInterceptor locker;

    @BeforeAll
    static void setup() throws Exception {
        var plugins = new MybatisPlusConfig().mybatisPlusInterceptor();
        locker =
                plugins.getInterceptors().stream()
                        .filter(OptimisticLockerInnerInterceptor.class::isInstance)
                        .map(OptimisticLockerInnerInterceptor.class::cast)
                        .findFirst()
                        .orElseThrow();
        var bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(mock(DataSource.class));
        bean.setConfiguration(new MybatisConfiguration());
        bean.setPlugins(plugins);
        bean.setMapperLocations(
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:mapper/*Mapper.xml"));
        factory = bean.getObject();
        factory.getConfiguration().addMapper(UserMapper.class);
    }

    @TestFactory
    Stream<DynamicTest> updatesCompareOriginalVersionAndIncrementTheStoredVersion() {
        return List.of(
                        "Customer",
                        "Supplier",
                        "Product",
                        "CustomerVisit",
                        "SampleApply",
                        "SalesOrder",
                        "PurchaseOrder",
                        "InboundOrder",
                        "OutboundOrder",
                        "Inventory",
                        "SysUser")
                .stream()
                .map(
                        name ->
                                DynamicTest.dynamicTest(
                                        name,
                                        () -> {
                                            Class<?> type =
                                                    Class.forName(
                                                            "com.glw.backend.model.entity." + name);
                                            assertThat(
                                                            TableInfoHelper.getTableInfo(type)
                                                                    .isWithVersion())
                                                    .isTrue();
                                            Object entity = type.getConstructor().newInstance();
                                            type.getMethod("setId", Long.class).invoke(entity, 1L);
                                            type.getMethod("setVersion", Integer.class)
                                                    .invoke(entity, 3);
                                            var params = new HashMap<String, Object>();
                                            params.put("et", entity);
                                            String mapper =
                                                    name.equals("SysUser")
                                                            ? "UserMapper"
                                                            : name + "Mapper";
                                            var statement =
                                                    factory.getConfiguration()
                                                            .getMappedStatement(
                                                                    "com.glw.backend.mapper."
                                                                            + mapper
                                                                            + ".updateById");
                                            locker.beforeUpdate(null, statement, params);
                                            assertThat(type.getMethod("getVersion").invoke(entity))
                                                    .isEqualTo(4);
                                            var sql = statement.getBoundSql(params);
                                            String normalized =
                                                    sql.getSql().replaceAll("\\s+", " ");
                                            assertThat(normalized).contains("version=?");
                                            assertThat(
                                                            normalized.substring(
                                                                    normalized.indexOf("WHERE")))
                                                    .contains("version=?");
                                            var originalVersion =
                                                    sql.getParameterMappings().stream()
                                                            .filter(
                                                                    p ->
                                                                            p.getProperty()
                                                                                    .contains(
                                                                                            "MP_OPTLOCK_VERSION_ORIGINAL"))
                                                            .findFirst()
                                                            .orElseThrow();
                                            assertThat(
                                                            factory.getConfiguration()
                                                                    .newMetaObject(params)
                                                                    .getValue(
                                                                            originalVersion
                                                                                    .getProperty()))
                                                    .isEqualTo(3);
                                            Object fresh = type.getConstructor().newInstance();
                                            assertThat(type.getMethod("getVersion").invoke(fresh))
                                                    .isNull();
                                            String insert =
                                                    factory.getConfiguration()
                                                            .getMappedStatement(
                                                                    "com.glw.backend.mapper."
                                                                            + mapper
                                                                            + ".insert")
                                                            .getBoundSql(fresh)
                                                            .getSql();
                                            assertThat(insert).doesNotContain("version");
                                        }));
    }

    @Test
    void orderItemTablesUseTheirParentOrderVersion() throws Exception {
        for (String name :
                List.of(
                        "SalesOrderItem",
                        "PurchaseOrderItem",
                        "InboundOrderItem",
                        "OutboundOrderItem")) {
            Class<?> type = Class.forName("com.glw.backend.model.entity." + name);
            assertThat(TableInfoHelper.getTableInfo(type).isWithVersion()).isFalse();
        }
    }
}
