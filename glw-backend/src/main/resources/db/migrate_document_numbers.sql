-- MySQL 8+. Stop application writes while running this migration.
-- Run migrate_inbound_many_to_many.sql first on databases with the old inbound header.
-- Preserves IDs, customer reference numbers, quantities and costs. Safe to rerun.
SET NAMES utf8mb4;
SET @dn_old_time_zone = @@SESSION.time_zone;
SET SESSION time_zone = '+08:00';
CREATE TABLE IF NOT EXISTS document_number_sequence (
  document_type varchar(8) NOT NULL,
  business_date date NOT NULL,
  last_sequence int NOT NULL DEFAULT 0,
  PRIMARY KEY (document_type, business_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP PROCEDURE IF EXISTS glw_migrate_document_numbers;
DELIMITER $$
CREATE PROCEDURE glw_migrate_document_numbers()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    DROP TEMPORARY TABLE IF EXISTS tmp_document_numbers;
    SET SESSION time_zone = @dn_old_time_zone;
    RESIGNAL;
  END;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='outbound_order' AND column_name='outbound_order_no') THEN
    ALTER TABLE outbound_order ADD COLUMN outbound_order_no varchar(64) NULL COMMENT '出库单编号' AFTER id;
  END IF;
  DROP TEMPORARY TABLE IF EXISTS tmp_document_numbers;
  CREATE TEMPORARY TABLE tmp_document_numbers (
    document_type varchar(8) NOT NULL,
    document_id bigint NOT NULL,
    business_date date NOT NULL,
    sequence_no int NOT NULL,
    PRIMARY KEY (document_type, document_id)
  );
  START TRANSACTION;
  INSERT INTO tmp_document_numbers (document_type,document_id,business_date,sequence_no)
  SELECT 'SO', source.id, DATE(COALESCE(source.order_time,source.created_at)),
    ROW_NUMBER() OVER (PARTITION BY DATE(COALESCE(source.order_time,source.created_at)) ORDER BY source.id)
    + COALESCE((SELECT MAX(CAST(RIGHT(existing.order_no,3) AS UNSIGNED)) FROM sales_order existing
        WHERE DATE(COALESCE(existing.order_time,existing.created_at)) = DATE(COALESCE(source.order_time,source.created_at))
          AND COALESCE(REGEXP_LIKE(existing.order_no, CONCAT('^SO', DATE_FORMAT(COALESCE(existing.order_time,existing.created_at),'%Y%m%d'), '[0-9]{3}$'), 'c') AND RIGHT(existing.order_no,3) <> '000', 0)), 0)
  FROM sales_order source WHERE NOT COALESCE(REGEXP_LIKE(source.order_no, CONCAT('^SO', DATE_FORMAT(COALESCE(source.order_time,source.created_at),'%Y%m%d'), '[0-9]{3}$'), 'c') AND RIGHT(source.order_no,3) <> '000', 0);
  INSERT INTO tmp_document_numbers (document_type,document_id,business_date,sequence_no)
  SELECT 'GL', source.id, DATE(COALESCE(source.order_time,source.created_at)),
    ROW_NUMBER() OVER (PARTITION BY DATE(COALESCE(source.order_time,source.created_at)) ORDER BY source.id)
    + COALESCE((SELECT MAX(CAST(RIGHT(existing.purchase_order_no,3) AS UNSIGNED)) FROM purchase_order existing
        WHERE DATE(COALESCE(existing.order_time,existing.created_at)) = DATE(COALESCE(source.order_time,source.created_at))
          AND COALESCE(REGEXP_LIKE(existing.purchase_order_no, CONCAT('^GL', DATE_FORMAT(COALESCE(existing.order_time,existing.created_at),'%Y%m%d'), '[0-9]{3}$'), 'c') AND RIGHT(existing.purchase_order_no,3) <> '000', 0)), 0)
  FROM purchase_order source WHERE NOT COALESCE(REGEXP_LIKE(source.purchase_order_no, CONCAT('^GL', DATE_FORMAT(COALESCE(source.order_time,source.created_at),'%Y%m%d'), '[0-9]{3}$'), 'c') AND RIGHT(source.purchase_order_no,3) <> '000', 0);
  INSERT INTO tmp_document_numbers (document_type,document_id,business_date,sequence_no)
  SELECT 'IN', source.id, DATE(COALESCE(source.inbound_time,source.created_at)),
    ROW_NUMBER() OVER (PARTITION BY DATE(COALESCE(source.inbound_time,source.created_at)) ORDER BY source.id)
    + COALESCE((SELECT MAX(CAST(RIGHT(existing.inbound_order_no,3) AS UNSIGNED)) FROM inbound_order existing
        WHERE DATE(COALESCE(existing.inbound_time,existing.created_at)) = DATE(COALESCE(source.inbound_time,source.created_at))
          AND COALESCE(REGEXP_LIKE(existing.inbound_order_no, CONCAT('^IN', DATE_FORMAT(COALESCE(existing.inbound_time,existing.created_at),'%Y%m%d'), '[0-9]{3}$'), 'c') AND RIGHT(existing.inbound_order_no,3) <> '000', 0)), 0)
  FROM inbound_order source WHERE NOT COALESCE(REGEXP_LIKE(source.inbound_order_no, CONCAT('^IN', DATE_FORMAT(COALESCE(source.inbound_time,source.created_at),'%Y%m%d'), '[0-9]{3}$'), 'c') AND RIGHT(source.inbound_order_no,3) <> '000', 0);
  INSERT INTO tmp_document_numbers (document_type,document_id,business_date,sequence_no)
  SELECT 'OUT', source.id, DATE(COALESCE(source.outbound_time,source.created_at)),
    ROW_NUMBER() OVER (PARTITION BY DATE(COALESCE(source.outbound_time,source.created_at)) ORDER BY source.id)
    + COALESCE((SELECT MAX(CAST(RIGHT(existing.outbound_order_no,3) AS UNSIGNED)) FROM outbound_order existing
        WHERE DATE(COALESCE(existing.outbound_time,existing.created_at)) = DATE(COALESCE(source.outbound_time,source.created_at))
          AND COALESCE(REGEXP_LIKE(existing.outbound_order_no, CONCAT('^OUT', DATE_FORMAT(COALESCE(existing.outbound_time,existing.created_at),'%Y%m%d'), '[0-9]{3}$'), 'c') AND RIGHT(existing.outbound_order_no,3) <> '000', 0)), 0)
  FROM outbound_order source WHERE NOT COALESCE(REGEXP_LIKE(source.outbound_order_no, CONCAT('^OUT', DATE_FORMAT(COALESCE(source.outbound_time,source.created_at),'%Y%m%d'), '[0-9]{3}$'), 'c') AND RIGHT(source.outbound_order_no,3) <> '000', 0);
  IF EXISTS (SELECT 1 FROM tmp_document_numbers WHERE sequence_no > 999) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Daily document sequence exceeds 999; no document numbers changed';
  END IF;
  UPDATE sales_order doc JOIN tmp_document_numbers m ON m.document_type='SO' AND m.document_id=doc.id
    SET doc.order_no=CONCAT('__DN_SO_',doc.id), doc.updated_at=doc.updated_at;
  UPDATE sales_order doc JOIN tmp_document_numbers m ON m.document_type='SO' AND m.document_id=doc.id
    SET doc.order_no=CONCAT('SO',DATE_FORMAT(m.business_date,'%Y%m%d'),LPAD(m.sequence_no,3,'0')), doc.updated_at=doc.updated_at;
  UPDATE purchase_order doc JOIN tmp_document_numbers m ON m.document_type='GL' AND m.document_id=doc.id
    SET doc.purchase_order_no=CONCAT('__DN_GL_',doc.id), doc.updated_at=doc.updated_at;
  UPDATE purchase_order doc JOIN tmp_document_numbers m ON m.document_type='GL' AND m.document_id=doc.id
    SET doc.purchase_order_no=CONCAT('GL',DATE_FORMAT(m.business_date,'%Y%m%d'),LPAD(m.sequence_no,3,'0')), doc.updated_at=doc.updated_at;
  UPDATE inbound_order doc JOIN tmp_document_numbers m ON m.document_type='IN' AND m.document_id=doc.id
    SET doc.inbound_order_no=CONCAT('__DN_IN_',doc.id), doc.updated_at=doc.updated_at;
  UPDATE inbound_order doc JOIN tmp_document_numbers m ON m.document_type='IN' AND m.document_id=doc.id
    SET doc.inbound_order_no=CONCAT('IN',DATE_FORMAT(m.business_date,'%Y%m%d'),LPAD(m.sequence_no,3,'0')), doc.updated_at=doc.updated_at;
  UPDATE outbound_order doc JOIN tmp_document_numbers m ON m.document_type='OUT' AND m.document_id=doc.id
    SET doc.outbound_order_no=CONCAT('__DN_OUT_',doc.id), doc.updated_at=doc.updated_at;
  UPDATE outbound_order doc JOIN tmp_document_numbers m ON m.document_type='OUT' AND m.document_id=doc.id
    SET doc.outbound_order_no=CONCAT('OUT',DATE_FORMAT(m.business_date,'%Y%m%d'),LPAD(m.sequence_no,3,'0')), doc.updated_at=doc.updated_at;
  UPDATE purchase_order_item detail JOIN purchase_order po ON po.id=detail.purchase_order_id
    SET detail.purchase_order_no=po.purchase_order_no, detail.updated_at=detail.updated_at;
  UPDATE inbound_order_item detail JOIN purchase_order po ON po.id=detail.purchase_order_id
    SET detail.purchase_order_no=po.purchase_order_no, detail.updated_at=detail.updated_at;
  UPDATE inbound_order_batch_item detail JOIN purchase_order po ON po.id=detail.purchase_order_id
    SET detail.purchase_order_no=po.purchase_order_no, detail.updated_at=detail.updated_at;
  UPDATE inbound_order_purchase_order detail JOIN purchase_order po ON po.id=detail.purchase_order_id
    SET detail.purchase_order_no=po.purchase_order_no, detail.updated_at=detail.updated_at;
  INSERT INTO document_number_sequence (document_type,business_date,last_sequence)
  SELECT 'SO', DATE(COALESCE(order_time,created_at)), MAX(CAST(RIGHT(order_no,3) AS UNSIGNED)) FROM sales_order
  GROUP BY DATE(COALESCE(order_time,created_at))
  ON DUPLICATE KEY UPDATE last_sequence=GREATEST(last_sequence,VALUES(last_sequence));
  INSERT INTO document_number_sequence (document_type,business_date,last_sequence)
  SELECT 'GL', DATE(COALESCE(order_time,created_at)), MAX(CAST(RIGHT(purchase_order_no,3) AS UNSIGNED)) FROM purchase_order
  GROUP BY DATE(COALESCE(order_time,created_at))
  ON DUPLICATE KEY UPDATE last_sequence=GREATEST(last_sequence,VALUES(last_sequence));
  INSERT INTO document_number_sequence (document_type,business_date,last_sequence)
  SELECT 'IN', DATE(COALESCE(inbound_time,created_at)), MAX(CAST(RIGHT(inbound_order_no,3) AS UNSIGNED)) FROM inbound_order
  GROUP BY DATE(COALESCE(inbound_time,created_at))
  ON DUPLICATE KEY UPDATE last_sequence=GREATEST(last_sequence,VALUES(last_sequence));
  INSERT INTO document_number_sequence (document_type,business_date,last_sequence)
  SELECT 'OUT', DATE(COALESCE(outbound_time,created_at)), MAX(CAST(RIGHT(outbound_order_no,3) AS UNSIGNED)) FROM outbound_order
  GROUP BY DATE(COALESCE(outbound_time,created_at))
  ON DUPLICATE KEY UPDATE last_sequence=GREATEST(last_sequence,VALUES(last_sequence));
  COMMIT;
  DROP TEMPORARY TABLE tmp_document_numbers;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='purchase_order' AND index_name='uk_purchase_order_no') THEN
    ALTER TABLE purchase_order ADD UNIQUE KEY uk_purchase_order_no(purchase_order_no);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='outbound_order' AND index_name='uk_outbound_order_no') THEN
    ALTER TABLE outbound_order ADD UNIQUE KEY uk_outbound_order_no(outbound_order_no);
  END IF;
END$$
DELIMITER ;
CALL glw_migrate_document_numbers();
DROP PROCEDURE glw_migrate_document_numbers;
SET SESSION time_zone = @dn_old_time_zone;
