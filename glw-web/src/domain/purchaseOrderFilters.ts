import type { Product, PurchaseOrder, PurchaseReceipt } from './purchase'

export interface PurchaseOrderFilters {
  code: string
  supplier: string
  paymentStatus: '' | '未付款' | '部分付款' | '已付款'
  /** 收货状态筛选。 */
  receiptStatus: '' | '未收货' | '部分收货' | '全部收货'
  /** 订单生命周期筛选。 */
  orderStatus: '' | '待审核' | '执行中' | '已完结' | '已取消'
  dates: string[] | null
  product: { brand: string; model: string; packaging: string }
}
export function emptyPurchaseOrderFilters(): PurchaseOrderFilters {
  return { code: '', supplier: '', paymentStatus: '', receiptStatus: '', orderStatus: '', dates: null, product: { brand: '', model: '', packaging: '' } }
}
export function purchaseFilterCount(filters: PurchaseOrderFilters): number {
  return [filters.code, filters.supplier, filters.paymentStatus, filters.receiptStatus, filters.orderStatus, ...Object.values(filters.product)].filter(value => value.trim()).length + Number(!!filters.dates?.some(Boolean))
}
export function purchaseReceiptStatus(order: PurchaseOrder, receipts: PurchaseReceipt[]): PurchaseOrderFilters['receiptStatus'] {
  if (['未收货', '部分收货', '全部收货'].includes(order.receiptStatus)) return order.receiptStatus
  const quantities = new Map<string, number>()
  for (const receipt of receipts) {
    if (!(receipt.orderIds ?? [receipt.orderId]).includes(order.id) || !receipt.confirmed) continue
    for (const line of receipt.lines) quantities.set(line.lineId, (quantities.get(line.lineId) || 0) + Math.max(0, line.arrived - line.rejected))
  }
  if (order.lines.length && order.lines.every(line => (quantities.get(line.id) || 0) >= line.quantity)) return '全部收货'
  return order.lines.some(line => (quantities.get(line.id) || 0) > 0) ? '部分收货' : '未收货'
}
export function matchesPurchaseOrderFilters(order: PurchaseOrder, catalog: Product[], supplier: string, total: number, paid: number, receipts: PurchaseReceipt[], filters: PurchaseOrderFilters): boolean {
  const includes = (value: string, query: string) => value.toLowerCase().includes(query.trim().toLowerCase())
  if (!includes(order.code, filters.code)) return false
  if (filters.supplier && supplier !== filters.supplier) return false
  const payment = order.paymentStatus ?? (Math.round(paid * 100) <= 0 ? '未付款' : Math.round(paid * 100) >= Math.round(total * 100) ? '已付款' : '部分付款')
  if (filters.paymentStatus && payment !== filters.paymentStatus) return false
  if (filters.receiptStatus && purchaseReceiptStatus(order, receipts) !== filters.receiptStatus) return false
  if (filters.orderStatus && order.orderStatus !== filters.orderStatus) return false
  if (filters.dates?.some(Boolean)) {
    const date = order.signed?.slice(0, 10)
    if (!date || (filters.dates[0] && date < filters.dates[0]) || (filters.dates[1] && date > filters.dates[1])) return false
  }
  if (!Object.values(filters.product).some(value => value.trim())) return true
  return order.lines.some(line => {
    const product = catalog.find(p => p.id === line.productId)
    return includes(line.brand ?? product?.brand ?? '', filters.product.brand)
      && includes(line.model ?? product?.model ?? '', filters.product.model)
      && includes(line.packaging ?? product?.packaging ?? '', filters.product.packaging)
  })
}
