-- glw.sql: complete development schema for MySQL 8+.
-- Rebuilds all business tables, daily number counters and sys_user.
-- Run this script first, then full_data.sql. Re-running resets all table data.

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
  UNIQUE INDEX `uk_inbound_order_no`(`inbound_order_no`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '入库表' ROW_FORMAT = Dynamic;

-- One actual receipt can fulfill several purchase orders.
DROP TABLE IF EXISTS `inbound_order_purchase_order`;
CREATE TABLE `inbound_order_purchase_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `inbound_order_id` bigint NOT NULL COMMENT '入库单ID',
  `purchase_order_id` bigint NOT NULL COMMENT '采购单ID',
  `purchase_order_no` varchar(64) NOT NULL COMMENT '采购单编号快照',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inbound_purchase` (`inbound_order_id`, `purchase_order_id`),
  KEY `idx_purchase_order_id` (`purchase_order_id`),
  KEY `idx_purchase_order_no` (`purchase_order_no`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '入库单与采购单关联表';

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
  INDEX `idx_inbound_order_id`(`inbound_order_id`) USING BTREE,
  INDEX `idx_purchase_order_id`(`purchase_order_id`) USING BTREE,
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
-- Table structure for outbound_order_sales_order
-- ----------------------------
DROP TABLE IF EXISTS `outbound_order_sales_order`;
CREATE TABLE `outbound_order_sales_order`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `outbound_order_id` bigint(0) NOT NULL COMMENT '出库单id',
  `sales_order_id` bigint(0) NOT NULL COMMENT '销售订单id',
  `sales_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '销售订单编号快照',
  `customer_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户采购单编号快照',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_outbound_sales_order`(`outbound_order_id`, `sales_order_id`) USING BTREE,
  INDEX `idx_sales_order_id`(`sales_order_id`) USING BTREE,
  INDEX `idx_customer_order_no`(`customer_order_no`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '出库单与销售订单关联表' ROW_FORMAT = Dynamic;

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
  INDEX `idx_outbound_order_id`(`outbound_order_id`) USING BTREE,
  INDEX `idx_sales_order_id`(`sales_order_id`) USING BTREE,
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
  `brand` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '品牌',
  `part_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '型号',
  `package_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '封装',
  `version` int(0) NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `created_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(0) NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`) USING BTREE
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
  UNIQUE INDEX `uk_purchase_order_no`(`purchase_order_no`) USING BTREE
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
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '供应商名称',
  `short_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '供应商简称',
  `brand` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '品牌',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户地址',
  `contact` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系人手机',
  `version` int(0) NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `created_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(0) NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '供应商表' ROW_FORMAT = Dynamic;

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

SET FOREIGN_KEY_CHECKS = 1;
