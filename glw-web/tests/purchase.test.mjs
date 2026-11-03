import test from 'node:test'
import assert from 'node:assert/strict'
import {
  orderTotals,
  receiptTotals,
  receiptLineQuantity,
  receiptProducts,
  receiptOrders,
  purchaseLineProductText,
  products,
  validateReceipt,
  remainingQuantity,
  pendingDeliveryQuantity,
  returnableQuantity,
  chineseMoney,
  nextPurchaseCode,
} from '../src/domain/purchase.ts'
const order = {
  id: 'o1',
  orderStatus: '执行中',
  receiptStatus: '未收货',
  warehouse: '杭州仓',
  discount: 0.01,
  lines: [
    { id: 'l1', productId: 'p1', quantity: 3, price: 0.1, tax: 13 },
    { id: 'l2', productId: 'p2', quantity: 2, price: 0.2, tax: 13 },
  ],
}
const state = () => ({
  orders: [structuredClone(order)],
  receipts: [],
  returns: [],
  payments: [],
  logs: [],
})
const receipt = () => ({
  id: 'r1',
  orderId: 'o1',
  warehouse: '杭州仓',
  lines: [{ lineId: 'l1', arrived: 2, rejected: 1, location: 'HZ-A01',
    batches: [{id: 'b1', dateCode: '2618', lotCode: 'LOT-A', quantity: 2}] }],
  quality: '存在不合格品',
  inspector: '测试',
  inspectedAt: '2026-09-24 09:00',
  confirmed: true,
  keeper: '测试',
  receivedAt: '2026-09-24 10:00',
})
test('one receipt allocates quantities and costs to multiple purchase orders independently', () => {
  const first = { ...structuredClone(order), supplierId: '1', discount: 0, lines: [{ id: 'a', productId: '6', model: 'ET5907', quantity: 5000, price: 0.85, tax: 0 }] }
  const second = { ...structuredClone(first), id: 'o2', lines: [{ ...first.lines[0], id: 'b', quantity: 4000, price: 0.9 }] }
  const combined = { ...receipt(), orderIds: ['o1', 'o2'], quality: '合格', lines: [
    { lineId: 'a', arrived: 2000, rejected: 0, batches: [{ id: 'a1', dateCode: '2610', lotCode: 'A', quantity: 2000 }] },
    { lineId: 'b', arrived: 3000, rejected: 0, batches: [{ id: 'b1', dateCode: '2610', lotCode: 'A', quantity: 3000 }] },
  ] }
  const s = { ...state(), orders: [first, second], receipts: [combined] }
  assert.deepEqual(receiptTotals(combined, first), { quantity: 2000, total: 1700 })
  assert.deepEqual(receiptTotals(combined, second), { quantity: 3000, total: 2700 })
  assert.deepEqual(receiptTotals(combined, [first, second]), { quantity: 5000, total: 4400 })
  assert.equal(pendingDeliveryQuantity(second, second.lines[0], s.receipts), 1000)
  assert.equal(remainingQuantity(s, 'o2', 'b'), 1000)
  assert.equal(returnableQuantity(s, 'o2', 'b'), 3000)
  assert.equal(receiptOrders(combined, s.orders).length, 2)
  const grouped = receiptProducts(combined, s.orders)
  assert.equal(grouped.length, 1)
  assert.equal(grouped[0].quantity, 5000)
  assert.equal(grouped[0].lines.length, 2)
  assert.equal(validateReceipt(combined, s), '')
  second.supplierId = '2'
  assert.match(validateReceipt(combined, s), /同一供应商/)
  second.supplierId = '1'
  combined.orderIds = ['o1']
  assert.match(validateReceipt(combined, s), /不属于/)
})
test('receipt product information uses backend purchase details instead of demo product IDs', () => {
  const line = { id: '11', productId: '8', quantity: 100, price: 1, tax: 13,
    brand: 'TI', model: 'TPS63020DSJR', packaging: 'WLCSP12(0.4 Pitch)' }
  assert.equal(purchaseLineProductText(line, 'brand'), 'TI')
  assert.equal(purchaseLineProductText(line, 'model'), 'TPS63020DSJR')
  assert.equal(purchaseLineProductText(line, 'packaging'), 'WLCSP12(0.4 Pitch)')
  assert.equal(purchaseLineProductText({ ...line, productId: products[0].id }, 'model'), line.model)
  assert.equal(purchaseLineProductText({ ...line, productId: products[0].id, model: undefined }, 'model'), products[0].model)
  assert.equal(purchaseLineProductText(undefined, 'model'), '')
})

test('pending delivery counts only completed receipts for the exact purchase line', () => {
  const s = state()
  s.receipts.push(receipt(), { ...receipt(), id: 'r2', confirmed: false },
    { ...receipt(), id: 'other-order', orderId: 'other' },
    { ...receipt(), id: 'r3', lines: [{ lineId: 'l2', arrived: 1, rejected: 0 }] })
  assert.equal(pendingDeliveryQuantity(order, order.lines[0], s.receipts), 2)
  assert.equal(pendingDeliveryQuantity(order, order.lines[1], s.receipts), 1)
  s.receipts.push({ ...receipt(), id: 'r4', lines: [{ lineId: 'l1', arrived: 1, rejected: 0 }] })
  assert.equal(pendingDeliveryQuantity(order, order.lines[0], s.receipts), 1)
})
test('pending delivery uses server totals regardless of loaded receipt pagination and preserves zero', () => {
  assert.equal(pendingDeliveryQuantity(order, { ...order.lines[0], pendingQuantity: 1 }, []), 1)
  assert.equal(pendingDeliveryQuantity(order, { ...order.lines[0], pendingQuantity: 0 }, []), 0)
  assert.equal(pendingDeliveryQuantity(order, order.lines[0], []), 3)
  assert.equal(pendingDeliveryQuantity(order, order.lines[0], [
    { ...receipt(), lines: [{ lineId: 'l1', arrived: 10, rejected: 0 }] },
  ]), 0)
})
test('receiving uses server pending totals without counting completed receipts twice', () => {
  const s = state()
  s.orders[0].receiptStatus = '部分收货'
  s.orders[0].lines[0].pendingQuantity = 2
  s.receipts.push(receipt())
  assert.equal(remainingQuantity(s, 'o1', 'l1'), 2)
  assert.equal(validateReceipt({ ...receipt(), id: 'new' }, s), '')
  s.receipts.push({ ...receipt(), id: 'pending', confirmed: false })
  assert.equal(remainingQuantity(s, 'o1', 'l1'), 1)
  assert.equal(remainingQuantity(s, 'o1', 'l1', 'pending'), 2)
  s.orders[0].receiptStatus = '未收货'
  assert.equal(validateReceipt({ ...receipt(), id: 'pending' }, s), '')
  s.orders[0].lines[0].pendingQuantity = 0
  s.receipts = []
  assert.equal(remainingQuantity(s, 'o1', 'l1'), 0)
})

test('receipt quantities show each delivery even after the purchase line is fully received', () => {
  const s = state()
  s.orders[0].lines[0].pendingQuantity = 0
  const first = receipt()
  const second = { ...receipt(), id: 'r2', lines: [
    { lineId: 'l1', arrived: 2, rejected: 0 },
    { lineId: 'l2', arrived: 1, rejected: 0 },
  ] }
  s.receipts.push(first, second)
  assert.equal(remainingQuantity(s, 'o1', 'l1'), 0)
  assert.equal(receiptLineQuantity(first, 'l1'), 1)
  assert.equal(receiptLineQuantity(second, 'l1'), 2)
  assert.equal(receiptLineQuantity(second, 'l2'), 1)
  assert.equal(receiptLineQuantity(first, 'l2'), 0)
})

test('discount allocation keeps cents balanced and excludes VAT correctly', () => {
  const t = orderTotals(order)
  assert.equal(t.gross, 0.7)
  assert.equal(t.total, 0.69)
  assert.equal(Math.round(t.details.reduce((a, b) => a + b.total, 0) * 100), 69)
  assert.equal(
    orderTotals({ discount: 13, lines: [{ quantity: 1, price: 113, tax: 13 }] }).net,
    88.5,
  )
})
test('receipts exclude rejected quantities, reserve pending goods, prevent over-receiving and allow self edit', () => {
  const s = state(),
    r = receipt()
  assert.equal(validateReceipt(r, s), '')
  s.receipts.push(r)
  assert.equal(remainingQuantity(s, 'o1', 'l1'), 2)
  assert.equal(remainingQuantity(s, 'o1', 'l1', 'r1'), 3)
  assert.equal(receiptTotals(r, order).quantity, 1)
  const another = {
    ...r,
    id: 'r2',
    lines: [{ lineId: 'l1', arrived: 3, rejected: 0, location: 'A',
      batches: [{id: 'b2', dateCode: '2618', lotCode: 'LOT-B', quantity: 3}] }],
  }
  assert.match(validateReceipt(another, s), /不能超过待入库/)
  s.receipts[0].confirmed = false
  assert.equal(returnableQuantity(s, 'o1', 'l1'), 0)
  assert.equal(remainingQuantity(s, 'o1', 'l1'), 2)
})
test('invalid quality, quantities, approval and confirmation cannot be submitted', () => {
  const s = state(),
    r = receipt()
  r.quality = '合格'
  assert.match(validateReceipt(r, s), /不合格/)
  r.quality = '存在不合格品'
  r.lines[0].rejected = 3
  assert.match(validateReceipt(r, s), /不合格数量/)
  r.lines[0].rejected = 0
  r.lines[0].location = ''
  assert.equal(validateReceipt(r, s), '')
  r.inspector = ''; r.inspectedAt = ''
  assert.equal(validateReceipt(r, s), '')
  r.receivedAt = ''
  assert.match(validateReceipt(r, s), /入库员和入库时间/)
  r.receivedAt = '2026-09-24 10:00'
  s.orders[0].orderStatus = '待审核'
  assert.match(validateReceipt(r, s), /可入库/)
})
test('receipt quantities are summed per date and lot, with missing codes and duplicate combinations rejected', () => {
  const s = state(), r = receipt()
  s.orders[0].lines[0].quantity = 1000
  r.lines[0].rejected = 0
  r.lines[0].batches = [
    {id: 'b1', dateCode: '2618', lotCode: 'LOT-A', quantity: 300},
    {id: 'b2', dateCode: '2619', lotCode: 'LOT-A', quantity: 200},
  ]
  assert.equal(validateReceipt(r, s), '')
  assert.equal(r.lines[0].arrived, 500)
  r.lines[0].batches[1].dateCode = '2618'
  assert.match(validateReceipt(r, s), /不可重复/)
  r.lines[0].batches[1].dateCode = ''
  assert.match(validateReceipt(r, s), /日期码/)
  r.lines[0].batches[1].dateCode = '2619'
  r.lines[0].batches[1].lotCode = ''
  assert.match(validateReceipt(r, s), /批次号/)
  r.lines[0].batches[1].lotCode = 'LOT-A'
  s.orders[0].lines[0].quantity = 400
  assert.match(validateReceipt(r, s), /待入库/)
})

test('every receipt batch requires a positive integer quantity', () => {
  for (const quantity of [undefined, null, NaN, 0, -1, 1.5]) {
    const r = receipt()
    r.lines[0].batches[0].quantity = quantity
    assert.match(validateReceipt(r, state()), /批次入库数量/)
  }
  const r = receipt()
  r.lines[0].batches = []
  assert.match(validateReceipt(r, state()), /批次入库数量/)
})

test('returns never count unconfirmed receipts or already returned goods', () => {
  const s = state()
  s.receipts.push(receipt())
  assert.equal(returnableQuantity(s, 'o1', 'l1'), 1)
  s.returns.push({ orderId: 'o1', lineId: 'l1', quantity: 1 })
  assert.equal(returnableQuantity(s, 'o1', 'l1'), 0)
})
test('document numbers skip existing sequences and amount uppercase handles group boundaries', () => {
  assert.equal(
    nextPurchaseCode('GL', [{ code: 'GL20260924003' }, { code: 'GL20260923099' }], '2026-09-24'),
    'GL20260924004',
  )
  for (const [n, s] of [
    [0, '零元整'],
    [11000, '壹万壹仟元整'],
    [10001, '壹万零壹元整'],
    [100000001, '壹亿零壹元整'],
    [12.05, '壹拾贰元零伍分'],
  ])
    assert.equal(chineseMoney(n), s)
})
