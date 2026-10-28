# 入库单与采购单多对多

`inbound_order` 表示一次实际收货，使用独立的 `inbound_order_no`，不再存储采购单 ID、采购单号。
`inbound_order_purchase_order` 保存入库单与采购单的关联，每一对单据只有一条关联。
`inbound_order_item` 按产品记录本次收货数量（同一产品每张入库单只有一行）；`inbound_order_item_alloc` 是“采购明细 × 外部批次”的分配行，同时保存 Date Code、Lot Code、采购单价、含税标识、税率、金额和币种快照。入库只有产品行、分配行两级明细，外部批次不再单独建表。相同产品来自不同采购单时，分别沿用各自约定单价。

外部批次（Date Code + Lot Code）只用于记录本次收货物理上来自哪些原厂批次；系统内部库存批次 `inventory.batch_no` 不跟随分配行，而是在同一入库单内按“产品 + Date Code + Lot Code + 含税单位成本”生成库存记录（含税成本不同必须拆开，因为一条库存记录只能保存一个单位成本），再按入库日期生成 `BAT + yyMMdd + 5 位产品 ID + 3 位当日序号`。内部批次以入库单为边界：同一天内多次入库（多张入库单）算多个批次，不跨入库单合并；同一入库单内才按上述属性汇总。同一外部批次分多次、不同日期入库时，同样各算一个内部批次。

`inventory.unit_cost` 统一为含税成本。含税报价直接使用原单价；未税报价按 `单价 × (1 + 税率 / 100)` 换算，四舍五入保留四位小数后参与库存分组。未税报价必须填写有效税率（显式填写 0 表示零税率），缺失税率不能默认按零处理；含税报价无需税率也能确定含税成本。采购明细与入库分配行保留原始单价、是否含税、税率和金额快照，不改写为换算价格。出库成本快照沿用库存的含税成本。

同一入库单支持同一供应商的多张采购单。创建时校验每张采购单、每条采购明细的待交数量，并在一个事务内写入关联、入库明细和库存、更新采购单状态。

## 数据库初始化

开发阶段统一依次执行 `glw.sql`、`full_data.sql`，重建表结构并导入基础数据，不维护增量脚本。`glw.sql` 已包含入库单、采购单关联表及独立单号字段；编号规则见 [document_numbers.md](document_numbers.md)。

`full_data.sql` 已包含确认的包裹合并结果：产品 6 在 2026-10-10 收货的 5,000、1,000、3,000 件统一归入 ID 为 14 的入库单 `IN20261010001`，分别分配给三个采购单；其他产品保留原单。

## 接口与页面

- `POST /api/inbound-order`：`purchaseOrderIds` 为采购单 ID 数组，`allocations` 每项为 `purchaseOrderItemId + dateCode + lotCode + allocatedQuantity`；产品行由服务端按 `product_id` 汇总生成。
- `POST /api/outbound-order`：与入库对称，`salesOrderIds` 为销售单 ID 数组，`allocations` 按 `salesOrderItemId` 分配库存批次；产品行由服务端按 `product_id` 汇总生成。
- `POST /api/inbound-order/list`：每条入库单返回 `purchaseOrders` 列表；按采购单筛选仍返回完整入库单。
- `GET /api/inbound-order/{id}`：整张入库单详情。
- `GET /api/inbound-order/{id}/purchase-orders/{purchaseOrderId}`：仅返回该采购单在本次收货中的分配明细。

入库列表的采购单号逐行展示、分别可点击。入库详情先按产品汇总，再展示采购单分配、Date Code、Lot Code 和入库数量。

采购订单行内“入库”继续使用原单笔入库弹窗，不显示采购单分配列。列表工具栏“入库”打开独立的多单入库弹窗，先从未全部入库的采购明细中选择去重后的产品，按产品统一填写 Date Code、Lot Code 和本次批次入库数量。采购单候选列表随产品选择联动筛选，选择至少两张同供应商采购单后，按产品批次与包含该产品的采购明细生成数量分配表。留空或为 0 的组合不生成入库明细；同一采购明细的各批次合计不得超过待交数量，每个批次的分配合计不得超过预先填写的入库数量，保存时须分配完整。
