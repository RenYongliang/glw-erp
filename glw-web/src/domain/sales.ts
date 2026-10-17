import catalog from '../data/products.json' with { type: 'json' }
import type { ShipmentBatch } from './shipmentBatches'
export type SalesKind = 'orders' | 'shipments'
export interface Product {
  id: string
  name: string
  code: string
  brand: string
  model: string
  unit: string
  price: number
  tax: number
  stock: number
  cost: number
}
export interface Customer {
  id: string
  name: string
  code: string
  contact: string
  phone: string
  bank: string
  account: string
}
export interface SalesLine {
  pendingQuantity?: number
  brand?: string
  model?: string
  packaging?: string
  amount?: number
  cancelledQuantity?: number
  id: string
  productId: string
  quantity: number
  price: number
  tax: number
  listPrice?: number
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
export interface SalesOrder {
  customerShortName?: string
  paymentStatus?: '未收款' | '部分收款' | '已收款'
  remark?: string
  customerName?: string
  purchaseNumber?: string
  priceLevel?: string
  quoteCode?: string
  id: string
  code: string
  name: string
  customerId: string
  signed: string
  delivery: string
  owner: string
  department: string
  warehouse: string
  address: string
  discount: number
  lines: SalesLine[]
  arrivals: ArrivalPlan[]
  payments: PaymentPlan[]
  attachments: Attachment[]
  term: string
  status: '待审批' | '审批通过' | '不通过' | '待发货' | '部分发货' | '已完成' | '剩余未交取消'
  reason: string
  approver: string
  approvedAt: string
}
export interface ShipmentLine {
  batches?: ShipmentBatch[]
  remark?: string
  lineId: string
  arrived: number
  location: string
}
export interface ShippingInfo {
  address: string
  recipient: string
  phone: string
  sender: string
  senderPhone: string
  method: string
  carrier: string
  trackingNumber: string
  purchaseNumber: string
  remark: string
}
export function orderCustomerName(order?: SalesOrder): string {
  return customers.find(c => c.id === order?.customerId)?.name || order?.customerName?.trim() || ''
}
export function defaultShippingInfo(order?: SalesOrder): ShippingInfo {
  const customer = customers.find(c => c.id === order?.customerId)
  return { address: order?.address || '', recipient: customer?.contact || '', phone: customer?.phone || '', sender: order?.owner || '', senderPhone: '', method: '快递物流', carrier: '', trackingNumber: '', purchaseNumber: order?.purchaseNumber || '', remark: '' }
}
export interface SalesShipment {
  customerShortName?: string
  customerName?: string
  purchaseNumber?: string
  shipping?: ShippingInfo
  id: string
  code: string
  orderId: string
  warehouse: string
  lines: ShipmentLine[]
  tag: '出库' | '库存冻结' | '计划出库'
  confirmed: boolean
  shippedAt: string
  keeper: string
  department: string
  photos: Attachment[]
  customerConfirmed: boolean
  customerReceivedAt: string
}
export interface SalesPayment {
  id: string
  orderId: string
  amount: number
  date: string
  method: string
  note: string
}
export interface SalesReturn {
  id: string
  orderId: string
  lineId: string
  quantity: number
  date: string
  reason: string
}
export interface SalesState {
  orders: SalesOrder[]
  shipments: SalesShipment[]
  payments: SalesPayment[]
  returns: SalesReturn[]
  logs: { id: string; time: string; action: string; name: string }[]
}
export const salesStorageKey = 'glw.sales.v1'
export const customers: Customer[] = [
  {
    id: 'c1',
    name: '示例：无锡简道云',
    code: 'KH001',
    contact: '李明',
    phone: '13800000001',
    bank: '中国银行',
    account: '演示账户001',
  },
  {
    id: 'c2',
    name: '示例：无锡帆软',
    code: 'KH002',
    contact: '王芳',
    phone: '13800000002',
    bank: '建设银行',
    account: '演示账户002',
  },
  {
    id: 'c3',
    name: '示例：杭州简道云',
    code: 'KH003',
    contact: '陈晨',
    phone: '13800000003',
    bank: '工商银行',
    account: '演示账户003',
  },
  {
    id: 'c4',
    name: '示例：上海帆软',
    code: 'KH004',
    contact: '张颖',
    phone: '13800000004',
    bank: '招商银行',
    account: '演示账户004',
  },
  {
    id: 'c5',
    name: '示例：杭州帆软',
    code: 'KH005',
    contact: '赵磊',
    phone: '13800000005',
    bank: '农业银行',
    account: '演示账户005',
  },
  {
    id: 'c6',
    name: '示例：厦门帆软',
    code: 'KH006',
    contact: '林晓',
    phone: '13800000006',
    bank: '中国银行',
    account: '演示账户006',
  },
]
export const products: Product[] = catalog.map((p) => ({
  ...p,
  id: p.code,
  tax: 13,
  stock: 150000,
}))
export function salesLineProduct(line?: SalesLine, catalog: Product[] = products) {
  const product = catalog.find(product => product.id === line?.productId)
  return { brand: line?.brand ?? product?.brand ?? '', name: line?.model ?? product?.name ?? '',
    model: line?.packaging ?? product?.model ?? '' }
}
export const priceLevels = ['标准售价', '一级售价', '二级售价']
export function levelPrice(product: Product, level = '标准售价') {
  return roundMoney(product.price * (level === '一级售价' ? 0.97 : level === '二级售价' ? 0.94 : 1))
}
export function margin(order: SalesOrder) {
  const total = orderTotals(order).total
  const cost = order.lines.reduce(
    (sum, line) => sum + line.quantity * (products.find((p) => p.id === line.productId)?.cost || 0),
    0,
  )
  return total > 0 ? ((total - cost) / total) * 100 : 0
}
export function availableStock(
  state: SalesState,
  productId: string,
  warehouse: string,
  excludeShipment = '',
) {
  const base = products.find((p) => p.id === productId)?.stock || 0
  const reserved = state.shipments
    .filter((s) => s.warehouse === warehouse && s.id !== excludeShipment)
    .reduce((sum, s) => {
      const order = state.orders.find((o) => o.id === s.orderId)
      return (
        sum +
        s.lines
          .filter((l) => order?.lines.find((ol) => ol.id === l.lineId)?.productId === productId)
          .reduce((n, l) => n + l.arrived, 0)
      )
    }, 0)
  const returned = state.returns
    .filter((r) => {
      const o = state.orders.find((o) => o.id === r.orderId)
      return (
        o?.warehouse === warehouse &&
        o.lines.find((l) => l.id === r.lineId)?.productId === productId
      )
    })
    .reduce((sum, r) => sum + r.quantity, 0)
  return Math.max(0, base - reserved + returned)
}
export const warehouses = ['杭州仓', '无锡仓']
export const money = (n: number) =>
  n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
export const roundMoney = (n: number) => Math.round((n + Number.EPSILON) * 100) / 100
export const today = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
export function nextSalesCode(prefix: string, rows: { code: string }[], date = today()) {
  const start = prefix + date.replaceAll('-', '').slice(2) + '-'
  const n =
    Math.max(
      0,
      ...rows
        .filter((r) => r.code.startsWith(start))
        .map((r) => Number(r.code.slice(start.length)) || 0),
    ) + 1
  return start + String(n).padStart(2, '0')
}
export function orderTotals(order: Pick<SalesOrder, 'lines' | 'discount'>) {
  const cents = order.lines.map((l) => Math.round((l.amount ?? l.quantity * l.price) * 100))
  const actualCents = cents.reduce((a, b) => a + b, 0)
  const grossCents = order.lines.reduce(
    (sum, l) => sum + Math.round(l.quantity * (l.listPrice ?? l.price) * 100),
    0,
  )
  const discountCents = Math.min(actualCents, Math.max(0, Math.round((order.discount || 0) * 100)))
  let allocated = 0
  const details = order.lines.map((l, i) => {
    const discount =
      i === order.lines.length - 1
        ? discountCents - allocated
        : Math.floor((discountCents * (cents[i] || 0)) / (actualCents || 1))
    allocated += discount
    const total = ((cents[i] || 0) - discount) / 100
    return {
      gross: roundMoney(l.quantity * (l.listPrice ?? l.price)),
      total,
      unit: l.quantity ? total / l.quantity : 0,
      net: roundMoney(total / (1 + l.tax / 100)),
    }
  })
  return {
    gross: grossCents / 100,
    total: (actualCents - discountCents) / 100,
    beforeDiscount: actualCents / 100,
    rate: grossCents ? ((actualCents - discountCents) / grossCents) * 100 : 100,
    net: roundMoney(details.reduce((a, b) => a + b.net, 0)),
    details,
  }
}
export function pendingShipmentQuantity(order: SalesOrder, line: SalesLine, shipments: SalesShipment[]) {
  if (line.pendingQuantity !== undefined) return Math.max(0, line.pendingQuantity)
  const shipped = shipments.filter(s => s.orderId === order.id && s.confirmed && s.tag === '出库')
    .flatMap(s => s.lines).filter(l => l.lineId === line.id)
    .reduce((sum, l) => sum + l.arrived, 0)
  return Math.max(0, line.quantity - (line.cancelledQuantity || 0) - shipped)
}

export function remainingQuantity(
  state: SalesState,
  orderId: string,
  lineId: string,
  excludeShipment = '',
) {
  const line = state.orders.find((o) => o.id === orderId)?.lines.find((l) => l.id === lineId)
  const ordered = line?.pendingQuantity ?? Math.max(0, (line?.quantity || 0) - (line?.cancelledQuantity || 0))
  return (
    ordered -
    state.shipments
      .filter((r) => r.orderId === orderId && r.id !== excludeShipment &&
        (line?.pendingQuantity === undefined || !r.confirmed || r.tag !== '出库'))
      .reduce(
        (sum, r) =>
          sum + r.lines.filter((l) => l.lineId === lineId).reduce((a, l) => a + l.arrived, 0),
        0,
      )
  )
}
export function returnableQuantity(state: SalesState, orderId: string, lineId: string) {
  return (
    state.shipments
      .filter((r) => r.orderId === orderId && r.confirmed)
      .reduce(
        (sum, r) =>
          sum + r.lines.filter((l) => l.lineId === lineId).reduce((a, l) => a + l.arrived, 0),
        0,
      ) -
    state.returns
      .filter((r) => r.orderId === orderId && r.lineId === lineId)
      .reduce((a, r) => a + r.quantity, 0)
  )
}
export function shipmentTotals(shipment: SalesShipment, order?: SalesOrder) {
  if (!order) return { quantity: 0, total: 0 }
  const totals = orderTotals(order)
  return shipment.lines.reduce(
    (sum, l) => {
      const quantity = l.arrived,
        i = order.lines.findIndex((p) => p.id === l.lineId)
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
export function validateOrder(order: SalesOrder, catalog: Pick<Product, 'id'>[] = products) {
  if (order.customerId && !customers.some((s) => s.id === order.customerId)) return '请选择有效客户'
  if (!orderCustomerName(order)) return '请选择或填写客户名称'
  if (orderCustomerName(order).length > 100) return '客户名称不能超过100字'
  if (!order.name.trim()) return '请填写销售订单名称'
  if (!dateValid(order.signed) || !dateValid(order.delivery)) return '请填写订单签订日期和交付日期'
  if (order.delivery < order.signed) return '订单交付日期不能早于签订日期'
  if (!warehouses.includes(order.warehouse)) return '请选择出库仓库'
  if (!order.lines.length) return '请至少添加一条销售产品明细'
  if (new Set(order.lines.map((l) => l.productId)).size !== order.lines.length)
    return '同一产品请合并数量，不要重复添加'
  if (
    order.lines.some(
      (l) =>
        !catalog.some((p) => p.id === l.productId) ||
        !Number.isSafeInteger(l.quantity) ||
        l.quantity <= 0 ||
        l.quantity > 100000000 ||
        !Number.isFinite(l.price) ||
        l.price < 0 ||
        l.price > 10000000 ||
        (l.listPrice !== undefined && (!Number.isFinite(l.listPrice) || l.listPrice < 0)) ||
        !Number.isFinite(l.tax) ||
        l.tax < 0 ||
        l.tax > 100,
    )
  )
    return '请检查产品、销售数量、单价和税率'
  if (
    !Number.isFinite(order.discount) ||
    order.discount < 0 ||
    order.discount > orderTotals(order).beforeDiscount
  )
    return '优惠金额应介于 0 与实际售价总额之间'
  if (order.arrivals.some((a) => !dateValid(a.date) || a.date < order.signed))
    return '请填写有效的计划送货日期'
  if (
    order.payments.some(
      (p) => !Number.isFinite(p.ratio) || p.ratio <= 0 || p.ratio > 100 || !dateValid(p.date),
    )
  )
    return '请检查收款比例和计划收款日期'
  if (
    order.payments.length &&
    Math.abs(order.payments.reduce((s, p) => s + p.ratio, 0) - 100) > 0.0001
  )
    return '收款计划比例合计须为 100%'
  return ''
}
export function validateShipment(shipment: SalesShipment, state: SalesState) {
  const order = state.orders.find((o) => o.id === shipment.orderId)
  if (!order || !['审批通过', '待发货', '部分发货'].includes(order.status)) return '请选择可发货的销售订单'
  if (shipment.shipping && shipment.confirmed) {
    if (!shipment.shipping.address.trim()) return '请填写收货地址'
    if (!shipment.shipping.recipient.trim()) return '请填写收货人'
    if (!shipment.shipping.phone.trim()) return '请填写收货联系电话'
  }
  if (shipment.warehouse !== order.warehouse) return '出库仓库必须与销售订单一致'
  if (!['出库', '库存冻结', '计划出库'].includes(shipment.tag)) return '请选择产品出库标签'
  if (!shipment.lines.length || !shipment.lines.some((l) => l.arrived > 0))
    return '本次出库数量须大于 0'
  if (new Set(shipment.lines.map((l) => l.lineId)).size !== shipment.lines.length)
    return '产品明细不可重复'
  for (const line of shipment.lines) {
    const source = order.lines.find((l) => l.id === line.lineId)
    if (!source || !Number.isSafeInteger(line.arrived) || line.arrived < 0)
      return '请填写有效的非负整数出库数量'
    if (line.arrived > remainingQuantity(state, order.id, line.lineId, shipment.id))
      return '本次出库数量不能超过待出库数量'
    if (line.batches !== undefined) {
      if (new Set(line.batches.map(b => b.inventoryId)).size !== line.batches.length)
        return '同一批次不能重复选择'
      if (line.batches.some(b => !b.inventoryId || !Number.isSafeInteger(b.quantity) || b.quantity <= 0 || !Number.isFinite(b.unitCost) || b.unitCost < 0))
        return '请检查批次、批次数量和成本'
      if (line.batches.reduce((sum, b) => sum + b.quantity, 0) !== line.arrived)
        return '本次出库数量必须等于所选批次数量合计'
    } else if (line.arrived > availableStock(state, source.productId, shipment.warehouse, shipment.id))
      return '本次出库数量不能超过当前可用库存'
  }
  if (shipment.confirmed && shipment.tag !== '出库')
    return '库存冻结、计划出库不能直接确认出库，请先切换标签为出库'
  if (shipment.confirmed && (!shipment.keeper.trim() || !dateValid(shipment.shippedAt)))
    return '请填写出库员和出库时间'
  if (shipment.confirmed && shipment.shippedAt.slice(0, 10) < order.signed)
    return '出库时间不能早于订单日期'
  if (
    shipment.customerConfirmed &&
    (!shipment.confirmed ||
      !dateValid(shipment.customerReceivedAt) ||
      shipment.customerReceivedAt < shipment.shippedAt)
  )
    return '请先确认出库，收货时间不能早于出库时间'
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
