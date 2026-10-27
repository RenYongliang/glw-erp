import { apiRequest } from './api'
import { receiptOrderIds, type PurchaseOrder, type PurchaseReceipt } from '../domain/purchase'

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
  pendingQuantity: number
  amount: string
}

interface PurchaseOrderResponse {
  id: number
  purchaseOrderNo?: string
  orderTime: string
  supplierId: number
  supplierName: string
  supplierShortName?: string | null
  deliveryTime?: string | null
  orderStatus: string
  paymentStatus: string
  remark?: string | null
  items: PurchaseOrderItemResponse[]
}

interface InboundOrderBatchItemResponse {
  id: number
  inboundOrderItemId: number
  productId: number
  dateCode: string
  lotCode: string
  inboundQuantity: number
}

interface InboundOrderItemResponse {
  productId: number
  brand?: string
  partNo?: string
  packageType?: string
  unitPrice?: string
  id: number
  inboundOrderId: number
  purchaseOrderId: number
  purchaseOrderNo: string
  purchaseOrderItemId: number
  inboundQuantity: number
  batches: InboundOrderBatchItemResponse[]
}

interface InboundOrderResponse {
  id: number
  inboundOrderNo: string
  purchaseOrders: { purchaseOrderId: number; purchaseOrderNo: string }[]
  supplierId: number
  supplierName: string
  inboundType: string
  status: string
  inboundTime?: string | null
  items: InboundOrderItemResponse[]
}

interface InboundOrderCreateRequest {
  purchaseOrderIds: number[]
  inboundTime?: string
  items: {
    purchaseOrderItemId: number
    batches: {
      dateCode: string
      lotCode: string
      inboundQuantity: number
    }[]
  }[]
}

async function allRecords<T>(path: string, filters: Record<string, unknown> = {}): Promise<T[]> {
  const records: T[] = []
  for (let pageNo = 1; ; pageNo++) {
    const page = await apiRequest<PageResponse<T>>(path, { method: 'POST', body: JSON.stringify({ ...filters, pageNo, pageSize: 200 }) })
    records.push(...page.records)
    if (pageNo >= page.pages || !page.records.length) return records
  }
}
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
    supplierShortName: order.supplierShortName?.trim() || undefined,
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
      pendingQuantity: item.pendingQuantity,
      price: Number(item.unitPrice),
      tax: item.taxRate ?? 0,
      amount: Number(item.amount),
      delivery,
    })),
    arrivals: [],
    payments: [],
    paymentStatus: ({ UNPAID: '未付款', PARTIALLY_PAID: '部分付款', PAID: '已付款' } as const)[order.paymentStatus as 'UNPAID' | 'PARTIALLY_PAID' | 'PAID'],
    attachments: [],
    term: order.remark || '',
    status: orderStatus(order.orderStatus),
    reason: '',
    approver: '',
    approvedAt: '',
  }
}

function toPurchaseReceipt(receipt: InboundOrderResponse): PurchaseReceipt {
  const lines = receipt.items.map(item => {
    const batches = item.batches?.map(batch => ({
      id: String(batch.id),
      dateCode: batch.dateCode || '',
      lotCode: batch.lotCode || '',
      quantity: batch.inboundQuantity,
    })) ?? [{ id: `legacy-${item.id}`, dateCode: '', lotCode: '', quantity: item.inboundQuantity }]
    return {
      orderId: String(item.purchaseOrderId),
      productId: String(item.productId),
      brand: item.brand,
      model: item.partNo,
      packaging: item.packageType,
      unitPrice: Number(item.unitPrice ?? 0),
      lineId: String(item.purchaseOrderItemId),
      arrived: batches.reduce((sum, batch) => sum + batch.quantity, 0),
      rejected: 0,
      location: '',
      batches,
    }
  })
  return {
    id: String(receipt.id),
    code: receipt.inboundOrderNo,
    orderId: String(receipt.purchaseOrders[0]?.purchaseOrderId ?? ''),
    orderIds: receipt.purchaseOrders.map(order => String(order.purchaseOrderId)),
    supplierName: receipt.supplierName,
    warehouse: '',
    lines,
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
  return (await allRecords<PurchaseOrderResponse>('/purchase-order/list')).map(toPurchaseOrder)
}

export async function fetchInboundOrders() {
  return (await allRecords<InboundOrderResponse>('/inbound-order/list')).map(toPurchaseReceipt)
}

export async function fetchInboundDetail(id: string, orderId?: string) {
  return toPurchaseReceipt(await apiRequest<InboundOrderResponse>(`/inbound-order/${id}${orderId ? `/purchase-orders/${orderId}` : ''}`))
}

export async function fetchPurchaseInboundHistory(orderId: string) {
  const receipts = await allRecords<InboundOrderResponse>('/inbound-order/list', { purchaseOrderId: Number(orderId) })
  return receipts.map(receipt => toPurchaseReceipt({
    ...receipt,
    purchaseOrders: receipt.purchaseOrders.filter(order => String(order.purchaseOrderId) === orderId),
    items: receipt.items.filter(item => String(item.purchaseOrderId) === orderId),
  }))
}

export async function createInboundOrder(receipt: PurchaseReceipt) {
  const items = receipt.lines
    .map(line => ({
      purchaseOrderItemId: Number(line.lineId),
      batches: line.batches
        .filter(batch => batch.quantity > 0)
        .map(batch => ({
          dateCode: batch.dateCode.trim(),
          lotCode: batch.lotCode.trim(),
          inboundQuantity: batch.quantity,
        })),
    }))
    .filter(item => item.batches.length > 0)
  if (!items.length) throw Error('请填写至少一个入库批次')
  const request: InboundOrderCreateRequest = {
    purchaseOrderIds: receiptOrderIds(receipt).map(Number),
    inboundTime: receipt.receivedAt ? (receipt.receivedAt.length === 10 ? `${receipt.receivedAt}T00:00:00` : receipt.receivedAt.replace(' ', 'T')) : undefined,
    items,
  }
  await apiRequest<unknown>('/inbound-order', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}
