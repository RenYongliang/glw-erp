import test from 'node:test'
import assert from 'node:assert/strict'
import { allocateFifo, validateBatchSelection } from '../src/domain/shipmentBatches.ts'
import { validateShipment } from '../src/domain/sales.ts'

const stock = [
  {inventoryId: '1', batchNo: 'BAT26100100001001', quantity: 100, unitCost: 0.075},
  {inventoryId: '2', batchNo: 'BAT26100200001001', quantity: 300, unitCost: 0.08},
]
test('FIFO splits quantities across batches and stops at stock or pending quantity', () => {
  const batches = allocateFifo(stock, 250)
  assert.deepEqual(batches.map(b => b.quantity), [100, 150])
  assert.equal(batches[0].batchNo, stock[0].batchNo)
  assert.equal(batches[0].unitCost, 0.075)
  assert.equal(validateBatchSelection(batches, stock, 250), '')
  assert.equal(allocateFifo(stock, 1000).reduce((n, b) => n + b.quantity, 0), 400)
  assert.deepEqual(allocateFifo(stock, 0), [])
  assert.deepEqual(allocateFifo([], 100), [])
  assert.deepEqual(stock.map(b => b.quantity), [100, 300])
})
test('batch selection rejects stale stock, duplicates, invalid quantities and excess delivery', () => {
  const batches = allocateFifo(stock, 250)
  assert.match(validateBatchSelection(batches, [], 250), /已无库存/)
  assert.match(validateBatchSelection([batches[0], batches[0]], stock, 250), /重复/)
  assert.match(validateBatchSelection([{...batches[0], quantity: 101}], stock, 250), /剩余库存/)
  for (const quantity of [0, -1, 0.5, NaN]) assert.match(validateBatchSelection([{...batches[0], quantity}], stock, 250), /正整数/)
  assert.match(validateBatchSelection(batches, stock, 200), /待发数量/)
  assert.equal(validateBatchSelection([], stock, 250), '')
})
test('shipment validation uses actual batch quantities for real products and checks the sum', () => {
  const order = {id: 'o1', orderStatus: '执行中', deliveryStatus: '未发货', warehouse: '', lines: [{id: 'l1', productId: '1', quantity: 500, pendingQuantity: 500}]}
  const state = {orders: [order], shipments: [], returns: []}
  const shipment = {id: 'new', orderId: 'o1', warehouse: '', tag: '出库', confirmed: false, customerConfirmed: false,
    lines: [{lineId: 'l1', arrived: 250, batches: allocateFifo(stock, 250)}]}
  assert.equal(validateShipment(shipment, state), '')
  shipment.lines[0].arrived = 200
  assert.match(validateShipment(shipment, state), /合计/)
  shipment.lines[0].arrived = 250
  shipment.lines[0].batches[0].quantity = -1
  assert.match(validateShipment(shipment, state), /批次数量/)
})
