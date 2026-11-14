<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { FullScreen, Refresh, Sort } from '@element-plus/icons-vue'
import { useSalesStore } from '../../stores/sales'
import { useAuthStore } from '../../stores/auth'
import {
  today,
  orderCustomerName,
  remainingQuantity,
  defaultShippingInfo,
  type SalesShipment,
} from '../../domain/sales'
import ShipmentShippingForm from './ShipmentShippingForm.vue'
import DeliveryNoteActions from './DeliveryNoteActions.vue'
import { fetchCustomerOptions, type CustomerOption } from '../../services/customerApi'
import { allocateFifo } from '../../domain/shipmentBatches'
import {
  pendingOutboundProducts,
  matchingOutboundOrders,
  outboundAllocationColumns,
  buildMultiOutboundLines,
  type MultiOutboundProduct,
  type MultiOutboundBatch,
} from '../../domain/multiOutbound'
import { fetchProductCostBatches } from '../../services/productApi'
import { formatUnitCost } from '../../services/productApiData'

const emit = defineEmits<{ close: []; saved: [] }>()
const store = useSalesStore(),
  auth = useAuthStore()
const full = ref(false),
  saving = ref(false),
  dirty = ref(false),
  savedShipment = ref<SalesShipment | null>(null),
  successOpen = ref(false)
const productIds = ref<string[]>([]),
  orderIds = ref<string[]>([])
const drafts = ref<Record<string, MultiOutboundProduct>>({})
const loading = ref<Record<string, boolean>>({}),
  errors = ref<Record<string, string>>({})
const shippedAt = ref(`${today()} 09:00:00`)
const shippingInfo = ref(defaultShippingInfo())
const customers = ref<CustomerOption[]>([])
const customerError = ref('')
const shippingCustomerId = ref('')
let disposed = false
onBeforeUnmount(() => {
  disposed = true
})
const pendingLines = computed(() =>
  store.state.orders.flatMap((order) =>
    order.lines.map((line) => ({
      order,
      line,
      pending: remainingQuantity(store.state, order.id, line.id),
    })),
  ),
)
const catalog = computed(() => pendingOutboundProducts(pendingLines.value))
const products = computed(() =>
  productIds.value.map((id) => drafts.value[id]).filter((p): p is MultiOutboundProduct => !!p),
)
const candidates = computed(() => matchingOutboundOrders(pendingLines.value, productIds.value))
const selectedOrders = computed(() =>
  candidates.value.filter((e) => orderIds.value.includes(e.order.id)),
)
const customerId = computed(() => selectedOrders.value[0]?.order.customerId)
const customerName = computed(
  () =>
    selectedOrders.value[0]?.order.customerShortName ||
    orderCustomerName(selectedOrders.value[0]?.order),
)
const purchaseNumbers = computed(() =>
  [...new Set(selectedOrders.value.map((e) => e.order.purchaseNumber).filter(Boolean))].join(' / '),
)
function fillShippingDefaults() {
  const id = customerId.value || ''
  if (shippingCustomerId.value !== id) {
    shippingInfo.value = defaultShippingInfo(selectedOrders.value[0]?.order)
    shippingInfo.value.sender = auth.user?.name || ''
    shippingCustomerId.value = id
  }
  const customer = customers.value.find((c) => c.id === id)
  if (customer) {
    shippingInfo.value.address ||= customer.address
    shippingInfo.value.recipient ||= customer.contact
    shippingInfo.value.phone ||= customer.phone
  }
}
watch(customerId, fillShippingDefaults)
async function loadCustomers() {
  customerError.value = ''
  try {
    const rows = await fetchCustomerOptions()
    if (!disposed) {
      customers.value = rows
      fillShippingDefaults()
    }
  } catch (error) {
    if (!disposed) customerError.value = (error as Error).message
  }
}
onMounted(loadCustomers)
const busy = computed(() => products.value.some((p) => loading.value[p.productId]))
const hasErrors = computed(() => products.value.some((p) => !!errors.value[p.productId]))
watch(productIds, (ids) => {
  for (const id of ids) {
    const source = catalog.value.find((p) => p.productId === id)
    if (source && !drafts.value[id]) {
      drafts.value[id] = { ...source, batches: [] }
      void loadStock(drafts.value[id]!)
    }
  }
  orderIds.value = orderIds.value.filter((id) => candidates.value.some((e) => e.order.id === id))
  dirty.value = true
})
async function loadStock(product: MultiOutboundProduct) {
  if (loading.value[product.productId]) return
  loading.value[product.productId] = true
  errors.value[product.productId] = ''
  try {
    const stock = await fetchProductCostBatches(product.productId)
    if (disposed) return
    product.batches = stock.map((batch) => {
      const previous = product.batches.find((b) => b.inventoryId === batch.inventoryId)
      return {
        ...batch,
        selected: previous?.selected ?? false,
        outboundQuantity: previous?.outboundQuantity,
        quantities: previous?.quantities ?? {},
      }
    })
  } catch (error) {
    if (!disposed) errors.value[product.productId] = (error as Error).message
  } finally {
    if (!disposed) loading.value[product.productId] = false
  }
}
function columns(product: MultiOutboundProduct) {
  return outboundAllocationColumns(pendingLines.value, product.productId, orderIds.value)
}
const selectedBatches = (product: MultiOutboundProduct) => product.batches.filter((b) => b.selected)
const orderQuantity = (product: MultiOutboundProduct, lineId: string) =>
  selectedBatches(product).reduce((sum, batch) => sum + (Number(batch.quantities[lineId]) || 0), 0)
const allocated = (product: MultiOutboundProduct, batch: MultiOutboundBatch) =>
  columns(product).reduce((sum, entry) => sum + (Number(batch.quantities[entry.line.id]) || 0), 0)
const quantity = computed(() =>
  products.value.reduce(
    (sum, p) => sum + selectedBatches(p).reduce((s, b) => s + (Number(b.outboundQuantity) || 0), 0),
    0,
  ),
)
const allocationTotal = computed(() =>
  products.value.reduce(
    (sum, p) => sum + selectedBatches(p).reduce((s, b) => s + allocated(p, b), 0),
    0,
  ),
)
function allocationLimit(
  product: MultiOutboundProduct,
  batch: MultiOutboundBatch,
  lineId: string,
  pending: number,
) {
  const current = Number(batch.quantities[lineId]) || 0
  return Math.max(
    0,
    Math.min(
      (Number(batch.outboundQuantity) || 0) - allocated(product, batch) + current,
      pending - orderQuantity(product, lineId) + current,
    ),
  )
}
function toggleBatch(product: MultiOutboundProduct, batch: MultiOutboundBatch, checked: boolean) {
  batch.selected = checked
  batch.outboundQuantity = checked
    ? Math.min(
        batch.quantity,
        Math.max(
          0,
          product.pending -
            selectedBatches(product)
              .filter((b) => b !== batch)
              .reduce((sum, b) => sum + (Number(b.outboundQuantity) || 0), 0),
        ),
      )
    : undefined
  batch.quantities = {}
  dirty.value = true
}
function fifo(product: MultiOutboundProduct) {
  const pending = orderIds.value.length
    ? columns(product).reduce((sum, e) => sum + e.pending, 0)
    : product.pending
  const picked = allocateFifo(product.batches, pending)
  for (const batch of product.batches) {
    const match = picked.find((b) => b.inventoryId === batch.inventoryId)
    batch.selected = !!match
    batch.outboundQuantity = match?.quantity
    batch.quantities = {}
  }
  dirty.value = true
}
function distribute(product: MultiOutboundProduct) {
  const entries = columns(product)
    .slice()
    .sort(
      (a, b) =>
        a.order.signed.localeCompare(b.order.signed) || a.order.code.localeCompare(b.order.code),
    )
  const remaining = new Map(entries.map((e) => [e.line.id, e.pending]))
  for (const batch of selectedBatches(product)) {
    let left = Number(batch.outboundQuantity) || 0
    batch.quantities = {}
    for (const entry of entries) {
      const count = Math.min(left, remaining.get(entry.line.id) || 0)
      batch.quantities[entry.line.id] = count
      remaining.set(entry.line.id, (remaining.get(entry.line.id) || 0) - count)
      left -= count
    }
  }
  dirty.value = true
}
function toggleOrder(id: string, selected: boolean) {
  orderIds.value = selected
    ? [...orderIds.value, id]
    : orderIds.value.filter((value) => value !== id)
  dirty.value = true
}
function createShipment(): SalesShipment {
  const lines = buildMultiOutboundLines(products.value, pendingLines.value, orderIds.value)
  const first = selectedOrders.value[0]!.order
  return {
    id: draftId,
    requestKey: draftId,
    code: '',
    orderId: first.id,
    orderIds: [...orderIds.value],
    warehouse: first.warehouse,
    lines,
    tag: '出库',
    confirmed: true,
    shippedAt: shippedAt.value,
    keeper: auth.user?.name || '出库员',
    department: auth.user?.department || '',
    photos: [],
    customerConfirmed: false,
    customerReceivedAt: '',
    shipping: { ...shippingInfo.value, purchaseNumber: purchaseNumbers.value },
  }
}
const draftId = crypto.randomUUID()
/** 提交成功后先展示出库单号和打印入口，关闭后再通知父级刷新。 */
function finishSaved() {
  successOpen.value = false
  savedShipment.value = null
  emit('saved')
}
async function save() {
  if (saving.value || busy.value || hasErrors.value) return
  try {
    const shipment = createShipment()
    saving.value = true
    const saved = await store.saveShipment(shipment)
    dirty.value = false
    if (saved.code) {
      savedShipment.value = saved
      successOpen.value = true
      return
    }
    ElMessage.success('出库成功')
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
      await ElMessageBox.confirm('关闭后将丢失本次填写内容，是否继续？', '关闭出库单', {
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
</script>

<template>
  <el-dialog
    :model-value="true"
    title="新增出库"
    width="min(1000px, 96vw)"
    top="3vh"
    :fullscreen="full"
    class="purchase-dialog multi-outbound-dialog"
    :close-on-click-modal="false"
    :before-close="close"
  >
    <template #header
      ><div class="outbound-heading">
        <strong>新增出库</strong
        ><el-button
          text
          :icon="FullScreen"
          title="切换全屏"
          aria-label="切换全屏"
          @click="full = !full"
        /></div
    ></template>
    <h3 class="purchase-section outbound-section">出库信息</h3>
    <el-form label-position="top" class="outbound-info" :disabled="saving">
      <el-form-item label="出库类型" required
        ><el-select model-value="SALE" aria-label="出库类型">
          <el-option value="SALE" label="销售出库" /></el-select
      ></el-form-item>
      <el-form-item label="出库日期" required
        ><el-date-picker
          v-model="shippedAt"
          type="datetime"
          format="YYYY-MM-DD HH:mm"
          value-format="YYYY-MM-DD HH:mm:ss"
          :clearable="false"
          @change="dirty = true"
      /></el-form-item>
    </el-form>
    <h3 class="purchase-section outbound-section">
      出库产品与批次 <small>已选 {{ products.length }} 个产品</small>
    </h3>
    <el-form label-position="top" :disabled="saving"
      ><el-form-item label="本次出库产品" required>
        <el-select
          v-model="productIds"
          multiple
          filterable
          clearable
          placeholder="选择出库产品"
          aria-label="选择出库产品"
        >
          <el-option
            v-for="p in catalog"
            :key="p.productId"
            :value="p.productId"
            :label="[p.model, p.brand, p.packaging].filter(Boolean).join(' · ')"
          >
            <span>{{ p.model }}</span
            ><span class="outbound-option-meta"
              >{{ p.brand }} · {{ p.packaging }} · 待发
              {{ p.pending.toLocaleString('zh-CN') }}</span
            >
          </el-option>
        </el-select>
      </el-form-item></el-form
    >
    <el-empty v-if="!products.length" description="请选择出库产品" :image-size="52" />
    <div v-else class="outbound-products">
      <section v-for="product in products" :key="product.productId" class="outbound-product">
        <header class="outbound-product-heading">
          <div>
            <strong>{{ product.model }}</strong
            ><span>{{ product.brand }}</span>
            <span :title="product.packaging">{{ product.packaging }}</span>
          </div>
          <div>
            <el-button
              link
              type="primary"
              :icon="Sort"
              :disabled="
                saving ||
                loading[product.productId] ||
                !!errors[product.productId] ||
                !product.batches.length
              "
              @click="fifo(product)"
              >按 FIFO 选择</el-button
            ><el-tooltip content="刷新可用库存"
              ><el-button
                text
                :icon="Refresh"
                aria-label="刷新可用库存"
                :disabled="saving || loading[product.productId]"
                @click="loadStock(product)"
            /></el-tooltip>
          </div>
        </header>
        <el-alert
          v-if="errors[product.productId]"
          :title="errors[product.productId]"
          type="error"
          :closable="false"
        >
          <el-button link type="primary" @click="loadStock(product)">重试</el-button></el-alert
        >
        <el-table
          v-else
          v-loading="loading[product.productId]"
          :data="product.batches"
          row-key="inventoryId"
          size="small"
          class="outbound-stock-table"
          :empty-text="loading[product.productId] ? '加载中' : '暂无可用库存'"
          max-height="270"
        >
          <el-table-column width="40" align="center"
            ><template #default="{ row }"
              ><el-checkbox
                :model-value="row.selected"
                :disabled="saving || row.quantity <= 0"
                :aria-label="'选择批次 ' + row.batchNo"
                @change="toggleBatch(product, row, !!$event)" /></template
          ></el-table-column>
          <el-table-column
            prop="batchNo"
            label="库存批次号"
            min-width="190"
            show-overflow-tooltip
          />
          <el-table-column label="Date Code" min-width="90" show-overflow-tooltip
            ><template #default="{ row }">{{ row.dateCode || '—' }}</template></el-table-column
          >
          <el-table-column label="Lot Code" min-width="100" show-overflow-tooltip
            ><template #default="{ row }">{{ row.lotCode || '—' }}</template></el-table-column
          >
          <el-table-column label="含税成本" width="85" align="right"
            ><template #default="{ row }">{{
              formatUnitCost(row.unitCost)
            }}</template></el-table-column
          >
          <el-table-column prop="quantity" label="可用库存" width="85" align="right" />
          <el-table-column width="145" align="right"
            ><template #header>出库数量<span class="outbound-required"> *</span></template>
            <template #default="{ row }"
              ><el-input-number
                v-model="row.outboundQuantity"
                :min="1"
                :max="Math.min(row.quantity, 100000000)"
                :step="100"
                :precision="0"
                controls-position="right"
                placeholder="出库数量"
                :disabled="saving || !row.selected"
                :aria-label="row.batchNo + ' 出库数量'"
                @change="dirty = true" /></template
          ></el-table-column>
        </el-table>
      </section>
    </div>
    <h3 class="purchase-section outbound-section">
      销售单与数量分配
      <small
        >已选 {{ orderIds.length }} / {{ candidates.length }} 张{{
          customerName ? ' · ' + customerName : ''
        }}</small
      >
    </h3>
    <el-table
      :data="candidates"
      row-key="order.id"
      size="small"
      class="outbound-orders"
      max-height="265"
      :empty-text="products.length ? '没有匹配的待发货销售单' : '请选择出库产品'"
    >
      <el-table-column width="40" align="center"
        ><template #default="{ row }"
          ><el-tooltip
            content="同一出库单只能选择同一客户"
            :disabled="!customerId || row.order.customerId === customerId"
            ><el-checkbox
              :model-value="orderIds.includes(row.order.id)"
              :disabled="saving || (!!customerId && row.order.customerId !== customerId)"
              :aria-label="'选择销售单 ' + row.order.code"
              @change="toggleOrder(row.order.id, !!$event)" /></el-tooltip></template
      ></el-table-column>
      <el-table-column label="销售单号" min-width="150"
        ><template #default="{ row }">{{ row.order.code }}</template></el-table-column
      >
      <el-table-column label="客户" min-width="100"
        ><template #default="{ row }"
          ><span :title="orderCustomerName(row.order)">{{
            row.order.customerShortName || orderCustomerName(row.order)
          }}</span></template
        ></el-table-column
      >
      <el-table-column label="订单日期" width="110"
        ><template #default="{ row }">{{ row.order.signed }}</template></el-table-column
      >
      <el-table-column label="匹配产品 / 待发数量" min-width="225"
        ><template #default="{ row }"
          ><div class="outbound-matches">
            <span v-for="entry in row.matches" :key="entry.line.id"
              >{{ entry.line.model }} <b>{{ entry.pending.toLocaleString('zh-CN') }}</b></span
            >
          </div></template
        ></el-table-column
      >
    </el-table>
    <el-empty v-if="!selectedOrders.length" description="请选择销售单" :image-size="52" />
    <div v-else class="outbound-products outbound-allocations">
      <section v-for="product in products" :key="product.productId" class="outbound-product">
        <header class="outbound-product-heading">
          <div>
            <strong>{{ product.model }}</strong
            ><span>{{ product.brand }}</span
            ><span>{{ product.packaging }}</span>
          </div>
          <el-button
            link
            type="primary"
            :disabled="saving || !columns(product).length || !selectedBatches(product).length"
            @click="distribute(product)"
            >自动分配</el-button
          >
        </header>
        <el-table
          v-if="columns(product).length"
          :data="selectedBatches(product)"
          row-key="inventoryId"
          size="small"
          empty-text="请选择本次出库的库存批次"
          class="outbound-allocation-table"
        >
          <el-table-column
            prop="batchNo"
            label="库存批次号"
            min-width="190"
            show-overflow-tooltip
          />
          <el-table-column label="出库数量" width="95" align="right"
            ><template #default="{ row }">{{
              row.outboundQuantity || 0
            }}</template></el-table-column
          >
          <el-table-column
            v-for="entry in columns(product)"
            :key="entry.line.id"
            min-width="185"
            align="right"
          >
            <template #header
              ><div class="outbound-order-heading">
                <span>{{ entry.order.code }}</span>
                <small :class="{ 'is-over': orderQuantity(product, entry.line.id) > entry.pending }"
                  >已分配 {{ orderQuantity(product, entry.line.id).toLocaleString('zh-CN') }} / 待发
                  {{ entry.pending.toLocaleString('zh-CN') }}</small
                >
              </div></template
            >
            <template #default="{ row }"
              ><el-input-number
                v-model="row.quantities[entry.line.id]"
                :min="0"
                :max="allocationLimit(product, row, entry.line.id, entry.pending)"
                :step="100"
                :precision="0"
                controls-position="right"
                placeholder="0"
                :disabled="saving || !row.outboundQuantity"
                :aria-label="`${row.batchNo} ${entry.order.code} 分配数量`"
                @change="dirty = true"
            /></template>
          </el-table-column>
          <el-table-column label="未分配" width="95" align="right"
            ><template #default="{ row }"
              ><b :class="{ 'is-over': allocated(product, row) > (row.outboundQuantity || 0) }">{{
                (row.outboundQuantity || 0) - allocated(product, row)
              }}</b></template
            ></el-table-column
          >
        </el-table>
        <div v-else class="outbound-unmatched">该产品尚未选择可出库的销售单</div>
      </section>
    </div>
    <h3 class="purchase-section outbound-section">发货信息</h3>
    <el-alert v-if="customerError" :title="customerError" type="error" :closable="false">
      <el-button link type="primary" @click="loadCustomers">重试</el-button>
    </el-alert>
    <ShipmentShippingForm
      v-model="shippingInfo"
      required
      :disabled="saving"
      @change="dirty = true"
    />
    <template #footer
      ><span class="outbound-totals"
        >本次出库 <b>{{ quantity.toLocaleString('zh-CN') }}</b> · 已分配
        <b>{{ allocationTotal.toLocaleString('zh-CN') }}</b></span
      >
      <el-button :disabled="saving" @click="close">取消</el-button
      ><el-button
        type="primary"
        :loading="saving"
        :disabled="
          busy ||
          hasErrors ||
          !products.length ||
          !orderIds.length ||
          quantity <= 0 ||
          allocationTotal !== quantity
        "
        @click="save"
        >确认出库</el-button
      >
    </template>
  </el-dialog>
  <el-dialog
    v-model="successOpen"
    title="出库成功"
    width="min(560px,94vw)"
    append-to-body
    :close-on-click-modal="false"
    class="purchase-dialog"
    @closed="finishSaved"
  >
    <div class="shipment-success">
      <p>
        出库单 <strong>{{ savedShipment?.code }}</strong> 已生成，可以打印或下载发货单。
      </p>
      <DeliveryNoteActions
        v-if="savedShipment"
        :order="selectedOrders[0]?.order"
        :orders="selectedOrders.map((e) => e.order)"
        :shipment="savedShipment"
      />
    </div>
    <template #footer
      ><el-button type="primary" @click="successOpen = false">完成</el-button></template
    >
  </el-dialog>
</template>

<style scoped>
.outbound-heading,
.outbound-product-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.outbound-heading {
  padding-right: 24px;
}
.outbound-section {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.outbound-section small {
  font-size: 12px;
  font-weight: 400;
  color: #818b98;
}
.outbound-info {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
}
.outbound-info :deep(.el-date-editor),
:deep(.el-select) {
  width: 100%;
}
.outbound-option-meta {
  color: #8a95a3;
  font-size: 12px;
  margin-left: 18px;
}
.outbound-products {
  border: 1px solid #dfe5eb;
  border-radius: 4px;
  overflow: hidden;
}
.outbound-product + .outbound-product {
  border-top: 1px solid #dfe5eb;
}
.outbound-product-heading {
  min-height: 42px;
  padding: 6px 12px;
  background: #f3f7fa;
}
.outbound-product-heading > div {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 14px;
  min-width: 0;
}
.outbound-product-heading strong {
  font-size: 13px;
  color: #334155;
  overflow-wrap: anywhere;
}
.outbound-product-heading span {
  font-size: 12px;
  color: #7b8795;
  overflow-wrap: anywhere;
}
.outbound-products :deep(.el-input-number) {
  width: 100%;
  max-width: 146px;
}
.outbound-products :deep(.el-table__inner-wrapper::before) {
  display: none;
}
.outbound-products :deep(td.el-table__cell) {
  height: 44px;
}
.outbound-required,
.is-over {
  color: #d65c58;
}
.outbound-orders {
  border: 1px solid #dfe5eb;
  border-radius: 4px;
}
.outbound-matches {
  display: flex;
  flex-wrap: wrap;
  gap: 5px 16px;
  font-size: 12px;
}
.outbound-matches b {
  color: #42816c;
  margin-left: 6px;
}
.outbound-order-heading {
  display: flex;
  flex-direction: column;
  line-height: 20px;
}
.outbound-order-heading small {
  font-size: 11px;
  font-weight: 400;
}
.outbound-allocations {
  margin-top: 16px;
}
.outbound-unmatched {
  padding: 14px 12px;
  color: #a46c38;
  font-size: 12px;
}
.outbound-totals {
  float: left;
  line-height: 32px;
  color: #818b98;
  font-size: 12px;
}
.outbound-totals b {
  color: #268a70;
}
@media (max-width: 650px) {
  .outbound-info {
    grid-template-columns: 1fr;
    gap: 0;
  }
  .outbound-product-heading {
    align-items: flex-start;
    flex-wrap: wrap;
  }
  .outbound-option-meta {
    display: none;
  }
  .outbound-totals {
    float: none;
    display: block;
    text-align: left;
  }
}
</style>
