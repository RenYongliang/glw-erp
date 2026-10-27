package com.glw.backend.common;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public final class CsvExport {
    private CsvExport() {}

    public static byte[] encode(List<? extends List<?>> rows) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        for (var row : rows) csv.append(row.stream().map(CsvExport::cell)
                .collect(Collectors.joining(","))).append("\r\n");
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String cell(Object value) {
        String text = value == null ? "" : value.toString();
        // Spreadsheet applications must treat user-entered formulas as plain text.
        String leading = text.stripLeading();
        if (!leading.isEmpty() && "=+-@".indexOf(leading.charAt(0)) >= 0
                && !(value instanceof Number)) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
