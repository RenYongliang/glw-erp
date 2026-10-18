import test from 'node:test'
import assert from 'node:assert/strict'
import { toSalesOrder, toSalesShipment, fetchAllPages } from '../src/services/salesApiData.ts'
import { orderTotals, remainingQuantity, pendingShipmentQuantity, salesLineProduct, shipmentTotals } from '../src/domain/sales.ts'
import { emptySalesOrderFilters, matchesSalesOrderFilters, salesFulfillmentStatus } from '../src/domain/salesOrderFilters.ts'

const source = () => ({
  id: 1, orderNo: 'SO-real', customerOrderNo: 'CO-real', customerId: 9, customerName: '真实客户',
  customerShortName: '  简称  ',
  orderTime: '2026-10-17T10:00:00', deliveryTime: null, settleType: 'MONTHLY',
  orderStatus: 'CANCELLED', paymentStatus: 'PARTIALLY_PAID',
  items: [{ id: 11, salesOrderId: 1, productId: 8, brand: 'TI', partNo: 'TPS63020',
    packageType: 'WLCSP12(0.4 Pitch)', unitPrice: '0.0750', taxRate: 13,
    quantity: 1000, cancelledQuantity: 600, amount: '75.00' }],
})

test('real sales rows retain snapshots, precise prices, cancellation and collection status', () => {
  const order = toSalesOrder(source())
  assert.equal(order.customerName, '真实客户')
  assert.equal(order.customerShortName, '简称')
  assert.equal(toSalesOrder({ ...source(), customerShortName: ' ' }).customerShortName, undefined)
  assert.equal(order.purchaseNumber, 'CO-real')
  assert.equal(order.term, '月结')
  assert.equal(order.delivery, '')
  assert.equal(order.lines[0].price, 0.075)
  assert.equal(orderTotals(order).total, 75)
  assert.deepEqual(salesLineProduct(order.lines[0], []), {
    brand: 'TI', name: 'TPS63020', model: 'WLCSP12(0.4 Pitch)',
  })
  assert.equal(salesFulfillmentStatus(order, []), '剩余未交取消')
  const filters = emptySalesOrderFilters()
  filters.status = '剩余未交取消'
  filters.paymentStatus = '部分收款'
  filters.text.models = 'WLCSP12'
  assert.equal(matchesSalesOrderFilters(order, [], order.customerName, 75,
    salesFulfillmentStatus(order, []), filters, 0), true)
})

test('outbound rows retain actual quantities and multiple batch costs without doubling quantities', () => {
  const order = toSalesOrder(source())
  const shipment = toSalesShipment({ id: 21, outboundOrderNo: 'OUT20261018002', salesOrderId: 1, customerId: 9,
    customerName: '真实客户', customerShortName: '简称', customerOrderNo: 'CO-real', status: 'COMPLETED',
    outboundTime: '2026-10-18T11:00:00', items: [{ id: 31, salesOrderItemId: 11,
      outboundQuantity: 400, batches: [
        { id: 41, inventoryId: 51, quantity: 100, unitCost: '0.0250' },
        { id: 42, inventoryId: 52, quantity: 300, unitCost: '0.0350' },
      ] }] })
  assert.equal(shipment.confirmed, true)
  assert.equal(shipment.code, 'OUT20261018002')
  assert.equal(shipment.customerShortName, '简称')
  assert.equal(shipment.customerName, '真实客户')
  assert.equal(shipment.orderId, order.id)
  assert.equal(shipment.lines[0].lineId, order.lines[0].id)
  assert.equal(shipment.lines[0].batches.length, 2)
  assert.equal(shipment.lines[0].batches[1].unitCost, 0.035)
  assert.deepEqual(shipmentTotals(shipment, order), { quantity: 400, total: 30 })
  assert.equal(remainingQuantity({ orders: [order], shipments: [shipment] }, '1', '11'), 0)
})

test('backend completion status stays correct when only part of the outbound history is loaded', () => {
  for (const [status, label] of [['PENDING_SHIPMENT', '待发货'], ['PARTIALLY_SHIPPED', '部分发货'], ['COMPLETED', '已完成']]) {
    assert.equal(salesFulfillmentStatus(toSalesOrder({ ...source(), orderStatus: status }), []), label)
  }
})

test('sales list loader follows all pages and stops on empty pages', async () => {
  const calls = []
  const rows = await fetchAllPages(async (pageNo, pageSize) => {
    calls.push([pageNo, pageSize])
    return { total: 3, records: pageNo === 1 ? [1, 2] : [3] }
  })
  assert.deepEqual(rows, [1, 2, 3])
  assert.deepEqual(calls, [[1, 200], [2, 200]])
  assert.deepEqual(await fetchAllPages(async () => ({ total: 0, records: [] })), [])
})

test('pending shipment counts only actual completed shipments for each sales line', () => {
  const order = toSalesOrder(source())
  const line = order.lines[0]
  const completed = { orderId: order.id, confirmed: true, tag: '出库', lines: [{ lineId: line.id, arrived: 100 }] }
  const shipments = [completed, { ...completed, confirmed: false }, { ...completed, tag: '计划出库' },
    { ...completed, orderId: 'another' }, { ...completed, lines: [{ lineId: 'another', arrived: 100 }] }]
  assert.equal(pendingShipmentQuantity(order, line, shipments), 300)
  assert.equal(pendingShipmentQuantity(order, line, [...shipments, completed]), 200)
  assert.equal(pendingShipmentQuantity(order, { ...line, pendingQuantity: 40 }, []), 40)
  assert.equal(pendingShipmentQuantity(order, { ...line, pendingQuantity: 0 }, []), 0)
  assert.equal(pendingShipmentQuantity(order, line, [{ ...completed, lines: [{ lineId: line.id, arrived: 1000 }] }]), 0)
})

test('shipping capacity uses server pending totals without subtracting completed shipments again', () => {
  const order = toSalesOrder(source())
  const line = order.lines[0]
  line.pendingQuantity = 300
  const shipment = { id: 'completed', orderId: order.id, confirmed: true, tag: '出库', lines: [{ lineId: line.id, arrived: 100 }] }
  const state = { orders: [order], shipments: [shipment] }
  assert.equal(remainingQuantity(state, order.id, line.id), 300)
  state.shipments.push({ ...shipment, id: 'pending', confirmed: false })
  assert.equal(remainingQuantity(state, order.id, line.id), 200)
  assert.equal(remainingQuantity(state, order.id, line.id, 'pending'), 300)
})
