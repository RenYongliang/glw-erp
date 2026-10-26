-- full_data.sql: cumulative confirmed business data.
-- Confirmed sheets: 采购订单合同 / 销售订单 / 出库登记.
-- Confirmed workbook SHA256: cb0e641271f8972e25d1ef5a50f518c3f8db6f689f699d89ce2e583983e19401
-- Includes all referenced supplier/product rows and local login users. No prior data import is needed.
-- Document numbers: SO / GL / IN / OUT + yyyyMMdd + 3-digit daily sequence.
-- Initialization order: glw.sql, then full_data.sql. No other SQL scripts are required.
SET NAMES utf8mb4;
SET @fd_old_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = 'STRICT_TRANS_TABLES,NO_BACKSLASH_ESCAPES,NO_ENGINE_SUBSTITUTION';
SET @fd_old_time_zone = @@SESSION.time_zone;
SET SESSION time_zone = '+08:00';
DROP PROCEDURE IF EXISTS `glw_import_full_data`;
DELIMITER $$
CREATE PROCEDURE `glw_import_full_data`()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    SET SESSION sql_mode = @fd_old_sql_mode;
    SET SESSION time_zone = @fd_old_time_zone;
    RESIGNAL;
  END;
  START TRANSACTION;
  IF EXISTS (SELECT 1 FROM `document_number_sequence` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Daily number counters must be empty: document_number_sequence';
  END IF;
  IF EXISTS (SELECT 1 FROM `sys_user` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'User table must be empty: sys_user';
  END IF;
  IF EXISTS (SELECT 1 FROM `customer` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Business table must be empty: customer';
  END IF;
  IF EXISTS (SELECT 1 FROM `customer_visit` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Business table must be empty: customer_visit';
  END IF;
  IF EXISTS (SELECT 1 FROM `inbound_order` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Business table must be empty: inbound_order';
  END IF;
  IF EXISTS (SELECT 1 FROM `inbound_order_item` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Business table must be empty: inbound_order_item';
  END IF;
  IF EXISTS (SELECT 1 FROM `inbound_order_batch_item` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Business table must be empty: inbound_order_batch_item';
  END IF;
  IF EXISTS (SELECT 1 FROM `inbound_order_purchase_order` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Business table must be empty: inbound_order_purchase_order';
  END IF;
  IF EXISTS (SELECT 1 FROM `inventory` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Business table must be empty: inventory';
  END IF;
  IF EXISTS (SELECT 1 FROM `outbound_order` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Business table must be empty: outbound_order';
  END IF;
  IF EXISTS (SELECT 1 FROM `outbound_order_item` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Business table must be empty: outbound_order_item';
  END IF;
  IF EXISTS (SELECT 1 FROM `outbound_order_batch_item` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Business table must be empty: outbound_order_batch_item';
  END IF;
  IF EXISTS (SELECT 1 FROM `product` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Business table must be empty: product';
  END IF;
  IF EXISTS (SELECT 1 FROM `purchase_order` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Business table must be empty: purchase_order';
  END IF;
  IF EXISTS (SELECT 1 FROM `purchase_order_item` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Business table must be empty: purchase_order_item';
  END IF;
  IF EXISTS (SELECT 1 FROM `sales_order` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Business table must be empty: sales_order';
  END IF;
  IF EXISTS (SELECT 1 FROM `sales_order_item` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Business table must be empty: sales_order_item';
  END IF;
  IF EXISTS (SELECT 1 FROM `sales_order_batch_item` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Business table must be empty: sales_order_batch_item';
  END IF;
  IF EXISTS (SELECT 1 FROM `sample_apply` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Business table must be empty: sample_apply';
  END IF;
  IF EXISTS (SELECT 1 FROM `supplier` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Business table must be empty: supplier';
  END IF;
  -- sys_user: 2 rows from local MySQL (BCrypt hashes preserved)
  INSERT INTO `sys_user` (`id`, `username`, `password_hash`, `name`, `role`, `department`, `enabled`, `deleted`, `version`, `created_at`, `updated_at`) VALUES
    (1, 'admin', '$2a$10$LJ9S7JCkTQf0eRubB3Fx/uSe1aZvsleTDSZE9SeMyWFv/plG7d7Nq', '管理员', 'ADMIN', '运营管理部', 1, 0, 0, '2026-10-17 12:43:07', '2026-10-17 12:43:07'),
    (2, 'demo', '$2a$10$FsQjImExBVt8PJ5OYqmUs.G69uMw0w4gbygedpjtvOmISNb1ONmGC', '体验用户', 'USER', '业务体验组', 1, 0, 0, '2026-10-17 12:43:07', '2026-10-17 12:43:07');
  -- supplier: 2 confirmed rows
  INSERT INTO `supplier` (`id`, `name`, `brand`) VALUES
    (1, '深圳中盛昌', '力芯微'),
    (2, '深圳联思微', '芯洲');
  -- product: 8 confirmed rows
  INSERT INTO `product` (`id`, `supplier_id`, `brand`, `part_no`, `package_type`) VALUES
    (1, 1, '力芯微', 'ET7303Y', 'QFN14'),
    (2, 1, '力芯微', 'ET8211', 'SOP16'),
    (3, 1, '力芯微', 'ET5A033YB', 'DFN1*1'),
    (4, 1, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)'),
    (5, 2, '芯洲', 'SCT9330STER', 'ESOP-8L'),
    (6, 1, '力芯微', 'ET5907', 'WLCSP20(0.35 Pitch)'),
    (7, 1, '力芯微', 'ET2104', 'WLCSP12(0.5 Pitch)/ QFN12/TSSOP14'),
    (8, 1, '力芯微', 'ET52318YB', 'DFN1*1');
  -- purchase_order: 17 confirmed rows
  INSERT INTO `purchase_order` (`id`, `purchase_order_no`, `order_time`, `supplier_id`, `supplier_name`, `delivery_time`, `order_status`, `payment_status`, `remark`) VALUES
    (1, 'GL20260410001', '2026-04-10 00:00:00', 1, '深圳中盛昌', NULL, 'PARTIALLY_RECEIVED', 'PARTIALLY_PAID', 'Excel采购订单合同;源行:3,5,23;采购人:Eric;结算:当月结25号前付款;已付款:44100.0000;已入库未付款:0.0000;预计交期原文:1周'),
    (2, 'GL20260515001', '2026-05-15 00:00:00', 1, '深圳中盛昌', NULL, 'PARTIALLY_RECEIVED', 'PARTIALLY_PAID', 'Excel采购订单合同;源行:4,6;采购人:Eric;结算:当月结25号前付款;已付款:6720.0000;已入库未付款:0.0000;预计交期原文:1周'),
    (3, 'GL20260720001', '2026-07-20 00:00:00', 1, '深圳中盛昌', NULL, 'COMPLETED', 'PAID', 'Excel采购订单合同;源行:7,8;采购人:Eric;结算:当月结25号前付款;已付款:4680.0000;已入库未付款:0.0000;预计交期原文:1周'),
    (4, 'GL20260720002', '2026-07-20 00:00:00', 2, '深圳联思微', NULL, 'COMPLETED', 'PAID', 'Excel采购订单合同;源行:9;采购人:Eric;结算:货到周结;已付款:200.0000;已入库未付款:0.0000;预计交期原文:1周'),
    (5, 'GL20260722001', '2026-07-22 00:00:00', 1, '深圳中盛昌', NULL, 'COMPLETED', 'PAID', 'Excel采购订单合同;源行:10;采购人:Eric;结算:当月结25号前付款;已付款:19200.0000;已入库未付款:0.0000;预计交期原文:1周'),
    (6, 'GL20260721001', '2026-07-21 00:00:00', 1, '深圳中盛昌', NULL, 'COMPLETED', 'PARTIALLY_PAID', 'Excel采购订单合同;源行:13,14,15,20,21,22,24,25,26,36,37,38,57,58,59;采购人:Eric;结算:当月结25号前付款;已付款:25050.0000;已入库未付款:4250.0000;预计交期原文:1周'),
    (7, 'GL20260731001', '2026-07-31 00:00:00', 1, '深圳中盛昌', NULL, 'COMPLETED', 'PAID', 'Excel采购订单合同;源行:17;采购人:Eric;结算:当月结25号前付款;已付款:19200.0000;已入库未付款:0.0000;预计交期原文:1周'),
    (8, 'GL20260804001', '2026-08-04 00:00:00', 2, '深圳联思微', NULL, 'COMPLETED', 'PAID', 'Excel采购订单合同;源行:18;采购人:Eric;结算:货到周结;已付款:1500.0000;已入库未付款:0.0000;预计交期原文:1周'),
    (9, 'GL20260810001', '2026-08-10 00:00:00', 1, '深圳中盛昌', NULL, 'COMPLETED', 'PAID', 'Excel采购订单合同;源行:19;采购人:Eric;结算:当月结25号前付款;已付款:38400.0000;已入库未付款:0.0000;预计交期原文:1周'),
    (10, 'GL20260818001', '2026-08-18 00:00:00', 1, '深圳中盛昌', NULL, 'COMPLETED', 'PAID', 'Excel采购订单合同;源行:29;采购人:Eric;结算:当月结25号前付款;已付款:57600.0000;已入库未付款:0.0000'),
    (11, 'GL20260818002', '2026-08-18 00:00:00', 1, '深圳中盛昌', NULL, 'PARTIALLY_RECEIVED', 'PARTIALLY_PAID', 'Excel采购订单合同;源行:30,31,32,39,40,41,46,47,48,54,55,56;采购人:Eric;结算:当月结25号前付款;已付款:21450.0000;已入库未付款:850.0000'),
    (12, 'GL20260903001', '2026-09-03 00:00:00', 1, '深圳中盛昌', NULL, 'COMPLETED', 'PAID', 'Excel采购订单合同;源行:33;采购人:Eric;结算:当月结25号前付款;已付款:750.0000;已入库未付款:0.0000'),
    (13, 'GL20260907001', '2026-09-07 00:00:00', 1, '深圳中盛昌', NULL, 'COMPLETED', 'PARTIALLY_PAID', 'Excel采购订单合同;源行:34,35;采购人:Eric;结算:当月结25号前付款;已付款:1500.0000;已入库未付款:57600.0000'),
    (14, 'GL20260910001', '2026-09-10 00:00:00', 1, '深圳中盛昌', NULL, 'COMPLETED', 'PARTIALLY_PAID', 'Excel采购订单合同;源行:42,43,60,61;采购人:Eric;结算:当月结25号前付款;已付款:6000.0000;已入库未付款:3600.0000'),
    (15, 'GL20260921001', '2026-09-21 00:00:00', 1, '深圳中盛昌', NULL, 'PENDING_RECEIPT', 'UNPAID', 'Excel采购订单合同;源行:44;采购人:Eric;结算:当月结25号前付款;已付款:0.0000;已入库未付款:0.0000'),
    (16, 'GL20260922001', '2026-09-22 00:00:00', 1, '深圳中盛昌', NULL, 'COMPLETED', 'UNPAID', 'Excel采购订单合同;源行:45;采购人:Eric;结算:当月结25号前付款;已付款:0.0000;已入库未付款:2250.0000'),
    (17, 'GL20261008001', '2026-10-08 00:00:00', 1, '深圳中盛昌', NULL, 'PARTIALLY_RECEIVED', 'UNPAID', 'Excel采购订单合同;源行:51,52,53;采购人:Eric;结算:当月结25号前付款;已付款:0.0000;已入库未付款:4650.0000');
  -- purchase_order_item: 26 confirmed rows
  INSERT INTO `purchase_order_item` (`id`, `purchase_order_id`, `purchase_order_no`, `product_id`, `brand`, `part_no`, `package_type`, `unit_price`, `tax_included`, `tax_rate`, `quantity`, `amount`) VALUES
    (1, 1, 'GL20260410001', 1, '力芯微', 'ET7303Y', 'QFN14', 0.7000, 1, NULL, 264000, 184800.0000),
    (2, 2, 'GL20260515001', 2, '力芯微', 'ET8211', 'SOP16', 0.2800, 1, NULL, 200000, 56000.0000),
    (3, 3, 'GL20260720001', 3, '力芯微', 'ET5A033YB', 'DFN1*1', 0.3000, 1, NULL, 6000, 1800.0000),
    (4, 3, 'GL20260720001', 4, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.3200, 1, NULL, 9000, 2880.0000),
    (5, 4, 'GL20260720002', 5, '芯洲', 'SCT9330STER', 'ESOP-8L', 2.0000, 1, NULL, 100, 200.0000),
    (6, 5, 'GL20260722001', 4, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.3200, 1, NULL, 60000, 19200.0000),
    (7, 6, 'GL20260721001', 6, '力芯微', 'ET5907', 'WLCSP20(0.35 Pitch)', 0.8500, 1, NULL, 20000, 17000.0000),
    (8, 6, 'GL20260721001', 7, '力芯微', 'ET2104', 'WLCSP12(0.5 Pitch)/ QFN12/TSSOP14', 0.7000, 1, NULL, 9000, 6300.0000),
    (9, 6, 'GL20260721001', 3, '力芯微', 'ET5A033YB', 'DFN1*1', 0.3000, 1, NULL, 20000, 6000.0000),
    (10, 7, 'GL20260731001', 4, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.3200, 1, NULL, 60000, 19200.0000),
    (11, 8, 'GL20260804001', 5, '芯洲', 'SCT9330STER', 'ESOP-8L', 2.0000, 1, NULL, 750, 1500.0000),
    (12, 9, 'GL20260810001', 4, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.3200, 1, NULL, 120000, 38400.0000),
    (13, 10, 'GL20260818001', 4, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.3200, 1, NULL, 180000, 57600.0000),
    (14, 11, 'GL20260818002', 6, '力芯微', 'ET5907', 'WLCSP20(0.35 Pitch)', 0.8500, 1, NULL, 22000, 18700.0000),
    (15, 11, 'GL20260818002', 7, '力芯微', 'ET2104', 'WLCSP12(0.5 Pitch)/ QFN12/TSSOP14', 0.7000, 1, NULL, 9000, 6300.0000),
    (16, 11, 'GL20260818002', 3, '力芯微', 'ET5A033YB', 'DFN1*1', 0.3000, 1, NULL, 22000, 6600.0000),
    (17, 12, 'GL20260903001', 8, '力芯微', 'ET52318YB', 'DFN1*1', 0.0750, 1, NULL, 10000, 750.0000),
    (18, 13, 'GL20260907001', 8, '力芯微', 'ET52318YB', 'DFN1*1', 0.0750, 1, NULL, 20000, 1500.0000),
    (19, 13, 'GL20260907001', 4, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.3200, 1, NULL, 180000, 57600.0000),
    (20, 14, 'GL20260910001', 3, '力芯微', 'ET5A033YB', 'DFN1*1', 0.3000, 1, NULL, 18000, 5400.0000),
    (21, 14, 'GL20260910001', 7, '力芯微', 'ET2104', 'WLCSP12(0.5 Pitch)/ QFN12/TSSOP14', 0.7000, 1, NULL, 6000, 4200.0000),
    (22, 15, 'GL20260921001', 4, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.3200, 1, NULL, 180000, 57600.0000),
    (23, 16, 'GL20260922001', 8, '力芯微', 'ET52318YB', 'DFN1*1', 0.0750, 1, NULL, 30000, 2250.0000),
    (24, 17, 'GL20261008001', 3, '力芯微', 'ET5A033YB', 'DFN1*1', 0.3000, 1, NULL, 12000, 3600.0000),
    (25, 17, 'GL20261008001', 7, '力芯微', 'ET2104', 'WLCSP12(0.5 Pitch)/ QFN12/TSSOP14', 0.7000, 1, NULL, 3000, 2100.0000),
    (26, 17, 'GL20261008001', 6, '力芯微', 'ET5907', 'WLCSP20(0.35 Pitch)', 0.8500, 1, NULL, 12000, 10200.0000);
  -- inbound_order: 28 confirmed receipts; product 6 on Oct 10 is one package.
  INSERT INTO `inbound_order` (`id`, `inbound_order_no`, `supplier_id`, `supplier_name`, `inbound_type`, `status`, `inbound_time`) VALUES
    (1, 'IN20260526001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-05-26 00:00:00'),
    (2, 'IN20260615001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-06-15 00:00:00'),
    (3, 'IN20260819001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-08-19 00:00:00'),
    (4, 'IN20260624001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-06-24 00:00:00'),
    (5, 'IN20260704001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-07-04 00:00:00'),
    (6, 'IN20260723001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-07-23 00:00:00'),
    (7, 'IN20260720001', 2, '深圳联思微', 'PURCHASE', 'COMPLETED', '2026-07-20 00:00:00'),
    (8, 'IN20260727001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-07-27 00:00:00'),
    (9, 'IN20260803001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-08-03 00:00:00'),
    (10, 'IN20260820001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-08-20 00:00:00'),
    (11, 'IN20260822001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-08-22 00:00:00'),
    (12, 'IN20260907001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-09-07 00:00:00'),
    (13, 'IN20260912001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-09-12 00:00:00'),
    (14, 'IN20261010001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-10-10 00:00:00'),
    (15, 'IN20260803002', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-08-03 00:00:00'),
    (16, 'IN20260807001', 2, '深圳联思微', 'PURCHASE', 'COMPLETED', '2026-08-07 00:00:00'),
    (17, 'IN20260813001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-08-13 00:00:00'),
    (18, 'IN20260909001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-09-09 00:00:00'),
    (19, 'IN20260902001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-09-02 00:00:00'),
    (20, 'IN20260907002', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-09-07 00:00:00'),
    (21, 'IN20260930001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-09-30 00:00:00'),
    (23, 'IN20260907003', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-09-07 00:00:00'),
    (24, 'IN20260909002', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-09-09 00:00:00'),
    (25, 'IN20261010002', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-10-10 00:00:00'),
    (26, 'IN20260912002', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-09-12 00:00:00'),
    (27, 'IN20261010003', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-10-10 00:00:00'),
    (28, 'IN20261010004', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-10-10 00:00:00'),
    (29, 'IN20261010005', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-10-10 00:00:00');
  -- inbound_order_item: 36 confirmed rows
  INSERT INTO `inbound_order_item` (`id`, `inbound_order_id`, `purchase_order_id`, `purchase_order_no`, `purchase_order_item_id`, `inbound_quantity`) VALUES
    (1, 1, 1, 'GL20260410001', 1, 30000),
    (2, 2, 1, 'GL20260410001', 1, 3000),
    (3, 3, 1, 'GL20260410001', 1, 30000),
    (4, 4, 2, 'GL20260515001', 2, 16000),
    (5, 5, 2, 'GL20260515001', 2, 8000),
    (6, 6, 3, 'GL20260720001', 3, 6000),
    (7, 6, 3, 'GL20260720001', 4, 9000),
    (8, 7, 4, 'GL20260720002', 5, 100),
    (9, 8, 5, 'GL20260722001', 6, 60000),
    (10, 9, 6, 'GL20260721001', 7, 9000),
    (11, 10, 6, 'GL20260721001', 8, 3000),
    (12, 10, 6, 'GL20260721001', 9, 6000),
    (13, 11, 6, 'GL20260721001', 8, 6000),
    (14, 11, 6, 'GL20260721001', 9, 9000),
    (15, 12, 6, 'GL20260721001', 9, 5000),
    (16, 13, 6, 'GL20260721001', 7, 6000),
    (17, 14, 6, 'GL20260721001', 7, 5000),
    (18, 15, 7, 'GL20260731001', 10, 60000),
    (19, 16, 8, 'GL20260804001', 11, 750),
    (20, 17, 9, 'GL20260810001', 12, 120000),
    (21, 18, 10, 'GL20260818001', 13, 180000),
    (22, 19, 11, 'GL20260818002', 14, 9000),
    (23, 19, 11, 'GL20260818002', 15, 3000),
    (24, 19, 11, 'GL20260818002', 16, 18000),
    (25, 20, 11, 'GL20260818002', 16, 4000),
    (26, 21, 11, 'GL20260818002', 14, 6000),
    (27, 14, 11, 'GL20260818002', 14, 1000),
    (28, 23, 12, 'GL20260903001', 17, 10000),
    (29, 24, 13, 'GL20260907001', 18, 20000),
    (30, 25, 13, 'GL20260907001', 19, 180000),
    (31, 26, 14, 'GL20260910001', 20, 6000),
    (32, 26, 14, 'GL20260910001', 21, 6000),
    (33, 27, 14, 'GL20260910001', 20, 12000),
    (34, 28, 16, 'GL20260922001', 23, 30000),
    (35, 29, 17, 'GL20261008001', 25, 3000),
    (36, 14, 17, 'GL20261008001', 26, 3000);
  -- Purchase associations are derived from allocation details, never from receipt dates.
  INSERT INTO `inbound_order_purchase_order` (`inbound_order_id`, `purchase_order_id`, `purchase_order_no`)
  SELECT DISTINCT `inbound_order_id`, `purchase_order_id`, `purchase_order_no`
  FROM `inbound_order_item` ORDER BY `inbound_order_id`, `purchase_order_id`;
  -- inbound_order_batch_item: 36 confirmed rows
  INSERT INTO `inbound_order_batch_item` (`id`, `inbound_order_id`, `inbound_order_item_id`, `purchase_order_id`, `purchase_order_no`, `purchase_order_item_id`, `product_id`, `date_code`, `lot_code`, `inbound_quantity`) VALUES
    (1, 1, 1, 1, 'GL20260410001', 1, 1, '', '', 30000),
    (2, 2, 2, 1, 'GL20260410001', 1, 1, '', '', 3000),
    (3, 3, 3, 1, 'GL20260410001', 1, 1, '', '', 30000),
    (4, 4, 4, 2, 'GL20260515001', 2, 2, '', '', 16000),
    (5, 5, 5, 2, 'GL20260515001', 2, 2, '', '', 8000),
    (6, 6, 6, 3, 'GL20260720001', 3, 3, '', '', 6000),
    (7, 6, 7, 3, 'GL20260720001', 4, 4, '', '', 9000),
    (8, 7, 8, 4, 'GL20260720002', 5, 5, '', '', 100),
    (9, 8, 9, 5, 'GL20260722001', 6, 4, '', '', 60000),
    (10, 9, 10, 6, 'GL20260721001', 7, 6, '', '', 9000),
    (11, 10, 11, 6, 'GL20260721001', 8, 7, '', '', 3000),
    (12, 10, 12, 6, 'GL20260721001', 9, 3, '', '', 6000),
    (13, 11, 13, 6, 'GL20260721001', 8, 7, '', '', 6000),
    (14, 11, 14, 6, 'GL20260721001', 9, 3, '', '', 9000),
    (15, 12, 15, 6, 'GL20260721001', 9, 3, '', '', 5000),
    (16, 13, 16, 6, 'GL20260721001', 7, 6, '', '', 6000),
    (17, 14, 17, 6, 'GL20260721001', 7, 6, '', '', 5000),
    (18, 15, 18, 7, 'GL20260731001', 10, 4, '', '', 60000),
    (19, 16, 19, 8, 'GL20260804001', 11, 5, '', '', 750),
    (20, 17, 20, 9, 'GL20260810001', 12, 4, '', '', 120000),
    (21, 18, 21, 10, 'GL20260818001', 13, 4, '', '', 180000),
    (22, 19, 22, 11, 'GL20260818002', 14, 6, '', '', 9000),
    (23, 19, 23, 11, 'GL20260818002', 15, 7, '', '', 3000),
    (24, 19, 24, 11, 'GL20260818002', 16, 3, '', '', 18000),
    (25, 20, 25, 11, 'GL20260818002', 16, 3, '', '', 4000),
    (26, 21, 26, 11, 'GL20260818002', 14, 6, '', '', 6000),
    (27, 14, 27, 11, 'GL20260818002', 14, 6, '', '', 1000),
    (28, 23, 28, 12, 'GL20260903001', 17, 8, '', '', 10000),
    (29, 24, 29, 13, 'GL20260907001', 18, 8, '', '', 20000),
    (30, 25, 30, 13, 'GL20260907001', 19, 4, '', '', 180000),
    (31, 26, 31, 14, 'GL20260910001', 20, 3, '', '', 6000),
    (32, 26, 32, 14, 'GL20260910001', 21, 7, '', '', 6000),
    (33, 27, 33, 14, 'GL20260910001', 20, 3, '', '', 12000),
    (34, 28, 34, 16, 'GL20260922001', 23, 8, '', '', 30000),
    (35, 29, 35, 17, 'GL20261008001', 25, 7, '', '', 3000),
    (36, 14, 36, 17, 'GL20261008001', 26, 6, '', '', 3000);
  -- customer: 6 confirmed rows
  INSERT INTO `customer` (`id`, `name`, `short_name`, `settle_type`, `category`) VALUES
    (1, '深圳同益芯', NULL, 'TT/先款后货', NULL),
    (2, '深圳市中诺泽科技有限公司', '深圳中诺泽', 'TT/先款后货', NULL),
    (3, '深圳市成胜微电子有限公司', '深圳成胜微', 'TT/先款后货', NULL),
    (4, '广州番禺巨大汽车音响设备有限公司', '广州巨大汽车音响', NULL, NULL),
    (5, '深圳市铭泰电子信息科技有限公司', '深圳市铭泰电子', '次月结60', NULL),
    (6, '深圳市勤泰智能信息技术有限公司', '深圳市勤泰信息', '次月结60', NULL);
  -- inventory: 36 confirmed rows
  INSERT INTO `inventory` (`id`, `product_id`, `date_code`, `lot_code`, `batch_no`, `inbound_order_item_id`, `inbound_order_batch_item_id`, `unit_cost`, `initial_quantity`, `quantity`, `reserved_quantity`, `created_at`) VALUES
    (1, 1, '', '', 'BAT26052600001001', 1, 1, 0.7000, 30000, 0, 0, '2026-05-26 00:00:00'),
    (2, 1, '', '', 'BAT26061500001001', 2, 2, 0.7000, 3000, 0, 0, '2026-06-15 00:00:00'),
    (3, 1, '', '', 'BAT26081900001001', 3, 3, 0.7000, 30000, 0, 0, '2026-08-19 00:00:00'),
    (4, 2, '', '', 'BAT26062400002001', 4, 4, 0.2800, 16000, 0, 0, '2026-06-24 00:00:00'),
    (5, 2, '', '', 'BAT26070400002001', 5, 5, 0.2800, 8000, 0, 0, '2026-07-04 00:00:00'),
    (6, 3, '', '', 'BAT26072300003001', 6, 6, 0.3000, 6000, 0, 0, '2026-07-23 00:00:00'),
    (7, 4, '', '', 'BAT26072300004001', 7, 7, 0.3200, 9000, 0, 0, '2026-07-23 00:00:00'),
    (8, 5, '', '', 'BAT26072000005001', 8, 8, 2.0000, 100, 0, 0, '2026-07-20 00:00:00'),
    (9, 4, '', '', 'BAT26072700004001', 9, 9, 0.3200, 60000, 0, 0, '2026-07-27 00:00:00'),
    (10, 6, '', '', 'BAT26080300006001', 10, 10, 0.8500, 9000, 0, 0, '2026-08-03 00:00:00'),
    (11, 7, '', '', 'BAT26082000007001', 11, 11, 0.7000, 3000, 0, 0, '2026-08-20 00:00:00'),
    (12, 3, '', '', 'BAT26082000003001', 12, 12, 0.3000, 6000, 0, 0, '2026-08-20 00:00:00'),
    (13, 7, '', '', 'BAT26082200007001', 13, 13, 0.7000, 6000, 0, 0, '2026-08-22 00:00:00'),
    (14, 3, '', '', 'BAT26082200003001', 14, 14, 0.3000, 9000, 0, 0, '2026-08-22 00:00:00'),
    (15, 3, '', '', 'BAT26090700003001', 15, 15, 0.3000, 5000, 0, 0, '2026-09-07 00:00:00'),
    (16, 6, '', '', 'BAT26091200006001', 16, 16, 0.8500, 6000, 0, 0, '2026-09-12 00:00:00'),
    (17, 6, '', '', 'BAT26101000006001', 17, 17, 0.8500, 5000, 5000, 0, '2026-10-10 00:00:00'),
    (18, 4, '', '', 'BAT26080300004001', 18, 18, 0.3200, 60000, 0, 0, '2026-08-03 00:00:00'),
    (19, 5, '', '', 'BAT26080700005001', 19, 19, 2.0000, 750, 0, 0, '2026-08-07 00:00:00'),
    (20, 4, '', '', 'BAT26081300004001', 20, 20, 0.3200, 120000, 0, 0, '2026-08-13 00:00:00'),
    (21, 4, '', '', 'BAT26090900004001', 21, 21, 0.3200, 180000, 0, 0, '2026-09-09 00:00:00'),
    (22, 6, '', '', 'BAT26090200006001', 22, 22, 0.8500, 9000, 0, 0, '2026-09-02 00:00:00'),
    (23, 7, '', '', 'BAT26090200007001', 23, 23, 0.7000, 3000, 0, 0, '2026-09-02 00:00:00'),
    (24, 3, '', '', 'BAT26090200003001', 24, 24, 0.3000, 18000, 0, 0, '2026-09-02 00:00:00'),
    (25, 3, '', '', 'BAT26090700003002', 25, 25, 0.3000, 4000, 0, 0, '2026-09-07 00:00:00'),
    (26, 6, '', '', 'BAT26093000006001', 26, 26, 0.8500, 6000, 0, 0, '2026-09-30 00:00:00'),
    (27, 6, '', '', 'BAT26101000006002', 27, 27, 0.8500, 1000, 1000, 0, '2026-10-10 00:00:00'),
    (28, 8, '', '', 'BAT26090700008001', 28, 28, 0.0750, 10000, 0, 0, '2026-09-07 00:00:00'),
    (29, 8, '', '', 'BAT26090900008001', 29, 29, 0.0750, 20000, 0, 0, '2026-09-09 00:00:00'),
    (30, 4, '', '', 'BAT26101000004001', 30, 30, 0.3200, 180000, 0, 0, '2026-10-10 00:00:00'),
    (31, 3, '', '', 'BAT26091200003001', 31, 31, 0.3000, 6000, 0, 0, '2026-09-12 00:00:00'),
    (32, 7, '', '', 'BAT26091200007001', 32, 32, 0.7000, 6000, 0, 0, '2026-09-12 00:00:00'),
    (33, 3, '', '', 'BAT26101000003001', 33, 33, 0.3000, 12000, 9000, 0, '2026-10-10 00:00:00'),
    (34, 8, '', '', 'BAT26101000008001', 34, 34, 0.0750, 30000, 0, 0, '2026-10-10 00:00:00'),
    (35, 7, '', '', 'BAT26101000007001', 35, 35, 0.7000, 3000, 3000, 0, '2026-10-10 00:00:00'),
    (36, 6, '', '', 'BAT26101000006003', 36, 36, 0.8500, 3000, 3000, 0, '2026-10-10 00:00:00');
  -- sales_order: 17 confirmed rows
  INSERT INTO `sales_order` (`id`, `order_no`, `customer_order_no`, `order_time`, `customer_id`, `customer_name`, `settle_type`, `delivery_time`, `order_status`, `payment_status`, `remark`) VALUES
    (1, 'SO20260407001', 'TYX20260407015', '2026-04-07 00:00:00', 1, '深圳同益芯', 'PREPAID', NULL, 'PARTIALLY_SHIPPED', 'PARTIALLY_PAID', '账期:TT/先款后货;已出库:30000;取消:0;有效金额:224400.0000;已收:25500.0000'),
    (2, 'SO20260517001', 'ZNZPO-2605-1701', '2026-05-17 00:00:00', 2, '深圳市中诺泽科技有限公司', 'PREPAID', NULL, 'CANCELLED', 'PAID', '账期:TT/先款后货;取消订单;已出库:24000;取消:176000;有效金额:7680.0000;已收:7680.0000'),
    (3, 'SO20260612001', '无', '2026-06-12 00:00:00', 3, '深圳市成胜微电子有限公司', 'PREPAID', NULL, 'COMPLETED', 'PAID', '账期:TT/先款后货;陈的代理商;已出库:3000;取消:0;有效金额:2250.0000;已收:2250.0000'),
    (4, 'SO20260716001', '3508-260716001', '2026-07-16 00:00:00', 4, '广州番禺巨大汽车音响设备有限公司', 'COD', NULL, 'COMPLETED', 'PAID', '账期:货到一周付款;已出库:100;取消:0;有效金额:250.0000;已收:250.0000'),
    (5, 'SO20260717001', 'MT2026/071700', '2026-07-17 00:00:00', 5, '深圳市铭泰电子信息科技有限公司', 'MONTHLY', NULL, 'COMPLETED', 'UNPAID', '账期:次月结60;已出库:9000;取消:0;有效金额:3150.0000;已收:0.0000'),
    (6, 'SO20260715001', 'QT202607150003', '2026-07-15 00:00:00', 6, '深圳市勤泰智能信息技术有限公司', 'MONTHLY', NULL, 'PARTIALLY_SHIPPED', 'UNPAID', '账期:次月结60;已出库:96000;取消:0;有效金额:391050.0000;已收:0.0000'),
    (7, 'SO20260721001', 'MT2026/072100', '2026-07-21 00:00:00', 5, '深圳市铭泰电子信息科技有限公司', 'MONTHLY', NULL, 'COMPLETED', 'UNPAID', '账期:次月结60;交付完成;已出库:60000;取消:0;有效金额:21000.0000;已收:0.0000'),
    (8, 'SO20260730001', 'MT2026/073000', '2026-07-30 00:00:00', 5, '深圳市铭泰电子信息科技有限公司', 'MONTHLY', NULL, 'COMPLETED', 'UNPAID', '账期:次月结60;交付完成;已出库:60000;取消:0;有效金额:21000.0000;已收:0.0000'),
    (9, 'SO20260804001', '3508-260803064', '2026-08-04 00:00:00', 4, '广州番禺巨大汽车音响设备有限公司', 'COD', NULL, 'COMPLETED', 'PAID', '账期:货到付款;已出库:750;取消:0;有效金额:1875.0000;已收:1875.0000'),
    (10, 'SO20260808001', 'MT2026/080800', '2026-08-08 00:00:00', 5, '深圳市铭泰电子信息科技有限公司', 'MONTHLY', NULL, 'COMPLETED', 'UNPAID', '账期:次月结60;已出库:120000;取消:0;有效金额:42000.0000;已收:0.0000'),
    (11, 'SO20260817001', 'MT2026/081700', '2026-08-17 00:00:00', 5, '深圳市铭泰电子信息科技有限公司', 'MONTHLY', '2026-10-10 00:00:00', 'COMPLETED', 'UNPAID', '账期:次月结60;ET5A2ADJZB交期:10月10号;已出库:180000;取消:0;有效金额:63000.0000;已收:0.0000'),
    (12, 'SO20260818001', '无', '2026-08-18 00:00:00', 3, '深圳市成胜微电子有限公司', 'PREPAID', NULL, 'COMPLETED', 'UNPAID', '账期:TT/先款后货;陈的代理商;已出库:30000;取消:0;有效金额:22500.0000;已收:0.0000'),
    (13, 'SO20260820001', 'MT2026/082000', '2026-08-20 00:00:00', 5, '深圳市铭泰电子信息科技有限公司', 'MONTHLY', NULL, 'COMPLETED', 'UNPAID', '账期:次月结60;已出库:3000;取消:0;有效金额:1050.0000;已收:0.0000'),
    (14, 'SO20260903001', 'MT2026/090300', '2026-09-03 00:00:00', 5, '深圳市铭泰电子信息科技有限公司', 'MONTHLY', NULL, 'COMPLETED', 'UNPAID', '账期:次月结60;已出库:10000;取消:0;有效金额:1000.0000;已收:0.0000'),
    (15, 'SO20260907001', 'MT2026/090700', '2026-09-07 00:00:00', 5, '深圳市铭泰电子信息科技有限公司', 'MONTHLY', '2026-09-09 00:00:00', 'COMPLETED', 'UNPAID', '账期:次月结60;ET5A2ADJZB交期:9月9号;已出库:206000;取消:0;有效金额:67100.0000;已收:0.0000'),
    (16, 'SO20260920001', 'MT2026/092000', '2026-09-20 00:00:00', 5, '深圳市铭泰电子信息科技有限公司', 'MONTHLY', NULL, 'PENDING_SHIPMENT', 'UNPAID', '账期:次月结60;已出库:0;取消:0;有效金额:63000.0000;已收:0.0000'),
    (17, 'SO20260921001', 'MT2026/092100', '2026-09-21 00:00:00', 5, '深圳市铭泰电子信息科技有限公司', 'MONTHLY', '2026-09-28 00:00:00', 'COMPLETED', 'UNPAID', '账期:次月结60;ET52318YB交期:9月28;已出库:30000;取消:0;有效金额:3000.0000;已收:0.0000');
  -- sales_order_item: 21 confirmed rows
  INSERT INTO `sales_order_item` (`id`, `sales_order_id`, `customer_order_no`, `product_id`, `brand`, `part_no`, `package_type`, `unit_price`, `tax_included`, `tax_rate`, `quantity`, `cancelled_quantity`, `amount`) VALUES
    (1, 1, 'TYX20260407015', 1, '力芯微', 'ET7303Y', 'QFN14', 0.8500, 1, NULL, 264000, 0, 224400.0000),
    (2, 2, 'ZNZPO-2605-1701', 2, '力芯微', 'ET8211', 'SOP16', 0.3200, 1, NULL, 200000, 176000, 64000.0000),
    (3, 3, '无', 1, '力芯微', 'ET7303Y', 'QFN14', 0.7500, 1, NULL, 3000, 0, 2250.0000),
    (4, 4, '3508-260716001', 5, '芯洲', 'SCT9330STER', 'ESOP-8L', 2.5000, 1, NULL, 100, 0, 250.0000),
    (5, 5, 'MT2026/071700', 4, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.3500, 0, NULL, 9000, 0, 3150.0000),
    (6, 6, 'QT202607150003', 6, '力芯微', 'ET5907', 'WLCSP20(0.35 Pitch)', 1.0000, 1, NULL, 282000, 0, 282000.0000),
    (7, 6, 'QT202607150003', 3, '力芯微', 'ET5A033YB', 'DFN1*1', 0.3500, 1, NULL, 219000, 0, 76650.0000),
    (8, 6, 'QT202607150003', 7, '力芯微', 'ET2104', 'WLCSP12(0.5 Pitch)/ QFN12/TSSOP14', 0.9000, 1, NULL, 36000, 0, 32400.0000),
    (9, 7, 'MT2026/072100', 4, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.3500, 0, NULL, 60000, 0, 21000.0000),
    (10, 8, 'MT2026/073000', 4, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.3500, 0, NULL, 60000, 0, 21000.0000),
    (11, 9, '3508-260803064', 5, '芯洲', 'SCT9330STER', 'ESOP-8L', 2.5000, 1, NULL, 750, 0, 1875.0000),
    (12, 10, 'MT2026/080800', 4, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.3500, 0, NULL, 120000, 0, 42000.0000),
    (13, 11, 'MT2026/081700', 4, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.3500, 0, NULL, 180000, 0, 63000.0000),
    (14, 12, '无', 1, '力芯微', 'ET7303Y', 'QFN14', 0.7500, 1, NULL, 30000, 0, 22500.0000),
    (15, 13, 'MT2026/082000', 3, '力芯微', 'ET5A033YB', 'DFN1*1', 0.3500, 0, NULL, 3000, 0, 1050.0000),
    (16, 14, 'MT2026/090300', 8, '力芯微', 'ET52318YB', 'DFN1*1', 0.1000, 0, NULL, 10000, 0, 1000.0000),
    (17, 15, 'MT2026/090700', 8, '力芯微', 'ET52318YB', 'DFN1*1', 0.1000, 0, NULL, 20000, 0, 2000.0000),
    (18, 15, 'MT2026/090700', 3, '力芯微', 'ET5A033YB', 'DFN1*1', 0.3500, 0, NULL, 6000, 0, 2100.0000),
    (19, 15, 'MT2026/090700', 4, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.3500, 0, NULL, 180000, 0, 63000.0000),
    (20, 16, 'MT2026/092000', 4, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.3500, 0, NULL, 180000, 0, 63000.0000),
    (21, 17, 'MT2026/092100', 8, '力芯微', 'ET52318YB', 'DFN1*1', 0.1000, 0, NULL, 30000, 0, 3000.0000);
  -- sales_order_batch_item: 0 rows (orders do not reserve stock).
  -- outbound_order: 25 confirmed rows
  INSERT INTO `outbound_order` (`id`, `outbound_order_no`, `sales_order_id`, `customer_order_no`, `customer_id`, `customer_name`, `outbound_type`, `status`, `outbound_time`) VALUES
    (1, 'OUT20260527001', 1, 'TYX20260407015', 1, '深圳同益芯', 'SALE', 'COMPLETED', '2026-05-27 00:00:00'),
    (2, 'OUT20260615001', 3, '无', 3, '深圳市成胜微电子有限公司', 'SALE', 'COMPLETED', '2026-06-15 00:00:00'),
    (3, 'OUT20260624001', 2, 'ZNZPO-2605-1701', 2, '深圳市中诺泽科技有限公司', 'SALE', 'COMPLETED', '2026-06-24 00:00:00'),
    (4, 'OUT20260704001', 2, 'ZNZPO-2605-1701', 2, '深圳市中诺泽科技有限公司', 'SALE', 'COMPLETED', '2026-07-04 00:00:00'),
    (5, 'OUT20260720001', 4, '3508-260716001', 4, '广州番禺巨大汽车音响设备有限公司', 'SALE', 'COMPLETED', '2026-07-20 00:00:00'),
    (6, 'OUT20260723001', 5, 'MT2026/071700', 5, '深圳市铭泰电子信息科技有限公司', 'SALE', 'COMPLETED', '2026-07-23 00:00:00'),
    (7, 'OUT20260727001', 7, 'MT2026/072100', 5, '深圳市铭泰电子信息科技有限公司', 'SALE', 'COMPLETED', '2026-07-27 00:00:00'),
    (8, 'OUT20260803001', 8, 'MT2026/073000', 5, '深圳市铭泰电子信息科技有限公司', 'SALE', 'COMPLETED', '2026-08-03 00:00:00'),
    (9, 'OUT20260803002', 6, 'QT202607150003', 6, '深圳市勤泰智能信息技术有限公司', 'SALE', 'COMPLETED', '2026-08-03 00:00:00'),
    (10, 'OUT20260804001', 6, 'QT202607150003', 6, '深圳市勤泰智能信息技术有限公司', 'SALE', 'COMPLETED', '2026-08-04 00:00:00'),
    (11, 'OUT20260807001', 9, '3508-260803064', 4, '广州番禺巨大汽车音响设备有限公司', 'SALE', 'COMPLETED', '2026-08-07 00:00:00'),
    (12, 'OUT20260813001', 10, 'MT2026/080800', 5, '深圳市铭泰电子信息科技有限公司', 'SALE', 'COMPLETED', '2026-08-13 00:00:00'),
    (13, 'OUT20260819001', 12, '无', 3, '深圳市成胜微电子有限公司', 'SALE', 'COMPLETED', '2026-08-19 00:00:00'),
    (14, 'OUT20260822001', 6, 'QT202607150003', 6, '深圳市勤泰智能信息技术有限公司', 'SALE', 'COMPLETED', '2026-08-22 00:00:00'),
    (15, 'OUT20260822002', 13, 'MT2026/082000', 5, '深圳市铭泰电子信息科技有限公司', 'SALE', 'COMPLETED', '2026-08-22 00:00:00'),
    (16, 'OUT20260902001', 6, 'QT202607150003', 6, '深圳市勤泰智能信息技术有限公司', 'SALE', 'COMPLETED', '2026-09-02 00:00:00'),
    (17, 'OUT20260907001', 14, 'MT2026/090300', 5, '深圳市铭泰电子信息科技有限公司', 'SALE', 'COMPLETED', '2026-09-07 00:00:00'),
    (18, 'OUT20260909001', 6, 'QT202607150003', 6, '深圳市勤泰智能信息技术有限公司', 'SALE', 'COMPLETED', '2026-09-09 00:00:00'),
    (19, 'OUT20260910001', 15, 'MT2026/090700', 5, '深圳市铭泰电子信息科技有限公司', 'SALE', 'COMPLETED', '2026-09-10 00:00:00'),
    (20, 'OUT20260921001', 6, 'QT202607150003', 6, '深圳市勤泰智能信息技术有限公司', 'SALE', 'COMPLETED', '2026-09-21 00:00:00'),
    (21, 'OUT20260930001', 6, 'QT202607150003', 6, '深圳市勤泰智能信息技术有限公司', 'SALE', 'COMPLETED', '2026-09-30 00:00:00'),
    (22, 'OUT20261010001', 11, 'MT2026/081700', 5, '深圳市铭泰电子信息科技有限公司', 'SALE', 'COMPLETED', '2026-10-10 00:00:00'),
    (23, 'OUT20261010002', 17, 'MT2026/092100', 5, '深圳市铭泰电子信息科技有限公司', 'SALE', 'COMPLETED', '2026-10-10 00:00:00'),
    (24, 'OUT20261013001', 6, 'QT202607150003', 6, '深圳市勤泰智能信息技术有限公司', 'SALE', 'COMPLETED', '2026-10-13 00:00:00'),
    (25, 'OUT20261014001', 6, 'QT202607150003', 6, '深圳市勤泰智能信息技术有限公司', 'SALE', 'COMPLETED', '2026-10-14 00:00:00');
  -- outbound_order_item: 33 confirmed rows
  INSERT INTO `outbound_order_item` (`id`, `outbound_order_id`, `sales_order_id`, `customer_order_no`, `sales_order_item_id`, `outbound_quantity`) VALUES
    (1, 1, 1, 'TYX20260407015', 1, 30000),
    (2, 2, 3, '无', 3, 3000),
    (3, 3, 2, 'ZNZPO-2605-1701', 2, 16000),
    (4, 4, 2, 'ZNZPO-2605-1701', 2, 8000),
    (5, 5, 4, '3508-260716001', 4, 100),
    (6, 6, 5, 'MT2026/071700', 5, 9000),
    (7, 7, 7, 'MT2026/072100', 9, 60000),
    (8, 8, 8, 'MT2026/073000', 10, 60000),
    (9, 9, 6, 'QT202607150003', 6, 6000),
    (10, 9, 6, 'QT202607150003', 7, 6000),
    (11, 10, 6, 'QT202607150003', 6, 3000),
    (12, 11, 9, '3508-260803064', 11, 750),
    (13, 12, 10, 'MT2026/080800', 12, 120000),
    (14, 13, 12, '无', 14, 30000),
    (15, 14, 6, 'QT202607150003', 7, 9000),
    (16, 14, 6, 'QT202607150003', 8, 6000),
    (17, 15, 13, 'MT2026/082000', 15, 3000),
    (18, 16, 6, 'QT202607150003', 7, 12000),
    (19, 16, 6, 'QT202607150003', 6, 3000),
    (20, 17, 14, 'MT2026/090300', 16, 10000),
    (21, 18, 6, 'QT202607150003', 6, 6000),
    (22, 18, 6, 'QT202607150003', 7, 3000),
    (23, 18, 6, 'QT202607150003', 8, 6000),
    (24, 19, 15, 'MT2026/090700', 19, 180000),
    (25, 19, 15, 'MT2026/090700', 18, 6000),
    (26, 19, 15, 'MT2026/090700', 17, 20000),
    (27, 20, 6, 'QT202607150003', 7, 6000),
    (28, 21, 6, 'QT202607150003', 6, 12000),
    (29, 21, 6, 'QT202607150003', 7, 6000),
    (30, 22, 11, 'MT2026/081700', 13, 180000),
    (31, 23, 17, 'MT2026/092100', 21, 30000),
    (32, 24, 6, 'QT202607150003', 7, 6000),
    (33, 25, 6, 'QT202607150003', 8, 6000);
  -- outbound_order_batch_item: 41 confirmed rows
  INSERT INTO `outbound_order_batch_item` (`id`, `outbound_order_item_id`, `inventory_id`, `quantity`, `unit_cost`) VALUES
    (1, 1, 1, 30000, 0.7000),
    (2, 2, 2, 3000, 0.7000),
    (3, 3, 4, 16000, 0.2800),
    (4, 4, 5, 8000, 0.2800),
    (5, 5, 8, 100, 2.0000),
    (6, 6, 7, 9000, 0.3200),
    (7, 7, 9, 60000, 0.3200),
    (8, 8, 18, 60000, 0.3200),
    (9, 9, 10, 6000, 0.8500),
    (10, 10, 6, 6000, 0.3000),
    (11, 11, 10, 3000, 0.8500),
    (12, 12, 19, 750, 2.0000),
    (13, 13, 20, 120000, 0.3200),
    (14, 14, 3, 30000, 0.7000),
    (15, 15, 12, 6000, 0.3000),
    (16, 15, 14, 3000, 0.3000),
    (17, 16, 11, 3000, 0.7000),
    (18, 16, 13, 3000, 0.7000),
    (19, 17, 14, 3000, 0.3000),
    (20, 18, 14, 3000, 0.3000),
    (21, 18, 24, 9000, 0.3000),
    (22, 19, 22, 3000, 0.8500),
    (23, 20, 28, 10000, 0.0750),
    (24, 21, 22, 6000, 0.8500),
    (25, 22, 24, 3000, 0.3000),
    (26, 23, 13, 3000, 0.7000),
    (27, 23, 23, 3000, 0.7000),
    (28, 24, 21, 180000, 0.3200),
    (29, 25, 24, 6000, 0.3000),
    (30, 26, 29, 20000, 0.0750),
    (31, 27, 15, 5000, 0.3000),
    (32, 27, 25, 1000, 0.3000),
    (33, 28, 16, 6000, 0.8500),
    (34, 28, 26, 6000, 0.8500),
    (35, 29, 25, 3000, 0.3000),
    (36, 29, 31, 3000, 0.3000),
    (37, 30, 30, 180000, 0.3200),
    (38, 31, 34, 30000, 0.0750),
    (39, 32, 31, 3000, 0.3000),
    (40, 32, 33, 3000, 0.3000),
    (41, 33, 32, 6000, 0.7000);
  INSERT INTO document_number_sequence (document_type, business_date, last_sequence)
  SELECT 'SO', DATE(order_time), MAX(CAST(RIGHT(order_no,3) AS UNSIGNED)) FROM sales_order GROUP BY DATE(order_time);
  INSERT INTO document_number_sequence (document_type, business_date, last_sequence)
  SELECT 'GL', DATE(order_time), MAX(CAST(RIGHT(purchase_order_no,3) AS UNSIGNED)) FROM purchase_order GROUP BY DATE(order_time);
  INSERT INTO document_number_sequence (document_type, business_date, last_sequence)
  SELECT 'IN', DATE(inbound_time), MAX(CAST(RIGHT(inbound_order_no,3) AS UNSIGNED)) FROM inbound_order GROUP BY DATE(inbound_time);
  INSERT INTO document_number_sequence (document_type, business_date, last_sequence)
  SELECT 'OUT', DATE(outbound_time), MAX(CAST(RIGHT(outbound_order_no,3) AS UNSIGNED)) FROM outbound_order GROUP BY DATE(outbound_time);
  COMMIT;
END$$
DELIMITER ;
CALL `glw_import_full_data`();
DROP PROCEDURE `glw_import_full_data`;
SET SESSION sql_mode = @fd_old_sql_mode;
SET SESSION time_zone = @fd_old_time_zone;
SELECT 'customer' AS table_name, COUNT(*) AS actual_rows, 6 AS expected_rows FROM `customer`;
SELECT 'customer_visit' AS table_name, COUNT(*) AS actual_rows, 0 AS expected_rows FROM `customer_visit`;
SELECT 'inbound_order' AS table_name, COUNT(*) AS actual_rows, 28 AS expected_rows FROM `inbound_order`;
SELECT 'inbound_order_purchase_order' AS table_name, COUNT(*) AS actual_rows, 30 AS expected_rows FROM `inbound_order_purchase_order`;
SELECT 'inbound_order_item' AS table_name, COUNT(*) AS actual_rows, 36 AS expected_rows FROM `inbound_order_item`;
SELECT 'inventory' AS table_name, COUNT(*) AS actual_rows, 36 AS expected_rows FROM `inventory`;
SELECT 'outbound_order' AS table_name, COUNT(*) AS actual_rows, 25 AS expected_rows FROM `outbound_order`;
SELECT 'outbound_order_item' AS table_name, COUNT(*) AS actual_rows, 33 AS expected_rows FROM `outbound_order_item`;
SELECT 'outbound_order_batch_item' AS table_name, COUNT(*) AS actual_rows, 41 AS expected_rows FROM `outbound_order_batch_item`;
SELECT 'product' AS table_name, COUNT(*) AS actual_rows, 8 AS expected_rows FROM `product`;
SELECT 'purchase_order' AS table_name, COUNT(*) AS actual_rows, 17 AS expected_rows FROM `purchase_order`;
SELECT 'purchase_order_item' AS table_name, COUNT(*) AS actual_rows, 26 AS expected_rows FROM `purchase_order_item`;
SELECT 'sales_order' AS table_name, COUNT(*) AS actual_rows, 17 AS expected_rows FROM `sales_order`;
SELECT 'sales_order_item' AS table_name, COUNT(*) AS actual_rows, 21 AS expected_rows FROM `sales_order_item`;
SELECT 'sales_order_batch_item' AS table_name, COUNT(*) AS actual_rows, 0 AS expected_rows FROM `sales_order_batch_item`;
SELECT 'sample_apply' AS table_name, COUNT(*) AS actual_rows, 0 AS expected_rows FROM `sample_apply`;
SELECT 'supplier' AS table_name, COUNT(*) AS actual_rows, 2 AS expected_rows FROM `supplier`;
SELECT 'sys_user' AS table_name, COUNT(*) AS actual_rows, 2 AS expected_rows FROM `sys_user`;
