import { apiRequest } from './api'
import { shipmentOrderIds, type SalesShipment } from '../domain/sales'
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

interface OutboundOrderCreateRequest {
  salesOrderIds: number[]
  outboundTime?: string
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
  }
  await apiRequest<unknown>('/outbound-order', { method: 'POST', body: JSON.stringify(request) })
}
