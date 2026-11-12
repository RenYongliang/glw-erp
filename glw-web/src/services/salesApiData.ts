import type { SalesOrder, SalesShipment, ShippingInfo } from '../domain/sales'

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
  customerPartNo?: string | null
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
  /** 订单级单价口径：0 未税，1 含税。 */
  taxIncluded?: number | null
  deliveryTime?: string | null
  warehouseCode?: string | null
  warehouseName?: string | null
  orderStatus: string
  deliveryStatus?: string | null
  paymentStatus: string
  /** 已登记但尚未核销的预付款余额。 */
  prepaymentAmount?: number | null
  remark?: string | null
  version?: number
  items: SalesOrderItemResponse[]
}
export interface OutboundSalesOrderResponse {
  salesOrderId: number
  salesOrderNo?: string | null
  customerOrderNo?: string | null
}
export interface OutboundOrderItemAllocResponse {
  id: number
  outboundOrderItemId: number
  salesOrderId: number
  customerOrderNo?: string | null
  salesOrderItemId: number
  outboundQuantity: number
  unitPrice?: string | number | null
  taxIncluded?: number | null
  taxRate?: number | null
  amount?: string | number | null
  currency?: string | null
  batches: { id: number; inventoryId: number; quantity: number; unitCost: string | number }[]
}
export interface OutboundOrderResponse {
  id: number
  outboundOrderNo?: string | null
  customerId: number
  customerName: string
  customerShortName?: string | null
  status: string
  outboundTime?: string | null
  warehouseCode?: string | null
  warehouseName?: string | null
  /** 客户签收时间；为空表示尚未签收，应收记录暂不进入对账单。 */
  signTime?: string | null
  statementStatus?: string | null
  shipping?: Partial<Record<keyof ShippingInfo, string | null>> | null
  salesOrders: OutboundSalesOrderResponse[]
  items: {
    id: number
    productId: number
    outboundQuantity: number
    allocations: OutboundOrderItemAllocResponse[]
  }[]
}

const date = (value?: string | null) => value?.slice(0, 10) || ''
/** 签收时间精确到分钟，列表编辑时保留时间，避免只改时分却不触发保存。 */
const dateTime = (value?: string | null) =>
  value ? value.replace('T', ' ').slice(0, 16) : ''
const orderStatuses: Record<string, SalesOrder['orderStatus']> = {
  PENDING_APPROVAL: '待审核', CONFIRMED: '执行中', COMPLETED: '已完结', CANCELLED: '已取消',
}
const deliveryStatuses: Record<string, SalesOrder['deliveryStatus']> = {
  PENDING_SHIPMENT: '未发货', PARTIALLY_SHIPPED: '部分发货', SHIPPED: '全部发货',
}
const payments: Record<string, NonNullable<SalesOrder['paymentStatus']>> = {
  UNPAID: '未收款', PARTIALLY_PAID: '部分收款', PAID: '已收款',
}
const terms: Record<string, string> = { CASH: '现金', MONTHLY: '月结', PREPAID: '预付', COD: '货到付款' }

export function toSalesOrder(order: SalesOrderResponse): SalesOrder {
  // 含税口径由订单表统一给出；未返回时按含税处理。
  const taxIncluded = order.taxIncluded == null ? true : order.taxIncluded !== 0
  return {
    id: String(order.id), code: order.orderNo || String(order.id),
    name: `${order.customerName}销售订单`, customerId: String(order.customerId),
    taxIncluded,
    customerName: order.customerName, purchaseNumber: order.customerOrderNo || '',
    customerShortName: order.customerShortName?.trim() || undefined,
    signed: date(order.orderTime), delivery: date(order.deliveryTime),
    owner: '', department: '', warehouse: order.warehouseName || '深圳盐田仓', address: '', discount: 0,
    term: terms[order.settleType || ''] || '',
    orderStatus: orderStatuses[order.orderStatus] || '执行中',
    deliveryStatus: deliveryStatuses[order.deliveryStatus || ''] || '未发货',
    paymentStatus: payments[order.paymentStatus],    remark: order.remark || '',
    prepaymentAmount: Number(order.prepaymentAmount ?? 0),
    version: order.version,
    lines: order.items.map(item => ({
      id: String(item.id), productId: String(item.productId),
      brand: item.brand ?? '', model: item.partNo, packaging: item.packageType ?? '',
      customerPartNo: item.customerPartNo ?? undefined,
      quantity: item.quantity, cancelledQuantity: item.cancelledQuantity ?? 0,
      pendingQuantity: item.pendingQuantity,
      price: Number(item.unitPrice), tax: item.taxRate ?? 0,
      amount: Number(item.amount),
    })),
    arrivals: [], payments: [], attachments: [], reason: '', approver: '', approvedAt: '',
  }
}

export function toSalesShipment(order: OutboundOrderResponse): SalesShipment {
  const salesOrders = order.salesOrders || []
  const allocations = order.items.flatMap(item => item.allocations || [])
  return {
    id: String(order.id), code: order.outboundOrderNo || String(order.id),
    orderId: String(salesOrders[0]?.salesOrderId ?? ''),
    orderIds: salesOrders.map(link => String(link.salesOrderId)),
    customerName: order.customerName,
    customerShortName: order.customerShortName?.trim() || undefined,
    purchaseNumber: salesOrders[0]?.customerOrderNo || '',
    warehouse: order.warehouseName || '深圳盐田仓',
    shipping: order.shipping && Object.values(order.shipping).some(value => !!value)
      ? { address: order.shipping.address ?? '', recipient: order.shipping.recipient ?? '', phone: order.shipping.phone ?? '',
        sender: order.shipping.sender ?? '', senderPhone: order.shipping.senderPhone ?? '', method: order.shipping.method ?? '',
        carrier: order.shipping.carrier ?? '', trackingNumber: order.shipping.trackingNumber ?? '',
        purchaseNumber: order.shipping.purchaseNumber ?? '', remark: order.shipping.remark ?? '' }
      : undefined,
    tag: '出库', confirmed: order.status === 'COMPLETED', shippedAt: date(order.outboundTime),
    keeper: '', department: '', photos: [],
    customerConfirmed: !!order.signTime, customerReceivedAt: dateTime(order.signTime),
    statementStatus: order.statementStatus || undefined,
    lines: allocations.map(alloc => ({
      lineId: String(alloc.salesOrderItemId), orderId: String(alloc.salesOrderId),
      arrived: alloc.outboundQuantity, location: '',
      batches: alloc.batches.map((batch, index) => ({
        id: batch.id != null ? String(batch.id) : `legacy-${alloc.id}-${index}`,
        inventoryId: batch.inventoryId != null ? String(batch.inventoryId) : '',
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
