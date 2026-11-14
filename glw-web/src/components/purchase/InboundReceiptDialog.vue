<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, FullScreen } from '@element-plus/icons-vue'
import { usePurchaseStore } from '../../stores/purchase'
import { useAuthStore } from '../../stores/auth'
import { fetchInboundDetail } from '../../services/purchaseApi'
import AttachmentTiles from '../common/AttachmentTiles.vue'
import {
  deleteOrderAttachment,
  fetchOrderAttachments,
  uploadOrderAttachment,
  type AttachmentVO,
} from '../../services/attachmentApi'
import type { AttachmentTypeDef } from '../../domain/attachments'
import {
  receiptOrders,
  receiptOrderIds,
  receiptProducts,
  remainingQuantity,
  today,
  purchaseSupplierName,
  type PurchaseOrder,
  type PurchaseReceipt,
  type ReceiptLine,
} from '../../domain/purchase'

const props = defineProps<{
  receipt?: PurchaseReceipt
  scopeOrder?: PurchaseOrder
}>()
const emit = defineEmits<{ close: []; saved: [] }>()
const store = usePurchaseStore()
const auth = useAuthStore()
const view = !!props.receipt
const title = props.scopeOrder ? '采购单入库明细' : view ? '入库详情' : '新增入库'
const full = ref(false)
const saving = ref(false)
const loading = ref(false)
const loadError = ref('')
const dirty = ref(false)
const selectedId = ref('')
const inboundReceiptTypes: AttachmentTypeDef[] = [
  { key: 'INBOUND_RECEIPT', label: '收货回签', slots: 'unlimited' },
]
const pendingPhotos = ref<{ file: File; preview: PurchaseReceipt['photos'][number] }[]>([])
const receipt = ref<PurchaseReceipt>(
  props.receipt
    ? structuredClone(JSON.parse(JSON.stringify(props.receipt)))
    : {
        id: crypto.randomUUID(),
        requestKey: crypto.randomUUID(),
        code: '',
        orderId: '',
        orderIds: [],
        warehouse: '',
        lines: [],
        quality: '合格',
        qualityNote: '',
        inspector: '',
        inspectionDepartment: '',
        inspectedAt: '',
        confirmed: true,
        receivedAt: `${today()} 09:00:00`,
        keeper: auth.user?.name || '入库员',
        department: auth.user?.department || '',
        photos: [],
      },
)
const orders = computed(() => receiptOrders(receipt.value, store.state.orders))
const products = computed(() => receiptProducts(receipt.value, orders.value))
const quantity = computed(() =>
  receipt.value.lines.reduce(
    (sum, line) => sum + line.batches.reduce((s, batch) => s + (Number(batch.quantity) || 0), 0),
    0,
  ),
)
const supplier = computed(
  () => purchaseSupplierName(orders.value[0]) || receipt.value.supplierName || '—',
)
const availableOrders = computed(() =>
  store.state.orders.filter(
    (order) =>
      !receiptOrderIds(receipt.value).includes(order.id) &&
      order.orderStatus === '执行中' &&
      (!orders.value.length || order.supplierId === orders.value[0]?.supplierId) &&
      order.lines.some((line) => remainingQuantity(store.state, order.id, line.id) > 0),
  ),
)
const batch = () => ({
  id: crypto.randomUUID(),
  dateCode: '',
  lotCode: '',
  quantity: undefined as unknown as number,
})
const omittedLines = new Map<string, ReceiptLine>()
const candidateLines = computed(() =>
  orders.value.flatMap((order) =>
    order.lines
      .filter((line) => remainingQuantity(store.state, order.id, line.id) > 0)
      .map((line) => ({ line, order })),
  ),
)
const selectedLines = computed({
  get: () => receipt.value.lines.map((line) => line.lineId),
  set: (ids: string[]) => {
    receipt.value.lines.forEach((line) => omittedLines.set(line.lineId, line))
    receipt.value.lines = ids.map((id) => omittedLines.get(id)!).filter(Boolean)
    dirty.value = true
  },
})
function addOrder(order?: PurchaseOrder) {
  order ??= availableOrders.value.find((o) => o.id === selectedId.value)
  if (!order) return
  receipt.value.orderIds = [...receiptOrderIds(receipt.value), order.id]
  receipt.value.orderId = receipt.value.orderIds[0] || ''
  receipt.value.warehouse = order.warehouse
  for (const line of order.lines.filter(
    (l) => remainingQuantity(store.state, order!.id, l.id) > 0,
  )) {
    const allocation: ReceiptLine = {
      orderId: order.id,
      lineId: line.id,
      productId: line.productId,
      brand: line.brand,
      model: line.model,
      packaging: line.packaging,
      unitPrice: line.price,
      arrived: 0,
      rejected: 0,
      location: '',
      batches: [batch()],
    }
    receipt.value.lines.push(allocation)
    omittedLines.set(line.id, allocation)
  }
  selectedId.value = ''
  dirty.value = true
}
function removeOrder(id: string) {
  receipt.value.lines = receipt.value.lines.filter((line) => line.orderId !== id)
  receipt.value.orderIds = receiptOrderIds(receipt.value).filter((orderId) => orderId !== id)
  receipt.value.orderId = receipt.value.orderIds[0] || ''
  dirty.value = true
}
function orderFor(line: ReceiptLine) {
  return orders.value.find((order) => order.lines.some((item) => item.id === line.lineId))
}
function rows(lines: ReceiptLine[]) {
  const list = lines.flatMap((line) =>
    line.batches.map((batch, index) => ({ line, batch, index })),
  )
  if (!view) return list
  // 详情态按 Date Code、Lot Code 归组，让相同编码的批次相邻，便于合并单元格。
  const key = (row: (typeof list)[number]) =>
    `${row.batch.dateCode || ''}\u0000${row.batch.lotCode || ''}`
  return [...list].sort((a, b) => key(a).localeCompare(key(b)))
}
/**
 * Date Code、Lot Code 相同的相邻批次合并单元格。
 * 当前表格前两列就是这两列，其余列保持默认。
 */
function batchSpan(lines: ReceiptLine[], rowIndex: number, columnIndex: number): [number, number] {
  if (columnIndex > 1) return [1, 1]
  const list = rows(lines)
  const current = list[rowIndex]
  if (!current) return [1, 1]
  const key = (row: (typeof list)[number]) =>
    `${row.batch.dateCode || ''}\u0000${row.batch.lotCode || ''}`
  const currentKey = key(current)
  let start = rowIndex
  while (start > 0 && key(list[start - 1]!) === currentKey) start--
  let end = rowIndex
  while (end + 1 < list.length && key(list[end + 1]!) === currentKey) end++
  return rowIndex === start ? [end - start + 1, 1] : [0, 0]
}
function batchSpanMethod(lines: ReceiptLine[]) {
  // 仅详情态合并；新增入库仍需逐批填写 Date Code / Lot Code。
  if (!view) return () => [1, 1]
  return ({ rowIndex, columnIndex }: { rowIndex: number; columnIndex: number }) =>
    batchSpan(lines, rowIndex, columnIndex)
}
function addProductBatch(lines: ReceiptLine[], id: string) {
  const line = lines.find((line) => line.lineId === id)
  if (line) addBatch(line)
}
const rowKey = (row: ReturnType<typeof rows>[number]) => row.batch.id
function remaining(line: ReceiptLine) {
  const order = orderFor(line)
  return order ? remainingQuantity(store.state, order.id, line.lineId) : 0
}
function syncQuantity(line: ReceiptLine) {
  line.arrived = line.batches.reduce((sum, batch) => sum + (Number(batch.quantity) || 0), 0)
  dirty.value = true
}
function addBatch(line: ReceiptLine) {
  line.batches.push(batch())
  dirty.value = true
}
function deleteBatch(line: ReceiptLine, index: number) {
  if (line.batches.length <= 1) return
  line.batches.splice(index, 1)
  syncQuantity(line)
}
async function load() {
  if (!props.receipt) return
  loading.value = true
  loadError.value = ''
  try {
    receipt.value = await fetchInboundDetail(props.receipt.id, props.scopeOrder?.id)
    if (/^\d+$/.test(receipt.value.id)) {
      // 入库单自己的收货回签 + 所关联采购订单上的收货回签，视为同一组文件。
      const orderIds = receiptOrderIds(receipt.value).filter(id => /^\d+$/.test(id))
      const [files, ...orderGroups] = await Promise.all([
        fetchOrderAttachments('INBOUND_ORDER', Number(receipt.value.id)),
        ...orderIds.map(id => fetchOrderAttachments('PURCHASE_ORDER', Number(id))),
      ])
      receipt.value.photos = [
        ...files.filter(file => file.category === 'INBOUND_RECEIPT').map(attachment),
        ...orderGroups
          .flat()
          .filter(file => file.category === 'RECEIPT_SIGN')
          .map(file => ({ ...attachment(file), type: 'INBOUND_RECEIPT' })),
      ]
    }
  } catch (error) {
    loadError.value = (error as Error).message
  } finally {
    loading.value = false
  }
}
function attachment(file: AttachmentVO): PurchaseReceipt['photos'][number] {
  return {
    id: file.id,
    name: file.fileName,
    url: file.downloadUrl,
    fileSize: file.fileSize,
    type: file.category,
  }
}
function onConfirmChange(value: boolean | string | number) {
  const confirmed = value === true || value === 'true'
  if (confirmed && !receipt.value.receivedAt)
    receipt.value.receivedAt = new Date().toLocaleString('sv-SE').slice(0, 19)
  if (!confirmed) receipt.value.receivedAt = ''
  dirty.value = true
}
async function uploadPhotos(files: File[]) {
  for (const file of files) {
    if (file.size > 10 * 1024 * 1024) {
      ElMessage.warning('单个收货回签不超过 10MB')
      continue
    }
    if (/^\d+$/.test(receipt.value.id)) {
      try {
        const uploaded = await uploadOrderAttachment(
          'INBOUND_ORDER',
          Number(receipt.value.id),
          'INBOUND_RECEIPT',
          file,
        )
        receipt.value.photos.push(attachment(uploaded))
        ElMessage.success('收货回签已上传')
      } catch (error) {
        ElMessage.error((error as Error).message)
      }
      continue
    }
    const url = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(String(reader.result))
      reader.onerror = reject
      reader.readAsDataURL(file)
    }).catch(() => '')
    if (!url) continue
    const preview = { name: file.name, url, type: 'INBOUND_RECEIPT' }
    receipt.value.photos.push(preview)
    pendingPhotos.value.push({ file, preview })
    dirty.value = true
  }
}
async function removePhoto(photo: PurchaseReceipt['photos'][number]) {
  const index = receipt.value.photos.indexOf(photo)
  if (index >= 0) receipt.value.photos.splice(index, 1)
  const pendingIndex = pendingPhotos.value.findIndex(item => item.preview === photo)
  if (pendingIndex >= 0) {
    pendingPhotos.value.splice(pendingIndex, 1)
    dirty.value = true
    return
  }
  if (photo.id) {
    try {
      await deleteOrderAttachment(photo.id)
    } catch (error) {
      ElMessage.error((error as Error).message)
    }
  }
}
onMounted(() => {
  if (view) void load()
})
async function close() {
  if (!view && dirty.value && !saving.value) {
    try {
      await ElMessageBox.confirm('关闭后将丢失本次填写内容，是否继续？', '关闭入库单', {
        type: 'warning',
        confirmButtonText: '关闭',
        cancelButtonText: '继续填写',
      })
    } catch {
      return
    }
  }
  if (!saving.value) emit('close')
}
async function save() {
  if (orders.value.length < 1) return ElMessage.warning('请选择采购单')
  saving.value = true
  try {
    receipt.value.lines.forEach(syncQuantity)
    if (!receipt.value.confirmed) throw Error('请勾选入库确认')
    if (!receipt.value.receivedAt) throw Error('请填写入库时间')
    if (!pendingPhotos.value.length && !receipt.value.photos.length)
      throw Error('请上传收货回签')
    const created = await store.saveReceipt(receipt.value)
    if (pendingPhotos.value.length && created?.id) {
      let failed = 0
      for (const item of pendingPhotos.value) {
        try {
          await uploadOrderAttachment('INBOUND_ORDER', created.id, 'INBOUND_RECEIPT', item.file)
        } catch {
          failed++
        }
      }
      if (failed) ElMessage.warning(`${failed} 个收货回签文件上传失败，可在入库详情中重新上传`)
    }
    ElMessage.success('入库成功')
    emit('saved')
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="true"
    :title="title"
    width="min(1000px,96vw)"
    top="3vh"
    :fullscreen="full"
    class="purchase-dialog inbound-receipt-dialog"
    :close-on-click-modal="false"
    :before-close="close"
  >
    <template #header
      ><div class="inbound-dialog-heading">
        <strong>{{ title }}</strong
        ><span v-if="view">{{ receipt.code }}</span
        ><el-tag v-if="view" size="small" type="success" effect="light">已入库</el-tag
        ><el-button
          text
          :icon="FullScreen"
          title="切换全屏"
          aria-label="切换全屏"
          @click="full = !full"
        /></div
    ></template>
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false"
      ><el-button link type="primary" @click="load">重试</el-button></el-alert
    >
    <div v-else v-loading="loading" class="inbound-detail-body">
      <h3 class="purchase-section">入库信息</h3>
      <div class="inbound-info-card">
        <div class="inbound-summary">
          <div>
            <span>供应商</span
            ><strong :title="supplier">{{ orders[0]?.supplierShortName || supplier }}</strong>
          </div>
          <div>
            <span>{{ scopeOrder ? '本单入库数量' : '入库总数' }}</span
            ><strong class="inbound-total">{{ quantity.toLocaleString('zh-CN') }}</strong>
          </div>
        </div>
        <div class="inbound-order-bar">
          <div class="inbound-order-tags">
            <span class="inbound-muted">采购单</span
            ><el-tag
              v-for="order in orders"
              :key="order.id"
              :closable="!view"
              @close="removeOrder(order.id)"
              >{{ order.code }}</el-tag
            >
          </div>
          <div v-if="!view" class="inbound-order-picker">
            <el-select
              v-model="selectedId"
              filterable
              clearable
              placeholder="选择采购单"
              aria-label="追加采购单"
              ><el-option
                v-for="order in availableOrders"
                :key="order.id"
                :value="order.id"
                :label="`${order.code} · ${order.supplierShortName || purchaseSupplierName(order)}`" /></el-select
            ><el-button :icon="Plus" :disabled="!selectedId" @click="addOrder()">{{ orders.length ? '追加' : '添加' }}</el-button>
          </div>
        </div>
        <div v-if="!view && candidateLines.length" class="inbound-line-picker">
          <span class="inbound-muted">本次入库产品</span
          ><el-select
            v-model="selectedLines"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            :max-collapse-tags="2"
            placeholder="选择本次收到的产品"
            aria-label="本次入库产品"
            ><el-option
              v-for="entry in candidateLines"
              :key="entry.line.id"
              :value="entry.line.id"
              :label="`${entry.line.model} · ${entry.order.code}`"
        /></el-select>
        </div>
      </div>
      <h3 class="purchase-section">入库产品明细 <small>{{ products.length }} 个产品</small></h3>
      <div class="inbound-products">
        <el-empty v-if="!products.length && !loading" description="暂无入库产品" />
        <section v-for="product in products" :key="product.productId" class="inbound-product">
          <header class="inbound-product-heading">
            <div class="inbound-product-name">
              <strong>{{ product.model || '—' }}</strong
              ><span>{{ product.brand || '—' }}</span
              ><span :title="product.packaging">{{ product.packaging || '—' }}</span>
            </div>
            <div class="inbound-product-tools">
              <span
                >入库
                <b>{{
                  product.lines
                    .reduce(
                      (sum, line) =>
                        sum + line.batches.reduce((s, b) => s + (Number(b.quantity) || 0), 0),
                      0,
                    )
                    .toLocaleString('zh-CN')
                }}</b></span
              ><el-dropdown
                v-if="!view && product.lines.length > 1"
                trigger="click"
                @command="(id: string) => addProductBatch(product.lines, id)"
                ><el-button link type="primary" :icon="Plus">添加批次</el-button
                ><template #dropdown
                  ><el-dropdown-menu
                    ><el-dropdown-item
                      v-for="line in product.lines"
                      :key="line.lineId"
                      :command="line.lineId"
                      >{{ orderFor(line)?.code }}</el-dropdown-item
                    ></el-dropdown-menu
                  ></template
                ></el-dropdown
              ><el-button
                v-else-if="!view"
                link
                type="primary"
                :icon="Plus"
                @click="addBatch(product.lines[0]!)"
                >添加批次</el-button
              >
            </div>
          </header>
          <el-table
            :data="rows(product.lines)"
            class="inbound-allocation-table"
            size="small"
            border
            :row-key="rowKey"
            :span-method="batchSpanMethod(product.lines)"
          >
            <el-table-column min-width="140"
              ><template #header
                >Date Code<span v-if="!view" class="inbound-required"> *</span></template
              ><template #default="{ row }"
                ><span v-if="view">{{ row.batch.dateCode || '—' }}</span
                ><el-input
                  v-else
                  v-model="row.batch.dateCode"
                  placeholder="原厂日期码"
                  maxlength="32"
                  required
                  aria-required="true"
                  aria-label="Date Code"
                  @input="dirty = true" /></template
            ></el-table-column>
            <el-table-column min-width="185"
              ><template #header
                >Lot Code<span v-if="!view" class="inbound-required"> *</span></template
              ><template #default="{ row }"
                ><span v-if="view">{{ row.batch.lotCode || '—' }}</span
                ><el-input
                  v-else
                  v-model="row.batch.lotCode"
                  placeholder="原厂批次号"
                  maxlength="64"
                  required
                  aria-required="true"
                  aria-label="Lot Code"
                  @input="dirty = true" /></template
            ></el-table-column>
            <el-table-column label="分配采购单" min-width="177"
              ><template #default="{ row }"
                ><div class="inbound-allocation-order">
                  {{ orderFor(row.line)?.code || '—'
                  }}<small v-if="!view"
                    >待交 {{ remaining(row.line).toLocaleString('zh-CN') }}</small
                  >
                </div></template
              ></el-table-column
            >
            <el-table-column width="170" align="right"
              ><template #header
                >入库数量<span v-if="!view" class="inbound-required"> *</span></template
              ><template #default="{ row }"
                ><strong v-if="view">{{ row.batch.quantity.toLocaleString('zh-CN') }}</strong
                ><el-input-number
                  v-else
                  v-model="row.batch.quantity"
                  :min="1"
                  :max="Math.min(100000000, remaining(row.line))"
                  :precision="0"
                  :step="100"
                  controls-position="right"
                  placeholder="必填"
                  aria-required="true"
                  aria-label="入库数量"
                  @change="syncQuantity(row.line)" /></template
            ></el-table-column>
            <el-table-column v-if="!view" width="45" align="center"
              ><template #default="{ row }"
                ><el-tooltip content="删除批次"
                  ><el-button
                    text
                    :icon="Delete"
                    :disabled="row.line.batches.length <= 1"
                    aria-label="删除批次"
                    @click="deleteBatch(row.line, row.index)" /></el-tooltip></template
            ></el-table-column>
          </el-table>
        </section>
      </div>
      <section class="inbound-confirm-section">
        <h3 class="purchase-section">入库确认</h3>
        <div class="inbound-confirm-row">
          <div class="inbound-confirm-field">
            <span>入库确认</span>
            <el-checkbox
              v-model="receipt.confirmed"
              :disabled="view"
              aria-label="入库确认"
              @change="onConfirmChange"
            />
          </div>
          <div class="inbound-confirm-field inbound-confirm-time">
            <span>入库时间</span>
            <el-date-picker
              v-model="receipt.receivedAt"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              :disabled="view || !receipt.confirmed"
              placeholder="勾选后自动填写当前时间"
              aria-label="入库时间"
            />
          </div>
        </div>
        <p class="purchase-tip">收货回签为必传，支持 PDF、图片等格式。</p>
        <AttachmentTiles
          :attachments="receipt.photos"
          :types="inboundReceiptTypes"
          :uploadable="!saving"
          :removable="!saving"
          @upload="uploadPhotos"
          @remove="removePhoto"
        />
      </section>
    </div>
    <template #footer
      ><el-button :disabled="saving" @click="close">{{ view ? '关闭' : '取消' }}</el-button
      ><el-button
        v-if="!view"
        type="primary"
        :loading="saving"
        :disabled="orders.length < 2 || !products.length || !!loadError"
        @click="save"
        >确认入库</el-button
      ></template
    >
  </el-dialog>
</template>

<style scoped>
.inbound-dialog-heading {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-right: 25px;
}
.inbound-dialog-heading > strong {
  font-size: 17px;
}
.inbound-dialog-heading > span {
  font-size: 13px;
  color: #7a8291;
}
.inbound-dialog-heading > .el-button {
  margin-left: auto;
}
.inbound-detail-body {
  padding-bottom: 4px;
}
.inbound-detail-body .purchase-section:not(:first-child) {
  margin-top: 20px;
}
.inbound-detail-body .purchase-section small {
  margin-left: 8px;
  color: #8a94a6;
  font-size: 12px;
  font-weight: 400;
}
.inbound-info-card {
  padding: 12px 16px 14px;
  border: 1px solid #e3e7ed;
  border-radius: 6px;
  background: #f8fafc;
}
.inbound-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 16px 36px;
  padding: 0 0 10px;
  border-bottom: 1px solid #e8ebef;
}
.inbound-summary > div {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 32px;
}
.inbound-confirm-section {
  padding-top: 2px;
}
.inbound-confirm-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px 40px;
  padding: 2px 0 8px;
}
.inbound-confirm-field {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: #7a8291;
}
.inbound-confirm-field > span {
  white-space: nowrap;
}
.inbound-confirm-time :deep(.el-date-editor) {
  width: 210px;
}
.inbound-confirm-field :deep(.el-checkbox.is-disabled.is-checked .el-checkbox__inner) {
  background-color: #86c5ba;
  border-color: #86c5ba;
}
.inbound-confirm-field :deep(.el-checkbox.is-disabled.is-checked .el-checkbox__inner::after) {
  border-color: #fff;
}
.inbound-summary > div > span,
.inbound-muted {
  color: #7b8491;
  font-size: 13px;
}
.inbound-summary > div > span { white-space: nowrap; flex-shrink: 0; }
.inbound-summary strong {
  font-size: 14px;
}
.inbound-summary .inbound-total {
  color: #268a70;
  font-size: 18px;
  font-variant-numeric: tabular-nums;
}
.inbound-summary :deep(.el-date-editor) {
  width: 190px;
}
.inbound-order-bar {
  padding: 10px 0 0;
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
}
.inbound-order-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.inbound-order-picker {
  display: flex;
  gap: 6px;
}
.inbound-order-picker .el-select {
  width: 225px;
}
.inbound-line-picker {
  display: flex;
  gap: 12px;
  align-items: center;
  margin: 10px 0 0;
}
.inbound-line-picker .el-select {
  flex: 1;
  min-width: 0;
}
.inbound-line-picker > span {
  flex-shrink: 0;
}
.inbound-products {
  border: 1px solid #e3e7ed;
  border-radius: 4px;
  overflow: hidden;
}
.inbound-product + .inbound-product {
  border-top: 1px solid #d9dfe7;
}
.inbound-product-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  min-height: 45px;
  padding: 7px 12px;
  background: #f4f7fa;
  box-sizing: border-box;
}
.inbound-product-name {
  display: flex;
  align-items: center;
  gap: 18px;
  min-width: 0;
  flex-wrap: wrap;
}
.inbound-product-name strong {
  font-size: 14px;
  color: #303d4e;
}
.inbound-product-name span {
  font-size: 12px;
  color: #6d7887;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.inbound-product-tools {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
  font-size: 12px;
  color: #7b8491;
}
.inbound-product-tools b {
  color: #303d4e;
  margin-left: 4px;
}
.inbound-required {
  color: #e35b59;
}
.inbound-allocation-table :deep(th.el-table__cell) {
  background: #fff;
  color: #7b8491;
  font-weight: 500;
  height: 32px;
}
.inbound-allocation-table :deep(td.el-table__cell) {
  height: 48px;
}
.inbound-allocation-table :deep(.el-input-number) {
  width: 144px;
}
.inbound-allocation-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}
.inbound-allocation-order {
  display: flex;
  flex-direction: column;
  line-height: 20px;
  font-variant-numeric: tabular-nums;
}
.inbound-allocation-order small {
  color: #8b95a4;
  font-size: 11px;
}
.inbound-photo-actions {
  display: flex;
  gap: 12px;
}
.inbound-photo-actions label {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #4e6a8b;
  cursor: pointer;
  font-size: 13px;
}
.inbound-photo-actions input {
  display: none;
}
.inbound-photos {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  flex-wrap: wrap;
}
.inbound-photos > div {
  display: flex;
  align-items: flex-start;
}
.inbound-photos .el-image {
  width: 64px;
  height: 64px;
  border-radius: 4px;
}
@media (max-width: 650px) {
  .inbound-product-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .inbound-product-name {
    gap: 6px 14px;
  }
  .inbound-product-tools {
    width: 100%;
    justify-content: space-between;
  }
  .inbound-order-picker {
    width: 100%;
  }
  .inbound-order-picker .el-select {
    flex: 1;
    min-width: 0;
  }
  .inbound-summary {
    gap: 8px 20px;
  }
}
</style>
