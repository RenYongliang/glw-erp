# GLW-ERP 项目分析报告

> 分析日期：2026-10-29
> 分析范围：`glw-backend`、`glw-web`、`glw-website` 三个子项目及数据库脚本、测试与文档。

## 一、总体结论

这是一个面向电子元器件分销场景的**进销存 / ERP 单体应用**，已经具备可运行的真实业务骨架：

- 登录鉴权、销售/采购/库存查询、入库与出库落库、批次成本、应收应付与对账首版均为真实实现。
- 入库/出库数据会写入数据库，并联动库存、订单状态与财务应收应付。

但项目当前处于**“半真实、半原型”状态**：

- 出入库、客户、财务已经接入后端。
- 销售订单、采购订单的新增/审批/付款/退货/删除等操作仍只写入浏览器 `localStorage`，刷新后会被后端数据覆盖。
- 部分运行文档仍按“纯前端原型”描述，与实际代码不一致。

**上线或正式演示前，最需要处理的是“真实后端”和“前端本地原型”两套逻辑并存的边界问题。**

---

## 二、项目组成

| 子项目 | 技术栈 | 体量 | 作用 |
|---|---|---|---|
| `glw-backend` | Java 21、Spring Boot 4.1.1、MyBatis-Plus 3.5.17、MySQL、Redis、JJWT、MapStruct | 228 个 Java 文件，约 8k 行主代码 | 单体后端 |
| `glw-web` | Vue 3.5、TypeScript 6、Vite 8、Element Plus、Pinia | 约 18k 行 TS/Vue | ERP 管理端 |
| `glw-website` | 原生 HTML/CSS/JS | 16 个文件 | 公司官网静态页，与 ERP 无代码耦合 |

数据库包含约 **20 张 ERP 业务表 + 8 张财务表**，以及 11 个业务枚举。

---

## 三、技术架构与核心流程

### 3.1 后端分层

- `controller → service → mapper/XML → entity` 分层组织。
- VO 与实体转换使用 MapStruct。
- 统一 `ApiResponse`、`GlobalExceptionHandler` 与错误码。
- 使用 MyBatis-Plus 的 `BaseMapper` / `IService` / `ServiceImpl`，自定义 SQL 放在 Mapper XML。
- 金额字段统一 `decimal(12,4)`，业务表带有 `version` 乐观锁字段。

### 3.2 认证与安全

- 登录前通过 `GET /api/users/login-key` 获取 RSA 公钥与一次性 challenge。
- 前端使用 AES-256-GCM 加密账号密码，再用 RSA-OAEP 包装 AES 密钥。
- 登录成功返回 accessToken / refreshToken，双 Token 类型隔离。
- refresh 轮换并检测重放；prod 会话存 Redis，mock 会话存内存。
- 白名单、CORS、统一 401/403 响应已配置。

整体认证设计比较扎实，但仍有权限粒度和登录限流方面的缺口，见问题清单。

### 3.3 入库链路

```text
采购订单
  → 入库分配（采购明细 + Date Code + Lot Code）
  → inbound_order / inbound_order_item / inbound_order_item_alloc
  → inventory 批次（产品 + Date Code + Lot Code + 单位成本）
  → 更新采购订单状态
  → 财务 AP 投影
```

核心实现：

- 创建入库：`glw-backend/src/main/java/com/glw/backend/service/impl/InboundOrderServiceImpl.java:135`
- 财务投影：`glw-backend/src/main/java/com/glw/backend/service/impl/InboundOrderServiceImpl.java:243`

入库与库存、订单状态、AP 投影在同一事务内完成，并使用 `FOR UPDATE` 锁定采购订单、产品与相关库存。

### 3.4 出库链路

```text
销售订单
  → 出库分配（销售明细）
  → 选择库存批次并记录成本快照
  → 扣减库存
  → 更新销售订单状态
  → 财务 AR 投影
```

核心实现：

- 创建出库：`glw-backend/src/main/java/com/glw/backend/service/impl/OutboundOrderServiceImpl.java:128`
- 财务投影：`glw-backend/src/main/java/com/glw/backend/service/impl/OutboundOrderServiceImpl.java:282`

支持一张出库单关联多张同客户销售订单，同一个库存批次可同时服务于多条销售明细，库存按批次汇总后只扣减一次。

### 3.5 财务模块（第一版）

已实现：

- 应收应付投影（AR / AP）
- 对账单生成、发送、确认、作废
- 收付款登记、作废
- 核销、反核销
- 结算档案
- 历史回填
- 审计记录
- 财务行锁与至少一次幂等保护

明确暂不包含：

- 发票及红冲
- 折让尾差
- 退换货调整
- 外币
- 关账
- 银行导入

### 3.6 前端结构

- 路由：销售订单、销售出库、采购订单、产品入库、库存列表、库存看板、客户、跟进记录、财务四页面、登录页。
- 查询层：`services/*Api.ts` 统一使用 `apiRequest`，自动携带 accessToken，401 时刷新并重试。
- 列表：多数业务列表已从后端真实查询，CSV 导出由后端生成。
- 状态：`stores/sales.ts`、`stores/purchase.ts`、`stores/crm.ts`、`stores/auth.ts`。

---

## 四、实际验证结果

### 4.1 后端测试

```powershell
cd glw-backend
.\mvnw.cmd clean test
```

结果：**128 个测试全部通过，BUILD SUCCESS**。

需要注意：

- 直接执行 `.\mvnw.cmd test`（不 clean）时，曾因 `target` 中残留已删除的 Mapper XML 而失败。
- 执行 `clean test` 后恢复正常，说明不是源码问题，而是构建产物清理问题。

### 4.2 前端测试

```powershell
cd glw-web
node --test tests/*.test.mjs
```

结果：**85 通过、1 失败**。

失败用例：

- `glw-web/tests/multi-inbound.test.mjs:56-57`
- 测试仍期望错误信息包含“至少两张”。
- 当前实现已改为优先校验“批次尚有数量未分配”，属于测试用例过期。

### 4.3 前端类型检查

```powershell
cd glw-web
npm run type-check
```

结果：通过。

### 4.4 覆盖盲区

- 没有真实 MySQL / Redis 集成测试。
- 没有浏览器端 E2E 测试。
- 没有 CI 流水线。

---

## 五、主要问题（按优先级）

### P0-1：前端部分写操作只写 localStorage，刷新后会丢失

销售端：

- `glw-web/src/stores/sales.ts:57` `saveOrder`
- `glw-web/src/stores/sales.ts:89` `approve`
- 付款、退货、导入、删除同样为本地逻辑

采购端：

- `glw-web/src/stores/purchase.ts:60` `saveOrder`
- `glw-web/src/stores/purchase.ts:88` `approve`
- 付款、退货、导入、删除同样为本地逻辑

但 `load()` 每次刷新都会用后端数据整体覆盖状态，并清空 `payments/returns/logs`：

- `glw-web/src/stores/sales.ts:33`
- `glw-web/src/stores/purchase.ts:36`

结果：

- 用户新增订单、审批、确认付款或退货后，界面看起来成功。
- 刷新页面或重新进入菜单后，这些操作全部消失。
- 这会造成明显的数据可信度问题。

目前真正落库的写入能力只有：

- 入库单创建
- 出库单创建
- 客户与拜访记录 CRUD
- 财务模块操作

建议：

1. 实现销售/采购订单及审批、付款、退货的后端接口；或
2. 在“后端模式”下隐藏/禁用未接入后端的操作，并明确提示“仅本地演示”。

### P0-2：运行说明与实际行为不一致

- `glw-backend/README.md:22` 描述默认 `mock` profile 不依赖 MySQL/Redis。
- `glw-backend/README.md:60` 仍称“业务页面使用本地 mock 数据”。
- 实际上除销售列表外，所有业务 Controller 都标注了 `@Profile("!mock")`。

结果：

- 按 README 用默认 mock profile 启动后端后，采购、库存、财务、客户接口都不存在。
- 前端登录可以成功，但业务页面会加载失败或显示空数据。
- 要使用完整功能，必须使用 `local` profile 并准备好 MySQL/Redis。

相关文档同样滞后：

- `glw-web/docs/sales-pages.md:18` 仍称销售单据保存在 localStorage、尚未接入后端。
- `glw-web/docs/purchase-pages.md:19` 仍称没有采购后端接口。

建议：

- 明确整理 mock / local / prod 三种启动方式。
- 修正 README 与页面文档。
- 在 mock 模式下给出前端可视化提示，避免误判。

### P1-1：已提交的敏感配置与演示账号

- `glw-backend/src/main/resources/application-local.yml:6` 提交了硬编码的本地数据库口令。
- `full_data.sql` 包含真实客户/供应商数据、用户 BCrypt 哈希，以及文档中公开密码的 `admin` / `demo` 账号。
- 登录流程没有失败次数限制、账号锁定或验证码逻辑。
- Token 存储在 localStorage/sessionStorage，存在 XSS 窃取风险。
- 除财务“历史回填”要求 ADMIN 外，其他接口仅要求登录，没有细粒度权限。

风险：

- 开发配置中的口令不应进入版本库。
- 演示账号不能直接用于生产种子。
- 任意登录用户目前都可以生成对账单、作废收付款、反核销。

建议：

- 移除硬编码口令，改用环境变量。
- 区分开发种子与生产初始化脚本。
- 增加登录限流与账号锁定。
- 补充角色/权限模型。
- 为非财务写操作增加审计记录。

### P1-2：采购订单日期筛选 Bug

位置：`glw-backend/src/main/java/com/glw/backend/service/impl/PurchaseOrderServiceImpl.java:79-80`

```java
.ge(StringUtils.hasText(pageDTO.getOrderTimeStart()), PurchaseOrder::getOrderTimeStart, pageDTO.getOrderTimeStart())
.le(StringUtils.hasText(pageDTO.getOrderTimeEnd()),   PurchaseOrder::getOrderTimeEnd,   pageDTO.getOrderTimeEnd())
```

实体中 `orderTimeStart` / `orderTimeEnd` 标注为 `@TableField(exist = false)`，不是数据库列。

正确写法应与销售端一致，比较 `PurchaseOrder::getOrderTime`：

```java
.ge(StringUtils.hasText(pageDTO.getOrderTimeStart()), PurchaseOrder::getOrderTime, pageDTO.getOrderTimeStart())
.le(StringUtils.hasText(pageDTO.getOrderTimeEnd()),   PurchaseOrder::getOrderTime, pageDTO.getOrderTimeEnd())
```

当前一旦传入日期筛选，可能直接抛异常或生成无效 SQL 条件。

### P1-3：缺少可逆操作与幂等保护

- 入库/出库只创建 `COMPLETED` 单据，没有草稿、作废、红冲、库存恢复。
- 创建接口没有幂等键。
- 重复提交可能生成重复入库/出库，并重复扣减库存、重复生成财务应收应付。
- 销售/采购订单没有 HTTP 新增、修改、取消接口。

### P1-4：工程化基础设施缺失

- 没有 Flyway / Liquibase，只有“全量删表重建 + 导入”的 SQL 脚本。
- 没有 Docker、CI/CD、根目录统一 README。
- 前端没有 `npm test` 脚本。
- 后端测试文档未说明必须先 `clean`。
- 没有真实数据库集成测试和 E2E 测试。

### P2-1：前端可维护性

单文件过大：

| 文件 | 行数 |
|---|---:|
| `glw-web/src/components/sales/SalesRecordDialog.vue` | 约 1601 |
| `glw-web/src/views/SalesManagementView.vue` | 约 1282 |
| `glw-web/src/views/FinanceView.vue` | 约 1103 |
| `glw-web/src/components/purchase/PurchaseRecordDialog.vue` | 约 1084 |
| `glw-web/src/views/PurchaseManagementView.vue` | 约 1003 |

状态、校验、查询、弹窗、打印逻辑混在同一文件中，后续维护和测试成本较高。

### P2-2：文档滞后

- `glw-backend/docs/persistence-layer.md:3` 仍写“14 张业务表”和旧提交 `c8f199a`。
- 实际表结构已增加多对多分配表、出库批次表、财务表。
- 部分文档仍描述“线上入库流程尚未接入编号生成”，但代码已经接入。

### P2-3：数据库与领域模型

- 没有外键约束，依赖应用层保证一致性。
- `created_by` / `updated_by` 在全项目中从未赋值，审计字段实际为空。
- 部分 `int(0)` 写法属于旧版 MySQL 显示宽度风格。
- 多币种字段已预留，但财务明确只支持 CNY。
- 产品、供应商主数据没有维护接口，只能通过 SQL 导入。
- `sales_order_batch_item` 与 `inventory.reserved_quantity` 当前为预留结构，尚未产生实际预占。

---

## 六、建议的推进顺序

### 第一阶段：统一数据边界

1. 把销售/采购订单的新增、修改、审批、付款、退货、删除接后端。
2. 在接口完成前，禁用后端模式下的本地写入操作。
3. 统一入库/出库的幂等键与失败提示。

### 第二阶段：修复与验证

1. 修复采购订单日期筛选 Bug。
2. 更新过期测试用例。
3. 增加前端 `npm test` 脚本。
4. 固定执行 `mvn clean verify` + 前端测试。
5. 修正 README、sales-pages、purchase-pages、persistence-layer 文档。

### 第三阶段：安全与工程化

1. 移除硬编码数据库口令。
2. 分离开发种子与生产初始化。
3. 增加登录限流、账号锁定、角色权限。
4. 引入 Flyway / Liquibase。
5. 增加 Docker、CI/CD 与根目录 README。

### 第四阶段：生产级能力

1. 出入库作废、红冲、库存恢复。
2. 真实 MySQL/Redis 集成测试。
3. Playwright 端到端测试。
4. 前端大组件拆分。
5. 可观测性：结构化日志、指标、审计查询。

---

## 七、总体评价

**优点：**

- 领域模型对电子元器件批次、Date Code、Lot Code、单位成本的处理比较完整。
- 入库/出库与库存、订单状态、财务投影放在同一事务内，事务边界清晰。
- 财务首版功能完整，金额精度和核销规则设计较严谨。
- 认证体系明显高于一般原型项目。
- 后端单元测试数量可观，核心金额与列表逻辑有覆盖。

**主要短板：**

- 真实后端与前端本地原型并存，用户无法判断哪些操作真正保存。
- 销售/采购订单主数据写入链路尚未打通。
- 文档、启动方式、权限和工程化基础设施仍停留在开发阶段。

**判断：**

项目不是“从零原型”，而是**已经完成核心交易与财务骨架、但尚未完成主数据写入闭环和上线配套**的系统。下一阶段应优先收口数据边界，而不是继续扩展页面或财务功能。
