# 财务对账模块方案（AR / AP）

> 本文只描述方案，不包含代码。先确认模型、边界和实施顺序，再落地。

> 2026-10-28：第一版已按评审意见实现，实际范围和初始化说明见 [finance_v1.md](finance_v1.md)。财务 DDL 已并入 `glw.sql`（不再单独维护 `finance.sql`）。下文保留为后续阶段规划，其中出库写流程等历史描述已不适用于当前代码。

## 一、总体原则

1. **业务表只记录交易事实，财务字段不写回出入库行**
   新建独立财务子账 `fin_*`，所有对账、开票、核销状态都放在财务表里。

2. **三层快照**
   - 业务行：`inbound_order_item_alloc` / `outbound_order_item_alloc`（已保存单价、税率、金额、币种快照）
   - 财务事实：`fin_open_item`（可对账项）
   - 对外确认快照：`fin_statement_item`（对账单明细）

3. **应收应付同一套模型**
   用 `direction = AR / AP` 区分，不写两套代码。

4. **金额在进入财务表时固化**
   单价、含税标志、税率、币种、税额、价税合计都在投影时快照。以后业务改价，不影响历史对账单。

5. **所有写入幂等**
   唯一键 + `INSERT IGNORE` + 条件更新 + 行锁；单据号与业务数据在同一事务内生成。

---

## 二、领域对象

| 对象 | 作用 | 数据来源 |
|---|---|---|
| 可对账项 `fin_open_item` | 财务上的最小应收/应付单元 | 入库分配行、出库明细行 |
| 对账单 `fin_statement` | 按伙伴 + 期间 + 币种汇总对外确认 | 可对账项池 |
| 对账单明细 `fin_statement_item` | 发送时的金额/数量快照 | 生成草稿时复制 |
| 差异单 `fin_discrepancy` | 记录数量、价格、金额、批次等不一致 | 对账单明细 |
| 发票 `fin_invoice` | 蓝票、红票及金额 | 对账单 |
| 收付款单 `fin_payment` | 一笔银行到账/付款 | 财务手工或银行导入 |
| 核销明细 `fin_allocation` | 收付款金额分配到哪些可对账项 | 核销操作 |
| 结算档案 `fin_profile` | 每个伙伴的账期、对账日、对账依据 | 财务维护 |
| 会计期间 `fin_period` | 月结、关账、反关账 | 财务维护 |

---

## 三、核心表设计

### 1. `fin_open_item`（最重要）

字段分六组：

- **来源**：`direction`、`source_type`、`source_id`、`source_line_id`、`source_no`、`order_id`、`order_no`、`partner_order_no`
- **伙伴**：`partner_type`、`partner_id`、`partner_name`
- **商品**：`product_id`、`brand`、`part_no`、`package_type`、`date_code`、`lot_code`、`quantity`、`uom`
- **金额**：`unit_price`、`tax_included`、`tax_rate`、`net_amount`、`tax_amount`、`gross_amount`、`currency`、`exchange_rate`
- **期间**：`biz_date`、`period`
- **状态**：`basis_ready`、`basis_date`、`reconcile_status`、`invoice_status`、`settle_status`、`reconciled_qty`、`invoiced_amount`、`settled_amount`、`statement_id`、`version`

关键约束：

- 唯一键 `(source_type, source_line_id)`：保证同一业务行只投影一次。
- 索引 `(direction, partner_id, period, biz_date)`：对账池查询。
- 索引 `(direction, reconcile_status, basis_ready, period)`：月末取数。
- `statement_id` 索引：反查对账单。

### 2. 其他表

| 表 | 作用 | 关键约束 |
|---|---|---|
| `fin_profile` | 结算档案 | 唯一 `(partner_type, partner_id, direction)` |
| `fin_statement` | 对账单 | 唯一 `statement_no`；唯一 `active_key` |
| `fin_statement_item` | 对账单明细快照 | 索引 `(statement_id, open_item_id)` |
| `fin_discrepancy` | 差异单 | 索引 `(statement_id, status)` |
| `fin_invoice` | 发票 | 唯一 `invoice_no` |
| `fin_payment` | 收付款单 | 唯一 `payment_no` |
| `fin_allocation` | 核销明细 | 索引 `(payment_id)`、`(target_type, target_id)` |
| `fin_period` | 会计期间 | 主键 `period` |

`fin_statement.active_key` 设计为：

```text
direction:partnerId:period:currency
```

作废时置 `NULL`，利用 MySQL 唯一索引对 NULL 不冲突的特性，允许同期间重新生成。

### 3. 与现有表的边界

- 不修改 `inbound_order_item` / `inbound_order_item_alloc` / `outbound_order_item` / `outbound_order_item_alloc` 的财务字段。
- 采购价从 `purchase_order_item` 读取，在投影时写入 `fin_open_item`。
- 销售价从 `sales_order_item` 读取；有效数量按 `quantity - cancelled_quantity` 口径，出库行按实际出库数量计算。
- 业务表的 `payment_status` 只作为展示缓存，财务表是唯一事实源；第一版不强制回写。

---

## 四、状态机

### 可对账项 `reconcile_status`

```text
PENDING_BASIS --签收/标记就绪--> OPEN
OPEN --生成草稿--> IN_STATEMENT --对方确认--> RECONCILED
IN_STATEMENT --对账单作废/释放--> OPEN
任意状态 --作废--> VOID
```

另外两个维度独立维护：

- `invoice_status`：`UNINVOICED → PARTIAL → INVOICED`
- `settle_status`：`UNSETTLED → PARTIAL → SETTLED`

不要把“对账、开票、收款”塞进一个状态字段。

### 对账单 `status`

```text
DRAFT --发送--> SENT --对方确认--> CONFIRMED
                  |  ↑
                  |  └--差异处理完-- DISPUTED
SENT --对方提出差异--> DISPUTED
CONFIRMED --登记发票--> INVOICED
INVOICED --部分核销--> PARTIAL_SETTLED --全部核销--> SETTLED --归档--> CLOSED
DRAFT / SENT --作废--> VOID
```

### 收付款单 `status`

```text
RECEIVED --部分核销--> PARTIAL_ALLOCATED --全部核销--> ALLOCATED
RECEIVED / PARTIAL_ALLOCATED --作废--> VOID
```

### 差异单 `status`

```text
OPEN → PROCESSING → RESOLVED / WAIVED / SUSPENDED
```

---

## 五、生成时机

### AP 应付

1. 采购订单创建，价格在 `purchase_order_item`。
2. 入库单 `COMPLETED`。
3. **在入库事务内**，按每条 `inbound_order_item_alloc` 生成一条 `fin_open_item`：
   - `direction = AP`
   - `source_type = INBOUND`
   - `source_line_id = inbound_order_item_alloc.id`
   - `basis_ready = 1`，`basis_date = inbound_time`
   - 单价、税率、含税标志直接取分配行快照（入库时已从采购明细固化）
4. 月末按供应商 + 期间生成对账单草稿。
5. 财务发给供应商，供应商确认或提差异。
6. 供应商开票，财务登记发票。
7. 财务付款，核销。

### AR 应收

1. 销售订单创建，价格在 `sales_order_item`。
2. 出库单 `COMPLETED`。
3. 按 `outbound_order_item_alloc` 生成 `fin_open_item`：
   - `direction = AR`
   - `source_type = OUTBOUND`
   - `source_line_id = outbound_order_item_alloc.id`
   - 数量取分配数量，单价、税率、金额直接取分配行快照
4. **是否要等客户签收**，由 `fin_profile.reconcile_basis` 决定：
   - `DELIVERED`：出库完成即可对账
   - `SIGNED`：需要签收单/回签后才把 `basis_ready` 置 1
5. 生成草稿 → 发送客户 → 客户确认 → 开票 → 收款 → 核销。

> 注意：定时任务不是用来“查询已签收未对账”的，那是一个列表查询。定时任务只负责自动生成草稿、巡检、提醒。

---

## 六、自动 vs 手工

| 操作 | 自动/手工 | 说明 |
|---|---|---|
| 入库完成投影 AP 可对账项 | 自动 | 与入库同一事务 |
| 出库完成投影 AR 可对账项 | 自动 | 出库写流程接入后 |
| 计算净额、税额、价税合计、期间 | 自动 | 投影时固化 |
| 月末生成对账单草稿 | 自动 | 按 `fin_profile.statement_day` |
| 汇总金额重算 | 自动 | 生成、增删明细后 |
| 一致性巡检 | 自动 | 业务行与财务行对账 |
| 账龄、逾期标记 | 自动 | 每日计算 |
| 到期/未确认提醒 | 自动 | 生成待办或消息 |
| 确认签收/标记就绪 | 手工 | `SIGNED` 模式下 |
| 预览草稿、剔除/补录明细 | 手工 | 财务复核 |
| 发送对账单 | 手工 | 打印/邮件/门户 |
| 录入客户回签 | 手工 | 上传签字件 |
| 登记差异、处理差异 | 手工 | 数量/价格/批次/质量 |
| 登记发票、红冲 | 手工 | 与税务系统对接可后置 |
| 登记收款/付款 | 手工/导入 | 银行流水 |
| 核销、反核销、尾差处理 | 手工 | 系统可给建议 |
| 月结关账、反关账 | 手工 | 权限控制 |
| 作废对账单/收付款单 | 手工 | 留痕，不物理删除 |

---

## 七、接口清单

### 可对账项

- `POST /api/fin/open-item/list`
- `POST /api/fin/open-item/summary`
- `POST /api/fin/open-item/mark-ready`
- `POST /api/fin/open-item/rebuild`（管理员，历史数据回填）
- `GET /api/fin/open-item/{id}`

### 对账单

- `POST /api/fin/statement/preview`（只看会纳入哪些，不落库）
- `POST /api/fin/statement/generate`
- `POST /api/fin/statement/list`
- `GET /api/fin/statement/{id}`
- `POST /api/fin/statement/{id}/send`
- `POST /api/fin/statement/{id}/confirm`
- `POST /api/fin/statement/{id}/void`
- `POST /api/fin/statement/{id}/discrepancy`
- `POST /api/fin/statement/{id}/discrepancy/{did}/resolve`
- `POST /api/fin/statement/{id}/invoice`
- `POST /api/fin/statement/{id}/invoice/{iid}/red`
- `GET /api/fin/statement/{id}/export`

### 收付款与核销

- `POST /api/fin/payment/create`
- `POST /api/fin/payment/list`
- `GET /api/fin/payment/{id}`
- `POST /api/fin/payment/{id}/void`
- `POST /api/fin/clearing/suggest`
- `POST /api/fin/clearing/allocate`
- `POST /api/fin/clearing/unallocate`
- `POST /api/fin/clearing/write-off`

### 档案、报表、期间

- `GET /api/fin/profile/list`
- `POST /api/fin/profile/save`
- `POST /api/fin/report/aging`
- `POST /api/fin/report/statement-summary`
- `POST /api/fin/period/close`
- `POST /api/fin/period/reopen`

---

## 八、页面清单

1. **财务工作台**
   待生成、待发送、待确认、有差异、逾期未收/未付、待核销。

2. **可对账项池**
   AR/AP 两个页签，按伙伴、期间、状态、单据号筛选；支持批量标记就绪。

3. **对账单列表**
   状态、伙伴、期间、金额、发送/确认时间、逾期天数。

4. **对账单详情**
   表头 + 明细快照 + 差异 + 发票 + 核销记录 + 操作日志。

5. **差异处理**
   差异类型、责任方、差异数量/金额、处理方式。

6. **发票管理**
   蓝票/红票、关联对账单、开票金额、未开票余额。

7. **收付款单**
   到账登记、银行流水号、已核销/未核销金额。

8. **核销工作台**
   左侧收付款单，右侧该伙伴已对账可核销项；支持建议分配、部分核销、尾差处理。

9. **账龄分析**
   按伙伴、期间、账龄区间汇总。

10. **结算档案**
    账期、对账日、付款日、对账依据、是否自动生成、币种。

11. **期间管理**
    月结、关账、反关账。

12. **模板配置**（P2）
    字段映射、分组、排序、输出格式；模板是配置，不是每个客户一套文件。

---

## 九、关键流程

### AP 应付闭环

```text
采购订单 → 入库完成 → 生成 AP 可对账项
→ 月末生成草稿 → 财务复核 → 发送供应商
→ 供应商确认 / 提差异 → 差异处理
→ 供应商开票 → 登记发票
→ 财务付款 → 核销
→ 对账单 CLOSED
```

### AR 应收闭环

```text
销售订单 → 出库完成 → 客户签收（可配置）
→ 生成 AR 可对账项 → 生成草稿
→ 发送客户 → 客户确认 / 提差异
→ 开票 → 客户付款 → 核销
→ 对账单 CLOSED
```

### 核销规则

1. 只有 `reconcile_status = RECONCILED` 的可对账项才能核销。
2. 一笔收付款可核销多条可对账项；一条可对账项也可分多次核销。
3. 核销金额 ≤ 可对账项未结金额，且 ≤ 收付款单未分配金额。
4. 支持部分核销、反核销、尾差处理。
5. 尾差记入 `fin_allocation.diff_type`，不篡改原对账金额。
6. 外币时记录 `exchange_rate`，汇兑损益单独记差异。

### 差异处理

- 数量差异：补发货、退货、下期调整。
- 价格差异：补价、折让、下期调整。
- 批次/质量差异：退货、换货、索赔。
- 重复/跨期：作废或转入下期。

差异未关闭前，对账单不能确认。

### 最终状态

一张对账单完全闭环后：

- `fin_open_item`：`RECONCILED` + `INVOICED` + `SETTLED`，`settled_amount = gross_amount`
- `fin_statement`：`CLOSED`
- `fin_invoice`：`ISSUED`（或已红冲）
- `fin_payment`：`ALLOCATED`，`allocated_amount = amount`
- `fin_discrepancy`：`RESOLVED` / `WAIVED`
- 业务单据状态不变，财务状态独立

---

## 十、定时任务

| 任务 | 频率 | 做什么 |
|---|---|---|
| `FinStatementJob` | 每月对账日 01:05 | 按 `fin_profile` 自动生成草稿 |
| `FinConsistencyJob` | 每日 02:30 | 业务行与财务行数量/金额巡检 |
| `FinAgingJob` | 每日 03:00 | 计算账龄、逾期天数 |
| `FinReminderJob` | 每日 09:00 | 未确认、到期未付提醒 |

共同要求：

- 分布式锁，避免多实例重复执行。
- 按伙伴逐个事务，单个失败不影响其他伙伴。
- 任务只做“自动生成/检查/提醒”，不做人工确认类操作。
- 所有任务可重跑，结果幂等。

---

## 十一、并发与幂等

1. `fin_open_item` 唯一键 `(source_type, source_line_id)` + `INSERT IGNORE`。
2. 对账单 `active_key` 唯一，作废置 `NULL`。
3. 草稿生成时，可对账项用条件更新 `OPEN → IN_STATEMENT`，更新影响行数为 0 就跳过。
4. 核销时对 `fin_payment` 和 `fin_open_item` 加 `FOR UPDATE` 行锁。
5. 核销金额校验：`settled_amount <= gross_amount`，`allocated_amount <= amount`。
6. 单据号在业务事务内生成，沿用 `DocumentNumberService` 的 `Propagation.MANDATORY`。
7. 已关账期间禁止新增、修改、作废。
8. 对账单、收付款单使用 `version` 乐观锁；关键状态更新必须检查影响行数。
9. 所有“删除”改为状态 `VOID`，保留审计链。

---

## 十二、与现有代码的接入点

1. `InboundOrderServiceImpl.createInboundOrder`
   在保存完入库明细后、同一事务内调用财务投影，生成 AP 可对账项。

2. `DocumentNumberService.Kind`
   增加：
   - `STATEMENT_AR / STATEMENT_AP` → `fin_statement.statement_no`
   - `RECEIPT / PAYMENT` → `fin_payment.payment_no`

3. 出库写流程
   当前 `outbound_order` 只有列表查询，没有新增/确认/扣库存。AR 要真正闭环，需要先补齐出库写入和签收；第一版可先用现有出库数据 + 手工标记就绪。

4. 历史数据
   不写增量 SQL，增加管理端 `POST /api/fin/open-item/rebuild`，扫描已完成入库/出库，调用同一投影逻辑，幂等回填。

5. DDL 位置
   建议把 `fin_*` 建表语句追加到 `glw.sql` 末尾，保持“glw.sql + full_data.sql 全量重建”的约定；历史库用 rebuild 接口回填，不维护增量脚本。

6. mock 模式
   财务 Service、Controller、Job 全部加 `@Profile("!mock")`，与现有模块一致。

---

## 十三、建议的落地顺序

### 第一阶段：核心闭环（AR + AP 一起）

- `fin_*` 建表、索引、枚举/常量
- 实体、Mapper
- 入库投影 AP、出库投影 AR
- 可对账项池：列表、汇总、标记就绪
- 对账单：预览、生成、列表、详情、发送、确认、作废
- 收付款：登记、列表、建议核销、核销、反核销
- 历史数据 rebuild 接口
- 一致性巡检任务
- 基础页面：工作台、可对账池、对账单、核销台

### 第二阶段：差异、发票、报表

- 差异单登记与处理
- 发票登记、红冲
- 尾差处理
- 账龄分析
- 期间关账/反关账
- 到期提醒
- Excel/PDF 导出

### 第三阶段：行业增强

- 客户/供应商对账单模板配置
- 门户/邮件/电子签
- 多币种与汇兑损益
- VMI/寄售
- 退换货、返利、价格保护
- 银行流水自动导入与匹配

---

## 十四、需要确认的 6 个点

1. **AR 对账依据**：出库完成即可，还是必须客户签收？建议做成 `fin_profile.reconcile_basis` 可配置，默认 AR 用 `SIGNED`，AP 用 `INBOUND`。
2. **出库写流程**：当前只有列表查询，没有出库确认和扣库存。第一版是先用现有数据 + 手工标记就绪，还是先补出库写入？
3. **多币种**：第一版是否只做 CNY？如果涉及美元采购/销售，需要现在就加 `currency` 和 `exchange_rate`。
4. **价税口径**：现有数据全部含税、税率 `NULL`。第一版是按“含税价、税额 0”处理，还是要补税率并拆税？
5. **历史数据回填**：用管理端 rebuild 接口，还是写一次性 SQL？
6. **对账单输出**：第一版固定模板 + Excel 导出即可，客户定制模板放第三阶段，是否可以？
