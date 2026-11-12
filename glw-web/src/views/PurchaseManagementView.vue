<script setup lang="ts">
import { emptyPurchaseOrderFilters, purchaseFilterCount, matchesPurchaseOrderFilters } from "../domain/purchaseOrderFilters";
import { orderStatusBadge } from "../domain/orderStatusBadge";
import { useListPagination } from "../composables/useListPagination";
import { computed, ref, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  Upload,
  Download,
  TakeawayBox,
  Clock,
  Search,
  Filter,
  Refresh,
  Sort,
  Setting,
  Operation,
  FullScreen,
  Document,
  Money,
  RefreshLeft,
  CircleCloseFilled,
} from '@element-plus/icons-vue'
import PurchaseRecordDialog from '../components/purchase/PurchaseRecordDialog.vue'
import InboundReceiptDialog from '../components/purchase/InboundReceiptDialog.vue'
import PurchaseInboundHistoryDialog from '../components/purchase/PurchaseInboundHistoryDialog.vue'
import MultiOrderInboundDialog from '../components/purchase/MultiOrderInboundDialog.vue'
import { usePurchaseStore } from '../stores/purchase'
import { financeApi, type SettleOrderResult } from '../services/financeApi'
import {
  suppliers,
  purchaseSupplierName,
  purchaseContact,
  purchasePhone,
  products,
  money,
  today,
  orderTotals,
  receiptTotals,
  receiptOrders,
  receiptMatchesOrder,
  receiptProducts,
  receiptLineQuantity,
  returnableQuantity,
  remainingQuantity,
  pendingDeliveryQuantity,
  chineseMoney,
  type PurchaseKind,
  type PurchaseOrder,
  type PurchaseReceipt,
  type PurchasePayment,
  type PurchaseReturn,
} from '../domain/purchase'
import { encodeCsv, parseCsv } from '../domain/crm'
import { downloadExport } from '../services/exportApi'
import '../assets/purchase.css'
const props = defineProps<{ kind: PurchaseKind }>(),
  store = usePurchaseStore(),
  isOrder = props.kind === 'orders'
const tabs = isOrder
  ? ['全部订单', '待收货', '待付款', '已完结']
  : ['全部入库', '已入库', '待入库']
const tab = ref(tabs[0]!),
  keyword = ref(''),
  filterOpen = ref(false),
  filterKey = ref('supplier'),
  filterOperator = ref('包含'),
  filterValue = ref(''),
  grouped = ref(true),
  collapsed = ref<string[]>([]),
  sortKey = ref(''),
  sortDirection = ref('asc'),
  hidden = ref<string[]>([]),
  logsOpen = ref(false),
  expanded = ref(false),
  rowHeight = ref('normal')
const orderFilterOpen = ref(false)
const orderFilters = ref(emptyPurchaseOrderFilters())
const draftFilters = ref(emptyPurchaseOrderFilters())
const activeFilterCount = computed(() => purchaseFilterCount(orderFilters.value))
const filterSuppliers = computed(() => [...new Set(store.state.orders.map(purchaseSupplierName).filter(Boolean))])
const productTextFilters = [{key:'brand',label:'品牌'}, {key:'model',label:'型号'}, {key:'packaging',label:'封装'}] as const
function openFilters() {
  if (!isOrder) { filterOpen.value = !filterOpen.value; return }
  draftFilters.value = JSON.parse(JSON.stringify(orderFilters.value))
  orderFilterOpen.value = true
}
function applyOrderFilters() {
  orderFilters.value = JSON.parse(JSON.stringify(draftFilters.value))
  orderFilterOpen.value = false
  collapsed.value = []
}
const groupMode = ref<'month' | 'supplier' | 'none'>('month')
function changeGrouping(mode: 'month' | 'supplier' | 'none') {
  groupMode.value = mode
  grouped.value = mode !== 'none'
  collapsed.value = []
}
const editor = ref(false),
  editorKind = ref<PurchaseKind>(props.kind),
  editingOrder = ref<PurchaseOrder>(),
  editingReceipt = ref<PurchaseReceipt>(),
  relatedOrder = ref<PurchaseOrder>(),
  readonly = ref(false),
  editorVersion = ref(0)
const scopeOrder = ref<PurchaseOrder>()
const singleReceipt = computed(() => editorKind.value === 'receipts' && !!relatedOrder.value && !editingReceipt.value)
type TableRow = {
  id: string
  group: string
  values: Record<string, string | number>
  order?: PurchaseOrder
  receipt?: PurchaseReceipt
}
const orderColumns = [
  ['code', '采购单号'], ['supplier', '供应商'], ['signed', '采购日期'],
  ['receiptStatus', '收货状态'],
  ['productNames', '型号'], ['brands', '品牌'], ['models', '封装'],
  ['prices', '单价'], ['quantities', '采购数量'], ['pendingDelivery', '待交数量'],
  ['amounts', '金额'], ['deliveries', '交付日期'],
  ['total', '订单金额'],
  ['prepayment', '预付款'],
  ['owner', '采购负责人'],
]
const productKeys = isOrder
  ? ['productNames', 'brands', 'models', 'prices', 'quantities', 'pendingDelivery', 'amounts', 'deliveries']
  : ['productNames', 'brands', 'models', 'quantities', 'received']
function productCells(order: PurchaseOrder, key: string, receipt?: PurchaseReceipt): string[] {
  const totals = orderTotals(order)
  return order.lines.flatMap((l, i) => {
    if (receipt && !receipt.lines.some(line => line.lineId === l.id)) return []
    const cells: Record<string, string> = { brands: l.brand || '', productNames: l.model || '', models: l.packaging || '', quantities: String(l.quantity), pendingDelivery: String(pendingDeliveryQuantity(order, l, store.state.receipts)), received: receipt ? String(receiptLineQuantity(receipt, l.id)) : '—', taxes: `${l.tax}%`, prices: money(l.price), amounts: money(l.amount ?? totals.details[i]?.total ?? 0), deliveries: l.delivery ?? order.delivery, remarks: l.remark || '—' }
    return cells[key] || '—'
  })
}
function receiptCells(receipt: PurchaseReceipt, key: string): string[] {
  return receiptProducts(receipt, receiptOrders(receipt, store.state.orders)).map(product => {
    const values: Record<string, string> = { brands: product.brand, productNames: product.model, models: product.packaging, quantities: String(product.ordered), received: String(product.quantity) }
    return values[key] || '—'
  })
}
const receiptColumns = [
  ['code', '入库单号'],
  ['orderCode', '采购单号'],
  ['supplier', '供应商'],
  ['receivedAt', '入库日期'],
  ['productNames', '型号'], ['brands', '品牌'], ['models', '封装'],
  ['quantities', '采购数量'], ['received', '入库数量'],
  ['status', '入库状态'],
]
const columns = (isOrder ? orderColumns : receiptColumns).map(([key, label]) => ({
  key: key!,
  label: label!,
}))
const shownColumns = computed(() => columns.filter((c) => !hidden.value.includes(c.key)))
const records = computed<TableRow[]>(() =>
  isOrder
    ? store.state.orders.map((o) => {
        const t = orderTotals(o)
        return {
          id: o.id,
          order: o,
          group: purchaseSupplierName(o) || '未填写',
          values: {
            ...Object.fromEntries(productKeys.map(key => [key, productCells(o, key).join('、')])),
            signed: o.signed,
            supplier: purchaseSupplierName(o),
            contact: purchaseContact(o),
            phone: purchasePhone(o),
            quantity: o.lines.reduce((n,l) => n + l.quantity, 0),
            name: o.name,
            code: o.code,
            owner: o.owner,
            department: o.department,
            warehouse: o.warehouse,
            gross: t.gross,
            discount: o.discount,
            rate: t.rate.toFixed(2) + '%',
            total: t.total,
            prepayment: o.prepaymentAmount ? o.prepaymentAmount : '—',
            uppercase: chineseMoney(t.total),
            net: t.net,
            attachments: o.attachments.map((a) => a.name).join('、'),
            address: o.address,
            orderStatus: o.orderStatus,
            receiptStatus: o.receiptStatus,
          },
        }
      })
    : store.state.receipts.map((r) => {
        const linkedOrders = receiptOrders(r, store.state.orders), o = linkedOrders[0],
          t = receiptTotals(r, linkedOrders)
        return {
          id: r.id,
          receipt: r,
          order: o,
          group: purchaseSupplierName(o) || '未填写',
          values: {
            ...Object.fromEntries(productKeys.map(key => [key, receiptCells(r, key).join('、')])),
            name: o?.name || '—',
            orderCode: linkedOrders.map(order => order.code).join('、') || '—',
            supplier: purchaseSupplierName(o) || '—',
            supplierCode: o?.code || '—',
            contact: purchaseContact(o) || '—',
            phone: purchasePhone(o) || '—',
            owner: o?.owner || '—',
            department: o?.department || '—',
            code: r.code,
            receivedAt: r.receivedAt?.slice(0, 10) || '—',
            quantity: t.quantity,
            total: t.total,
            quality: r.quality,
            inspectedAt: r.inspectedAt,
            status: r.confirmed ? '已入库' : '待入库',
          },
        }
      }),
)
function matchesOrderTab(order: PurchaseOrder, tabName: string) {
  if (tabName === '待收货')
    return (
      order.orderStatus === '执行中' &&
      order.lines.some((l) => pendingDeliveryQuantity(order, l, store.state.receipts) > 0)
    )
  if (tabName === '待付款')
    return (
      !['已取消', '待审核'].includes(order.orderStatus) &&
      order.receiptStatus !== '未收货' &&
      (order.paymentStatus ?? '未付款') !== '已付款'
    )
  if (tabName === '已完结') return order.orderStatus === '已完结'
  return true
}
function statusTagType(key: string, value: string) {
  if (key === 'orderStatus')
    return value === '已完结' ? 'success' : value === '执行中' ? 'warning' : value === '已取消' ? 'danger' : 'info'
  if (key === 'receiptStatus')
    return value === '全部收货' ? 'success' : value === '部分收货' ? 'warning' : 'info'
  return value === '已入库' ? 'success' : 'warning'
}
const filtered = computed(() => {
  const rows = records.value.filter((r) => {
    if (isOrder && r.order && !matchesOrderTab(r.order, tab.value)) return false
    if (!isOrder && tab.value === '待入库' && r.receipt?.confirmed) return false
    if (!isOrder && tab.value === '已入库' && !r.receipt?.confirmed) return false
    if (
      keyword.value.trim() &&
      ![r.group, ...Object.values(r.values)]
        .join(' ')
        .toLowerCase()
        .includes(keyword.value.trim().toLowerCase())
    )
      return false
    if (isOrder && r.order) return matchesPurchaseOrderFilters(r.order, products, purchaseSupplierName(r.order), Number(r.values.total), store.state.payments.filter(p => p.orderId === r.order!.id).reduce((sum, p) => sum + p.amount, 0), store.state.receipts, orderFilters.value)
    if (!filterOpen.value) return true
    const v = String(r.values[filterKey.value] ?? '')
    if (filterOperator.value === '为空') return !v
    if (filterOperator.value === '不为空') return !!v
    if (!filterValue.value) return true
    if (filterOperator.value === '等于') return v === filterValue.value
    if (filterOperator.value === '不等于') return v !== filterValue.value
    return filterOperator.value === '不包含'
      ? !v.includes(filterValue.value)
      : v.includes(filterValue.value)
  })
  if (sortKey.value)
    rows.sort((a, b) => {
      const x = a.values[sortKey.value] ?? '',
        y = b.values[sortKey.value] ?? ''
      return (
        (typeof x === 'number' && typeof y === 'number'
          ? x - y
          : String(x).localeCompare(String(y), 'zh-CN')) * (sortDirection.value === 'asc' ? 1 : -1)
      )
    })
  return rows
})
const allGroups = computed(() => {
  if (!grouped.value) return [{ name: '', rows: filtered.value }]
  const monthly = groupMode.value === 'month'
  const missingDate = isOrder ? '未填写采购日期' : '未填写入库日期'
  const key = (row: TableRow) => {
    if (!monthly) return row.group
    const match = /^(\d{4})-(0[1-9]|1[0-2])-\d{2}/.exec((isOrder ? row.order?.signed : row.receipt?.receivedAt) || '')
    return match ? `${match[1]}年${match[2]}月` : missingDate
  }
  const names = [...new Set(filtered.value.map(key))]
  if (monthly) names.sort((a, b) => a === b ? 0 : a === missingDate ? 1 : b === missingDate ? -1 : b.localeCompare(a))
  return names.map(name => ({ name, rows: filtered.value.filter(row => key(row) === name) }))
})
const paginationSource = computed(() => allGroups.value.flatMap(group => group.rows));
const { page, pageSize, pageRows } = useListPagination(paginationSource);
watch([page, pageSize], () => { collapsed.value = []; });
const groups = computed(() => {
  const visible = new Set(pageRows.value.map(row => row.id));
  return allGroups.value.map(group => ({ ...group, rows: group.rows.filter(row => visible.has(row.id)) })).filter(group => group.rows.length);
});
function toggleGroup(name: string) {
  collapsed.value = collapsed.value.includes(name)
    ? collapsed.value.filter((n) => n !== name)
    : [...collapsed.value, name]
}
function display(row: TableRow, key: string) {
  const value = row.values[key]
  return typeof value === 'number' && key !== 'quantity' ? money(value) : value
}
function open(row?: TableRow, read = false) {
  scopeOrder.value = undefined
  editorKind.value = props.kind
  editingOrder.value = isOrder ? row?.order : undefined
  editingReceipt.value = isOrder ? undefined : row?.receipt
  relatedOrder.value = undefined
  readonly.value = read
  editorVersion.value++
  editor.value = true
}
function canCancelOrder(order: PurchaseOrder) {
  return (
    order.orderStatus === '执行中' &&
    !store.state.receipts.some((r) => receiptMatchesOrder(r, order.id)) &&
    !store.state.payments.some((p) => p.orderId === order.id) &&
    !store.state.returns.some((r) => r.orderId === order.id)
  )
}
async function reverseReceipt(receipt: PurchaseReceipt) {
  const prepaid = receiptOrders(receipt, store.state.orders)
    .reduce((sum, order) => sum + Number(order.prepaymentAmount ?? 0), 0)
  const prepaymentNotice = prepaid > 0
    ? `该订单还有未核销预付款 ¥${money(prepaid)}，撤销入库后会保留在订单上，可后续冲抵应付或到财务退款；如为误登记，请先在财务收付款中作废。`
    : ''
  try {
    await ElMessageBox.confirm(
      `确认撤销入库单 ${receipt.code}？撤销后库存将会扣回，相关应付将一并删除，未确认的对账单会同步作废。${prepaymentNotice}`,
      '撤销入库',
      { confirmButtonText: '撤销', cancelButtonText: '返回', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await store.reverseReceipt(receipt.id)
    ElMessage.success('入库单已撤销')
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}
async function cancelOrder(order: PurchaseOrder) {
  try {
    await ElMessageBox.confirm(
      `确认取消订单 ${order.code}？取消后不会再生成入库单。`,
      '取消订单',
      { confirmButtonText: '确认取消', cancelButtonText: '返回', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await store.cancelOrder(order.id)
    ElMessage.success('订单已取消')
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}
function receive(order: PurchaseOrder) {
  scopeOrder.value = undefined
  editor.value = false
  editorKind.value = 'receipts'
  editingOrder.value = undefined
  editingReceipt.value = undefined
  relatedOrder.value = order
  readonly.value = false
  editorVersion.value++
  editor.value = true
}
function openAllocation(row: TableRow, order: PurchaseOrder) {
  open(row, true)
  scopeOrder.value = order
}
function saved() {
  editor.value = false
}
async function refresh(showSuccess = false) {
  await store.load()
  if (store.loadError) ElMessage.warning(store.loadError)
  else if (showSuccess) ElMessage.success('数据已刷新')
}
onMounted(() => {
  void refresh()
})
const operation = ref(''),
  activeOrder = ref<PurchaseOrder>(),
  payment = ref<PurchasePayment>(),
  returned = ref<PurchaseReturn>()
// 未结应付来自财务开项；订单可付金额按订单总额扣除已登记付款（含未核销预付款）计算。
const financeOutstanding = ref<number | null>(null),
  financeSettled = ref(0),
  financeOutstandingLoading = ref(false),
  paymentRequestKey = ref('')
const unpaid = computed(() => financeOutstanding.value ?? 0)
const orderAmount = computed(() => (activeOrder.value ? orderTotals(activeOrder.value).total : 0))
const paidAmount = computed(
  () => Number(activeOrder.value?.prepaymentAmount ?? 0) + financeSettled.value,
)
const payable = computed(() =>
  Math.max(0, Math.round((orderAmount.value - paidAmount.value) * 100) / 100),
)
async function loadOutstanding(order: PurchaseOrder) {
  financeOutstanding.value = null
  financeSettled.value = 0
  financeOutstandingLoading.value = true
  try {
    const page = await financeApi.openItems({
      direction: 'AP',
      orderId: Number(order.id),
      pageNo: 1,
      pageSize: 200,
    })
    financeOutstanding.value = page.records.reduce(
      (sum, item) => sum + Number(item.grossAmount ?? 0) - Number(item.settledAmount ?? 0),
      0,
    )
    financeSettled.value = page.records.reduce(
      (sum, item) => sum + Number(item.settledAmount ?? 0),
      0,
    )
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '未结金额加载失败')
  } finally {
    financeOutstandingLoading.value = false
  }
}
function business(order: PurchaseOrder, kind: string) {
  activeOrder.value = order
  operation.value = kind
  if (kind === '付款') {
    paymentRequestKey.value = crypto.randomUUID()
    payment.value = {
      id: crypto.randomUUID(),
      orderId: order.id,
      amount: 0,
      date: today(),
      method: '银行转账',
      bankReference: '',
      note: '',
    }
    void loadOutstanding(order).then(() => {
      if (payment.value)
        payment.value.amount = Math.max(
          0,
          Math.round(Math.min(financeOutstanding.value ?? 0, payable.value) * 100) / 100,
        )
    })
  } else
    returned.value = {
      id: crypto.randomUUID(),
      orderId: order.id,
      lineId:
        order.lines.find((l) => returnableQuantity(store.state, order.id, l.id) > 0)?.id || '',
      quantity: 1,
      date: today(),
      reason: '',
    }
}
function settlementMessage(action: string, result: SettleOrderResult) {
  const allocated = Number(result.allocatedAmount ?? 0),
    unallocated = Number(result.unallocatedAmount ?? 0)
  if (allocated <= 0 && unallocated > 0)
    return `${action}已登记为预付款 ${money(unallocated)} 元，待入库产生应付后冲抵`
  if (unallocated > 0)
    return `${action}已登记：已核销 ${money(allocated)} 元，未分配 ${money(unallocated)} 元（待对账确认后核销）`
  return `${action}已登记并核销 ${money(allocated)} 元`
}
async function submitBusiness() {
  try {
    if (operation.value === '付款') {
      const row = payment.value!
      if (!row.amount || row.amount <= 0) throw Error('请填写付款金额')
      if (row.amount > payable.value + 0.005) throw Error('付款金额不能超过订单可付金额')
      const result = await financeApi.settleOrder({
        requestKey: paymentRequestKey.value,
        direction: 'AP',
        orderId: Number(row.orderId),
        paymentDate: row.date,
        amount: row.amount,
        bankReference: row.bankReference || '',
        remark: [row.method, row.note].filter(Boolean).join('；'),
      })
      operation.value = ''
      ElMessage.success(settlementMessage('付款', result))
      await refresh()
      return
    }
    store.returnGoods(returned.value!)
    operation.value = ''
    ElMessage.success('已保存')
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}
function download(name: string, rows: unknown[][]) {
  const url = URL.createObjectURL(new Blob([encodeCsv(rows)], { type: 'text/csv;charset=utf-8' }))
  const a = document.createElement('a')
  a.href = url
  a.download = name
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
const exporting = ref(false)
async function exportData() {
  if (exporting.value) return
  exporting.value = true
  try {
    await downloadExport(isOrder ? 'purchase-orders' : 'inbound-orders', {
      keyword: keyword.value, tab: tab.value, purchase: orderFilters.value,
      selectedIds: [], columns: shownColumns.value.map(c => c.key),
      sortKey: sortKey.value, sortDirection: sortDirection.value,
      filterOpen: filterOpen.value, filterKey: filterKey.value,
      filterOperator: filterOperator.value, filterValue: filterValue.value,
    }, `${isOrder ? '采购订单' : '产品入库'}-${today()}`)
    ElMessage.success('导出成功')
  } catch (e) { ElMessage.error((e as Error).message) }
  finally { exporting.value = false }
}
const importOpen = ref(false),
  importRows = ref<Record<string, string>[]>([]),
  importError = ref(''),
  fileName = ref('')
const importHeaders = isOrder
  ? [
      '采购订单名称',
      '供应商编码',
      '签订日期',
      '交付日期',
      '入库仓库',
      '产品编码',
      '采购数量',
      '含税单价',
      '优惠金额',
    ]
  : [
      '采购订单编号',
      '入库仓库',
      '产品编码',
      '原厂日期码',
      '原厂批次号',
      '到货数量',
      '不合格数量',
      '入库仓位',
      '质检员',
      '质检时间',
    ]
function downloadTemplate() {
  download(`${isOrder ? '采购订单' : '产品入库'}导入模板.csv`, [
    importHeaders,
    isOrder
      ? ['测试采购订单', 'GYS002', today(), today(), '杭州仓', 'CP001', 100, 8.5, 0]
      : [
          store.state.orders.find(
            (o) =>
              o.lines.some((l) => remainingQuantity(store.state, o.id, l.id) > 0) &&
              o.orderStatus === '执行中' && o.receiptStatus !== '全部收货'
          )?.code || '请填写订单编号',
          '杭州仓',
          'CP001',
          '2519',
          'L2519A001',
          1,
          0,
          'HZ-A01',
          '李敏',
          today() + ' 09:00',
        ],
  ])
}
async function readImport(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  importRows.value = []
  importError.value = ''
  if (!file) return
  fileName.value = file.name
  try {
    if (file.size > 2 * 1024 * 1024) throw Error('导入文件不能超过 2MB')
    const rows = parseCsv(await file.text())
    const head = rows.shift() || []
    if (importHeaders.some((h) => !head.includes(h))) throw Error('列名不完整，请下载导入模板填写')
    const data = rows.filter((r) => r.some((v) => v.trim()))
    if (!data.length || data.length > 500) throw Error('每次导入 1 至 500 行')
    importRows.value = data.map((r) =>
      Object.fromEntries(head.map((h, i) => [h, r[i]?.trim() || ''])),
    )
  } catch (e) {
    importError.value = (e as Error).message
  }
}
function confirmImport() {
  try {
    if (!importRows.value.length) throw Error('请先选择有效文件')
    if (isOrder) {
      const result: PurchaseOrder[] = []
      for (const r of importRows.value) {
        const s = suppliers.find((s) => s.code === r['供应商编码']),
          p = products.find((p) => p.code === r['产品编码'])
        if (!s || !p) throw Error('供应商编码或产品编码不存在')
        let o = result.find((o) => o.name === r['采购订单名称'])
        if (!o) {
          o = {
            id: crypto.randomUUID(),
            code: '',
            name: r['采购订单名称']!,
            supplierId: s.id,
            signed: r['签订日期']!,
            delivery: r['交付日期']!,
            warehouse: r['入库仓库']!,
            owner: '张睿',
            department: '采购部',
            address: '',
            discount: Number(r['优惠金额']),
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
          }
          result.push(o)
        }
        if (
          o.supplierId !== s.id ||
          o.warehouse !== r['入库仓库'] ||
          o.signed !== r['签订日期'] ||
          o.delivery !== r['交付日期'] ||
          o.discount !== Number(r['优惠金额'])
        )
          throw Error('同名订单的基础信息必须一致')
        o.lines.push({
          id: crypto.randomUUID(),
          productId: p.id,
          quantity: Number(r['采购数量']),
          price: r['含税单价'] === '' ? p.price : Number(r['含税单价']),
          tax: p.tax,
        })
      }
      store.importOrders(result)
    } else {
      const result: PurchaseReceipt[] = []
      for (const r of importRows.value) {
        const o = store.state.orders.find((o) => o.code === r['采购订单编号']),
          p = products.find((p) => p.code === r['产品编码']),
          line = o?.lines.find((l) => l.productId === p?.id)
        if (!o || !line) throw Error('订单编号或对应产品不存在')
        let receipt = result.find((t) => t.orderId === o.id)
        if (!receipt) {
          receipt = {
            id: crypto.randomUUID(),
            code: '',
            orderId: o.id,
            warehouse: r['入库仓库']!,
            lines: [],
            quality: '合格',
            qualityNote: '',
            inspector: r['质检员']!,
            inspectionDepartment: '质量部',
            inspectedAt: r['质检时间']!,
            confirmed: false,
            receivedAt: '',
            keeper: '陈晨',
            department: '仓储部',
            photos: [],
          }
          result.push(receipt)
        }
        if (
          receipt.warehouse !== r['入库仓库'] ||
          receipt.inspector !== r['质检员'] ||
          receipt.inspectedAt !== r['质检时间']
        )
          throw Error('同一入库单的仓库及质检信息必须一致')
        receipt.lines.push({
          lineId: line.id,
          arrived: Math.max(0, Number(r['到货数量']) - Number(r['不合格数量'])),
          rejected: Number(r['不合格数量']),
          location: r['入库仓位']!,
          batches: [
            {
              id: crypto.randomUUID(),
              dateCode: r['原厂日期码']!,
              lotCode: r['原厂批次号']!,
              quantity: Math.max(0, Number(r['到货数量']) - Number(r['不合格数量'])),
            },
          ],
        })
        if (Number(r['不合格数量']) > 0) receipt.quality = '存在不合格品'
      }
      store.importReceipts(result)
    }
    importOpen.value = false
    ElMessage.success('导入成功')
  } catch (e) {
    importError.value = (e as Error).message
  }
}
</script>

<template>
  <section class="purchase-page" :class="{ 'purchase-expanded': expanded }">
    <nav class="purchase-view-tabs" aria-label="采购数据视图">
      <button
        v-for="name in tabs"
        :key="name"
        :class="{ active: tab === name }"
        :aria-selected="tab === name"
        @click="tab = name"
      >
        <el-icon><Document /></el-icon>{{ name }}
      </button>
    </nav>
    <div class="purchase-toolbar">
      <div class="purchase-toolbar-actions">
        <el-button type="primary" :icon="Plus" @click="open()">添加</el-button
        ><el-button
          text
          :icon="Upload"
          @click="
            () => {
              importOpen = true
              importRows = []
              importError = ''
              fileName = ''
            }
          "
          >导入</el-button
        ><el-button text :icon="Download" :loading="exporting" :disabled="store.loading || !!store.loadError" @click="exportData">导出</el-button
        >
      </div>
      <div class="purchase-toolbar-search">
        <el-button
          :icon="Filter"
          :type="(isOrder ? activeFilterCount > 0 : filterOpen) ? 'primary' : ''"
          plain
          @click="openFilters"
          >筛选{{ isOrder && activeFilterCount ? `（${activeFilterCount}）` : "" }}</el-button
        >
        <el-popover trigger="click" width="220"
          ><template #reference
            ><el-button :icon="Setting" aria-label="显示字段" title="显示字段" /></template
          ><strong>显示字段</strong>
          <div class="purchase-column-options">
            <el-checkbox
              v-for="c in columns"
              :key="c.key"
              :model-value="!hidden.includes(c.key)"
              :disabled="shownColumns.length === 1 && !hidden.includes(c.key)"
              @change="
                () => {
                  hidden = hidden.includes(c.key)
                    ? hidden.filter((k) => k !== c.key)
                    : [...hidden, c.key]
                }
              "
              >{{ c.label }}</el-checkbox
            >
          </div>
          <el-button text type="primary" @click="hidden = []">恢复默认</el-button></el-popover
        >

        <el-dropdown trigger="click" @command="changeGrouping">
          <el-button :icon="Operation" :type="grouped ? 'primary' : ''" plain :aria-label="isOrder ? '采购订单分组方式' : '产品入库分组方式'">{{ groupMode === 'month' ? '按月份分组' : groupMode === 'supplier' ? '按供应商分组' : '不分组' }}</el-button>
          <template #dropdown><el-dropdown-menu>
            <el-dropdown-item command="month">{{ isOrder ? '按采购日期月份分组' : '按入库时间月份分组' }}</el-dropdown-item>
            <el-dropdown-item command="supplier">按供应商分组</el-dropdown-item>
            <el-dropdown-item command="none">不分组</el-dropdown-item>
          </el-dropdown-menu></template>
        </el-dropdown>
        <el-button :icon="Refresh" text aria-label="刷新采购数据" @click="refresh" /><el-button
          :icon="FullScreen"
          text
          aria-label="切换表格全屏"
          @click="expanded = !expanded"
        />
      </div>
    </div>
    <div v-if="isOrder && activeFilterCount" class="purchase-active-filters"><span>已应用 {{ activeFilterCount }} 项筛选条件</span><el-button text type="primary" @click="orderFilters = emptyPurchaseOrderFilters(); collapsed = []">清空筛选</el-button></div>
    <div v-if="!isOrder && filterOpen" class="purchase-filters">
      <el-select v-model="filterKey" aria-label="筛选字段"
        ><el-option v-for="c in columns" :key="c.key" :value="c.key" :label="c.label" /></el-select
      ><el-select v-model="filterOperator" aria-label="筛选方式"
        ><el-option
          v-for="n in ['包含', '不包含', '等于', '不等于', '为空', '不为空']"
          :key="n"
          :value="n" /></el-select
      ><el-input
        v-if="!['为空', '不为空'].includes(filterOperator)"
        v-model="filterValue"
        placeholder="请输入筛选内容"
        clearable
      /><el-button
        @click="
          () => {
            filterValue = ''
            filterOperator = '包含'
            filterOpen = false
          }
        "
        >重置</el-button
      >
    </div>
    <div
      class="purchase-table-scroll"
      :class="{ 'purchase-comfortable': rowHeight === 'comfortable' }"
    >
      <table class="purchase-table">
        <thead class="purchase-order-head">
          <tr>
            <template v-for="c in shownColumns" :key="c.key">
              <th v-if="!productKeys.includes(c.key)" :class="{ 'purchase-supplier-compact': c.key === 'supplier', 'purchase-code-compact': ['code', 'orderCode'].includes(c.key) }" :rowspan="shownColumns.some(c => productKeys.includes(c.key)) ? 2 : 1" :style="{minWidth: c.key === 'supplier' ? 'auto' : ['code','orderCode'].includes(c.key) ? '140px' : c.key === 'phone' ? '150px' : '120px'}">{{ c.label }}</th>
              <th v-else-if="c.key === shownColumns.find(c => productKeys.includes(c.key))?.key" :colspan="shownColumns.filter(c => productKeys.includes(c.key)).length" class="purchase-product-heading">{{ isOrder ? "采购产品明细" : "入库产品明细" }}</th>
            </template>
            <th class="purchase-fixed-actions" :class="{ 'purchase-order-actions': isOrder, 'purchase-receipt-actions': !isOrder }" :rowspan="shownColumns.some(c => productKeys.includes(c.key)) ? 2 : 1">操作</th>
          </tr>
          <tr v-if="shownColumns.some(c => productKeys.includes(c.key))">
            <th v-for="c in shownColumns.filter(c => productKeys.includes(c.key))" :key="c.key" :class="{ 'purchase-packaging-compact': c.key === 'models' }" :style="{minWidth: c.key === 'brands' ? '65px' : c.key === 'models' ? '160px' : c.key === 'productNames' ? '85px' : c.key === 'remarks' ? '190px' : '120px'}">{{ c.label }}</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="group in groups" :key="group.name">
            <tr v-if="grouped" class="purchase-group">
              <td :colspan="shownColumns.length + 1">
                <button
                  :aria-expanded="!collapsed.includes(group.name)"
                  @click="toggleGroup(group.name)"
                >
                  <span>{{ collapsed.includes(group.name) ? '▸' : '▾' }}</span
                  >{{ group.name }}<small>{{ group.rows.length }}</small>
                </button>
              </td>
            </tr>
            <template v-if="!grouped || !collapsed.includes(group.name)"
              ><tr
                v-for="row in group.rows"
                :key="row.id"
                @dblclick="open(row, true)"
              >
                <td
                  v-for="c in shownColumns"
                  :key="c.key"
                  :title="c.key === 'supplier' ? purchaseSupplierName(row.order) : String(display(row, c.key) || '')"
                  :class="{
                    'purchase-supplier-compact': c.key === 'supplier',
                    'purchase-code-compact': ['code', 'orderCode'].includes(c.key),
                    'purchase-packaging-compact': c.key === 'models',
                    'purchase-product-td': productKeys.includes(c.key),
                    'purchase-center-cell': ['receivedAt', 'signed', 'receiptStatus'].includes(c.key),
                    'purchase-money': ['gross', 'discount', 'total', 'net', 'quantity'].includes(
                      c.key,
                    ),
                  }"
                >
                  <span v-if="c.key === 'supplier'">{{ row.order?.supplierShortName || purchaseSupplierName(row.order) || '—' }}</span>
                  <div v-else-if="row.receipt && c.key === 'orderCode'" class="inbound-purchase-links">
                    <button v-for="order in receiptOrders(row.receipt, store.state.orders)" :key="order.id" class="purchase-record-link" @click.stop="openAllocation(row, order)" @dblclick.stop>{{ order.code }}</button>
                  </div>
                  <div v-else-if="row.receipt && productKeys.includes(c.key)" class="purchase-product-cell">
                    <div v-for="(value,index) in receiptCells(row.receipt, c.key)" :key="index" :title="c.key === 'models' ? value : undefined">{{ value }}</div>
                  </div>
                  <div v-else-if="row.order && productKeys.includes(c.key)" class="purchase-product-cell">
                    <div v-for="(value,index) in productCells(row.order, c.key, row.receipt)" :key="index" :title="c.key === 'models' ? value : undefined">{{ value }}</div>
                  </div>
                  <span
                    v-else-if="['name', 'code', 'orderCode'].includes(c.key)"
                    class="purchase-order-code"
                  >
                    <button
                      class="purchase-record-link"
                      @click="open(row, true)"
                    >
                      {{ display(row, c.key) }}</button
                    ><el-tooltip
                      v-if="isOrder && c.key === 'code' && row.order && row.order.orderStatus !== '执行中'"
                      :content="row.order.orderStatus"
                      placement="top"
                    >
                      <span
                        class="order-status-badge"
                        :class="orderStatusBadge(row.order.orderStatus).className"
                        tabindex="0"
                        :aria-label="row.order.orderStatus"
                        >{{ orderStatusBadge(row.order.orderStatus).text }}</span
                      >
                    </el-tooltip>
                  </span>
                  <el-tag
                    v-else-if="['receiptStatus', 'status'].includes(c.key)"
                    size="small"
                    :type="statusTagType(c.key, String(row.values[c.key] ?? ''))"
                    >{{ row.values[c.key] }}</el-tag
                  ><span v-else>{{ display(row, c.key) || '—' }}</span>
                </td>
                <td class="purchase-fixed-actions" :class="{ 'purchase-order-actions': isOrder, 'purchase-receipt-actions': !isOrder }">
                  <template v-if="isOrder">
                    <el-tooltip content="付款" placement="top">
                      <span class="action-icon">
                        <el-button class="action-icon-btn purchase-pay-icon" circle text :icon="Money"
                          aria-label="付款" :disabled="row.order?.orderStatus !== '执行中'"
                          @click="business(row.order!, '付款')" />
                      </span>
                    </el-tooltip>
                    <el-tooltip content="入库" placement="top">
                      <span class="action-icon">
                        <el-button class="action-icon-btn purchase-receive-icon" circle text :icon="TakeawayBox" aria-label="入库"
                          :disabled="row.order?.orderStatus !== '执行中' || !row.order?.lines.some((l) => pendingDeliveryQuantity(row.order!, l, store.state.receipts) > 0)"
                          @click="receive(row.order!)" />
                      </span>
                    </el-tooltip>
                    <el-tooltip content="取消订单" placement="top">
                      <span class="action-icon">
                        <el-button class="action-icon-btn purchase-cancel-icon" circle text :icon="CircleCloseFilled"
                          aria-label="取消订单" :disabled="!canCancelOrder(row.order!)" @click="cancelOrder(row.order!)" />
                      </span>
                    </el-tooltip>
                  </template>
                  <el-tooltip v-else content="撤销入库" placement="top">
                    <span class="action-icon">
                      <el-button class="action-icon-btn purchase-reverse-icon" circle text :icon="RefreshLeft"
                        aria-label="撤销入库" :disabled="!row.receipt?.confirmed"
                        @click="row.receipt && reverseReceipt(row.receipt)" />
                    </span>
                  </el-tooltip>
                </td>
              </tr></template
            >
          </template>
          <tr v-if="!filtered.length">
            <td :colspan="shownColumns.length + 1">
              <el-empty description="暂无符合条件的数据" />
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <footer class="purchase-table-footer">
<el-button v-if="grouped" text @click="collapsed = []">⌄ 展开全部</el-button
      ><el-button v-if="grouped" text @click="collapsed = groups.map((g) => g.name)"
        >⌃ 收起全部</el-button
      ><span class="purchase-footer-hint">单击名称查看详情</span>
    <el-pagination class="list-pagination" v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10,20,50,100]" :total="filtered.length" layout="total, sizes, prev, pager, next, jumper" /></footer>
  </section>
  <el-dialog v-if="isOrder" v-model="orderFilterOpen" title="采购订单筛选" width="min(900px,94vw)" top="5vh" append-to-body class="purchase-order-filter-dialog">
    <p class="purchase-filter-tip">填写的条件同时生效；留空表示不限。产品条件匹配同一条产品明细。</p>
    <el-form label-position="top" @submit.prevent="applyOrderFilters">
      <h3>采购单信息</h3>
      <div class="purchase-filter-grid">
        <el-form-item label="采购单号"><el-input v-model="draftFilters.code" aria-label="筛选采购单号" placeholder="包含关键词" clearable /></el-form-item>
        <el-form-item label="供应商名称"><el-select v-model="draftFilters.supplier" filterable clearable placeholder="全部供应商" aria-label="筛选供应商名称"><el-option v-for="name in filterSuppliers" :key="name" :label="name" :value="name" /></el-select></el-form-item>
        <el-form-item label="付款状态"><el-select v-model="draftFilters.paymentStatus" clearable placeholder="不限" aria-label="筛选付款状态"><el-option v-for="status in ['未付款','部分付款','已付款']" :key="status" :label="status" :value="status" /></el-select></el-form-item>
        <el-form-item label="订单状态"><el-select v-model="draftFilters.orderStatus" clearable placeholder="不限" aria-label="筛选订单状态"><el-option v-for="status in ['待审核','执行中','已完结','已取消']" :key="status" :label="status" :value="status" /></el-select></el-form-item>
        <el-form-item label="收货状态"><el-select v-model="draftFilters.receiptStatus" clearable placeholder="不限" aria-label="筛选收货状态"><el-option v-for="status in ['未收货','部分收货','全部收货']" :key="status" :label="status" :value="status" /></el-select></el-form-item>
        <el-form-item label="订单日期"><el-date-picker v-model="draftFilters.dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" aria-label="筛选订单日期" /></el-form-item>
      </div>
      <h3>产品信息</h3>
      <div class="purchase-filter-grid"><el-form-item v-for="field in productTextFilters" :key="field.key" :label="field.label"><el-input v-model="draftFilters.product[field.key]" :aria-label="'筛选' + field.label" placeholder="包含关键词" clearable /></el-form-item></div>
    </el-form>
    <template #footer><el-button @click="draftFilters = emptyPurchaseOrderFilters()">重置条件</el-button><el-button @click="orderFilterOpen = false">取消</el-button><el-button type="primary" @click="applyOrderFilters">应用筛选</el-button></template>
  </el-dialog>
  <PurchaseRecordDialog
    v-if="editor && (editorKind === 'orders' || singleReceipt)"
    :key="editorVersion"
    :kind="editorKind"
    :order="editingOrder"
    :receipt="editingReceipt"
    :related-order="relatedOrder"
    :readonly="readonly"
    @close="editor = false"
    @saved="saved"
    @receive="receive"
  />
  <PurchaseInboundHistoryDialog v-if="editor && editorKind === 'receipts' && editingReceipt && scopeOrder" :key="editorVersion" :order="scopeOrder" :current-receipt-id="editingReceipt.id" @close="editor = false" />
  <InboundReceiptDialog v-else-if="editor && editorKind === 'receipts' && editingReceipt" :key="editorVersion" :receipt="editingReceipt" @close="editor = false" @saved="saved" />
  <MultiOrderInboundDialog v-if="editor && editorKind === 'receipts' && !singleReceipt && !editingReceipt" :key="editorVersion" @close="editor = false" @saved="saved" />
  <el-drawer v-model="logsOpen" title="操作记录" size="min(600px,95vw)" class="purchase-dialog"
    ><el-empty v-if="!store.state.logs.length" description="暂无操作记录" /><el-timeline v-else
      ><el-timeline-item v-for="log in store.state.logs" :key="log.id" :timestamp="log.time"
        ><strong>{{ log.action }}</strong>
        <p>{{ log.name }}</p></el-timeline-item
      ></el-timeline
    ></el-drawer
  >
  <el-dialog
    :model-value="!!operation"
    :title="'采购' + operation"
    width="min(550px,94vw)"
    class="purchase-dialog"
    @close="operation = ''"
  >
    <p>{{ activeOrder?.name }}</p>
    <el-form v-if="operation === '付款' && payment" label-position="top"
      ><div class="purchase-payment-summary">
        <div><span>订单金额</span><strong>¥ {{ money(orderAmount) }}</strong></div>
        <div><span>已登记付款</span><strong>¥ {{ money(paidAmount) }}</strong></div>
        <div><span>未结应付</span><strong>¥ {{ money(unpaid) }}</strong></div>
        <div><span>可付金额</span><strong>¥ {{ money(payable) }}</strong></div>
      </div>
      <p v-if="financeOutstandingLoading" class="purchase-tip">应付金额加载中…</p>
      <p v-if="!financeOutstandingLoading && unpaid <= 0 && payable > 0" class="prepayment-hint">
        当前订单还没有入库应付，本次付款将登记为预付款，待入库产生应付后冲抵。
      </p>
      <p v-if="Number(activeOrder?.prepaymentAmount ?? 0) > 0" class="prepayment-hint">
        本订单已有未分配预付款 ¥ {{ money(Number(activeOrder?.prepaymentAmount ?? 0)) }}，可先在财务收付款中核销，避免重复付款。
      </p>
      <el-form-item label="付款金额/元" required
        ><el-input-number
          v-model="payment.amount"
          :min="0"
          :max="Math.max(0, payable)"
          :precision="2" /></el-form-item
      ><el-form-item label="付款日期" required
        ><el-date-picker v-model="payment.date" value-format="YYYY-MM-DD" /></el-form-item
      ><el-form-item label="付款方式"
        ><el-select v-model="payment.method"
          ><el-option
            v-for="n in ['银行转账', '现金', '承兑汇票']"
            :key="n"
            :value="n" /></el-select></el-form-item
      ><el-form-item label="银行流水号"
        ><el-input
          v-model="payment.bankReference"
          maxlength="128"
          placeholder="银行回单号/流水号"
      /></el-form-item
      ><el-form-item label="备注"><el-input v-model="payment.note" type="textarea" /></el-form-item
    ></el-form>
    <el-form v-else-if="returned && activeOrder" label-position="top"
      ><el-form-item label="退货产品" required
        ><el-select v-model="returned.lineId"
          ><el-option
            v-for="line in activeOrder.lines"
            :key="line.id"
            :value="line.id"
            :label="line.model || products.find((p) => p.id === line.productId)?.name"
            :disabled="returnableQuantity(store.state, activeOrder.id, line.id) <= 0" /></el-select
      ></el-form-item>
      <p>可退数量：{{ returnableQuantity(store.state, activeOrder.id, returned.lineId) }}</p>
      <el-form-item label="退货数量" required
        ><el-input-number
          v-model="returned.quantity"
          :min="1"
          :max="Math.max(1, returnableQuantity(store.state, activeOrder.id, returned.lineId))"
          :precision="0" /></el-form-item
      ><el-form-item label="退货日期"
        ><el-date-picker v-model="returned.date" value-format="YYYY-MM-DD" /></el-form-item
      ><el-form-item label="退货原因" required
        ><el-input v-model="returned.reason" type="textarea" /></el-form-item
    ></el-form>
    <template #footer
      ><el-button @click="operation = ''">取消</el-button
      ><el-button type="primary" @click="submitBusiness">提交</el-button></template
    >
  </el-dialog>
  <el-dialog
    v-model="importOpen"
    :title="'导入' + (isOrder ? '采购订单' : '产品入库')"
    width="min(850px,94vw)"
    class="purchase-dialog"
    destroy-on-close
  >
    <p>
      使用 UTF-8 CSV 模板，每行一条产品明细。{{
        isOrder ? '同名订单会合并，导入后进入执行中。' : '同一订单会合并，导入后进入待入库。'
      }}
    </p>
    <el-button :icon="Download" @click="downloadTemplate">下载导入模板</el-button
    ><label class="purchase-import-file"
      >选择 CSV 文件<input type="file" accept=".csv" @change="readImport"
    /></label>
    <p>
      {{ fileName }} <span v-if="importRows.length">· 共 {{ importRows.length }} 行</span>
    </p>
    <el-alert v-if="importError" :title="importError" type="error" :closable="false" /><el-table
      v-if="importRows.length"
      :data="importRows.slice(0, 10)"
      max-height="300"
      border
      ><el-table-column v-for="h in importHeaders" :key="h" :prop="h" :label="h" min-width="130"
    /></el-table>
    <p v-if="importRows.length > 10">仅预览前 10 行，提交时导入全部数据。</p>
    <template #footer
      ><el-button @click="importOpen = false">取消</el-button
      ><el-button type="primary" :disabled="!importRows.length" @click="confirmImport"
        >确认导入</el-button
      ></template
    >
  </el-dialog>
</template>

<style>
.purchase-product-td { padding: 0 !important; text-align: left; }
.inbound-purchase-links { display: flex; flex-direction: column; align-items: flex-start; gap: 8px; padding: 4px 0; }
.purchase-product-cell > div { min-height: 35px; padding: 8px 10px; box-sizing: border-box; white-space: nowrap; }
.purchase-product-cell > div + div { border-top: 1px solid #eef0f3; }
.purchase-table .purchase-packaging-compact { width: 160px; max-width: 160px; }
.purchase-table .purchase-code-compact { width: 1%; padding-left: 8px; padding-right: 8px; }
.purchase-table td.purchase-center-cell { text-align: center; }
.purchase-order-code { display: inline-flex; align-items: center; justify-content: flex-start; gap: 4px; }
.purchase-packaging-compact .purchase-product-cell { width: 160px; }
.purchase-packaging-compact .purchase-product-cell > div { overflow: hidden; text-overflow: ellipsis; }
.purchase-table tbody > tr:nth-child(even of :not(.purchase-group)) > td {
  background: #eef2f6;
}
.purchase-table tbody > tr:nth-child(even of :not(.purchase-group)):hover > td {
  background: #f4fbfa;
}
</style>

<style>
.purchase-order-head tr:first-child { height: 37px; }
.purchase-order-head th { text-align: center; }
.purchase-order-head tr:nth-child(2) th { top: 37px; }
.purchase-order-head tr:nth-child(2) th { text-align: center; white-space: nowrap; }
.purchase-order-head .purchase-product-heading { text-align: center; }
</style>

<style>
.purchase-filter-grid { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 0 18px; }
.purchase-order-filter-dialog .el-form-item { margin-bottom: 14px; }
.purchase-order-filter-dialog h3 { font-size: 14px; margin: 12px 0; }
.purchase-filter-tip { color: #8491a4; font-size: 13px; margin: 0 0 16px; }
.purchase-filter-grid .el-date-editor { width: 100%; min-width: 0; }
.purchase-active-filters { display: flex; align-items: center; gap: 12px; padding: 0 16px 8px; font-size: 13px; color: #65738a; }
@media(max-width: 700px) { .purchase-filter-grid { grid-template-columns: 1fr; } }
</style>
