import type { Product } from '../domain/sales'

export interface ProductOptionResponse {
  id: number | string
  brand: string | null
  partNo: string
  packageType: string | null
  stockQuantity: number
  estimatedCost: number | string | null
  minCost: number | string | null
  maxCost: number | string | null
}

export interface SalesProductOption extends Product {
  estimatedCost: number | null
  minCost: number | null
  maxCost: number | null
}

export interface ProductCostBatchResponse {
  inventoryId: number | string
  batchNo: string
  quantity: number
  unitCost: number | string
}

export function toProductCostBatch(row: ProductCostBatchResponse) {
  return { inventoryId: String(row.inventoryId), batchNo: row.batchNo,
    quantity: Number(row.quantity), unitCost: Number(row.unitCost) }
}
export type ProductCostBatch = ReturnType<typeof toProductCostBatch>

const cost = (value: number | string | null) => value === null ? null : Number(value)
export function toSalesProductOption(row: ProductOptionResponse): SalesProductOption {
  return {
    id: String(row.id), code: String(row.id), brand: row.brand ?? '',
    name: row.partNo, model: row.packageType ?? '', unit: '个',
    stock: Number(row.stockQuantity), price: 0, tax: 13, cost: Number(row.estimatedCost ?? 0),
    estimatedCost: cost(row.estimatedCost), minCost: cost(row.minCost), maxCost: cost(row.maxCost),
  }
}

export const formatUnitCost = (value: number | null) => value === null ? '—' : value.toFixed(4)
export function formatCostRange(product: Pick<SalesProductOption, 'minCost' | 'maxCost'>) {
  if (product.minCost === null || product.maxCost === null) return '—'
  return product.minCost === product.maxCost ? formatUnitCost(product.minCost)
    : `${formatUnitCost(product.minCost)} ~ ${formatUnitCost(product.maxCost)}`
}
