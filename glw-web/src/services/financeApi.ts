import { apiRequest, authClient } from './api'
import type { DownloadFile } from './authClient'
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
export type StatementFormat = 'XLSX' | 'PDF'
export type DocumentTypeCode =
  | 'STATEMENT'
  | 'SALES_CONTRACT'
  | 'PURCHASE_CONTRACT'
  | 'DELIVERY_NOTE'
export interface DocumentTypeOption {
  code: DocumentTypeCode
  label: string
  directional: boolean
  directions: Direction[]
}
export interface StatementTemplate {
  id: number
  partnerId: number
  docType: DocumentTypeCode
  direction: Direction
  format: StatementFormat
  name: string
  fileName: string
  version: number
  isDefault: number
  enabled: number
  /** 可视化设计器创建的模板，可继续在设计器中编辑。 */
  hasLayout?: boolean
  createdAt: string
}
export interface LayoutColumn {
  title: string
  field: string
  width: number
  align?: 'left' | 'center' | 'right'
}
export interface LayoutComponent {
  id: string
  type: 'text' | 'field' | 'table' | 'signature'
  x: number
  y: number
  w: number
  h: number
  text?: string
  label?: string
  field?: string
  fontSize?: number
  bold?: boolean
  align?: 'left' | 'center' | 'right'
  border?: boolean
  headerHeight?: number
  rowHeight?: number
  headerBold?: boolean
  columns?: LayoutColumn[]
}
export interface StatementLayout {
  page: {
    size: 'A4'
    orientation: 'PORTRAIT' | 'LANDSCAPE'
    marginMm: number
    gridMm: number
  }
  components: LayoutComponent[]
}
export interface StatementLayoutDetail {
  template: StatementTemplate
  layout: StatementLayout
}
export const statementTemplateApi = {
  docTypes: () => apiRequest<DocumentTypeOption[]>('/fin/statement/template/doc-types'),
  list: (docType: DocumentTypeCode, direction: Direction, partnerId?: number) =>
    apiRequest<StatementTemplate[]>(
      `/fin/statement/template/list?docType=${docType}&direction=${direction}${
        partnerId === undefined ? '' : `&partnerId=${partnerId}`
      }`,
    ),
  setDefault: (id: number) =>
    apiRequest<StatementTemplate>(`/fin/statement/template/${id}/default`, { method: 'POST' }),
  delete: (id: number) =>
    apiRequest<StatementTemplate>(`/fin/statement/template/${id}/delete`, { method: 'POST' }),
  layout: (id: number) =>
    apiRequest<StatementLayoutDetail>(`/fin/statement/template/${id}/layout`),
  saveLayout: (input: {
    docType: DocumentTypeCode
    direction: Direction
    partnerId: number
    name: string
    isDefault?: boolean
    format: StatementFormat
    layout: StatementLayout
  }) => post<StatementTemplate>('statement/template/layout', input),
  previewLayout: (
    layout: StatementLayout,
    format: StatementFormat,
    docType: DocumentTypeCode,
    direction: Direction,
  ) =>
    authClient.request<DownloadFile>(
      `/fin/statement/template/layout/preview?format=${format}&docType=${docType}&direction=${direction}`,
      { method: 'POST', body: JSON.stringify(layout) },
      false,
      'download',
    ),
}
export async function exportStatement(id: number, name: string) {
  const file = await authClient.request<DownloadFile>(
    `/fin/statement/${id}/export`,
    {},
    false,
    'download',
  )
  const url = URL.createObjectURL(file.blob)
  const a = document.createElement('a')
  a.href = url
  a.download = file.fileName || `${name}.csv`
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
