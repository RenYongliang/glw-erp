-- 可视化对账单设计器：布局 JSON 与模板文件共存，layout_json 非空时导出走布局渲染器。
SET NAMES utf8mb4;
ALTER TABLE fin_statement_template
 ADD COLUMN layout_json longtext NULL COMMENT '可视化设计器布局，NULL 表示传统 Excel 文件模板' AFTER file_name;
