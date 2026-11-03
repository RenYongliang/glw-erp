import test from 'node:test'
import assert from 'node:assert/strict'
import { createDeliveryNote } from '../src/domain/deliveryNote.ts'
import { deliveryNoteHtml } from '../src/domain/deliveryNoteHtml.ts'
import { toSalesShipment } from '../src/services/salesApiData.ts'

const order = (id, purchaseNumber, customerId = '9') => ({ id, customerId, customerName: '真实客户', code: 'SO-' + id,
  purchaseNumber, address: '旧地址', owner: '旧经办人', lines: [{ id: 'line-' + id, productId: '99001',
    model: 'ET5907', quantity: 1000 }] })
const shipping = () => ({ address: '本次收货地址', recipient: '收货人', phone: '13800000000', sender: '发货人',
  senderPhone: '', method: '快递物流', carrier: '顺丰', trackingNumber: 'SF123456', purchaseNumber: 'CO-1 / CO-2', remark: '包装要求 <易碎>' })
const shipment = () => ({ id: 'receipt', code: 'OUT20261028001', orderId: '1', orderIds: ['1', '2'], shippedAt: '2026-10-28 09:00:00',
  keeper: '出库员', shipping: shipping(), lines: [{ orderId: '1', lineId: 'line-1', arrived: 30 }, { orderId: '2', lineId: 'line-2', arrived: 20 }] })

test('multi-order delivery note includes all allocated products and customer purchase numbers', () => {
  const orders = [order('1', 'CO-1'), order('2', 'CO-2')]
  const note = createDeliveryNote(orders[0], shipment(), orders)
  assert.equal(note.lines.length, 2)
  assert.equal(note.lines.reduce((sum, line) => sum + line.quantity, 0), 50)
  assert.deepEqual(note.lines.map(l => l.model), ['ET5907', 'ET5907'])
  assert.deepEqual(note.lines.map(l => l.remark), ['CO-1', 'CO-2'])
  assert.equal(note.purchaseNumber, 'CO-1 / CO-2')
  assert.equal(note.address, '本次收货地址')
  assert.equal(note.contact, '收货人')
  assert.equal(note.handler, '发货人')
  assert.match(note.shippingSummary, /顺丰.*SF123456/)
  assert.match(deliveryNoteHtml(note), /&lt;易碎&gt;/)
})
test('single-order delivery note leaves material code blank without a customer part snapshot', () => {
  const source = order('1', 'CO-1')
  const single = shipment(); single.orderIds = ['1']; single.lines = single.lines.slice(0, 1)
  single.shipping.purchaseNumber = 'CO-1'
  const note = createDeliveryNote(source, single)
  assert.equal(note.number, 'OUT20261028001')
  assert.equal(note.lines.length, 1)
  assert.equal(note.lines[0].code, '')
  assert.equal(note.lines[0].model, 'ET5907')
  assert.equal(note.lines[0].remark, '')
})

test('delivery note prints the customer part snapshot when present', () => {
  const source = order('1', 'CO-1')
  source.lines[0].customerPartNo = 'CUST-99001'
  const single = shipment(); single.orderIds = ['1']; single.lines = single.lines.slice(0, 1)
  single.shipping.purchaseNumber = 'CO-1'
  const note = createDeliveryNote(source, single)
  assert.equal(note.lines[0].code, 'CUST-99001')
})
test('printing rejects omitted order allocations, mixed customers and invalid shipment quantities', () => {
  const orders = [order('1', 'CO-1'), order('2', 'CO-2')]
  assert.throws(() => createDeliveryNote(orders[0], shipment()), /核对发货产品/)
  assert.throws(() => createDeliveryNote(orders[0], shipment(), [orders[0], order('2', 'CO-2', '10')]), /同一客户/)
  const invalid = shipment(); invalid.lines[1].arrived = 1001
  assert.throws(() => createDeliveryNote(orders[0], invalid, orders), /核对发货产品/)
})
test('saved outbound data restores shipping for detail and print while old records retain defaults', () => {
  const response = { id: 1, outboundOrderNo: 'OUT20261028001', customerId: 9, customerName: '真实客户', status: 'COMPLETED',
    salesOrders: [{ salesOrderId: 1 }], items: [], shipping: shipping() }
  assert.deepEqual(toSalesShipment(response).shipping, shipping())
  assert.equal(toSalesShipment({ ...response, shipping: null }).shipping, undefined)
  assert.equal(toSalesShipment({ ...response, shipping: { address: null, recipient: null } }).shipping, undefined)
})
