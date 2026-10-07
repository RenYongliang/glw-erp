import type { Product, SalesOrder, SalesShipment } from './sales'

export type FulfillmentStatus = '待发货' | '部分发货' | '已完成'
export type NumberRange = [number | null, number | null]
export interface SalesOrderFilters {
  text: Record<'code' | 'customer' | 'purchaseNumber' | 'term' | 'owner' | 'brands' | 'productNames' | 'models', string>
  dates: Record<'signed' | 'delivery', string[] | null>
  numbers: Record<'total' | 'prices' | 'ordered' | 'amounts', NumberRange>
  status: FulfillmentStatus | ''
  paymentStatus: '' | '未收款' | '部分收款' | '已收款'
}
export function emptySalesOrderFilters(): SalesOrderFilters {
  return {
    text: { code: '', customer: '', purchaseNumber: '', term: '', owner: '', brands: '', productNames: '', models: '' },
    dates: { signed: null, delivery: null },
    numbers: { total: [null, null], prices: [null, null], ordered: [null, null], amounts: [null, null] },
    status: '',
    paymentStatus: '',
  }
}
export function salesFulfillmentStatus(order: SalesOrder, shipments: SalesShipment[]): FulfillmentStatus {
  const delivered = new Map<string, number>()
  for (const shipment of shipments) {
    if (shipment.orderId !== order.id || !shipment.confirmed || shipment.tag !== '出库') continue
    for (const line of shipment.lines) delivered.set(line.lineId, (delivered.get(line.lineId) || 0) + Math.max(0, line.arrived || 0))
  }
  if (order.lines.length && order.lines.every(l => (delivered.get(l.id) || 0) >= l.quantity)) return '已完成'
  return order.lines.some(l => (delivered.get(l.id) || 0) > 0) ? '部分发货' : '待发货'
}
export function salesOrderMonth(date: string): string {
  const match = /^(\d{4})-(0[1-9]|1[0-2])-\d{2}/.exec(date)
  return match ? `${match[1]}年${match[2]}月` : '未填写订单日期'
}
export function salesFilterCount(filters: SalesOrderFilters): number {
  return Object.values(filters.text).filter(v => v.trim()).length
    + Object.values(filters.dates).filter(v => v?.some(Boolean)).length
    + Object.values(filters.numbers).filter(v => v.some(n => n != null)).length
    + Number(!!filters.status) + Number(!!filters.paymentStatus)
}
const includes = (value: string, query: string) => value.toLowerCase().includes(query.trim().toLowerCase())
const inRange = (value: number, range: NumberRange) => (range[0] == null || value >= range[0]) && (range[1] == null || value <= range[1])
export function matchesSalesOrderFilters(order: SalesOrder, catalog: Product[], customer: string, total: number, status: FulfillmentStatus, filters: SalesOrderFilters, paid = 0): boolean {
  const text = {code: order.code, customer, purchaseNumber: order.purchaseNumber || '', term: order.term, owner: order.owner}
  for (const key of Object.keys(text) as (keyof typeof text)[]) if (!includes(text[key], filters.text[key])) return false
  if (filters.text.customer && customer !== filters.text.customer) return false
  if (filters.paymentStatus && salesPaymentStatus(total, paid) !== filters.paymentStatus) return false
  if (filters.status && filters.status !== status) return false
  for (const key of ['signed', 'delivery'] as const) {
    const range = filters.dates[key]
    if (!range?.some(Boolean)) continue
    const date = order[key]?.slice(0, 10)
    if (!date || (range[0] && date < range[0]) || (range[1] && date > range[1])) return false
  }
  if (!inRange(total, filters.numbers.total)) return false
  const hasProductFilter = ['brands', 'productNames', 'models'].some(k => filters.text[k as keyof typeof filters.text].trim())
    || [filters.numbers.prices, filters.numbers.ordered, filters.numbers.amounts].some(r => r.some(v => v != null))
  if (!hasProductFilter) return true
  // Product conditions must match the same line, not unrelated products in the order.
  return order.lines.some(line => {
    const product = catalog.find(p => p.id === line.productId)
    return includes(product?.brand || '', filters.text.brands)
      && includes(product?.name || '', filters.text.productNames)
      && includes(product?.model || '', filters.text.models)
      && inRange(line.price, filters.numbers.prices)
      && inRange(line.quantity, filters.numbers.ordered)
      && inRange(line.price * line.quantity, filters.numbers.amounts)
  })
}

export function salesPaymentStatus(total: number, paid: number): '未收款' | '部分收款' | '已收款' {
  if (Math.round(paid * 100) <= 0) return '未收款'
  return Math.round(paid * 100) >= Math.round(total * 100) ? '已收款' : '部分收款'
}
