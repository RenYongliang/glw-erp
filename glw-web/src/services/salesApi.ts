import { apiRequest } from './api'
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
