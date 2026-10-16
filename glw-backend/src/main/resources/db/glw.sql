/*
 Navicat Premium Data Transfer

 Source Server         : local
 Source Server Type    : MySQL
 Source Server Version : 80042
 Source Host           : localhost:3306
 Source Schema         : glw

 Target Server Type    : MySQL
 Target Server Version : 80042
 File Encoding         : 65001

 Date: 28/09/2026 18:34:31
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for customer
-- ----------------------------
DROP TABLE IF EXISTS `customer`;
CREATE TABLE `customer`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '客户名称',
  `short_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户简称',
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
  `purchase_order_id` bigint(0) NOT NULL COMMENT '采购单id',
  `purchase_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '采购单编号',
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
  INDEX `idx_purchase_order_id`(`purchase_order_id`) USING BTREE,
  INDEX `idx_purchase_order_no`(`purchase_order_no`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '入库表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for inbound_order_item
-- ----------------------------
DROP TABLE IF EXISTS `inbound_order_item`;
CREATE TABLE `inbound_order_item`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `inbound_order_id` bigint(0) NOT NULL COMMENT '入库单id',
  `purchase_order_id` bigint(0) NOT NULL COMMENT '采购单id',
  `purchase_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '采购单编号',
  `purchase_order_item_id` bigint(0) NOT NULL COMMENT '采购商品表id',
  `inbound_quantity` int(0) NOT NULL COMMENT '入库数量',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `created_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(0) NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_inbound_order_id`(`inbound_order_id`) USING BTREE,
  INDEX `idx_purchase_order_id`(`purchase_order_id`) USING BTREE,
  INDEX `idx_purchase_order_no`(`purchase_order_no`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '入库商品表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for inventory
-- ----------------------------
DROP TABLE IF EXISTS `inventory`;
CREATE TABLE `inventory`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `product_id` bigint NOT NULL COMMENT '产品ID',
    `batch_no` varchar(64) NOT NULL COMMENT '库存批次号：BAT+实际入库日期yyMMdd+五位产品ID+同产品当天三位流水号',
    `inbound_order_item_id` bigint DEFAULT NULL COMMENT '来源入库明细ID',
    `unit_cost` decimal(12, 4) NOT NULL COMMENT '批次单位成本',
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
-- ----------------------------
DROP TABLE IF EXISTS `outbound_order`;
CREATE TABLE `outbound_order`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sales_order_id` bigint(0) NOT NULL COMMENT '销售订单id',
  `customer_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户采购单编号',
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
  INDEX `idx_sales_order_id`(`sales_order_id`) USING BTREE,
  INDEX `idx_customer_order_no`(`customer_order_no`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '出库表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for outbound_order_item
-- ----------------------------
DROP TABLE IF EXISTS `outbound_order_item`;
CREATE TABLE `outbound_order_item`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `outbound_order_id` bigint(0) NOT NULL COMMENT '出库单id',
  `sales_order_id` bigint(0) NOT NULL COMMENT '销售单id',
  `customer_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '客户采购单编号',
  `sales_order_item_id` bigint(0) NOT NULL COMMENT '销售商品表id',
  `outbound_quantity` int(0) NOT NULL COMMENT '出库数量',
  `created_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_at` timestamp(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `created_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint(0) NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_outbound_order_id`(`outbound_order_id`) USING BTREE,
  INDEX `idx_sales_order_id`(`sales_order_id`) USING BTREE,
  INDEX `idx_customer_order_no`(`customer_order_no`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '出库商品表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for outbound_order_batch_item
-- ----------------------------
DROP TABLE IF EXISTS `outbound_order_batch_item`;
CREATE TABLE `outbound_order_batch_item` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `outbound_order_item_id` bigint NOT NULL COMMENT '出库订单明细ID',
    `inventory_id` bigint NOT NULL COMMENT '库存批次ID',
    `quantity` int NOT NULL COMMENT '实际出库数量',
    `unit_cost` decimal(12, 4) NOT NULL COMMENT '出库成本单价快照',
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` bigint DEFAULT NULL COMMENT '创建人',
    `updated_by` bigint DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_outbound_order_item_inventory` (`outbound_order_item_id`, `inventory_id`),
    KEY `idx_inventory_id` (`inventory_id`)
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '出库商品批次表' ROW_FORMAT = Dynamic;

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
  INDEX `idx_purchase_order_no`(`purchase_order_no`) USING BTREE
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

SET FOREIGN_KEY_CHECKS = 1;
