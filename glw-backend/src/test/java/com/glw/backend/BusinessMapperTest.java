package com.glw.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Test;
import com.glw.backend.model.dto.InventoryPageDTO;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.sql.DataSource;

/**
 * Loads the real mapper XML and checks MyBatis-Plus SQL against the committed DDL, without a DB.
 */
class BusinessMapperTest {
    private static SqlSessionFactory factory;
    private static String ddl;

    @BeforeAll
    static void loadMappers() throws Exception {
        var bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(mock(DataSource.class));
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        bean.setConfiguration(configuration);
        var global = new GlobalConfig();
        var db = new GlobalConfig.DbConfig();
        db.setLogicDeleteField("deleted");
        global.setDbConfig(db);
        bean.setGlobalConfig(global);
        bean.setMapperLocations(
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:mapper/*Mapper.xml"));
        factory = bean.getObject();
        try (var input = new ClassPathResource("db/glw.sql").getInputStream()) {
            ddl = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @TestFactory
    Stream<DynamicTest> everyBusinessTableHasWorkingMappingsAndCrudSql() {
        var matcher =
                Pattern.compile("CREATE TABLE `([^`]+)`\\s*\\((.*?)\\) ENGINE", Pattern.DOTALL)
                        .matcher(ddl);
        List<DynamicTest> tests = new ArrayList<>();
        while (matcher.find()) {
            String table = matcher.group(1), body = matcher.group(2);
            if (table.equals("sys_user")) continue;
            tests.add(DynamicTest.dynamicTest(table, () -> verifyTable(table, body)));
        }
        assertThat(tests).hasSize(22);
        return tests.stream();
    }

    @Test
    void inventoryQueriesKeepAvailabilityFiltersAndProductPagingSeparate() {
        var configuration = factory.getConfiguration();
        var query = new InventoryPageDTO();
        query.setKeyword("quoted'_%");
        String namespace = "com.glw.backend.mapper.InventoryMapper.";
        var statement = configuration.getMappedStatement(namespace + "selectProductStock");
        String stockSql = statement.getBoundSql(Map.of("query", query)).getSql().replaceAll("\\s+", " ");
        assertThat(stockSql).contains("GREATEST(i.quantity - COALESCE(i.reserved_quantity, 0), 0) > 0")
                .contains("s.available_quantity > 0").contains("LOCATE(?, p.part_no)")
                .doesNotContain("quoted'_%");
        query.setInStockOnly(false);
        String allSql = statement.getBoundSql(Map.of("query", query)).getSql();
        assertThat(allSql).doesNotContain("s.available_quantity > 0");
        String batchSql = configuration.getMappedStatement(namespace + "selectAvailableBatches")
                .getBoundSql(Map.of("productIds", List.of(4L, 7L))).getSql().replaceAll("\\s+", " ");
        assertThat(batchSql).contains("i.product_id IN").contains("GREATEST(i.quantity - COALESCE(i.reserved_quantity, 0), 0) > 0")
                .contains("ORDER BY i.product_id");
    }

    private void verifyTable(String table, String body) throws Exception {
        String name = pascal(table), namespace = "com.glw.backend.mapper." + name + "Mapper";
        Class<?> entityClass = Class.forName("com.glw.backend.model.entity." + name);
        Class<?> mapperClass = Class.forName(namespace);
        Class<?> serviceClass = Class.forName("com.glw.backend.service." + name + "Service");
        Class<?> implementation =
                Class.forName("com.glw.backend.service.impl." + name + "ServiceImpl");
        assertThat(serviceClass.isAssignableFrom(implementation)).isTrue();
        var config = factory.getConfiguration();
        assertThat(config.hasMapper(mapperClass)).isTrue();

        Map<String, String> columns = new LinkedHashMap<>();
        var fields = Pattern.compile("^\\s*`([^`]+)`\\s+(\\w+)", Pattern.MULTILINE).matcher(body);
        while (fields.find()) columns.put(fields.group(1), fields.group(2));
        var resultMap = config.getResultMap(namespace + ".BaseResultMap");
        assertThat(resultMap.getResultMappings())
                .extracting(r -> r.getColumn())
                .containsExactlyElementsOf(columns.keySet());
        for (var mapping : resultMap.getResultMappings()) {
            assertThat(mapping.getJavaType()).isEqualTo(entityClass.getDeclaredField(mapping.getProperty()).getType());
        }
        var tableInfo = TableInfoHelper.getTableInfo(entityClass);
        assertThat(tableInfo.getTableName()).isEqualTo(table);
        assertThat(tableInfo.isWithLogicDelete()).isFalse();
        var select = config.getMappedStatement(namespace + ".selectById");
        assertThat(select.getResultMaps().getFirst().getId())
                .isEqualTo(namespace + ".BaseResultMap");
        String selectSql = select.getBoundSql(1L).getSql();
        // With an explicit XML resultMap, MyBatis-Plus may legitimately select '*'.
        assertThat(selectSql).contains("FROM " + table).contains("WHERE id=?");
        var mappedColumns = new ArrayList<String>();
        mappedColumns.add(tableInfo.getKeyColumn());
        tableInfo.getFieldList().forEach(field -> mappedColumns.add(field.getColumn()));
        assertThat(mappedColumns).containsExactlyElementsOf(columns.keySet());
        assertThat(selectSql).doesNotContain("deleted");

        Object entity = entityClass.getConstructor().newInstance();
        for (var column : columns.entrySet()) {
            String property = camel(column.getKey());
            Class<?> type =
                    switch (column.getValue()) {
                        case "bigint" -> Long.class;
                        case "tinyint" -> Boolean.class;
                        case "int" -> Integer.class;
                        case "decimal" -> BigDecimal.class;
                        case "timestamp" -> LocalDateTime.class;
                        default -> String.class;
                    };
            Class<?> actualType = entityClass.getDeclaredField(property).getType();
            if (actualType.isEnum()) {
                var code = actualType.getDeclaredField("code");
                assertThat(code.getType()).isEqualTo(type);
                assertThat(
                                code.isAnnotationPresent(
                                        com.baomidou.mybatisplus.annotation.EnumValue.class))
                        .isTrue();
                type = actualType;
            } else {
                assertThat(actualType).isEqualTo(type);
            }
            Object value =
                    type.isEnum()
                            ? type.getEnumConstants()[0]
                            : type == Long.class
                                    ? 1L
                                    : type == Integer.class
                                            ? 1
                                            : type == Boolean.class
                                                    ? true
                                                    : type == BigDecimal.class
                                                            ? new BigDecimal("12.34")
                                                            : type == LocalDateTime.class
                                                                    ? LocalDateTime.of(2026, 9, 28, 12, 0)
                                                                    : "test";
            entityClass.getMethod("set" + pascal(column.getKey()), type).invoke(entity, value);
        }
        // Even entities loaded from a previous query must not write stale database timestamps.
        String insert =
                config.getMappedStatement(namespace + ".insert").getBoundSql(entity).getSql();
        String update =
                config.getMappedStatement(namespace + ".updateById")
                        .getBoundSql(Map.of("et", entity))
                        .getSql();
        assertThat(insert)
                .contains("INSERT INTO " + table)
                .doesNotContain("created_at", "updated_at");
        assertThat(update).contains("UPDATE " + table).doesNotContain("created_at", "updated_at");
        if (table.equals("inventory")) {
            assertThat(insert).contains("reserved_quantity");
            assertThat(update).contains("reserved_quantity");
            // A quantity-only update must not reset existing reservations to zero.
            entityClass.getMethod("setReservedQuantity", Integer.class).invoke(entity, new Object[] {null});
            String partialUpdate = config.getMappedStatement(namespace + ".updateById")
                    .getBoundSql(Map.of("et", entity)).getSql();
            assertThat(partialUpdate).doesNotContain("reserved_quantity");
        }
        String delete =
                config.getMappedStatement(namespace + ".deleteById")
                        .getBoundSql(Map.of("id", 1L))
                        .getSql();
        assertThat(delete).contains("DELETE FROM " + table).doesNotContain("deleted");

        // Nullable Java fields allow MySQL defaults (status, type, flags) to take effect on INSERT.
        Object empty = entityClass.getConstructor().newInstance();
        String defaultInsert =
                config.getMappedStatement(namespace + ".insert").getBoundSql(empty).getSql();
        for (String field :
                List.of("order_status", "payment_status", "tax_included", "is_delivered", "reserved_quantity")) {
            assertThat(defaultInsert).doesNotContain(field);
        }
    }

    private static String pascal(String column) {
        String value = camel(column);
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String camel(String column) {
        var parts = column.split("_");
        var result = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            result.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return result.toString();
    }
}
