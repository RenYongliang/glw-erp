<script setup lang="ts">
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import type { TableInstance, TableColumnCtx } from 'element-plus'
import { FullScreen, Refresh, Download, Sort, ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import BoardTextFilter from '../components/boards/BoardTextFilter.vue'
import BoardDateFilter from '../components/boards/BoardDateFilter.vue'
import TrendChart from '../components/TrendChart.vue'
import {
  boardData,
  orderMetric,
  executionTag,
  money,
  type BoardKind,
  type BoardOrder,
  type ExecutionLine,
} from '../domain/orderBoards'
import { emptyTextFilter, emptyDateFilter, matchesText, matchesDate } from '../domain/boardFilters'

const smallScreen = ref(false)
const media = window.matchMedia('(max-width: 700px)')
function resizeLayout() {
  smallScreen.value = media.matches
}
onMounted(() => {
  resizeLayout()
  media.addEventListener('change', resizeLayout)
})
onBeforeUnmount(() => media.removeEventListener('change', resizeLayout))
const props = defineProps<{ kind: BoardKind }>()
const purchase = computed(() => props.kind === 'purchase')
const prefix = computed(() => (purchase.value ? '采购' : '销售'))
const title = computed(() => `${prefix.value}订单看板`)
const partnerLabel = computed(() => (purchase.value ? '供应商' : '客户名称'))
const executionTitle = computed(() =>
  purchase.value ? '采购订单-入库-退货-对账执行统计' : '销售订单-出库-退换货-对账执行统计',
)
const tab = ref('statistics')
const approval = ref('通过')
const partner = ref(emptyTextFilter())
const owner = ref(emptyTextFilter())
const warehouse = ref(emptyTextFilter())
const date = ref(emptyDateFilter())
const executionPartner = ref(emptyTextFilter())
const orderName = ref(emptyTextFilter())
const tag = ref('')
const page = ref(1)
const pageSize = ref(20)
const executionPage = ref(1)
const sort = ref({ prop: 'signed', order: 'descending' })
const detailsId = ref('')
const drawerFullscreen = ref(false)
const statsTable = ref<TableInstance>()
const executionTable = ref<TableInstance>()
const sortVisible = ref(false)
const sortField = ref('signed')
const sortDirection = ref('descending')
const sortOptions = computed(() =>
  purchase.value
    ? [
        { value: 'partner', label: '供应商' },
        { value: 'name', label: '采购订单名称' },
        { value: 'amount', label: '订单金额' },
        { value: 'signed', label: '订单签订日期' },
        { value: 'delivery', label: '订单交付日期' },
        { value: 'owner', label: '采购负责人' },
      ]
    : [
        { value: 'id', label: '订单编号' },
        { value: 'signed', label: '订单日期' },
        { value: 'partner', label: '客户名称' },
        { value: 'amount', label: '订单金额' },
        { value: 'paymentTerm', label: '账期' },
        { value: 'owner', label: '销售负责人' },
        { value: 'delivery', label: '交付日期' },
      ],
)
function applySort() {
  statsTable.value?.sort(
    sortField.value === 'amount' ? 'order.amount' : sortField.value,
    sortDirection.value,
  )
  sortVisible.value = false
}
function refreshTable() {
  statsTable.value?.doLayout()
  executionTable.value?.doLayout()
  ElMessage.success('数据已刷新')
}
const tablePanel = ref<HTMLElement>()
const fullTable = ref(false)
const orders = computed(() => boardData[props.kind].orders)
const options = computed(() => ({
  partners: [...new Set(orders.value.map((o) => o.partner))],
  owners: [...new Set(orders.value.map((o) => o.owner))],
  warehouses: [...new Set(orders.value.map((o) => o.warehouse).filter(Boolean))],
  names: orders.value.map((o) => o.name),
}))
const filteredOrders = computed(() =>
  orders.value.filter(
    (order) =>
      (!purchase.value || !approval.value || order.approval === `审批${approval.value}`) &&
      matchesText(order.partner, partner.value) &&
      matchesText(order.owner, owner.value) &&
      matchesText(order.warehouse, warehouse.value) &&
      matchesDate(order.signed, date.value),
  ),
)
const sortedOrders = computed(() => {
  const list = [...filteredOrders.value]
  if (!sort.value.order) return list
  const key = sort.value.prop as keyof BoardOrder
  return list.sort((a, b) => {
    const x = a[key],
      y = b[key]
    const result =
      typeof x === 'number' && typeof y === 'number'
        ? x - y
        : String(x).localeCompare(String(y), 'zh-CN')
    return sort.value.order === 'ascending' ? result : -result
  })
})
const statistics = computed(() => ({
  amount: filteredOrders.value.reduce((sum, order) => sum + orderMetric(order, props.kind), 0),
  partners: new Set(filteredOrders.value.map((order) => order.partner)).size,
  count: filteredOrders.value.length,
}))
const trend = computed(() => {
  const groups = new Map<string, number>()
  for (const order of filteredOrders.value) {
    const month = order.signed.slice(0, 7)
    groups.set(month, (groups.get(month) ?? 0) + orderMetric(order, props.kind))
  }
  const months = [...groups.keys()].sort()
  return {
    labels: months.map((month) => month.replace('-', '年') + '月'),
    values: months.map((month) => Number(groups.get(month)!.toFixed(2))),
  }
})
const statsRows = computed(() =>
  sortedOrders.value
    .slice((page.value - 1) * pageSize.value, page.value * pageSize.value)
    .flatMap((order) =>
      order.lines.map((line, index) => ({
        ...order,
        ...line,
        order,
        plan: order.plans[index],
        span: index === 0 ? order.lines.length : 0,
      })),
    ),
)
type StatsRow = (typeof statsRows.value)[number]
const filteredExecution = computed(() =>
  boardData[props.kind].execution.filter(
    (line) =>
      matchesText(line.partner, executionPartner.value) &&
      matchesText(line.name, orderName.value) &&
      executionTag(line, tag.value),
  ),
)
const executionRows = computed(() => {
  const lines = filteredExecution.value.slice(
    (executionPage.value - 1) * 50,
    executionPage.value * 50,
  )
  return lines.map((line, index) => ({
    ...line,
    span:
      index && lines[index - 1]?.id === line.id
        ? 0
        : lines.slice(index).findIndex((item) => item.id !== line.id) === -1
          ? lines.length - index
          : lines.slice(index).findIndex((item) => item.id !== line.id),
  }))
})
const tags = computed(() =>
  purchase.value
    ? ['待入库', '待对账', '待付款', '已付款']
    : ['待出库', '待对账', '待收款', '已收款'],
)
const executionColumns = computed(() => {
  const cols = [
    { prop: 'id', label: `${prefix.value}订单编号`, width: 155 },
    { prop: 'name', label: `${prefix.value}订单名称`, width: 180 },
    { prop: 'partner', label: purchase.value ? '供应商名称' : '客户名称', width: 145 },
    { prop: 'product', label: '产品名称', width: 135 },
    { prop: 'code', label: '产品编号', width: 95 },
    { prop: 'quantity', label: `${prefix.value}数量`, width: 95 },
    { prop: 'returnedBefore', label: `未${purchase.value ? '入' : '出'}库-退货数量`, width: 140 },
    { prop: 'shipped', label: `${purchase.value ? '入' : '出'}库数量`, width: 95 },
    { prop: 'returnedAfter', label: `已${purchase.value ? '入' : '出'}库-退货数量`, width: 140 },
  ]
  if (!purchase.value) cols.push({ prop: 'exchanged', label: '换货数量', width: 95 })
  cols.push({ prop: 'pending', label: `待${purchase.value ? '入' : '出'}库数量`, width: 110 })
  if (!purchase.value)
    cols.push(
      { prop: 'frozen', label: '库存冻结数量', width: 115 },
      { prop: 'planned', label: '计划出库数量', width: 115 },
      { prop: 'unplanned', label: '待规划出库数量', width: 125 },
    )
  cols.push(
    { prop: 'settled', label: '已对账数量', width: 110 },
    { prop: 'unsettled', label: '待对账数量', width: 110 },
    { prop: 'payable', label: `应${purchase.value ? '付' : '收'}金额/元`, width: 135 },
    { prop: 'paid', label: `${purchase.value ? '付' : '收'}款金额/元`, width: 135 },
    { prop: 'unpaid', label: `待${purchase.value ? '付' : '收'}款金额/元`, width: 135 },
  )
  return cols
})
const detailOrders = computed(() =>
  tab.value === 'statistics'
    ? sortedOrders.value
    : orders.value.filter((order) => filteredExecution.value.some((line) => line.id === order.id)),
)
const detailIndex = computed(() =>
  detailOrders.value.findIndex((order) => order.id === detailsId.value),
)
const detail = computed(() => detailOrders.value[detailIndex.value])
const detailsOpen = computed({
  get: () => Boolean(detail.value),
  set: (value) => {
    if (!value) detailsId.value = ''
  },
})
function resetStatistics() {
  approval.value = '通过'
  partner.value = emptyTextFilter()
  owner.value = emptyTextFilter()
  warehouse.value = emptyTextFilter()
  date.value = emptyDateFilter()
  page.value = 1
}
function resetExecution() {
  executionPartner.value = emptyTextFilter()
  orderName.value = emptyTextFilter()
  tag.value = ''
  executionPage.value = 1
}
watch(
  [partner, owner, warehouse, date, approval, pageSize, sort],
  () => {
    page.value = 1
    statsTable.value?.setScrollTop(0)
  },
  { deep: true },
)
watch(
  [executionPartner, orderName, tag],
  () => {
    executionPage.value = 1
    executionTable.value?.setScrollTop(0)
  },
  { deep: true },
)
watch(page, () => statsTable.value?.setScrollTop(0))
watch(executionPage, () => executionTable.value?.setScrollTop(0))
watch(
  () => props.kind,
  () => {
    tab.value = 'statistics'
    resetStatistics()
    resetExecution()
    detailsId.value = ''
    sort.value = { prop: 'signed', order: 'descending' }
  },
)
function statsSpan({ row, column }: { row: StatsRow; column: TableColumnCtx<StatsRow> }) {
  return [
    'id',
    'paymentTerm',
    'partner',
    'name',
    'order.amount',
    'warehouse',
    'owner',
    'signed',
    'delivery',
    'approval',
  ].includes(column.property)
    ? [row.span, row.span ? 1 : 0]
    : [1, 1]
}
function executionSpan({ row, columnIndex }: { row: { span: number }; columnIndex: number }) {
  return columnIndex < 3 ? [row.span, row.span ? 1 : 0] : [1, 1]
}
function cellClass({ row, column }: { row: ExecutionLine; column: { property: string } }) {
  const prop = column.property as keyof ExecutionLine
  if (prop === 'unpaid' && row.unpaid > 0) return 'board-cell-red'
  if (prop === 'unsettled' && row.unsettled > 0) return 'board-cell-orange'
  if (['pending', 'unplanned'].includes(prop) && Number(row[prop]) > 0) return 'board-cell-yellow'
  return ''
}
function summary({ columns }: { columns: TableColumnCtx<ExecutionLine>[] }) {
  return columns.map((column, index) =>
    index === 0
      ? '汇总'
      : index < 5
        ? ''
        : (() => {
            const value = filteredExecution.value.reduce(
              (sum, line) => sum + Number(line[column.property as keyof ExecutionLine] ?? 0),
              0,
            )
            return ['payable', 'paid', 'unpaid'].includes(column.property) ? money(value) : value
          })(),
  )
}
function openOrder(row: StatsRow) {
  detailsId.value = row.order.id
}
function moveDetail(offset: number) {
  detailsId.value = detailOrders.value[detailIndex.value + offset]?.id ?? detailsId.value
}
function sortChanged({ prop, order }: { prop: string; order: string | null }) {
  sort.value = { prop: prop === 'order.amount' ? 'amount' : prop, order: order ?? '' }
}
function syncFullscreen() {
  fullTable.value = Boolean(document.fullscreenElement)
}
async function toggleTableFullscreen() {
  try {
    if (document.fullscreenElement) await document.exitFullscreen()
    else await tablePanel.value?.requestFullscreen()
    fullTable.value = Boolean(document.fullscreenElement)
  } catch {
    ElMessage.warning('当前浏览器无法进入全屏模式')
  }
}
function exportCsv() {
  const salesHeaders = [
    '订单编号', '订单日期', '客户名称',
    '品牌', '型号', '封装', '单价', '数量', '金额', '订单金额', '账期', '交付日期', '销售负责人',
  ]
  const salesRows = sortedOrders.value.flatMap((order) =>
    order.lines.map((line) => [
      order.id, order.signed, order.partner,
      line.brand || '', line.product, line.package || '', line.price, line.quantity,
      line.amount, order.amount, order.paymentTerm || '', order.delivery, order.owner,
    ]),
  )
  const data =
    tab.value === 'statistics'
      ? !purchase.value
        ? salesRows
        : sortedOrders.value.map((order) => [
            order.partner,
            order.name,
            order.amount,
            order.signed,
            order.delivery,
            order.approval,
            order.owner,
          ])
      : filteredExecution.value.map((line) =>
          executionColumns.value.map((column) => line[column.prop as keyof ExecutionLine] ?? ''),
        )
  const headers =
    tab.value === 'statistics'
      ? !purchase.value
        ? salesHeaders
        : [
            partnerLabel.value,
            `${prefix.value}订单名称`,
            '订单金额/元',
            '订单签订日期',
            '订单交付日期',
            '审批结果',
            `${prefix.value}负责人`,
          ]
      : executionColumns.value.map((column) => column.label)
  const escape = (value: unknown) => '"' + String(value).replace(/"/g, '""') + '"'
  const url = URL.createObjectURL(
    new Blob(['\uFEFF' + [headers, ...data].map((row) => row.map(escape).join(',')).join('\r\n')], {
      type: 'text/csv;charset=utf-8',
    }),
  )
  const link = document.createElement('a')
  link.href = url
  link.download = `${title.value}-${tab.value === 'statistics' ? '订单统计' : '执行跟踪'}.csv`
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
</script>

<template>
  <div class="order-board" :data-kind="kind">
    <header class="board-banner">
      <h1>{{ title }}</h1>
      <span /><span /><span />
    </header>
    <section class="board-body">
      <el-tabs v-model="tab" class="report-tabs board-tabs"
        ><el-tab-pane :label="`${prefix}订单统计`" name="statistics" /><el-tab-pane
          :label="purchase ? '采购订单-入库-退货-对账执行跟踪' : executionTitle"
          name="execution"
      /></el-tabs>
      <template v-if="tab === 'statistics'">
        <div class="board-overview">
          <article class="board-amount">
            <h2>{{ prefix }}总金额</h2>
            <div>{{ money(statistics.amount) }}<span>元</span></div>
          </article>
          <div class="board-counts">
            <article>
              <h2>{{ purchase ? '签订供应商数量' : '签单客户数' }}</h2>
              <div>{{ statistics.partners }}<span>家</span></div>
            </article>
            <article>
              <h2>{{ prefix }}订单签订数量</h2>
              <div>{{ statistics.count }}<span>份</span></div>
            </article>
          </div>
          <TrendChart
            v-if="trend.values.length"
            :title="`${prefix}金额趋势`"
            :labels="trend.labels"
            :values="trend.values"
          />
          <div v-else class="board-empty-chart">
            <h2>{{ prefix }}金额趋势</h2>
            <el-empty description="暂无数据" :image-size="60" />
          </div>
        </div>
        <div class="board-stat-filters">
          <div v-if="purchase" class="board-filter approval-filter">
            <div class="board-filter-heading"><strong>审批结果</strong></div>
            <div class="filter-buttons">
              <el-button
                v-for="item in ['通过', '不通过']"
                :key="item"
                :type="approval === item ? 'primary' : 'default'"
                :aria-pressed="approval === item"
                @click="approval = approval === item ? '' : item"
                >{{ item }}</el-button
              >
            </div>
          </div>
          <template v-if="purchase"
            ><BoardTextFilter
              v-model="warehouse"
              label="仓库"
              :options="options.warehouses" /><BoardDateFilter
              v-model="date"
              label="订单签署日期" /><BoardTextFilter
              v-model="partner"
              label="供应商"
              :options="options.partners"
          /></template>
          <template v-else
            ><BoardTextFilter
              v-model="partner"
              label="客户名称"
              :options="options.partners" /><BoardTextFilter
              v-model="owner"
              label="销售负责人"
              :options="options.owners" /><BoardDateFilter v-model="date" label="订单日期"
          /></template>
        </div>
      </template>
      <div v-else class="board-execution-filters">
        <div class="board-filter">
          <div class="board-filter-heading">
            <strong>{{ prefix }}订单标签</strong>
          </div>
          <div class="filter-buttons">
            <el-button
              v-for="item in tags"
              :key="item"
              :type="tag === item ? 'primary' : 'default'"
              :aria-pressed="tag === item"
              @click="tag = tag === item ? '' : item"
              >{{ item }}</el-button
            >
          </div>
        </div>
        <BoardTextFilter
          v-model="executionPartner"
          :label="purchase ? '供应商名称' : '客户名称'"
          :options="options.partners"
        /><BoardTextFilter
          v-model="orderName"
          :label="`${prefix}订单名称`"
          :options="options.names"
        />
      </div>
      <section ref="tablePanel" class="board-table-panel" @fullscreenchange="syncFullscreen">
        <div class="board-table-heading">
          <h2>{{ tab === 'statistics' ? `${prefix}订单明细` : executionTitle }}</h2>
          <div class="board-table-tools">
            <el-button
              text
              aria-label="重置筛选"
              @click="tab === 'statistics' ? resetStatistics() : resetExecution()"
              >重置</el-button
            >
            <el-button
              text
              :icon="Refresh"
              aria-label="刷新数据"
              title="刷新数据"
              @click="refreshTable"
            />
            <el-popover
              v-if="tab === 'statistics'"
              v-model:visible="sortVisible"
              trigger="click"
              :width="280"
            >
              <template #reference
                ><el-button text :icon="Sort" aria-label="排序设置" title="排序设置"
              /></template>
              <div class="board-sort-settings">
                <strong>排序设置</strong
                ><el-select :teleported="false" v-model="sortField" aria-label="排序字段"
                  ><el-option
                    v-for="item in sortOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value" /></el-select
                ><el-select :teleported="false" v-model="sortDirection" aria-label="排序方向"
                  ><el-option label="升序" value="ascending" /><el-option
                    label="降序"
                    value="descending" /></el-select
                ><el-button type="primary" @click="applySort">确定排序</el-button>
              </div> </el-popover
            ><el-button
              text
              :icon="Download"
              aria-label="导出当前数据"
              title="导出当前数据"
              @click="exportCsv"
            /><el-button
              text
              :icon="FullScreen"
              aria-label="表格全屏"
              title="表格全屏"
              @click="toggleTableFullscreen"
            />
          </div>
        </div>
        <el-table
          v-if="tab === 'statistics'"
          :key="`${kind}-statistics`"
          ref="statsTable"
          class="board-table"
          :class="{ 'sales-statistics-table': !purchase }"
          :data="statsRows"
          border
          stripe
          :height="fullTable ? 'calc(100vh - 120px)' : 470"
          :span-method="statsSpan"
          :default-sort="{ prop: 'signed', order: 'descending' }"
          @sort-change="sortChanged"
          @row-click="openOrder"
          empty-text="暂无数据"
        >
          <template v-if="purchase">
            <el-table-column
              prop="partner"
              :label="partnerLabel"
              width="125"
              :fixed="!smallScreen"
              sortable="custom"
            /><el-table-column
              prop="name"
              :label="`${prefix}订单名称`"
              width="150"
              :fixed="!smallScreen"
              sortable="custom"
              ><template #default="{ row }"
                ><button class="board-order-link" @click.stop="openOrder(row)">
                  {{ row.name }}
                </button></template
              ></el-table-column
            >
            <el-table-column
              v-if="purchase"
              prop="warehouse"
              label="仓库"
              width="95"
              sortable="custom"
            />
            <el-table-column
              prop="order.amount"
              :label="`${prefix}订单金额/元`"
              width="150"
              align="right"
              sortable="custom"
              ><template #default="{ row }">{{
                money(row.order.amount)
              }}</template></el-table-column
            >
            <el-table-column
              v-if="purchase"
              prop="owner"
              label="采购负责人"
              width="120"
              sortable="custom"
            />
            <el-table-column
              prop="signed"
              label="订单签订日期"
              width="140"
              sortable="custom"
            /><el-table-column
              prop="delivery"
              label="订单交付日期"
              width="140"
              sortable="custom"
            /><el-table-column prop="approval" label="审批结果" width="120" sortable="custom"
              ><template #default="{ row }"
                ><el-tag size="small" effect="dark">{{ row.approval }}</el-tag></template
              ></el-table-column
            >
            <el-table-column
              v-if="!purchase"
              prop="owner"
              label="销售负责人"
              width="120"
              sortable="custom"
            />
            <el-table-column :label="purchase ? '采购产品明细' : '产品明细'" align="center"
              ><el-table-column prop="product" label="产品名称" width="135" /><el-table-column
                prop="code"
                label="产品编码"
                width="100"
              /><el-table-column
                prop="quantity"
                :label="`${prefix}数量`"
                width="100"
                align="right"
              /><el-table-column
                prop="price"
                :label="purchase ? '采购单价/元' : '实际售价/元'"
                width="120"
                align="right"
                ><template #default="{ row }">{{ money(row.price) }}</template></el-table-column
              ><el-table-column
                prop="amount"
                :label="purchase ? '产品采购总价/元' : '实际售价合计/元'"
                width="145"
                align="right"
                ><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column
              ></el-table-column
            >
          </template>
          <template v-else>
            <el-table-column align="center" class-name="sales-order-centered"
              prop="id"
              label="订单编号"
              min-width="105"
              :fixed="!smallScreen"
              sortable="custom"
              ><template #default="{ row }"
                ><button class="board-order-link" @click.stop="openOrder(row)">
                  {{ row.order.id }}
                </button></template
              ></el-table-column
            >
            <el-table-column align="center" class-name="sales-order-centered" prop="signed" label="订单日期" min-width="82" sortable="custom" />
            <el-table-column align="center" class-name="sales-order-centered" prop="partner" label="客户名称" min-width="100" sortable="custom" />
            <el-table-column label="产品明细" align="center">
              <el-table-column align="left" header-align="left" prop="brand" label="品牌" min-width="58" />
              <el-table-column align="left" header-align="left" prop="product" label="型号" min-width="82" />
              <el-table-column align="left" header-align="left" prop="package" label="封装" min-width="112" />
              <el-table-column align="left" header-align="left" prop="price" label="单价" min-width="50"
                ><template #default="{ row }">{{ money(row.price) }}</template></el-table-column
              >
              <el-table-column align="left" header-align="left" prop="quantity" label="数量" min-width="50" />
              <el-table-column align="left" header-align="left" prop="amount" label="金额" min-width="72"
                ><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column
              >
            </el-table-column>
            <el-table-column align="center" class-name="sales-order-centered"
              prop="order.amount"
              label="订单金额"
              min-width="80"
              sortable="custom"
              ><template #default="{ row }">{{
                money(row.order.amount)
              }}</template></el-table-column
            >
            <el-table-column align="center" class-name="sales-order-centered" prop="paymentTerm" label="账期" min-width="75" sortable="custom" />
            <el-table-column align="center" class-name="sales-order-centered" prop="delivery" label="交付日期" min-width="82" sortable="custom" />
            <el-table-column align="center" class-name="sales-order-centered" prop="owner" label="销售负责人" min-width="86" sortable="custom" />
          </template>
          <el-table-column v-if="purchase" label="到货计划" align="center"
            ><el-table-column
              prop="plan.batch"
              :label="purchase ? '到货批次' : '送货批次'"
              width="100" /><el-table-column
              prop="plan.date"
              :label="purchase ? '计划到货日期' : '计划送货日期'"
              width="140" /><el-table-column
              prop="plan.note"
              :label="purchase ? '计划到货内容备注' : '计划送货内容备注'"
              width="180"
          /></el-table-column>
        </el-table>
        <el-table
          v-else
          :key="`${kind}-execution`"
          ref="executionTable"
          class="board-table execution-table"
          :data="executionRows"
          border
          stripe
          :height="fullTable ? 'calc(100vh - 120px)' : 620"
          :span-method="executionSpan"
          :cell-class-name="cellClass"
          :show-summary="purchase && filteredExecution.length > 0"
          :summary-method="summary"
          empty-text="暂无数据"
          ><el-table-column
            v-for="(column, index) in executionColumns"
            :key="column.prop"
            v-bind="column"
            :fixed="!smallScreen && index < 3"
            :align="index >= 5 ? 'right' : 'left'"
            ><template #default="{ row }">{{
              ['payable', 'paid', 'unpaid'].includes(column.prop)
                ? money(row[column.prop])
                : row[column.prop]
            }}</template></el-table-column
          ></el-table
        >
        <div v-if="tab === 'statistics'" class="board-pagination">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :page-sizes="[20, 50, 100]"
            :total="sortedOrders.length"
            layout="sizes, total, ->, prev, pager, next, jumper"
          />
        </div>
        <div v-else class="board-execution-pagination">
          <el-button :disabled="executionPage === 1" aria-label="第一页" @click="executionPage = 1"
            >|‹</el-button
          ><el-button :disabled="executionPage === 1" aria-label="上一页" @click="executionPage--"
            >‹</el-button
          ><el-input-number
            v-model="executionPage"
            :min="1"
            :max="Math.max(1, Math.ceil(filteredExecution.length / 50))"
            :controls="false"
            aria-label="页码"
            @change="executionPage = executionPage || 1"
          /><span>/ {{ Math.max(1, Math.ceil(filteredExecution.length / 50)) }}</span
          ><el-button
            :disabled="executionPage >= Math.ceil(filteredExecution.length / 50)"
            aria-label="下一页"
            @click="executionPage++"
            >›</el-button
          ><el-button
            :disabled="executionPage >= Math.ceil(filteredExecution.length / 50)"
            aria-label="最后一页"
            @click="executionPage = Math.max(1, Math.ceil(filteredExecution.length / 50))"
            >›|</el-button
          >
        </div>
      </section>
    </section>
    <el-drawer
      v-model="detailsOpen"
      :title="`${prefix}订单明细`"
      :size="drawerFullscreen ? '100%' : 'min(1000px, 96vw)'"
      class="board-detail"
      destroy-on-close
    >
      <template v-if="detail"
        ><div class="board-detail-toolbar">
          <el-button
            :icon="ArrowLeft"
            :disabled="detailIndex <= 0"
            aria-label="上一笔订单"
            @click="moveDetail(-1)"
          /><span>{{ detailIndex + 1 }} / {{ detailOrders.length }}</span
          ><el-button
            :icon="ArrowRight"
            :disabled="detailIndex >= detailOrders.length - 1"
            aria-label="下一笔订单"
            @click="moveDetail(1)"
          /><el-button
            text
            :icon="FullScreen"
            aria-label="展开订单明细"
            @click="drawerFullscreen = !drawerFullscreen"
          />
        </div>
        <template v-if="purchase">
          <el-descriptions :column="2" direction="vertical" border
            ><el-descriptions-item :label="partnerLabel">{{ detail.partner }}</el-descriptions-item
            ><el-descriptions-item :label="`${prefix}订单名称`">{{
              detail.name
            }}</el-descriptions-item
            ><el-descriptions-item v-if="purchase" label="仓库">{{
              detail.warehouse
            }}</el-descriptions-item
            ><el-descriptions-item :label="`${prefix}订单金额/元`">{{
              money(detail.amount)
            }}</el-descriptions-item
            ><el-descriptions-item label="订单签订日期">{{ detail.signed }}</el-descriptions-item
            ><el-descriptions-item label="订单交付日期">{{ detail.delivery }}</el-descriptions-item
            ><el-descriptions-item label="审批结果"
              ><el-tag effect="dark">{{ detail.approval }}</el-tag></el-descriptions-item
            ><el-descriptions-item :label="`${prefix}负责人`">{{
              detail.owner
            }}</el-descriptions-item></el-descriptions
          >
          <h3>{{ purchase ? '采购产品明细' : '产品明细' }}</h3>
          <el-table :data="detail.lines" border
            ><el-table-column type="index" width="50" /><el-table-column
              prop="product"
              label="产品名称"
              min-width="130"
            /><el-table-column prop="code" label="产品编码" min-width="95" /><el-table-column
              prop="quantity"
              :label="`${prefix}数量`"
              min-width="90"
            /><el-table-column :label="purchase ? '采购单价/元' : '实际售价/元'" min-width="120"
              ><template #default="{ row }">{{ money(row.price) }}</template></el-table-column
            ><el-table-column
              :label="purchase ? '产品采购总价/元' : '实际售价合计/元'"
              min-width="145"
              ><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column
            ></el-table
          >
        </template>
        <template v-else>
          <el-descriptions :column="2" direction="vertical" border>
            <el-descriptions-item label="订单编号">{{ detail.id }}</el-descriptions-item>
            <el-descriptions-item label="订单日期">{{ detail.signed }}</el-descriptions-item>
            <el-descriptions-item label="客户名称">{{ detail.partner }}</el-descriptions-item>
            <el-descriptions-item label="订单金额">{{ money(detail.amount) }}</el-descriptions-item>
            <el-descriptions-item label="账期">{{ detail.paymentTerm }}</el-descriptions-item>
            <el-descriptions-item label="销售负责人">{{ detail.owner }}</el-descriptions-item>
            <el-descriptions-item label="交付日期">{{ detail.delivery }}</el-descriptions-item>
          </el-descriptions>
          <h3>产品明细</h3>
          <el-table :data="detail.lines" border>
            <el-table-column prop="brand" label="品牌" min-width="100" />
            <el-table-column prop="product" label="型号" min-width="170" />
            <el-table-column prop="package" label="封装" min-width="100" />
            <el-table-column label="单价" min-width="120" align="right"
              ><template #default="{ row }">{{ money(row.price) }}</template></el-table-column
            >
            <el-table-column prop="quantity" label="数量" min-width="90" align="right" />
            <el-table-column label="金额" min-width="145" align="right"
              ><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column
            >
          </el-table>
        </template>
        <h3>{{ purchase ? '到货计划' : '送货计划' }}</h3>
        <el-table :data="detail.plans" border
          ><el-table-column type="index" width="50" /><el-table-column
            prop="batch"
            :label="purchase ? '到货批次' : '送货批次'" /><el-table-column
            prop="date"
            :label="purchase ? '计划到货日期' : '计划送货日期'" /><el-table-column
            prop="note"
            :label="purchase ? '计划到货内容备注' : '计划送货内容备注'" /></el-table
      ></template>
    </el-drawer>
  </div>
</template>
