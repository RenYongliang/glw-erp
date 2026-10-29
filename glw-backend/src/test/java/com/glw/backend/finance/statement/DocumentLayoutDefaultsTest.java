package com.glw.backend.finance.statement;

import com.glw.backend.config.FinanceStatementProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DocumentLayoutDefaultsTest {
    private final LayoutStatementRenderer xlsx = new LayoutStatementRenderer(JsonMapper.builder().build());
    private final LayoutPdfRenderer pdf = new LayoutPdfRenderer(
            xlsx, new FinanceStatementProperties(null, "pcs", null, null));

    @Test void everyDefaultLayoutValidatesAndRenders() {
        for (DocumentType type : DocumentType.values()) {
            for (String direction : type.directional() ? List.of("AR", "AP")
                    : List.of(type.normalizeDirection(null))) {
                String layout = DocumentLayoutDefaults.layout(type, direction);
                assertThat(xlsx.validate(layout)).isNotNull();
                Map<String, Object> scalars = DocumentSampleData.scalars(type, direction);
                List<Map<String, Object>> rows = DocumentSampleData.rows(type);
                assertThat(xlsx.render(scalars, rows, layout)).isNotEmpty();
                assertThat(scalars.get("header.title")).isNotNull();
                assertThat(rows).isNotEmpty();
            }
        }
    }

    @Test void everyDefaultLayoutRendersPdf() {
        assumeTrue(LayoutPdfRenderer.fontCandidates(null).stream().anyMatch(Files::isRegularFile));
        for (DocumentType type : DocumentType.values()) {
            String direction = type.directional() ? "AR" : type.normalizeDirection(null);
            String layout = DocumentLayoutDefaults.layout(type, direction);
            byte[] bytes = pdf.render(DocumentSampleData.scalars(type, direction),
                    DocumentSampleData.rows(type), layout);
            assertThat(new String(bytes, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1))
                    .isEqualTo("%PDF");
        }
    }
}
