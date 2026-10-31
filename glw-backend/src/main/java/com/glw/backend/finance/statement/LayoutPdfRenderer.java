package com.glw.backend.finance.statement;

import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;
import com.glw.backend.config.FinanceStatementProperties;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 把可视化布局直接渲染成 PDF。
 *
 * 明细表格跨页时每页重复表头，表格自带的合计行跟在最后一行明细后面（与表格同一个框）；
 * 签章等表格下方组件只出现在最后一页，最后一页放不下时优先贴到页面底部，仍放不下才另起一页。
 */
@Component
public class LayoutPdfRenderer {
    private static final double MM_TO_POINTS = 72 / 25.4;
    private static final float PADDING = 1.2f;
    private static final float BORDER_WIDTH = 0.5f;
    private static final float HEADER_FILL = 0.93f;

    private final LayoutStatementRenderer layouts;
    private final FinanceStatementProperties properties;

    public LayoutPdfRenderer(LayoutStatementRenderer layouts, FinanceStatementProperties properties) {
        this.layouts = layouts;
        this.properties = properties;
    }

    public byte[] render(StatementRenderModel model, String layoutJson) {
        return render(LayoutStatementRenderer.scalarValues(model),
                model.items().stream().map(LayoutStatementRenderer::lineValues).toList(), layoutJson);
    }

    /** 通用 PDF 渲染入口；单据类型只影响样例数据。 */
    public byte[] render(Map<String, Object> scalars, List<Map<String, Object>> rows, String layoutJson) {
        StatementLayout layout = layouts.parse(layoutJson);
        List<StatementLayout.Component> components =
                layout.components() == null ? List.of() : layout.components();
        List<StatementLayout.Component> tables = components.stream()
                .filter(c -> "table".equals(c.kind())).toList();
        if (tables.isEmpty()) throw bad("请添加明细表格组件");
        if (tables.size() > 1) throw bad("PDF 模板只支持一个明细表格，请删除多余的表格组件");
        StatementLayout.Component table = tables.get(0);

        List<StatementLayout.Component> above = new ArrayList<>();
        List<StatementLayout.Component> below = new ArrayList<>();
        for (StatementLayout.Component component : components) {
            if (component == table || "table".equals(component.kind())) continue;
            if (component.y() + component.h() <= table.y() + 0.5) above.add(component);
            else below.add(component);
        }
        above.sort(Comparator.comparingDouble(StatementLayout.Component::y));
        below.sort(Comparator.comparingDouble(StatementLayout.Component::y));

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDFont font = loadFont(document);
            Canvas canvas = new Canvas(document, layout.page());
            try {
                for (StatementLayout.Component component : above)
                    component(canvas, font, component, component.y(), scalars);

                double tableEnd = table(canvas, font, table, rows, scalars);
                below(canvas, font, below, table, tableEnd, scalars);
            } finally {
                canvas.close();
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "生成 PDF 文档失败");
        }
    }

    /** 按真实行数分页渲染明细，返回最后一页表格的底边（mm，含合计行）。 */
    private double table(Canvas canvas, PDFont font, StatementLayout.Component table,
                         List<Map<String, Object>> rows, Map<String, Object> scalars) throws IOException {
        int rowCount = Math.max(rows.size(), 1);
        double rowMm = table.rowMm();
        double footerMm = table.footerMm();
        double pageBottom = canvas.page.heightMm() - canvas.page.margin();
        int index = 0;
        int pageIndex = 0;
        double end = table.y() + table.headerMm();
        while (true) {
            double headerTop = pageIndex == 0 ? table.y() : canvas.page.margin();
            double bodyTop = headerTop + table.headerMm();
            header(canvas, font, table, headerTop);

            // 每页都给合计行留位置，避免明细填满后合计行被挤到下一页单独成行。
            double available = pageBottom - bodyTop - footerMm;
            int capacity = Math.max(1, (int) Math.floor(available / rowMm));
            int count = Math.min(capacity, rowCount - index);
            if (count <= 0 && index < rowCount) count = 1;
            for (int i = 0; i < count; i++) {
                double top = bodyTop + i * rowMm;
                Map<String, Object> values = index + i < rows.size() ? rows.get(index + i) : Map.of();
                row(canvas, font, table, top, values);
            }
            index += count;
            end = bodyTop + count * rowMm;
            if (index >= rowCount) break;
            pageIndex++;
            canvas.next();
        }
        if (footerMm > 0) {
            footer(canvas, font, table, end, scalars);
            end += footerMm;
        }
        return end;
    }

    /** 合计行与明细共用一套列宽和边框，跨列数由布局里的 span 决定。 */
    private void footer(Canvas canvas, PDFont font, StatementLayout.Component table, double top,
                        Map<String, Object> scalars) throws IOException {
        float[] widths = widths(table);
        double cursor = table.x();
        int column = 0;
        for (StatementLayout.FooterCell cell : table.summary()) {
            int span = Math.min(cell.spanCount(), widths.length - column);
            if (span <= 0) break;
            float width = 0;
            for (int i = 0; i < span; i++) width += widths[column + i];
            cell(canvas, font, cursor, top, width, table.rowMm(), footerText(cell, scalars),
                    cell.horizontal(), table.font(), cell.strong(), false);
            cursor += width;
            column += span;
        }
    }

    private String footerText(StatementLayout.FooterCell cell, Map<String, Object> scalars) {
        String text = layouts.replace(cell.label(), scalars);
        if (cell.key().isEmpty()) return text;
        return text + LayoutStatementRenderer.display(scalars.get(cell.key()));
    }

    private void header(Canvas canvas, PDFont font, StatementLayout.Component table, double top)
            throws IOException {
        double cursor = table.x();
        float[] widths = widths(table);
        for (int i = 0; i < table.columns().size(); i++) {
            StatementLayout.Column column = table.columns().get(i);
            cell(canvas, font, cursor, top, widths[i], table.headerMm(), column.heading(),
                    column.horizontal(), table.font(), table.strongHeader(), true);
            cursor += widths[i];
        }
    }

    private void row(Canvas canvas, PDFont font, StatementLayout.Component table, double top,
                     Map<String, Object> values) throws IOException {
        double cursor = table.x();
        float[] widths = widths(table);
        for (int i = 0; i < table.columns().size(); i++) {
            StatementLayout.Column column = table.columns().get(i);
            Object value = values.get(column.key().substring(5));
            cell(canvas, font, cursor, top, widths[i], table.rowMm(),
                    LayoutStatementRenderer.display(value), column.horizontal(), table.font(),
                    table.strong(), false);
            cursor += widths[i];
        }
    }

    private void cell(Canvas canvas, PDFont font, double x, double top, float widthMm, double heightMm,
                      String text, String align, int fontSize, boolean bold, boolean fill)
            throws IOException {
        PDPageContentStream stream = canvas.stream;
        float xPt = pt(x);
        float yPt = pt(canvas.page.heightMm() - top - heightMm);
        float wPt = pt(widthMm);
        float hPt = pt(heightMm);
        if (fill) {
            stream.setNonStrokingColor(HEADER_FILL);
            stream.addRect(xPt, yPt, wPt, hPt);
            stream.fill();
            stream.setNonStrokingColor(0f);
        }
        text(canvas, font, text, fontSize, bold, align, xPt, yPt, wPt, hPt, PADDING);
        stream.setStrokingColor(0f);
        stream.setLineWidth(BORDER_WIDTH);
        stream.addRect(xPt, yPt, wPt, hPt);
        stream.stroke();
    }

    private double below(Canvas canvas, PDFont font, List<StatementLayout.Component> components,
                         StatementLayout.Component table, double tableEnd,
                         Map<String, Object> scalars) throws IOException {
        double pageBottom = canvas.page.heightMm() - canvas.page.margin();
        // 表格按真实行数撑开/收缩后，表格下方整体平移同样的距离，保持同一行上的组件仍然并排。
        double shift = tableEnd - (table.y() + table.h());
        double cursor = tableEnd;
        for (int index = 0; index < components.size(); ) {
            int end = index + 1;
            while (end < components.size()
                    && Math.abs(components.get(end).y() - components.get(index).y()) <= 0.5) end++;
            List<StatementLayout.Component> row = components.subList(index, end);
            double height = row.stream().mapToDouble(StatementLayout.Component::h).max().orElse(0);
            double y = Math.max(cursor, row.get(0).y() + shift);
            if (y + height > pageBottom) {
                double pinned = pageBottom - height;
                if (pinned >= cursor) y = pinned;
                else if (height <= pageBottom - canvas.page.margin()) {
                    canvas.next();
                    y = Math.max(canvas.page.margin(), pageBottom - height);
                } else {
                    y = Math.max(canvas.page.margin(), pinned);
                }
            }
            for (StatementLayout.Component component : row) component(canvas, font, component, y, scalars);
            cursor = y + height;
            index = end;
        }
        return cursor;
    }

    private void component(Canvas canvas, PDFont font, StatementLayout.Component component, double top,
                           Map<String, Object> scalars) throws IOException {
        String value = switch (component.kind()) {
            case "text" -> layouts.replace(component.text(), scalars);
            case "field" -> layouts.fieldText(component, scalars);
            case "signature" -> layouts.replace(component.text(), scalars);
            default -> null;
        };
        if (value == null) return;
        boolean boxed = component.boxed() || "signature".equals(component.kind());
        if (boxed) {
            PDPageContentStream stream = canvas.stream;
            float xPt = pt(component.x());
            float yPt = pt(canvas.page.heightMm() - top - component.h());
            stream.setStrokingColor(0f);
            stream.setLineWidth(BORDER_WIDTH);
            stream.addRect(xPt, yPt, pt(component.w()), pt(component.h()));
            stream.stroke();
        }
        text(canvas, font, value, component.font(), component.strong(), component.horizontal(),
                pt(component.x()), pt(canvas.page.heightMm() - top - component.h()),
                pt(component.w()), pt(component.h()), PADDING);
    }

    /** 在矩形内绘制自动换行、垂直居中的文本，并裁剪到矩形范围。 */
    private void text(Canvas canvas, PDFont font, String value, int fontSize, boolean bold,
                      String align, float x, float y, float width, float height, float padding)
            throws IOException {
        List<String> lines = wrap(value, font, fontSize, Math.max(1, width - padding * 2));
        float leading = fontSize * 1.28f;
        // 用字体自身的 ascent/descent 做垂直居中，避免文字顶部被单元格裁剪。
        float ascent = 0.86f;
        float descent = -0.14f;
        var descriptor = font.getFontDescriptor();
        if (descriptor != null) {
            ascent = descriptor.getAscent() / 1000f;
            descent = descriptor.getDescent() / 1000f;
        }
        float ascentPt = ascent * fontSize;
        float descentPt = descent * fontSize;
        float contentHeight = (lines.size() - 1) * leading + ascentPt - descentPt;
        float top = y + (height - contentHeight) / 2 - descentPt;
        PDPageContentStream stream = canvas.stream;
        stream.saveGraphicsState();
        stream.addRect(x + 0.4f, y + 0.4f, Math.max(0.1f, width - 0.8f), Math.max(0.1f, height - 0.8f));
        stream.clip();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty()) continue;
            float lineWidth = width(font, fontSize, line);
            float left = switch (align == null ? "" : align) {
                case "center" -> x + (width - lineWidth) / 2;
                case "right" -> x + width - padding - lineWidth;
                default -> x + padding;
            };
            stream.beginText();
            stream.setFont(font, fontSize);
            stream.setNonStrokingColor(0f);
            if (bold) {
                stream.setRenderingMode(RenderingMode.FILL_STROKE);
                stream.setStrokingColor(0f);
                stream.setLineWidth(fontSize * 0.035f);
            } else {
                stream.setRenderingMode(RenderingMode.FILL);
            }
            stream.setTextMatrix(Matrix.getTranslateInstance(left, top - i * leading));
            stream.showText(line);
            stream.endText();
        }
        stream.setRenderingMode(RenderingMode.FILL);
        stream.restoreGraphicsState();
    }

    private List<String> wrap(String value, PDFont font, float fontSize, float maxWidth)
            throws IOException {
        List<String> lines = new ArrayList<>();
        if (value == null || value.isEmpty()) return List.of("");
        for (String paragraph : value.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            int index = 0;
            while (index < paragraph.length()) {
                int end = tokenEnd(paragraph, index);
                String token = paragraph.substring(index, end);
                if (line.length() > 0 && width(font, fontSize, line + token) > maxWidth) {
                    lines.add(line.toString().stripTrailing());
                    line.setLength(0);
                }
                line.append(token);
                index = end;
            }
            lines.add(line.toString().stripTrailing());
        }
        return lines;
    }

    /** 英文单词整体换行，中文按字换行。 */
    private static int tokenEnd(String text, int start) {
        int index = start + 1;
        if (text.charAt(start) <= 0x7F && !Character.isWhitespace(text.charAt(start))) {
            while (index < text.length() && text.charAt(index) <= 0x7F
                    && !Character.isWhitespace(text.charAt(index))) index++;
        }
        return index;
    }

    private static float width(PDFont font, float fontSize, String text) throws IOException {
        return font.getStringWidth(text) * fontSize / 1000;
    }

    private static float[] widths(StatementLayout.Component table) {
        List<StatementLayout.Column> columns = table.columns();
        double total = columns.stream().mapToDouble(StatementLayout.Column::widthMm).sum();
        double scale = total <= 0 ? 1 : table.w() / total;
        float[] widths = new float[columns.size()];
        float used = 0;
        for (int i = 0; i < columns.size(); i++) {
            widths[i] = i == columns.size() - 1
                    ? (float) (table.w() - used)
                    : (float) (columns.get(i).widthMm() * scale);
            used += widths[i];
        }
        return widths;
    }

    private PDFont loadFont(PDDocument document) throws IOException {
        for (Path path : fontCandidates(properties == null ? null : properties.pdfFont())) {
            if (!Files.isRegularFile(path)) continue;
            try {
                if (isCollection(path)) {
                    try (TrueTypeCollection collection = new TrueTypeCollection(path.toFile())) {
                        List<TrueTypeFont> fonts = new ArrayList<>();
                        collection.processAllFonts(fonts::add);
                        if (!fonts.isEmpty()) return PDType0Font.load(document, fonts.get(0), true);
                    }
                } else {
                    try (InputStream input = Files.newInputStream(path)) {
                        return PDType0Font.load(document, input, true);
                    }
                }
            } catch (IOException | RuntimeException ignored) {
                // 换下一个字体，避免某个字体文件损坏导致整张单子出不来。
            }
        }
        throw bad("服务器未找到可用的中文字体，请配置 app.finance.pdf-font 指向 TTF/TTC 字体文件");
    }

    private static boolean isCollection(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".ttc") || name.endsWith(".otc");
    }

    /** 配置优先，其次按操作系统常见的黑体/雅黑/文泉驿顺序查找。测试也会用它确认环境是否有字体。 */
    static List<Path> fontCandidates(String configured) {
        List<Path> paths = new ArrayList<>();
        if (configured != null && !configured.isBlank()) {
            try {
                paths.add(Path.of(configured.strip()));
            } catch (RuntimeException ignored) {
                // 配置的路径不合法时继续走系统字体。
            }
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            Path fonts = Path.of("C:/Windows/Fonts");
            paths.add(fonts.resolve("simhei.ttf"));
            paths.add(fonts.resolve("msyh.ttc"));
            paths.add(fonts.resolve("simfang.ttf"));
            paths.add(fonts.resolve("simkai.ttf"));
            paths.add(fonts.resolve("Deng.ttf"));
        } else if (os.contains("mac")) {
            paths.add(Path.of("/System/Library/Fonts/PingFang.ttc"));
            paths.add(Path.of("/System/Library/Fonts/STHeiti Medium.ttc"));
            paths.add(Path.of("/Library/Fonts/Arial Unicode.ttf"));
        } else {
            paths.add(Path.of("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"));
            paths.add(Path.of("/usr/share/fonts/truetype/wqy/wqy-microhei.ttc"));
            paths.add(Path.of("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"));
            paths.add(Path.of("/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc"));
        }
        return paths;
    }

    private static float pt(double mm) {
        return (float) (mm * MM_TO_POINTS);
    }

    private static BusinessException bad(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    /** 管理当前页和内容流；换页时先关闭上一页的流。 */
    private static final class Canvas {
        private final PDDocument document;
        private final StatementLayout.Page page;
        private PDPageContentStream stream;

        private Canvas(PDDocument document, StatementLayout.Page page) throws IOException {
            this.document = document;
            this.page = page;
            next();
        }

        private void next() throws IOException {
            if (stream != null) stream.close();
            PDPage current = new PDPage(new PDRectangle(pt(page.widthMm()), pt(page.heightMm())));
            document.addPage(current);
            stream = new PDPageContentStream(document, current);
        }

        private void close() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }
    }
}
