-- 对账单多客户模板：模板元数据表 + 出单时的模板快照字段。
-- 已有库执行本脚本；新库直接执行 finance.sql。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS fin_statement_template (
 id bigint PRIMARY KEY AUTO_INCREMENT,
 direction varchar(2) NOT NULL,
 partner_id bigint NOT NULL DEFAULT 0 COMMENT '0 表示全局默认模板',
 format varchar(10) NOT NULL COMMENT 'XLSX / PDF',
 name varchar(128) NOT NULL,
 version int NOT NULL DEFAULT 1,
 file_key varchar(255) NOT NULL COMMENT '模板目录下的文件键，classpath: 前缀表示内置模板',
 file_name varchar(255) NOT NULL,
 is_default tinyint NOT NULL DEFAULT 0,
 enabled tinyint NOT NULL DEFAULT 1,
 created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_resolve (direction, partner_id, format, is_default, enabled),
 CHECK (direction IN ('AR','AP')),
 CHECK (format IN ('XLSX','PDF')),
 CHECK (partner_id >= 0),
 CHECK (version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE fin_statement
 ADD COLUMN template_id bigint NULL COMMENT '出单时绑定的对账单模板，模板改版后旧单仍按旧版重导' AFTER remark,
 ADD COLUMN template_version int NULL AFTER template_id;
