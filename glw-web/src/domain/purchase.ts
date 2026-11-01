import catalog from '../data/products.json' with { type: 'json' }
import { nextDocumentNumber } from './documentNumbers.ts'
export type PurchaseKind = 'orders' | 'receipts'
export interface Product {
  id: string
  name: string
  code: string
  supplierId?: string
  brand: string
  model: string
  packaging?: string
  unit: string
  price: number
  tax: number
  stock: number
}
export interface Supplier {
  id: string
  name: string
  code: string
  contact: string
  phone: string
  bank: string
  account: string
  enabled?: boolean
}
export interface PurchaseLine {
  delivery?: string
  remark?: string
  brand?: string
  model?: string
  packaging?: string
  amount?: number
  id: string
  productId: string
  quantity: number
  pendingQuantity?: number
  price: number
  taxIncluded?: boolean
  tax: number
}
export interface ArrivalPlan {
  batch: string
  date: string
  note: string
}
export interface PaymentPlan {
  name: string
  ratio: number
  method: string
  date: string
  note: string
}
export interface Attachment {
  name: string
  url: string
}
export interface PurchaseOrder {
  supplierName?: string
  supplierShortName?: string
  contact?: string
  phone?: string
  id: string
  code: string
  name: string
  supplierId: string
  signed: string
  delivery: string
  owner: string
  department: string
  warehouse: string
  address: string
  discount: number
  lines: PurchaseLine[]
  arrivals: ArrivalPlan[]
  payments: PaymentPlan[]
  paymentStatus?: '未付款' | '部分付款' | '已付款'
  attachments: Attachment[]
  term: string
  status: '待收货' | '部分收货' | '已完成'
  reason: string
  approver: string
  approvedAt: string
  /** 后端乐观锁版本号，修改已保存的订单时回传。 */
  version?: number
}
export interface ReceiptBatch {
  id: string
  dateCode: string
  lotCode: string
  quantity: number
}
export interface ReceiptLine {
  orderId?: string
  productId?: string
  brand?: string
  model?: string
  packaging?: string
  unitPrice?: number
  lineId: string
  arrived: number
  rejected: number
  location: string
  batches: ReceiptBatch[]
}
export interface PurchaseReceipt {
  orderIds?: string[]
  supplierName?: string
  id: string
  code: string
  orderId: string
  warehouse: string
  lines: ReceiptLine[]
  quality: '合格' | '存在不合格品'
  qualityNote: string
  inspector: string
  inspectionDepartment: string
  inspectedAt: string
  confirmed: boolean
  receivedAt: string
  keeper: string
  department: string
  photos: Attachment[]
}
export interface PurchasePayment {
  id: string
  orderId: string
  amount: number
  date: string
  method: string
  note: string
}
export interface PurchaseReturn {
  id: string
  orderId: string
  lineId: string
  quantity: number
  date: string
  reason: string
}
export interface PurchaseState {
  orders: PurchaseOrder[]
  receipts: PurchaseReceipt[]
  payments: PurchasePayment[]
  returns: PurchaseReturn[]
  logs: { id: string; time: string; action: string; name: string }[]
}
export const purchaseStorageKey = 'glw.purchase.v1'
export const suppliers: Supplier[] = [
  {
    id: 's1',
    name: '示例：杭州道友云',
    code: 'GYS002',
    contact: '纪泽',
    phone: '17777777772',
    bank: '中国银行',
    account: '6111111111111112',
  },
  {
    id: 's2',
    name: '示例：上海道友云',
    code: 'GYS003',
    contact: '泰叔',
    phone: '17777777775',
    bank: '招商银行',
    account: '6225888811110003',
  },
  {
    id: 's3',
    name: '示例：无锡道友云',
    code: 'GYS001',
    contact: '徐忠',
    phone: '17777777777',
    bank: '工商银行',
    account: '6222000011110001',
  },
]
// Purchase and sales share the same catalog. Purchase model is the part number.
export const products: Product[] = catalog.map(p => ({
  id: p.code,
  code: p.code,
  name: p.name,
  brand: p.brand,
  model: p.name,
  packaging: p.model,
  unit: p.unit,
  price: p.cost,
  tax: 13,
  stock: 150000,
}))
export function purchaseLineProductText(line: PurchaseLine | undefined, key: string): string {
  if (!line || !['brand', 'model', 'packaging'].includes(key)) return ''
  const field = key as 'brand' | 'model' | 'packaging'
  return line[field] ?? products.find(product => product.id === line.productId)?.[field] ?? ''
}
// Keep previously saved demo orders readable after replacing the six legacy products.
export const legacyPurchaseProductIds: Record<string, string> = Object.fromEntries(
  Array.from({ length: 6 }, (_, index) => [`p${index + 1}`, products[index % products.length]!.id]),
)
export const warehouses = ['杭州仓', '无锡仓']
export const money = (n: number) =>
  n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
export const roundMoney = (n: number) => Math.round((n + Number.EPSILON) * 100) / 100
export const today = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
export function nextPurchaseCode(prefix: string, rows: { code: string }[], date = today()) {
  return nextDocumentNumber(prefix, rows, date)
}
export function orderTotals(order: Pick<PurchaseOrder, 'lines' | 'discount'>) {
  const cents = order.lines.map((l) => Math.round(l.quantity * l.price * 100))
  const grossCents = cents.reduce((a, b) => a + b, 0)
  const discountCents = Math.min(grossCents, Math.max(0, Math.round((order.discount || 0) * 100)))
  let allocated = 0
  const details = order.lines.map((l, i) => {
    const discount =
      i === order.lines.length - 1
        ? discountCents - allocated
        : Math.floor((discountCents * (cents[i] || 0)) / (grossCents || 1))
    allocated += discount
    const total = ((cents[i] || 0) - discount) / 100
    return {
      gross: (cents[i] || 0) / 100,
      total,
      unit: l.quantity ? total / l.quantity : 0,
      // 含税单价按税率倒算不含税金额；不含税单价本身即为不含税金额。
      net: roundMoney(l.taxIncluded === false ? total : total / (1 + l.tax / 100)),
    }
  })
  return {
    gross: grossCents / 100,
    total: (grossCents - discountCents) / 100,
    rate: grossCents ? ((grossCents - discountCents) / grossCents) * 100 : 100,
    net: roundMoney(details.reduce((a, b) => a + b.net, 0)),
    details,
  }
}
export function pendingDeliveryQuantity(
  order: PurchaseOrder,
  line: PurchaseLine,
  receipts: PurchaseReceipt[],
) {
  if (line.pendingQuantity !== undefined) return Math.max(0, line.pendingQuantity)
  const received = receipts
    .filter(receipt => receiptMatchesOrder(receipt, order.id) && receipt.confirmed)
    .flatMap(receipt => receipt.lines)
    .filter(receiptLine => receiptLine.lineId === line.id)
    .reduce((sum, receiptLine) => sum + Math.max(0, receiptLine.arrived - receiptLine.rejected), 0)
  return Math.max(0, line.quantity - received)
}

export function remainingQuantity(
  state: PurchaseState,
  orderId: string,
  lineId: string,
  excludeReceipt = '',
) {
  const line = state.orders.find((o) => o.id === orderId)?.lines.find((l) => l.id === lineId)
  const ordered = line?.pendingQuantity ?? line?.quantity ?? 0
  return (
    ordered -
    state.receipts
      .filter((r) => receiptMatchesOrder(r, orderId) && r.id !== excludeReceipt &&
        (line?.pendingQuantity === undefined || !r.confirmed))
      .reduce(
        (sum, r) =>
          sum +
          r.lines
            .filter((l) => l.lineId === lineId)
            .reduce((a, l) => a + l.arrived - l.rejected, 0),
        0,
      )
  )
}
export function returnableQuantity(state: PurchaseState, orderId: string, lineId: string) {
  return (
    state.receipts
      .filter((r) => receiptMatchesOrder(r, orderId) && r.confirmed)
      .reduce(
        (sum, r) =>
          sum +
          r.lines
            .filter((l) => l.lineId === lineId)
            .reduce((a, l) => a + l.arrived - l.rejected, 0),
        0,
      ) -
    state.returns
      .filter((r) => r.orderId === orderId && r.lineId === lineId)
      .reduce((a, r) => a + r.quantity, 0)
  )
}
export function receiptLineQuantity(receipt: PurchaseReceipt, lineId: string) {
  return receipt.lines
    .filter(line => line.lineId === lineId)
    .reduce((sum, line) => sum + line.arrived - line.rejected, 0)
}

export function receiptOrderIds(receipt: PurchaseReceipt): string[] {
  return receipt.orderIds ?? (receipt.orderId ? [receipt.orderId] : [])
}
export function receiptMatchesOrder(receipt: PurchaseReceipt, orderId: string) {
  return receiptOrderIds(receipt).includes(orderId)
}
export function receiptOrders(receipt: PurchaseReceipt, orders: PurchaseOrder[]) {
  return receiptOrderIds(receipt).map(id => orders.find(order => order.id === id))
    .filter((order): order is PurchaseOrder => !!order)
}
export function receiptProducts(receipt: PurchaseReceipt, orders: PurchaseOrder[]) {
  const groups = new Map<string, { productId: string; brand: string; model: string; packaging: string; quantity: number; ordered: number; lines: ReceiptLine[] }>()
  for (const line of receipt.lines) {
    const source = orders.flatMap(order => order.lines).find(item => item.id === line.lineId)
    const productId = line.productId || source?.productId || line.lineId
    const group = groups.get(productId) ?? { productId, brand: line.brand ?? source?.brand ?? '', model: line.model ?? source?.model ?? '', packaging: line.packaging ?? source?.packaging ?? '', quantity: 0, ordered: 0, lines: [] }
    group.quantity += line.arrived - line.rejected
    group.ordered += source?.quantity ?? 0
    group.lines.push(line)
    groups.set(productId, group)
  }
  return [...groups.values()]
}
export function receiptTotals(receipt: PurchaseReceipt, order?: PurchaseOrder | PurchaseOrder[]): { quantity: number; total: number } {
  if (Array.isArray(order)) return order.reduce((sum, po) => {
    const totals = receiptTotals(receipt, po)
    return { quantity: sum.quantity + totals.quantity, total: roundMoney(sum.total + totals.total) }
  }, { quantity: 0, total: 0 })
  if (!order) return { quantity: 0, total: 0 }
  const totals = orderTotals(order)
  return receipt.lines.reduce(
    (sum, l) => {
      const quantity = l.arrived - l.rejected,
        i = order.lines.findIndex((p) => p.id === l.lineId)
      if (i < 0) return sum
      return {
        quantity: sum.quantity + quantity,
        total: roundMoney(sum.total + roundMoney(quantity * (totals.details[i]?.unit || 0))),
      }
    },
    { quantity: 0, total: 0 },
  )
}
const dateValid = (value: string) =>
  /^\d{4}-\d{2}-\d{2}/.test(value) && Number.isFinite(Date.parse(value))
export function purchaseSupplierName(order?: PurchaseOrder): string {
  // 后端订单直接带供应商名称；静态供应商列表仅用于兼容历史本地数据。
  return order?.supplierName?.trim() || suppliers.find(s => s.id === order?.supplierId)?.name || ''
}
export function purchaseContact(order?: PurchaseOrder): string {
  return order?.contact ?? suppliers.find(s => s.id === order?.supplierId)?.contact ?? ''
}
export function purchasePhone(order?: PurchaseOrder): string {
  return order?.phone ?? suppliers.find(s => s.id === order?.supplierId)?.phone ?? ''
}
export function validateOrder(order: PurchaseOrder) {
  // 后端供应商 id 为纯数字；静态列表只覆盖历史本地数据的 s1..sn。
  if (
    order.supplierId &&
    !/^\d+$/.test(order.supplierId) &&
    !suppliers.some(s => s.id === order.supplierId)
  )
    return '请选择有效供应商'
  if (!purchaseSupplierName(order)) return '请选择或填写供应商名称'
  if (purchaseSupplierName(order).length > 100) return '供应商名称不能超过100字'
  if (!order.name.trim()) return '请填写采购订单名称'
  if (!dateValid(order.signed)) return '请填写采购日期'
  if (order.lines.some(l => !dateValid(l.delivery ?? order.delivery))) return '请填写每项产品的交付日期'
  if (order.lines.some(l => (l.delivery ?? order.delivery) < order.signed)) return '产品交付日期不能早于采购日期'
  if (order.lines.some(l => (l.remark?.length || 0) > 200)) return '产品备注不能超过200字'
  // 后端采购订单不保存仓库字段，编辑这类订单时允许为空。
  if (!warehouses.includes(order.warehouse) && !(/^\d+$/.test(order.id) && !order.warehouse))
    return '请选择入库仓库'
  if (!order.lines.length) return '请至少添加一条采购产品明细'
  if (new Set(order.lines.map((l) => l.productId)).size !== order.lines.length)
    return '同一产品请合并数量，不要重复添加'
  if (
    order.lines.some(
      (l) =>
        // 后端产品 id 为纯数字；静态目录只覆盖历史本地数据。
        (!/^\d+$/.test(l.productId) && !products.some((p) => p.id === l.productId)) ||
        !Number.isSafeInteger(l.quantity) ||
        l.quantity <= 0 ||
        l.quantity > 100000000 ||
        !Number.isFinite(l.price) ||
        l.price < 0 ||
        l.price > 10000000 ||
        !Number.isFinite(l.tax) ||
        l.tax < 0 ||
        l.tax > 100,
    )
  )
    return '请检查产品、采购数量、单价和税率'
  if (
    !Number.isFinite(order.discount) ||
    order.discount < 0 ||
    order.discount > orderTotals(order).gross
  )
    return '优惠金额应介于 0 与采购原价总额之间'
  if (order.arrivals.some((a) => !dateValid(a.date) || a.date < order.signed))
    return '请填写有效的计划到货日期'
  if (
    order.payments.some(
      (p) => !Number.isFinite(p.ratio) || p.ratio <= 0 || p.ratio > 100 || !dateValid(p.date),
    )
  )
    return '请检查付款比例和计划付款日期'
  if (
    order.payments.length &&
    Math.abs(order.payments.reduce((s, p) => s + p.ratio, 0) - 100) > 0.0001
  )
    return '付款计划比例合计须为 100%'
  return ''
}
export function validateReceipt(receipt: PurchaseReceipt, state: PurchaseState) {
  const ids = receiptOrderIds(receipt)
  const orders = receiptOrders(receipt, state.orders)
  if (!ids.length || new Set(ids).size !== ids.length || orders.length !== ids.length || orders.some(o => !['待收货', '部分收货'].includes(o.status)))
    return '请选择可入库的采购订单'
  if (new Set(orders.map(o => o.supplierId)).size !== 1) return '同一入库单只能关联同一供应商的采购订单'
  if (orders.some(o => receipt.warehouse !== o.warehouse)) return '入库仓库必须与采购订单一致'
  if (orders.some(o => !receipt.lines.some(l => o.lines.some(p => p.id === l.lineId)))) return '每个关联采购单都必须有本次入库明细'
  if (!receipt.lines.length) return '本次入库数量须大于 0'
  if (new Set(receipt.lines.map((l) => l.lineId)).size !== receipt.lines.length)
    return '产品明细不可重复'
  for (const l of receipt.lines) {
    const order = orders.find(o => o.lines.some(p => p.id === l.lineId))
    if (!order) return '入库明细不属于所选采购订单'
    const batches = l.batches ?? []
    if (!batches.length || batches.some((batch) => !Number.isSafeInteger(batch.quantity) || batch.quantity <= 0))
      return '请填写批次入库数量，数量必须是正整数'
    l.arrived = batches.reduce((sum, batch) => sum + Math.max(0, batch.quantity), 0)
    if (
      !order.lines.some((p) => p.id === l.lineId) ||
      !Number.isSafeInteger(l.arrived) ||
      !Number.isSafeInteger(l.rejected) ||
      l.arrived < 0 ||
      l.rejected < 0 ||
      l.rejected > l.arrived
    )
      return '不合格数量不能超过本次到货数量，数量须为非负整数'
    if (l.arrived > remainingQuantity(state, order.id, l.lineId, receipt.id))
      return '本次到货数量不能超过待入库数量'
    const activeBatches = batches.filter((batch) => batch.quantity > 0)
    const batchKeys = new Set<string>()
    for (const batch of activeBatches) {
      if (!batch.dateCode.trim() || batch.dateCode.trim().length > 32)
        return '请填写 32 字以内的原厂日期码'
      if (!batch.lotCode.trim() || batch.lotCode.trim().length > 64)
        return '请填写 64 字以内的原厂批次号'
      if (!Number.isSafeInteger(batch.quantity) || batch.quantity <= 0)
        return '批次入库数量必须是正整数'
      const key = `${batch.dateCode.trim()}|${batch.lotCode.trim()}`
      if (batchKeys.has(key)) return '同一产品下批次明细不可重复'
      batchKeys.add(key)
    }
  }
  if (!receipt.lines.some((l) => l.arrived - l.rejected > 0)) return '本次入库数量须大于 0'
  if (receipt.lines.some((l) => l.rejected > 0) && receipt.quality !== '存在不合格品')
    return '存在不合格数量时，请选择“存在不合格品”'
  if ((receipt.inspector.trim() || receipt.inspectedAt) && (!receipt.inspector.trim() || !dateValid(receipt.inspectedAt))) return '请填写质检员和质检时间'
  if (receipt.confirmed && (!receipt.keeper.trim() || !dateValid(receipt.receivedAt)))
    return '请填写入库员和入库时间'
  if (receipt.confirmed && receipt.inspectedAt && receipt.receivedAt < receipt.inspectedAt)
    return '入库时间不能早于质检时间'
  return ''
}
export function chineseMoney(amount: number) {
  if (!Number.isFinite(amount) || amount < 0 || amount >= 1e12) return '—'
  const digits = '零壹贰叁肆伍陆柒捌玖',
    units = ['', '拾', '佰', '仟'],
    groups = ['', '万', '亿']
  const cents = Math.round(amount * 100),
    integer = Math.floor(cents / 100)
  const section = (value: number) => {
    let text = '',
      zero = false
    for (let i = 3; i >= 0; i--) {
      const digit = Math.floor(value / 10 ** i) % 10
      if (digit) {
        text += (zero ? '零' : '') + digits[digit]! + units[i]!
        zero = false
      } else if (text) zero = true
    }
    return text
  }
  let text = '',
    gap = false
  for (let i = 2; i >= 0; i--) {
    const value = Math.floor(integer / 10000 ** i) % 10000
    if (value) {
      text += (text && (gap || value < 1000) ? '零' : '') + section(value) + groups[i]!
      gap = false
    } else if (text) gap = true
  }
  const jiao = Math.floor((cents % 100) / 10),
    fen = cents % 10
  return (
    (text || '零') +
    '元' +
    (!jiao && !fen
      ? '整'
      : (jiao ? digits[jiao]! + '角' : fen ? '零' : '') + (fen ? digits[fen]! + '分' : ''))
  )
}
