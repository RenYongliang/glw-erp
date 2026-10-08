import test from 'node:test'
import assert from 'node:assert/strict'
import {
  orderTotals,
  receiptTotals,
  validateReceipt,
  remainingQuantity,
  returnableQuantity,
  chineseMoney,
  nextPurchaseCode,
} from '../src/domain/purchase.ts'
const order = {
  id: 'o1',
  status: '审批通过',
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
  lines: [{ lineId: 'l1', arrived: 2, rejected: 1, location: 'HZ-A01' }],
  quality: '存在不合格品',
  inspector: '测试',
  inspectedAt: '2026-09-24 09:00',
  confirmed: true,
  keeper: '测试',
  receivedAt: '2026-09-24 10:00',
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
    lines: [{ lineId: 'l1', arrived: 3, rejected: 0, location: 'A' }],
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
  s.orders[0].status = '待审批'
  assert.match(validateReceipt(r, s), /已审批/)
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
    nextPurchaseCode('CGDD', [{ code: 'CGDD260924-03' }, { code: 'CGDD260923-99' }], '2026-09-24'),
    'CGDD260924-04',
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
