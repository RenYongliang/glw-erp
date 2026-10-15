-- Source: 表格.xlsx. Read excel_import_notes.md before execution.
-- MySQL 8; select the target database first. Empty business tables only.
-- Requires CREATE ROUTINE permission. No DROP/TRUNCATE of business tables.
SET NAMES utf8mb4;
SET @xl_old_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = 'STRICT_TRANS_TABLES,NO_BACKSLASH_ESCAPES,NO_ENGINE_SUBSTITUTION';
SET @xl_old_time_zone = @@SESSION.time_zone;
SET SESSION time_zone = '+08:00';
DELIMITER $$
CREATE PROCEDURE `glw_import_excel_20261015`()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    SET SESSION sql_mode = @xl_old_sql_mode;
    SET SESSION time_zone = @xl_old_time_zone;
    RESIGNAL;
  END;
  START TRANSACTION;
  IF EXISTS (SELECT 1 FROM `customer` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Import requires empty table: customer';
  END IF;
  IF EXISTS (SELECT 1 FROM `customer_visit` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Import requires empty table: customer_visit';
  END IF;
  IF EXISTS (SELECT 1 FROM `inbound_order` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Import requires empty table: inbound_order';
  END IF;
  IF EXISTS (SELECT 1 FROM `inbound_order_item` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Import requires empty table: inbound_order_item';
  END IF;
  IF EXISTS (SELECT 1 FROM `inventory` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Import requires empty table: inventory';
  END IF;
  IF EXISTS (SELECT 1 FROM `outbound_order` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Import requires empty table: outbound_order';
  END IF;
  IF EXISTS (SELECT 1 FROM `outbound_order_item` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Import requires empty table: outbound_order_item';
  END IF;
  IF EXISTS (SELECT 1 FROM `outbound_order_batch_item` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Import requires empty table: outbound_order_batch_item';
  END IF;
  IF EXISTS (SELECT 1 FROM `product` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Import requires empty table: product';
  END IF;
  IF EXISTS (SELECT 1 FROM `purchase_order` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Import requires empty table: purchase_order';
  END IF;
  IF EXISTS (SELECT 1 FROM `purchase_order_item` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Import requires empty table: purchase_order_item';
  END IF;
  IF EXISTS (SELECT 1 FROM `sales_order` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Import requires empty table: sales_order';
  END IF;
  IF EXISTS (SELECT 1 FROM `sales_order_item` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Import requires empty table: sales_order_item';
  END IF;
  IF EXISTS (SELECT 1 FROM `sales_order_batch_item` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Import requires empty table: sales_order_batch_item';
  END IF;
  IF EXISTS (SELECT 1 FROM `sample_apply` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Import requires empty table: sample_apply';
  END IF;
  IF EXISTS (SELECT 1 FROM `supplier` LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Import requires empty table: supplier';
  END IF;
  -- supplier: 3 rows
  INSERT INTO `supplier` (`id`, `name`, `brand`) VALUES
    (1, '深圳中盛昌', '力芯微'),
    (2, '深圳联思微', '芯洲'),
    (3, '待核实供应商（Excel未提供）', '力芯微');
  -- customer: 20 rows
  INSERT INTO `customer` (`id`, `name`, `short_name`, `contact`, `settle_type`) VALUES
    (1, '深圳同益芯', NULL, NULL, 'PREPAID'),
    (2, '深圳市中诺泽科技有限公司', '深圳中诺泽', NULL, 'PREPAID'),
    (3, '深圳市成胜微电子有限公司', '深圳成胜微', NULL, 'PREPAID'),
    (4, '广州番禺巨大汽车音响设备有限公司', '广州巨大汽车音响', NULL, 'COD'),
    (5, '深圳市铭泰电子信息科技有限公司', '深圳市铭泰电子', 'RD 陈工 黄工
钟小姐采购', 'MONTHLY'),
    (6, '深圳市勤泰智能信息技术有限公司', '深圳市勤泰信息', '黄经理', 'MONTHLY'),
    (7, '盐城鸿石智能', NULL, '采购陈小姐', NULL),
    (8, '迎客松', NULL, '刘工', NULL),
    (9, '小糯米', NULL, 'RD总 张总', NULL),
    (10, '亿道', NULL, NULL, NULL),
    (11, '英卡', NULL, NULL, NULL),
    (12, '叩鼎', NULL, NULL, NULL),
    (13, '智合泰', NULL, NULL, NULL),
    (14, '深圳市阿达视高新技术有限公司', '阿达视', 'RD总 陶工', NULL),
    (15, '东莞市慧思电子有限公司', '莞市慧思电子有限公司', '陈金云采购', NULL),
    (16, '优博讯', NULL, '彭厚满', NULL),
    (17, '隆源盛电子科技', NULL, NULL, NULL),
    (18, '晶华显示电子股份', NULL, NULL, NULL),
    (19, '深圳市裕晟芯电子科技有限公司', NULL, NULL, NULL),
    (20, '江苏乔科科技有限公司', NULL, NULL, NULL);
  -- product: 39 rows
  INSERT INTO `product` (`id`, `supplier_id`, `brand`, `part_no`, `package_type`, `base_price`, `tax_rate`) VALUES
    (1, 1, '力芯微', 'ET7303Y', 'QFN14', 0.70, NULL),
    (2, 1, '力芯微', 'ET8211', 'SOP16', 0.28, NULL),
    (3, 1, '力芯微', 'ET5A033YB', 'DFN1*1', 0.30, NULL),
    (4, 1, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.32, NULL),
    (5, 2, '芯洲', 'SCT9330STER', 'ESOP-8L', 2.00, NULL),
    (6, 1, '力芯微', 'ET5907', 'WLCSP20(0.35 Pitch)', 0.85, NULL),
    (7, 1, '力芯微', 'ET2104', 'WLCSP12(0.5 Pitch)/ QFN12/TSSOP14', 0.70, NULL),
    (8, 1, '力芯微', 'ET52318YB', 'DFN1*1', 0.08, NULL),
    (9, 3, '力芯微', 'ET4860', NULL, NULL, NULL),
    (10, 3, '力芯微', 'ET4557', NULL, NULL, NULL),
    (11, 3, '力芯微', 'ET2095', NULL, NULL, NULL),
    (12, 3, '力芯微', 'ET9552', NULL, NULL, NULL),
    (13, 1, '力芯微', 'ET5907', 'WLCSP', NULL, NULL),
    (14, 1, '力芯微', 'ET5A033YB', 'XDFNI 2xI.6-8L', NULL, NULL),
    (15, 3, '力芯微', 'ETS5IADJYIB', 'DFN 12', NULL, NULL),
    (16, 1, '力芯微', 'ET5A2ADJZB', 'WLCSP6', NULL, NULL),
    (17, 3, '力芯微', 'ET7304', 'WLCSP9', NULL, NULL),
    (18, 3, '力芯微', 'ET2104Y', 'WLCSP9', NULL, NULL),
    (19, 1, '力芯微', 'ET2104', 'BGA12 1.9*1.4*0.65MM', NULL, NULL),
    (20, 3, '力芯微', 'ET9515ALT3', 'TDFN8-2x2', NULL, NULL),
    (21, 3, '力芯微', 'ET9515ALS2', 'SOT23-6', NULL, NULL),
    (22, 3, '力芯微', 'ET95153Y', 'DFN8
(3mmx3mm)', NULL, NULL),
    (23, 3, '力芯微', 'ET7303', NULL, NULL, NULL),
    (24, 3, '力芯微', 'ET7304', NULL, NULL, NULL),
    (25, 3, '力芯微', 'ET95601CX', NULL, NULL, NULL),
    (26, 3, '力芯微', 'ET52333YB', NULL, NULL, NULL),
    (27, 3, '力芯微', 'ET9523CL', NULL, NULL, NULL),
    (28, 3, '力芯微', 'ET9528', NULL, NULL, NULL),
    (29, 3, '力芯微', 'ET6037Y', 'SSOP24', NULL, NULL),
    (30, 3, '力芯微', 'ET1616', 'SOP16', NULL, NULL),
    (31, 3, '待核实品牌', 'ET5120', NULL, NULL, NULL),
    (32, 3, '待核实品牌', 'ET53130B', NULL, NULL, NULL),
    (33, 3, '待核实品牌', 'ET53833YB', NULL, NULL, NULL),
    (34, 3, '待核实品牌', 'ET6934S', NULL, NULL, NULL),
    (35, 3, '待核实品牌', 'ET6037S', NULL, NULL, NULL),
    (36, 3, '待核实品牌', 'ETC1117V50', NULL, NULL, NULL),
    (37, 3, '待核实品牌', 'LS5202V4-1D', NULL, NULL, NULL),
    (38, 3, '待核实品牌', 'LS4101V12-1N', NULL, NULL, NULL),
    (39, 3, '待核实品牌', 'T425LS3204V5-1N', NULL, NULL, NULL);
  -- purchase_order: 17 rows
  INSERT INTO `purchase_order` (`id`, `purchase_order_no`, `order_time`, `supplier_id`, `supplier_name`, `delivery_time`, `order_status`, `payment_status`, `remark`) VALUES
    (1, 'gl20260407001', '2026-04-10 00:00:00', 1, '深圳中盛昌', NULL, 'PARTIALLY_RECEIVED', 'PARTIALLY_PAID', 'Excel采购订单 行:3,5,23；采购人:Eric；结算:当月结25号前付款；预计交期原文:1周'),
    (2, 'gl20260515001', '2026-05-15 00:00:00', 1, '深圳中盛昌', NULL, 'PARTIALLY_RECEIVED', 'PARTIALLY_PAID', 'Excel采购订单 行:4,6；采购人:Eric；结算:当月结25号前付款；预计交期原文:1周'),
    (3, 'gl20260720001', '2026-07-20 00:00:00', 1, '深圳中盛昌', NULL, 'COMPLETED', 'PAID', 'Excel采购订单 行:7,8；采购人:Eric；结算:当月结25号前付款；预计交期原文:1周'),
    (4, 'gl2026717001', '2026-07-20 00:00:00', 2, '深圳联思微', NULL, 'COMPLETED', 'PAID', 'Excel采购订单 行:9；采购人:Eric；结算:货到周结；预计交期原文:1周'),
    (5, 'gl20260722001', '2026-07-22 00:00:00', 1, '深圳中盛昌', NULL, 'COMPLETED', 'PAID', 'Excel采购订单 行:10；采购人:Eric；结算:当月结25号前付款；预计交期原文:1周'),
    (6, 'gl20260721001', '2026-07-21 00:00:00', 1, '深圳中盛昌', NULL, 'COMPLETED', 'PARTIALLY_PAID', 'Excel采购订单 行:13,14,15,20,21,22,24,25,26,36,37,38,57,58,59；采购人:Eric；结算:当月结25号前付款；预计交期原文:1周'),
    (7, 'gl20260731001', '2026-07-31 00:00:00', 1, '深圳中盛昌', NULL, 'COMPLETED', 'PAID', 'Excel采购订单 行:17；采购人:Eric；结算:当月结25号前付款；预计交期原文:1周'),
    (8, 'gl20260804001', '2026-08-04 00:00:00', 2, '深圳联思微', NULL, 'COMPLETED', 'PAID', 'Excel采购订单 行:18；采购人:Eric；结算:货到周结；预计交期原文:1周'),
    (9, 'gl20260810001', '2026-08-10 00:00:00', 1, '深圳中盛昌', NULL, 'COMPLETED', 'PAID', 'Excel采购订单 行:19；采购人:Eric；结算:当月结25号前付款；预计交期原文:1周'),
    (10, 'gl20260818001', '2026-08-18 00:00:00', 1, '深圳中盛昌', NULL, 'COMPLETED', 'PAID', 'Excel采购订单 行:29；采购人:Eric；结算:当月结25号前付款；预计交期原文:未提供'),
    (11, 'gl20260818002', '2026-08-18 00:00:00', 1, '深圳中盛昌', NULL, 'PARTIALLY_RECEIVED', 'PARTIALLY_PAID', 'Excel采购订单 行:30,31,32,39,40,41,46,47,48,54,55,56；采购人:Eric；结算:当月结25号前付款；预计交期原文:未提供'),
    (12, 'gl20260903001', '2026-09-03 00:00:00', 1, '深圳中盛昌', NULL, 'COMPLETED', 'PAID', 'Excel采购订单 行:33；采购人:Eric；结算:当月结25号前付款；预计交期原文:未提供'),
    (13, 'gl20260907001', '2026-09-07 00:00:00', 1, '深圳中盛昌', NULL, 'COMPLETED', 'PARTIALLY_PAID', 'Excel采购订单 行:34,35；采购人:Eric；结算:当月结25号前付款；预计交期原文:未提供'),
    (14, 'gl20260910001', '2026-09-10 00:00:00', 1, '深圳中盛昌', NULL, 'COMPLETED', 'PARTIALLY_PAID', 'Excel采购订单 行:42,43,60,61；采购人:Eric；结算:当月结25号前付款；预计交期原文:未提供'),
    (15, 'gl20260921001', '2026-09-21 00:00:00', 1, '深圳中盛昌', NULL, 'PENDING_RECEIPT', 'UNPAID', 'Excel采购订单 行:44；采购人:Eric；结算:当月结25号前付款；预计交期原文:未提供'),
    (16, 'gl20260922001', '2026-09-22 00:00:00', 1, '深圳中盛昌', NULL, 'COMPLETED', 'UNPAID', 'Excel采购订单 行:45；采购人:Eric；结算:当月结25号前付款；预计交期原文:未提供'),
    (17, 'gl20261008001', '2026-10-08 00:00:00', 1, '深圳中盛昌', NULL, 'PARTIALLY_RECEIVED', 'UNPAID', 'Excel采购订单 行:51,52,53；采购人:Eric；结算:当月结25号前付款；预计交期原文:未提供');
  -- purchase_order_item: 26 rows
  INSERT INTO `purchase_order_item` (`id`, `purchase_order_id`, `purchase_order_no`, `product_id`, `brand`, `part_no`, `package_type`, `unit_price`, `tax_included`, `tax_rate`, `quantity`, `amount`) VALUES
    (1, 1, 'gl20260407001', 1, '力芯微', 'ET7303Y', 'QFN14', 0.70, 1, NULL, 264000, 184800.00),
    (2, 2, 'gl20260515001', 2, '力芯微', 'ET8211', 'SOP16', 0.28, 1, NULL, 200000, 56000.00),
    (3, 3, 'gl20260720001', 3, '力芯微', 'ET5A033YB', 'DFN1*1', 0.30, 1, NULL, 6000, 1800.00),
    (4, 3, 'gl20260720001', 4, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.32, 1, NULL, 9000, 2880.00),
    (5, 4, 'gl2026717001', 5, '芯洲', 'SCT9330STER', 'ESOP-8L', 2.00, 1, NULL, 100, 200.00),
    (6, 5, 'gl20260722001', 4, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.32, 1, NULL, 60000, 19200.00),
    (7, 6, 'gl20260721001', 6, '力芯微', 'ET5907', 'WLCSP20(0.35 Pitch)', 0.85, 1, NULL, 20000, 17000.00),
    (8, 6, 'gl20260721001', 7, '力芯微', 'ET2104', 'WLCSP12(0.5 Pitch)/ QFN12/TSSOP14', 0.70, 1, NULL, 9000, 6300.00),
    (9, 6, 'gl20260721001', 3, '力芯微', 'ET5A033YB', 'DFN1*1', 0.30, 1, NULL, 20000, 6000.00),
    (10, 7, 'gl20260731001', 4, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.32, 1, NULL, 60000, 19200.00),
    (11, 8, 'gl20260804001', 5, '芯洲', 'SCT9330STER', 'ESOP-8L', 2.00, 1, NULL, 750, 1500.00),
    (12, 9, 'gl20260810001', 4, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.32, 1, NULL, 120000, 38400.00),
    (13, 10, 'gl20260818001', 4, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.32, 1, NULL, 180000, 57600.00),
    (14, 11, 'gl20260818002', 6, '力芯微', 'ET5907', 'WLCSP20(0.35 Pitch)', 0.85, 1, NULL, 22000, 18700.00),
    (15, 11, 'gl20260818002', 7, '力芯微', 'ET2104', 'WLCSP12(0.5 Pitch)/ QFN12/TSSOP14', 0.70, 1, NULL, 9000, 6300.00),
    (16, 11, 'gl20260818002', 3, '力芯微', 'ET5A033YB', 'DFN1*1', 0.30, 1, NULL, 22000, 6600.00),
    (17, 12, 'gl20260903001', 8, '力芯微', 'ET52318YB', 'DFN1*1', 0.08, 1, NULL, 10000, 750.00),
    (18, 13, 'gl20260907001', 8, '力芯微', 'ET52318YB', 'DFN1*1', 0.08, 1, NULL, 20000, 1500.00),
    (19, 13, 'gl20260907001', 4, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.32, 1, NULL, 180000, 57600.00),
    (20, 14, 'gl20260910001', 3, '力芯微', 'ET5A033YB', 'DFN1*1', 0.30, 1, NULL, 18000, 5400.00),
    (21, 14, 'gl20260910001', 7, '力芯微', 'ET2104', 'WLCSP12(0.5 Pitch)/ QFN12/TSSOP14', 0.70, 1, NULL, 6000, 4200.00),
    (22, 15, 'gl20260921001', 4, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.32, 1, NULL, 180000, 57600.00),
    (23, 16, 'gl20260922001', 8, '力芯微', 'ET52318YB', 'DFN1*1', 0.08, 1, NULL, 30000, 2250.00),
    (24, 17, 'gl20261008001', 3, '力芯微', 'ET5A033YB', 'DFN1*1', 0.30, 1, NULL, 12000, 3600.00),
    (25, 17, 'gl20261008001', 7, '力芯微', 'ET2104', 'WLCSP12(0.5 Pitch)/ QFN12/TSSOP14', 0.70, 1, NULL, 3000, 2100.00),
    (26, 17, 'gl20261008001', 6, '力芯微', 'ET5907', 'WLCSP20(0.35 Pitch)', 0.85, 1, NULL, 12000, 10200.00);
  -- inbound_order: 37 rows
  INSERT INTO `inbound_order` (`id`, `purchase_order_id`, `purchase_order_no`, `supplier_id`, `supplier_name`, `inbound_type`, `status`, `inbound_time`) VALUES
    (1, 1, 'gl20260407001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-05-26 00:00:00'),
    (2, 1, 'gl20260407001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-06-15 00:00:00'),
    (3, 1, 'gl20260407001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-08-19 00:00:00'),
    (4, 2, 'gl20260515001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-06-24 00:00:00'),
    (5, 2, 'gl20260515001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-07-04 00:00:00'),
    (6, 3, 'gl20260720001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-07-23 00:00:00'),
    (7, 3, 'gl20260720001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-07-23 00:00:00'),
    (8, 4, 'gl2026717001', 2, '深圳联思微', 'PURCHASE', 'COMPLETED', '2026-07-20 00:00:00'),
    (9, 5, 'gl20260722001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-07-27 00:00:00'),
    (10, 6, 'gl20260721001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-08-03 00:00:00'),
    (11, 6, 'gl20260721001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-09-12 00:00:00'),
    (12, 6, 'gl20260721001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-10-10 00:00:00'),
    (13, 6, 'gl20260721001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-08-20 00:00:00'),
    (14, 6, 'gl20260721001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-08-22 00:00:00'),
    (15, 6, 'gl20260721001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-08-20 00:00:00'),
    (16, 6, 'gl20260721001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-08-22 00:00:00'),
    (17, 6, 'gl20260721001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-09-07 00:00:00'),
    (18, 7, 'gl20260731001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-08-03 00:00:00'),
    (19, 8, 'gl20260804001', 2, '深圳联思微', 'PURCHASE', 'COMPLETED', '2026-08-07 00:00:00'),
    (20, 9, 'gl20260810001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-08-13 00:00:00'),
    (21, 10, 'gl20260818001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-09-09 00:00:00'),
    (22, 11, 'gl20260818002', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-09-02 00:00:00'),
    (23, 11, 'gl20260818002', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-09-30 00:00:00'),
    (24, 11, 'gl20260818002', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-09-30 00:00:00'),
    (25, 11, 'gl20260818002', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-10-10 00:00:00'),
    (26, 11, 'gl20260818002', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-09-02 00:00:00'),
    (27, 11, 'gl20260818002', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-09-02 00:00:00'),
    (28, 11, 'gl20260818002', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-09-07 00:00:00'),
    (29, 12, 'gl20260903001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-09-07 00:00:00'),
    (30, 13, 'gl20260907001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-09-09 00:00:00'),
    (31, 13, 'gl20260907001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-10-10 00:00:00'),
    (32, 14, 'gl20260910001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-09-12 00:00:00'),
    (33, 14, 'gl20260910001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-10-10 00:00:00'),
    (34, 14, 'gl20260910001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-09-12 00:00:00'),
    (35, 16, 'gl20260922001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-10-10 00:00:00'),
    (36, 17, 'gl20261008001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-10-10 00:00:00'),
    (37, 17, 'gl20261008001', 1, '深圳中盛昌', 'PURCHASE', 'COMPLETED', '2026-10-10 00:00:00');
  -- inbound_order_item: 37 rows
  INSERT INTO `inbound_order_item` (`id`, `inbound_order_id`, `purchase_order_id`, `purchase_order_no`, `purchase_order_item_id`, `inbound_quantity`) VALUES
    (1, 1, 1, 'gl20260407001', 1, 30000),
    (2, 2, 1, 'gl20260407001', 1, 3000),
    (3, 3, 1, 'gl20260407001', 1, 30000),
    (4, 4, 2, 'gl20260515001', 2, 16000),
    (5, 5, 2, 'gl20260515001', 2, 8000),
    (6, 6, 3, 'gl20260720001', 3, 6000),
    (7, 7, 3, 'gl20260720001', 4, 9000),
    (8, 8, 4, 'gl2026717001', 5, 100),
    (9, 9, 5, 'gl20260722001', 6, 60000),
    (10, 10, 6, 'gl20260721001', 7, 9000),
    (11, 11, 6, 'gl20260721001', 7, 6000),
    (12, 12, 6, 'gl20260721001', 7, 5000),
    (13, 13, 6, 'gl20260721001', 8, 3000),
    (14, 14, 6, 'gl20260721001', 8, 6000),
    (15, 15, 6, 'gl20260721001', 9, 6000),
    (16, 16, 6, 'gl20260721001', 9, 9000),
    (17, 17, 6, 'gl20260721001', 9, 5000),
    (18, 18, 7, 'gl20260731001', 10, 60000),
    (19, 19, 8, 'gl20260804001', 11, 750),
    (20, 20, 9, 'gl20260810001', 12, 120000),
    (21, 21, 10, 'gl20260818001', 13, 180000),
    (22, 22, 11, 'gl20260818002', 14, 9000),
    (23, 23, 11, 'gl20260818002', 14, 6000),
    (24, 24, 11, 'gl20260818002', 14, 6000),
    (25, 25, 11, 'gl20260818002', 14, 1000),
    (26, 26, 11, 'gl20260818002', 15, 3000),
    (27, 27, 11, 'gl20260818002', 16, 18000),
    (28, 28, 11, 'gl20260818002', 16, 4000),
    (29, 29, 12, 'gl20260903001', 17, 10000),
    (30, 30, 13, 'gl20260907001', 18, 20000),
    (31, 31, 13, 'gl20260907001', 19, 180000),
    (32, 32, 14, 'gl20260910001', 20, 6000),
    (33, 33, 14, 'gl20260910001', 20, 12000),
    (34, 34, 14, 'gl20260910001', 21, 6000),
    (35, 35, 16, 'gl20260922001', 23, 30000),
    (36, 36, 17, 'gl20261008001', 25, 3000),
    (37, 37, 17, 'gl20261008001', 26, 3000);
  -- sales_order: 16 rows
  INSERT INTO `sales_order` (`id`, `order_no`, `customer_order_no`, `order_time`, `customer_id`, `customer_name`, `settle_type`, `delivery_time`, `order_status`, `payment_status`, `remark`) VALUES
    (1, 'XL-SO-0003', 'TYX20260407015', '2026-04-07 00:00:00', 1, '深圳同益芯', 'PREPAID', NULL, 'PARTIALLY_SHIPPED', 'PARTIALLY_PAID', 'Excel销售订单 行3；账期:TT/先款后货；备注:；交期原文:'),
    (2, 'XL-SO-0005', '无', '2026-06-12 00:00:00', 3, '深圳市成胜微电子有限公司', 'PREPAID', NULL, 'COMPLETED', 'PAID', 'Excel销售订单 行5；账期:TT/先款后货；备注:陈的代理商；交期原文:'),
    (3, 'XL-SO-0008', '3508-260716001', '2026-07-16 00:00:00', 4, '广州番禺巨大汽车音响设备有限公司', 'COD', NULL, 'COMPLETED', 'PAID', 'Excel销售订单 行8；账期:货到一周付款；备注:；交期原文:'),
    (4, 'XL-SO-0009', 'MT2026/071700', '2026-07-17 00:00:00', 5, '深圳市铭泰电子信息科技有限公司', 'MONTHLY', NULL, 'COMPLETED', 'UNPAID', 'Excel销售订单 行9；账期:次月结60；备注:；交期原文:'),
    (5, 'XL-SO-0010', 'QT202607150003', '2026-07-20 00:00:00', 6, '深圳市勤泰智能信息技术有限公司', 'MONTHLY', NULL, 'PARTIALLY_SHIPPED', 'UNPAID', 'Excel销售订单 行10；账期:次月结60；备注:；交期原文:'),
    (6, 'XL-SO-0013', 'MT2026/072100', '2026-07-21 00:00:00', 5, '深圳市铭泰电子信息科技有限公司', 'MONTHLY', NULL, 'COMPLETED', 'UNPAID', 'Excel销售订单 行13；账期:次月结60；备注:交付完成；交期原文:'),
    (7, 'XL-SO-0014', 'MT2026/073000', '2026-07-30 00:00:00', 5, '深圳市铭泰电子信息科技有限公司', 'MONTHLY', NULL, 'COMPLETED', 'UNPAID', 'Excel销售订单 行14；账期:次月结60；备注:交付完成；交期原文:'),
    (8, 'XL-SO-0017', '3508-260803064', '2026-08-04 00:00:00', 4, '广州番禺巨大汽车音响设备有限公司', 'COD', NULL, 'COMPLETED', 'PAID', 'Excel销售订单 行17；账期:货到付款；备注:；交期原文:'),
    (9, 'XL-SO-0018', 'MT2026/080800', '2026-08-08 00:00:00', 5, '深圳市铭泰电子信息科技有限公司', 'MONTHLY', NULL, 'COMPLETED', 'UNPAID', 'Excel销售订单 行18；账期:次月结60；备注:；交期原文:'),
    (10, 'XL-SO-0019', 'MT2026/081700', '2026-08-17 00:00:00', 5, '深圳市铭泰电子信息科技有限公司', 'MONTHLY', NULL, 'COMPLETED', 'UNPAID', 'Excel销售订单 行19；账期:次月结60；备注:；交期原文:10月10号'),
    (11, 'XL-SO-0020', '无', '2026-08-18 00:00:00', 3, '深圳市成胜微电子有限公司', 'PREPAID', NULL, 'COMPLETED', 'UNPAID', 'Excel销售订单 行20；账期:TT/先款后货；备注:陈的代理商；交期原文:'),
    (12, 'XL-SO-0021', 'MT2026/082000', '2026-08-20 00:00:00', 5, '深圳市铭泰电子信息科技有限公司', 'MONTHLY', NULL, 'COMPLETED', 'UNPAID', 'Excel销售订单 行21；账期:次月结60；备注:；交期原文:'),
    (13, 'XL-SO-0024', 'MT2026/090300', '2026-09-03 00:00:00', 5, '深圳市铭泰电子信息科技有限公司', 'MONTHLY', NULL, 'COMPLETED', 'UNPAID', 'Excel销售订单 行24；账期:次月结60；备注:；交期原文:'),
    (14, 'XL-SO-0025', 'MT2026/090700', '2026-09-07 00:00:00', 5, '深圳市铭泰电子信息科技有限公司', 'MONTHLY', NULL, 'PARTIALLY_SHIPPED', 'UNPAID', 'Excel销售订单 行25；账期:次月结60；备注:；交期原文:'),
    (15, 'XL-SO-0028', 'MT2026/092000', '2026-09-20 00:00:00', 5, '深圳市铭泰电子信息科技有限公司', 'MONTHLY', NULL, 'PENDING_SHIPMENT', 'UNPAID', 'Excel销售订单 行28；账期:次月结60；备注:；交期原文:'),
    (16, 'XL-SO-0029', 'MT2026/092100', '2026-09-21 00:00:00', 5, '深圳市铭泰电子信息科技有限公司', 'MONTHLY', NULL, 'PENDING_SHIPMENT', 'UNPAID', 'Excel销售订单 行29；账期:次月结60；备注:；交期原文:9月28');
  -- sales_order_item: 20 rows
  INSERT INTO `sales_order_item` (`id`, `sales_order_id`, `customer_order_no`, `product_id`, `brand`, `part_no`, `package_type`, `unit_price`, `tax_included`, `tax_rate`, `quantity`, `amount`) VALUES
    (1, 1, 'TYX20260407015', 1, '力芯微', 'ET7303Y', 'QFN14', 0.85, 1, NULL, 264000, 224400.00),
    (2, 2, '无', 1, '力芯微', 'ET7303Y', 'QFN14', 0.75, 1, NULL, 3000, 2250.00),
    (3, 3, '3508-260716001', 5, '芯洲', 'SCT9330STER', 'ESOP-8L', 2.50, 1, NULL, 100, 250.00),
    (4, 4, 'MT2026/071700', 4, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.35, 0, NULL, 9000, 3150.00),
    (5, 5, 'QT202607150003', 6, '力芯微', 'ET5907', 'WLCSP20(0.35 Pitch)', 1.00, 1, NULL, 282000, 282000.00),
    (6, 5, 'QT202607150003', 3, '力芯微', 'ET5A033YB', NULL, 0.35, 1, NULL, 219000, 76650.00),
    (7, 5, 'QT202607150003', 7, '力芯微', 'ET2104', 'WLCSP12(0.5 Pitch)/ QFN12/TSSOP14', 0.90, 1, NULL, 36000, 32400.00),
    (8, 6, 'MT2026/072100', 4, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.35, 0, NULL, 60000, 21000.00),
    (9, 7, 'MT2026/073000', 4, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.35, 0, NULL, 60000, 21000.00),
    (10, 8, '3508-260803064', 5, '芯洲', 'SCT9330STER', 'ESOP-8L', 2.50, 1, NULL, 750, 1875.00),
    (11, 9, 'MT2026/080800', 4, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.35, 0, NULL, 120000, 42000.00),
    (12, 10, 'MT2026/081700', 4, '力芯微', 'ET5A2ADJZB', 'WLCSP6(0.4 Pitch)', 0.35, 0, NULL, 180000, 63000.00),
    (13, 11, '无', 1, '力芯微', 'ET7303Y', 'QFN14', 0.75, 1, NULL, 30000, 22500.00),
    (14, 12, 'MT2026/082000', 3, '力芯微', 'ET5A033YB', NULL, 0.35, 0, NULL, 3000, 1050.00),
    (15, 13, 'MT2026/090300', 8, '力芯微', 'ET52318YB', NULL, 0.10, 0, NULL, 10000, 1000.00),
    (16, 14, 'MT2026/090700', 8, '力芯微', 'ET52318YB', NULL, 0.10, 0, NULL, 20000, 2000.00),
    (17, 14, 'MT2026/090700', 3, '力芯微', 'ET5A033YB', NULL, 0.35, 0, NULL, 6000, 2100.00),
    (18, 14, 'MT2026/090700', 4, '力芯微', 'ET5A2ADJZB', NULL, 0.35, 0, NULL, 180000, 63000.00),
    (19, 15, 'MT2026/092000', 4, '力芯微', 'ET5A2ADJZB', NULL, 0.35, 0, NULL, 180000, 63000.00),
    (20, 16, 'MT2026/092100', 8, '力芯微', 'ET52318YB', NULL, 0.10, 0, NULL, 30000, 3000.00);
  -- outbound_order: 29 rows
  INSERT INTO `outbound_order` (`id`, `sales_order_id`, `customer_order_no`, `customer_id`, `customer_name`, `outbound_type`, `status`, `outbound_time`) VALUES
    (1, 1, 'TYX20260407015', 1, '深圳同益芯', 'SALE', 'COMPLETED', '2026-05-27 00:00:00'),
    (2, 2, '无', 3, '深圳市成胜微电子有限公司', 'SALE', 'COMPLETED', '2026-06-15 00:00:00'),
    (3, 3, '3508-260716001', 4, '广州番禺巨大汽车音响设备有限公司', 'SALE', 'COMPLETED', '2026-07-20 00:00:00'),
    (4, 4, 'MT2026/071700', 5, '深圳市铭泰电子信息科技有限公司', 'SALE', 'COMPLETED', '2026-07-23 00:00:00'),
    (5, 6, 'MT2026/072100', 5, '深圳市铭泰电子信息科技有限公司', 'SALE', 'COMPLETED', '2026-07-27 00:00:00'),
    (6, 7, 'MT2026/073000', 5, '深圳市铭泰电子信息科技有限公司', 'SALE', 'COMPLETED', '2026-08-03 00:00:00'),
    (7, 5, 'QT202607150003', 6, '深圳市勤泰智能信息技术有限公司', 'SALE', 'COMPLETED', '2026-08-03 00:00:00'),
    (8, 5, 'QT202607150003', 6, '深圳市勤泰智能信息技术有限公司', 'SALE', 'COMPLETED', '2026-08-03 00:00:00'),
    (9, 5, 'QT202607150003', 6, '深圳市勤泰智能信息技术有限公司', 'SALE', 'COMPLETED', '2026-08-04 00:00:00'),
    (10, 8, '3508-260803064', 4, '广州番禺巨大汽车音响设备有限公司', 'SALE', 'COMPLETED', '2026-08-07 00:00:00'),
    (11, 9, 'MT2026/080800', 5, '深圳市铭泰电子信息科技有限公司', 'SALE', 'COMPLETED', '2026-08-13 00:00:00'),
    (12, 11, '无', 3, '深圳市成胜微电子有限公司', 'SALE', 'COMPLETED', '2026-08-19 00:00:00'),
    (13, 5, 'QT202607150003', 6, '深圳市勤泰智能信息技术有限公司', 'SALE', 'COMPLETED', '2026-08-22 00:00:00'),
    (14, 5, 'QT202607150003', 6, '深圳市勤泰智能信息技术有限公司', 'SALE', 'COMPLETED', '2026-08-22 00:00:00'),
    (15, 12, 'MT2026/082000', 5, '深圳市铭泰电子信息科技有限公司', 'SALE', 'COMPLETED', '2026-08-22 00:00:00'),
    (16, 5, 'QT202607150003', 6, '深圳市勤泰智能信息技术有限公司', 'SALE', 'COMPLETED', '2026-09-02 00:00:00'),
    (17, 5, 'QT202607150003', 6, '深圳市勤泰智能信息技术有限公司', 'SALE', 'COMPLETED', '2026-09-02 00:00:00'),
    (18, 13, 'MT2026/090300', 5, '深圳市铭泰电子信息科技有限公司', 'SALE', 'COMPLETED', '2026-09-07 00:00:00'),
    (19, 5, 'QT202607150003', 6, '深圳市勤泰智能信息技术有限公司', 'SALE', 'COMPLETED', '2026-09-09 00:00:00'),
    (20, 5, 'QT202607150003', 6, '深圳市勤泰智能信息技术有限公司', 'SALE', 'COMPLETED', '2026-09-09 00:00:00'),
    (21, 5, 'QT202607150003', 6, '深圳市勤泰智能信息技术有限公司', 'SALE', 'COMPLETED', '2026-09-09 00:00:00'),
    (22, 10, 'MT2026/081700', 5, '深圳市铭泰电子信息科技有限公司', 'SALE', 'COMPLETED', '2026-09-10 00:00:00'),
    (23, 14, 'MT2026/090700', 5, '深圳市铭泰电子信息科技有限公司', 'SALE', 'COMPLETED', '2026-09-10 00:00:00'),
    (24, 14, 'MT2026/090700', 5, '深圳市铭泰电子信息科技有限公司', 'SALE', 'COMPLETED', '2026-09-10 00:00:00'),
    (25, 5, 'QT202607150003', 6, '深圳市勤泰智能信息技术有限公司', 'SALE', 'COMPLETED', '2026-09-21 00:00:00'),
    (26, 5, 'QT202607150003', 6, '深圳市勤泰智能信息技术有限公司', 'SALE', 'COMPLETED', '2026-09-30 00:00:00'),
    (27, 5, 'QT202607150003', 6, '深圳市勤泰智能信息技术有限公司', 'SALE', 'COMPLETED', '2026-09-30 00:00:00'),
    (28, 5, 'QT202607150003', 6, '深圳市勤泰智能信息技术有限公司', 'SALE', 'COMPLETED', '2026-10-13 00:00:00'),
    (29, 5, 'QT202607150003', 6, '深圳市勤泰智能信息技术有限公司', 'SALE', 'COMPLETED', '2026-10-14 00:00:00');
  -- outbound_order_item: 29 rows
  INSERT INTO `outbound_order_item` (`id`, `outbound_order_id`, `sales_order_id`, `customer_order_no`, `sales_order_item_id`, `outbound_quantity`) VALUES
    (1, 1, 1, 'TYX20260407015', 1, 30000),
    (2, 2, 2, '无', 2, 3000),
    (3, 3, 3, '3508-260716001', 3, 100),
    (4, 4, 4, 'MT2026/071700', 4, 9000),
    (5, 5, 6, 'MT2026/072100', 8, 60000),
    (6, 6, 7, 'MT2026/073000', 9, 60000),
    (7, 7, 5, 'QT202607150003', 5, 6000),
    (8, 8, 5, 'QT202607150003', 6, 6000),
    (9, 9, 5, 'QT202607150003', 5, 3000),
    (10, 10, 8, '3508-260803064', 10, 750),
    (11, 11, 9, 'MT2026/080800', 11, 120000),
    (12, 12, 11, '无', 13, 30000),
    (13, 13, 5, 'QT202607150003', 6, 9000),
    (14, 14, 5, 'QT202607150003', 7, 6000),
    (15, 15, 12, 'MT2026/082000', 14, 3000),
    (16, 16, 5, 'QT202607150003', 6, 12000),
    (17, 17, 5, 'QT202607150003', 5, 3000),
    (18, 18, 13, 'MT2026/090300', 15, 10000),
    (19, 19, 5, 'QT202607150003', 5, 6000),
    (20, 20, 5, 'QT202607150003', 6, 3000),
    (21, 21, 5, 'QT202607150003', 7, 6000),
    (22, 22, 10, 'MT2026/081700', 12, 180000),
    (23, 23, 14, 'MT2026/090700', 17, 6000),
    (24, 24, 14, 'MT2026/090700', 16, 20000),
    (25, 25, 5, 'QT202607150003', 6, 6000),
    (26, 26, 5, 'QT202607150003', 5, 12000),
    (27, 27, 5, 'QT202607150003', 6, 6000),
    (28, 28, 5, 'QT202607150003', 6, 6000),
    (29, 29, 5, 'QT202607150003', 7, 6000);
  -- inventory: 3 rows
  INSERT INTO `inventory` (`id`, `product_id`, `batch_no`, `inbound_order_item_id`, `unit_cost`, `initial_quantity`, `quantity`, `reserved_quantity`, `created_at`) VALUES
    (1, 3, 'XL-SNAPSHOT-20261014-002', NULL, 0.30, 9000, 9000, 0, '2026-10-14 00:00:00'),
    (2, 7, 'XL-SNAPSHOT-20261014-003', NULL, 0.70, 3000, 3000, 0, '2026-10-14 00:00:00'),
    (3, 6, 'XL-SNAPSHOT-20261014-004', NULL, 0.85, 9000, 9000, 0, '2026-10-14 00:00:00');
  -- sample_apply: 16 rows
  INSERT INTO `sample_apply` (`id`, `customer_id`, `apply_time`, `project`, `product_id`, `quantity`, `is_delivered`) VALUES
    (1, 5, '2026-07-06 00:00:00', '智能手机/平板', 3, 10, 0),
    (2, 5, '2026-07-15 00:00:00', '智能手机/平板', 4, 30, 1),
    (3, 19, '2026-07-23 00:00:00', '空气质量坚持', 32, 20, 1),
    (4, 14, '2026-07-23 00:00:00', '后装行车记录仪', 33, 20, 1),
    (5, 15, '2026-07-24 00:00:00', '小家电控制板', 34, 230, 1),
    (6, 5, '2026-07-15 00:00:00', '智能手机/平板', 6, 5, 0),
    (7, 5, '2026-07-15 00:00:00', '智能手机/平板', 7, 5, 0),
    (8, 5, '2026-08-03 00:00:00', '智能手机/平板', 3, 150, 1),
    (9, 5, '2026-08-20 00:00:00', '智能手机/平板', 8, 45, 1),
    (10, 15, '2026-08-26 00:00:00', '小家电控制板', 35, 10, 1),
    (11, 1, '2026-08-27 00:00:00', NULL, 1, 20, 1),
    (12, 5, '2026-08-28 00:00:00', '智能手机/平板', 8, 100, 1),
    (13, 20, '2026-09-08 00:00:00', NULL, 36, 50, 1),
    (14, 5, '2026-09-21 00:00:00', NULL, 37, 100, 1),
    (15, 5, '2026-09-21 00:00:00', NULL, 38, 100, 1),
    (16, 5, '2026-09-21 00:00:00', NULL, 39, 200, 1);
  COMMIT;
END$$
DELIMITER ;
CALL `glw_import_excel_20261015`();
DROP PROCEDURE `glw_import_excel_20261015`;
SET SESSION sql_mode = @xl_old_sql_mode;
SET SESSION time_zone = @xl_old_time_zone;
SELECT 'supplier' AS table_name, COUNT(*) AS actual_rows, 3 AS expected_rows FROM `supplier`;
SELECT 'customer' AS table_name, COUNT(*) AS actual_rows, 20 AS expected_rows FROM `customer`;
SELECT 'product' AS table_name, COUNT(*) AS actual_rows, 39 AS expected_rows FROM `product`;
SELECT 'purchase_order' AS table_name, COUNT(*) AS actual_rows, 17 AS expected_rows FROM `purchase_order`;
SELECT 'purchase_order_item' AS table_name, COUNT(*) AS actual_rows, 26 AS expected_rows FROM `purchase_order_item`;
SELECT 'inbound_order' AS table_name, COUNT(*) AS actual_rows, 37 AS expected_rows FROM `inbound_order`;
SELECT 'inbound_order_item' AS table_name, COUNT(*) AS actual_rows, 37 AS expected_rows FROM `inbound_order_item`;
SELECT 'sales_order' AS table_name, COUNT(*) AS actual_rows, 16 AS expected_rows FROM `sales_order`;
SELECT 'sales_order_item' AS table_name, COUNT(*) AS actual_rows, 20 AS expected_rows FROM `sales_order_item`;
SELECT 'outbound_order' AS table_name, COUNT(*) AS actual_rows, 29 AS expected_rows FROM `outbound_order`;
SELECT 'outbound_order_item' AS table_name, COUNT(*) AS actual_rows, 29 AS expected_rows FROM `outbound_order_item`;
SELECT 'inventory' AS table_name, COUNT(*) AS actual_rows, 3 AS expected_rows FROM `inventory`;
SELECT 'sample_apply' AS table_name, COUNT(*) AS actual_rows, 16 AS expected_rows FROM `sample_apply`;
