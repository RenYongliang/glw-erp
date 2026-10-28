import { apiRequest, authClient } from './api'
import type { PageResponse } from './salesApiData'

export type Direction = 'AR' | 'AP'
export type Money = number | string
export interface FinanceQuery {
  direction: Direction
  partnerId?: number
  period?: string
  keyword?: string
  status?: string
  unsettledOnly?: boolean
  overdueOnly?: boolean
  pageNo: number
  pageSize: number
}
export interface Partner {
  id: number
  name: string
  shortName?: string
}
export interface OpenItem {
  id: number
  direction: Direction
  partnerId: number
  partnerName: string
  sourceNo: string
  orderNo: string
  partnerOrderNo?: string
  brand?: string
  partNo: string
  packageType?: string
  quantity: number
  unitPrice: Money
  taxIncluded: number
  taxRate?: number | null
  netAmount?: Money | null
  taxAmount?: Money | null
  grossAmount: Money | null
  settledAmount: Money
  currency: string
  period: string
  bizDate: string
  dueDate: string
  reconcileStatus: string
  statementId?: number
}
export interface Statement {
  id: number
  statementNo: string
  direction: Direction
  partnerId: number
  partnerName: string
  period: string
  currency: string
  grossAmount: Money
  settledAmount: Money
  status: string
  remark: string
}
export interface Payment {
  id: number
  paymentNo: string
  direction: Direction
  partnerId: number
  partnerName: string
  paymentDate: string
  currency: string
  amount: Money
  allocatedAmount: Money
  status: string
  bankReference: string
  remark: string
}
export interface Allocation {
  id: number
  paymentId: number
  openItemId: number
  amount: Money
  status: string
  sourceNo: string
  orderNo: string
  paymentNo: string
  createdAt: string
  reversedAt?: string
}
export interface StatementDetail {
  statement: Statement
  items: OpenItem[]
  allocations: Allocation[]
}
export interface PaymentDetail {
  payment: Payment
  allocations: Allocation[]
}
export interface Profile {
  id?: number
  direction: Direction
  partnerId: number
  partnerName?: string
  termDays: number
  termBasis: 'BIZ_DATE' | 'MONTH_END'
}
export interface Summary {
  grossAmount: Money
  settledAmount: Money
  outstandingAmount: Money
  overdueAmount: Money
  unallocatedAmount: Money
  pendingTaxCount: number
}
export interface PaymentInput {
  requestKey: string
  direction: Direction
  partnerId?: number
  paymentDate: string
  amount?: number
  bankReference: string
  remark: string
}
const post = <T>(path: string, body: object = {}) =>
  apiRequest<T>(`/fin/${path}`, { method: 'POST', body: JSON.stringify(body) })
export const financeApi = {
  openItems: (q: FinanceQuery) => post<PageResponse<OpenItem>>('open-item/list', q),
  preview: (q: FinanceQuery) => post<OpenItem[]>('statement/preview', q),
  summary: (q: FinanceQuery) => post<Summary>('open-item/summary', q),
  rebuild: () => post<number>('open-item/rebuild'),
  resolveTax: (id: number, taxRate: number) =>
    post<OpenItem>(`open-item/${id}/tax-rate`, { taxRate }),
  generate: (ids: number[], remark: string) =>
    post<StatementDetail>('statement/generate', { openItemIds: ids, remark }),
  statements: (q: FinanceQuery) => post<PageResponse<Statement>>('statement/list', q),
  statement: (id: number) => apiRequest<StatementDetail>(`/fin/statement/${id}`),
  transition: (id: number, action: 'send' | 'confirm' | 'void') =>
    post<StatementDetail>(`statement/${id}/${action}`),
  payments: (q: FinanceQuery) => post<PageResponse<Payment>>('payment/list', q),
  createPayment: (input: PaymentInput) => post<PaymentDetail>('payment/create', input),
  payment: (id: number) => apiRequest<PaymentDetail>(`/fin/payment/${id}`),
  voidPayment: (id: number) => post<void>(`payment/${id}/void`),
  allocate: (paymentId: number, openItemId: number, amount: number, requestKey: string) =>
    post<Allocation>('clearing/allocate', { paymentId, openItemId, amount, requestKey }),
  reverse: (id: number) => post<void>(`clearing/${id}/reverse`),
  partners: (direction: Direction) => apiRequest<Partner[]>(`/fin/partners?direction=${direction}`),
  profiles: (direction: Direction) =>
    apiRequest<Profile[]>(`/fin/profile/list?direction=${direction}`),
  saveProfile: (input: Profile) => post<void>('profile/save', input),
}
export async function exportStatement(id: number, name: string) {
  const blob = await authClient.request<Blob>(`/fin/statement/${id}/export`, {}, false, true)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${name}.csv`
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
