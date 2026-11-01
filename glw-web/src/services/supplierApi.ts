import { apiRequest } from './api'
import { fetchAllPages, type PageResponse } from './salesApiData'

export interface SupplierOption {
  id: number
  code: string | null
  name: string
  shortName: string | null
}
export interface SupplierRecord {
  id: number
  version: number
  code: string | null
  name: string
  shortName: string | null
  category: string | null
  enabled: boolean
  address: string | null
  contact: string | null
  contactPhone: string | null
  taxNo: string | null
  bankName: string | null
  bankAccount: string | null
  accountName: string | null
  remark: string | null
  brands: string[]
}
export interface SupplierBrandRecord {
  id: number
  version: number
  supplierId: number
  supplierName: string
  brandId: number
  brandName: string
  remark: string | null
}
export type SupplierInput = Omit<SupplierRecord, 'id' | 'version' | 'brands'> & { version?: number }
export type SupplierBrandInput = Omit<
  SupplierBrandRecord,
  'id' | 'version' | 'supplierName' | 'brandName'
> & { version?: number }

export const fetchSuppliers = () =>
  fetchAllPages((pageNo, pageSize) =>
    apiRequest<PageResponse<SupplierRecord>>('/supplier/list', {
      method: 'POST',
      body: JSON.stringify({ pageNo, pageSize }),
    }),
  )
export const fetchSupplierOptions = () => apiRequest<SupplierOption[]>('/supplier/options')
export const fetchSupplierBrands = (supplierId?: number) =>
  apiRequest<SupplierBrandRecord[]>(
    `/supplier-brand/list${supplierId ? `?supplierId=${supplierId}` : ''}`,
  )
export function saveSupplier(input: SupplierInput, id?: number) {
  return apiRequest<SupplierRecord>(`/supplier${id ? '/' + id : ''}`, {
    method: id ? 'PUT' : 'POST',
    body: JSON.stringify(input),
  })
}
export function saveSupplierBrand(input: SupplierBrandInput, id?: number) {
  return apiRequest<SupplierBrandRecord>(`/supplier-brand${id ? '/' + id : ''}`, {
    method: id ? 'PUT' : 'POST',
    body: JSON.stringify(input),
  })
}
export function deleteSupplierRecord(row: { id: number; version: number }) {
  return apiRequest<void>(`/supplier/${row.id}?version=${row.version}`, { method: 'DELETE' })
}
export function deleteSupplierBrand(row: { id: number; version: number }) {
  return apiRequest<void>(`/supplier-brand/${row.id}?version=${row.version}`, { method: 'DELETE' })
}
