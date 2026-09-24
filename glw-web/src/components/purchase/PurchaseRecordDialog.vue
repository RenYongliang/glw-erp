<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, FullScreen } from '@element-plus/icons-vue'
import { usePurchaseStore } from '../../stores/purchase'
import {
  suppliers,
  products,
  warehouses,
  today,
  money,
  chineseMoney,
  orderTotals,
  receiptTotals,
  remainingQuantity,
  type PurchaseKind,
  type PurchaseOrder,
  type PurchaseReceipt,
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
  view = ref(!!props.readonly),
  full = ref(false),
  tab = ref('订单明细'),
  picker = ref(''),
  pickerQuery = ref(''),
  productSelection = ref<string[]>([]),
  dirty = ref(false)
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
        warehouse: '',
        address: '',
        discount: 0,
        lines: [],
        arrivals: [],
        payments: [],
        attachments: [],
        term: '月结30天',
        status: '待审批',
        reason: '',
        approver: '',
        approvedAt: '',
      },
)
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
        inspector: '李敏',
        inspectionDepartment: '质量部',
        inspectedAt: today() + ' 09:00',
        confirmed: false,
        receivedAt: '',
        keeper: '陈晨',
        department: '仓储部',
        photos: [],
      },
)
const isOrder = props.kind === 'orders',
  title = isOrder ? '采购订单' : '采购入库'
const linkedOrder = computed(() =>
  isOrder ? order.value : store.state.orders.find((o) => o.id === receipt.value.orderId),
)
const supplier = computed(() => suppliers.find((s) => s.id === linkedOrder.value?.supplierId))
const totals = computed(() => orderTotals(order.value)),
  received = computed(() => receiptTotals(receipt.value, linkedOrder.value))
const baseFields = computed(() => [
  ['供应商名称', supplier.value?.name],
  ['供应商编码', supplier.value?.code],
  ['供应商联系人姓名', supplier.value?.contact],
  ['供应商联系人手机', supplier.value?.phone],
])
const linkedReceipts = computed(() =>
  store.state.receipts.filter((r) => r.orderId === order.value.id),
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
      o.status === '审批通过' &&
      o.lines.some((l) => remainingQuantity(store.state, o.id, l.id, receipt.value.id) > 0) &&
      [o.name, o.code].join().includes(pickerQuery.value),
  ),
)
const availableProducts = computed(() =>
  products.filter((p) =>
    [p.name, p.code, p.model, p.brand]
      .join()
      .toLowerCase()
      .includes(pickerQuery.value.toLowerCase()),
  ),
)
function chooseSupplier(id: string) {
  order.value.supplierId = id
  const s = suppliers.find((s) => s.id === id)!
  order.value.name = `${s.name}-第${store.state.orders.filter((o) => o.supplierId === id).length + 1}份采购订单`
  picker.value = ''
  dirty.value = true
}
function chooseOrder(o: PurchaseOrder) {
  receipt.value.orderId = o.id
  receipt.value.warehouse = o.warehouse
  receipt.value.lines = o.lines
    .filter((l) => remainingQuantity(store.state, o.id, l.id, receipt.value.id) > 0)
    .map((l) => ({
      lineId: l.id,
      arrived: remainingQuantity(store.state, o.id, l.id, receipt.value.id),
      rejected: 0,
      location: '',
    }))
  picker.value = ''
  dirty.value = true
}
if (props.relatedOrder && !props.receipt) chooseOrder(props.relatedOrder)
function addProducts() {
  for (const id of productSelection.value)
    if (!order.value.lines.some((l) => l.productId === id)) {
      const p = products.find((p) => p.id === id)!
      order.value.lines.push({
        id: crypto.randomUUID(),
        productId: id,
        quantity: 1,
        price: p.price,
        tax: p.tax,
      })
    }
  productSelection.value = []
  picker.value = ''
  dirty.value = true
}
function productValue(id: string | undefined, key: string) {
  const p = products.find((p) => p.id === id)
  return p ? p[key as keyof typeof p] : ''
}
function productFor(lineId: string) {
  return products.find(
    (p) => p.id === linkedOrder.value?.lines.find((l) => l.id === lineId)?.productId,
  )
}
function orderLine(lineId: string) {
  return linkedOrder.value?.lines.find((l) => l.id === lineId)
}
function openPicker(name: string) {
  pickerQuery.value = ''
  productSelection.value = []
  picker.value = name
}
function save() {
  try {
    if (isOrder) store.saveOrder(order.value)
    else store.saveReceipt(receipt.value)
    dirty.value = false
    ElMessage.success(
      isOrder ? '已提交，等待审批' : receipt.value.confirmed ? '入库已确认' : '已保存到待入库',
    )
    emit('saved')
  } catch (e) {
    ElMessage.error((e as Error).message)
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
async function approve(passed: boolean) {
  try {
    let reason = ''
    if (!passed) {
      const r = await ElMessageBox.prompt('请填写不通过原因', '合同审批', {
        inputValidator: (v) => !!v?.trim() || '请填写原因',
      })
      reason = r.value
    }
    store.approve(order.value.id, passed, reason)
    ElMessage.success('审批结果已保存')
    emit('saved')
  } catch (e) {
    if (e instanceof Error) ElMessage.error(e.message)
  }
}
async function attach(event: Event, images = false) {
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
      const target: Attachment[] = images ? receipt.value.photos : order.value.attachments
      target.push({ name: file.name, url })
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
  const changes = rows.map((r) => ({
    p: products.find((p) => p.code === r[0]?.trim()),
    q: Number(r[1]),
  }))
  if (!changes.length || changes.some((r) => !r.p || !Number.isSafeInteger(r.q) || r.q <= 0))
    return ElMessage.error('每行填写产品编码和正整数数量，以逗号或 Tab 分隔')
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
          quantity: r.q,
          price: r.p!.price,
          tax: r.p!.tax,
        })
    } else {
      const l = linkedOrder.value?.lines.find((l) => l.productId === r.p!.id),
        row = receipt.value.lines.find((x) => x.lineId === l?.id)
      if (!row) return ElMessage.error('产品不在本次入库订单中')
      row.arrived = r.q
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
    width="820px"
    top="3vh"
    :fullscreen="full"
    class="purchase-dialog"
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
              order.status === '审批通过'
                ? 'success'
                : order.status === '不通过'
                  ? 'danger'
                  : 'warning'
            "
            >{{ order.status }}</el-tag
          ></span
        ><el-button :icon="FullScreen" text aria-label="切换表单全屏" @click="full = !full" /></div
    ></template>
    <div class="purchase-form-body" @change="dirty = true" @input="dirty = true">
      <h3 class="purchase-section">{{ isOrder ? '采购订单基础信息' : '采购订单信息' }}</h3>
      <p class="purchase-tip">
        {{
          isOrder
            ? '使用Tips：采购订单通过标签页，可以一站式查看采购业务流程'
            : '选择采购订单后自动带入供应商和产品明细；确认后计入已入库数量。'
        }}
      </p>
      <el-form label-position="top" class="purchase-form-grid" :disabled="view">
        <el-form-item :label="isOrder ? '选择供应商' : '选择采购订单'" required
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
          ><el-form-item label="采购订单名称"
            ><div class="purchase-read">{{ linkedOrder?.name || '暂无内容' }}</div></el-form-item
          ><el-form-item label="采购订单编号"
            ><div class="purchase-read">{{ linkedOrder?.code || '暂无内容' }}</div></el-form-item
          ></template
        >
        <el-form-item v-for="[label, value] in baseFields" :key="label" :label="label"
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
        <el-form-item :label="isOrder ? '采购订单编号' : '采购入库单编号'"
          ><div class="purchase-read">
            {{ (isOrder ? order.code : receipt.code) || '自动生成无需填写' }}
          </div></el-form-item
        >
        <el-form-item label="采购负责人"
          ><el-select v-if="isOrder" v-model="order.owner" filterable allow-create
            ><el-option v-for="n in ['张睿', '林晓', '陈晨']" :key="n" :value="n"
          /></el-select>
          <div v-else class="purchase-read">
            {{ linkedOrder?.owner || '暂无内容' }}
          </div></el-form-item
        >
        <el-form-item label="采购归属部门"
          ><el-select v-if="isOrder" v-model="order.department" filterable allow-create
            ><el-option v-for="n in ['采购部', '业务部', '生产部', '市场部']" :key="n" :value="n"
          /></el-select>
          <div v-else class="purchase-read">
            {{ linkedOrder?.department || '暂无内容' }}
          </div></el-form-item
        >
      </el-form>
      <h3 class="purchase-section">{{ isOrder ? '采购产品明细' : '入库产品明细' }}</h3>
      <el-form label-position="top" :disabled="view"
        ><el-form-item label="入库仓库" required class="purchase-third"
          ><el-select v-if="isOrder" v-model="order.warehouse" aria-label="入库仓库"
            ><el-option v-for="w in warehouses" :key="w" :value="w"
          /></el-select>
          <div v-else class="purchase-read">
            {{ receipt.warehouse || '选择订单后自动带入' }}
          </div></el-form-item
        ></el-form
      >
      <div class="purchase-field-label">
        <span>*</span> {{ isOrder ? '采购产品明细' : '入库产品明细' }}
      </div>
      <el-table
        v-if="isOrder"
        :data="order.lines"
        border
        class="purchase-lines"
        empty-text="点击“添加”选择采购产品"
      >
        <el-table-column type="index" width="45" />
        <el-table-column label="产品名称" width="145"
          ><template #default="{ row }">{{
            products.find((p) => p.id === row.productId)?.name
          }}</template></el-table-column
        >
        <el-table-column
          v-for="f in [
            { key: 'code', label: '产品编码' },
            { key: 'brand', label: '品牌' },
            { key: 'model', label: '规格型号' },
            { key: 'unit', label: '单位' },
            { key: 'stock', label: '当前可用库存数量' },
          ]"
          :key="f.key"
          :label="f.label"
          :width="f.key === 'model' ? 190 : 125"
          ><template #default="{ row }">{{
            productValue(row.productId, f.key)
          }}</template></el-table-column
        >
        <el-table-column label="采购数量" width="155"
          ><template #default="{ row }"
            ><el-input-number
              v-model="row.quantity"
              :disabled="view"
              :min="1"
              :max="100000000"
              :precision="0"
              controls-position="right"
              aria-label="采购数量" /></template
        ></el-table-column>
        <el-table-column label="采购单价(含税)/元" width="165"
          ><template #default="{ row }"
            ><el-input-number
              v-model="row.price"
              :disabled="view"
              :min="0"
              :max="10000000"
              :precision="2"
              controls-position="right"
              aria-label="采购单价" /></template
        ></el-table-column>
        <el-table-column label="增值税税率 %" width="150"
          ><template #default="{ row }"
            ><el-input-number
              v-model="row.tax"
              :disabled="view"
              :min="0"
              :max="100"
              :precision="2"
              controls-position="right" /></template
        ></el-table-column>
        <el-table-column label="产品原价合价(含税)/元" width="185"
          ><template #default="{ $index }">{{
            money(totals.details[$index]?.gross || 0)
          }}</template></el-table-column
        >
        <el-table-column label="实际采购价(含税)/元" width="165"
          ><template #default="{ $index }">{{
            (totals.details[$index]?.unit || 0).toFixed(4)
          }}</template></el-table-column
        >
        <el-table-column label="产品采购价合计(含税)/元" width="190"
          ><template #default="{ $index }">{{
            money(totals.details[$index]?.total || 0)
          }}</template></el-table-column
        >
        <el-table-column label="产品采购价合计(不含税)/元" width="205"
          ><template #default="{ $index }">{{
            money(totals.details[$index]?.net || 0)
          }}</template></el-table-column
        >
        <el-table-column v-if="!view" fixed="right" label="操作" width="65"
          ><template #default="{ $index }"
            ><el-button
              text
              type="danger"
              :icon="Delete"
              aria-label="删除产品"
              @click="
                () => {
                  order.lines.splice($index, 1)
                  dirty = true
                }
              " /></template
        ></el-table-column>
      </el-table>
      <el-table
        v-else
        :data="receipt.lines"
        border
        class="purchase-lines"
        empty-text="请先选择采购订单"
      >
        <el-table-column type="index" width="45" /><el-table-column label="产品名称" width="145"
          ><template #default="{ row }">{{
            productFor(row.lineId)?.name
          }}</template></el-table-column
        >
        <el-table-column
          v-for="f in [
            { key: 'code', label: '产品编码' },
            { key: 'brand', label: '品牌' },
            { key: 'model', label: '规格型号' },
            { key: 'unit', label: '单位' },
          ]"
          :key="f.key"
          :label="f.label"
          :width="f.key === 'model' ? 190 : 100"
          ><template #default="{ row }">{{
            productValue(productFor(row.lineId)?.id, f.key)
          }}</template></el-table-column
        >
        <el-table-column label="采购数量" width="105"
          ><template #default="{ row }">{{
            orderLine(row.lineId)?.quantity
          }}</template></el-table-column
        >
        <el-table-column label="待入库数量" width="110"
          ><template #default="{ row }">{{
            remainingQuantity(store.state, receipt.orderId, row.lineId, receipt.id)
          }}</template></el-table-column
        >
        <el-table-column label="本次到货数量" width="160"
          ><template #default="{ row }"
            ><el-input-number
              v-model="row.arrived"
              :disabled="view"
              :min="0"
              :max="remainingQuantity(store.state, receipt.orderId, row.lineId, receipt.id)"
              :precision="0"
              controls-position="right"
              aria-label="本次到货数量" /></template
        ></el-table-column>
        <el-table-column label="不合格数量" width="150"
          ><template #default="{ row }"
            ><el-input-number
              v-model="row.rejected"
              :disabled="view"
              :min="0"
              :max="row.arrived"
              :precision="0"
              controls-position="right"
              aria-label="不合格数量"
              @change="
                () => {
                  receipt.quality = receipt.lines.some((l) => l.rejected > 0)
                    ? '存在不合格品'
                    : '合格'
                }
              " /></template
        ></el-table-column>
        <el-table-column label="本次入库数量" width="125"
          ><template #default="{ row }">{{ row.arrived - row.rejected }}</template></el-table-column
        >
        <el-table-column label="入库仓位" width="145"
          ><template #default="{ row }"
            ><el-input
              v-model="row.location"
              :disabled="view"
              placeholder="如 HZ-A01"
              aria-label="入库仓位" /></template
        ></el-table-column>
      </el-table>
      <div v-if="!view" class="purchase-line-actions">
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
      <el-form label-position="top" class="purchase-form-grid" :disabled="view">
        <template v-if="isOrder">
          <el-form-item label="采购原价总额(含税)/元"
            ><div class="purchase-read">{{ money(totals.gross) }}</div></el-form-item
          >
          <el-form-item label="优惠金额/元"
            ><small>按金额比例均摊到各产品</small
            ><el-input-number
              v-model="order.discount"
              :min="0"
              :max="totals.gross"
              :precision="2"
              controls-position="right"
              aria-label="优惠金额"
          /></el-form-item>
          <el-form-item label="整单折扣率 %"
            ><div class="purchase-read">{{ totals.rate.toFixed(2) }}%</div></el-form-item
          >
          <el-form-item label="采购订单金额(含税)/元"
            ><div class="purchase-read">{{ money(totals.total) }}</div></el-form-item
          ><el-form-item label="采购订单金额大写"
            ><div class="purchase-read">{{ chineseMoney(totals.total) }}</div></el-form-item
          ><el-form-item label="采购金额合计(不含税)/元"
            ><div class="purchase-read">{{ money(totals.net) }}</div></el-form-item
          >
        </template>
        <template v-else
          ><el-form-item label="入库产品总数"
            ><div class="purchase-read">{{ received.quantity }}</div></el-form-item
          ><el-form-item label="入库产品采购价总额(含税)/元"
            ><div class="purchase-read">{{ money(received.total) }}</div></el-form-item
          ></template
        >
      </el-form>
      <template v-if="isOrder">
        <div class="purchase-field-label">合同附件上传</div>
        <input
          v-if="!view"
          type="file"
          multiple
          aria-label="合同附件上传"
          @change="attach($event)"
        /><small v-if="!view" class="purchase-tip">本地预览：每个文件不超过 2MB。</small>
        <div v-for="(file, i) in order.attachments" :key="i" class="purchase-attachment">
          <a :href="file.url" :download="file.name">{{ file.name }}</a
          ><el-button
            v-if="!view"
            text
            type="danger"
            @click="
              () => {
                order.attachments.splice(i, 1)
                dirty = true
              }
            "
            >移除</el-button
          >
        </div>
        <nav class="purchase-detail-tabs" aria-label="订单关联信息">
          <button
            v-for="name in ['订单明细', '财务明细', '采购入库', '采购退货', '采购订单执行跟踪']"
            :key="name"
            :class="{ active: tab === name }"
            @click="tab = name"
          >
            {{ name }}
          </button>
        </nav>
        <template v-if="tab === '订单明细'">
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
            v-if="view && order.status === '审批通过'"
            type="primary"
            @click="emit('receive', order)"
            >采购入库</el-button
          ></template
        >
        <el-table v-else-if="tab === '采购退货'" :data="linkedReturns" border
          ><el-table-column prop="date" label="退货日期" /><el-table-column label="产品"
            ><template #default="{ row }">{{
              productFor(row.lineId)?.name
            }}</template></el-table-column
          ><el-table-column prop="quantity" label="退货数量" /><el-table-column
            prop="reason"
            label="原因"
        /></el-table>
        <el-table v-else :data="order.lines" border
          ><el-table-column label="产品"
            ><template #default="{ row }">{{ productFor(row.id)?.name }}</template></el-table-column
          ><el-table-column prop="quantity" label="采购数量" /><el-table-column label="待收货数量"
            ><template #default="{ row }">{{
              remainingQuantity(store.state, order.id, row.id)
            }}</template></el-table-column
          ><el-table-column label="已确认入库"
            ><template #default="{ row }">{{
              store.state.receipts
                .filter((r) => r.orderId === order.id && r.confirmed)
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
        <template v-if="view"
          ><h3 class="purchase-section">合同审批</h3>
          <el-descriptions :column="2" border
            ><el-descriptions-item label="审批结果">{{ order.status }}</el-descriptions-item
            ><el-descriptions-item label="审批人">{{ order.approver || '—' }}</el-descriptions-item
            ><el-descriptions-item label="审批时间">{{
              order.approvedAt || '—'
            }}</el-descriptions-item
            ><el-descriptions-item label="不通过原因">{{
              order.reason || '—'
            }}</el-descriptions-item></el-descriptions
          ></template
        >
      </template>
      <template v-else>
        <h3 class="purchase-section">入库质检</h3>
        <el-form label-position="top" :disabled="view"
          ><el-form-item label="是否存在不合格品" required
            ><el-radio-group v-model="receipt.quality"
              ><el-radio value="合格">合格</el-radio
              ><el-radio value="存在不合格品">存在不合格品</el-radio></el-radio-group
            ></el-form-item
          ><el-form-item v-if="receipt.quality === '存在不合格品'" label="不合格品说明"
            ><el-input v-model="receipt.qualityNote" type="textarea" /></el-form-item
        ></el-form>
        <el-form label-position="top" class="purchase-form-grid" :disabled="view"
          ><el-form-item label="质检时间"
            ><el-date-picker
              v-model="receipt.inspectedAt"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm" /></el-form-item
          ><el-form-item label="质检员"><el-input v-model="receipt.inspector" /></el-form-item
          ><el-form-item label="质检员归属部门"
            ><el-input v-model="receipt.inspectionDepartment" /></el-form-item
        ></el-form>
        <h3 class="purchase-section">入库确认</h3>
        <el-checkbox
          v-model="receipt.confirmed"
          :disabled="view"
          @change="receipt.receivedAt ||= today() + ' 10:00'"
          >合格品入库确认</el-checkbox
        >
        <p class="purchase-tip">未确认的单据保存在“待入库”，确认后更新为已入库。</p>
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
        <el-form label-position="top" class="purchase-form-grid" :disabled="view"
          ><el-form-item label="入库时间"
            ><el-date-picker
              v-model="receipt.receivedAt"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm" /></el-form-item
          ><el-form-item label="入库员"><el-input v-model="receipt.keeper" /></el-form-item
          ><el-form-item label="库管归属部门"
            ><el-input v-model="receipt.department" /></el-form-item
        ></el-form>
      </template>
    </div>
    <template #footer
      ><div class="purchase-form-footer">
        <template v-if="view"
          ><el-button v-if="!isOrder || !locked" type="primary" @click="view = false"
            >编辑</el-button
          ><el-button
            v-if="isOrder && order.status === '待审批'"
            type="success"
            @click="approve(true)"
            >审批通过</el-button
          ><el-button
            v-if="isOrder && order.status === '待审批'"
            type="danger"
            plain
            @click="approve(false)"
            >不通过</el-button
          ><span v-if="isOrder && locked" class="purchase-tip"
            >已有业务记录，订单基础数据已锁定</span
          ></template
        ><el-button v-else type="primary" @click="save">提交</el-button
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
      placeholder="搜索名称、编码或规格型号"
      clearable
      class="purchase-picker-search"
    />
    <el-table
      v-if="picker === 'supplier'"
      :data="suppliers.filter((s) => [s.name, s.code].join().includes(pickerQuery))"
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
        min-width="165"
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
        prop="name"
        label="产品名称" /><el-table-column
        prop="code"
        label="产品编码"
        width="100" /><el-table-column
        prop="model"
        label="规格型号"
        min-width="180" /><el-table-column prop="price" label="含税单价" width="100"
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
    ><p>每行填写“产品编码,数量”，例如 CP001,100。相同产品更新数量。</p>
    <el-input
      v-model="quickText"
      type="textarea"
      :rows="7"
      placeholder="CP001,100&#10;CP002,200"
    /><template #footer
      ><el-button @click="quick = false">取消</el-button
      ><el-button type="primary" @click="quickFill">填入明细</el-button></template
    ></el-dialog
  >
</template>
