import { apiRequest } from './api'
import { shipmentOrderIds, type SalesOrder, type SalesShipment, type ShippingInfo } from '../domain/sales'
import { fetchAllPages, toSalesOrder, toSalesShipment,
  type PageResponse, type SalesOrderResponse, type OutboundOrderResponse } from './salesApiData'

export async function fetchSalesOrders() {
  const rows = await fetchAllPages((pageNo, pageSize) => apiRequest<PageResponse<SalesOrderResponse>>(
    '/sales-order/list', { method: 'POST', body: JSON.stringify({ pageNo, pageSize }) },
  ))
  return rows.map(toSalesOrder)
}

export async function fetchOutboundOrders() {
  const rows = await fetchAllPages((pageNo, pageSize) => apiRequest<PageResponse<OutboundOrderResponse>>(
    '/outbound-order/list', { method: 'POST', body: JSON.stringify({ pageNo, pageSize }) },
  ))
  return rows.map(toSalesShipment)
}

const settleTypes: [string, string][] = [
  ['月结', 'MONTHLY'],
  ['预付', 'PREPAID'],
  ['货到付款', 'COD'],
  ['现金', 'CASH'],
]

function toSettleType(term?: string) {
  return settleTypes.find(([label]) => (term || '').startsWith(label))?.[1]
}

function toSalesOrderRequest(order: SalesOrder) {
  if (!/^\d+$/.test(order.customerId)) throw Error('请从客户列表中选择客户')
  const taxIncluded = order.taxIncluded !== false
  const items = order.lines.map(line => {
    if (!/^\d+$/.test(line.productId)) throw Error('销售产品需从产品列表中选择')
    return {
      productId: Number(line.productId),
      customerPartNo: (line.customerPartNo || '').trim().slice(0, 64) || null,
      unitPrice: line.price,
      taxRate: taxIncluded ? line.tax : null,
      quantity: line.quantity,
    }
  })
  return {
    customerId: Number(order.customerId),
    orderTime: order.signed,
    deliveryTime: order.delivery,
    customerOrderNo: (order.purchaseNumber || '').trim().slice(0, 64) || null,
    settleType: toSettleType(order.term),
    taxIncluded: taxIncluded ? 1 : 0,
    remark: (order.remark || '').trim().slice(0, 256) || null,
    version: order.version,
    items,
  }
}

export async function createSalesOrder(order: SalesOrder) {
  await apiRequest<unknown>('/sales-order', {
    method: 'POST',
    body: JSON.stringify(toSalesOrderRequest(order)),
  })
}

export async function updateSalesOrder(order: SalesOrder) {
  await apiRequest<unknown>(`/sales-order/${order.id}`, {
    method: 'PUT',
    body: JSON.stringify(toSalesOrderRequest(order)),
  })
}

interface OutboundOrderCreateRequest {
  salesOrderIds: number[]
  outboundTime?: string
  shipping?: ShippingInfo
  allocations: {
    salesOrderItemId: number
    batches: { inventoryId: number; quantity: number }[]
  }[]
}

export async function createOutboundOrder(shipment: SalesShipment) {
  const salesOrderIds = shipmentOrderIds(shipment).map(Number)
  if (!salesOrderIds.length) throw Error('请选择可发货的销售订单')
  const allocations = shipment.lines
    .filter(line => line.arrived > 0)
    .map(line => ({
      salesOrderItemId: Number(line.lineId),
      batches: (line.batches ?? [])
        .filter(batch => batch.quantity > 0)
        .map(batch => ({ inventoryId: Number(batch.inventoryId), quantity: batch.quantity })),
    }))
  if (!allocations.length) throw Error('本次出库数量须大于 0')
  if (allocations.some(allocation => !allocation.batches.length))
    throw Error('请为每条出库明细选择批次')
  const request: OutboundOrderCreateRequest = {
    salesOrderIds,
    outboundTime: shipment.shippedAt
      ? (shipment.shippedAt.length === 10 ? `${shipment.shippedAt}T00:00:00` : shipment.shippedAt.replace(' ', 'T'))
      : undefined,
    allocations,
    shipping: shipment.shipping,
  }
  await apiRequest<unknown>('/outbound-order', { method: 'POST', body: JSON.stringify(request) })
}

/** 登记客户签收日期，应收记录据此归入账单月。 */
export async function signOutboundOrder(id: string, signTime: string) {
  await apiRequest<unknown>('/outbound-order/sign', {
    method: 'POST',
    body: JSON.stringify({ orderIds: [Number(id)], signTime }),
  })
}
