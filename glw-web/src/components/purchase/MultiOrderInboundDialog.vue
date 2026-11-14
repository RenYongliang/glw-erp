<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, FullScreen } from '@element-plus/icons-vue'
import { usePurchaseStore } from '../../stores/purchase'
import { useAuthStore } from '../../stores/auth'
import AttachmentTiles from '../common/AttachmentTiles.vue'
import { uploadOrderAttachment } from '../../services/attachmentApi'
import type { AttachmentTypeDef } from '../../domain/attachments'
import {
  remainingQuantity,
  purchaseSupplierName,
  type PurchaseReceipt,
} from '../../domain/purchase'
import {
  pendingInboundProducts,
  matchingInboundOrders,
  inboundAllocationColumns,
  buildMultiInboundLines,
  type MultiInboundProduct,
  type MultiInboundBatch,
} from '../../domain/multiInbound'

const emit = defineEmits<{ close: []; saved: [] }>()
const store = usePurchaseStore(),
  auth = useAuthStore()
const full = ref(false),
  saving = ref(false),
  dirty = ref(false)
type InboundType = 'PURCHASE'
const inboundTypes = [{ value: 'PURCHASE', label: '采购入库' }] as const
const inboundType = ref<InboundType>('PURCHASE')
const isPurchaseInbound = computed(() => inboundType.value === 'PURCHASE')
const productIds = ref<string[]>([]),
  orderIds = ref<string[]>([])
const drafts = ref<Record<string, MultiInboundProduct>>({})
const receivedAt = ref('')
const confirmed = ref(false)
const requestKey = crypto.randomUUID()
const photos = ref<PurchaseReceipt['photos']>([])
const pendingPhotos = ref<{ file: File; preview: PurchaseReceipt['photos'][number] }[]>([])
const inboundReceiptTypes: AttachmentTypeDef[] = [
  { key: 'INBOUND_RECEIPT', label: '收货回签', slots: 'unlimited' },
]
const pendingLines = computed(() =>
  store.state.orders.flatMap((order) =>
    order.lines.map((line) => ({
      order,
      line,
      pending: remainingQuantity(store.state, order.id, line.id),
    })),
  ),
)
const catalog = computed(() => pendingInboundProducts(pendingLines.value))
const products = computed(() =>
  productIds.value.map((id) => drafts.value[id]).filter((p): p is MultiInboundProduct => !!p),
)
const candidates = computed(() => matchingInboundOrders(pendingLines.value, productIds.value))
const selectedOrders = computed(() =>
  candidates.value.filter((item) => orderIds.value.includes(item.order.id)),
)
const supplierId = computed(() => selectedOrders.value[0]?.order.supplierId)
const supplier = computed(
  () =>
    selectedOrders.value[0]?.order.supplierShortName ||
    purchaseSupplierName(selectedOrders.value[0]?.order) ||
    '—',
)
const newBatch = (): MultiInboundBatch => ({
  id: crypto.randomUUID(),
  dateCode: '',
  lotCode: '',
  quantity: undefined,
  quantities: {},
})
watch(productIds, (ids) => {
  for (const id of ids) {
    const source = catalog.value.find((product) => product.productId === id)
    if (source && !drafts.value[id]) drafts.value[id] = { ...source, batches: [newBatch()] }
  }
  dirty.value = true
})
watch(candidates, (items) => {
  orderIds.value = orderIds.value.filter((id) => items.some((item) => item.order.id === id))
})
watch(inboundType, () => {
  productIds.value = []
  orderIds.value = []
  drafts.value = {}
  photos.value = []
  dirty.value = true
})
function columns(product: MultiInboundProduct) {
  return inboundAllocationColumns(pendingLines.value, product.productId, orderIds.value)
}
function productLabel(product: MultiInboundProduct) {
  return [product.model, product.brand, product.packaging].filter(Boolean).join(' · ')
}
function allocation(batch: MultiInboundBatch, product: MultiInboundProduct) {
  return columns(product).reduce(
    (sum, entry) => sum + (Number(batch.quantities[entry.line.id]) || 0),
    0,
  )
}
function orderQuantity(product: MultiInboundProduct, lineId: string) {
  return product.batches.reduce((sum, batch) => sum + (Number(batch.quantities[lineId]) || 0), 0)
}
const quantity = computed(() =>
  products.value.reduce(
    (sum, product) =>
      sum + product.batches.reduce((s, batch) => s + (Number(batch.quantity) || 0), 0),
    0,
  ),
)
const allocatedQuantity = computed(() =>
  products.value.reduce(
    (sum, product) => sum + product.batches.reduce((s, batch) => s + allocation(batch, product), 0),
    0,
  ),
)
function allocationLimit(
  batch: MultiInboundBatch,
  product: MultiInboundProduct,
  lineId: string,
  pending: number,
) {
  const current = Number(batch.quantities[lineId]) || 0
  return Math.max(
    0,
    Math.min(
      (Number(batch.quantity) || 0) - allocation(batch, product) + current,
      pending - orderQuantity(product, lineId) + current,
      100000000,
    ),
  )
}
function toggleOrder(id: string, selected: boolean) {
  orderIds.value = selected
    ? [...orderIds.value, id]
    : orderIds.value.filter((value) => value !== id)
  dirty.value = true
}
function addBatch(product: MultiInboundProduct) {
  product.batches.push(newBatch())
  dirty.value = true
}
function removeBatch(product: MultiInboundProduct, index: number) {
  if (product.batches.length > 1) {
    product.batches.splice(index, 1)
    dirty.value = true
  }
}
function summary(product: MultiInboundProduct) {
  return [
    '分配合计',
    '',
    product.batches
      .reduce((sum, batch) => sum + (Number(batch.quantity) || 0), 0)
      .toLocaleString('zh-CN'),
    ...columns(product).map(
      (entry) =>
        `${orderQuantity(product, entry.line.id).toLocaleString('zh-CN')} / ${entry.pending.toLocaleString('zh-CN')}`,
    ),
    product.batches
      .reduce((sum, batch) => sum + allocation(batch, product), 0)
      .toLocaleString('zh-CN'),
  ]
}
async function save() {
  if (saving.value) return
  if (!isPurchaseInbound.value) return ElMessage.warning('该入库类型暂未开放')
  try {
    if (!confirmed.value) return ElMessage.warning('请勾选入库确认')
    if (!receivedAt.value) return ElMessage.warning('请填写入库时间')
    if (!photos.value.length) return ElMessage.warning('请上传收货回签')
    const lines = buildMultiInboundLines(products.value, pendingLines.value, orderIds.value)
    const first = selectedOrders.value[0]!.order
    const receipt: PurchaseReceipt = {
      id: crypto.randomUUID(),
      requestKey,
      code: '',
      orderId: first.id,
      orderIds: [...orderIds.value],
      warehouse: first.warehouse,
      lines,
      quality: '合格',
      qualityNote: '',
      inspector: '',
      inspectionDepartment: '',
      inspectedAt: '',
      confirmed: confirmed.value,
      receivedAt: receivedAt.value,
      keeper: auth.user?.name || '入库员',
      department: auth.user?.department || '',
      photos: photos.value,
    }
    saving.value = true
    const created = await store.saveReceipt(receipt)
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
    dirty.value = false
    ElMessage.success('入库成功')
    emit('saved')
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    saving.value = false
  }
}
async function close() {
  if (saving.value) return
  if (dirty.value) {
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
  emit('close')
}
function onConfirmChange(value: boolean | string | number) {
  const checked = value === true || value === 'true'
  if (checked && !receivedAt.value)
    receivedAt.value = new Date().toLocaleString('sv-SE').slice(0, 19)
  if (!checked) receivedAt.value = ''
  dirty.value = true
}
async function removePhoto(photo: PurchaseReceipt['photos'][number]) {
  const index = photos.value.indexOf(photo)
  if (index >= 0) photos.value.splice(index, 1)
  const pendingIndex = pendingPhotos.value.findIndex(item => item.preview === photo)
  if (pendingIndex >= 0) pendingPhotos.value.splice(pendingIndex, 1)
  dirty.value = true
}
async function selectPhotos(files: File[]) {
  for (const file of files) {
    if (file.size > 10 * 1024 * 1024) {
      ElMessage.warning('单个收货回签不超过 10MB')
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
    photos.value.push(preview)
    pendingPhotos.value.push({ file, preview })
    dirty.value = true
  }
}
</script>

<template>
  <el-dialog
    :model-value="true"
    title="新增入库"
    width="820px"
    top="3vh"
    :fullscreen="full"
    class="purchase-dialog multi-inbound-dialog"
    :close-on-click-modal="false"
    :before-close="close"
  >
    <template #header
      ><div class="multi-heading">
        <strong>新增入库</strong
        ><el-button
          text
          :icon="FullScreen"
          title="切换全屏"
          aria-label="切换全屏"
          @click="full = !full"
        /></div
    ></template>
    <h3 class='purchase-section multi-section-heading'>入库信息</h3>
    <el-form label-position='top' :disabled='saving' class='multi-inbound-info'>
      <el-form-item label='入库类型' required>
        <el-select v-model='inboundType' aria-label='选择入库类型'>
          <el-option
            v-for='item in inboundTypes'
            :key='item.value'
            :label='item.label'
            :value='item.value'
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template v-if='isPurchaseInbound'>
      <h3 class='purchase-section multi-section-heading'>
        入库产品与批次 <small>已选 {{ products.length }} 个产品</small>
      </h3>
      <el-form label-position='top' :disabled='saving' class='multi-product-picker'>
        <el-form-item label='本次入库产品' required>
          <el-select
            v-model='productIds'
            multiple
            filterable
            clearable
            placeholder='选择入库产品'
            aria-label='选择入库产品'
          >
            <el-option
              v-for='product in catalog'
              :key='product.productId'
              :value='product.productId'
              :label='productLabel(product)'
            >
              <span class='multi-option-model'>{{ product.model }}</span>
              <span class='multi-option-meta'>{{ product.brand }} · {{ product.packaging }}</span>
              <span class='multi-option-pending'>待交 {{ product.pending.toLocaleString('zh-CN') }}</span>
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>
    <el-empty v-if="!products.length" description="请选择入库产品" :image-size="52" />
    <div v-else class="multi-products">
      <section v-for="product in products" :key="product.productId" class="multi-product">
        <header class="multi-product-heading">
          <div>
            <strong>{{ product.model }}</strong
            ><span>{{ product.brand }}</span
            ><span :title="product.packaging">{{ product.packaging }}</span>
          </div>
          <el-button
            link
            type="primary"
            :icon="Plus"
            :disabled="saving"
            aria-label="添加产品批次"
            @click="addBatch(product)"
            >添加批次</el-button
          >
        </header>
        <el-table :data="product.batches" row-key="id" size="small" class="multi-batch-table">
          <el-table-column type="index" label="#" width="45" />
          <el-table-column min-width="160"
            ><template #header>Date Code<span class="multi-required"> *</span></template
            ><template #default="{ row }"
              ><el-input
                v-model="row.dateCode"
                maxlength="32"
                required
                aria-required="true"
                aria-label="Date Code"
                placeholder="原厂日期码"
                :disabled="saving"
                @input="dirty = true" /></template
          ></el-table-column>
          <el-table-column min-width="205"
            ><template #header>Lot Code<span class="multi-required"> *</span></template
            ><template #default="{ row }"
              ><el-input
                v-model="row.lotCode"
                maxlength="64"
                required
                aria-required="true"
                aria-label="Lot Code"
                placeholder="原厂批次号"
                :disabled="saving"
                @input="dirty = true" /></template
          ></el-table-column>
          <el-table-column width="180" align="right"
            ><template #header>入库数量<span class="multi-required"> *</span></template
            ><template #default="{ row }"
              ><el-input-number
                v-model="row.quantity"
                :min="1"
                :max="100000000"
                :step="100"
                :precision="0"
                controls-position="right"
                placeholder="入库数量"
                aria-label="批次入库数量"
                :disabled="saving"
                @change="dirty = true" /></template
          ></el-table-column>
          <el-table-column label="已分配数量" width="125" align="right"
            ><template #default="{ row }">{{
              allocation(row, product).toLocaleString('zh-CN')
            }}</template></el-table-column
          >
          <el-table-column width="48" align="center"
            ><template #default="{ $index }"
              ><el-tooltip :content="product.batches.length <= 1 ? '至少保留一个批次' : '删除批次'"
                ><el-button
                  text
                  :icon="Delete"
                  aria-label="删除产品批次"
                  :disabled="saving || product.batches.length <= 1"
                  @click="removeBatch(product, $index)" /></el-tooltip></template
          ></el-table-column>
        </el-table>
      </section>
    </div>
    <h3 class="purchase-section multi-section-heading">
      采购单与数量分配
      <small
        >已选 {{ orderIds.length }} / {{ candidates.length }} 张{{ supplierId ? ' · ' + supplier : '' }}</small
      >
    </h3>
    <el-table
      :data="candidates"
      row-key="order.id"
      size="small"
      class="multi-order-table"
      :empty-text="products.length ? '没有匹配的待入库采购单' : '请选择入库产品'"
    >
      <el-table-column width="44" align="center"
        ><template #default="{ row }"
          ><el-tooltip
            :disabled="!supplierId || row.order.supplierId === supplierId"
            content="同一入库单只能选择同一供应商"
            ><el-checkbox
              :model-value="orderIds.includes(row.order.id)"
              :disabled="saving || (!!supplierId && row.order.supplierId !== supplierId)"
              :aria-label="'选择采购单 ' + row.order.code"
              @change="toggleOrder(row.order.id, !!$event)" /></el-tooltip></template
      ></el-table-column>
      <el-table-column label="采购单号" min-width="145"
        ><template #default="{ row }">{{ row.order.code }}</template></el-table-column
      >
      <el-table-column label="供应商" min-width="100"
        ><template #default="{ row }"
          ><span :title="purchaseSupplierName(row.order)">{{
            row.order.supplierShortName || purchaseSupplierName(row.order)
          }}</span></template
        ></el-table-column
      >
      <el-table-column label="采购日期" width="115"
        ><template #default="{ row }">{{ row.order.signed }}</template></el-table-column
      >
      <el-table-column label="匹配产品 / 待交数量" min-width="225"
        ><template #default="{ row }"
          ><div class="multi-matched-products">
            <span v-for="entry in row.matches" :key="entry.line.id"
              >{{ entry.line.model }} <b>{{ entry.pending.toLocaleString('zh-CN') }}</b></span
            >
          </div></template
        ></el-table-column
      >
    </el-table>
    <el-empty v-if="!selectedOrders.length" description="请选择采购单" :image-size="52" />
    <div v-else class="multi-products multi-allocation-products">
      <section v-for="product in products" :key="product.productId" class="multi-product">
        <header class="multi-product-heading">
          <div>
            <strong>{{ product.model }}</strong
            ><span>{{ product.brand }}</span
            ><span>{{ product.packaging }}</span>
          </div>
        </header>
        <el-table
          v-if="columns(product).length"
          :data="product.batches"
          row-key="id"
          size="small"
          class="multi-allocation-table"
          show-summary
          :summary-method="() => summary(product)"
        >
          <el-table-column label="Date Code" min-width="115"
            ><template #default="{ row }">{{ row.dateCode || '—' }}</template></el-table-column
          >
          <el-table-column label="Lot Code" min-width="145"
            ><template #default="{ row }">{{ row.lotCode || '—' }}</template></el-table-column
          >
          <el-table-column label="入库数量" width="100" align="right"
            ><template #default="{ row }">{{
              (Number(row.quantity) || 0).toLocaleString('zh-CN')
            }}</template></el-table-column
          >
          <el-table-column
            v-for="entry in columns(product)"
            :key="entry.line.id"
            min-width="175"
            align="right"
            ><template #header
              ><div class="multi-allocation-order">
                <span>{{ entry.order.code }}</span
                ><small
                  :class="{ 'is-over': orderQuantity(product, entry.line.id) > entry.pending }"
                  >已分配 {{ orderQuantity(product, entry.line.id).toLocaleString('zh-CN') }} / 待交
                  {{ entry.pending.toLocaleString('zh-CN') }}</small
                >
              </div></template
            ><template #default="{ row }"
              ><el-input-number
                v-model="row.quantities[entry.line.id]"
                :min="0"
                :max="allocationLimit(row, product, entry.line.id, entry.pending)"
                :step="100"
                :precision="0"
                controls-position="right"
                placeholder="0"
                :disabled="saving || !row.quantity"
                :aria-label="`${product.model} ${entry.order.code} 入库数量`"
                @change="dirty = true" /></template
          ></el-table-column>
          <el-table-column label="已分配" width="100" align="right"
            ><template #default="{ row }"
              ><b :class="{ 'is-over': allocation(row, product) > (Number(row.quantity) || 0) }">{{
                allocation(row, product).toLocaleString('zh-CN')
              }}</b></template
            ></el-table-column
          >
        </el-table>
        <div v-else class="multi-unmatched">该产品尚未选择可入库的采购单</div>
      </section>
    </div>
    </template>
    <el-empty v-else description="该入库类型暂未开放" :image-size="52" />
    <h3 class="purchase-section multi-section-heading">入库确认</h3>
    <div class="multi-inbound-confirm">
      <div class="multi-inbound-confirm-field">
        <span>入库确认</span>
        <el-checkbox
          v-model="confirmed"
          :disabled="saving"
          aria-label="入库确认"
          @change="onConfirmChange"
        />
      </div>
      <div class="multi-inbound-confirm-field multi-inbound-confirm-time">
        <span>入库时间</span>
        <el-date-picker
          v-model="receivedAt"
          type="datetime"
          format="YYYY-MM-DD HH:mm"
          value-format="YYYY-MM-DD HH:mm:ss"
          :clearable="false"
          :disabled="saving || !confirmed"
          placeholder="勾选后自动填写当前时间"
          aria-label="入库时间"
        />
      </div>
    </div>
    <p class="purchase-tip">收货回签为必传，支持 PDF、图片等格式。</p>
    <AttachmentTiles
      :attachments="photos"
      :types="inboundReceiptTypes"
      :uploadable="!saving"
      :removable="!saving"
      @upload="selectPhotos"
      @remove="removePhoto"
    />
    <template #footer
      ><span class="multi-footer-total"
        >{{ products.length }} 个产品 · {{ orderIds.length }} 张采购单 ·
        <b>{{ quantity.toLocaleString('zh-CN') }}</b> 件</span
      ><el-button :disabled="saving" @click="close">取消</el-button
      ><el-button
        type="primary"
        :disabled="orderIds.length < 1 || !products.length"
        :loading="saving"
        @click="save"
        >确认入库</el-button
      ></template
    >
  </el-dialog>
</template>

<style scoped>
.multi-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-right: 25px;
}
.multi-heading strong {
  font-size: 17px;
}
.multi-inbound-info {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 16px;
}
.multi-inbound-info .el-form-item {
  margin-bottom: 10px;
}
.multi-inbound-info :deep(.el-date-editor) {
  width: 100%;
}
.multi-section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.multi-section-heading small {
  color: #8b95a3;
  font-size: 12px;
  font-weight: 400;
}
.multi-product-picker .el-form-item {
  margin-bottom: 12px;
}
.multi-product-picker .el-select {
  width: 100%;
}
.multi-option-model {
  display: inline-block;
  min-width: 110px;
  margin-right: 12px;
}
.multi-option-meta {
  color: #84909e;
  font-size: 12px;
}
.multi-option-pending {
  margin-left: 16px;
  color: #638274;
  font-size: 12px;
}
.multi-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px 30px;
  padding: 4px 0 14px;
  border-bottom: 1px solid #e7ebef;
  font-size: 13px;
  color: #7e8895;
}
.multi-meta label {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}
.multi-meta b {
  color: #344152;
  margin-left: 8px;
  font-weight: 600;
}
.multi-meta :deep(.el-date-editor) {
  flex: 0 0 210px;
  width: 210px;
}
.multi-meta .multi-total {
  color: #268a70;
  font-size: 17px;
}
.multi-section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  color: #344152;
  margin: 20px 0 10px;
}
.multi-section-title small {
  font-size: 12px;
  color: #8b95a3;
  font-weight: 400;
}
.multi-products {
  border: 1px solid #e0e6ed;
  border-radius: 4px;
  overflow: hidden;
}
.multi-product + .multi-product {
  border-top: 1px solid #dce2e9;
}
.multi-product-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 7px 12px;
  min-height: 43px;
  box-sizing: border-box;
  background: #f4f7fa;
}
.multi-product-heading > div {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 16px;
  min-width: 0;
}
.multi-product-heading strong {
  font-size: 14px;
  color: #344152;
}
.multi-product-heading span {
  font-size: 12px;
  color: #7b8695;
}
.multi-product-heading .el-button {
  flex-shrink: 0;
}
.multi-required,
.is-over {
  color: #dd5b58;
}
.multi-products :deep(th.el-table__cell),
.multi-order-table :deep(th.el-table__cell) {
  background: #fff;
  color: #7b8795;
  font-weight: 500;
}
.multi-products :deep(td.el-table__cell) {
  height: 45px;
}
.multi-products :deep(.el-table__inner-wrapper::before) {
  display: none;
}
.multi-order-table {
  border: 1px solid #e0e6ed;
  border-radius: 4px;
}
.multi-order-table :deep(.el-checkbox) {
  height: 28px;
}
.multi-matched-products {
  display: flex;
  gap: 6px 18px;
  flex-wrap: wrap;
  font-size: 12px;
}
.multi-matched-products b {
  color: #638274;
  margin-left: 6px;
}
.multi-allocation-order {
  display: flex;
  flex-direction: column;
  line-height: 20px;
}
.multi-allocation-order small {
  font-size: 11px;
  font-weight: 400;
}
.multi-products :deep(.el-input-number) {
  width: 146px;
}
.multi-unmatched {
  padding: 14px 12px;
  color: #b08150;
  font-size: 12px;
}
.multi-photo-actions {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #52759a;
}
.multi-photo-actions label {
  display: flex;
  align-items: center;
  gap: 5px;
  cursor: pointer;
}
.multi-photo-actions input {
  display: none;
}
.multi-photos {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.multi-photos > div {
  display: flex;
  align-items: flex-start;
}
.multi-photos .el-image {
  width: 64px;
  height: 64px;
  border-radius: 4px;
}
.multi-inbound-confirm {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px 40px;
  padding: 2px 0 8px;
}
.multi-inbound-confirm-field {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: #7a8291;
}
.multi-inbound-confirm-field > span {
  white-space: nowrap;
}
.multi-inbound-confirm-time :deep(.el-date-editor) {
  width: 210px;
}
.multi-footer-total {
  float: left;
  line-height: 32px;
  color: #8a95a3;
  font-size: 12px;
}
.multi-footer-total b {
  color: #268a70;
}
@media (max-width: 650px) {
  .multi-inbound-info {
    grid-template-columns: 1fr;
  }
  .multi-meta {
    gap: 12px;
  }
  .multi-product-heading {
    align-items: flex-start;
  }
  .multi-footer-total {
    float: none;
    display: block;
    text-align: left;
  }
  .multi-option-meta,
  .multi-option-pending {
    display: none;
  }
}
</style>
