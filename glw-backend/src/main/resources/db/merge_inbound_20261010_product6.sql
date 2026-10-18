-- Confirmed package: product 6, 2026-10-10, allocations 5000 + 1000 + 3000.
-- Run migrate_inbound_many_to_many.sql first. Other products keep their receipts.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS merge_inbound_20261010_product6;
DELIMITER $$
CREATE PROCEDURE merge_inbound_20261010_product6()
BEGIN
  DECLARE matched_rows INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  START TRANSACTION;
  SELECT COUNT(*) INTO matched_rows
  FROM inbound_order_item i
  JOIN purchase_order_item p ON p.id=i.purchase_order_item_id
  JOIN inbound_order r ON r.id=i.inbound_order_id
  WHERE p.product_id=6 AND DATE(r.inbound_time)='2026-10-10' AND r.supplier_id=1 AND r.status='COMPLETED'
    AND ((i.id=17 AND i.purchase_order_id=6 AND i.inbound_quantity=5000 AND i.inbound_order_id=14)
      OR (i.id=27 AND i.purchase_order_id=11 AND i.inbound_quantity=1000 AND i.inbound_order_id IN (14,22))
      OR (i.id=36 AND i.purchase_order_id=17 AND i.inbound_quantity=3000 AND i.inbound_order_id IN (14,29)));
  IF matched_rows <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical package allocations differ; inspect data before merging';
  END IF;
  UPDATE inbound_order SET version=version+1 WHERE id IN (14,22,29)
    AND EXISTS (SELECT 1 FROM inbound_order_item i WHERE i.id IN (27,36) AND i.inbound_order_id IN (22,29));
  UPDATE inbound_order_item SET inbound_order_id=14 WHERE id IN (27,36) AND inbound_order_id IN (22,29);
  UPDATE inbound_order_batch_item SET inbound_order_id=14 WHERE inbound_order_item_id IN (27,36) AND inbound_order_id IN (22,29);
  INSERT INTO inbound_order_purchase_order (inbound_order_id,purchase_order_id,purchase_order_no)
  SELECT DISTINCT i.inbound_order_id,i.purchase_order_id,i.purchase_order_no FROM inbound_order_item i
  LEFT JOIN inbound_order_purchase_order rel ON rel.inbound_order_id=i.inbound_order_id AND rel.purchase_order_id=i.purchase_order_id
  WHERE i.inbound_order_id=14 AND rel.id IS NULL;
  DELETE rel FROM inbound_order_purchase_order rel
  WHERE rel.inbound_order_id IN (22,29) AND NOT EXISTS
    (SELECT 1 FROM inbound_order_item i WHERE i.inbound_order_id=rel.inbound_order_id AND i.purchase_order_id=rel.purchase_order_id);
  DELETE r FROM inbound_order r WHERE r.id IN (22,29)
    AND NOT EXISTS (SELECT 1 FROM inbound_order_item i WHERE i.inbound_order_id=r.id)
    AND NOT EXISTS (SELECT 1 FROM inbound_order_batch_item b WHERE b.inbound_order_id=r.id);
  COMMIT;
END$$
DELIMITER ;
CALL merge_inbound_20261010_product6();
DROP PROCEDURE merge_inbound_20261010_product6;
