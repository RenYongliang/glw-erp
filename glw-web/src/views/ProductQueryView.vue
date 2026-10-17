<script setup lang="ts">
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Download, FullScreen, ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import BoardTextFilter from '../components/boards/BoardTextFilter.vue'
import { emptyTextFilter, matchesText } from '../domain/boardFilters'
import { encodeCsv } from '../domain/crm'
import products from '../data/products.json'

type Product = (typeof products)[number]
type Field = keyof Product
const columns: { key: Field; label: string; width: number; numeric?: boolean }[] = [
  { key: 'code', label: '产品编码', width: 100 },
  { key: 'name', label: '产品名称', width: 140 },
  { key: 'type', label: '产品类型', width: 110 },
  { key: 'attribute', label: '产品属性', width: 90 },
  { key: 'brand', label: '品牌', width: 65 },
  { key: 'model', label: '规格型号', width: 85 },
  { key: 'unit', label: '单位', width: 65 },
  { key: 'cost', label: '成本单价/元', width: 120, numeric: true },
  { key: 'price', label: '标准售价/元', width: 120, numeric: true },
]
const makeFilters = () => ({
  type: emptyTextFilter(),
  brand: emptyTextFilter(),
  name: emptyTextFilter(),
})
const filters = ref(makeFilters())
const filterFields = [
  { key: 'type', label: '产品类型' },
  { key: 'brand', label: '品牌' },
  { key: 'name', label: '产品名称' },
] as const
const options = (key: 'type' | 'brand' | 'name') => [
  ...new Set(products.map((product) => product[key])),
]
const tab = ref('products')
const page = ref(1)
const pageSize = ref(20)
const sort = ref<{ key: Field; order: string }>({ key: 'code', order: 'ascending' })
const table = ref<{ clearSort: () => void; setScrollTop: (value: number) => void }>()
const panel = ref<HTMLElement>()
const full = ref(false)
const rows = computed(() => {
  const filtered = products.filter((product) =>
    filterFields.every((field) => matchesText(product[field.key], filters.value[field.key])),
  )
  if (!sort.value.order) return filtered
  const { key, order } = sort.value
  return [...filtered].sort((a, b) => {
    const x = a[key],
      y = b[key]
    const result =
      typeof x === 'number' && typeof y === 'number'
        ? x - y
        : String(x).localeCompare(String(y), 'zh-CN', { numeric: true })
    return result * (order === 'ascending' ? 1 : -1)
  })
})
const visible = computed(() =>
  rows.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value),
)
const detailCode = ref('')
const detailOpen = ref(false)
const detailWide = ref(false)
const detailIndex = computed(() => rows.value.findIndex((row) => row.code === detailCode.value))
const detail = computed(() => rows.value[detailIndex.value])
const format = (value: string | number) =>
  typeof value === 'number'
    ? value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    : value
function openDetail(row: Product) {
  detailCode.value = row.code
  detailWide.value = false
  detailOpen.value = true
}
function moveDetail(offset: number) {
  const row = rows.value[detailIndex.value + offset]
  if (row) detailCode.value = row.code
}
function sortChanged(value: { prop: Field; order: string | null }) {
  sort.value = { key: value.prop || 'code', order: value.order || '' }
  page.value = 1
}
function reset() {
  filters.value = makeFilters()
  table.value?.clearSort()
  sort.value = { key: 'code', order: 'ascending' }
  page.value = 1
}
function refresh() {
  table.value?.setScrollTop(0)
  ElMessage.success('产品数据已刷新')
}
function exportCsv() {
  const csv = encodeCsv([
    columns.map((column) => column.label),
    ...rows.value.map((row) => columns.map((column) => String(row[column.key]))),
  ])
  const url = URL.createObjectURL(new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' }))
  const link = document.createElement('a')
  link.href = url
  link.download = '产品列表.csv'
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
async function toggleFullscreen() {
  try {
    if (document.fullscreenElement) await document.exitFullscreen()
    else await panel.value?.requestFullscreen()
  } catch {
    ElMessage.warning('当前浏览器无法进入全屏模式')
  }
}
function syncFullscreen() {
  full.value = document.fullscreenElement === panel.value
}
watch(
  [filters, pageSize],
  () => {
    page.value = 1
  },
  { deep: true },
)
watch(page, () => table.value?.setScrollTop(0))
watch(rows, () => {
  if (detailIndex.value < 0) detailOpen.value = false
})
onMounted(() => document.addEventListener('fullscreenchange', syncFullscreen))
onBeforeUnmount(() => document.removeEventListener('fullscreenchange', syncFullscreen))
</script>

<template>
  <div class="order-board product-query">
    <header class="board-banner">
      <h1>产品信息查询</h1>
      <span /><span /><span />
    </header>
    <div class="board-body">
      <el-tabs v-model="tab" class="product-query-tabs"
        ><el-tab-pane label="产品列表" name="products"
      /></el-tabs>
      <section class="product-query-filters" aria-label="产品筛选">
        <BoardTextFilter
          v-for="field in filterFields"
          :key="field.key"
          v-model="filters[field.key]"
          :label="field.label"
          :options="options(field.key)"
        />
      </section>
      <section ref="panel" class="board-table-panel product-query-panel">
        <div class="board-table-heading">
          <h2>产品明细表</h2>
          <div class="board-table-tools">
            <el-button text aria-label="重置产品筛选" @click="reset">重置</el-button>
            <el-button
              text
              :icon="Refresh"
              title="刷新"
              aria-label="刷新产品数据"
              @click="refresh"
            />
            <el-button
              text
              :icon="Download"
              title="导出"
              aria-label="导出产品列表"
              :disabled="!rows.length"
              @click="exportCsv"
            />
            <el-button
              text
              :icon="FullScreen"
              title="全屏"
              aria-label="产品列表全屏"
              @click="toggleFullscreen"
            />
          </div>
        </div>
        <el-table
          ref="table"
          class="board-table product-query-table"
          :data="visible"
          row-key="code"
          border
          stripe
          :height="full ? 'calc(100vh - 115px)' : 490"
          :default-sort="{ prop: 'code', order: 'ascending' }"
          empty-text="暂无符合条件的产品"
          @sort-change="sortChanged"
          @row-click="openDetail"
        >
          <el-table-column
            v-for="column in columns"
            :key="column.key"
            :prop="column.key"
            :label="column.label"
            :min-width="column.width"
            show-overflow-tooltip
            sortable="custom"
            :align="column.numeric ? 'right' : 'left'"
          >
            <template #default="{ row }">
              <button
                v-if="column.key === 'code'"
                class="board-order-link"
                :aria-label="`查看产品 ${row.name}`"
                @click.stop="openDetail(row)"
              >
                {{ row.code }}
              </button>
              <span v-else>{{ format(row[column.key]) }}</span>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[20, 50, 100]"
          :total="rows.length"
          layout="sizes, total, ->, prev, pager, next, jumper"
          class="product-query-pagination"
        />
      </section>
    </div>
    <el-drawer
      v-model="detailOpen"
      title="产品明细表"
      :size="detailWide ? '100%' : 'min(760px, 96vw)'"
      class="product-query-detail"
      destroy-on-close
    >
      <template v-if="detail">
        <div class="product-detail-tools">
          <el-button
            :icon="ArrowLeft"
            aria-label="上一个产品"
            :disabled="detailIndex <= 0"
            @click="moveDetail(-1)"
          />
          <span>{{ detailIndex + 1 }} / {{ rows.length }}</span>
          <el-button
            :icon="ArrowRight"
            aria-label="下一个产品"
            :disabled="detailIndex >= rows.length - 1"
            @click="moveDetail(1)"
          />
          <el-button
            :icon="FullScreen"
            aria-label="切换产品详情大小"
            @click="detailWide = !detailWide"
          />
        </div>
        <el-descriptions :column="1" border>
          <el-descriptions-item v-for="column in columns" :key="column.key" :label="column.label">{{
            format(detail[column.key])
          }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.product-query-tabs :deep(.el-tabs__header) {
  margin: 0 0 10px;
}
.product-query-tabs :deep(.el-tabs__item) {
  height: 44px;
  padding: 0 14px !important;
}
.product-query-tabs :deep(.el-tabs__nav-wrap::after) {
  background: transparent;
}
.product-query-filters {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 10px;
}
.product-query-panel {
  padding: 10px;
}
.product-query-panel:fullscreen {
  background: white;
}
.product-query-table :deep(.el-table__row) {
  cursor: pointer;
}
.product-query-table :deep(.el-table__cell) {
  padding: 9px 0;
}
.product-query-table :deep(.cell) {
  padding: 0 8px;
}
.product-query-pagination {
  padding: 10px;
  border: 1px solid #accaff;
  border-top: 0;
}
.product-detail-tools {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 20px;
}
@media (max-width: 700px) {
  .product-query-filters {
    grid-template-columns: 1fr;
  }
  .product-query-pagination {
    overflow-x: auto;
    gap: 5px;
  }
  .product-query .board-banner h1 {
    font-size: 24px;
  }
}
</style>
