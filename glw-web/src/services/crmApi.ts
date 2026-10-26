import { apiRequest } from './api'
import { fetchAllPages, type PageResponse } from './salesApiData'

export interface CustomerRecord {
  id: number
  version: number
  name: string
  shortName: string | null
  address: string | null
  contact: string | null
  contactPhone: string | null
  settleType: string | null
  category: string | null
}
export interface VisitRecord {
  id: number
  version: number
  customerId: number
  customerName?: string
  customerShortName?: string
  visitTime: string
  project: string | null
  originalPartNo: string | null
  productId: number | null
  brand?: string
  partNo?: string
  packageType?: string
  demandQuantity: number | null
  unitPrice: number | string
  taxIncluded: number
  progress: string | null
  stage: string | null
  detail: string | null
}
export type CustomerInput = Omit<CustomerRecord, 'id' | 'version'> & { version?: number }
export type VisitInput = Pick<VisitRecord, 'customerId' | 'visitTime' | 'project' | 'originalPartNo' | 'productId' | 'demandQuantity' | 'unitPrice' | 'taxIncluded' | 'progress' | 'stage' | 'detail'> & { version?: number }
export const categoryOptions = [
  { value: '潜在客户', label: '潜在客户', color: '#c77c11' },
  { value: '成交客户', label: '成交客户', color: '#009c75' },
  { value: '战略合作', label: '战略客户', color: '#3980cf' },
]
export const stageOptions = [
  { value: '初次沟通', color: '#d35b5b' }, { value: '需求沟通', color: '#c58c13' },
  { value: '方案确认', color: '#389867' }, { value: '报价', color: '#8c981c' },
  { value: '合同签署', color: '#009cb9' }, { value: '销售回访', color: '#6863c5' },
]
export const settlementOptions = [
  { value: 'CASH', label: '现金' }, { value: 'MONTHLY', label: '月结' },
  { value: 'PREPAID', label: '预付' }, { value: 'COD', label: '货到付款' },
]
export const settlementLabel = (value?: string | null) => settlementOptions.find(option => option.value === value)?.label || value || '—'
export const fetchCrmCustomers = () => fetchAllPages((pageNo, pageSize) => apiRequest<PageResponse<CustomerRecord>>('/customer/list', { method: 'POST', body: JSON.stringify({ pageNo, pageSize }) }))
export const fetchCrmVisits = () => fetchAllPages((pageNo, pageSize) => apiRequest<PageResponse<VisitRecord>>('/customer-visit/list', { method: 'POST', body: JSON.stringify({ pageNo, pageSize }) }))
export function saveCustomer(input: CustomerInput, id?: number) {
  return apiRequest<CustomerRecord>(`/customer${id ? '/' + id : ''}`, { method: id ? 'PUT' : 'POST', body: JSON.stringify(input) })
}
export function saveVisit(input: VisitInput, id?: number) {
  return apiRequest<VisitRecord>(`/customer-visit${id ? '/' + id : ''}`, { method: id ? 'PUT' : 'POST', body: JSON.stringify(input) })
}
export function deleteCrmRecord(kind: 'customers' | 'followups', row: CustomerRecord | VisitRecord) {
  return apiRequest<void>(`/${kind === 'customers' ? 'customer' : 'customer-visit'}/${row.id}?version=${row.version}`, { method: 'DELETE' })
}
