package com.glw.backend.finance.statement;

import com.glw.backend.finance.FinanceModels.BuiltinTemplate;

import java.util.List;
import java.util.Optional;

/**
 * 随应用发布的系统模板。
 * 业务用户不需要自己从零做模板：先套用一套现成版式，特殊客户再下载后微调。
 */
public final class StatementBuiltins {
    public record Definition(String key, String name, String description, String fileKey,
                             String format, String direction) {}

    private static final List<Definition> DEFINITIONS = List.of(
            new Definition("standard", "标准版对账单",
                    "通用版式：抬头、客户信息、明细、合计、币别、银行信息与签章区",
                    "classpath:statement-templates/qintai-AR.xlsx", "XLSX", "AR"),
            new Definition("mingtai", "铭泰版对账单",
                    "精简版式：客户与供应商信息位于表格上方，明细后直接合计并留签章区",
                    "classpath:statement-templates/mingtai-AR.xlsx", "XLSX", "AR"));

    private StatementBuiltins() {}

    /** 目前内置版式只覆盖客户（应收）对账单，应付供应商请上传自定义模板。 */
    public static List<BuiltinTemplate> list(String direction) {
        return DEFINITIONS.stream()
                .filter(d -> d.direction().equals(direction))
                .map(d -> new BuiltinTemplate(d.key(), d.name(), d.description(), d.format()))
                .toList();
    }

    public static Optional<Definition> find(String key) {
        return DEFINITIONS.stream().filter(d -> d.key().equals(key)).findFirst();
    }
}
