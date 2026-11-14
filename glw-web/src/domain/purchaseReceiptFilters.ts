/** 产品入库列表的多条件筛选，与采购订单筛选弹窗保持同一交互。 */
export interface PurchaseReceiptFilters {
  text: {
    code: string
    orderCode: string
    supplier: string
  }
  product: {
    brands: string
    productNames: string
    models: string
  }
  dates: string[] | null
  status: '' | '已入库' | '待入库'
}

export function emptyPurchaseReceiptFilters(): PurchaseReceiptFilters {
  return {
    text: { code: '', orderCode: '', supplier: '' },
    product: { brands: '', productNames: '', models: '' },
    dates: null,
    status: '',
  }
}

export function purchaseReceiptFilterCount(filters: PurchaseReceiptFilters): number {
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

export function matchesPurchaseReceiptFilters(
  values: Record<string, string | number>,
  filters: PurchaseReceiptFilters,
): boolean {
  return (
    includes(values.code, filters.text.code) &&
    includes(values.orderCode, filters.text.orderCode) &&
    includes(values.supplier, filters.text.supplier) &&
    includes(values.brands, filters.product.brands) &&
    includes(values.productNames, filters.product.productNames) &&
    includes(values.models, filters.product.models) &&
    (!filters.status || values.status === filters.status) &&
    inRange(values.receivedAt, filters.dates)
  )
}
