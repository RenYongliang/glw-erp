import { apiRequest } from './api'
import type { PageResponse } from './salesApiData'

export interface InventoryBatch {
  inventoryId: number | string
  productId: number | string
  dateCode: string
  lotCode: string
  batchNo: string
  quantity: number
  availableQuantity: number
  unitCost: number | string
  stockValue: number | string
  inboundTime: string | null
}

export interface InventoryProduct {
  productId: number | string
  brand: string | null
  partNo: string
  packageType: string | null
  availableQuantity: number
  batchCount: number
  averageCost: number | string | null
  minCost: number | string | null
  maxCost: number | string | null
  stockValue: number | string
  batches: InventoryBatch[]
}

export interface InventoryQuery {
  pageNo: number
  pageSize: number
  inStockOnly: boolean
  keyword: string
  brand: string
  partNo: string
  packageType: string
}

export function fetchInventory(query: InventoryQuery) {
  return apiRequest<PageResponse<InventoryProduct>>('/inventory/list', {
    method: 'POST', body: JSON.stringify(query),
  })
}
