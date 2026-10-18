-- Incremental migration: preserves receipt IDs, allocation IDs and inventory batches.
-- Take a database backup first. Historical packages must be merged only after confirmation.
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS `inbound_order_purchase_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `inbound_order_id` bigint NOT NULL,
  `purchase_order_id` bigint NOT NULL,
  `purchase_order_no` varchar(64) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inbound_purchase` (`inbound_order_id`, `purchase_order_id`),
  KEY `idx_purchase_order_id` (`purchase_order_id`),
  KEY `idx_purchase_order_no` (`purchase_order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='入库单与采购单关联表';

DROP PROCEDURE IF EXISTS migrate_inbound_many_to_many;
DELIMITER $$
CREATE PROCEDURE migrate_inbound_many_to_many()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inbound_order' AND column_name='inbound_order_no') THEN
    ALTER TABLE inbound_order ADD COLUMN inbound_order_no varchar(64) NULL DEFAULT NULL COMMENT '入库单编号' AFTER id;
  END IF;
  -- Backfill numbers with migrate_document_numbers.sql after historical package merges.
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inbound_order' AND column_name='purchase_order_id') THEN
    INSERT INTO inbound_order_purchase_order (inbound_order_id,purchase_order_id,purchase_order_no)
    SELECT r.id,r.purchase_order_id,COALESCE(r.purchase_order_no,p.purchase_order_no)
    FROM inbound_order r JOIN purchase_order p ON p.id=r.purchase_order_id
    LEFT JOIN inbound_order_purchase_order rel ON rel.inbound_order_id=r.id AND rel.purchase_order_id=r.purchase_order_id
    WHERE rel.id IS NULL;
  END IF;
  INSERT INTO inbound_order_purchase_order (inbound_order_id,purchase_order_id,purchase_order_no)
  SELECT DISTINCT i.inbound_order_id,i.purchase_order_id,i.purchase_order_no
  FROM inbound_order_item i
  LEFT JOIN inbound_order_purchase_order rel ON rel.inbound_order_id=i.inbound_order_id AND rel.purchase_order_id=i.purchase_order_id
  WHERE rel.id IS NULL;
  IF EXISTS (SELECT 1 FROM inbound_order r LEFT JOIN inbound_order_purchase_order rel ON rel.inbound_order_id=r.id WHERE rel.id IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Receipt without purchase association; inspect before dropping legacy columns';
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inbound_order' AND column_name='purchase_order_id') THEN
    ALTER TABLE inbound_order DROP COLUMN purchase_order_id;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inbound_order' AND column_name='purchase_order_no') THEN
    ALTER TABLE inbound_order DROP COLUMN purchase_order_no;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='inbound_order' AND index_name='uk_inbound_order_no') THEN
    ALTER TABLE inbound_order ADD UNIQUE KEY uk_inbound_order_no(inbound_order_no);
  END IF;
END$$
DELIMITER ;
CALL migrate_inbound_many_to_many();
DROP PROCEDURE migrate_inbound_many_to_many;
