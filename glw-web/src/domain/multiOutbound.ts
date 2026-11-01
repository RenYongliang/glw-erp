import type { SalesOrder, SalesLine, ShipmentLine } from './sales'
import type { BatchStock } from './shipmentBatches'

export interface PendingOutboundLine {
  order: SalesOrder
  line: SalesLine
  pending: number
}
export interface MultiOutboundBatch extends BatchStock {
  dateCode?: string
  lotCode?: string
  selected: boolean
  outboundQuantity: number | undefined
  quantities: Record<string, number | undefined>
}
export interface MultiOutboundProduct {
  productId: string
  brand?: string
  model?: string
  packaging?: string
  pending: number
  batches: MultiOutboundBatch[]
}
const active = (entry: PendingOutboundLine) =>
  entry.pending > 0 && ['待发货', '部分发货'].includes(entry.order.status)

export function pendingOutboundProducts(entries: PendingOutboundLine[]) {
  const products = new Map<string, MultiOutboundProduct>()
  for (const entry of entries.filter(active)) {
    const product = products.get(entry.line.productId) ?? {
      productId: entry.line.productId,
      brand: entry.line.brand,
      model: entry.line.model,
      packaging: entry.line.packaging,
      pending: 0,
      batches: [],
    }
    product.pending += entry.pending
    products.set(product.productId, product)
  }
  return [...products.values()].sort((a, b) =>
    (a.model || '').localeCompare(b.model || '', 'zh-CN', { numeric: true }),
  )
}

export function matchingOutboundOrders(entries: PendingOutboundLine[], productIds: string[]) {
  const orders = new Map<string, { order: SalesOrder; matches: PendingOutboundLine[] }>()
  for (const entry of entries.filter((e) => active(e) && productIds.includes(e.line.productId))) {
    const order = orders.get(entry.order.id) ?? { order: entry.order, matches: [] }
    order.matches.push(entry)
    orders.set(entry.order.id, order)
  }
  return [...orders.values()].sort(
    (a, b) =>
      a.order.signed.localeCompare(b.order.signed) || a.order.code.localeCompare(b.order.code),
  )
}

export function outboundAllocationColumns(
  entries: PendingOutboundLine[],
  productId: string,
  orderIds: string[],
) {
  return entries.filter(
    (e) => active(e) && e.line.productId === productId && orderIds.includes(e.order.id),
  )
}

export function buildMultiOutboundLines(
  products: MultiOutboundProduct[],
  entries: PendingOutboundLine[],
  orderIds: string[],
): ShipmentLine[] {
  if (!products.length) throw Error('请选择本次出库产品')
  if (new Set(products.map((p) => p.productId)).size !== products.length)
    throw Error('出库产品不可重复')
  const orders = matchingOutboundOrders(
    entries,
    products.map((p) => p.productId),
  ).filter((e) => orderIds.includes(e.order.id))
  if (
    !orderIds.length ||
    orderIds.length > 100 ||
    new Set(orderIds).size !== orderIds.length ||
    orders.length !== orderIds.length
  )
    throw Error('请选择包含本次出库产品的待发货销售单（最多 100 张）')
  if (new Set(orders.map((e) => e.order.customerId)).size !== 1 || !orders[0]?.order.customerId)
    throw Error('同一出库单只能关联同一客户的销售订单')
  const lines = new Map<string, ShipmentLine>()
  const inventoryIds = new Set<string>()
  for (const product of products) {
    const columns = outboundAllocationColumns(entries, product.productId, orderIds)
    if (!columns.length) throw Error(`${product.model} 没有可分配的销售单`)
    const batches = product.batches.filter((b) => b.selected)
    if (!batches.length) throw Error(`${product.model} 请至少选择一个库存批次`)
    for (const batch of batches) {
      if (inventoryIds.has(batch.inventoryId)) throw Error('同一库存批次不能重复选择')
      inventoryIds.add(batch.inventoryId)
      const quantity = batch.outboundQuantity
      if (!Number.isSafeInteger(quantity) || !quantity || quantity < 1 || quantity > 100000000)
        throw Error(`${batch.batchNo} 的出库数量须为 1 至 100000000 的整数`)
      if (quantity > batch.quantity)
        throw Error(`${batch.batchNo} 的出库数量不能超过可用库存 ${batch.quantity}`)
      let total = 0
      for (const entry of columns) {
        const allocated = batch.quantities[entry.line.id] ?? 0
        if (!Number.isSafeInteger(allocated) || allocated < 0 || allocated > 100000000)
          throw Error('分配数量须为 0 至 100000000 的整数')
        if (!allocated) continue
        total += allocated
        const line = lines.get(entry.line.id) ?? {
          lineId: entry.line.id,
          orderId: entry.order.id,
          arrived: 0,
          location: '',
          batches: [],
        }
        line.arrived += allocated
        if (line.arrived > entry.pending)
          throw Error(
            `${entry.order.code} 的 ${product.model} 分配合计不能超过待发数量 ${entry.pending}`,
          )
        line.batches!.push({
          id: `${batch.inventoryId}-${entry.line.id}`,
          inventoryId: batch.inventoryId,
          batchNo: batch.batchNo,
          quantity: allocated,
          unitCost: batch.unitCost,
        })
        lines.set(entry.line.id, line)
      }
      if (total > quantity) throw Error(`${batch.batchNo} 分配合计不能超过本次出库数量 ${quantity}`)
      if (total < quantity)
        throw Error(`${batch.batchNo} 尚有 ${quantity - total} 件未分配，请分配完整后再出库`)
    }
  }
  for (const { order } of orders) {
    if (![...lines.values()].some((l) => l.orderId === order.id))
      throw Error(`${order.code} 尚未分配出库数量`)
  }
  return [...lines.values()]
}
