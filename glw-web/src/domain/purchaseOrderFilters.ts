import type { Product, PurchaseOrder, PurchaseReceipt } from './purchase'

export interface PurchaseOrderFilters {
  code: string
  supplier: string
  paymentStatus: '' | '未付款' | '部分付款' | '已付款'
  receiptStatus: '' | '待收货' | '部分收货' | '已收货'
  dates: string[] | null
  product: { brand: string; model: string; packaging: string }
}
export function emptyPurchaseOrderFilters(): PurchaseOrderFilters {
  return { code: '', supplier: '', paymentStatus: '', receiptStatus: '', dates: null, product: { brand: '', model: '', packaging: '' } }
}
export function purchaseFilterCount(filters: PurchaseOrderFilters): number {
  return [filters.code, filters.supplier, filters.paymentStatus, filters.receiptStatus, ...Object.values(filters.product)].filter(value => value.trim()).length + Number(!!filters.dates?.some(Boolean))
}
export function purchaseReceiptStatus(order: PurchaseOrder, receipts: PurchaseReceipt[]): PurchaseOrderFilters['receiptStatus'] {
  const quantities = new Map<string, number>()
  for (const receipt of receipts) {
    if (!(receipt.orderIds ?? [receipt.orderId]).includes(order.id) || !receipt.confirmed) continue
    for (const line of receipt.lines) quantities.set(line.lineId, (quantities.get(line.lineId) || 0) + Math.max(0, line.arrived - line.rejected))
  }
  if (order.lines.length && order.lines.every(line => (quantities.get(line.id) || 0) >= line.quantity)) return '已收货'
  return order.lines.some(line => (quantities.get(line.id) || 0) > 0) ? '部分收货' : '待收货'
}
export function matchesPurchaseOrderFilters(order: PurchaseOrder, catalog: Product[], supplier: string, total: number, paid: number, receipts: PurchaseReceipt[], filters: PurchaseOrderFilters): boolean {
  const includes = (value: string, query: string) => value.toLowerCase().includes(query.trim().toLowerCase())
  if (!includes(order.code, filters.code)) return false
  if (filters.supplier && supplier !== filters.supplier) return false
  const payment = Math.round(paid * 100) <= 0 ? '未付款' : Math.round(paid * 100) >= Math.round(total * 100) ? '已付款' : '部分付款'
  if (filters.paymentStatus && payment !== filters.paymentStatus) return false
  if (filters.receiptStatus && purchaseReceiptStatus(order, receipts) !== filters.receiptStatus) return false
  if (filters.dates?.some(Boolean)) {
    const date = order.signed?.slice(0, 10)
    if (!date || (filters.dates[0] && date < filters.dates[0]) || (filters.dates[1] && date > filters.dates[1])) return false
  }
  if (!Object.values(filters.product).some(value => value.trim())) return true
  return order.lines.some(line => {
    const product = catalog.find(p => p.id === line.productId)
    return includes(product?.brand || '', filters.product.brand)
      && includes(product?.model || '', filters.product.model)
      && includes(product?.packaging || '', filters.product.packaging)
  })
}
