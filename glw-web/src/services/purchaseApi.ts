import { apiRequest } from './api'
import type { PurchaseOrder, PurchaseReceipt } from '../domain/purchase'

interface PageResponse<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

interface PurchaseOrderItemResponse {
  id: number
  purchaseOrderId: number
  purchaseOrderNo: string
  productId: number
  brand?: string
  partNo: string
  packageType?: string
  unitPrice: string
  taxIncluded?: string
  taxRate?: number | null
  quantity: number
  amount: string
}

interface PurchaseOrderResponse {
  id: number
  purchaseOrderNo?: string
  orderTime: string
  supplierId: number
  supplierName: string
  deliveryTime?: string | null
  orderStatus: string
  paymentStatus: string
  remark?: string | null
  items: PurchaseOrderItemResponse[]
}

interface InboundOrderItemResponse {
  id: number
  inboundOrderId: number
  purchaseOrderId: number
  purchaseOrderNo: string
  purchaseOrderItemId: number
  inboundQuantity: number
}

interface InboundOrderResponse {
  id: number
  purchaseOrderId: number
  purchaseOrderNo?: string | null
  supplierId: number
  supplierName: string
  inboundType: string
  status: string
  inboundTime?: string | null
  items: InboundOrderItemResponse[]
}

const pageRequest = { pageNo: 1, pageSize: 1000 }
const date = (value?: string | null) => (value ? value.slice(0, 10) : '')

function orderStatus(status: string): PurchaseOrder['status'] {
  if (status === 'PENDING_RECEIPT') return '待收货'
  if (status === 'PARTIALLY_RECEIVED') return '部分收货'
  if (status === 'COMPLETED') return '已完成'
  return '待收货'
}

function toPurchaseOrder(order: PurchaseOrderResponse): PurchaseOrder {
  const delivery = date(order.deliveryTime)
  return {
    id: String(order.id),
    code: order.purchaseOrderNo || String(order.id),
    name: `${order.supplierName}采购订单`,
    supplierId: String(order.supplierId),
    supplierName: order.supplierName,
    signed: date(order.orderTime),
    delivery,
    owner: '',
    department: '',
    warehouse: '',
    address: '',
    discount: 0,
    lines: order.items.map(item => ({
      id: String(item.id),
      productId: String(item.productId),
      brand: item.brand,
      model: item.partNo,
      packaging: item.packageType,
      quantity: item.quantity,
      price: Number(item.unitPrice),
      tax: item.taxRate ?? 0,
      amount: Number(item.amount),
      delivery,
    })),
    arrivals: [],
    payments: [],
    attachments: [],
    term: order.remark || '',
    status: orderStatus(order.orderStatus),
    reason: '',
    approver: '',
    approvedAt: '',
  }
}

function toPurchaseReceipt(receipt: InboundOrderResponse): PurchaseReceipt {
  return {
    id: String(receipt.id),
    code: `CGRK-${String(receipt.id).padStart(6, '0')}`,
    orderId: String(receipt.purchaseOrderId),
    warehouse: '',
    lines: receipt.items.map(item => ({
      lineId: String(item.purchaseOrderItemId),
      arrived: item.inboundQuantity,
      rejected: 0,
      location: '',
    })),
    quality: '合格',
    qualityNote: '',
    inspector: '',
    inspectionDepartment: '',
    inspectedAt: date(receipt.inboundTime),
    confirmed: receipt.status === 'COMPLETED',
    receivedAt: date(receipt.inboundTime),
    keeper: '',
    department: '',
    photos: [],
  }
}

export async function fetchPurchaseOrders() {
  const page = await apiRequest<PageResponse<PurchaseOrderResponse>>(
    '/purchase-order/list',
    { method: 'POST', body: JSON.stringify(pageRequest) },
  )
  return page.records.map(toPurchaseOrder)
}

export async function fetchInboundOrders() {
  const page = await apiRequest<PageResponse<InboundOrderResponse>>(
    '/inbound-order/list',
    { method: 'POST', body: JSON.stringify(pageRequest) },
  )
  return page.records.map(toPurchaseReceipt)
}
