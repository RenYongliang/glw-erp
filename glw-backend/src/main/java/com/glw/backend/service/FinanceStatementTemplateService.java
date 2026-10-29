package com.glw.backend.service;

import com.glw.backend.finance.FinanceModels.StatementTemplate;
import com.glw.backend.finance.statement.DocumentType;
import com.glw.backend.finance.statement.StatementFile;
import com.glw.backend.finance.statement.StatementLayoutDetail;
import com.glw.backend.finance.statement.StatementTemplateLayoutInput;
import com.glw.backend.model.entity.FinStatementTemplate;
import java.util.List;

/** 通用文档模板的解析、版本和可视化布局管理。 */
public interface FinanceStatementTemplateService {
    /** 模板中心支持的单据类型。 */
    List<DocumentType.Option> documentTypes();

    /** 首次启动或某类型被清空时，补一份系统全局默认可视化模板。 */
    void ensureDefault(String docType, String direction);

    /**
     * @param requestedFormat 为空时取客户默认模板（默认标记优先，其次 XLSX），CSV 返回 null 表示使用内置格式
     */
    FinStatementTemplate resolve(String direction, Long partnerId, String requestedFormat);

    /** 出单时选定模板，用于在 fin_statement 上留版本快照。 */
    FinStatementTemplate snapshotFor(String direction, Long partnerId);

    FinStatementTemplate version(Long id, Integer version);

    List<StatementTemplate> templates(String docType, String direction, Long partnerId);

    /** 保存可视化设计器布局，按选择的格式生成新的 XLSX/PDF 版本。 */
    StatementTemplate saveLayout(StatementTemplateLayoutInput input);

    /** 读取可视化布局，供设计器继续编辑。 */
    StatementLayoutDetail layout(Long id);

    /** 用样例数据把布局渲染成指定格式，用于设计器预览。 */
    StatementFile previewLayout(tools.jackson.databind.JsonNode layout, String format,
                                String docType, String direction);

    StatementTemplate setDefault(Long id);

    /** 软删除模板；已删除模板不再出现在列表和导出解析中。 */
    StatementTemplate delete(Long id);
}
