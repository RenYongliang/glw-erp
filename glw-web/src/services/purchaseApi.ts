import { apiRequest } from './api'
import { receiptOrderIds, type PurchaseOrder, type PurchaseReceipt, type ReceiptLine } from '../domain/purchase'

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
  /** 订单级单价口径：0 未税，1 含税。 */
  taxIncluded?: number | null
  orderStatus: string
  receiptStatus?: string | null
  paymentStatus: string
  remark?: string | null
  version?: number
  items: PurchaseOrderItemResponse[]
}

interface InboundOrderItemAllocResponse {
  id: number
  inboundOrderItemId: number
  purchaseOrderId: number
  purchaseOrderNo: string
  purchaseOrderItemId: number
  dateCode: string
  lotCode: string
  inboundQuantity: number
  unitPrice?: string
  taxIncluded?: number
  taxRate?: number | null
  amount?: string
  currency?: string
}

interface InboundOrderItemResponse {
  id: number
  inboundOrderId: number
  productId: number
  brand?: string
  partNo?: string
  packageType?: string
  inboundQuantity: number
  allocations: InboundOrderItemAllocResponse[]
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
  allocations: {
    purchaseOrderItemId: number
    dateCode: string
    lotCode: string
    allocatedQuantity: number
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

const orderStatuses: Record<string, PurchaseOrder['orderStatus']> = {
  PENDING_APPROVAL: '待审核', CONFIRMED: '执行中', COMPLETED: '已完结', CANCELLED: '已取消',
}
const receiptStatuses: Record<string, PurchaseOrder['receiptStatus']> = {
  PENDING_RECEIPT: '未收货', PARTIALLY_RECEIVED: '部分收货', RECEIVED: '全部收货',
}

function toPurchaseOrder(order: PurchaseOrderResponse): PurchaseOrder {
  const delivery = date(order.deliveryTime)
  // 含税口径是订单级的，后端随订单一起返回；缺省按含税处理。
  const taxIncluded = order.taxIncluded == null ? true : order.taxIncluded !== 0
  return {
    id: String(order.id),
    code: order.purchaseOrderNo || String(order.id),
    name: `${order.supplierName}采购订单`,
    taxIncluded,
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
    version: order.version,
    arrivals: [],
    payments: [],
    paymentStatus: ({ UNPAID: '未付款', PARTIALLY_PAID: '部分付款', PAID: '已付款' } as const)[order.paymentStatus as 'UNPAID' | 'PARTIALLY_PAID' | 'PAID'],
    attachments: [],
    term: order.remark || '',
    orderStatus: orderStatuses[order.orderStatus] || '执行中',
    receiptStatus: receiptStatuses[order.receiptStatus || ''] || '未收货',
    reason: '',    approver: '',
    approvedAt: '',
  }
}

function toPurchaseReceipt(receipt: InboundOrderResponse, purchaseOrderId?: string): PurchaseReceipt {
  const lines = receipt.items.flatMap(item => {
    const byPurchaseItem = new Map<string, ReceiptLine>()
    for (const alloc of item.allocations) {
      if (purchaseOrderId && String(alloc.purchaseOrderId) !== purchaseOrderId) continue
      const lineId = String(alloc.purchaseOrderItemId)
      const line = byPurchaseItem.get(lineId) ?? {
        orderId: String(alloc.purchaseOrderId),
        productId: String(item.productId),
        brand: item.brand,
        model: item.partNo,
        packaging: item.packageType,
        unitPrice: Number(alloc.unitPrice ?? 0),
        lineId,
        arrived: 0,
        rejected: 0,
        location: '',
        batches: [],
      }
      line.batches.push({
        id: String(alloc.id),
        dateCode: alloc.dateCode || '',
        lotCode: alloc.lotCode || '',
        quantity: alloc.inboundQuantity,
      })
      line.arrived += alloc.inboundQuantity
      byPurchaseItem.set(lineId, line)
    }
    return [...byPurchaseItem.values()]
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

function toPurchaseOrderRequest(order: PurchaseOrder) {
  if (!/^\d+$/.test(order.supplierId)) throw Error('请从供应商列表中选择供应商')
  const taxIncluded = order.taxIncluded !== false
  const items = order.lines.map(line => {
    if (!/^\d+$/.test(line.productId)) throw Error('采购产品需从产品列表中选择')
    return {
      productId: Number(line.productId),
      unitPrice: line.price,
      taxRate: line.tax,
      quantity: line.quantity,
    }
  })
  return {
    supplierId: Number(order.supplierId),
    orderTime: order.signed,
    deliveryTime: order.delivery,
    taxIncluded: taxIncluded ? 1 : 0,
    remark: (order.term || '').trim().slice(0, 256),
    version: order.version,
    items,
  }
}

export async function createPurchaseOrder(order: PurchaseOrder) {
  const created = await apiRequest<PurchaseOrderResponse>('/purchase-order', {
    method: 'POST',
    body: JSON.stringify(toPurchaseOrderRequest(order)),
  })
  return toPurchaseOrder(created)
}

export async function reverseInboundOrder(id: string) {
  await apiRequest<unknown>(`/inbound-order/${encodeURIComponent(id)}/reverse`, {
    method: 'POST',
  })
}

export async function cancelPurchaseOrder(id: string) {
  await apiRequest<unknown>(`/purchase-order/${encodeURIComponent(id)}/cancel`, {
    method: 'POST',
  })
}

export async function updatePurchaseOrder(order: PurchaseOrder) {
  const updated = await apiRequest<PurchaseOrderResponse>(`/purchase-order/${order.id}`, {
    method: 'PUT',
    body: JSON.stringify(toPurchaseOrderRequest(order)),
  })
  return toPurchaseOrder(updated)
}

export async function fetchInboundOrders() {
  return (await allRecords<InboundOrderResponse>('/inbound-order/list')).map(receipt => toPurchaseReceipt(receipt))
}

export async function fetchInboundDetail(id: string, orderId?: string) {
  return toPurchaseReceipt(await apiRequest<InboundOrderResponse>(`/inbound-order/${id}${orderId ? `/purchase-orders/${orderId}` : ''}`))
}

export async function fetchPurchaseInboundHistory(orderId: string) {
  const receipts = await allRecords<InboundOrderResponse>('/inbound-order/list', { purchaseOrderId: Number(orderId) })
  return receipts.map(receipt => toPurchaseReceipt({
    ...receipt,
    purchaseOrders: receipt.purchaseOrders.filter(order => String(order.purchaseOrderId) === orderId),
  }, orderId))
}

export async function createInboundOrder(receipt: PurchaseReceipt) {
  const allocations = receipt.lines.flatMap(line =>
    line.batches
      .filter(batch => batch.quantity > 0)
      .map(batch => ({
        purchaseOrderItemId: Number(line.lineId),
        dateCode: batch.dateCode.trim(),
        lotCode: batch.lotCode.trim(),
        allocatedQuantity: batch.quantity,
      })),
  )
  if (!allocations.length) throw Error('请填写至少一个入库批次')
  const request: InboundOrderCreateRequest = {
    purchaseOrderIds: receiptOrderIds(receipt).map(Number),
    inboundTime: receipt.receivedAt ? (receipt.receivedAt.length === 10 ? `${receipt.receivedAt}T00:00:00` : receipt.receivedAt.replace(' ', 'T')) : undefined,
    allocations,
  }
  await apiRequest<unknown>('/inbound-order', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}
