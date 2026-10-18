# 入库单与采购单多对多

`inbound_order` 表示一次实际收货，使用独立的 `inbound_order_no`，不再存储采购单 ID、采购单号。
`inbound_order_purchase_order` 保存入库单与采购单的关联，每一对单据只有一条关联。
`inbound_order_item` 继续按采购明细记录分配数量；`inbound_order_batch_item` 在此基础上按 Date Code、Lot Code 细分。相同产品来自不同采购单时，分别沿用各自约定单价，保留独立库存批次。

同一入库单支持同一供应商的多张采购单。创建时校验每张采购单、每条采购明细的待交数量，并在一个事务内写入关联、入库明细和库存、更新采购单状态。

## 数据库初始化

开发阶段统一依次执行 `glw.sql`、`full_data.sql`，重建表结构并导入基础数据，不维护增量脚本。`glw.sql` 已包含入库单、采购单关联表及独立单号字段；编号规则见 [document_numbers.md](document_numbers.md)。

`full_data.sql` 已包含确认的包裹合并结果：产品 6 在 2026-10-10 收货的 5,000、1,000、3,000 件统一归入 ID 为 14 的入库单 `IN20261010001`，分别分配给三个采购单；其他产品保留原单。

## 接口与页面

- `POST /api/inbound-order`：`purchaseOrderIds` 为采购单 ID 数组，`items` 按 `purchaseOrderItemId` 分配批次及数量。
- `POST /api/inbound-order/list`：每条入库单返回 `purchaseOrders` 列表；按采购单筛选仍返回完整入库单。
- `GET /api/inbound-order/{id}`：整张入库单详情。
- `GET /api/inbound-order/{id}/purchase-orders/{purchaseOrderId}`：仅返回该采购单在本次收货中的分配明细。

入库列表的采购单号逐行展示、分别可点击。入库详情先按产品汇总，再展示采购单分配、Date Code、Lot Code 和入库数量。

采购订单行内“入库”继续使用原单笔入库弹窗，不显示采购单分配列。列表工具栏“入库”打开独立的多单入库弹窗，先从未全部入库的采购明细中选择去重后的产品，按产品统一填写 Date Code、Lot Code 和本次批次入库数量。采购单候选列表随产品选择联动筛选，选择至少两张同供应商采购单后，按产品批次与包含该产品的采购明细生成数量分配表。留空或为 0 的组合不生成入库明细；同一采购明细的各批次合计不得超过待交数量，每个批次的分配合计不得超过预先填写的入库数量，保存时须分配完整。
