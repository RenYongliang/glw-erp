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
const recordList = ref<HTMLElement>()
const total = computed(() => receipts.value.filter(receipt => receipt.confirmed).reduce((sum, receipt) => sum + quantity(receipt), 0))
const ordered = computed(() => props.order.lines.reduce((sum, line) => sum + line.quantity, 0))
function quantity(receipt: PurchaseReceipt) {
  return receipt.lines.reduce((sum, line) => sum + line.arrived - line.rejected, 0)
}
function rows(receipt: PurchaseReceipt) {
  return receipt.lines.flatMap(line => {
    const product = props.order.lines.find(item => item.id === line.lineId)
    return line.batches.map(batch => ({
      id: `${line.lineId}-${batch.id}`,
      brand: line.brand || product?.brand || '—',
      model: line.model || product?.model || '—',
      packaging: line.packaging || product?.packaging || '—',
      dateCode: batch.dateCode || '—',
      lotCode: batch.lotCode || '—',
      quantity: batch.quantity,
    }))
  })
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
  const current = recordList.value?.querySelector<HTMLElement>('.is-current')
  if (current && recordList.value) recordList.value.scrollTop = current.offsetTop
}
onMounted(load)
</script>

<template>
  <el-dialog :model-value="true" width="min(1060px,96vw)" :fullscreen="full"
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
      <div class="history-summary">
        <div><span>供应商</span><strong :title="purchaseSupplierName(order)">{{ order.supplierShortName || purchaseSupplierName(order) }}</strong></div>
        <div><span>采购数量</span><strong>{{ ordered.toLocaleString('zh-CN') }}</strong></div>
        <div><span>累计已入库</span><strong>{{ total.toLocaleString('zh-CN') }}</strong></div>
        <div><span>入库次数</span><strong>{{ receipts.length }}</strong></div>
      </div>
      <el-empty v-if="!loading && !receipts.length" description="暂无入库记录" />
      <div ref="recordList" class="history-records">
        <section v-for="receipt in receipts" :key="receipt.id" class="history-record"
          :class="{ 'is-current': receipt.id === currentReceiptId }" :data-receipt-id="receipt.id">
          <header class="history-record-heading">
            <strong>{{ receipt.code }}</strong>
            <el-tag v-if="receipt.id === currentReceiptId" type="success" size="small" effect="light">本次入库</el-tag>
            <span>{{ receipt.receivedAt || '—' }}</span>
            <span v-if="!receipt.confirmed">未完成</span>
            <span class="history-record-total">入库数量 <b>{{ quantity(receipt).toLocaleString('zh-CN') }}</b></span>
          </header>
          <el-table :data="rows(receipt)" row-key="id" size="small" class="history-product-table">
            <el-table-column prop="brand" label="品牌" width="85" show-overflow-tooltip />
            <el-table-column prop="model" label="型号" min-width="145" show-overflow-tooltip />
            <el-table-column prop="packaging" label="封装" min-width="160" show-overflow-tooltip />
            <el-table-column prop="dateCode" label="Date Code" min-width="110" show-overflow-tooltip />
            <el-table-column prop="lotCode" label="Lot Code" min-width="130" show-overflow-tooltip />
            <el-table-column label="入库数量" width="110" align="right">
              <template #default="{ row }">{{ row.quantity.toLocaleString('zh-CN') }}</template>
            </el-table-column>
          </el-table>
        </section>
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
.history-summary { display: flex; flex-shrink: 0; flex-wrap: wrap; gap: 16px 36px; padding: 4px 0 18px; border-bottom: 1px solid #e3e8f0; }
.history-summary > div { display: flex; flex-direction: column; gap: 6px; min-width: 100px; }
.history-summary span { font-size: 12px; color: #64748b; }
.history-summary strong { color: #263445; font-size: 14px; overflow-wrap: anywhere; }
.history-records { position: relative; min-height: 0; max-height: 62vh; overflow: auto; }
.history-record { border-bottom: 1px solid #e3e8f0; padding: 14px 0; }
.history-record-heading { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 12px; padding: 9px 12px; background: #f1f4f7; font-size: 12px; color: #64748b; }
.history-record-heading > strong { color: #334155; font-size: 13px; }
.history-record-total { margin-left: auto; }
.history-record-total b { color: #334155; margin-left: 6px; font-variant-numeric: tabular-nums; }
.history-product-table { --el-table-header-bg-color: #fafbfc; font-size: 12px; }
.is-current .history-record-heading { background: #dcf3eb; box-shadow: inset 3px 0 #00a487; }
.is-current .history-record-heading > strong, .is-current .history-record-total b { color: #007d66; font-weight: 700; }
.is-current .history-product-table { --el-table-tr-bg-color: #f1fbf7; --el-table-header-bg-color: #eaf7f1; }
.history-product-table :deep(.el-table__cell) { padding: 6px 0; }
.history-product-table :deep(.cell) { line-height: 22px; }
@media (max-width: 600px) {
  .history-summary { gap: 14px 24px; }
  .history-record-total { margin-left: 0; }
}
</style>
