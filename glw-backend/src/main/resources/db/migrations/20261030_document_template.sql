-- 模板中心从「对账单模板」升级为通用文档模板。
ALTER TABLE fin_statement_template
  ADD COLUMN doc_type varchar(32) NOT NULL DEFAULT 'STATEMENT'
    COMMENT 'STATEMENT/SALES_CONTRACT/PURCHASE_CONTRACT/DELIVERY_NOTE' AFTER direction,
  ADD COLUMN deleted tinyint NOT NULL DEFAULT 0 COMMENT '软删除标记' AFTER enabled;

CREATE INDEX idx_stmt_tpl_doc ON fin_statement_template (doc_type, direction, partner_id, format, deleted);

-- 模板中心只保留可视化模板；历史上传的 Excel/PDF 文件模板不再出现在列表中。
UPDATE fin_statement_template SET deleted=1 WHERE layout_json IS NULL OR layout_json='';
