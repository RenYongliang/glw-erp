import { apiRequest } from './api'
import { fetchAllPages, type PageResponse } from './salesApiData'

interface CustomerResponse {
  id: number
  name: string
  shortName?: string | null
  address?: string | null
  contact?: string | null
  contactPhone?: string | null
  settleType?: string | null
}

export interface CustomerOption {
  id: string
  name: string
  shortName: string
  address: string
  contact: string
  phone: string
  settlement: string
}

export async function fetchCustomerOptions(): Promise<CustomerOption[]> {
  const rows = await fetchAllPages((pageNo, pageSize) => apiRequest<PageResponse<CustomerResponse>>(
    '/customer/list', { method: 'POST', body: JSON.stringify({ pageNo, pageSize }) },
  ))
  const settlements: Record<string, string> = { CASH: '现金', MONTHLY: '月结', PREPAID: '预付', COD: '货到付款' }
  return rows.map(row => ({
    id: String(row.id), name: row.name, shortName: row.shortName || '',
    address: row.address || '', contact: row.contact || '', phone: row.contactPhone || '',
    settlement: row.settleType ? settlements[row.settleType] || row.settleType : '—',
  }))
}
