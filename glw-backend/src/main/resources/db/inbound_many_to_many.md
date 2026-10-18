# 入库单与采购单多对多

`inbound_order` 表示一次实际收货，使用独立的 `inbound_order_no`，不再存储采购单 ID、采购单号。
`inbound_order_purchase_order` 保存入库单与采购单的关联，每一对单据只有一条关联。
`inbound_order_item` 继续按采购明细记录分配数量；`inbound_order_batch_item` 在此基础上按 Date Code、Lot Code 细分。相同产品来自不同采购单时，分别沿用各自约定单价，保留独立库存批次。

同一入库单支持同一供应商的多张采购单。创建时校验每张采购单、每条采购明细的待交数量，并在一个事务内写入关联、入库明细和库存、更新采购单状态。

## 更新已有数据库

先备份数据库，再依次执行：

1. `migrate_inbound_many_to_many.sql`：迁移已有单据关系、生成入库单号、移除入库单头的采购字段。可重复执行。
2. `merge_inbound_20261010_product6.sql`：合并已确认的产品 6 在 2026-10-10 收货的 5,000、1,000、3,000 件到 `CGRK-000014`。有历史明细校验，可重复执行；其他产品先保留原单。库存 ID、库存批次号、成本与数量均不改变。

全新数据库执行 `glw.sql`、`full_data.sql` 即可，后者已包含上述确认的关系和合并结果。`glw.sql` 会重新建表，不用于迁移已有数据库。

## 接口与页面

- `POST /api/inbound-order`：`purchaseOrderIds` 为采购单 ID 数组，`items` 按 `purchaseOrderItemId` 分配批次及数量。
- `POST /api/inbound-order/list`：每条入库单返回 `purchaseOrders` 列表；按采购单筛选仍返回完整入库单。
- `GET /api/inbound-order/{id}`：整张入库单详情。
- `GET /api/inbound-order/{id}/purchase-orders/{purchaseOrderId}`：仅返回该采购单在本次收货中的分配明细。

入库列表的采购单号逐行展示、分别可点击。入库详情先按产品汇总，再展示采购单分配、Date Code、Lot Code 和入库数量。

采购订单行内“入库”继续使用原单笔入库弹窗，不显示采购单分配列。列表工具栏“多单入库”打开独立弹窗，选择至少两张同供应商采购单，按产品填写各采购单的批次和入库数量。
