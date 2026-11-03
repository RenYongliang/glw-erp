import test from 'node:test'
import assert from 'node:assert/strict'
import { pendingOutboundProducts, matchingOutboundOrders, outboundAllocationColumns, buildMultiOutboundLines } from '../src/domain/multiOutbound.ts'

const order = (id, customerId = 'c1', orderStatus = '执行中') => ({ id, code: `SO-${id}`, customerId, orderStatus, deliveryStatus: '未发货', signed: '2026-10-01' })
const entry = (order, id, productId, pending) => ({ order, line: { id, productId, model: productId, brand: 'Brand', packaging: 'QFN' }, pending })
const entries = () => [entry(order('1'), '11', 'A', 100), entry(order('1'), '12', 'B', 60),
  entry(order('2'), '21', 'A', 80), entry(order('3', 'c2'), '31', 'A', 70),
  entry(order('4'), '41', 'A', 0), entry(order('5', 'c1', '已取消'), '51', 'C', 100)]
const products = () => [{ productId: 'A', model: 'A', pending: 250, batches: [
  { inventoryId: '101', batchNo: 'BAT-101', quantity: 70, unitCost: 0.075, selected: true, outboundQuantity: 50, quantities: { '11': 30, '21': 20, '12': 999 } },
  { inventoryId: '102', batchNo: 'BAT-102', quantity: 100, unitCost: 0.09, selected: true, outboundQuantity: 40, quantities: { '11': 40 } },
  { inventoryId: '103', batchNo: 'BAT-103', quantity: 10, unitCost: 0.1, selected: false, outboundQuantity: 10, quantities: { '11': 10 } },
] }]

test('deduplicates pending products and matches only eligible order/product combinations', () => {
  assert.deepEqual(pendingOutboundProducts(entries()).map(p => [p.productId, p.pending]), [['A', 250], ['B', 60]])
  assert.deepEqual(matchingOutboundOrders(entries(), ['A']).map(e => e.order.id), ['1', '2', '3'])
  assert.deepEqual(outboundAllocationColumns(entries(), 'B', ['1', '2']).map(e => e.line.id), ['12'])
  assert.deepEqual(matchingOutboundOrders(entries(), []), [])
})
test('one batch serves multiple sales orders and one sales item consumes multiple batches at their own cost', () => {
  const result = buildMultiOutboundLines(products(), entries(), ['1', '2'])
  assert.deepEqual(result.map(l => [l.orderId, l.lineId, l.arrived]), [['1', '11', 70], ['2', '21', 20]])
  assert.deepEqual(result[0].batches.map(b => [b.inventoryId, b.quantity, b.unitCost]), [['101', 30, 0.075], ['102', 40, 0.09]])
  assert.deepEqual(result[1].batches.map(b => [b.inventoryId, b.quantity]), [['101', 20]])
})
test('supports a single selected order and multiple products in a consolidated shipment', () => {
  const selected = products(); selected[0].batches[0].quantities = { '11': 50 }
  selected.push({ productId: 'B', model: 'B', batches: [{ inventoryId: '201', batchNo: 'BAT-201', quantity: 60, unitCost: 1,
    selected: true, outboundQuantity: 10, quantities: { '12': 10, '11': 500 } }] })
  assert.deepEqual(buildMultiOutboundLines(selected, entries(), ['1']).map(l => [l.lineId, l.arrived]), [['11', 90], ['12', 10]])
})
test('rejects mixed customers, missing orders, duplicate order ids, and unallocated orders', () => {
  assert.throws(() => buildMultiOutboundLines(products(), entries(), ['1', '3']), /同一客户/)
  for (const ids of [[], ['1', '1'], ['1', '999']]) assert.throws(() => buildMultiOutboundLines(products(), entries(), ids), /请选择/)
  const selected = products(); selected[0].batches[0].quantities = { '11': 50 }
  assert.throws(() => buildMultiOutboundLines(selected, entries(), ['1', '2']), /SO-2 尚未分配/)
})
test('requires independently entered positive integer batch quantity within real inventory', () => {
  for (const quantity of [undefined, 0, -1, 0.5, 100000001]) {
    const selected = products(); selected[0].batches[0].outboundQuantity = quantity
    assert.throws(() => buildMultiOutboundLines(selected, entries(), ['1', '2']), /出库数量须为/)
  }
  const selected = products(); selected[0].batches[0].quantity = 49
  assert.throws(() => buildMultiOutboundLines(selected, entries(), ['1', '2']), /不能超过可用库存/)
})
test('checks batch totals and per-order totals across all batches', () => {
  const over = products(); over[0].batches[0].outboundQuantity = 49
  assert.throws(() => buildMultiOutboundLines(over, entries(), ['1', '2']), /分配合计不能超过本次出库/)
  const under = products(); under[0].batches[0].outboundQuantity = 51
  assert.throws(() => buildMultiOutboundLines(under, entries(), ['1', '2']), /尚有 1 件未分配/)
  const orderOver = products(); orderOver[0].batches[1].outboundQuantity = 80; orderOver[0].batches[1].quantities = { '11': 80 }
  assert.throws(() => buildMultiOutboundLines(orderOver, entries(), ['1', '2']), /不能超过待发数量/)
})
test('rejects missing batches, duplicate inventory and malformed allocations', () => {
  const empty = products(); empty[0].batches.forEach(b => { b.selected = false })
  assert.throws(() => buildMultiOutboundLines(empty, entries(), ['1', '2']), /至少选择一个/)
  const duplicate = products(); duplicate[0].batches[1].inventoryId = '101'
  assert.throws(() => buildMultiOutboundLines(duplicate, entries(), ['1', '2']), /不能重复选择/)
  const fractional = products(); fractional[0].batches[0].quantities['11'] = 0.5
  assert.throws(() => buildMultiOutboundLines(fractional, entries(), ['1', '2']), /分配数量须为/)
  assert.throws(() => buildMultiOutboundLines([{ productId: 'B', model: 'B', batches: [] }, ...products()], entries(), ['2']), /没有可分配/)
})
