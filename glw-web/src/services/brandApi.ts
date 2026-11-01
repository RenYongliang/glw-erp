import { apiRequest } from './api'
import { fetchAllPages, type PageResponse } from './salesApiData'

export interface BrandOption {
  id: number
  code: string | null
  name: string
  manufacturer: string | null
}
export interface BrandRecord {
  id: number
  version: number
  code: string | null
  name: string
  manufacturer: string | null
  enabled: boolean
  remark: string | null
}
export type BrandInput = Omit<BrandRecord, 'id' | 'version'> & { version?: number }

export const fetchBrands = () =>
  fetchAllPages((pageNo, pageSize) =>
    apiRequest<PageResponse<BrandRecord>>('/brand/list', {
      method: 'POST',
      body: JSON.stringify({ pageNo, pageSize }),
    }),
  )
export const fetchBrandOptions = () => apiRequest<BrandOption[]>('/brand/options')
export function saveBrand(input: BrandInput, id?: number) {
  return apiRequest<BrandRecord>(`/brand${id ? '/' + id : ''}`, {
    method: id ? 'PUT' : 'POST',
    body: JSON.stringify(input),
  })
}
export function deleteBrandRecord(row: { id: number; version: number }) {
  return apiRequest<void>(`/brand/${row.id}?version=${row.version}`, { method: 'DELETE' })
}
