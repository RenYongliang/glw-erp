import type { PurchaseOrder, PurchaseLine, ReceiptLine } from './purchase'

export interface PendingInboundLine {
  order: PurchaseOrder
  line: PurchaseLine
  pending: number
}
export interface MultiInboundBatch {
  id: string
  dateCode: string
  lotCode: string
  quantity: number | undefined
  quantities: Record<string, number | undefined>
}
export interface MultiInboundProduct {
  productId: string
  brand?: string
  model?: string
  packaging?: string
  batches: MultiInboundBatch[]
}

const active = (entry: PendingInboundLine) =>
  entry.pending > 0 && ['待收货', '部分收货'].includes(entry.order.status)

export function pendingInboundProducts(entries: PendingInboundLine[]) {
  const products = new Map<string, MultiInboundProduct & { pending: number }>()
  for (const entry of entries.filter(active)) {
    const product = products.get(entry.line.productId) ?? {
      productId: entry.line.productId,
      brand: entry.line.brand,
      model: entry.line.model,
      packaging: entry.line.packaging,
      batches: [],
      pending: 0,
    }
    product.pending += entry.pending
    products.set(product.productId, product)
  }
  return [...products.values()]
}

export function matchingInboundOrders(entries: PendingInboundLine[], productIds: string[]) {
  const orders = new Map<string, { order: PurchaseOrder; matches: PendingInboundLine[] }>()
  for (const entry of entries.filter(
    (item) => active(item) && productIds.includes(item.line.productId),
  )) {
    const match = orders.get(entry.order.id) ?? { order: entry.order, matches: [] }
    match.matches.push(entry)
    orders.set(entry.order.id, match)
  }
  return [...orders.values()]
}

export function inboundAllocationColumns(
  entries: PendingInboundLine[],
  productId: string,
  orderIds: string[],
) {
  return entries.filter(
    (entry) =>
      active(entry) && entry.line.productId === productId && orderIds.includes(entry.order.id),
  )
}

export function buildMultiInboundLines(
  products: MultiInboundProduct[],
  entries: PendingInboundLine[],
  orderIds: string[],
): ReceiptLine[] {
  if (!products.length) throw Error('请选择本次入库产品')
  const orders = matchingInboundOrders(
    entries,
    products.map((p) => p.productId),
  ).filter((item) => orderIds.includes(item.order.id))
  if (
    orderIds.length < 2 ||
    new Set(orderIds).size !== orderIds.length ||
    orders.length !== orderIds.length
  )
    throw Error('请选择至少两张包含本次入库产品的待入库采购单')
  if (new Set(orders.map((item) => item.order.supplierId)).size > 1)
    throw Error('同一入库单只能关联同一供应商的采购订单')
  const lines = new Map<string, ReceiptLine>()
  for (const product of products) {
    const columns = inboundAllocationColumns(entries, product.productId, orderIds)
    if (!columns.length) throw Error(`${product.model || product.productId} 没有可分配的采购单`)
    if (!product.batches.length)
      throw Error(`${product.model || product.productId} 至少保留一个入库批次`)
    const keys = new Set<string>()
    for (const [index, batch] of product.batches.entries()) {
      const dateCode = batch.dateCode.trim(),
        lotCode = batch.lotCode.trim()
      if (!dateCode || dateCode.length > 32)
        throw Error(`${product.model} 第 ${index + 1} 批请填写 32 字以内的 Date Code`)
      if (!lotCode || lotCode.length > 64)
        throw Error(`${product.model} 第 ${index + 1} 批请填写 64 字以内的 Lot Code`)
      const key = JSON.stringify([dateCode.toLowerCase(), lotCode.toLowerCase()])
      if (keys.has(key)) throw Error(`${product.model} 的 Date Code + Lot Code 不可重复`)
      keys.add(key)
      if (
        !Number.isSafeInteger(batch.quantity) ||
        !batch.quantity ||
        batch.quantity < 1 ||
        batch.quantity > 100000000
      )
        throw Error(`${product.model} 第 ${index + 1} 批的入库数量须为 1 至 100000000 的整数`)
      let total = 0
      for (const entry of columns) {
        const quantity = batch.quantities[entry.line.id] ?? 0
        if (!Number.isSafeInteger(quantity) || quantity < 0 || quantity > 100000000)
          throw Error(`${product.model} 的分配数量须为 0 至 100000000 的整数`)
        if (!quantity) continue
        total += quantity
        const line = lines.get(entry.line.id) ?? {
          orderId: entry.order.id,
          lineId: entry.line.id,
          productId: product.productId,
          brand: product.brand,
          model: product.model,
          packaging: product.packaging,
          unitPrice: entry.line.price,
          arrived: 0,
          rejected: 0,
          location: '',
          batches: [],
        }
        line.batches.push({ id: `${batch.id}-${entry.line.id}`, dateCode, lotCode, quantity })
        line.arrived += quantity
        if (line.arrived > entry.pending)
          throw Error(
            `${entry.order.code} 的 ${product.model} 分配合计不能超过待交数量 ${entry.pending}`,
          )
        lines.set(entry.line.id, line)
      }
      if (total > batch.quantity)
        throw Error(
          `${product.model} 第 ${index + 1} 批分配合计不能超过本次入库数量 ${batch.quantity}`,
        )
      if (total < batch.quantity)
        throw Error(
          `${product.model} 第 ${index + 1} 批尚有 ${batch.quantity - total} 件未分配，请分配完整后再入库`,
        )
    }
  }
  for (const { order } of orders) {
    if (![...lines.values()].some((line) => line.orderId === order.id))
      throw Error(`${order.code} 尚未分配入库数量`)
  }
  return [...lines.values()]
}
