<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Box, Search, Filter, Refresh, Download, FullScreen, Expand, Fold } from '@element-plus/icons-vue'
import { fetchInventory, type InventoryProduct, type InventoryQuery } from '../services/inventoryApi'
import { downloadExport } from '../services/exportApi'
import '../assets/purchase.css'

const view = ref('stock')
const keyword = ref('')
const filters = ref({ brand: '', partNo: '', packageType: '' })
const draftFilters = ref({ ...filters.value })
const filterOpen = ref(false)
const page = ref(1), pageSize = ref(20), total = ref(0)
const rows = ref<InventoryProduct[]>([])
const loading = ref(false), exporting = ref(false), error = ref(''), full = ref(false)
const expandedIds = ref<(number | string)[]>([])
let generation = 0
let searchTimer: ReturnType<typeof setTimeout> | undefined
const filterCount = computed(() => Object.values(filters.value).filter(Boolean).length)
const allExpanded = computed(() => rows.value.length > 0 && rows.value.every(r => expandedIds.value.includes(r.productId)))
const quantity = (value: number) => Number(value).toLocaleString('zh-CN')
const cost = (value: number | string | null) => value === null ? '—' : Number(value).toFixed(4)
const amount = (value: number | string) => Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const range = (row: InventoryProduct) => row.minCost === null || row.maxCost === null ? '—'
  : Number(row.minCost) === Number(row.maxCost) ? cost(row.minCost) : `${cost(row.minCost)} ~ ${cost(row.maxCost)}`
const date = (value: string | null) => value ? value.slice(0, 10) : '—'
function query(pageNo = page.value, size = pageSize.value): InventoryQuery {
  return { pageNo, pageSize: size, inStockOnly: view.value === 'stock', keyword: keyword.value.trim(), ...filters.value }
}
async function load() {
  const current = ++generation
  loading.value = true
  error.value = ''
  try {
    const result = await fetchInventory(query())
    if (current !== generation) return
    if (page.value > 1 && !result.records.length && Number(result.total) > 0) {
      page.value = Math.ceil(Number(result.total) / pageSize.value)
      return
    }
    rows.value = result.records
    total.value = Number(result.total)
    expandedIds.value = expandedIds.value.filter(id => result.records.some(r => r.productId === id))
  } catch (e) {
    if (current !== generation) return
    error.value = e instanceof Error ? e.message : '库存加载失败'
    rows.value = []
    total.value = 0
  } finally {
    if (current === generation) loading.value = false
  }
}
function firstPage() {
  expandedIds.value = []
  if (page.value === 1) void load()
  else page.value = 1
}
function applyFilters(reset = false) {
  filters.value = reset ? { brand: '', partNo: '', packageType: '' }
    : { brand: draftFilters.value.brand.trim(), partNo: draftFilters.value.partNo.trim(), packageType: draftFilters.value.packageType.trim() }
  draftFilters.value = { ...filters.value }
  filterOpen.value = false
  firstPage()
}
function searchNow() {
  clearTimeout(searchTimer)
  firstPage()
}
function expandChanged(_row: InventoryProduct, expanded: InventoryProduct[]) {
  expandedIds.value = expanded.map(row => row.productId)
}
async function exportData() {
  if (exporting.value) return
  exporting.value = true
  const snapshot = query(1, 200)
  try {
    await downloadExport('inventory', { inventory: snapshot }, `库存明细-${new Date().toISOString().slice(0, 10)}`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '导出失败')
  } finally { exporting.value = false }
}
watch(page, () => void load())
watch([view, pageSize], firstPage)
watch(keyword, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(firstPage, 300)
})
onMounted(load)
onBeforeUnmount(() => { clearTimeout(searchTimer); generation++ })
</script>

<template>
  <section class="purchase-page inventory-page" :class="{ 'purchase-expanded': full }">
    <nav class="purchase-view-tabs" aria-label="库存视图">
      <button v-for="tab in [{ key: 'stock', label: '有库存产品' }, { key: 'all', label: '全部产品' }]"
        :key="tab.key" :class="{ active: view === tab.key }" :aria-selected="view === tab.key" @click="view = tab.key">
        <el-icon><Box /></el-icon>{{ tab.label }}
      </button>
    </nav>
    <div class="purchase-toolbar">
      <div class="purchase-toolbar-actions">
        <el-button text :icon="Download" :loading="exporting" :disabled="loading || !!error || !total" @click="exportData">导出</el-button>
        <el-tooltip :content="allExpanded ? '收起全部批次' : '展开全部批次'">
          <el-button text :icon="allExpanded ? Fold : Expand" :aria-label="allExpanded ? '收起全部批次' : '展开全部批次'"
            :disabled="!rows.length" @click="expandedIds = allExpanded ? [] : rows.map(row => row.productId)" />
        </el-tooltip>
      </div>
      <div class="purchase-toolbar-search inventory-search">
        <el-input v-model="keyword" :prefix-icon="Search" placeholder="品牌 / 型号 / 封装" clearable maxlength="128" aria-label="搜索库存产品" @keyup.enter="searchNow" />
        <el-popover v-model:visible="filterOpen" trigger="click" :width="300" @show="draftFilters = { ...filters }">
          <template #reference><el-button :icon="Filter" :type="filterCount ? 'primary' : ''" plain>筛选{{ filterCount ? `（${filterCount}）` : '' }}</el-button></template>
          <el-form label-position="top" @submit.prevent="applyFilters()">
            <el-form-item label="品牌"><el-input v-model="draftFilters.brand" maxlength="128" clearable /></el-form-item>
            <el-form-item label="型号"><el-input v-model="draftFilters.partNo" maxlength="128" clearable /></el-form-item>
            <el-form-item label="封装"><el-input v-model="draftFilters.packageType" maxlength="128" clearable /></el-form-item>
            <el-button @click="applyFilters(true)">重置</el-button><el-button type="primary" native-type="submit">应用</el-button>
          </el-form>
        </el-popover>
        <el-tooltip content="刷新库存"><el-button text :icon="Refresh" :disabled="loading" aria-label="刷新库存" @click="load" /></el-tooltip>
        <el-tooltip :content="full ? '退出全屏' : '全屏显示'"><el-button text :icon="FullScreen" aria-label="切换库存全屏" @click="full = !full" /></el-tooltip>
      </div>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="inventory-error">
      <el-button text type="primary" @click="load">重新加载</el-button>
    </el-alert>
    <el-table v-loading="loading" :data="rows" row-key="productId" border class="inventory-table"
      :expand-row-keys="expandedIds" height="calc(100vh - 282px)" @expand-change="expandChanged"
      :empty-text="error ? '库存加载失败' : keyword || filterCount ? '没有匹配的产品' : '暂无库存数据'">
      <el-table-column type="expand" width="42">
        <template #default="{ row }">
          <div class="inventory-batch-detail">
            <el-table :data="row.batches" row-key="inventoryId" border class="inventory-batch-table" empty-text="暂无可出库批次">
              <el-table-column type="index" width="45" align="center" />
              <el-table-column prop="dateCode" label="Date Code" width="120" show-overflow-tooltip><template #default="{ row: batch }">{{ batch.dateCode || '—' }}</template></el-table-column>
              <el-table-column prop="lotCode" label="Lot Code" min-width="150" show-overflow-tooltip><template #default="{ row: batch }">{{ batch.lotCode || '—' }}</template></el-table-column>
              <el-table-column prop="batchNo" label="批次号" width="205" show-overflow-tooltip />
              <el-table-column label="入库日期" width="110"><template #default="{ row: batch }">{{ date(batch.inboundTime) }}</template></el-table-column>
              <el-table-column label="剩余库存" width="105" align="right"><template #default="{ row: batch }">{{ quantity(batch.quantity) }}</template></el-table-column>
              <el-table-column label="可出库库存" width="110" align="right"><template #default="{ row: batch }"><strong class="inventory-available">{{ quantity(batch.availableQuantity) }}</strong></template></el-table-column>
              <el-table-column label="单位成本" width="110" align="right"><template #default="{ row: batch }">{{ cost(batch.unitCost) }}</template></el-table-column>
              <el-table-column label="库存金额" width="125" align="right"><template #default="{ row: batch }">{{ amount(batch.stockValue) }}</template></el-table-column>
            </el-table>
          </div>
        </template>
      </el-table-column>
      <el-table-column type="index" label="#" width="50" align="center" :index="(index: number) => (page - 1) * pageSize + index + 1" />
      <el-table-column prop="brand" label="品牌" width="65" show-overflow-tooltip />
      <el-table-column prop="partNo" label="型号" min-width="150" show-overflow-tooltip />
      <el-table-column prop="packageType" label="封装" width="160" show-overflow-tooltip />
      <el-table-column label="可出库库存" width="125" align="right"><template #default="{ row }"><strong class="inventory-available">{{ quantity(row.availableQuantity) }}</strong></template></el-table-column>
      <el-table-column label="批次数" width="80" align="right"><template #default="{ row }">{{ quantity(row.batchCount) }}</template></el-table-column>
      <el-table-column label="平均成本" width="115" align="right"><template #default="{ row }">{{ cost(row.averageCost) }}</template></el-table-column>
      <el-table-column label="成本范围" width="185" align="right"><template #default="{ row }">{{ range(row) }}</template></el-table-column>
      <el-table-column label="库存金额" width="135" align="right"><template #default="{ row }">{{ amount(row.stockValue) }}</template></el-table-column>
    </el-table>
    <footer class="inventory-pagination">
      <span class="inventory-total">共 {{ total }} 个产品</span>
      <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total" :page-sizes="[20, 50, 100]" layout="sizes, prev, pager, next, jumper" />
    </footer>
  </section>
</template>

<style>
.inventory-page { min-width: 0; }
.inventory-page .inventory-table { width: 100%; min-height: 360px; flex: 1; --el-table-header-bg-color: #f5f6fa; --el-table-border-color: #e3e8f0; --el-table-row-hover-bg-color: #f4fbfa; font-size: 13px; }
.inventory-table .el-table__cell { padding: 7px 0; }
.inventory-table .cell { padding: 0 10px; line-height: 22px; }
.inventory-table th.el-table__cell { color: #536174; font-weight: 500; }
.inventory-table .el-table__expanded-cell { padding: 0 !important; }
.inventory-table .el-table__body > tbody > tr:nth-child(even of .el-table__row) > td.el-table__cell,
.inventory-batch-table .el-table__body > tbody > tr:nth-child(even of .el-table__row) > td.el-table__cell {
  background: #eef2f6;
}
.inventory-table .el-table__body > tbody > tr.el-table__row:hover > td.el-table__cell,
.inventory-batch-table .el-table__body > tbody > tr.el-table__row:hover > td.el-table__cell {
  background: #f4fbfa;
}
.inventory-batch-detail { padding: 10px 14px 12px 42px; background: #f8fafb; }
.inventory-batch-table { font-size: 12px; --el-table-header-bg-color: #f1f4f7; }
.inventory-available { color: #009d85; font-weight: 600; font-variant-numeric: tabular-nums; }
.inventory-pagination { display: flex; justify-content: space-between; align-items: center; gap: 10px; padding: 14px 0; }
.inventory-total { color: #8491a4; font-size: 12px; white-space: nowrap; }
.inventory-error { margin-bottom: 10px; }
.inventory-page.purchase-expanded { overflow: auto; }
.inventory-page.purchase-expanded .inventory-table { height: calc(100vh - 175px) !important; }
@media (max-width: 640px) {
  .inventory-search { flex-wrap: wrap; width: 100%; }
  .inventory-search .el-input { flex: 1; min-width: 150px; }
  .inventory-batch-detail { padding: 8px; }
  .inventory-pagination { flex-wrap: wrap; }
  .inventory-pagination .el-pagination { max-width: 100%; flex-wrap: wrap; gap: 6px; }
  .inventory-pagination .el-pagination__jump { display: none; }
}
</style>
