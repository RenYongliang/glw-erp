<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { FullScreen } from '@element-plus/icons-vue'
import { fetchPurchaseInboundHistory } from '../../services/purchaseApi'
import { purchaseSupplierName, type PurchaseOrder, type PurchaseReceipt } from '../../domain/purchase'

const props = defineProps<{ order: PurchaseOrder; currentReceiptId: string }>()
const emit = defineEmits<{ close: [] }>()
const full = ref(false)
const loading = ref(true)
const error = ref('')
const receipts = ref<PurchaseReceipt[]>([])
const recordTable = ref<HTMLElement>()
const total = computed(() => receipts.value.filter(receipt => receipt.confirmed).reduce((sum, receipt) => sum + quantity(receipt), 0))
const ordered = computed(() => props.order.lines.reduce((sum, line) => sum + line.quantity, 0))
function quantity(receipt: PurchaseReceipt) {
  return receipt.lines.reduce((sum, line) => sum + line.arrived - line.rejected, 0)
}
function rows(receipt: PurchaseReceipt) {
  // 同一入库单下品牌/型号/封装/Date Code/Lot Code 相同的批次合并为一行并累加数量。
  const grouped = new Map<string, {
    id: string
    brand: string
    model: string
    packaging: string
    dateCode: string
    lotCode: string
    quantity: number
  }>()
  for (const line of receipt.lines) {
    const product = props.order.lines.find(item => item.id === line.lineId)
    const brand = line.brand || product?.brand || '—'
    const model = line.model || product?.model || '—'
    const packaging = line.packaging || product?.packaging || '—'
    for (const batch of line.batches) {
      const dateCode = batch.dateCode || '—'
      const lotCode = batch.lotCode || '—'
      const key = [brand, model, packaging, dateCode, lotCode].join('\u0000')
      const existing = grouped.get(key)
      if (existing) existing.quantity += Number(batch.quantity) || 0
      else
        grouped.set(key, {
          id: `${receipt.id}-${key}`,
          brand,
          model,
          packaging,
          dateCode,
          lotCode,
          quantity: Number(batch.quantity) || 0,
        })
    }
  }
  return [...grouped.values()]
}
/** 所有入库记录展平成一张表，入库单号、入库日期按单合并单元格。 */
const allRows = computed(() =>
  receipts.value.flatMap(receipt =>
    rows(receipt).map(row => ({
      ...row,
      receipt,
      isCurrent: receipt.id === props.currentReceiptId,
    })),
  ),
)
function receiptSpan({ rowIndex, columnIndex }: { rowIndex: number; columnIndex: number }): [number, number] {
  if (columnIndex > 1) return [1, 1]
  const list = allRows.value
  const current = list[rowIndex]
  if (!current) return [1, 1]
  let start = rowIndex
  while (start > 0 && list[start - 1]!.receipt.id === current.receipt.id) start--
  let end = rowIndex
  while (end + 1 < list.length && list[end + 1]!.receipt.id === current.receipt.id) end++
  return rowIndex === start ? [end - start + 1, 1] : [0, 0]
}
function rowClass({ row }: { row: (typeof allRows.value)[number] }) {
  return row.isCurrent ? 'is-current' : ''
}
async function load() {
  loading.value = true
  error.value = ''
  try {
    receipts.value = await fetchPurchaseInboundHistory(props.order.id)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
  await nextTick()
  recordTable.value
    ?.querySelector<HTMLElement>('.history-table tr.is-current')
    ?.scrollIntoView({ block: 'center' })
}
onMounted(load)
</script>

<template>
  <el-dialog :model-value="true" width="min(1000px,96vw)" :fullscreen="full"
    class="purchase-dialog purchase-inbound-history" :close-on-click-modal="false" @close="emit('close')">
    <template #header>
      <div class="history-heading"><strong>采购单入库记录</strong><span>{{ order.code }}</span>
        <el-button text :icon="FullScreen" title="切换全屏" aria-label="切换全屏" @click="full = !full" />
      </div>
    </template>
    <el-alert v-if="error" :title="error" type="error" :closable="false">
      <el-button link type="primary" @click="load">重试</el-button>
    </el-alert>
    <div v-else v-loading="loading" class="history-content">
      <h3 class="history-section-title">入库概览</h3>
      <div class="history-summary">
        <div><span>供应商</span><strong :title="purchaseSupplierName(order)">{{ order.supplierShortName || purchaseSupplierName(order) }}</strong></div>
        <div><span>采购数量</span><strong>{{ ordered.toLocaleString('zh-CN') }}</strong></div>
        <div><span>累计已入库</span><strong>{{ total.toLocaleString('zh-CN') }}</strong></div>
        <div><span>入库次数</span><strong>{{ receipts.length }}</strong></div>
      </div>
      <h3 class="history-section-title">入库记录</h3>
      <el-empty v-if="!loading && !receipts.length" description="暂无入库记录" />
      <div v-else ref="recordTable" class="history-table-wrap">
        <el-table
          :data="allRows"
          row-key="id"
          size="small"
          border
          max-height="56vh"
          class="history-table"
          :span-method="receiptSpan"
          :row-class-name="rowClass"
        >
          <el-table-column label="入库单号" min-width="120">
            <template #default="{ row }">
              <div class="history-code">
                <strong>{{ row.receipt.code }}</strong>
                <el-tag v-if="!row.receipt.confirmed" type="info" size="small" effect="plain">未完成</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="入库日期" width="150">
            <template #default="{ row }">{{ row.receipt.receivedAt || '—' }}</template>
          </el-table-column>
          <el-table-column prop="model" label="型号" min-width="120" show-overflow-tooltip />
          <el-table-column prop="brand" label="品牌" width="80" show-overflow-tooltip />
          <el-table-column prop="packaging" label="封装" min-width="130" show-overflow-tooltip />
          <el-table-column prop="dateCode" label="Date Code" width="100" show-overflow-tooltip />
          <el-table-column prop="lotCode" label="Lot Code" width="120" show-overflow-tooltip />
          <el-table-column label="入库数量" width="100">
            <template #default="{ row }">{{ row.quantity.toLocaleString('zh-CN') }}</template>
          </el-table-column>
        </el-table>
        <p v-if="allRows.some(row => row.isCurrent)" class="history-note">注：浅绿色背景为本次入库行。</p>
      </div>
    </div>
    <template #footer><el-button @click="emit('close')">关闭</el-button></template>
  </el-dialog>
</template>

<style scoped>
:global(.purchase-inbound-history.el-dialog) { display: flex; flex-direction: column; max-height: 90vh; margin-top: 5vh; margin-bottom: 5vh; }
:global(.purchase-inbound-history.el-dialog.is-fullscreen) { height: 100vh; max-height: 100vh; margin: 0; }
:global(.purchase-inbound-history > .el-dialog__body) { display: flex; flex-direction: column; min-height: 0; overflow: hidden; }
:global(.purchase-inbound-history > .el-dialog__header), :global(.purchase-inbound-history > .el-dialog__footer) { flex-shrink: 0; }
.history-heading { display: flex; align-items: center; gap: 12px; padding-right: 22px; flex-wrap: wrap; }
.history-heading > strong { font-size: 16px; }
.history-heading > span { color: #64748b; font-size: 13px; overflow-wrap: anywhere; }
.history-heading .el-button { margin-left: auto; }
.history-content { display: flex; flex-direction: column; min-height: 0; }
.history-section-title { position: relative; margin: 18px 0 14px; padding: 0 0 10px; border-bottom: 4px solid #d2e6ff; color: #1384ff; font-size: 14px; font-weight: 600; line-height: 22px; }
.history-section-title:after { content: ''; position: absolute; bottom: -4px; left: 0; width: 112px; height: 7px; background: #1384ff; border-radius: 0 8px 0 6px; }
.history-content > .history-section-title:first-child { margin-top: 0; }
.history-summary { display: flex; flex-shrink: 0; flex-wrap: wrap; gap: 16px 36px; padding: 0 0 6px; }
.history-summary > div { display: flex; flex-direction: column; gap: 6px; min-width: 100px; }
.history-summary span { font-size: 12px; color: #64748b; }
.history-summary strong { color: #263445; font-size: 14px; overflow-wrap: anywhere; }
.history-table-wrap { min-height: 0; }
.history-table { font-size: 12px; }
.history-table :deep(.el-table__cell) { padding: 6px 0; }
.history-table :deep(.cell) { line-height: 22px; }
.history-table :deep(tr.is-current > td.el-table__cell) { background: #e3f6ee; }
.history-table :deep(tr.is-current:hover > td.el-table__cell) { background: #d7f0e4; }
.history-code { display: flex; align-items: center; gap: 8px; }
.history-code strong { color: #334155; }
.history-note { margin: 8px 0 0; color: #8a94a6; font-size: 12px; }
@media (max-width: 600px) {
  .history-summary { gap: 14px 24px; }
}
</style>
