import { ref } from 'vue'
import { defineStore } from 'pinia'
import {
  purchaseStorageKey,
  nextPurchaseCode,
  orderTotals,
  validateOrder,
  validateReceipt,
  returnableQuantity,
  type PurchaseState,
  type PurchaseOrder,
  type PurchaseReceipt,
  type PurchasePayment,
  type PurchaseReturn,
} from '../domain/purchase'
import {
  createInboundOrder,
  fetchInboundOrders,
  fetchPurchaseOrders,
} from '../services/purchaseApi'

function emptyState(): PurchaseState {
  return { orders: [], receipts: [], payments: [], returns: [], logs: [] }
}

export const usePurchaseStore = defineStore('purchase', () => {
  const state = ref<PurchaseState>(emptyState()),
    loadError = ref(''),
    loading = ref(false)

  async function load() {
    loading.value = true
    try {
      const [orders, receipts] = await Promise.all([fetchPurchaseOrders(), fetchInboundOrders()])
      state.value = { orders, receipts, payments: [], returns: [], logs: [] }
      loadError.value = ''
    } catch (error) {
      loadError.value = error instanceof Error ? error.message : '采购数据加载失败'
    } finally {
      loading.value = false
    }
  }
  const snapshot = (): PurchaseState => JSON.parse(JSON.stringify(state.value))
  function commit(next: PurchaseState, action: string, name: string) {
    next.logs.unshift({
      id: crypto.randomUUID(),
      time: new Date().toLocaleString('zh-CN', { hour12: false }),
      action,
      name,
    })
    next.logs = next.logs.slice(0, 200)
    try {
      localStorage.setItem(purchaseStorageKey, JSON.stringify(next))
    } catch {
      throw Error('保存失败，请检查浏览器存储空间；本次操作未保存')
    }
    state.value = next
  }
  function saveOrder(input: PurchaseOrder) {
    const order: PurchaseOrder = JSON.parse(JSON.stringify(input)),
      next = snapshot(),
      error = validateOrder(order)
    if (error) throw Error(error)
    const index = next.orders.findIndex((o) => o.id === order.id)
    if (
      index >= 0 &&
      (next.receipts.some((r) => r.orderId === order.id) ||
        next.payments.some((p) => p.orderId === order.id) ||
        next.returns.some((r) => r.orderId === order.id))
    )
      throw Error('订单已有入库或财务记录，不能再修改订单基础数据')
    order.name = order.name.trim()
    order.supplierName = order.supplierName?.trim()
    for (const line of order.lines) line.delivery ??= order.delivery
    order.delivery = order.lines.map(l => l.delivery!).sort().at(-1) || order.signed
    order.status = '待审批'
    order.reason = ''
    order.approver = ''
    order.approvedAt = ''
    if (index < 0) {
      order.code = nextPurchaseCode('CGDD', next.orders)
      next.orders.unshift(order)
    } else next.orders[index] = order
    commit(next, index < 0 ? '新增采购订单' : '修改并重新提交审批', order.code)
    return order
  }
  function approve(id: string, passed: boolean, reason: string) {
    const next = snapshot(),
      order = next.orders.find((o) => o.id === id)
    if (!order || order.status !== '待审批') throw Error('订单已发生变化，请刷新后重试')
    if (!passed && !reason.trim()) throw Error('请填写不通过原因')
    order.status = passed ? '审批通过' : '不通过'
    order.reason = reason
    order.approver = '张睿'
    order.approvedAt = new Date().toLocaleString('sv-SE')
    commit(next, passed ? '审批通过' : '审批不通过', order.code)
  }
  async function saveReceipt(input: PurchaseReceipt) {
    const receipt: PurchaseReceipt = JSON.parse(JSON.stringify(input)),
      error = validateReceipt(receipt, state.value)
    if (error) throw Error(error)
    if (receipt.code) throw Error('已保存的入库单不能修改')
    await createInboundOrder(receipt)
    await load()
  }
  function remove(kind: 'orders' | 'receipts', ids: string[]) {
    const next = snapshot()
    if (
      kind === 'orders' &&
      ids.some(
        (id) =>
          next.receipts.some((r) => r.orderId === id) ||
          next.payments.some((p) => p.orderId === id) ||
          next.returns.some((r) => r.orderId === id),
      )
    )
      throw Error('选中订单存在入库、付款或退货记录，请先处理关联数据')
    if (
      kind === 'receipts' &&
      next.receipts.some(
        (r) => ids.includes(r.id) && next.returns.some((t) => t.orderId === r.orderId),
      )
    )
      throw Error('关联订单已有退货记录，不能删除入库单')
    if (kind === 'orders') next.orders = next.orders.filter((r) => !ids.includes(r.id))
    else next.receipts = next.receipts.filter((r) => !ids.includes(r.id))
    commit(next, '删除' + (kind === 'orders' ? '采购订单' : '采购入库'), `${ids.length} 条`)
  }
  function pay(input: PurchasePayment) {
    const next = snapshot(),
      order = next.orders.find((o) => o.id === input.orderId)
    if (!order || order.status !== '审批通过') throw Error('只能为已审批订单付款')
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
      throw Error('请填写有效付款信息，金额不得超过剩余应付金额')
    next.payments.unshift({ ...input })
    commit(next, '采购付款', order.code)
  }
  function returnGoods(input: PurchaseReturn) {
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
      throw Error('请填写退货原因、日期及有效数量，不能超过已入库可退数量')
    next.returns.unshift({ ...input })
    commit(next, '采购退货', order.code)
  }
  function importOrders(rows: PurchaseOrder[]) {
    const next = snapshot()
    for (const row of rows) {
      const error = validateOrder(row)
      if (error) throw Error(error)
      row.code = nextPurchaseCode('CGDD', next.orders)
      row.status = '待审批'
      next.orders.unshift(row)
    }
    commit(next, '导入采购订单', `${rows.length} 条`)
  }
  function importReceipts(rows: PurchaseReceipt[]) {
    const next = snapshot()
    for (const row of rows) {
      const error = validateReceipt(row, next)
      if (error) throw Error(error)
      row.code = nextPurchaseCode('CGRK', next.receipts)
      next.receipts.unshift(row)
    }
    commit(next, '导入采购入库', `${rows.length} 条`)
  }
  return {
    state,
    loadError,
    loading,
    load,
    saveOrder,
    approve,
    saveReceipt,
    remove,
    pay,
    returnGoods,
    importOrders,
    importReceipts,
  }
})
