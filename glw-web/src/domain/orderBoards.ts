import source from '../data/orderBoards.json'
export type BoardKind = 'sales' | 'purchase'
export interface ProductLine {
  brand?: string
  package?: string
  product: string
  code: string
  quantity: number
  price: number
  amount: number
}
export interface BoardOrder {
  paymentTerm?: string
  id: string
  name: string
  partner: string
  warehouse: string
  amount: number
  owner: string
  signed: string
  delivery: string
  approval: string
  lines: ProductLine[]
  plans: { batch: string; date: string; note: string }[]
}
export interface ExecutionLine {
  id: string
  name: string
  partner: string
  product: string
  code: string
  quantity: number
  returnedBefore: number
  shipped: number
  returnedAfter: number
  exchanged?: number
  pending: number
  frozen?: number
  planned?: number
  unplanned?: number
  settled: number
  unsettled: number
  payable: number
  paid: number
  unpaid: number
}
export const boardData: Record<BoardKind, { orders: BoardOrder[]; execution: ExecutionLine[] }> =
  source
export const money = (value: number) =>
  value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
// The source template sums product purchase amounts but sales order amounts.
export const orderMetric = (order: BoardOrder, kind: BoardKind) =>
  kind === 'purchase' ? order.lines.reduce((sum, line) => sum + line.amount, 0) : order.amount
export function executionTag(line: ExecutionLine, tag: string) {
  if (!tag) return true
  if (tag === '待出库' || tag === '待入库') return line.pending > 0
  if (tag === '待对账') return line.unsettled > 0
  if (tag === '待收款' || tag === '待付款') return line.unpaid > 0
  return line.paid > 0 && line.unpaid === 0
}
