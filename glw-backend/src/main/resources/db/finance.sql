-- Finance V1, MySQL 8+. Rebuild order: glw.sql -> full_data.sql -> finance.sql.
-- WARNING: this script resets finance records only. Then use finance historical rebuild.
SET NAMES utf8mb4;
DROP TABLE IF EXISTS fin_audit;
DROP TABLE IF EXISTS fin_allocation;
DROP TABLE IF EXISTS fin_statement_item;
DROP TABLE IF EXISTS fin_statement;
DROP TABLE IF EXISTS fin_statement_template;
DROP TABLE IF EXISTS fin_payment;
DROP TABLE IF EXISTS fin_open_item;
DROP TABLE IF EXISTS fin_profile;
DROP TABLE IF EXISTS fin_lock;
CREATE TABLE fin_lock (id int PRIMARY KEY) ENGINE=InnoDB;
INSERT INTO fin_lock VALUES (1);

CREATE TABLE fin_profile (
 id bigint PRIMARY KEY AUTO_INCREMENT,
 direction varchar(2) NOT NULL,
 partner_id bigint NOT NULL,
 term_days int NOT NULL DEFAULT 0,
 term_basis varchar(20) NOT NULL DEFAULT 'BIZ_DATE',
 cutoff_day int NULL COMMENT '截止日 1-31；NULL=自然月',
 cycle_months int NOT NULL DEFAULT 1 COMMENT '账单周期长度（月）',
 statement_lag_months int NOT NULL DEFAULT 1 COMMENT '出单月 = 账单结束月 + N',
 statement_day int NOT NULL DEFAULT 1 COMMENT '出单日（1-31）',
 due_rule varchar(20) NOT NULL DEFAULT 'CREDIT_DAYS' COMMENT 'CREDIT_DAYS/CALENDAR',
 credit_days int NOT NULL DEFAULT 0 COMMENT '固定天数到期算法',
 credit_basis varchar(20) NOT NULL DEFAULT 'PERIOD_END' COMMENT 'CREDIT_DAYS 起算点：PERIOD_END/ISSUE_DATE/ISSUE_MONTH_END',
 due_month_offset int NOT NULL DEFAULT 0 COMMENT 'CALENDAR：账单结束月后第 N 个月',
 due_day int NOT NULL DEFAULT 0 COMMENT 'CALENDAR：第几天，0=月末',
 auto_generate tinyint NOT NULL DEFAULT 0 COMMENT '是否自动生成对账单',
 reconcile_basis varchar(20) NOT NULL DEFAULT 'INBOUND' COMMENT 'AR: SIGNED/INBOUND；AP: INBOUND',
 updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY uk_partner (direction, partner_id),
 CHECK (direction IN ('AR','AP')),
 CHECK (term_days BETWEEN 0 AND 3650),
 CHECK (term_basis IN ('BIZ_DATE','MONTH_END')),
 CHECK (cutoff_day IS NULL OR cutoff_day BETWEEN 1 AND 31),
 CHECK (cycle_months BETWEEN 1 AND 12),
 CHECK (statement_day BETWEEN 1 AND 31),
 CHECK (due_rule IN ('CREDIT_DAYS','CALENDAR')),
 CHECK (credit_basis IN ('PERIOD_END','ISSUE_DATE','ISSUE_MONTH_END')),
 CHECK (due_day BETWEEN 0 AND 31)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE fin_open_item (
 id bigint PRIMARY KEY AUTO_INCREMENT,
 direction varchar(2) NOT NULL,
 source_type varchar(16) NOT NULL,
 source_id bigint NOT NULL,
 source_line_id bigint NOT NULL,
 source_no varchar(64) NOT NULL,
 order_id bigint NOT NULL,
 order_no varchar(64) NOT NULL,
 partner_order_no varchar(64) NULL,
 partner_id bigint NOT NULL,
 partner_name varchar(255) NOT NULL,
 product_id bigint NOT NULL,
 brand varchar(64) NULL,
 part_no varchar(64) NOT NULL,
 package_type varchar(64) NULL,
 quantity int NOT NULL,
 unit_price decimal(12,4) NOT NULL,
 tax_included int NOT NULL,
 tax_rate int NULL,
 net_amount decimal(12,4) NULL,
 tax_amount decimal(12,4) NULL,
 gross_amount decimal(12,4) NULL COMMENT '未税且税率未知时为空，待确认后参与对账',
 currency varchar(8) NOT NULL DEFAULT 'CNY',
 biz_date date NOT NULL,
 basis_date date NULL COMMENT '结算基准日（AR=签收日，AP=入库日）',
 basis_ready tinyint NOT NULL DEFAULT 1 COMMENT '基准是否就绪，0=待签收',
 period varchar(7) NULL COMMENT '账单月 YYYY-MM；待签收时为空',
 period_start date NULL COMMENT '该账单月的记账区间开始',
 period_end date NULL COMMENT '账单期间结束',
 settle_month varchar(7) NULL COMMENT '结算月份 YYYY-MM',
 due_date date NULL COMMENT '到期日；待签收时为空',
 reconcile_status varchar(20) NOT NULL DEFAULT 'OPEN',
 statement_id bigint NULL,
 settled_amount decimal(12,4) NOT NULL DEFAULT 0,
 created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_source (source_type, source_line_id),
 KEY idx_pool (direction, partner_id, period, reconcile_status, biz_date),
 KEY idx_statement (statement_id),
 KEY idx_order (direction, order_id),
 CHECK (quantity > 0),
 CHECK (gross_amount >= 0 AND settled_amount >= 0 AND settled_amount <= gross_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE fin_statement (
 id bigint PRIMARY KEY AUTO_INCREMENT,
 statement_no varchar(64) NOT NULL,
 direction varchar(2) NOT NULL,
 partner_id bigint NOT NULL,
 partner_name varchar(255) NOT NULL,
 period varchar(20) NOT NULL COMMENT '账单期间标签：单月 2026-09，跨月 2026-08~2026-09',
 period_start date NULL COMMENT '账单期间开始',
 period_end date NULL COMMENT '账单期间结束',
 settle_month varchar(7) NULL COMMENT '结算月份 YYYY-MM',
 due_date date NULL COMMENT '到期日',
 auto_generated tinyint NOT NULL DEFAULT 0 COMMENT '是否自动生成',
 currency varchar(8) NOT NULL DEFAULT 'CNY',
 status varchar(20) NOT NULL DEFAULT 'DRAFT',
 gross_amount decimal(12,4) NOT NULL,
 remark varchar(500) NOT NULL DEFAULT '',
 template_id bigint NULL COMMENT '出单时绑定的对账单模板，模板改版后旧单仍按旧版重导',
 template_version int NULL,
 sent_at datetime NULL,
 confirmed_at datetime NULL,
 created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_no (statement_no),
 KEY idx_list (direction, partner_id, period, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE fin_statement_template (
 id bigint PRIMARY KEY AUTO_INCREMENT,
 direction varchar(2) NOT NULL,
 doc_type varchar(32) NOT NULL DEFAULT 'STATEMENT' COMMENT 'STATEMENT/SALES_CONTRACT/PURCHASE_CONTRACT/DELIVERY_NOTE',
 partner_id bigint NOT NULL DEFAULT 0 COMMENT '0 表示全局默认模板',
 format varchar(10) NOT NULL COMMENT 'XLSX / PDF',
 name varchar(128) NOT NULL,
 version int NOT NULL DEFAULT 1,
 file_key varchar(255) NOT NULL COMMENT '模板目录下的文件键，classpath: 前缀表示内置模板',
 file_name varchar(255) NOT NULL,
 layout_json longtext NULL COMMENT '可视化设计器布局，NULL 表示传统 Excel 文件模板',
 is_default tinyint NOT NULL DEFAULT 0,
 enabled tinyint NOT NULL DEFAULT 1,
 deleted tinyint NOT NULL DEFAULT 0 COMMENT '软删除标记',
 created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_resolve (direction, partner_id, format, is_default, enabled),
 KEY idx_doc (doc_type, direction, partner_id, format, deleted),
 CHECK (direction IN ('AR','AP')),
 CHECK (format IN ('XLSX','PDF')),
 CHECK (partner_id >= 0),
 CHECK (version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE fin_statement_item (
 id bigint PRIMARY KEY AUTO_INCREMENT,
 statement_id bigint NOT NULL,
 open_item_id bigint NOT NULL,
 source_no varchar(64) NOT NULL,
 order_no varchar(64) NOT NULL,
 partner_order_no varchar(64) NULL,
 brand varchar(64) NULL,
 part_no varchar(64) NOT NULL,
 package_type varchar(64) NULL,
 quantity int NOT NULL,
 unit_price decimal(12,4) NOT NULL,
 tax_included int NOT NULL,
 tax_rate int NULL,
 net_amount decimal(12,4) NULL,
 tax_amount decimal(12,4) NULL,
 gross_amount decimal(12,4) NOT NULL,
 biz_date date NOT NULL,
 due_date date NOT NULL,
 UNIQUE KEY uk_statement_item (statement_id, open_item_id),
 KEY idx_open (open_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE fin_payment (
 id bigint PRIMARY KEY AUTO_INCREMENT,
 payment_no varchar(64) NOT NULL,
 request_key varchar(64) NOT NULL,
 direction varchar(2) NOT NULL,
 partner_id bigint NOT NULL,
 partner_name varchar(255) NOT NULL,
 currency varchar(8) NOT NULL DEFAULT 'CNY',
 payment_date date NOT NULL,
 amount decimal(12,4) NOT NULL,
 allocated_amount decimal(12,4) NOT NULL DEFAULT 0,
 bank_reference varchar(128) NOT NULL DEFAULT '',
 remark varchar(500) NOT NULL DEFAULT '',
 status varchar(20) NOT NULL DEFAULT 'RECEIVED',
 created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_no (payment_no),
 UNIQUE KEY uk_request (request_key),
 KEY idx_list (direction, partner_id, payment_date),
 CHECK (amount > 0 AND allocated_amount >= 0 AND allocated_amount <= amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE fin_allocation (
 id bigint PRIMARY KEY AUTO_INCREMENT,
 request_key varchar(64) NOT NULL,
 payment_id bigint NOT NULL,
 open_item_id bigint NOT NULL,
 amount decimal(12,4) NOT NULL,
 status varchar(16) NOT NULL DEFAULT 'ACTIVE',
 created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 reversed_at datetime NULL,
 UNIQUE KEY uk_request (request_key),
 KEY idx_payment (payment_id, status),
 KEY idx_open (open_item_id, status),
 CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE fin_audit (
 id bigint PRIMARY KEY AUTO_INCREMENT,
 entity_type varchar(20) NOT NULL,
 entity_id bigint NOT NULL,
 action varchar(32) NOT NULL,
 actor_id bigint NULL,
 detail varchar(1000) NOT NULL DEFAULT '',
 created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_entity (entity_type, entity_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
