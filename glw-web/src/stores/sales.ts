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
  type SalesReturn,
  type Product,
} from '../domain/sales'
import {
  cancelSalesOrder,
  createOutboundOrder,
  createSalesOrder,
  fetchSalesOrders,
  fetchOutboundOrders,
  reverseOutboundOrder,
  signOutboundOrder,
  unsignOutboundOrder,
  updateOutboundShipping,
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
      return state.value.shipments.find(
        (item) => item.id === String(created.id),
      ) ?? shipment
    }
    if (persisted && existing.confirmed) {
      const previous = existing
      // 已确认出库单只允许修改收货/物流信息和客户收货确认，其余字段仍以服务端为准。
      const editableView = (value: SalesShipment) => ({
        ...value,
        customerConfirmed: false,
        customerReceivedAt: '',
        shipping: undefined,
      })
      if (JSON.stringify(editableView(previous)) !== JSON.stringify(editableView(shipment)))
        throw Error('已确认出库单仅允许修改发货信息和客户收货确认')
      let changed = false
      if (
        JSON.stringify(previous.shipping ?? null) !==
        JSON.stringify(shipment.shipping ?? null)
      ) {
        if (!shipment.shipping) throw Error('请填写发货信息')
        await updateOutboundShipping(previous.id, shipment.shipping)
        changed = true
      }
      if (!shipment.customerConfirmed && previous.customerConfirmed) {
        await unsignOutboundOrder(previous.id)
        changed = true
      } else if (
        shipment.customerConfirmed &&
        shipment.customerReceivedAt &&
        shipment.customerReceivedAt.replace('T', ' ').slice(0, 16) !==
          previous.customerReceivedAt.replace('T', ' ').slice(0, 16)
      ) {
        await signOutboundOrder(previous.id, shipment.customerReceivedAt)
        changed = true
      }
      if (changed) {
        await load()
        return state.value.shipments.find((item) => item.id === previous.id) ?? shipment
      }
      return previous
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
  /** 取消尚未出库的销售订单。 */
  async function cancelOrder(id: string) {
    await cancelSalesOrder(id)
    await load()
  }

  /** 撤销未签收的出库单，后端会回补库存并作废未生效应收。 */
  async function reverseShipment(id: string) {
    await reverseOutboundOrder(id)
    await load()
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
      row.orderStatus = '执行中'
      row.deliveryStatus = '未发货'
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
    cancelOrder,
    saveShipment,
    reverseShipment,
    returnGoods,
    importOrders,
    importShipments,
  }
})
