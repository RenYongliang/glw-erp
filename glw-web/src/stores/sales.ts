import { ref } from 'vue'
import { defineStore } from 'pinia'
import {
  salesStorageKey,
  nextSalesCode,
  orderTotals,
  validateOrder,
  validateShipment,
  returnableQuantity,
  shipmentMatchesOrder,
  shipmentOrderIds,
  type SalesState,
  type SalesOrder,
  type SalesShipment,
  type SalesPayment,
  type SalesReturn,
  type Product,
} from '../domain/sales'
import {
  createOutboundOrder,
  createSalesOrder,
  fetchSalesOrders,
  fetchOutboundOrders,
  signOutboundOrder,
  updateSalesOrder,
} from '../services/salesApi'

function initial(): SalesState {
  return { orders: [], shipments: [], payments: [], returns: [], logs: [] }
}
export const useSalesStore = defineStore('sales', () => {
  const state = ref<SalesState>(initial()),
    loadError = ref(''),
    loading = ref(false)
  async function load() {
    loading.value = true
    try {
      const [orders, shipments] = await Promise.all([fetchSalesOrders(), fetchOutboundOrders()])
      state.value = { orders, shipments, payments: [], returns: [], logs: [] }
      loadError.value = ''
    } catch (error) {
      loadError.value = error instanceof Error ? error.message : '销售数据加载失败'
    } finally {
      loading.value = false
    }
  }
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
  async function saveOrder(input: SalesOrder, catalog?: Pick<Product, 'id'>[]) {
    const order: SalesOrder = JSON.parse(JSON.stringify(input))
    order.name = order.name.trim()
    const error = validateOrder(order, catalog)
    if (error) throw Error(error)
    const existing = state.value.orders.find((o) => o.id === order.id)
    if (
      existing &&
      (state.value.shipments.some((r) => shipmentMatchesOrder(r, order.id)) ||
        state.value.payments.some((p) => p.orderId === order.id) ||
        state.value.returns.some((r) => r.orderId === order.id))
    )
      throw Error('订单已有出库或财务记录，不能再修改订单基础数据')
    if (existing) await updateSalesOrder(order)
    else await createSalesOrder(order)
    // 以服务端数据为准刷新列表，确保拿到后端订单号和状态。
    await load()
    return order
  }
  function saveAttachments(id: string, attachments: SalesOrder['attachments']) {
    const next = snapshot(),
      order = next.orders.find((o) => o.id === id)
    if (!order) throw Error('订单不存在，请刷新')
    order.attachments = JSON.parse(JSON.stringify(attachments))
    commit(next, '补充合同附件', order.code)
  }
  async function saveShipment(input: SalesShipment) {
    const next = snapshot(),
      shipment: SalesShipment = JSON.parse(JSON.stringify(input)),
      error = validateShipment(shipment, next)
    if (error) throw Error(error)
    const index = next.shipments.findIndex((r) => r.id === shipment.id)
    const existing = index >= 0 ? next.shipments[index]! : undefined
    // 后端出库单 id 为数字；本地未提交草稿是 UUID。草稿补确认后也必须走后端生成正式出库单。
    const persisted = !!existing && /^\d+$/.test(existing.id)
    if (!persisted && shipment.confirmed) {
      const created = await createOutboundOrder(shipment)
      if (shipment.customerConfirmed && shipment.customerReceivedAt)
        await signOutboundOrder(String(created.id), shipment.customerReceivedAt)
      await load()
      return shipment
    }
    if (persisted && existing.confirmed) {
      const previous = existing
      if (
        JSON.stringify({ ...previous, customerConfirmed: false, customerReceivedAt: '' }) !==
        JSON.stringify({ ...shipment, customerConfirmed: false, customerReceivedAt: '' })
      )
        throw Error('已确认出库单仅允许补充客户收货确认')
      if (
        shipment.customerConfirmed &&
        shipment.customerReceivedAt &&
        shipment.customerReceivedAt.slice(0, 10) !== previous.customerReceivedAt.slice(0, 10)
      ) {
        await signOutboundOrder(previous.id, shipment.customerReceivedAt)
        await load()
        return shipment
      }
    }
    if (
      index >= 0 &&
      JSON.stringify(shipmentOrderIds(next.shipments[index]!).slice().sort()) !==
        JSON.stringify(shipmentOrderIds(shipment).slice().sort())
    )
      throw Error('已保存的出库单不能更换销售订单')
    if (
      index >= 0 &&
      next.returns.some((r) => shipmentMatchesOrder(shipment, r.orderId))
    )
      throw Error('该订单已有退货记录，不能再修改出库记录')
    if (index < 0) {
      shipment.code = nextSalesCode('OUT', next.shipments, shipment.shippedAt || undefined)
      next.shipments.unshift(shipment)
    } else next.shipments[index] = shipment
    commit(next, index < 0 ? '新增产品出库' : '修改产品出库', shipment.code)
    return shipment
  }
  function remove(kind: 'orders' | 'shipments', ids: string[]) {
    const next = snapshot()
    if (
      kind === 'orders' &&
      ids.some(
        (id) =>
          next.shipments.some((r) => shipmentMatchesOrder(r, id)) ||
          next.payments.some((p) => p.orderId === id) ||
          next.returns.some((r) => r.orderId === id),
      )
    )
      throw Error('选中订单存在出库、收款或退货记录，请先处理关联数据')
    if (
      kind === 'shipments' &&
      next.shipments.some(
        (r) => ids.includes(r.id) && next.returns.some((t) => shipmentMatchesOrder(r, t.orderId)),
      )
    )
      throw Error('关联订单已有退货记录，不能删除出库单')
    if (kind === 'shipments' && next.shipments.some((s) => ids.includes(s.id) && s.confirmed))
      throw Error('已确认出库单不能删除')
    if (kind === 'orders') next.orders = next.orders.filter((r) => !ids.includes(r.id))
    else next.shipments = next.shipments.filter((r) => !ids.includes(r.id))
    commit(next, '删除' + (kind === 'orders' ? '销售订单' : '产品出库'), `${ids.length} 条`)
  }
  function pay(input: SalesPayment) {
    const next = snapshot(),
      order = next.orders.find((o) => o.id === input.orderId)
    if (!order || !['待发货', '部分发货', '已完成'].includes(order.status))
      throw Error('当前订单状态不能收款')
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
      row.code = nextSalesCode('SO', next.orders, row.signed)
      row.status = '待发货'
      next.orders.unshift(row)
    }
    commit(next, '导入销售订单', `${rows.length} 条`)
  }
  function importShipments(rows: SalesShipment[]) {
    const next = snapshot()
    for (const row of rows) {
      const error = validateShipment(row, next)
      if (error) throw Error(error)
      row.code = nextSalesCode('OUT', next.shipments, row.shippedAt || undefined)
      next.shipments.unshift(row)
    }
    commit(next, '导入产品出库', `${rows.length} 条`)
  }
  return {
    state,
    loadError,
    loading,
    load,
    saveOrder,
    saveAttachments,
    saveShipment,
    remove,
    pay,
    returnGoods,
    importOrders,
    importShipments,
  }
})
