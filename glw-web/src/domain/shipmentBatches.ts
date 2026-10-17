export interface BatchStock {
  inventoryId: string
  batchNo: string
  quantity: number
  unitCost: number
}

export interface ShipmentBatch {
  id: string
  inventoryId: string
  batchNo?: string
  quantity: number
  unitCost: number
}

export function validateBatchSelection(batches: ShipmentBatch[], stock: BatchStock[], pending: number) {
  if (new Set(batches.map(b => b.inventoryId)).size !== batches.length) return '同一批次不能重复选择'
  let total = 0
  for (const batch of batches) {
    const source = stock.find(s => s.inventoryId === batch.inventoryId)
    if (!source) return '所选批次已无库存，请重新选择'
    if (!Number.isSafeInteger(batch.quantity) || batch.quantity <= 0) return '批次数量须为正整数'
    if (batch.quantity > source.quantity) return '批次发货数量不能超过剩余库存'
    total += batch.quantity
  }
  return total > pending ? '本次发货数量不能超过待发数量' : ''
}

export function allocateFifo(stock: BatchStock[], pending: number): ShipmentBatch[] {
  let remaining = pending
  const result: ShipmentBatch[] = []
  for (const batch of stock) {
    const quantity = Math.min(batch.quantity, remaining)
    if (quantity <= 0) continue
    result.push({ id: batch.inventoryId, inventoryId: batch.inventoryId, batchNo: batch.batchNo, quantity, unitCost: batch.unitCost })
    remaining -= quantity
    if (remaining === 0) break
  }
  return result
}
