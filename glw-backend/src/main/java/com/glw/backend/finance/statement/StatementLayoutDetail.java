package com.glw.backend.finance.statement;

import com.glw.backend.finance.FinanceModels.StatementTemplate;
import tools.jackson.databind.JsonNode;

/** 设计器加载：模板元数据 + 画布布局。 */
public record StatementLayoutDetail(StatementTemplate template, JsonNode layout) {
}
