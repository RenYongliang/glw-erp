import boardData from '../data/orderBoards.json'
import { ref } from 'vue'
import { defineStore } from 'pinia'
import {
  customers,
  products,
  salesStorageKey,
  nextSalesCode,
  orderTotals,
  validateOrder,
  validateShipment,
  returnableQuantity,
  margin,
  type SalesState,
  type SalesOrder,
  type SalesShipment,
  type SalesPayment,
  type SalesReturn,
} from '../domain/sales'

function initial(): SalesState {
  const orders: SalesOrder[] = boardData.sales.orders.map((source, i) => ({
    id: `sales-order-${i + 1}`,
    code: source.id,
    name: source.name,
    customerId: customers.find((c) => c.name === source.partner)?.id || 'c1',
    signed: source.signed,
    delivery: source.delivery,
    owner: source.owner,
    department: '销售部',
    warehouse: i % 2 ? '无锡仓' : '杭州仓',
    address: '',
    discount: 0,
    priceLevel: '标准售价',
    quoteCode: '',
    term: source.paymentTerm,
    lines: source.lines.map((l, j) => ({
      id: `sales-line-${i + 1}-${j + 1}`,
      productId: products.find((p) => p.name === l.product)?.id || products[0]!.id,
      quantity: l.quantity,
      price: l.price,
      listPrice: l.price,
      tax: 13,
    })),
    arrivals: source.plans,
    payments: [
      { name: '整批货款', ratio: 100, method: '银行转账', date: source.delivery, note: '' },
    ],
    attachments: [],
    status: i >= 17 ? '不通过' : i >= 15 ? '待审批' : '审批通过',
    reason: i >= 17 ? '请核对实际售价与订单毛利率' : '',
    approver: i < 15 ? '张睿' : '',
    approvedAt: i < 15 ? source.signed + ' 09:00' : '',
  }))
  const shipments: SalesShipment[] = orders.slice(0, 15).map((o, i) => ({
    id: `sales-shipment-${i + 1}`,
    code: `XSCK260924-${String(i + 1).padStart(2, '0')}`,
    orderId: o.id,
    warehouse: o.warehouse,
    lines: o.lines.map((l, j) => ({
      lineId: l.id,
      arrived: i % 3 === 0 ? l.quantity : Math.floor(l.quantity / 2),
      location: `A0${j + 1}`,
    })),
    tag: i % 5 === 3 ? '库存冻结' : i % 5 === 4 ? '计划出库' : '出库',
    confirmed: i % 5 < 3,
    shippedAt: i % 5 < 3 ? '2026-09-24 10:00' : '',
    keeper: '陈晨',
    department: '仓储部',
    photos: [],
    customerConfirmed: i % 5 === 0,
    customerReceivedAt: i % 5 === 0 ? '2026-09-24 14:00' : '',
  }))
  return { orders, shipments, payments: [], returns: [], logs: [] }
}
export const useSalesStore = defineStore('sales', () => {
  const state = ref<SalesState>(initial()),
    loadError = ref('')
  function load() {
    try {
      const text = localStorage.getItem(salesStorageKey)
      if (!text) return
      const saved = JSON.parse(text)
      if (
        !['orders', 'shipments', 'payments', 'returns', 'logs'].every((k) =>
          Array.isArray(saved[k]),
        ) ||
        !saved.orders.every(
          (o: SalesOrder) =>
            typeof o.id === 'string' &&
            Array.isArray(o.lines) &&
            Array.isArray(o.arrivals) &&
            Array.isArray(o.payments) &&
            Array.isArray(o.attachments),
        ) ||
        !saved.shipments.every(
          (r: SalesShipment) => Array.isArray(r.lines) && Array.isArray(r.photos),
        )
      )
        throw Error()
      state.value = saved
      loadError.value = ''
    } catch {
      loadError.value = '本地销售数据无法读取，显示初始演示数据。'
    }
  }
  load()
  const snapshot = (): SalesState => JSON.parse(JSON.stringify(state.value))
  function commit(next: SalesState, action: string, name: string) {
    next.logs.unshift({
      id: crypto.randomUUID(),
      time: new Date().toLocaleString('zh-CN', { hour12: false }),
      action,
      name,
    })
    next.logs = next.logs.slice(0, 200)
    try {
      localStorage.setItem(salesStorageKey, JSON.stringify(next))
    } catch {
      throw Error('保存失败，请检查浏览器存储空间；本次操作未保存')
    }
    state.value = next
  }
  function saveOrder(input: SalesOrder) {
    const order: SalesOrder = JSON.parse(JSON.stringify(input)),
      next = snapshot(),
      error = validateOrder(order)
    if (error) throw Error(error)
    const index = next.orders.findIndex((o) => o.id === order.id)
    if (
      index >= 0 &&
      (next.shipments.some((r) => r.orderId === order.id) ||
        next.payments.some((p) => p.orderId === order.id) ||
        next.returns.some((r) => r.orderId === order.id))
    )
      throw Error('订单已有出库或财务记录，不能再修改订单基础数据')
    order.name = order.name.trim()
    order.status = '待审批'
    order.reason = ''
    order.approver = ''
    order.approvedAt = ''
    if (index < 0) {
      order.code = nextSalesCode('XSDD', next.orders)
      next.orders.unshift(order)
    } else next.orders[index] = order
    commit(next, index < 0 ? '新增销售订单' : '修改并重新提交审批', order.code)
    return order
  }
  function saveAttachments(id: string, attachments: SalesOrder['attachments']) {
    const next = snapshot(),
      order = next.orders.find((o) => o.id === id)
    if (!order) throw Error('订单不存在，请刷新')
    order.attachments = JSON.parse(JSON.stringify(attachments))
    commit(next, '补充合同附件', order.code)
  }
  function approve(id: string, passed: boolean, reason: string) {
    const next = snapshot(),
      order = next.orders.find((o) => o.id === id)
    if (!order || order.status !== '待审批') throw Error('订单已发生变化，请刷新后重试')
    if (!passed && !reason.trim()) throw Error('请填写不通过原因')
    if (passed && margin(order) < 35) throw Error('订单毛利率低于 35%，不能审批通过')
    order.status = passed ? '审批通过' : '不通过'
    order.reason = reason
    order.approver = '张睿'
    order.approvedAt = new Date().toLocaleString('sv-SE')
    commit(next, passed ? '审批通过' : '审批不通过', order.code)
  }
  function saveShipment(input: SalesShipment) {
    const next = snapshot(),
      shipment: SalesShipment = JSON.parse(JSON.stringify(input)),
      error = validateShipment(shipment, next)
    if (error) throw Error(error)
    const index = next.shipments.findIndex((r) => r.id === shipment.id)
    if (index >= 0 && next.shipments[index]!.confirmed) {
      const previous = next.shipments[index]!
      if (
        JSON.stringify({ ...previous, customerConfirmed: false, customerReceivedAt: '' }) !==
        JSON.stringify({ ...shipment, customerConfirmed: false, customerReceivedAt: '' })
      )
        throw Error('已确认出库单仅允许补充客户收货确认')
    }
    if (index >= 0 && next.shipments[index]!.orderId !== shipment.orderId)
      throw Error('已保存的出库单不能更换销售订单')
    if (index >= 0 && next.returns.some((r) => r.orderId === shipment.orderId))
      throw Error('该订单已有退货记录，不能再修改出库记录')
    if (index < 0) {
      shipment.code = nextSalesCode('XSCK', next.shipments)
      next.shipments.unshift(shipment)
    } else next.shipments[index] = shipment
    commit(next, index < 0 ? '新增销售出库' : '修改销售出库', shipment.code)
    return shipment
  }
  function remove(kind: 'orders' | 'shipments', ids: string[]) {
    const next = snapshot()
    if (
      kind === 'orders' &&
      ids.some(
        (id) =>
          next.shipments.some((r) => r.orderId === id) ||
          next.payments.some((p) => p.orderId === id) ||
          next.returns.some((r) => r.orderId === id),
      )
    )
      throw Error('选中订单存在出库、收款或退货记录，请先处理关联数据')
    if (
      kind === 'shipments' &&
      next.shipments.some(
        (r) => ids.includes(r.id) && next.returns.some((t) => t.orderId === r.orderId),
      )
    )
      throw Error('关联订单已有退货记录，不能删除出库单')
    if (kind === 'shipments' && next.shipments.some((s) => ids.includes(s.id) && s.confirmed))
      throw Error('已确认出库单不能删除')
    if (kind === 'orders') next.orders = next.orders.filter((r) => !ids.includes(r.id))
    else next.shipments = next.shipments.filter((r) => !ids.includes(r.id))
    commit(next, '删除' + (kind === 'orders' ? '销售订单' : '销售出库'), `${ids.length} 条`)
  }
  function pay(input: SalesPayment) {
    const next = snapshot(),
      order = next.orders.find((o) => o.id === input.orderId)
    if (!order || order.status !== '审批通过') throw Error('只能为已审批订单收款')
    const remaining =
      orderTotals(order).total -
      next.payments.filter((p) => p.orderId === order.id).reduce((a, p) => a + p.amount, 0)
    if (
      !Number.isFinite(input.amount) ||
      input.amount <= 0 ||
      Math.round(input.amount * 100) > Math.round(remaining * 100) ||
      !input.date ||
      !input.method
    )
      throw Error('请填写有效收款信息，金额不得超过剩余应收金额')
    next.payments.unshift({ ...input })
    commit(next, '销售收款', order.code)
  }
  function returnGoods(input: SalesReturn) {
    const next = snapshot(),
      order = next.orders.find((o) => o.id === input.orderId)
    if (
      !order ||
      !Number.isSafeInteger(input.quantity) ||
      input.quantity <= 0 ||
      input.quantity > returnableQuantity(next, input.orderId, input.lineId) ||
      !input.date ||
      !input.reason.trim()
    )
      throw Error('请填写退货原因、日期及有效数量，不能超过已出库可退数量')
    next.returns.unshift({ ...input })
    commit(next, '销售退货', order.code)
  }
  function importOrders(rows: SalesOrder[]) {
    const next = snapshot()
    for (const row of rows) {
      const error = validateOrder(row)
      if (error) throw Error(error)
      row.code = nextSalesCode('XSDD', next.orders)
      row.status = '待审批'
      next.orders.unshift(row)
    }
    commit(next, '导入销售订单', `${rows.length} 条`)
  }
  function importShipments(rows: SalesShipment[]) {
    const next = snapshot()
    for (const row of rows) {
      const error = validateShipment(row, next)
      if (error) throw Error(error)
      row.code = nextSalesCode('XSCK', next.shipments)
      next.shipments.unshift(row)
    }
    commit(next, '导入销售出库', `${rows.length} 条`)
  }
  return {
    state,
    loadError,
    load,
    saveOrder,
    saveAttachments,
    approve,
    saveShipment,
    remove,
    pay,
    returnGoods,
    importOrders,
    importShipments,
  }
})
