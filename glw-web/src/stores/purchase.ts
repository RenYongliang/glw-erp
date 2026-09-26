import { ref } from 'vue'
import { defineStore } from 'pinia'
import {
  suppliers,
  products,
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

function initial(): PurchaseState {
  const orders: PurchaseOrder[] = Array.from({ length: 26 }, (_, i) => {
    const supplier = suppliers[i % 3]!,
      product = products[i % products.length]!,
      second = products[(i + 1) % products.length]!
    return {
      id: `order-${i + 1}`,
      code: `CGDD2609${String(1 + (i % 20)).padStart(2, '0')}-${String(i + 1).padStart(2, '0')}`,
      name: `${supplier.name}-第${Math.floor(i / 3) + 1}份采购订单`,
      supplierId: supplier.id,
      signed: `2026-09-${String(1 + (i % 20)).padStart(2, '0')}`,
      delivery: '2026-09-30',
      owner: i % 3 ? '张睿' : '林晓',
      department: i % 2 ? '采购部' : '业务部',
      warehouse: i % 2 ? '无锡仓' : '杭州仓',
      address: i % 2 ? '江苏省无锡市锡山区安镇街道 188号' : '浙江省杭州市余杭区贝达梦工厂E座',
      discount: i % 4 === 0 ? 0 : 50,
      lines: [
        {
          id: `line-${i + 1}-1`,
          productId: product.id,
          quantity: 1000,
          price: product.price,
          tax: 13,
        },
        {
          id: `line-${i + 1}-2`,
          productId: second.id,
          quantity: 500,
          price: second.price,
          tax: 13,
        },
      ],
      arrivals: [{ batch: '整批', date: '2026-09-30', note: '' }],
      payments: [
        { name: '整批货款', ratio: 100, method: '银行转账', date: '2026-10-15', note: '' },
      ],
      attachments: [],
      term: '月结30天',
      status: i >= 24 ? '不通过' : i >= 22 ? '待审批' : '审批通过',
      reason: i >= 24 ? '请核对供应商报价后重新提交' : '',
      approver: i < 22 ? '张睿' : '',
      approvedAt: i < 22 ? '2026-09-22 09:00' : '',
    }
  })
  const receipts: PurchaseReceipt[] = Array.from({ length: 23 }, (_, i) => {
    const order = orders[i < 21 ? i : i - 21]!,
      partial = i < 2 || i >= 21,
      rejected = i === 3 ? 10 : 0
    return {
      id: `receipt-${i + 1}`,
      code: `CGRK260923-${String(i + 1).padStart(2, '0')}`,
      orderId: order.id,
      warehouse: order.warehouse,
      lines: order.lines.map((l, k) => ({
        lineId: l.id,
        arrived: partial ? Math.floor(l.quantity / 2) : l.quantity,
        rejected: k === 0 ? rejected : 0,
        location: `${order.warehouse === '杭州仓' ? 'HZ' : 'WX'}-A0${k + 1}`,
      })),
      quality: rejected ? '存在不合格品' : '合格',
      qualityNote: rejected ? '外观检测异常，已隔离待退换' : '',
      inspector: '李敏',
      inspectionDepartment: '质量部',
      inspectedAt: '2026-09-23 09:00',
      confirmed: i !== 20,
      receivedAt: i !== 20 ? '2026-09-23 10:00' : '',
      keeper: '陈晨',
      department: '仓储部',
      photos: [],
    }
  })
  return { orders, receipts, payments: [], returns: [], logs: [] }
}
export const usePurchaseStore = defineStore('purchase', () => {
  const state = ref<PurchaseState>(initial()),
    loadError = ref('')
  function load() {
    try {
      const text = localStorage.getItem(purchaseStorageKey)
      if (!text) return
      const saved = JSON.parse(text)
      if (
        !['orders', 'receipts', 'payments', 'returns', 'logs'].every((k) =>
          Array.isArray(saved[k]),
        ) ||
        !saved.orders.every(
          (o: PurchaseOrder) =>
            typeof o.id === 'string' &&
            Array.isArray(o.lines) &&
            Array.isArray(o.arrivals) &&
            Array.isArray(o.payments) &&
            Array.isArray(o.attachments),
        ) ||
        !saved.receipts.every(
          (r: PurchaseReceipt) => Array.isArray(r.lines) && Array.isArray(r.photos),
        )
      )
        throw Error()
      state.value = saved
      loadError.value = ''
    } catch {
      loadError.value = '本地采购数据无法读取，显示初始演示数据。'
    }
  }
  load()
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
  function saveReceipt(input: PurchaseReceipt) {
    const next = snapshot(),
      receipt: PurchaseReceipt = JSON.parse(JSON.stringify(input)),
      error = validateReceipt(receipt, next)
    if (error) throw Error(error)
    const index = next.receipts.findIndex((r) => r.id === receipt.id)
    if (index >= 0 && next.receipts[index]!.orderId !== receipt.orderId)
      throw Error('已保存的入库单不能更换采购订单')
    if (index >= 0 && next.returns.some((r) => r.orderId === receipt.orderId))
      throw Error('该订单已有退货记录，不能再修改入库记录')
    if (index < 0) {
      receipt.code = nextPurchaseCode('CGRK', next.receipts)
      next.receipts.unshift(receipt)
    } else next.receipts[index] = receipt
    commit(next, index < 0 ? '新增采购入库' : '修改采购入库', receipt.code)
    return receipt
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
