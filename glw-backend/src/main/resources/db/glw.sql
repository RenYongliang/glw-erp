-- glw.sql: complete development schema for MySQL 8+.
-- Rebuilds all table structures, including finance (fin_*) tables, and the daily number counters.
-- Run this script first, then full_data.sql. Re-running resets all table structures.
-- Project convention: no incremental scripts. On schema changes edit this file and rebuild the database.
-- Finance data is produced by the application; after a rebuild run the finance historical rebuild once.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- Legacy batch tables replaced by inbound_order_item_alloc and outbound_order_item_batch.
DROP TABLE IF EXISTS `inbound_order_batch_item`;
DROP TABLE IF EXISTS `inbound_order_item_batch`;
DROP TABLE IF EXISTS `outbound_order_batch_item`;

DROP TABLE IF EXISTS document_number_sequence;
CREATE TABLE IF NOT EXISTS document_number_sequence (
  document_type varchar(8) NOT NULL,
  business_date date NOT NULL,
  last_sequence int NOT NULL DEFAULT 0,
  PRIMARY KEY (document_type, business_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for brand
-- 品牌属于原厂；供应商通过 supplier_brand 关联可供品牌。
-- ----------------------------
DROP TABLE IF EXISTS `brand`;
CREATE TABLE `brand`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '品牌编码',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '品牌名称',
  `manufacturer` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '原厂/生产厂商',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用；停用后不出现在业务选择列表',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `version` int(0) NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `created_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(0) NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_brand_name`(`name`) USING BTREE,
  UNIQUE INDEX `uk_brand_code`(`code`) USING BTREE,
  INDEX `idx_brand_enabled`(`enabled`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '品牌主数据表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for customer
-- ----------------------------
DROP TABLE IF EXISTS `customer`;
CREATE TABLE `customer`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '客户名称',
  `short_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户简称',
  `category` varchar(32) NULL DEFAULT NULL COMMENT '客户分类：潜在客户、成交客户、战略合作',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户地址',
  `contact` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系人手机',
  `settle_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '结算类型',
  `version` int(0) NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `created_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(0) NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '客户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for customer_product_code
-- 客户系统内的物料编码对照；同一客户同一产品唯一。
-- ----------------------------
DROP TABLE IF EXISTS `customer_product_code`;
CREATE TABLE `customer_product_code`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `customer_id` bigint(0) NOT NULL COMMENT '客户id',
  `product_id` bigint(0) NOT NULL COMMENT '产品id',
  `customer_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '客户物料编码',
  `source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL、PURCHASE_CONTRACT、IMPORT',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `remark` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `version` int(0) NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `created_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(0) NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_customer_product`(`customer_id`, `product_id`) USING BTREE,
  INDEX `idx_customer_code`(`customer_id`, `customer_code`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '客户物料编码对照表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for customer_visit
-- ----------------------------
DROP TABLE IF EXISTS `customer_visit`;
CREATE TABLE `customer_visit`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `customer_id` bigint(0) NOT NULL COMMENT '客户id',
  `visit_time` timestamp(0) NOT NULL COMMENT '拜访时间',
  `project` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户项目应用',
  `original_part_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户项目原来用的物料型号',
  `product_id` bigint(0) NULL DEFAULT NULL COMMENT '推荐产品id',
  `demand_quantity` int(0) NULL DEFAULT NULL COMMENT '客户项目用量',
  `unit_price` decimal(12, 4) NOT NULL COMMENT '产品报价',
  `tax_included` int(0) NOT NULL DEFAULT 1 COMMENT '是否含税 0未税 1含税',
  `progress` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '进度',
  `stage` varchar(32) NULL DEFAULT NULL COMMENT '跟进阶段：初次沟通、需求沟通、方案确认、报价、合同签署、销售回访',
  `detail` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '详细内容',
  `version` int(0) NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `created_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(0) NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '客户拜访记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for inbound_order
-- ----------------------------
DROP TABLE IF EXISTS `inbound_order`;
CREATE TABLE `inbound_order`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `inbound_order_no` varchar(64) NULL DEFAULT NULL COMMENT '入库单编号',
  `supplier_id` bigint(0) NOT NULL COMMENT '供应商id',
  `supplier_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '供应商名称',
  `inbound_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PURCHASE' COMMENT '入库类型, PURCHASE:采购入库',
  `status` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '状态, PENDING:待入库; COMPLETED:已入库;',
  `inbound_time` timestamp(0) NULL DEFAULT NULL COMMENT '入库时间',
  `version` int(0) NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `created_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(0) NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_inbound_order_no`(`inbound_order_no`) USING BTREE,
  INDEX `idx_inbound_order_supplier_id`(`supplier_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '入库表' ROW_FORMAT = Dynamic;

-- Remove obsolete association tables during full initialization.
DROP TABLE IF EXISTS `inbound_order_purchase_order`;

-- ----------------------------
-- Table structure for inbound_order_item
-- One product line per receipt. Purchase-order allocation and the external
-- batch (Date Code + Lot Code) live in inbound_order_item_alloc.
-- ----------------------------
DROP TABLE IF EXISTS `inbound_order_item`;
CREATE TABLE `inbound_order_item`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `inbound_order_id` bigint(0) NOT NULL COMMENT '入库单id',
  `product_id` bigint(0) NOT NULL COMMENT '产品id',
  `brand` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '品牌快照',
  `part_no` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '型号快照',
  `package_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '封装快照',
  `inbound_quantity` int(0) NOT NULL COMMENT '产品入库总数量',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `created_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(0) NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_inbound_order_product`(`inbound_order_id`, `product_id`) USING BTREE,
  INDEX `idx_inbound_order_id`(`inbound_order_id`) USING BTREE,
  INDEX `idx_product_id`(`product_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '入库产品行' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for inbound_order_item_alloc
-- ----------------------------
DROP TABLE IF EXISTS `inbound_order_item_alloc`;
CREATE TABLE `inbound_order_item_alloc`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `inbound_order_id` bigint(0) NOT NULL COMMENT '入库单id',
  `inbound_order_item_id` bigint(0) NOT NULL COMMENT '入库产品行id',
  `purchase_order_id` bigint(0) NOT NULL COMMENT '采购单id',
  `purchase_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '采购单编号快照',
  `purchase_order_item_id` bigint(0) NOT NULL COMMENT '采购商品表id',
  `date_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '原厂日期码，如2519',
  `lot_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '原厂批次号',
  `allocated_quantity` int(0) NOT NULL COMMENT '分配到该采购行的数量',
  `unit_price` decimal(12, 4) NOT NULL DEFAULT 0.0000 COMMENT '采购单价快照',
  `tax_included` int(0) NOT NULL DEFAULT 1 COMMENT '是否含税 0未税 1含税',
  `tax_rate` int(0) NULL DEFAULT NULL COMMENT '增值税税率快照',
  `amount` decimal(12, 4) NOT NULL DEFAULT 0.0000 COMMENT '金额快照',
  `currency` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'CNY' COMMENT '币种',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `created_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(0) NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_item_purchase_item_lot`(`inbound_order_item_id`, `purchase_order_item_id`, `date_code`, `lot_code`) USING BTREE,
  INDEX `idx_inbound_purchase`(`inbound_order_id`, `purchase_order_id`) USING BTREE,
  INDEX `idx_purchase_inbound`(`purchase_order_id`, `inbound_order_id`) USING BTREE,
  INDEX `idx_purchase_no_inbound`(`purchase_order_no`, `inbound_order_id`) USING BTREE,
  INDEX `idx_purchase_order_item_id`(`purchase_order_item_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '入库产品行-采购行分配' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for inventory
-- ----------------------------
DROP TABLE IF EXISTS `inventory`;
CREATE TABLE `inventory`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `product_id` bigint NOT NULL COMMENT '产品ID',
    `date_code` varchar(32) NOT NULL DEFAULT '' COMMENT '原厂日期码，如2519',
    `lot_code` varchar(64) NOT NULL DEFAULT '' COMMENT '原厂批次号',
    `batch_no` varchar(64) NOT NULL COMMENT '库存批次号：BAT+实际入库日期yyMMdd+五位产品ID+同产品当天三位流水号',
    `inbound_order_item_id` bigint DEFAULT NULL COMMENT '来源入库产品行ID',
    `unit_cost` decimal(12, 4) NOT NULL COMMENT '批次含税单位成本，未税采购价按税率换算，四位小数',
    `initial_quantity` int NOT NULL COMMENT '初始入库数量',
    `quantity` int NOT NULL COMMENT '当前库存数量',
    `reserved_quantity` int NOT NULL DEFAULT 0 COMMENT '已预占数量',
    `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` bigint DEFAULT NULL COMMENT '创建人',
    `updated_by` bigint DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_batch_no` (`batch_no`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_inbound_order_item_id` (`inbound_order_item_id`)
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '库存表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for outbound_order
-- One actual shipment can fulfil several sales orders.
-- ----------------------------
DROP TABLE IF EXISTS `outbound_order`;
CREATE TABLE `outbound_order`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `outbound_order_no` varchar(64) NOT NULL COMMENT '出库单编号',
  `customer_id` bigint(0) NOT NULL COMMENT '客户id',
  `customer_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '客户名称',
  `outbound_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'SALE' COMMENT '出库类型, SALE:销售出库',
  `status` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '状态, PENDING:待出库; COMPLETED:已出库;',
  `outbound_time` timestamp(0) NULL DEFAULT NULL COMMENT '出库时间',
  `sign_time` timestamp(0) NULL DEFAULT NULL COMMENT '客户签收日期',
  `shipping_address` varchar(300) NULL DEFAULT NULL COMMENT '本次收货地址快照',
  `shipping_recipient` varchar(80) NULL DEFAULT NULL COMMENT '本次收货人快照',
  `shipping_phone` varchar(40) NULL DEFAULT NULL COMMENT '收货联系电话快照',
  `shipping_sender` varchar(80) NULL DEFAULT NULL COMMENT '发货人快照',
  `shipping_sender_phone` varchar(40) NULL DEFAULT NULL COMMENT '发货联系电话快照',
  `shipping_method` varchar(40) NULL DEFAULT NULL COMMENT '配送方式快照',
  `shipping_carrier` varchar(80) NULL DEFAULT NULL COMMENT '物流公司',
  `shipping_tracking_number` varchar(100) NULL DEFAULT NULL COMMENT '物流单号',
  `shipping_purchase_number` text NULL COMMENT '发货单展示的客户采购单号',
  `shipping_remark` varchar(200) NULL DEFAULT NULL COMMENT '发货备注',
  `version` int(0) NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `created_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(0) NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_outbound_order_no`(`outbound_order_no`) USING BTREE,
  INDEX `idx_customer_id`(`customer_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '出库表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Remove obsolete shipment associations; allocations are the source of truth.
-- ----------------------------
DROP TABLE IF EXISTS `outbound_order_sales_order`;

-- ----------------------------
-- Table structure for outbound_order_item
-- ----------------------------
DROP TABLE IF EXISTS `outbound_order_item`;
CREATE TABLE `outbound_order_item`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `outbound_order_id` bigint(0) NOT NULL COMMENT '出库单id',
  `product_id` bigint(0) NOT NULL COMMENT '产品id',
  `brand` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '品牌快照',
  `part_no` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '型号快照',
  `package_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '封装快照',
  `outbound_quantity` int(0) NOT NULL COMMENT '产品出库总数量',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `created_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(0) NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_outbound_product`(`outbound_order_id`, `product_id`) USING BTREE,
  INDEX `idx_outbound_order_id`(`outbound_order_id`) USING BTREE,
  INDEX `idx_product_id`(`product_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '出库产品行' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for outbound_order_item_alloc
-- ----------------------------
DROP TABLE IF EXISTS `outbound_order_item_alloc`;
CREATE TABLE `outbound_order_item_alloc`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `outbound_order_id` bigint(0) NOT NULL COMMENT '出库单id',
  `outbound_order_item_id` bigint(0) NOT NULL COMMENT '出库产品行id',
  `sales_order_id` bigint(0) NOT NULL COMMENT '销售订单id',
  `sales_order_no` varchar(64) NOT NULL COMMENT '销售订单编号快照',
  `customer_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户采购单编号快照',
  `sales_order_item_id` bigint(0) NOT NULL COMMENT '销售商品表id',
  `allocated_quantity` int(0) NOT NULL COMMENT '分配到该销售行的数量',
  `unit_price` decimal(12, 4) NOT NULL DEFAULT 0.0000 COMMENT '销售单价快照',
  `tax_included` int(0) NOT NULL DEFAULT 1 COMMENT '是否含税 0未税 1含税',
  `tax_rate` int(0) NULL DEFAULT NULL COMMENT '增值税税率快照',
  `amount` decimal(12, 4) NOT NULL DEFAULT 0.0000 COMMENT '金额快照',
  `currency` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'CNY' COMMENT '币种',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `created_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(0) NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_outbound_item_sales_item`(`outbound_order_item_id`, `sales_order_item_id`) USING BTREE,
  INDEX `idx_outbound_sales`(`outbound_order_id`, `sales_order_id`) USING BTREE,
  INDEX `idx_sales_outbound`(`sales_order_id`, `outbound_order_id`) USING BTREE,
  INDEX `idx_customer_no_outbound`(`customer_order_no`, `outbound_order_id`) USING BTREE,
  INDEX `idx_sales_order_item_id`(`sales_order_item_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '出库产品行-销售行分配' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for outbound_order_item_batch
-- ----------------------------
DROP TABLE IF EXISTS `outbound_order_item_batch`;
CREATE TABLE `outbound_order_item_batch` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `outbound_order_id` bigint NOT NULL COMMENT '出库单id',
    `alloc_id` bigint NOT NULL COMMENT '出库分配行id',
    `inventory_id` bigint NOT NULL COMMENT '库存批次ID',
    `quantity` int NOT NULL COMMENT '实际出库数量',
    `unit_cost` decimal(12, 4) NOT NULL COMMENT '出库含税成本单价快照',
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` bigint DEFAULT NULL COMMENT '创建人',
    `updated_by` bigint DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_outbound_alloc_inventory` (`alloc_id`, `inventory_id`),
    KEY `idx_outbound_order_id` (`outbound_order_id`),
    KEY `idx_inventory_id` (`inventory_id`)
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '出库分配行批次' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for product
-- ----------------------------
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `supplier_id` bigint(0) NOT NULL COMMENT '供应商id',
  `brand_id` bigint(0) NOT NULL COMMENT '品牌id',
  `brand` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '品牌名称（冗余展示，与brand_id对应）',
  `part_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '型号',
  `package_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '封装',
  `version` int(0) NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `created_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(0) NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_product_supplier_id`(`supplier_id`) USING BTREE,
  INDEX `idx_product_brand_id`(`brand_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '产品信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for purchase_order
-- ----------------------------
DROP TABLE IF EXISTS `purchase_order`;
CREATE TABLE `purchase_order`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `purchase_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '采购单编号',
  `order_time` timestamp(0) NOT NULL COMMENT '下单时间',
  `supplier_id` bigint(0) NOT NULL COMMENT '供应商id',
  `supplier_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '供应商名称',
  `delivery_time` timestamp(0) NULL DEFAULT NULL COMMENT '交付日期',
  `order_status` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING_RECEIPT' COMMENT '订单状态, PENDING_RECEIPT:待收货; PARTIALLY_RECEIVED:部分收货; COMPLETED:已完成',
  `payment_status` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'UNPAID' COMMENT '付款状态, UNPAID:待付款; PARTIALLY_PAID:部分付款; PAID:已付款',
  `remark` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `version` int(0) NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `created_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(0) NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_purchase_order_no`(`purchase_order_no`) USING BTREE,
  INDEX `idx_purchase_order_supplier_id`(`supplier_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '采购订单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for purchase_order_item
-- ----------------------------
DROP TABLE IF EXISTS `purchase_order_item`;
CREATE TABLE `purchase_order_item`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `purchase_order_id` bigint(0) NOT NULL COMMENT '采购订单id',
  `purchase_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '采购订单编号',
  `product_id` bigint(0) NOT NULL COMMENT '产品id',
  `brand` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '品牌',
  `part_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '型号',
  `package_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '封装',
  `unit_price` decimal(12, 4) NOT NULL COMMENT '单价',
  `tax_included` int(0) NOT NULL DEFAULT 1 COMMENT '是否含税 0未税 1含税',
  `tax_rate` int(0) NULL DEFAULT NULL COMMENT '增值税税率',
  `quantity` int(0) NOT NULL COMMENT '数量',
  `amount` decimal(12, 4) NOT NULL COMMENT '金额',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `created_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(0) NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_purchase_order_id`(`purchase_order_id`) USING BTREE,
  INDEX `idx_purchase_order_no`(`purchase_order_no`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '采购订单商品表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sales_order
-- ----------------------------
DROP TABLE IF EXISTS `sales_order`;
CREATE TABLE `sales_order`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '销售订单号',
  `customer_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户采购单号',
  `order_time` timestamp(0) NOT NULL COMMENT '下单时间',
  `customer_id` bigint(0) NOT NULL COMMENT '客户id',
  `customer_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '客户名称',
  `settle_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'CASH' COMMENT '结算类型, CASH:现金; MONTHLY:月结; PREPAID:预付; COD:货到付款',
  `delivery_time` timestamp(0) NULL DEFAULT NULL COMMENT '交付日期',
  `order_status` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING_SHIPMENT' COMMENT '订单状态, PENDING_SHIPMENT:待发货; PARTIALLY_SHIPPED:部分发货; COMPLETED:已完成; CANCELLED:剩余未交取消，保留已出库记录',
  `payment_status` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'UNPAID' COMMENT '收款状态, UNPAID:待收款; PARTIALLY_PAID:部分收款; PAID:已收款',
  `remark` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `version` int(0) NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `created_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(0) NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_order_no`(`order_no`) USING BTREE,
  INDEX `idx_customer_order_no`(`customer_order_no`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '销售订单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sales_order_item
-- ----------------------------
DROP TABLE IF EXISTS `sales_order_item`;
CREATE TABLE `sales_order_item`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sales_order_id` bigint(0) NOT NULL COMMENT '销售订单id',
  `customer_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '客户采购单号',
  `product_id` bigint(0) NOT NULL COMMENT '产品id',
  `brand` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '品牌',
  `part_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '型号',
  `package_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '封装',
  `customer_part_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户物料编码快照',
  `unit_price` decimal(12, 4) NOT NULL COMMENT '单价',
  `tax_included` int(0) NOT NULL DEFAULT 1 COMMENT '是否含税 0未税 1含税',
  `tax_rate` int(0) NULL DEFAULT NULL COMMENT '增值税税率',
  `quantity` int(0) NOT NULL COMMENT '数量',
  `cancelled_quantity` int NOT NULL DEFAULT 0 COMMENT '取消的未交数量，有效数量为quantity减去本字段',
  `amount` decimal(12, 4) NOT NULL COMMENT '金额',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `created_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(0) NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_sales_order_id`(`sales_order_id`) USING BTREE,
  INDEX `idx_customer_order_no`(`customer_order_no`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '销售订单商品表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sales_order_batch_item
-- ----------------------------
DROP TABLE IF EXISTS `sales_order_batch_item`;
CREATE TABLE `sales_order_batch_item` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `sales_order_item_id` bigint NOT NULL COMMENT '销售订单明细ID',
    `inventory_id` bigint NOT NULL COMMENT '库存批次ID',
    `quantity` int NOT NULL COMMENT '该批次预占数量',
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` bigint DEFAULT NULL COMMENT '创建人',
    `updated_by` bigint DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sales_order_item_inventory` (`sales_order_item_id`, `inventory_id`),
    KEY `idx_inventory_id` (`inventory_id`)
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '销售商品批次表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sample_apply
-- ----------------------------
DROP TABLE IF EXISTS `sample_apply`;
CREATE TABLE `sample_apply`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `customer_id` bigint(0) NOT NULL COMMENT '客户id',
  `apply_time` timestamp(0) NOT NULL COMMENT '申请时间',
  `project` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户项目应用',
  `product_id` bigint(0) NOT NULL COMMENT '送样产品id',
  `quantity` int(0) NOT NULL COMMENT '送样数量',
  `is_delivered` int(0) NULL DEFAULT 0 COMMENT '是否送样 0未送样 1已送样',
  `version` int(0) NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `created_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(0) NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '样品申请表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for supplier
-- ----------------------------
DROP TABLE IF EXISTS `supplier`;
CREATE TABLE `supplier`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '供应商编码',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '供应商名称',
  `short_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '供应商简称',
  `category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '供应商分类',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用 0停用 1启用',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '供应商地址',
  `contact` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系人手机/座机',
  `tax_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '税号',
  `bank_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '开户行',
  `bank_account` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '银行账号',
  `account_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '开户户名',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `version` int(0) NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `created_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(0) NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_supplier_name`(`name`) USING BTREE,
  UNIQUE INDEX `uk_supplier_code`(`code`) USING BTREE,
  INDEX `idx_supplier_enabled`(`enabled`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '供应商表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for supplier_brand
-- 供应商与品牌多对多关联：一个供应商可供应多个品牌，一个品牌也可由多个供应商供应。
-- ----------------------------
DROP TABLE IF EXISTS `supplier_brand`;
CREATE TABLE `supplier_brand`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `supplier_id` bigint(0) NOT NULL COMMENT '供应商id',
  `brand_id` bigint(0) NOT NULL COMMENT '品牌id',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `version` int(0) NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `created_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(0) NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_supplier_brand`(`supplier_id`, `brand_id`) USING BTREE,
  INDEX `idx_supplier_brand_brand_id`(`brand_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '供应商可供应品牌表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '登录账号',
  `password_hash` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'BCrypt密码哈希',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户名称',
  `role` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'USER' COMMENT '用户角色',
  `department` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '所属部门',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用 0禁用 1启用',
  `deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删除 1已删除',
  `version` int(0) NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_user_username`(`username`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统用户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Finance: 应收应付、对账单、收付款与核销
-- 结构与业务表一起全量重建；财务记录由应用生成，重建后执行一次“历史回填”。
-- ----------------------------
DROP TABLE IF EXISTS fin_audit;
DROP TABLE IF EXISTS fin_allocation;
DROP TABLE IF EXISTS fin_statement_item;
DROP TABLE IF EXISTS fin_statement;
DROP TABLE IF EXISTS fin_statement_template;
DROP TABLE IF EXISTS fin_payment;
DROP TABLE IF EXISTS fin_open_item;
DROP TABLE IF EXISTS fin_profile;
DROP TABLE IF EXISTS fin_lock;

-- ----------------------------
-- Table structure for fin_lock
-- 全局写操作行锁，占位行是应用启动的前提，属于建表引导数据。
-- ----------------------------
CREATE TABLE fin_lock (id int PRIMARY KEY) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO fin_lock VALUES (1);

-- ----------------------------
-- Table structure for fin_profile
-- 每个往来单位一份结算档案：截止日、账单周期、出单时点与到期规则。
-- partner_id = 0 是公司默认方案；客户行 plan_source=DEFAULT 时复制默认行，默认行保存后自动同步。
-- ----------------------------
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
 plan_source varchar(10) NOT NULL DEFAULT 'CUSTOM' COMMENT 'DEFAULT=复制公司默认方案（只读），CUSTOM=自定义',
 updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY uk_partner (direction, partner_id),
 CHECK (direction IN ('AR','AP')),
 CHECK (plan_source IN ('DEFAULT','CUSTOM')),
 CHECK (term_days BETWEEN 0 AND 3650),
 CHECK (term_basis IN ('BIZ_DATE','MONTH_END')),
 CHECK (cutoff_day IS NULL OR cutoff_day BETWEEN 1 AND 31),
 CHECK (cycle_months BETWEEN 1 AND 12),
 CHECK (statement_day BETWEEN 1 AND 31),
 CHECK (due_rule IN ('CREDIT_DAYS','CALENDAR')),
 CHECK (credit_basis IN ('PERIOD_END','ISSUE_DATE','ISSUE_MONTH_END')),
 CHECK (due_day BETWEEN 0 AND 31)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for fin_open_item
-- 可对账的应收/应付最小单元，来源出入库分配行快照。
-- ----------------------------
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

-- ----------------------------
-- Table structure for fin_statement
-- 对账单，期间可为单月或跨月区间。
-- ----------------------------
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

-- ----------------------------
-- Table structure for fin_statement_template
-- 文档模板元数据（对账单/合同/发货单），layout_json 非空时走可视化渲染。
-- ----------------------------
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

-- ----------------------------
-- Table structure for fin_statement_item
-- 对账单明细快照，出单时固化价格与数量。
-- ----------------------------
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

-- ----------------------------
-- Table structure for fin_payment
-- 收付款单。
-- ----------------------------
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

-- ----------------------------
-- Table structure for fin_allocation
-- 核销明细：收付款金额分配到哪些可对账项。
-- ----------------------------
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

-- ----------------------------
-- Table structure for fin_audit
-- 财务操作审计。
-- ----------------------------
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

SET FOREIGN_KEY_CHECKS = 1;
