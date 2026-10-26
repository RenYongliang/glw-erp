<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Plus, Search, Refresh, Download, Edit, Delete, Document } from '@element-plus/icons-vue'
import { fetchCrmCustomers, fetchCrmVisits, saveCustomer, saveVisit, deleteCrmRecord,
  categoryOptions, stageOptions, settlementOptions, settlementLabel, type CustomerRecord, type VisitRecord } from '../../services/crmApi'
import { fetchSalesProducts } from '../../services/productApi'
import type { SalesProductOption } from '../../services/productApiData'
import { encodeCsv } from '../../domain/crm'
import { today } from '../../domain/purchase'
import '../../assets/purchase.css'

const props = defineProps<{ kind: 'customers' | 'followups' }>()
const isCustomer = computed(() => props.kind === 'customers')
const customers = ref<CustomerRecord[]>([])
const visits = ref<VisitRecord[]>([])
const products = ref<SalesProductOption[]>([])
const loading = ref(false), error = ref(''), keyword = ref(''), settlement = ref('')
const category = ref(''), stage = ref('')
const filterCustomer = ref<number>(), dates = ref<string[]>([])
const page = ref(1), pageSize = ref(20)
const title = computed(() => isCustomer.value ? '客户' : '跟进记录')
const filtered = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  if (isCustomer.value) return customers.value.filter(row => (!settlement.value || row.settleType === settlement.value)
    && (!category.value || (category.value === 'unclassified' ? !row.category : row.category === category.value))
    && [row.name, row.shortName, row.category, row.address, row.contact, row.contactPhone, settlementLabel(row.settleType)].join(' ').toLowerCase().includes(query))
  return visits.value.filter(row => (!filterCustomer.value || row.customerId === filterCustomer.value)
    && (!stage.value || row.stage === stage.value)
    && (!dates.value?.length || (row.visitTime.slice(0, 10) >= dates.value[0]! && row.visitTime.slice(0, 10) <= dates.value[1]!))
    && [row.customerName, row.customerShortName, row.project, row.originalPartNo, row.brand, row.partNo, row.packageType, row.stage, row.progress, row.detail].join(' ').toLowerCase().includes(query))
})
const pageRows = computed(() => filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const settlements = computed(() => [...settlementOptions, ...[...new Set(customers.value.map(row => row.settleType).filter((value): value is string => !!value))]
  .filter(value => !settlementOptions.some(option => option.value === value)).map(value => ({ value, label: value }))])
watch([keyword, settlement, category, stage, filterCustomer, dates, pageSize], () => { page.value = 1 })
async function load() {
  loading.value = true
  error.value = ''
  try {
    const [customerRows, visitRows, productRows] = await Promise.all([
      fetchCrmCustomers(), isCustomer.value ? Promise.resolve([]) : fetchCrmVisits(),
      isCustomer.value ? Promise.resolve([]) : fetchSalesProducts(),
    ])
    customers.value = customerRows
    visits.value = visitRows
    products.value = productRows
    page.value = Math.min(page.value, Math.max(1, Math.ceil(filtered.value.length / pageSize.value)))
  } catch (e) { error.value = (e as Error).message }
  finally { loading.value = false }
}
onMounted(load)
watch(() => props.kind, () => { keyword.value = ''; settlement.value = ''; filterCustomer.value = undefined; dates.value = []; page.value = 1; void load() })

const editor = ref(false), editingCustomer = ref(false), readonly = ref(false), saving = ref(false)
const editingId = ref<number>(), version = ref<number>(), form = ref<FormInstance>()
const customerDraft = ref({ name: '', shortName: '', category: '', address: '', contact: '', contactPhone: '', settleType: '' })
const visitDraft = ref({ customerId: undefined as number | undefined, visitTime: '', project: '', originalPartNo: '',
  productId: undefined as number | undefined, demandQuantity: undefined as number | undefined,
  unitPrice: undefined as number | undefined, taxIncluded: 1, progress: '', stage: '初次沟通', detail: '' })
const editorTitle = computed(() => `${readonly.value ? '' : editingId.value ? '编辑' : '新增'}${editingCustomer.value ? '客户' : '跟进记录'}${readonly.value ? '详情' : ''}`)
const rules = computed(() => editingCustomer.value ? {
  name: [{ required: true, whitespace: true, message: '请填写客户名称', trigger: 'blur' }],
} : {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  visitTime: [{ required: true, message: '请选择拜访时间', trigger: 'change' }],
  unitPrice: [{ required: true, message: '请填写产品报价', trigger: 'blur' }],
  taxIncluded: [{ required: true, message: '请选择是否含税', trigger: 'change' }],
})
function openCustomer(row?: CustomerRecord, read = false) {
  editingCustomer.value = true; readonly.value = read; editingId.value = row?.id; version.value = row?.version
  customerDraft.value = { name: row?.name || '', shortName: row?.shortName || '', category: row?.category || '', address: row?.address || '',
    contact: row?.contact || '', contactPhone: row?.contactPhone || '', settleType: row?.settleType || '' }
  editor.value = true
}
function openVisit(row?: VisitRecord, read = false, customerId?: number) {
  editingCustomer.value = false; readonly.value = read; editingId.value = row?.id; version.value = row?.version
  visitDraft.value = { customerId: row?.customerId || customerId, visitTime: row?.visitTime || `${today()}T09:00:00`,
    project: row?.project || '', originalPartNo: row?.originalPartNo || '', productId: row?.productId ?? undefined,
    demandQuantity: row?.demandQuantity ?? undefined, unitPrice: row ? Number(row.unitPrice) : undefined,
    taxIncluded: row?.taxIncluded ?? 1, progress: row?.progress || '', stage: row ? row.stage || '' : '初次沟通', detail: row?.detail || '' }
  editor.value = true
  if (!products.value.length) void fetchSalesProducts().then(rows => { products.value = rows }).catch(e => ElMessage.error(e.message))
}
function openRecord(row?: CustomerRecord | VisitRecord, read = false) {
  if (isCustomer.value) openCustomer(row as CustomerRecord | undefined, read)
  else openVisit(row as VisitRecord | undefined, read)
}
async function submit() {
  if (!await form.value?.validate().catch(() => false)) return
  saving.value = true
  try {
    if (editingCustomer.value) await saveCustomer({ ...customerDraft.value, name: customerDraft.value.name.trim(), version: version.value }, editingId.value)
    else await saveVisit({ ...visitDraft.value, customerId: visitDraft.value.customerId!, productId: visitDraft.value.productId ?? null,
      demandQuantity: visitDraft.value.demandQuantity ?? null, unitPrice: visitDraft.value.unitPrice!, version: version.value }, editingId.value)
    ElMessage.success('已保存'); editor.value = false; await load()
  } catch (e) { ElMessage.error((e as Error).message) }
  finally { saving.value = false }
}
async function remove(row: CustomerRecord | VisitRecord) {
  try {
    await ElMessageBox.confirm(`确定删除这条${title.value}记录吗？`, '删除确认', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await deleteCrmRecord(props.kind, row); ElMessage.success('已删除'); await load()
  } catch (e) { if (e instanceof Error) ElMessage.error(e.message) }
}
function exportRows() {
  const rows = isCustomer.value ? [['客户名称', '客户简称', '客户分类', '联系人', '手机', '客户地址', '结算方式'], ...filtered.value.map(value => {
    const row = value as CustomerRecord; return [row.name, row.shortName, categoryOptions.find(option => option.value === row.category)?.label || '未分类', row.contact, row.contactPhone, row.address, settlementLabel(row.settleType)]
  })] : [['客户', '跟进阶段', '拜访时间', '项目应用', '原用物料型号', '品牌', '推荐型号', '封装', '项目用量', '产品报价', '是否含税', '进度', '详细内容'], ...filtered.value.map(value => {
    const row = value as VisitRecord; return [row.customerName, row.stage, row.visitTime, row.project, row.originalPartNo, row.brand, row.partNo, row.packageType, row.demandQuantity, row.unitPrice, row.taxIncluded === 1 ? '含税' : '未税', row.progress, row.detail]
  })]
  const url = URL.createObjectURL(new Blob([encodeCsv(rows)], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a'); link.href = url; link.download = `${title.value}.csv`; link.click(); URL.revokeObjectURL(url)
}
</script>

<template>
  <section class="purchase-page crm-business-page">
    <nav class="purchase-view-tabs crm-category-tabs" aria-label="分类视图">
      <template v-if="isCustomer">
        <button :class="{ active: !category }" @click="category = ''"><el-icon><Document /></el-icon>全部客户 <small>{{ customers.length }}</small></button>
        <button v-for="option in categoryOptions" :key="option.value" :class="{ active: category === option.value }" @click="category = option.value">{{ option.label }} <small>{{ customers.filter(row => row.category === option.value).length }}</small></button>
        <button :class="{ active: category === 'unclassified' }" @click="category = 'unclassified'">未分类 <small>{{ customers.filter(row => !row.category).length }}</small></button>
      </template>
      <template v-else>
        <button :class="{ active: !stage }" @click="stage = ''"><el-icon><Document /></el-icon>全部跟进记录 <small>{{ visits.length }}</small></button>
        <button v-for="option in stageOptions" :key="option.value" :class="{ active: stage === option.value }" @click="stage = option.value">{{ option.value }} <small>{{ visits.filter(row => row.stage === option.value).length }}</small></button>
      </template>
    </nav>
    <div class="crm-business-toolbar">
      <div class="crm-business-actions"><el-button type="primary" :icon="Plus" @click="openRecord()">添加</el-button>
        <el-button text :icon="Download" :disabled="!filtered.length" @click="exportRows">导出</el-button></div>
      <div class="crm-business-filters">
        <el-select v-if="isCustomer" v-model="settlement" clearable placeholder="全部结算方式" aria-label="筛选结算方式"><el-option v-for="option in settlements" :key="option.value" :value="option.value" :label="option.label" /></el-select>
        <template v-else>
          <el-select v-model="filterCustomer" clearable filterable placeholder="全部客户" aria-label="筛选客户"><el-option v-for="customer in customers" :key="customer.id" :value="customer.id" :label="customer.shortName || customer.name" /></el-select>
          <el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="拜访开始日期" end-placeholder="结束日期" aria-label="拜访日期范围" />
        </template>
        <el-input v-model="keyword" clearable :prefix-icon="Search" :placeholder="isCustomer ? '搜索客户、联系人、手机或地址' : '搜索项目、物料、进度或内容'" aria-label="搜索记录" />
        <el-button :icon="Refresh" title="刷新" aria-label="刷新" :loading="loading" @click="load" />
      </div>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button link @click="load">重试</el-button></el-alert>
    <el-table v-else v-loading="loading" :data="pageRows" border stripe row-key="id" class="crm-business-table" height="100%" @row-dblclick="(row: CustomerRecord | VisitRecord) => openRecord(row, true)">
      <el-table-column type="index" label="#" width="48" :index="(index: number) => (page - 1) * pageSize + index + 1" />
      <template v-if="isCustomer">
        <el-table-column prop="name" label="客户名称" min-width="220" show-overflow-tooltip><template #default="{ row }"><el-button link type="primary" @click="openCustomer(row, true)">{{ row.name }}</el-button></template></el-table-column>
        <el-table-column prop="shortName" label="客户简称" min-width="120" show-overflow-tooltip />
        <el-table-column label="客户分类" width="110"><template #default="{ row }"><el-tag v-if="row.category" effect="plain" :style="{ color: categoryOptions.find(option => option.value === row.category)?.color }">{{ categoryOptions.find(option => option.value === row.category)?.label || row.category }}</el-tag><span v-else class="crm-unclassified">未分类</span></template></el-table-column>
        <el-table-column prop="contact" label="联系人" width="100" show-overflow-tooltip />
        <el-table-column prop="contactPhone" label="手机" width="135" />
        <el-table-column prop="address" label="客户地址" min-width="220" show-overflow-tooltip />
        <el-table-column label="结算方式" width="115" show-overflow-tooltip><template #default="{ row }">{{ settlementLabel(row.settleType) }}</template></el-table-column>
      </template>
      <template v-else>
        <el-table-column label="客户" min-width="140" show-overflow-tooltip><template #default="{ row }"><span :title="row.customerName">{{ row.customerShortName || row.customerName || '—' }}</span></template></el-table-column>
        <el-table-column label="跟进阶段" width="115"><template #default="{ row }"><el-tag v-if="row.stage" effect="plain" :style="{ color: stageOptions.find(option => option.value === row.stage)?.color }">{{ row.stage }}</el-tag><span v-else class="crm-unclassified">未填写</span></template></el-table-column>
        <el-table-column label="拜访时间" width="165"><template #default="{ row }">{{ row.visitTime.replace('T', ' ') }}</template></el-table-column>
        <el-table-column prop="project" label="项目应用" min-width="145" show-overflow-tooltip />
        <el-table-column prop="originalPartNo" label="原用物料型号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="brand" label="品牌" width="85" show-overflow-tooltip />
        <el-table-column prop="partNo" label="推荐型号" min-width="105" show-overflow-tooltip />
        <el-table-column prop="packageType" label="封装" width="160" show-overflow-tooltip />
        <el-table-column prop="demandQuantity" label="项目用量" width="110" align="right"><template #default="{ row }">{{ row.demandQuantity?.toLocaleString('zh-CN') ?? '—' }}</template></el-table-column>
        <el-table-column label="产品报价" width="115" align="right"><template #default="{ row }">{{ Number(row.unitPrice).toFixed(4) }}</template></el-table-column>
        <el-table-column label="是否含税" width="85"><template #default="{ row }">{{ row.taxIncluded === 1 ? '含税' : '未税' }}</template></el-table-column>
        <el-table-column prop="progress" label="进度" min-width="130" show-overflow-tooltip />
        <el-table-column prop="detail" label="详细内容" min-width="220" show-overflow-tooltip />
      </template>
      <el-table-column label="操作" :width="isCustomer ? 175 : 105" fixed="right" align="center"><template #default="{ row }">
        <div class="crm-row-actions">
        <el-button v-if="isCustomer" link type="primary" @click="openVisit(undefined, false, row.id)">跟进</el-button>
        <el-button :icon="Edit" text title="编辑" aria-label="编辑" @click="openRecord(row)" />
        <el-button :icon="Delete" text type="danger" title="删除" aria-label="删除" @click="remove(row)" />
        </div>
      </template></el-table-column>
    </el-table>
    <footer class="crm-business-pagination"><el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="filtered.length" :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next" /></footer>
  </section>
  <el-dialog v-if="editor" :model-value="true" :title="editorTitle" width="820px" top="3vh" class="purchase-dialog crm-business-editor" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" @close="editor = false">
    <el-form ref="form" :model="editingCustomer ? customerDraft : visitDraft" :rules="rules" label-position="top" :disabled="readonly || saving" class="crm-business-form">
      <template v-if="editingCustomer">
        <el-form-item label="客户名称" prop="name" class="crm-full"><el-input v-model="customerDraft.name" maxlength="255" /></el-form-item>
        <el-form-item label="客户简称"><el-input v-model="customerDraft.shortName" maxlength="64" /></el-form-item>
        <el-form-item label="客户分类"><el-select v-model="customerDraft.category" clearable placeholder="选择客户分类"><el-option v-for="option in categoryOptions" :key="option.value" :value="option.value" :label="option.label" /></el-select></el-form-item>
        <el-form-item label="结算方式"><el-select v-model="customerDraft.settleType" filterable allow-create default-first-option clearable><el-option v-for="option in settlements" :key="option.value" :value="option.value" :label="option.label" /></el-select></el-form-item>
        <el-form-item label="联系人"><el-input v-model="customerDraft.contact" maxlength="32" /></el-form-item>
        <el-form-item label="联系人手机"><el-input v-model="customerDraft.contactPhone" maxlength="11" /></el-form-item>
        <el-form-item label="客户地址" class="crm-full"><el-input v-model="customerDraft.address" maxlength="255" /></el-form-item>
      </template>
      <template v-else>
        <el-form-item label="客户" prop="customerId"><el-select v-model="visitDraft.customerId" filterable placeholder="选择客户"><el-option v-for="customer in customers" :key="customer.id" :label="customer.name" :value="customer.id" /></el-select></el-form-item>
        <el-form-item label="拜访时间" prop="visitTime"><el-date-picker v-model="visitDraft.visitTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY-MM-DD HH:mm" /></el-form-item>
        <el-form-item label="跟进阶段"><el-select v-model="visitDraft.stage" clearable placeholder="选择跟进阶段"><el-option v-for="option in stageOptions" :key="option.value" :value="option.value" :label="option.value" /></el-select></el-form-item>
        <el-form-item label="项目应用"><el-input v-model="visitDraft.project" maxlength="64" /></el-form-item>
        <el-form-item label="原用物料型号"><el-input v-model="visitDraft.originalPartNo" maxlength="64" /></el-form-item>
        <el-form-item label="推荐产品" class="crm-full"><el-select v-model="visitDraft.productId" filterable clearable placeholder="选择推荐产品"><el-option v-for="product in products" :key="product.id" :value="Number(product.id)" :label="[product.brand, product.name, product.model].filter(Boolean).join(' / ')" /></el-select></el-form-item>
        <el-form-item label="项目用量"><el-input-number v-model="visitDraft.demandQuantity" :min="0" :max="2147483647" :precision="0" controls-position="right" /></el-form-item>
        <el-form-item label="产品报价" prop="unitPrice"><el-input-number v-model="visitDraft.unitPrice" :min="0" :max="99999999.9999" :precision="4" :step="0.0001" controls-position="right" /></el-form-item>
        <el-form-item label="是否含税" prop="taxIncluded"><el-radio-group v-model="visitDraft.taxIncluded"><el-radio-button :value="1">含税</el-radio-button><el-radio-button :value="0">未税</el-radio-button></el-radio-group></el-form-item>
        <el-form-item label="进度"><el-input v-model="visitDraft.progress" maxlength="64" /></el-form-item>
        <el-form-item label="详细内容" class="crm-full"><el-input v-model="visitDraft.detail" type="textarea" :rows="5" maxlength="16000" /></el-form-item>
      </template>
    </el-form>
    <template #footer><el-button :disabled="saving" @click="editor = false">{{ readonly ? '关闭' : '取消' }}</el-button>
      <el-button v-if="readonly" type="primary" :icon="Edit" @click="readonly = false">编辑</el-button>
      <el-button v-else type="primary" :loading="saving" @click="submit">保存</el-button></template>
  </el-dialog>
</template>

<style scoped>
.crm-business-page { height: calc(100vh - 122px); min-height: 440px; }
.crm-category-tabs { flex-shrink: 0; overflow-x: auto; }
.crm-category-tabs button { white-space: nowrap; flex-shrink: 0; }
.crm-category-tabs small { margin-left: 4px; color: #8491a4; font-size: 12px; font-weight: 400; }
.crm-unclassified { color: #8491a4; }
.crm-business-toolbar, .crm-business-actions, .crm-business-filters { display: flex; align-items: center; gap: 8px; }
.crm-business-toolbar { justify-content: space-between; flex-wrap: wrap; padding: 12px 0; }
.crm-business-filters { flex-wrap: wrap; }
.crm-business-filters > .el-select { width: 155px; }
.crm-business-filters > .el-input { width: 260px; }
.crm-business-filters :deep(.el-date-editor) { width: 270px; flex-grow: 0; }
.crm-business-table { flex: 1; min-height: 0; width: 100%; font-size: 13px; --el-table-header-bg-color: #f5f6fa; --el-table-tr-bg-color: #fff; }
.crm-business-table :deep(.el-table__row--striped > td.el-table__cell) { background: #eef2f6; }
.crm-business-table :deep(.cell) { line-height: 24px; }
.crm-business-table :deep(.el-button) { max-width: 100%; margin-left: 0; }
.crm-business-table :deep(.el-button > span) { overflow: hidden; text-overflow: ellipsis; }
.crm-row-actions { display: flex; align-items: center; justify-content: center; flex-wrap: nowrap; gap: 2px; }
.crm-row-actions .el-button { flex-shrink: 0; padding: 6px 8px; }
.crm-business-pagination { display: flex; justify-content: flex-end; overflow-x: auto; padding: 12px 0; }
.crm-business-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 20px; }
.crm-full { grid-column: 1 / -1; }
@media (max-width: 650px) {
  .crm-business-form { grid-template-columns: minmax(0, 1fr); }
  .crm-business-filters { width: 100%; }
  .crm-business-filters > .el-input { flex: 1; min-width: 190px; }
}
</style>
