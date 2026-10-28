-- Finance V1, MySQL 8+. Rebuild order: glw.sql -> full_data.sql -> finance.sql.
-- WARNING: this script resets finance records only. Then use finance historical rebuild.
SET NAMES utf8mb4;
DROP TABLE IF EXISTS fin_audit;
DROP TABLE IF EXISTS fin_allocation;
DROP TABLE IF EXISTS fin_statement_item;
DROP TABLE IF EXISTS fin_statement;
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
 updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY uk_partner (direction, partner_id),
 CHECK (direction IN ('AR','AP')),
 CHECK (term_days BETWEEN 0 AND 3650),
 CHECK (term_basis IN ('BIZ_DATE','MONTH_END'))
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
 period varchar(7) NOT NULL,
 due_date date NOT NULL,
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
 period varchar(7) NOT NULL,
 currency varchar(8) NOT NULL DEFAULT 'CNY',
 status varchar(20) NOT NULL DEFAULT 'DRAFT',
 gross_amount decimal(12,4) NOT NULL,
 remark varchar(500) NOT NULL DEFAULT '',
 sent_at datetime NULL,
 confirmed_at datetime NULL,
 created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_no (statement_no),
 KEY idx_list (direction, partner_id, period, status)
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
