import test from 'node:test'
import assert from 'node:assert/strict'
import { pendingInboundProducts, matchingInboundOrders, inboundAllocationColumns, buildMultiInboundLines } from '../src/domain/multiInbound.ts'

const order = (id, supplierId = 's1', orderStatus = '执行中') => ({ id, code: `PO-${id}`, supplierId, orderStatus, receiptStatus: '未收货' })
const entry = (order, id, productId, pending, price = 1) => ({ order, line: { id, productId, model: `Product-${productId}`, brand: 'Brand', packaging: 'QFN', price }, pending })
const entries = () => [entry(order('1'), '11', 'A', 100, 0.85), entry(order('1'), '12', 'B', 60), entry(order('2'), '21', 'A', 80, 0.9), entry(order('3'), '31', 'C', 70), entry(order('4'), '41', 'A', 0), entry(order('5', 's1', '已取消'), '51', 'D', 100)]
const products = () => [{ productId: 'A', model: 'Product-A', batches: [
  { id: 'b1', dateCode: ' 2610 ', lotCode: ' LOT-A ', quantity: 50, quantities: { '11': 30, '21': 20, '31': 999 } },
  { id: 'b2', dateCode: '2611', lotCode: 'LOT-B', quantity: 40, quantities: { '11': 40, '21': 0 } },
] }]

test('product picker deduplicates only pending products and order candidates follow selected products', () => {
  const data = entries()
  assert.deepEqual(pendingInboundProducts(data).map(p => [p.productId, p.pending]), [['A', 180], ['B', 60], ['C', 70]])
  assert.deepEqual(matchingInboundOrders(data, ['A']).map(x => x.order.id), ['1', '2'])
  assert.deepEqual(matchingInboundOrders(data, ['B']).map(x => x.order.id), ['1'])
  assert.deepEqual(matchingInboundOrders(data, []).map(x => x.order.id), [])
  assert.deepEqual(inboundAllocationColumns(data, 'B', ['1', '2']).map(x => x.line.id), ['12'])
})

test('batch cross order allocation omits zero and foreign-product combinations and retains each order cost', () => {
  const result = buildMultiInboundLines(products(), entries(), ['1', '2'])
  assert.deepEqual(result.map(line => [line.lineId, line.arrived, line.unitPrice]), [['11', 70, 0.85], ['21', 20, 0.9]])
  assert.equal(result[0].batches.length, 2)
  assert.equal(result[1].batches.length, 1)
  assert.equal(result[0].batches[0].dateCode, '2610')
  assert.equal(result[0].batches[0].lotCode, 'LOT-A')
  assert.ok(result.every(line => line.productId === 'A'))
})

test('one batch can serve several orders and an order can serve several selected products', () => {
  const selected = [...products(), { productId: 'B', model: 'Product-B', batches: [{ id: 'b3', dateCode: '2610', lotCode: 'LOT-C', quantity: 10, quantities: { '12': 10, '21': 500 } }] }]
  const result = buildMultiInboundLines(selected, entries(), ['1', '2'])
  assert.deepEqual(result.map(line => [line.lineId, line.arrived]), [['11', 70], ['21', 20], ['12', 10]])
  selected[0].batches[0].dateCode = 'NEW'
  assert.equal(buildMultiInboundLines(selected, entries(), ['1', '2'])[1].batches[0].dateCode, 'NEW')
})

test('validates aggregate pending quantity across batches, not only each allocation cell', () => {
  const selected = products()
  selected[0].batches[1].quantities['11'] = 80
  assert.throws(() => buildMultiInboundLines(selected, entries(), ['1', '2']), /分配合计不能超过/)
})

test('rejects missing or duplicate batch codes, unallocated batches and empty selected orders', () => {
  const missing = products(); missing[0].batches[0].dateCode = ''
  assert.throws(() => buildMultiInboundLines(missing, entries(), ['1', '2']), /Date Code/)
  const duplicate = products(); duplicate[0].batches[1].dateCode = '2610'; duplicate[0].batches[1].lotCode = 'lot-a'
  assert.throws(() => buildMultiInboundLines(duplicate, entries(), ['1', '2']), /不可重复/)
  const emptyBatch = products(); emptyBatch[0].batches[1].quantities = {}
  assert.throws(() => buildMultiInboundLines(emptyBatch, entries(), ['1', '2']), /未分配/)
  const emptyOrder = products(); emptyOrder[0].batches[0].quantities['21'] = undefined
  emptyOrder[0].batches[0].quantity = 30
  assert.throws(() => buildMultiInboundLines(emptyOrder, entries(), ['1', '2']), /PO-2 尚未分配/)
  assert.throws(() => buildMultiInboundLines(products(), entries(), ['1']), /至少两张/)
  assert.throws(() => buildMultiInboundLines(products(), entries(), ['1', '3']), /至少两张/)
})

test('batch receipt quantity is required and allocations must match the independently entered quantity', () => {
  for (const quantity of [undefined, 0, -1, 1.5, 100000001]) {
    const selected = products(); selected[0].batches[0].quantity = quantity
    assert.throws(() => buildMultiInboundLines(selected, entries(), ['1', '2']), /入库数量须为/)
  }
  const over = products(); over[0].batches[0].quantity = 49
  assert.throws(() => buildMultiInboundLines(over, entries(), ['1', '2']), /不能超过本次入库数量 49/)
  const under = products(); under[0].batches[0].quantity = 51
  assert.throws(() => buildMultiInboundLines(under, entries(), ['1', '2']), /尚有 1 件未分配/)
})

test('rejects mixed suppliers, noninteger allocations and products without a selected matching order', () => {
  const mixed = entries(); mixed[2].order.supplierId = 's2'
  assert.throws(() => buildMultiInboundLines(products(), mixed, ['1', '2']), /同一供应商/)
  const fractional = products(); fractional[0].batches[0].quantities['11'] = 0.5
  assert.throws(() => buildMultiInboundLines(fractional, entries(), ['1', '2']), /整数/)
  const unmatched = [...products(), { productId: 'C', model: 'Product-C', batches: [{ id: 'c', dateCode: '2610', lotCode: 'C', quantities: {} }] }]
  assert.throws(() => buildMultiInboundLines(unmatched, entries(), ['1', '2']), /没有可分配的采购单/)
})
