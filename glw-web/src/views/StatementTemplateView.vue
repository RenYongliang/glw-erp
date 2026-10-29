<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Plus, Refresh, Tickets } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import {
  financeApi,
  statementTemplateApi,
  type Direction,
  type DocumentTypeCode,
  type DocumentTypeOption,
  type Partner,
  type StatementFormat,
  type StatementTemplate,
} from '../services/financeApi'
import '../assets/purchase.css'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const isAdmin = computed(() => auth.user?.role === 'ADMIN')

const fallbackTypes: DocumentTypeOption[] = [
  { code: 'STATEMENT', label: '对账单', directional: true, directions: ['AR', 'AP'] },
  { code: 'SALES_CONTRACT', label: '销售合同', directional: false, directions: ['AR'] },
  { code: 'PURCHASE_CONTRACT', label: '采购合同', directional: false, directions: ['AP'] },
  { code: 'DELIVERY_NOTE', label: '送货单', directional: false, directions: ['AR'] },
]
const descriptions: Record<DocumentTypeCode, string> = {
  STATEMENT: '按客户 / 供应商生成应收、应付对账单',
  SALES_CONTRACT: '销售订单对应的合同文本',
  PURCHASE_CONTRACT: '采购订单对应的合同文本',
  DELIVERY_NOTE: '随货同行的送货 / 发货单据',
}
const icons: Record<DocumentTypeCode, string> = {
  STATEMENT: '账',
  SALES_CONTRACT: '销',
  PURCHASE_CONTRACT: '采',
  DELIVERY_NOTE: '送',
}

function initialDocType(): DocumentTypeCode {
  const value = route.query.docType
  return fallbackTypes.some(type => type.code === value) ? (value as DocumentTypeCode) : 'STATEMENT'
}
const docTypes = ref<DocumentTypeOption[]>(fallbackTypes)
const docType = ref<DocumentTypeCode>(initialDocType())
const direction = ref<Direction>(
  fallbackTypes.find(type => type.code === docType.value)?.directions[0] ?? 'AR',
)
const partnerFilter = ref<number>()
const rows = ref<StatementTemplate[]>([])
const partners = ref<Partner[]>([])
const loading = ref(false)
const error = ref('')
let generation = 0

const currentType = computed(
  () => docTypes.value.find(type => type.code === docType.value) ?? fallbackTypes[0]!,
)
const directional = computed(() => currentType.value.directional)
const partnerLabel = computed(() => (direction.value === 'AR' ? '客户' : '供应商'))

function partnerName(id: number) {
  if (id === 0) return direction.value === 'AR' ? '全部客户' : '全部供应商'
  const partner = partners.value.find(p => p.id === id)
  return partner ? partner.shortName || partner.name : `#${id}`
}
const dateTime = (value: string) => (value ? value.replace('T', ' ').slice(0, 16) : '—')
const formatLabel = (format: StatementFormat) => (format === 'XLSX' ? 'Excel' : 'PDF')

async function loadDocTypes() {
  try {
    const list = await statementTemplateApi.docTypes()
    if (list.length) docTypes.value = list
  } catch {
    docTypes.value = fallbackTypes
  }
}
async function loadPartners() {
  try {
    partners.value = await financeApi.partners(direction.value)
  } catch {
    partners.value = []
  }
}
async function load() {
  const current = ++generation
  loading.value = true
  error.value = ''
  try {
    const list = await statementTemplateApi.list(docType.value, direction.value, partnerFilter.value)
    if (current !== generation) return
    rows.value = list
  } catch (e) {
    if (current !== generation) return
    rows.value = []
    error.value = (e as Error).message || '模板加载失败'
  } finally {
    if (current === generation) loading.value = false
  }
}

function selectType(type: DocumentTypeOption) {
  if (type.code === docType.value) return
  docType.value = type.code
}
watch(docType, () => {
  direction.value = currentType.value.directions[0] ?? 'AR'
  partnerFilter.value = undefined
  void loadPartners()
  void load()
})
watch(direction, () => {
  partnerFilter.value = undefined
  void loadPartners()
  void load()
})
watch(partnerFilter, () => void load())

onMounted(() => {
  void loadDocTypes()
  void loadPartners()
  void load()
})

function createTemplate() {
  router.push({
    path: '/finance/templates/designer',
    query: { docType: docType.value, direction: direction.value },
  })
}
function editTemplate(row: StatementTemplate) {
  router.push({ path: '/finance/templates/designer', query: { id: row.id } })
}
async function setDefault(row: StatementTemplate) {
  try {
    await ElMessageBox.confirm(
      `将「${row.name}」设为${partnerName(row.partnerId)}的默认${currentType.value.label}模板？`,
      '设为默认模板',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await statementTemplateApi.setDefault(row.id)
    ElMessage.success('已设为默认模板')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message || '设置默认模板失败')
  }
}
async function removeTemplate(row: StatementTemplate) {
  try {
    await ElMessageBox.confirm(
      `删除「${row.name}」后不再出现在模板列表中，历史单据不受影响。`,
      '删除模板',
      { type: 'warning', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger' },
    )
  } catch {
    return
  }
  try {
    await statementTemplateApi.delete(row.id)
    ElMessage.success('模板已删除')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message || '删除模板失败')
  }
}
</script>

<template>
  <section class="purchase-page template-page">
    <header class="template-head">
      <div class="template-head-title">
        <span class="template-head-icon"><el-icon><Tickets /></el-icon></span>
        <div>
          <h2>文档模板</h2>
          <p>所有模板都在可视化设计器中制作，可分别设置 Excel 或 PDF 输出。</p>
        </div>
      </div>
      <el-button v-if="isAdmin" type="primary" :icon="Plus" @click="createTemplate">
        新增模板
      </el-button>
    </header>

    <div class="template-type-grid" role="tablist" aria-label="单据类型">
      <button
        v-for="type in docTypes"
        :key="type.code"
        type="button"
        class="template-type-card"
        :class="{ active: type.code === docType }"
        role="tab"
        :aria-selected="type.code === docType"
        @click="selectType(type)"
      >
        <span class="template-type-icon">{{ icons[type.code] }}</span>
        <span class="template-type-text">
          <strong>{{ type.label }}</strong>
          <small>{{ descriptions[type.code] }}</small>
        </span>
      </button>
    </div>

    <nav v-if="directional" class="purchase-view-tabs template-direction" aria-label="对账单方向">
      <button :class="{ active: direction === 'AR' }" @click="direction = 'AR'">应收 / 客户</button>
      <button :class="{ active: direction === 'AP' }" @click="direction = 'AP'">
        应付 / 供应商
      </button>
    </nav>

    <div class="purchase-toolbar">
      <div class="purchase-toolbar-actions">
        <span class="template-section-title">{{ currentType.label }}</span>
        <el-button text :icon="Refresh" :disabled="loading" @click="load">刷新</el-button>
      </div>
      <div class="template-filters">
        <el-select
          v-model="partnerFilter"
          clearable
          filterable
          :placeholder="`全部${partnerLabel}`"
          aria-label="往来单位筛选"
        >
          <el-option :label="`全部${partnerLabel}（全局）`" :value="0" />
          <el-option
            v-for="p in partners"
            :key="p.id"
            :label="p.shortName || p.name"
            :value="p.id"
          />
        </el-select>
      </div>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="template-error">
      <el-button text type="primary" @click="load">重新加载</el-button>
    </el-alert>

    <el-table
      v-loading="loading"
      :data="rows"
      row-key="id"
      border
      class="template-table"
      :empty-text="error ? '模板加载失败' : '暂无模板，系统会自动创建全局默认模板'"
    >
      <el-table-column label="模板名称" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="template-name">{{ row.name }}</span>
          <el-tag v-if="row.isDefault === 1" size="small" type="success" class="template-default">
            默认
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="适用范围" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span :class="{ 'template-global': row.partnerId === 0 }">
            {{ partnerName(row.partnerId) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="输出格式" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.format === 'PDF' ? 'warning' : 'success'" effect="plain">
            {{ formatLabel(row.format) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="版本" width="80" align="center">
        <template #default="{ row }">v{{ row.version }}</template>
      </el-table-column>
      <el-table-column label="更新时间" width="150">
        <template #default="{ row }">{{ dateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="210" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-if="isAdmin" text type="primary" :icon="Edit" @click="editTemplate(row)">
            编辑
          </el-button>
          <el-button
            v-if="isAdmin && row.isDefault !== 1"
            text
            type="primary"
            @click="setDefault(row)"
          >
            设为默认
          </el-button>
          <el-button
            v-if="isAdmin && !(row.partnerId === 0 && row.isDefault === 1)"
            text
            type="danger"
            @click="removeTemplate(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<style scoped>
.template-page {
  min-width: 0;
}
.template-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.template-head-title {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.template-head-icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: #e6f4f1;
  color: #0f766e;
  font-size: 20px;
}
.template-head h2 {
  margin: 0;
  font-size: 18px;
  color: #1f2d3d;
}
.template-head p {
  margin: 4px 0 0;
  color: #8a94a6;
  font-size: 12px;
}
.template-type-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}
.template-type-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #e3e8f0;
  border-radius: 10px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
}
.template-type-card:hover {
  border-color: #b7d9d3;
  box-shadow: 0 4px 14px rgba(15, 118, 110, 0.08);
}
.template-type-card.active {
  border-color: #0f766e;
  background: #f4fbfa;
  box-shadow: 0 4px 14px rgba(15, 118, 110, 0.12);
}
.template-type-icon {
  display: grid;
  place-items: center;
  flex: none;
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: #f1f5f9;
  color: #536174;
  font-weight: 600;
}
.template-type-card.active .template-type-icon {
  background: #0f766e;
  color: #fff;
}
.template-type-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.template-type-text strong {
  color: #303133;
  font-size: 14px;
}
.template-type-text small {
  color: #8a94a6;
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.template-direction {
  margin-bottom: 10px;
}
.template-section-title {
  font-weight: 600;
  color: #303133;
  font-size: 14px;
}
.template-filters {
  display: flex;
  gap: 8px;
}
.template-filters :deep(.el-select) {
  width: 200px;
}
.template-table {
  width: 100%;
  --el-table-header-bg-color: #f5f6fa;
  --el-table-border-color: #e3e8f0;
  --el-table-row-hover-bg-color: #f4fbfa;
  font-size: 13px;
}
.template-table :deep(.el-table__cell) {
  padding: 8px 0;
}
.template-table :deep(th.el-table__cell) {
  color: #536174;
  font-weight: 500;
}
.template-name {
  color: #1f2d3d;
  font-weight: 500;
}
.template-default {
  margin-left: 6px;
}
.template-global {
  color: #0f766e;
  font-weight: 600;
}
.template-error {
  margin-bottom: 12px;
}
@media (max-width: 720px) {
  .template-head {
    align-items: flex-start;
    flex-direction: column;
  }
  .template-filters :deep(.el-select) {
    width: 150px;
  }
}
</style>
