package com.glw.backend.finance.statement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/**
 * 可视化设计器保存入参。保存一次生成一个新版本，历史单据仍按旧版本重导。
 * docType 为空时按对账单处理，兼容旧版调用。
 */
public record StatementTemplateLayoutInput(
        @Pattern(regexp = "AR|AP") String direction,
        @Pattern(regexp = "STATEMENT|SALES_CONTRACT|PURCHASE_CONTRACT|DELIVERY_NOTE") String docType,
        @NotNull @PositiveOrZero Long partnerId,
        @NotBlank @Size(max = 128) String name,
        Boolean isDefault,
        @Pattern(regexp = "XLSX|PDF") String format,
        @NotNull JsonNode layout) {
}
