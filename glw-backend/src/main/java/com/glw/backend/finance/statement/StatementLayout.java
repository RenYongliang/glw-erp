package com.glw.backend.finance.statement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 可视化设计器保存的画布布局。
 *
 * 坐标单位统一为毫米，前端画布按 A4 纸张 1:1 渲染；后端按 {@link Page#gridMm()} 映射到 Excel 单元格。
 * 布局只在保存时生成一份样例 XLSX，正式导出仍按布局和数据实时渲染，保证任意明细行数都能出单。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StatementLayout(Page page, List<Component> components) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(String size, String orientation, Double marginMm, Double gridMm) {
        public String paper() {
            return size == null || size.isBlank() ? "A4" : size.strip().toUpperCase();
        }

        public boolean landscape() {
            return "LANDSCAPE".equalsIgnoreCase(orientation);
        }

        public double widthMm() {
            return landscape() ? 297 : 210;
        }

        public double heightMm() {
            return landscape() ? 210 : 297;
        }

        public double margin() {
            return marginMm == null ? 10 : Math.max(0, marginMm);
        }

        public double grid() {
            return gridMm == null ? 3 : Math.min(10, Math.max(2.25, gridMm));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Component(String id, String type,
                            double x, double y, double w, double h,
                            String text, String label, String field,
                            Integer fontSize, Boolean bold, String align, Boolean border,
                            String color,
                            Double headerHeight, Double rowHeight, Boolean headerBold,
                            List<Column> columns,
                            List<FooterCell> footer) {

        public String kind() {
            return type == null ? "" : type.strip().toLowerCase();
        }

        public int font() {
            return fontSize == null ? 10 : Math.min(72, Math.max(5, fontSize));
        }

        public boolean strong() {
            return Boolean.TRUE.equals(bold);
        }

        public String horizontal() {
            return switch (align == null ? "" : align.strip().toLowerCase()) {
                case "center" -> "center";
                case "right" -> "right";
                default -> "left";
            };
        }

        public boolean boxed() {
            return Boolean.TRUE.equals(border);
        }

        public double headerMm() {
            return headerHeight == null ? 7 : Math.max(4, headerHeight);
        }

        public double rowMm() {
            return rowHeight == null ? 6 : Math.max(4, rowHeight);
        }

        public boolean strongHeader() {
            return headerBold == null || headerBold;
        }

        /** 表格自带的合计行；为空表示没有合计行。 */
        public List<FooterCell> summary() {
            return footer == null ? List.of() : footer;
        }

        /** 合计行高度：占一行明细行高。 */
        public double footerMm() {
            return summary().isEmpty() ? 0 : rowMm();
        }
    }

    /** 合计行单元格：按 span 跨列合并，text 里的 {{占位符}} 和 field 都会替换成真实数据。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FooterCell(String text, String field, Integer span, String align, Boolean bold) {

        public String label() {
            return text == null ? "" : text;
        }

        public String key() {
            return field == null ? "" : field.strip();
        }

        public int spanCount() {
            return span == null || span < 1 ? 1 : Math.min(span, 20);
        }

        public String horizontal() {
            return switch (align == null ? "" : align.strip().toLowerCase()) {
                case "center" -> "center";
                case "right" -> "right";
                default -> "left";
            };
        }

        public boolean strong() {
            return Boolean.TRUE.equals(bold);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Column(String title, String field, Double width, String align) {
        public String heading() {
            return title == null ? "" : title;
        }

        public String key() {
            return field == null ? "" : field.strip();
        }

        public double widthMm() {
            return width == null || width <= 0 ? 20 : width;
        }

        public String horizontal() {
            return switch (align == null ? "" : align.strip().toLowerCase()) {
                case "center" -> "center";
                case "right" -> "right";
                default -> "left";
            };
        }
    }
}
