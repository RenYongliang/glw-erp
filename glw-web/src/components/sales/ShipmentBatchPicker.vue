<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchProductCostBatches } from '../../services/productApi'
import { formatUnitCost } from '../../services/productApiData'
import { allocateFifo, validateBatchSelection, type BatchStock, type ShipmentBatch } from '../../domain/shipmentBatches'

const props = defineProps<{
  productId: string
  productName: string
  pending: number
  batches: ShipmentBatch[]
  usedQuantities: Record<string, number>
}>()
const emit = defineEmits<{ close: []; selected: [batches: ShipmentBatch[]] }>()
const loading = ref(false), error = ref('')
const rows = ref<(BatchStock & { selected: boolean; shippingQuantity: number })[]>([])
const selected = computed<ShipmentBatch[]>(() => rows.value.filter(r => r.selected).map(r => ({
  id: props.batches.find(b => b.inventoryId === r.inventoryId)?.id || r.inventoryId,
  inventoryId: r.inventoryId, batchNo: r.batchNo, quantity: r.shippingQuantity, unitCost: r.unitCost,
})))
const total = computed(() => selected.value.reduce((sum, b) => sum + b.quantity, 0))
async function load() {
  loading.value = true
  error.value = ''
  try {
    const stock = await fetchProductCostBatches(props.productId)
    rows.value = stock.map(batch => {
      const previous = props.batches.find(b => b.inventoryId === batch.inventoryId)
      return { ...batch, quantity: Math.max(0, batch.quantity - (props.usedQuantities[batch.inventoryId] || 0)),
        selected: !!previous, shippingQuantity: previous?.quantity ?? 0 }
    })
  } catch (e) { error.value = e instanceof Error ? e.message : '批次加载失败' }
  finally { loading.value = false }
}
function toggle(row: typeof rows.value[number], checked: boolean) {
  row.selected = checked
  row.shippingQuantity = checked ? Math.max(0, Math.min(row.quantity, props.pending - total.value)) : 0
}
function fifo() {
  const batches = allocateFifo(rows.value, props.pending)
  rows.value.forEach(row => {
    const batch = batches.find(b => b.inventoryId === row.inventoryId)
    row.selected = !!batch
    row.shippingQuantity = batch?.quantity ?? 0
  })
}
function confirm() {
  const message = validateBatchSelection(selected.value, rows.value, props.pending)
  if (message) return void ElMessage.warning(message)
  emit('selected', selected.value)
}
onMounted(load)
</script>

<template>
  <el-dialog :model-value="true" title="选择发货批次" width="min(860px,94vw)" append-to-body class="purchase-dialog shipment-batch-picker" @close="emit('close')">
    <div class="shipment-batch-summary"><span>{{ productName }}</span><span>待发数量：{{ pending }}</span></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button link type="primary" @click="load">重试</el-button></el-alert>
    <template v-else>
      <div class="shipment-batch-tools"><el-button plain type="primary" :disabled="loading || !rows.some(r => r.quantity > 0)" @click="fifo">按 FIFO 分配</el-button><span>本次发货数量：{{ total }}</span></div>
      <el-table v-loading="loading" :data="rows" row-key="inventoryId" border max-height="400" :empty-text="loading ? '加载中…' : '暂无可发货批次'">
        <el-table-column width="45" align="center"><template #default="{row}"><el-checkbox :model-value="row.selected" :disabled="row.quantity <= 0" :aria-label="`选择批次${row.batchNo}`" @change="toggle(row, !!$event)" /></template></el-table-column>
        <el-table-column prop="batchNo" label="批次号" min-width="220" show-overflow-tooltip />
        <el-table-column prop="quantity" label="可发库存" min-width="100" align="right" />
        <el-table-column label="单位成本" min-width="105" align="right"><template #default="{row}">{{ formatUnitCost(row.unitCost) }}</template></el-table-column>
        <el-table-column label="本批发货数量" width="165"><template #default="{row}"><el-input-number v-model="row.shippingQuantity" :disabled="!row.selected" :min="0" :max="Math.min(row.quantity, pending)" :precision="0" :step="100" controls-position="right" :aria-label="`${row.batchNo}发货数量`" /></template></el-table-column>
      </el-table>
    </template>
    <template #footer><el-button @click="emit('close')">取消</el-button><el-button type="primary" :disabled="loading || !!error" @click="confirm">确定批次</el-button></template>
  </el-dialog>
</template>

<style>
.shipment-batch-summary, .shipment-batch-tools { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 16px; }
.shipment-batch-summary > span { overflow-wrap: anywhere; }
.shipment-batch-picker .el-input-number { width: 100%; }
</style>
