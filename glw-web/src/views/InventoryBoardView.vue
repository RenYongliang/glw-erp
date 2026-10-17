<script setup lang="ts">
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { FullScreen, Refresh, Download, Sort, InfoFilled } from '@element-plus/icons-vue'
import BoardTextFilter from '../components/boards/BoardTextFilter.vue'
import BoardDateFilter from '../components/boards/BoardDateFilter.vue'
import InventoryChart from '../components/boards/InventoryChart.vue'
import { emptyTextFilter, emptyDateFilter, matchesText, matchesDate } from '../domain/boardFilters'
import data from '../data/inventory.json'
import '../assets/inventory-board.css'

type Row = Record<string, string | number>
type Tab = 'realtime' | 'warnings' | 'period'
const tab = ref<Tab>('realtime')
const tabs = [
  { name: 'realtime', label: '实时库存查询' },
  { name: 'warnings', label: '库存预警查询' },
  { name: 'period', label: '库存期初期末查询' },
]
const makeFilters = () => ({
  product: emptyTextFilter(),
  model: emptyTextFilter(),
  warehouse: emptyTextFilter(),
  brand: emptyTextFilter(),
})
const filters = ref({ realtime: makeFilters(), warnings: makeFilters(), period: makeFilters() })
const date = ref({ ...emptyDateFilter(), operator: '选择范围' })
const warning = ref(false)
const page = ref(1)
const pageSize = ref(50)
const pageInput = ref(1)
const sortVisible = ref(false)
const sortKey = ref('')
const sortDirection = ref('asc')
const appliedSort = ref({ key: '', direction: 'asc' })
function applySort() {
  appliedSort.value = { key: sortKey.value, direction: sortDirection.value }
  sortVisible.value = false
}
const panel = ref<HTMLElement>()
const scroll = ref<HTMLElement>()
const fullscreen = ref(false)
const options = (key: keyof (typeof data.stock)[0]) => [
  ...new Set(data.stock.map((r) => String(r[key]))),
]
const stock = computed(() =>
  data.stock.filter(
    (r) =>
      Object.entries(filters.value[tab.value]).every(([key, f]) =>
        matchesText(String(r[key as keyof typeof r]), f),
      ) &&
      (tab.value !== 'warnings' || !warning.value || r.available <= 50),
  ),
)
const rows = computed<Row[]>(() =>
  tab.value === 'period'
    ? data.periods.filter(
        (r) =>
          matchesText(r.warehouse, filters.value.period.warehouse) &&
          matchesText(r.product, filters.value.period.product) &&
          matchesDate(r.date, date.value),
      )
    : stock.value,
)
const sorted = computed(() => {
  const { key, direction } = appliedSort.value
  if (!key) return rows.value
  return [...rows.value].sort((a, b) => {
    const x = a[key]!,
      y = b[key]!
    return (
      (typeof x === 'number' && typeof y === 'number'
        ? x - y
        : String(x).localeCompare(String(y), 'zh-CN')) * (direction === 'asc' ? 1 : -1)
    )
  })
})
const pages = computed(() => Math.max(1, Math.ceil(sorted.value.length / pageSize.value)))
const visibleRows = computed(() => sorted.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const titles: Record<Tab, string> = {
  realtime: '库存统计查询',
  warnings: '库存预警查询',
  period: '库存期初期末',
}
const baseColumns = [
  ['warehouse', '仓库'],
  ['product', '产品名称'],
  ['code', '产品编码'],
]
const columns = computed(() =>
  tab.value === 'period'
    ? [
        ...baseColumns,
        ['date', '日期'],
        ['opening', '期初库存'],
        ['incoming', '入库总数量'],
        ['outgoing', '出库总数量'],
        ['activity', '出入库活动汇总'],
        ['closing', '期末库存'],
      ]
    : tab.value === 'warnings'
      ? [
          ...baseColumns,
          ['brand', '品牌'],
          ['model', '规格型号'],
          ['unit', '单位'],
          ['available', '当前可用库存数量'],
          ['purchaseIn', '采购入库数量'],
          ['otherIn', '其他入库数量'],
          ['totalIn', '总入库数量'],
          ['salesOut', '销售出库数量'],
          ['otherOut', '其他出库数量'],
          ['totalOut', '总出库数量'],
          ['current', '当前库存数量'],
          ['frozen', '库存冻结数量'],
          ['planned', '计划出库数量'],
        ]
      : [
          ...baseColumns,
          ['totalIn', '总入库数量'],
          ['totalOut', '总出库数量'],
          ['current', '当前库存数量'],
          ['frozen', '库存冻结数量'],
          ['available', '当前可用库存数量'],
        ],
)
const dimensionCount = computed(() =>
  tab.value === 'period' ? 4 : tab.value === 'warnings' ? 6 : 3,
)
const totals = computed(() =>
  Object.fromEntries(
    columns.value
      .slice(dimensionCount.value)
      .map(([key]) => [key!, rows.value.reduce((sum, r) => sum + Number(r[key!] || 0), 0)]),
  ),
)
const metrics = computed(() =>
  [
    ['current', '当前库存数'],
    ['available', '可用库存总数'],
    ['frozen', '库存冻结数'],
    ['planned', '计划出库数'],
  ].map(([key, label]) => ({
    label,
    value: stock.value.reduce((s, r) => s + Number(r[key as keyof typeof r]), 0),
  })),
)
const chartData = computed(() =>
  [...new Set(stock.value.map((r) => r.product))]
    .sort()
    .map((name) => ({
      name,
      value: stock.value.filter((r) => r.product === name).reduce((s, r) => s + r.available, 0),
    })),
)
function span(index: number, column: number) {
  if (column > (tab.value === 'period' ? 2 : 0)) return 1
  const keys = columns.value.slice(0, column + 1).map((c) => c[0]!)
  const same = (other: Row) => keys.every((key) => other[key] === visibleRows.value[index]![key])
  if (index > 0 && same(visibleRows.value[index - 1]!)) return 0
  let count = 1
  while (index + count < visibleRows.value.length && same(visibleRows.value[index + count]!))
    count++
  return count
}
function go(value: number) {
  page.value = Math.min(pages.value, Math.max(1, Number.isFinite(value) ? Math.trunc(value) : 1))
  pageInput.value = page.value
  scroll.value?.scrollTo({ top: 0 })
}
watch([rows, appliedSort, pageSize], () => go(1))
watch(tab, () => {
  appliedSort.value = { key: '', direction: 'asc' }
  sortKey.value = ''
  sortVisible.value = false
  go(1)
})
function format(row: Row, key: string) {
  return key === 'date'
    ? String(row[key]).replace(/^(\d{4})-(\d{2})-\d{2}$/, (_, y, m) => `${y}年${Number(m)}月`)
    : row[key]
}
function isLow(row: Row, key: string) {
  return (
    tab.value !== 'period' &&
    Number(row[key]) < 50 &&
    (key === 'available' || (tab.value === 'realtime' && key === 'current'))
  )
}
function exportCsv() {
  const escape = (v: unknown) => `"${String(v ?? '').replaceAll('"', '""')}"`
  const lines = [
    columns.value.map((c) => c[1]),
    ...sorted.value.map((r) => columns.value.map((c) => format(r, c[0]!))),
    [
      ...Array.from({ length: dimensionCount.value }, (_, i) => (i === 0 ? '汇总' : '')),
      ...columns.value.slice(dimensionCount.value).map((c) => totals.value[c[0]!]),
    ],
  ]
  const url = URL.createObjectURL(
    new Blob(['\uFEFF' + lines.map((r) => r.map(escape).join(',')).join('\r\n')], {
      type: 'text/csv;charset=utf-8',
    }),
  )
  const link = document.createElement('a')
  link.href = url
  link.download = `${titles[tab.value]}.csv`
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
async function toggleFullscreen() {
  try {
    if (document.fullscreenElement) await document.exitFullscreen()
    else await panel.value?.requestFullscreen()
  } catch {
    ElMessage.warning('浏览器暂不支持全屏显示')
  }
}
function syncFullscreen() {
  fullscreen.value = document.fullscreenElement === panel.value
}
onMounted(() => document.addEventListener('fullscreenchange', syncFullscreen))
onBeforeUnmount(() => document.removeEventListener('fullscreenchange', syncFullscreen))
</script>

<template>
  <div class="order-board inventory-board">
    <header class="board-banner">
      <h1>库存统计看板</h1>
      <span /><span /><span />
    </header>
    <div class="board-body">
      <el-tabs v-model="tab" class="report-tabs board-tabs"
        ><el-tab-pane v-for="item in tabs" :key="item.name" :name="item.name" :label="item.label"
      /></el-tabs>
      <section v-if="tab === 'realtime'" class="inventory-overview">
        <article v-for="metric in metrics" :key="metric.label" class="inventory-metric">
          <h2>{{ metric.label }}</h2>
          <div>{{ metric.value }}<span>件</span></div>
        </article>
        <article class="inventory-chart-card">
          <h2>产品可用库存</h2>
          <InventoryChart :data="chartData" /><span
            v-if="!chartData.length"
            class="inventory-chart-empty"
            >暂无数据</span
          >
        </article>
        <BoardTextFilter
          v-model="filters.realtime.product"
          label="产品名称"
          :options="options('product')"
        />
        <BoardTextFilter
          v-model="filters.realtime.model"
          label="规格型号"
          :options="options('model')"
        />
        <BoardTextFilter
          v-model="filters.realtime.warehouse"
          label="仓库"
          :options="options('warehouse')"
        />
        <BoardTextFilter
          v-model="filters.realtime.brand"
          label="品牌"
          :options="options('brand')"
        />
      </section>
      <section v-else-if="tab === 'warnings'" class="inventory-filters warning-filters">
        <BoardTextFilter
          v-model="filters.warnings.warehouse"
          label="仓库"
          :options="options('warehouse')"
        />
        <BoardTextFilter
          v-model="filters.warnings.product"
          label="产品名称"
          :options="options('product')"
        />
        <BoardTextFilter
          v-model="filters.warnings.brand"
          label="品牌"
          :options="options('brand')"
        />
        <div class="board-filter">
          <div class="board-filter-heading"><strong>预警状态</strong></div>
          <button
            class="inventory-warning-toggle"
            :class="{ selected: warning }"
            :aria-pressed="warning"
            @click="warning = !warning"
          >
            库存预警
          </button>
        </div>
      </section>
      <section v-else class="inventory-filters period-filters">
        <BoardDateFilter v-model="date" label="日期" />
        <BoardTextFilter
          v-model="filters.period.warehouse"
          label="仓库"
          :options="options('warehouse')"
        />
        <BoardTextFilter
          v-model="filters.period.product"
          label="产品名称"
          :options="options('product')"
        />
      </section>
      <section ref="panel" class="inventory-panel" :class="{ 'is-fullscreen': fullscreen }">
        <header class="inventory-table-heading">
          <h2>{{ titles[tab] }}</h2>
          <div class="inventory-tools">
            <el-tooltip
              v-if="tab === 'warnings'"
              content="当前可用库存数量小于50时标色；库存预警筛选包含50。"
              ><el-icon><InfoFilled /></el-icon
            ></el-tooltip>
            <button title="刷新" aria-label="刷新库存数据" @click="ElMessage.success('数据已刷新')">
              <el-icon><Refresh /></el-icon>
            </button>

            <button title="导出" aria-label="导出库存表格" @click="exportCsv">
              <el-icon><Download /></el-icon>
            </button>
            <button
              :title="fullscreen ? '退出全屏' : '全屏'"
              aria-label="库存表格全屏"
              @click="toggleFullscreen"
            >
              <el-icon><FullScreen /></el-icon>
            </button>
          </div>
        </header>
        <div ref="scroll" class="inventory-table-scroll" :class="tab">
          <table class="inventory-table">
            <thead>
              <tr>
                <th v-for="column in columns" :key="column[0]" :class="{ 'inventory-brand': column[0] === 'brand', 'inventory-model': column[0] === 'model' }">{{ column[1] }}</th>
                <th class="inventory-filler" aria-hidden="true" />
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(row, index) in visibleRows"
                :key="`${row.warehouse}-${row.code}-${row.date || ''}`"
              >
                <template v-for="(column, ci) in columns" :key="column[0]"
                  ><td
                    v-if="span(index, ci)"
                    :rowspan="span(index, ci)"
                    :class="{
                      numeric: ci >= dimensionCount,
                      'inventory-brand': column[0] === 'brand',
                      'inventory-model': column[0] === 'model',
                      low: isLow(row, column[0]!),
                      group: span(index, ci) > 1,
                    }"
                    :title="String(format(row, column[0]!))"
                  >
                    {{ format(row, column[0]!) }}
                  </td></template
                >
                <td class="inventory-filler" />
              </tr>
              <tr v-if="!visibleRows.length">
                <td :colspan="columns.length + 1" class="inventory-empty">暂无数据</td>
              </tr>
              <tr v-else class="inventory-summary">
                <td :colspan="dimensionCount">汇总</td>
                <td
                  v-for="column in columns.slice(dimensionCount)"
                  :key="column[0]"
                  class="numeric"
                >
                  {{ totals[column[0]!] }}
                </td>
                <td />
              </tr>
            </tbody>
          </table>
        </div>
        <footer class="list-pagination"><el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10,20,50,100]" :total="sorted.length" layout="total, sizes, prev, pager, next, jumper" /></footer>
      </section>
    </div>
    <el-dialog v-model="sortVisible" title="排序" width="400px"
      ><div class="inventory-sort">
        <el-select v-model="sortKey" placeholder="默认排序" clearable aria-label="排序字段"
          ><el-option
            v-for="column in columns"
            :key="column[0]"
            :value="column[0]!"
            :label="column[1]" /></el-select
        ><el-radio-group v-model="sortDirection"
          ><el-radio value="asc">升序</el-radio
          ><el-radio value="desc">降序</el-radio></el-radio-group
        >
      </div>
      <template #footer
        ><el-button @click="sortVisible = false">取消</el-button
        ><el-button
          type="primary"
          @click="applySort"
          >确定</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
