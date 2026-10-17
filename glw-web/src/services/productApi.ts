import { apiRequest } from './api'
import { fetchAllPages, type PageResponse } from './salesApiData'
import { toSalesProductOption, toProductCostBatch, type ProductOptionResponse, type ProductCostBatchResponse } from './productApiData'

export async function fetchSalesProducts() {
  const rows = await fetchAllPages((pageNo, pageSize) => apiRequest<PageResponse<ProductOptionResponse>>(
    '/product/list', { method: 'POST', body: JSON.stringify({ pageNo, pageSize }) },
  ))
  return rows.map(toSalesProductOption)
}

export async function fetchProductCostBatches(productId: string) {
  const rows = await apiRequest<ProductCostBatchResponse[]>(`/product/${encodeURIComponent(productId)}/cost-batches`)
  return rows.map(toProductCostBatch)
}
