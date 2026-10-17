import type { SalesOrder, SalesShipment } from '../domain/sales'

export interface PageResponse<T> {
  records: T[]
  total: number
  current: number
  size: number
}
export interface SalesOrderItemResponse {
  id: number
  salesOrderId: number
  productId: number
  brand?: string | null
  partNo: string
  packageType?: string | null
  unitPrice: string | number
  taxRate?: number | null
  quantity: number
  pendingQuantity?: number
  cancelledQuantity?: number | null
  amount: string | number
}
export interface SalesOrderResponse {
  id: number
  orderNo?: string | null
  customerOrderNo?: string | null
  orderTime: string
  customerId: number
  customerName: string
  customerShortName?: string | null
  settleType?: string | null
  deliveryTime?: string | null
  orderStatus: string
  paymentStatus: string
  remark?: string | null
  items: SalesOrderItemResponse[]
}
export interface OutboundOrderResponse {
  id: number
  salesOrderId: number
  customerOrderNo?: string | null
  customerId: number
  customerName: string
  customerShortName?: string | null
  status: string
  outboundTime?: string | null
  items: {
    id: number
    salesOrderItemId: number
    outboundQuantity: number
    batches: { id: number; inventoryId: number; quantity: number; unitCost: string | number }[]
  }[]
}

const date = (value?: string | null) => value?.slice(0, 10) || ''
const statuses: Record<string, SalesOrder['status']> = {
  PENDING_SHIPMENT: '待发货', PARTIALLY_SHIPPED: '部分发货', COMPLETED: '已完成', CANCELLED: '剩余未交取消',
}
const payments: Record<string, NonNullable<SalesOrder['paymentStatus']>> = {
  UNPAID: '未收款', PARTIALLY_PAID: '部分收款', PAID: '已收款',
}
const terms: Record<string, string> = { CASH: '现金', MONTHLY: '月结', PREPAID: '预付', COD: '货到付款' }

export function toSalesOrder(order: SalesOrderResponse): SalesOrder {
  return {
    id: String(order.id), code: order.orderNo || String(order.id),
    name: `${order.customerName}销售订单`, customerId: String(order.customerId),
    customerName: order.customerName, purchaseNumber: order.customerOrderNo || '',
    customerShortName: order.customerShortName?.trim() || undefined,
    signed: date(order.orderTime), delivery: date(order.deliveryTime),
    owner: '', department: '', warehouse: '', address: '', discount: 0,
    term: terms[order.settleType || ''] || '',
    status: statuses[order.orderStatus] || '待发货',
    paymentStatus: payments[order.paymentStatus],
    remark: order.remark || '',
    lines: order.items.map(item => ({
      id: String(item.id), productId: String(item.productId),
      brand: item.brand ?? '', model: item.partNo, packaging: item.packageType ?? '',
      quantity: item.quantity, cancelledQuantity: item.cancelledQuantity ?? 0,
      pendingQuantity: item.pendingQuantity,
      price: Number(item.unitPrice), tax: item.taxRate ?? 0, amount: Number(item.amount),
    })),
    arrivals: [], payments: [], attachments: [], reason: '', approver: '', approvedAt: '',
  }
}

export function toSalesShipment(order: OutboundOrderResponse): SalesShipment {
  return {
    id: String(order.id), code: `XSCK-${String(order.id).padStart(6, '0')}`,
    orderId: String(order.salesOrderId), customerName: order.customerName,
    customerShortName: order.customerShortName?.trim() || undefined,
    purchaseNumber: order.customerOrderNo || '', warehouse: '',
    tag: '出库', confirmed: order.status === 'COMPLETED', shippedAt: date(order.outboundTime),
    keeper: '', department: '', photos: [], customerConfirmed: false, customerReceivedAt: '',
    lines: order.items.map(item => ({
      lineId: String(item.salesOrderItemId), arrived: item.outboundQuantity, location: '',
      batches: item.batches.map(batch => ({
        id: String(batch.id), inventoryId: String(batch.inventoryId),
        quantity: batch.quantity, unitCost: Number(batch.unitCost),
      })),
    })),
  }
}

export async function fetchAllPages<T>(request: (pageNo: number, pageSize: number) => Promise<PageResponse<T>>) {
  const rows: T[] = []
  let pageNo = 1
  while (true) {
    const page = await request(pageNo, 200)
    rows.push(...page.records)
    if (!page.records.length || rows.length >= page.total) return rows
    pageNo++
  }
}
