import { apiRequest } from './api'

export interface CustomerProductCode {
  id: number
  customerId: number
  productId: number
  brand?: string | null
  partNo?: string | null
  packageType?: string | null
  customerCode: string
  source?: string | null
  enabled: boolean
  remark?: string | null
  version: number
}

export interface CustomerProductCodeInput {
  customerId?: number
  productId: number
  customerCode: string
  enabled?: boolean
  remark?: string | null
  version?: number
}

export async function fetchCustomerProductCodes(customerId: string) {
  return apiRequest<CustomerProductCode[]>(
    `/customer-product-code?customerId=${encodeURIComponent(customerId)}`,
  )
}

export async function saveCustomerProductCode(input: CustomerProductCodeInput, id?: number) {
  return apiRequest<CustomerProductCode>(
    id ? `/customer-product-code/${id}` : '/customer-product-code',
    { method: id ? 'PUT' : 'POST', body: JSON.stringify(input) },
  )
}

export async function deleteCustomerProductCode(id: number, version: number) {
  await apiRequest<unknown>(
    `/customer-product-code/${id}?version=${encodeURIComponent(version)}`,
    { method: 'DELETE' },
  )
}
