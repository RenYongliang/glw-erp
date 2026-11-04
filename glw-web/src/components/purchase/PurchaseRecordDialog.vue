<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Document, FullScreen } from '@element-plus/icons-vue'
import PurchaseContractActions from './PurchaseContractActions.vue'
import { usePurchaseStore } from '../../stores/purchase'
import { useAuthStore } from '../../stores/auth'
import { fetchSuppliers } from '../../services/supplierApi'
import { fetchSalesProducts } from '../../services/productApi'
import {
  PURCHASE_ATTACHMENT_TYPES,
  attachmentTypeLabel,
  attachmentTypeOf,
  fileExtension,
  keepsUploadSlot,
  renumberAttachments,
  standardAttachmentName,
  type AttachmentTypeDef,
} from '../../domain/attachments'
import {
  purchaseSupplierName,
  purchaseContact,
  purchasePhone,
  purchaseLineProductText,
  warehouses,
  today,
  money,
  chineseMoney,
  orderTotals,
  receiptTotals,
  receiptMatchesOrder,
  remainingQuantity,
  pendingDeliveryQuantity,
  type Product,
  type PurchaseKind,
  type PurchaseOrder,
  type PurchaseReceipt,
  type Supplier,
  type ReceiptBatch,
  type ReceiptLine,
  type Attachment,
} from '../../domain/purchase'
const props = defineProps<{
  kind: PurchaseKind
  order?: PurchaseOrder
  receipt?: PurchaseReceipt
  relatedOrder?: PurchaseOrder
  readonly?: boolean
}>()
const emit = defineEmits<{ close: []; saved: []; receive: [order: PurchaseOrder] }>()
const store = usePurchaseStore(),
  auth = useAuthStore(),
  view = ref(!!props.readonly),
  full = ref(false),
  tab = ref('订单附件'),
  picker = ref(''),
  pickerQuery = ref(''),
  productSelection = ref<string[]>([]),
  dirty = ref(false),
  saving = ref(false)
const clone = <T,>(value: T): T => JSON.parse(JSON.stringify(value))
const order = ref<PurchaseOrder>(
  props.order
    ? clone(props.order)
    : {
        id: crypto.randomUUID(),
        code: '',
        name: '',
        supplierId: '',
        signed: today(),
        delivery: '',
        owner: '张睿',
        department: '采购部',
        warehouse: warehouses[0]!,
        address: '',
        discount: 0,
        lines: [],
        arrivals: [],
        payments: [],
        attachments: [],
        term: '月结30天',
        orderStatus: '执行中',
        receiptStatus: '未收货',
        reason: '',
        approver: '',
        approvedAt: '',
      },
)
order.value.contact = purchaseContact(order.value)
order.value.phone = purchasePhone(order.value)
// TEMP: 多附件预览数据，仅用于演示，稍后移除。
if (order.value.code === 'GL20260921001') {
  const demo: Array<[string, string]> = [
    ['采购合同-GL20260921001.pdf', 'PURCHASE_CONTRACT'],
    ['供应商盖章合同-GL20260921001.pdf', 'SUPPLIER_COUNTERSIGNED'],
    ['入库回签件-2026-09.pdf', 'RECEIPT_SIGN'],
    ['对账单-2026-09.pdf', 'OTHER'],
  ]
  order.value.attachments = demo.map(([name, type]) => ({
    name,
    originalName: name,
    type,
    url: 'data:text/plain;base64,ZGVtbw==',
  }))
}
order.value.taxIncluded ??= true
for (const line of order.value.lines) {
  line.delivery ??= order.value.delivery
}
const receipt = ref<PurchaseReceipt>(
  props.receipt
    ? clone(props.receipt)
    : {
        id: crypto.randomUUID(),
        code: '',
        orderId: '',
        warehouse: '',
        lines: [],
        quality: '合格',
        qualityNote: '',
        inspector: '',
        inspectionDepartment: '',
        inspectedAt: '',
        confirmed: false,
        receivedAt: '',
        keeper: '陈晨',
        department: '仓储部',
        photos: [],
      },
)
const isOrder = props.kind === 'orders',
  title = isOrder ? '采购订单' : '产品入库'
for (const line of receipt.value.lines) {
  line.batches ??= []
  line.arrived = line.batches.reduce((sum, batch) => sum + Math.max(0, batch.quantity), 0)
}
const linkedOrder = computed(() =>
  isOrder ? order.value : store.state.orders.find((o) => o.id === receipt.value.orderId),
)
// 供应商与产品均来自后端档案；旧订单可能只有名称（未建档），字段为空即可。
const supplierList = ref<Supplier[]>([])
const productList = ref<Product[]>([])
async function loadSupplierList() {
  try {
    supplierList.value = (await fetchSuppliers()).map((row) => ({
      id: String(row.id),
      name: row.name,
      code: row.code || '',
      contact: row.contact || '',
      phone: row.contactPhone || '',
      bank: row.bankName || '',
      account: row.bankAccount || '',
      enabled: row.enabled,
    }))
  } catch (e) {
    ElMessage.error(`供应商列表加载失败：${(e as Error).message}`)
  }
}
async function loadProductList() {
  try {
    productList.value = (await fetchSalesProducts()).map((row) => ({
      id: row.id,
      code: row.code,
      supplierId: row.supplierId,
      name: row.name,
      brand: row.brand,
      model: row.name,
      packaging: row.model,
      unit: row.unit,
      price: row.estimatedCost ?? 0,
      tax: 13,
      stock: row.stock,
    }))
  } catch (e) {
    ElMessage.error(`产品列表加载失败：${(e as Error).message}`)
  }
}
onMounted(() => {
  void loadSupplierList()
  void loadProductList()
})
const supplier = computed(() => supplierList.value.find((s) => s.id === linkedOrder.value?.supplierId))
const taxIncludedOrder = computed(() => order.value.taxIncluded !== false)
/** 切换订单级含税口径：不换算已填单价，只改变价格的口径解释。 */
function setTaxIncluded(value: boolean | string | number) {
  const included = value !== false && value !== "false"
  order.value.taxIncluded = included
  dirty.value = true
}
const totals = computed(() => orderTotals(order.value)),
  received = computed(() => receiptTotals(receipt.value, linkedOrder.value))
const baseFields = computed(() => [
  ['供应商名称', purchaseSupplierName(linkedOrder.value)],
  ['供应商联系人姓名', purchaseContact(linkedOrder.value)],
  ['供应商联系人手机', purchasePhone(linkedOrder.value)],
])
const linkedReceipts = computed(() =>
  store.state.receipts.filter((r) => receiptMatchesOrder(r, order.value.id)),
)
const linkedPayments = computed(() =>
  store.state.payments.filter((r) => r.orderId === order.value.id),
)
const linkedReturns = computed(() =>
  store.state.returns.filter((r) => r.orderId === order.value.id),
)
const locked = computed(
  () =>
    linkedReceipts.value.length > 0 ||
    linkedPayments.value.length > 0 ||
    linkedReturns.value.length > 0,
)
const availableOrders = computed(() =>
  store.state.orders.filter(
    (o) =>
      o.orderStatus === '执行中' &&
      o.lines.some((l) => remainingQuantity(store.state, o.id, l.id, receipt.value.id) > 0) &&
      [o.name, o.code].join().includes(pickerQuery.value),
  ),
)
// 已选供应商时只展示该供应商下的产品；未选供应商时展示全部产品。
const availableProducts = computed(() => {
  const supplierId = linkedOrder.value?.supplierId || ''
  return productList.value.filter(
    (p) =>
      (!supplierId || !p.supplierId || p.supplierId === supplierId) &&
      [p.name, p.code, p.model, p.brand, p.packaging]
        .join()
        .toLowerCase()
        .includes(pickerQuery.value.toLowerCase()),
  )
})
async function chooseSupplier(id: string) {
  if (order.value.supplierId !== id && order.value.lines.length) {
    if (!productList.value.length) await loadProductList()
    // 产品档案里没有的产品（历史数据）无法判断归属，保留不清理。
    const stray = order.value.lines.filter((line) => {
      const product = productList.value.find((p) => p.id === line.productId)
      return !!product?.supplierId && product.supplierId !== id
    })
    if (stray.length) {
      const target = supplierList.value.find((s) => s.id === id)
      try {
        await ElMessageBox.confirm(
          `供应商已切换为“${target?.name || ''}”，将从订单中移除 ${stray.length} 项不属于该供应商的产品，是否继续？`,
          '切换供应商',
          { type: 'warning', confirmButtonText: '切换并移除', cancelButtonText: '取消' },
        )
      } catch {
        return
      }
      order.value.lines = order.value.lines.filter((line) => !stray.includes(line))
    }
  }
  order.value.supplierId = id
  const s = supplierList.value.find((s) => s.id === id)!
  order.value.supplierName = s.name
  order.value.contact = s.contact
  order.value.phone = s.phone
  order.value.name = `${s.name}-第${store.state.orders.filter((o) => o.supplierId === id).length + 1}份采购订单`
  picker.value = ''
  dirty.value = true
}
function useSupplierName() {
  const name = pickerQuery.value.trim()
  if (!name || name.length > 100) return void ElMessage.warning('请填写100字以内的供应商名称')
  const existing = supplierList.value.find(s => s.name === name)
  if (existing) return chooseSupplier(existing.id)
  if (name !== purchaseSupplierName(order.value)) {
    order.value.contact = ''
    order.value.phone = ''
  }
  order.value.supplierId = ''
  order.value.supplierName = name
  order.value.name = `${name}-采购订单`
  picker.value = ''
  dirty.value = true
}
function chooseOrder(o: PurchaseOrder) {
  receipt.value.orderId = o.id
  receipt.value.warehouse = o.warehouse
  receipt.value.lines = o.lines
    .filter((l) => remainingQuantity(store.state, o.id, l.id, receipt.value.id) > 0)
    .map((l) => {
      const remaining = remainingQuantity(store.state, o.id, l.id, receipt.value.id)
      return {
        lineId: l.id,
        arrived: remaining,
        rejected: 0,
        location: '',
        batches: [newBatch(remaining)],
      }
    })
  picker.value = ''
  dirty.value = true
}
if (props.relatedOrder && !props.receipt) chooseOrder(props.relatedOrder)
function addProducts() {
  for (const id of productSelection.value)
    if (!order.value.lines.some((l) => l.productId === id)) {
      const p = productList.value.find((p) => p.id === id)!
      order.value.lines.push({
        id: crypto.randomUUID(),
        productId: id,
        brand: p.brand,
        model: p.model,
        packaging: p.packaging,
        quantity: 1,
        price: p.price,
        tax: p.tax,
        delivery: order.value.delivery || '',
        remark: '',
      })
    }
  productSelection.value = []
  picker.value = ''
  dirty.value = true
}
function orderLine(lineId: string) {
  return linkedOrder.value?.lines.find((l) => l.id === lineId)
}
function newBatch(quantity = 1): ReceiptBatch {
  return {
    id: crypto.randomUUID(),
    dateCode: '',
    lotCode: '',
    quantity,
  }
}
function syncLine(line: ReceiptLine) {
  line.arrived = line.batches.reduce((sum, batch) => sum + Math.max(0, batch.quantity), 0)
}
function batchTotal(line: ReceiptLine) {
  return line.batches.reduce((sum, batch) => sum + Math.max(0, batch.quantity), 0)
}
function unallocatedQuantity(line: ReceiptLine) {
  return remainingQuantity(store.state, receipt.value.orderId, line.lineId, receipt.value.id) - batchTotal(line)
}
function addBatch(line: ReceiptLine) {
  line.batches.push(newBatch())
  syncLine(line)
  dirty.value = true
}
function removeBatch(line: ReceiptLine, index: number) {
  if (line.batches.length <= 1) return
  line.batches.splice(index, 1)
  syncLine(line)
  dirty.value = true
}
function openPicker(name: string) {
  pickerQuery.value = name === 'supplier' && !order.value.supplierId ? purchaseSupplierName(order.value) : ''
  productSelection.value = []
  picker.value = name
}
async function save() {
  if (saving.value) return
  saving.value = true
  try {
    if (isOrder) await store.saveOrder(order.value)
    else {
      receipt.value.confirmed = true
      receipt.value.receivedAt ||= new Date().toLocaleString('sv-SE').slice(0, 16)
      receipt.value.keeper = auth.user?.name || receipt.value.keeper
      receipt.value.department = auth.user?.department || receipt.value.department
      await store.saveReceipt(receipt.value)
    }
    dirty.value = false
    ElMessage.success(
      isOrder ? '采购订单已保存' : receipt.value.confirmed ? '入库已确认' : '已保存到待入库',
    )
    emit('saved')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}
async function close(done?: () => void) {
  if (!view.value && dirty.value) {
    try {
      await ElMessageBox.confirm('尚有未保存的修改，确认关闭？', '放弃修改', {
        confirmButtonText: '放弃',
        cancelButtonText: '继续编辑',
        type: 'warning',
      })
    } catch {
      return
    }
  }
  done?.()
  emit('close')
}
const attachmentTypes = PURCHASE_ATTACHMENT_TYPES
function filesOfAttachmentType(type: string) {
  return order.value.attachments.filter((file) => attachmentTypeOf(file) === type)
}
/**
 * 固定类型只有一格：没传时显示加号，传了就不再占位；
 * 可能有多份的类型（回签、发票、其他）始终保留一个待上传格。
 */
function canAddAttachment(slot: AttachmentTypeDef) {
  if (view.value && !order.value.code) return false
  return keepsUploadSlot(slot) || filesOfAttachmentType(slot.key).length === 0
}
/** 按「单据号_类型_供应商_日期_序号」统一重命名某一类型的全部附件。 */
function renameAttachmentGroup(type: string) {
  const fallbackDate = today().replaceAll('-', '')
  renumberAttachments(order.value.attachments, type, (file, index, total) =>
    standardAttachmentName({
      orderCode: order.value.code,
      typeLabel: attachmentTypeLabel(attachmentTypes, type),
      partnerName: purchaseSupplierName(order.value) || '供应商',
      date: file.uploadedAt || fallbackDate,
      index,
      total,
      extension: fileExtension(file.originalName || file.name),
    }),
  )
}
/** 删除附件：编辑态随订单保存，查看态立即写回本地存档。 */
async function removeAttachment(file: Attachment) {
  const index = order.value.attachments.indexOf(file)
  if (index < 0) return
  try {
    await ElMessageBox.confirm(`确认删除附件「${file.name}」？`, '删除附件', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  order.value.attachments.splice(index, 1)
  renameAttachmentGroup(attachmentTypeOf(file))
  dirty.value = true
  if (!view.value) return
  try {
    store.saveAttachments(order.value.id, order.value.attachments)
    ElMessage.success('附件已删除')
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}
/** 查看态可补充附件，直接写入本地存档；编辑态随订单一起保存。 */
async function uploadAttachment(event: Event, type: string) {
  await attach(event, false, type)
  if (!view.value) return
  try {
    store.saveAttachments(order.value.id, order.value.attachments)
    ElMessage.success(`${attachmentTypeLabel(attachmentTypes, type)}已保存`)
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}
async function attach(event: Event, images = false, type = 'OTHER') {
  const input = event.target as HTMLInputElement
  for (const file of Array.from(input.files || [])) {
    if (file.size > 2 * 1024 * 1024) {
      ElMessage.warning('本地预览附件每个不超过 2MB')
      continue
    }
    if (images && !file.type.startsWith('image/')) {
      ElMessage.warning('请选择图片')
      continue
    }
    const url = await new Promise<string>((resolve, reject) => {
      const r = new FileReader()
      r.onload = () => resolve(String(r.result))
      r.onerror = reject
      r.readAsDataURL(file)
    }).catch(() => {
      ElMessage.error('文件读取失败')
      return ''
    })
    if (url) {
      if (images) {
        receipt.value.photos.push({ name: file.name, url })
      } else {
        order.value.attachments.push({
          name: file.name,
          originalName: file.name,
          type,
          uploadedAt: today().replaceAll('-', ''),
          url,
        })
        renameAttachmentGroup(type)
      }
      dirty.value = true
    }
  }
  input.value = ''
}
const quick = ref(false),
  quickText = ref('')
function quickFill() {
  const rows = quickText.value
    .trim()
    .split(/\r?\n/)
    .map((l) => l.split(/[\t,，]/))
  const changes = rows.map((r) => {
    const key = r[0]?.trim()
    return {
      p: key ? productList.value.find((p) => [p.code, p.model, p.name].includes(key)) : undefined,
      q: Number(r[1]),
    }
  })
  if (!changes.length || changes.some((r) => !r.p || !Number.isSafeInteger(r.q) || r.q <= 0))
    return ElMessage.error('每行填写产品型号和正整数数量，以逗号或 Tab 分隔')
  if (
    !isOrder &&
    changes.some(
      (r) => !receipt.value.lines.some((row) => orderLine(row.lineId)?.productId === r.p!.id),
    )
  )
    return ElMessage.error('产品不在本次入库订单中')
  for (const r of changes) {
    if (isOrder) {
      const old = order.value.lines.find((l) => l.productId === r.p!.id)
      if (old) old.quantity = r.q
      else
        order.value.lines.push({
          id: crypto.randomUUID(),
          productId: r.p!.id,
          brand: r.p!.brand,
          model: r.p!.model,
          packaging: r.p!.packaging,
          quantity: r.q,
          price: r.p!.price,
          tax: r.p!.tax,
          delivery: order.value.delivery || '',
          remark: '',
        })
    } else {
      const l = linkedOrder.value?.lines.find((l) => l.productId === r.p!.id),
        row = receipt.value.lines.find((x) => x.lineId === l?.id)
      if (!row) return ElMessage.error('产品不在本次入库订单中')
      row.batches = [newBatch(r.q)]
      syncLine(row)
    }
  }
  dirty.value = true
  quick.value = false
}
</script>

<template>
  <el-dialog
    :model-value="true"
    :title="title"
    :width="isOrder ? '820px' : '1000px'"
    top="3vh"
    :fullscreen="full"
    class="purchase-dialog"
    :class="{ 'purchase-order-editor': isOrder }"
    :close-on-click-modal="false"
    :before-close="close"
    destroy-on-close
  >
    <template #header
      ><div class="purchase-dialog-heading">
        <span
          >{{ title
          }}<el-tag
            v-if="isOrder && order.code"
            size="small"
            :type="
              order.orderStatus === '已完结'
                ? 'success'
                : order.orderStatus === '执行中'
                  ? 'warning'
                  : 'info'
            "
            >{{ order.orderStatus }}</el-tag
          ><el-tag
            v-if="isOrder && order.code"
            size="small"
            type="info"
            style="margin-left: 6px"
            >{{ order.receiptStatus }}</el-tag
          ></span
        ><div class="purchase-dialog-controls"
          ><el-button :icon="FullScreen" text aria-label="切换表单全屏" @click="full = !full" /></div
        ></div
    ></template>
    <div class="purchase-form-body" @change="dirty = true" @input="dirty = true">
      <h3 class="purchase-section">采购订单信息</h3>
      <p v-if="!isOrder && !props.relatedOrder && !props.receipt" class="purchase-tip">
        {{
          isOrder
            ? '使用Tips：采购订单通过标签页，可以一站式查看采购业务流程'
            : '选择采购订单后自动带入供应商和产品明细；确认后计入已入库数量。'
        }}
      </p>
      <el-form v-if="isOrder" label-position="top" class="purchase-form-grid purchase-order-info" :disabled="view">
        <el-form-item label="供应商名称" required><el-button class="purchase-picker-button" @click="openPicker('supplier')">{{ purchaseSupplierName(order) || '选择或填写供应商名称' }}</el-button></el-form-item>
        <el-form-item label="联系人"><el-input v-model="order.contact" aria-label="联系人" maxlength="50" /></el-form-item>
        <el-form-item label="联系人手机"><el-input v-model="order.phone" aria-label="联系人手机" maxlength="30" /></el-form-item>
        <el-form-item label="采购日期" required><el-date-picker v-model="order.signed" value-format="YYYY-MM-DD" aria-label="采购日期" /></el-form-item>
        <el-form-item label="采购单号"><div class="purchase-read">{{ order.code || '保存后自动生成' }}</div></el-form-item>
      </el-form>
      <el-form v-else label-position="top" class="purchase-form-grid purchase-receipt-meta" :disabled="view">
        <el-form-item v-if="!props.relatedOrder && !props.receipt" label="选择采购订单" required
          ><el-button
            class="purchase-picker-button"
            :disabled="!isOrder && !!receipt.code"
            @click="openPicker(isOrder ? 'supplier' : 'order')"
            >{{
              isOrder ? supplier?.name || '选择数据' : linkedOrder?.name || '选择数据'
            }}</el-button
          ></el-form-item
        >
        <template v-if="!isOrder"
          ><el-form-item label="采购订单编号"
            ><div class="purchase-read">{{ linkedOrder?.code || '暂无内容' }}</div></el-form-item
          ></template
        >
        <el-form-item v-for="[label, value] in baseFields.filter(([label, value]) => label === '供应商名称' || !!value)" :key="label" :label="label"
          ><div class="purchase-read">{{ value || '暂无内容' }}</div></el-form-item
        >
        <template v-if="isOrder">
          <el-form-item label="采购订单名称"
            ><el-input v-model="order.name" maxlength="100" aria-label="采购订单名称"
          /></el-form-item>
          <el-form-item label="订单签订日期" required
            ><el-date-picker
              v-model="order.signed"
              value-format="YYYY-MM-DD"
              aria-label="订单签订日期"
          /></el-form-item>
          <el-form-item label="订单交付日期" required
            ><el-date-picker
              v-model="order.delivery"
              value-format="YYYY-MM-DD"
              aria-label="订单交付日期"
          /></el-form-item>
        </template>
        <el-form-item v-if="isOrder || receipt.code" :label="isOrder ? '采购订单编号' : '入库单编号'"
          ><div class="purchase-read">
            {{ (isOrder ? order.code : receipt.code) || '自动生成无需填写' }}
          </div></el-form-item
        >
        <el-form-item v-if="isOrder || linkedOrder?.owner" label="采购负责人"
          ><el-select v-if="isOrder" v-model="order.owner" filterable allow-create
            ><el-option v-for="n in ['张睿', '林晓', '陈晨']" :key="n" :value="n"
          /></el-select>
          <div v-else class="purchase-read">
            {{ linkedOrder?.owner || '暂无内容' }}
          </div></el-form-item
        >

      </el-form>
      <div class="purchase-products-head">
        <h3 class="purchase-section">{{ isOrder ? '采购产品信息' : '入库产品明细' }}</h3>
        <div v-if="isOrder" class="purchase-price-mode">
          <span class="purchase-price-mode-label">单价口径</span>
          <el-radio-group :model-value="taxIncludedOrder" :disabled="view" size="small"
            @update:model-value="setTaxIncluded">
            <el-radio-button :value="true">含税</el-radio-button>
            <el-radio-button :value="false">未税</el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <el-table v-if="isOrder" :data="order.lines" border class="purchase-lines purchase-order-lines" empty-text="点击“添加”选择采购产品">
        <el-table-column type="index" label="序号" width="52" />
        <el-table-column label="规格" min-width="110"><template #default="{row}">{{ purchaseLineProductText(row, 'model') }}</template></el-table-column>
        <el-table-column label="品牌" min-width="80"><template #default="{row}">{{ purchaseLineProductText(row, 'brand') }}</template></el-table-column>
        <el-table-column label="封装" min-width="80"><template #default="{row}">{{ purchaseLineProductText(row, 'packaging') }}</template></el-table-column>
        <el-table-column label="数量" width="125"><template #default="{row}"><el-input-number v-model="row.quantity" :disabled="view" :min="1" :max="100000000" :step="100" :precision="0" controls-position="right" aria-label="数量" /></template></el-table-column>
        <el-table-column label="单位" width="55"><template #default>pcs</template></el-table-column>
        <el-table-column :label="taxIncludedOrder ? '含税单价' : '未税单价'" width="120"><template #default="{row}"><el-input-number v-model="row.price" :disabled="view" :min="0" :max="10000000" :precision="2" controls-position="right" aria-label="单价" /></template></el-table-column>
        <el-table-column label="税率(%)" width="110"><template #default="{row}"><el-input-number v-model="row.tax" :disabled="view" :min="0" :max="100" :precision="0" controls-position="right" aria-label="税率(%)" /></template></el-table-column>
        <el-table-column label="合计" width="105"><template #default="{$index}">{{ money(totals.details[$index]?.total || 0) }}</template></el-table-column>
        <el-table-column label="交付日期" width="150"><template #default="{row}"><el-date-picker v-model="row.delivery" :disabled="view" value-format="YYYY-MM-DD" aria-label="产品交付日期" /></template></el-table-column>
        <el-table-column label="备注" min-width="160"><template #default="{row}"><el-input v-model="row.remark" :disabled="view" maxlength="200" aria-label="产品备注" /></template></el-table-column>
        <el-table-column v-if="!view" fixed="right" label="操作" width="70" align="center" class-name="purchase-order-product-actions">
          <template #default="{ $index }">
            <el-button text type="danger" :icon="Delete" aria-label="删除产品" @click="order.lines.splice($index, 1); dirty = true" />
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="purchase-receipt-products">
        <article v-for="(row, productIndex) in receipt.lines" :key="row.lineId" class="purchase-receipt-product">
          <header class="purchase-receipt-product-heading">
            <div class="purchase-receipt-product-identity">
              <span class="purchase-receipt-product-index">{{ String(productIndex + 1).padStart(2, '0') }}</span>
              <div class="purchase-receipt-product-description">
                <strong class="purchase-receipt-product-model">{{ purchaseLineProductText(orderLine(row.lineId), 'model') || '—' }}</strong>
                <div class="purchase-receipt-product-specs">
                  <span>{{ purchaseLineProductText(orderLine(row.lineId), 'brand') || '—' }}</span>
                  <span>{{ purchaseLineProductText(orderLine(row.lineId), 'packaging') || '—' }}</span>
                </div>
              </div>
            </div>
            <dl class="purchase-receipt-product-quantities">
              <div><dt>采购数量</dt><dd>{{ orderLine(row.lineId)?.quantity }}</dd></div>
              <div><dt>待入库</dt><dd>{{ remainingQuantity(store.state, receipt.orderId, row.lineId, receipt.id) }}</dd></div>
              <div><dt>本次入库</dt><dd class="purchase-receipt-total">{{ batchTotal(row) }}</dd></div>
            </dl>
            <el-button v-if="!view && !props.receipt?.confirmed" class="purchase-product-add-batch" text type="primary" :icon="Plus" :disabled="saving" @click="addBatch(row)">添加批次</el-button>
          </header>
            <div class="purchase-batch-panel">
              <div class="purchase-batch-scroll">
              <table v-if="row.batches.length" class="purchase-batch-table" aria-label="入库批次明细">
                <colgroup><col class="purchase-batch-index-col" /><col class="purchase-batch-date-col" /><col /><col class="purchase-batch-quantity-col" /><col class="purchase-batch-action-col" /></colgroup>
                <thead><tr class="purchase-batch-columns">
                  <th scope="col">#</th><th scope="col"><span class="purchase-batch-required">*</span>Date Code <small>日期码</small></th><th scope="col"><span class="purchase-batch-required">*</span>Lot Code <small>原厂批次号</small></th><th scope="col" class="purchase-batch-number-heading"><span class="purchase-batch-required">*</span>入库数量</th><th scope="col">操作</th>
                </tr></thead>
                <tbody class="purchase-batch-list">
                <tr
                  v-for="(batch, index) in row.batches"
                  :key="batch.id"
                  class="purchase-batch-item"
                >
                  <td class="purchase-batch-index">{{ index + 1 }}</td>
                  <td>
                  <el-input
                    v-model="batch.dateCode"
                    class="purchase-batch-date"
                    :disabled="view || !!props.receipt?.confirmed || saving"
                    maxlength="32"
                    required
                    aria-required="true"
                    aria-label="原厂日期码"
                    placeholder="如 2618"
                  />
                  </td><td>
                  <el-input
                    v-model="batch.lotCode"
                    class="purchase-batch-lot"
                    :disabled="view || !!props.receipt?.confirmed || saving"
                    maxlength="64"
                    required
                    aria-required="true"
                    aria-label="原厂批次号"
                    placeholder="填写原厂批次号"
                  />
                  </td><td>
                    <el-input-number
                      v-model="batch.quantity"
                      class="purchase-batch-quantity"
                      :disabled="view || !!props.receipt?.confirmed || saving"
                      :min="1"
                      :max="remainingQuantity(store.state, receipt.orderId, row.lineId, receipt.id)"
                      :step="100"
                      :precision="0"
                      controls-position="right"
                      required
                      aria-required="true"
                      aria-label="批次本次入库数量"
                      @update:model-value="() => { syncLine(row); dirty = true }"
                    />
                  </td><td class="purchase-batch-actions">
                  <el-tooltip v-if="!view && !props.receipt?.confirmed" :content="row.batches.length <= 1 ? '至少保留一个批次' : '删除批次'" placement="top"><span class="purchase-batch-remove-trigger"><el-button
                    class="purchase-batch-remove"
                    text
                    :disabled="saving || row.batches.length <= 1"
                    :icon="Delete"
                    aria-label="删除批次"
                    @click="removeBatch(row, index)"
                  /></span></el-tooltip>
                  <span v-else>—</span>
                  </td>
                </tr>
                </tbody>
              </table>
              </div>
              <div v-if="!row.batches.length" class="purchase-batch-empty">
                暂无入库批次
              </div>
              <div class="purchase-batch-footer">
                <span>{{ row.batches.length }} 个批次</span>
                <span v-if="!view && !props.receipt?.confirmed" class="purchase-batch-balance" :class="{ 'is-over-limit': unallocatedQuantity(row) < 0 }">{{ unallocatedQuantity(row) < 0 ? `超出待入库 ${-unallocatedQuantity(row)}` : `剩余待入库 ${unallocatedQuantity(row)}` }}</span>
                <span class="purchase-batch-subtotal">本次入库 <strong>{{ batchTotal(row) }}</strong></span>
              </div>
            </div>
        </article>
        <el-empty v-if="!receipt.lines.length" description="暂无待入库产品" :image-size="60" />
      </div>
      <div v-if="isOrder && !view" class="purchase-line-actions">
        <el-button v-if="isOrder" text type="primary" :icon="Plus" @click="openPicker('product')"
          >添加</el-button
        ><el-button
          text
          type="primary"
          @click="
            () => {
              quickText = ''
              quick = true
            }
          "
          >快速填报</el-button
        >
      </div>
      <el-form v-if="isOrder" label-position="top" class="purchase-form-grid" :disabled="view">
        <template v-if="isOrder">
          <el-form-item label="合计数量"><div class="purchase-read">{{ order.lines.reduce((n,l) => n + (l.quantity || 0), 0) }}</div></el-form-item>
          <el-form-item label="采购订单金额/元" class="purchase-amount-cell"
            ><div class="purchase-read purchase-amount-read">
              <span class="purchase-tax-tag" :class="{ 'purchase-tax-tag-net': !taxIncludedOrder }">{{ taxIncludedOrder ? "含税" : "不含税" }}</span>
              <span class="purchase-amount-value">{{ money(totals.total) }}</span>
              <span v-if="taxIncludedOrder" class="purchase-net-hint">其中不含税 {{ money(totals.net) }}</span>
            </div></el-form-item
          >
          <el-form-item label="合计金额大写"><div class="purchase-read">{{ chineseMoney(totals.total) }}</div></el-form-item>
        </template>
</el-form>
      <PurchaseContractActions v-if="isOrder" :order="order" />
      <template v-if="isOrder">
        <el-collapse v-if="order.code" class="purchase-order-business"><el-collapse-item name="business">
        <template #title>
          <span class="purchase-business-title">业务记录</span>
          <span class="purchase-business-counts">入库 {{ linkedReceipts.length }} · 退货 {{ linkedReturns.length }} · 财务 {{ linkedPayments.length }}</span>
        </template>
        <nav class="purchase-detail-tabs" aria-label="订单关联信息">
          <button
            v-for="name in ['订单附件', '订单明细', '财务明细', '采购入库', '采购退货', '采购订单执行跟踪']"
            :key="name"
            :class="{ active: tab === name }"
            @click="tab = name"
          >
            {{ name }}
          </button>
        </nav>
        <template v-if="tab === '订单附件'">
          <p class="purchase-tip">
            上传后统一命名为「单据号_类型_供应商_日期_序号」。本地预览：每个文件不超过 2MB。
          </p>
          <div class="attachment-tile-grid">
            <template v-for="slot in attachmentTypes" :key="slot.key">
              <div
                v-for="(file, i) in filesOfAttachmentType(slot.key)"
                :key="slot.key + '-' + i"
                class="attachment-tile attachment-tile-doc"
                :title="file.name"
              >
                <span class="attachment-tile-type">{{ slot.label }}</span>
                <button
                  v-if="!view || order.code"
                  type="button"
                  class="attachment-tile-remove"
                  aria-label="移除附件"
                  @click.stop="removeAttachment(file)"
                >
                  ×
                </button>
                <a
                  class="attachment-tile-body"
                  :href="file.url"
                  :download="file.name"
                  :aria-label="'下载 ' + file.name"
                >
                  <el-icon class="attachment-tile-icon"><Document /></el-icon>
                  <span class="attachment-tile-name">{{ file.name }}</span>
                </a>
              </div>
              <label
                v-if="canAddAttachment(slot)"
                class="attachment-tile attachment-tile-add"
                :title="'上传' + slot.label"
                :aria-label="slot.label + '上传'"
              >
                <span class="attachment-tile-type">{{ slot.label }}</span>
                <el-icon><Plus /></el-icon>
                <input
                  type="file"
                  multiple
                  @change="uploadAttachment($event, slot.key)"
                />
              </label>
            </template>
          </div>
        </template>
        <template v-else-if="tab === '订单明细'">
          <h3 class="purchase-section">到货计划</h3>
          <el-form label-position="top" :disabled="view"
            ><el-form-item label="到货地址"
              ><el-input
                v-model="order.address"
                placeholder="省 / 市 / 区 / 详细地址" /></el-form-item
          ></el-form>
          <el-table :data="order.arrivals" border
            ><el-table-column label="到货批次" width="125"
              ><template #default="{ row }"
                ><el-select v-model="row.batch" :disabled="view"
                  ><el-option
                    v-for="n in ['整批', '第一批', '第二批', '第三批']"
                    :key="n"
                    :value="n" /></el-select></template></el-table-column
            ><el-table-column label="计划到货日期" width="200"
              ><template #default="{ row }"
                ><el-date-picker
                  v-model="row.date"
                  :disabled="view"
                  value-format="YYYY-MM-DD" /></template></el-table-column
            ><el-table-column label="计划到货内容备注" min-width="180"
              ><template #default="{ row }"
                ><el-input v-model="row.note" :disabled="view" /></template></el-table-column
            ><el-table-column v-if="!view" width="65"
              ><template #default="{ $index }"
                ><el-button
                  :icon="Delete"
                  text
                  aria-label="删除到货计划"
                  @click="
                    () => {
                      order.arrivals.splice($index, 1)
                      dirty = true
                    }
                  " /></template></el-table-column
          ></el-table>
          <el-button
            v-if="!view"
            text
            type="primary"
            :icon="Plus"
            @click="
              () => {
                order.arrivals.push({ batch: '整批', date: order.delivery, note: '' })
                dirty = true
              }
            "
            >添加到货计划</el-button
          >
          <h3 class="purchase-section">财务条款</h3>
          <el-form label-position="top" :disabled="view"
            ><el-form-item label="结算期限"><el-input v-model="order.term" /></el-form-item
          ></el-form>
          <el-table :data="order.payments" border
            ><el-table-column label="付款项" width="130"
              ><template #default="{ row }"
                ><el-select v-model="row.name" :disabled="view"
                  ><el-option
                    v-for="n in ['整批货款', '定金', '尾款']"
                    :key="n"
                    :value="n" /></el-select></template></el-table-column
            ><el-table-column label="付款比例 %" width="140"
              ><template #default="{ row }"
                ><el-input-number
                  v-model="row.ratio"
                  :disabled="view"
                  :min="0"
                  :max="100"
                  controls-position="right" /></template></el-table-column
            ><el-table-column label="付款金额/元" width="140"
              ><template #default="{ row }">{{
                money((totals.total * row.ratio) / 100)
              }}</template></el-table-column
            ><el-table-column label="付款方式" width="145"
              ><template #default="{ row }"
                ><el-select v-model="row.method" :disabled="view"
                  ><el-option
                    v-for="n in ['银行转账', '现金', '承兑汇票']"
                    :key="n"
                    :value="n" /></el-select></template></el-table-column
            ><el-table-column label="计划付款日期" width="190"
              ><template #default="{ row }"
                ><el-date-picker
                  v-model="row.date"
                  :disabled="view"
                  value-format="YYYY-MM-DD" /></template></el-table-column
            ><el-table-column label="备注" width="180"
              ><template #default="{ row }"
                ><el-input v-model="row.note" :disabled="view" /></template></el-table-column
            ><el-table-column v-if="!view" fixed="right" width="65"
              ><template #default="{ $index }"
                ><el-button
                  :icon="Delete"
                  text
                  aria-label="删除付款计划"
                  @click="
                    () => {
                      order.payments.splice($index, 1)
                      dirty = true
                    }
                  " /></template></el-table-column
          ></el-table>
          <el-button
            v-if="!view"
            text
            type="primary"
            :icon="Plus"
            @click="
              () => {
                order.payments.push({
                  name: '整批货款',
                  ratio: 100,
                  method: '银行转账',
                  date: order.delivery,
                  note: '',
                })
                dirty = true
              }
            "
            >添加付款计划</el-button
          >
        </template>
        <template v-else-if="tab === '财务明细'"
          ><el-descriptions :column="2" border
            ><el-descriptions-item label="发票抬头">{{ supplier?.name }}</el-descriptions-item
            ><el-descriptions-item label="税种">增值税专用发票</el-descriptions-item
            ><el-descriptions-item label="银行账户">{{ supplier?.account }}</el-descriptions-item
            ><el-descriptions-item label="开户银行">{{
              supplier?.bank
            }}</el-descriptions-item></el-descriptions
          ><el-table :data="linkedPayments" border
            ><el-table-column prop="date" label="付款日期" /><el-table-column
              prop="method"
              label="付款方式" /><el-table-column
              prop="amount"
              label="付款金额/元" /><el-table-column prop="note" label="备注" /></el-table
        ></template>
        <template v-else-if="tab === '采购入库'"
          ><el-table :data="linkedReceipts" border
            ><el-table-column prop="code" label="采购入库单编号" /><el-table-column
              prop="warehouse"
              label="仓库"
            /><el-table-column label="入库产品总数"
              ><template #default="{ row }">{{
                receiptTotals(row, order).quantity
              }}</template></el-table-column
            ><el-table-column label="状态"
              ><template #default="{ row }">{{
                row.confirmed ? '已入库' : '待入库'
              }}</template></el-table-column
            ></el-table
          ><el-button
            v-if="view && order.lines.some(l => pendingDeliveryQuantity(order, l, store.state.receipts) > 0)"
            type="primary"
            @click="emit('receive', order)"
            >采购入库</el-button
          ></template
        >
        <el-table v-else-if="tab === '采购退货'" :data="linkedReturns" border
          ><el-table-column prop="date" label="退货日期" /><el-table-column label="产品"
            ><template #default="{ row }">{{
              purchaseLineProductText(orderLine(row.lineId), 'model')
            }}</template></el-table-column
          ><el-table-column prop="quantity" label="退货数量" /><el-table-column
            prop="reason"
            label="原因"
        /></el-table>
        <el-table v-else :data="order.lines" border
          ><el-table-column label="产品"
            ><template #default="{ row }">{{ purchaseLineProductText(row, 'model') }}</template></el-table-column
          ><el-table-column prop="quantity" label="采购数量" /><el-table-column label="待收货数量"
            ><template #default="{ row }">{{
              remainingQuantity(store.state, order.id, row.id)
            }}</template></el-table-column
          ><el-table-column label="已确认入库"
            ><template #default="{ row }">{{
              store.state.receipts
                .filter((r) => receiptMatchesOrder(r, order.id) && r.confirmed)
                .reduce(
                  (s, r) =>
                    s +
                    r.lines
                      .filter((l) => l.lineId === row.id)
                      .reduce((n, l) => n + l.arrived - l.rejected, 0),
                  0,
                )
            }}</template></el-table-column
          ></el-table
        >
        </el-collapse-item></el-collapse>
      </template>
      <template v-else>
        <h3 class="purchase-section">入库确认</h3>
        <div class="purchase-field-label">拍照</div>
        <input
          v-if="!view"
          type="file"
          accept="image/*"
          multiple
          aria-label="入库拍照"
          @change="attach($event, true)"
        />
        <div v-for="(file, i) in receipt.photos" :key="i" class="purchase-attachment">
          <a :href="file.url" :download="file.name">{{ file.name }}</a
          ><el-button
            v-if="!view"
            text
            type="danger"
            @click="
              () => {
                receipt.photos.splice(i, 1)
                dirty = true
              }
            "
            >移除</el-button
          >
        </div>
      </template>
    </div>
    <template #footer
      ><div class="purchase-form-footer">
        <template v-if="view"
          ><el-button v-if="!isOrder || !locked" type="primary" @click="view = false"
            >编辑</el-button
          ><span v-if="isOrder && locked" class="purchase-tip"
            >已有业务记录，订单基础数据已锁定</span
          ></template
        ><el-button v-else type="primary" :loading="saving" @click="save">{{ isOrder ? '保存' : '确认入库' }}</el-button
        ><el-button @click="close()">{{ view ? '关闭' : '取消' }}</el-button>
      </div></template
    >
  </el-dialog>
  <el-dialog
    :model-value="!!picker"
    :title="
      picker === 'supplier' ? '选择供应商' : picker === 'product' ? '选择产品' : '选择采购订单'
    "
    width="min(880px,94vw)"
    append-to-body
    class="purchase-dialog"
    @close="picker = ''"
  >
    <el-input
      v-model="pickerQuery"
      :placeholder="
        picker === 'supplier'
          ? '搜索供应商或输入自定义名称'
          : picker === 'product'
            ? '搜索品牌、型号或封装'
            : '搜索订单名称或编号'
      "
      clearable
      class="purchase-picker-search"
    />
    <div v-if="picker === 'supplier'" class="purchase-custom-supplier">
      <span>也可输入未建档的供应商名称，资料稍后维护。</span>
      <el-button type="primary" plain :disabled="!pickerQuery.trim()" @click="useSupplierName">使用此供应商名称</el-button>
    </div>
    <el-table
      v-if="picker === 'supplier'"
      :data="
        supplierList.filter(
          (s) => s.enabled !== false && [s.name, s.code].join().includes(pickerQuery),
        )
      "
      border
      @row-dblclick="chooseSupplier($event.id)"
      ><el-table-column prop="name" label="供应商名称" /><el-table-column
        prop="code"
        label="供应商编码"
        width="125"
      /><el-table-column prop="contact" label="联系人" width="110" /><el-table-column
        prop="phone"
        label="手机"
        width="150"
      /><el-table-column width="80"
        ><template #default="{ row }"
          ><el-button link type="primary" @click="chooseSupplier(row.id)">选择</el-button></template
        ></el-table-column
      ></el-table
    >
    <el-table v-else-if="picker === 'order'" :data="availableOrders" border max-height="420"
      ><el-table-column prop="name" label="采购订单名称" min-width="230" /><el-table-column
        prop="code"
        label="订单编号"
        min-width="140"
        show-overflow-tooltip
      /><el-table-column prop="warehouse" label="入库仓库" width="100" /><el-table-column width="80"
        ><template #default="{ row }"
          ><el-button link type="primary" @click="chooseOrder(row)">选择</el-button></template
        ></el-table-column
      ></el-table
    >
    <el-table
      v-else
      :data="availableProducts"
      border
      @selection-change="productSelection = $event.map((p: { id: string }) => p.id)"
      ><el-table-column type="selection" width="45" /><el-table-column
        prop="brand"
        label="品牌"
        width="100" /><el-table-column
        prop="model"
        label="型号"
        min-width="130" show-overflow-tooltip /><el-table-column
        prop="packaging"
        label="封装"
        min-width="120" show-overflow-tooltip /><el-table-column prop="price" label="参考单价" width="100"
    /></el-table>
    <template #footer
      ><el-button @click="picker = ''">取消</el-button
      ><el-button
        v-if="picker === 'product'"
        type="primary"
        :disabled="!productSelection.length"
        @click="addProducts"
        >确定添加</el-button
      ></template
    >
  </el-dialog>
  <el-dialog
    v-model="quick"
    title="快速填报"
    width="min(520px,94vw)"
    append-to-body
    class="purchase-dialog"
    ><p>每行填写“型号,数量”，例如 ET7303Y,100。相同产品更新数量。</p>
    <el-input
      v-model="quickText"
      type="textarea"
      :rows="7"
      placeholder="ET7303Y,100&#10;ET8211,200"
    /><template #footer
      ><el-button @click="quick = false">取消</el-button
      ><el-button type="primary" @click="quickFill">填入明细</el-button></template
    ></el-dialog
  >
</template>

<style>
.purchase-order-editor .purchase-form-grid { margin: 12px 0 8px; column-gap: 16px; row-gap: 0; }
.purchase-order-editor .el-form-item { margin-bottom: 10px; }
.purchase-order-editor .el-form-item__label { margin-bottom: 5px; }
.purchase-order-editor .purchase-section:not(:first-child) { margin-top: 20px; }
.purchase-amount-cell { min-width: 0; }
.purchase-amount-read { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.purchase-tax-tag { flex: none; padding: 0 6px; border-radius: 3px; background: #fff4e6; color: #c26b00; font-size: 12px; line-height: 18px; }
.purchase-tax-tag-net { background: #eaf3ff; color: #2563eb; }
.purchase-amount-value { font-weight: 600; color: #17283f; }
.purchase-net-hint { color: #8a94a6; font-size: 12px; }
.purchase-order-editor .purchase-line-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin: 10px 0 14px; }
.purchase-order-lines .cell { white-space: nowrap; padding: 0 4px; font-size: 12px; }
.purchase-order-editor .purchase-order-lines .el-date-editor { width: 100%; }
.purchase-order-lines .el-select { width: 100%; }
.purchase-order-lines .purchase-tax-empty { color: #a8abb2; }
.purchase-order-business { margin-top: 16px; border-top: 0; }
.purchase-order-business .el-collapse-item__header {
  position: relative;
  height: auto;
  padding: 0 0 12px;
  border-bottom: 4px solid #d2e6ff;
  color: #1384ff;
  font-size: 14px;
  font-weight: 600;
  line-height: 22px;
}
.purchase-order-business .el-collapse-item__header:after {
  content: '';
  position: absolute;
  bottom: -4px;
  left: 0;
  width: 112px;
  height: 7px;
  background: #1384ff;
  border-radius: 0 8px 0 6px;
}
.purchase-order-business .el-collapse-item__arrow { color: #1384ff; }
.purchase-business-counts { margin-left: 12px; color: #8a94a6; font-size: 12px; font-weight: 400; }
.purchase-dialog-controls { display: flex; align-items: center; gap: 2px; margin-left: auto; }
.purchase-custom-supplier { display: flex; gap: 16px; align-items: center; margin: 12px 0; color: #8491a4; }
</style>

<style>
.purchase-order-lines .purchase-order-product-actions .el-button { padding: 8px; margin: 0; }
.purchase-form-grid.purchase-receipt-meta { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.purchase-receipt-products { display: flex; flex-direction: column; gap: 0; }
.purchase-receipt-product { min-width: 0; }
.purchase-receipt-product-heading { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: start; gap: 6px 16px; padding: 8px 10px; border: 1px solid #e4e8ed; border-bottom: 0; background: #f8fafb; }
.purchase-receipt-product:first-child .purchase-receipt-product-heading { border-radius: 4px 4px 0 0; }
.purchase-product-add-batch.el-button { height: 20px; padding: 0; margin: 0; font-size: 12px; }
.purchase-receipt-product-identity { display: flex; flex: 1; align-items: center; gap: 8px; min-width: 0; }
.purchase-receipt-product-index { flex-shrink: 0; color: #98a2ae; font-size: 12px; font-variant-numeric: tabular-nums; }
.purchase-receipt-product-description { display: flex; flex-wrap: wrap; align-items: center; gap: 3px 12px; min-width: 0; }
.purchase-receipt-product-model { display: block; overflow-wrap: anywhere; font-size: 13px; font-weight: 600; line-height: 20px; color: #263648; }
.purchase-receipt-product-specs { display: flex; flex-wrap: wrap; gap: 4px 10px; font-size: 12px; line-height: 18px; color: #7e8997; overflow-wrap: anywhere; }
.purchase-receipt-product-specs > span + span { padding-left: 10px; border-left: 1px solid #e1e6ec; }
.purchase-receipt-product-quantities { display: flex; flex-shrink: 0; flex-wrap: wrap; gap: 6px 16px; margin: 0; }
.purchase-receipt-product-quantities > div { display: flex; align-items: baseline; gap: 6px; }
.purchase-receipt-product-quantities dt { font-size: 11px; line-height: 18px; color: #84909e; }
.purchase-receipt-product-quantities dd { margin: 0; font-size: 12px; line-height: 20px; font-variant-numeric: tabular-nums; }
.purchase-batch-panel { margin-top: 0; }
.purchase-batch-scroll { overflow-x: auto; }
.purchase-batch-panel .purchase-batch-table { width: 100%; table-layout: fixed; border-collapse: collapse; background: #fff; font-size: 12px; }
.purchase-batch-index-col { width: 34px; }
.purchase-batch-date-col { width: 30%; }
.purchase-batch-quantity-col { width: 140px; }
.purchase-batch-action-col { width: 48px; }
.purchase-batch-panel .purchase-batch-table th, .purchase-batch-panel .purchase-batch-table td { height: 34px; padding: 0; border: 1px solid #e4e8ed; vertical-align: middle; }
.purchase-batch-panel .purchase-batch-table th { height: 30px; padding: 0 9px; background: #f1f4f7; font-size: 12px; font-weight: 400; text-align: left; white-space: nowrap; color: #526173; }
.purchase-batch-panel .purchase-batch-table th:first-child, .purchase-batch-panel .purchase-batch-table th:last-child { padding: 0; text-align: center; }
.purchase-batch-panel .purchase-batch-columns small { display: inline; margin-left: 5px; font-size: 11px; color: #98a1ad; }
.purchase-batch-required { margin-right: 4px; color: #e05252; }
.purchase-batch-panel .purchase-batch-table .purchase-batch-number-heading { text-align: right; padding-right: 30px; }
.purchase-batch-actions { text-align: center; }
.purchase-batch-index { color: #8a95a3; font-size: 12px; text-align: center; font-variant-numeric: tabular-nums; }
.purchase-batch-date, .purchase-batch-lot, .purchase-batch-quantity { min-width: 0; }
.purchase-batch-panel .el-input, .purchase-batch-panel .el-input-number { width: 100%; height: 34px; }
.purchase-batch-panel .el-input__wrapper { padding-left: 9px; border-radius: 0; background: #fff; box-shadow: none; }
.purchase-batch-panel .el-input__wrapper:hover { box-shadow: none; background: #fafcfd; }
.purchase-batch-panel .el-input__wrapper.is-focus { box-shadow: inset 0 0 0 1px var(--el-color-primary); }
.purchase-batch-panel .el-input__inner { height: 32px; font-size: 12px; }
.purchase-batch-panel .el-input__inner::placeholder { color: #a8b0bc; }
.purchase-dialog .purchase-batch-panel .el-input-number .el-input__inner { text-align: right; font-variant-numeric: tabular-nums; }
.purchase-batch-panel .el-input-number__increase, .purchase-batch-panel .el-input-number__decrease { width: 24px; }
.purchase-batch-panel .el-input-number .el-input__wrapper { padding-right: 30px; }
.purchase-batch-remove { width: 26px; height: 26px; padding: 0; margin: 0; color: #98a1ad; }
.purchase-batch-remove-trigger { display: inline-flex; vertical-align: middle; }
.purchase-batch-remove:hover { color: #e05252; background: #fff0f0; }
.purchase-batch-empty { color: #8a95a3; font-size: 12px; padding: 8px 0; }
.purchase-batch-footer { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; padding: 3px 10px; min-height: 26px; border-left: 1px solid #e4e8ed; border-right: 1px solid #e4e8ed; font-size: 12px; color: #8a95a3; }
.purchase-receipt-product:last-of-type .purchase-batch-footer { border-bottom: 1px solid #e4e8ed; }
.purchase-batch-footer .el-button { padding: 0; height: 24px; font-size: 12px; }
.purchase-batch-balance { margin-left: auto; color: #8a95a3; }
.purchase-batch-balance.is-over-limit { color: #d94040; }
.purchase-batch-subtotal { margin-left: auto; color: #596778; font-variant-numeric: tabular-nums; }
.purchase-batch-balance + .purchase-batch-subtotal { margin-left: 0; }
.purchase-batch-subtotal strong { margin-left: 6px; font-size: 13px; font-weight: 600; color: #009d85; }
.purchase-receipt-total { color: #009d85; font-weight: 600; font-variant-numeric: tabular-nums; }
@media (max-width: 640px) {
  .purchase-form-grid.purchase-receipt-meta { grid-template-columns: minmax(0, 1fr); }
  .purchase-receipt-product-heading { grid-template-columns: minmax(0, 1fr) auto; gap: 6px 8px; padding: 8px; }
  .purchase-product-add-batch { grid-column: 2; grid-row: 1; }
  .purchase-receipt-product-quantities { grid-column: 1 / -1; grid-row: 2; justify-content: space-between; gap: 4px 8px; }
  .purchase-receipt-product-quantities > div { gap: 4px; }
  .purchase-batch-panel .purchase-batch-table { min-width: 560px; }
  .purchase-batch-footer { gap: 4px 10px; }
  .purchase-batch-balance { font-size: 11px; }
  .purchase-batch-subtotal { display: none; }
}
</style>
