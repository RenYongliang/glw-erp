/** 产品出库列表的多条件筛选，与销售订单筛选弹窗保持同一交互。 */
export interface SalesShipmentFilters {
  text: {
    code: string
    orderCode: string
    customer: string
    purchaseNumber: string
  }
  product: {
    brands: string
    productNames: string
    models: string
  }
  dates: string[] | null
  status: '' | '已出库' | '待出库'
}

export function emptySalesShipmentFilters(): SalesShipmentFilters {
  return {
    text: { code: '', orderCode: '', customer: '', purchaseNumber: '' },
    product: { brands: '', productNames: '', models: '' },
    dates: null,
    status: '',
  }
}

export function salesShipmentFilterCount(filters: SalesShipmentFilters): number {
  return (
    Object.values(filters.text).filter((value) => value.trim()).length +
    Object.values(filters.product).filter((value) => value.trim()).length +
    Number(!!filters.status) +
    Number(!!filters.dates?.some(Boolean))
  )
}

function includes(value: unknown, term: string): boolean {
  const keyword = term.trim()
  return !keyword || String(value ?? '').toLowerCase().includes(keyword.toLowerCase())
}

function inRange(value: unknown, dates: string[] | null): boolean {
  if (!dates?.some(Boolean)) return true
  const date = String(value ?? '').slice(0, 10)
  if (!date || date === '—') return false
  const [start, end] = dates
  return (!start || date >= start) && (!end || date <= end)
}

export function matchesSalesShipmentFilters(
  values: Record<string, string | number>,
  filters: SalesShipmentFilters,
): boolean {
  return (
    includes(values.code, filters.text.code) &&
    includes(values.orderCode, filters.text.orderCode) &&
    includes(values.customer, filters.text.customer) &&
    includes(values.purchaseNumber, filters.text.purchaseNumber) &&
    includes(values.brands, filters.product.brands) &&
    includes(values.productNames, filters.product.productNames) &&
    includes(values.models, filters.product.models) &&
    (!filters.status || values.status === filters.status) &&
    inRange(values.shippedAt, filters.dates)
  )
}
