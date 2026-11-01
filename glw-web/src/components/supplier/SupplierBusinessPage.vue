<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Plus, Search, Refresh, Edit, Delete, Document } from '@element-plus/icons-vue'
import {
  fetchSuppliers,
  fetchSupplierBrands,
  saveSupplier,
  saveSupplierBrand,
  deleteSupplierRecord,
  deleteSupplierBrand,
  type SupplierRecord,
  type SupplierBrandRecord,
} from '../../services/supplierApi'
import { fetchBrandOptions, type BrandOption } from '../../services/brandApi'
import '../../assets/purchase.css'

const props = defineProps<{ kind: 'suppliers' | 'brands' }>()
const route = useRoute()
const router = useRouter()
const isSupplier = computed(() => props.kind === 'suppliers')

const suppliers = ref<SupplierRecord[]>([])
const brands = ref<SupplierBrandRecord[]>([])
const brandOptions = ref<BrandOption[]>([])
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const enabledFilter = ref('')
const supplierFilter = ref<number>()
const page = ref(1)
const pageSize = ref(20)
const title = computed(() => (isSupplier.value ? '供应商' : '供应商品牌'))

const categoryOptions = ['原厂', '代理商', '贸易商', '其他']
const enabledOptions = [
  { value: 'enabled', label: '启用' },
  { value: 'disabled', label: '停用' },
]

const filtered = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  if (isSupplier.value)
    return suppliers.value.filter(
      (row) =>
        (!enabledFilter.value ||
          (enabledFilter.value === 'enabled' ? row.enabled : !row.enabled)) &&
        [
          row.code,
          row.name,
          row.shortName,
          row.category,
          row.contact,
          row.contactPhone,
          row.address,
          row.taxNo,
          row.bankName,
          row.bankAccount,
          row.brands.join(' '),
        ]
          .join(' ')
          .toLowerCase()
          .includes(query),
    )
  return brands.value.filter(
    (row) =>
      (!supplierFilter.value || row.supplierId === supplierFilter.value) &&
      [row.supplierName, row.brandName, row.remark].join(' ').toLowerCase().includes(query),
  )
})
const pageRows = computed(() =>
  filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value),
)
watch([keyword, enabledFilter, supplierFilter, pageSize], () => {
  page.value = 1
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [supplierRows, brandRows, brandOptionRows] = await Promise.all([
      fetchSuppliers(),
      isSupplier.value ? Promise.resolve([] as SupplierBrandRecord[]) : fetchSupplierBrands(),
      fetchBrandOptions(),
    ])
    suppliers.value = supplierRows
    brands.value = brandRows
    brandOptions.value = brandOptionRows
    page.value = Math.min(page.value, Math.max(1, Math.ceil(filtered.value.length / pageSize.value)))
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}
onMounted(load)
watch(
  () => props.kind,
  () => {
    keyword.value = ''
    enabledFilter.value = ''
    supplierFilter.value = undefined
    page.value = 1
    void load()
  },
)
watch(
  () => route.query.supplierId,
  (value) => {
    supplierFilter.value = value ? Number(value) : undefined
  },
  { immediate: true },
)

const editor = ref(false)
const editingId = ref<number>()
const version = ref<number>()
const saving = ref(false)
const form = ref<FormInstance>()
const supplierDraft = ref({
  code: '',
  name: '',
  shortName: '',
  category: '',
  enabled: true,
  address: '',
  contact: '',
  contactPhone: '',
  taxNo: '',
  bankName: '',
  bankAccount: '',
  accountName: '',
  remark: '',
})
const brandDraft = ref({
  supplierId: undefined as number | undefined,
  brandId: undefined as number | undefined,
  remark: '',
})
const editorTitle = computed(() =>
  isSupplier.value
    ? `${editingId.value ? '编辑' : '新增'}供应商`
    : `${editingId.value ? '编辑' : '新增'}品牌关联`,
)
const rules = computed(() =>
  isSupplier.value
    ? {
        name: [{ required: true, whitespace: true, message: '请填写供应商名称', trigger: 'blur' }],
      }
    : {
        supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
        brandId: [{ required: true, message: '请选择品牌', trigger: 'change' }],
      },
)

function openSupplier(row?: SupplierRecord) {
  editingId.value = row?.id
  version.value = row?.version
  supplierDraft.value = {
    code: row?.code || '',
    name: row?.name || '',
    shortName: row?.shortName || '',
    category: row?.category || '',
    enabled: row?.enabled ?? true,
    address: row?.address || '',
    contact: row?.contact || '',
    contactPhone: row?.contactPhone || '',
    taxNo: row?.taxNo || '',
    bankName: row?.bankName || '',
    bankAccount: row?.bankAccount || '',
    accountName: row?.accountName || '',
    remark: row?.remark || '',
  }
  editor.value = true
  void nextTick(() => form.value?.clearValidate())
}
function openBrand(row?: SupplierBrandRecord, supplierId?: number) {
  editingId.value = row?.id
  version.value = row?.version
  brandDraft.value = {
    supplierId: row?.supplierId ?? supplierId ?? supplierFilter.value,
    brandId: row?.brandId,
    remark: row?.remark || '',
  }
  if (!suppliers.value.length) void load()
  if (!brandOptions.value.length) void load()
  editor.value = true
  void nextTick(() => form.value?.clearValidate())
}
function openRecord(row?: SupplierRecord | SupplierBrandRecord) {
  if (isSupplier.value) openSupplier(row as SupplierRecord | undefined)
  else openBrand(row as SupplierBrandRecord | undefined)
}
function manageBrands(row: SupplierRecord) {
  void router.push({ path: '/supplier-brands', query: { supplierId: String(row.id) } })
}
async function submit() {
  if (!(await form.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    if (isSupplier.value)
      await saveSupplier(
        { ...supplierDraft.value, name: supplierDraft.value.name.trim(), version: version.value },
        editingId.value,
      )
    else
      await saveSupplierBrand(
        {
          ...brandDraft.value,
          supplierId: brandDraft.value.supplierId!,
          brandId: brandDraft.value.brandId!,
          version: version.value,
        },
        editingId.value,
      )
    ElMessage.success('已保存')
    editor.value = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}
async function remove(row: SupplierRecord | SupplierBrandRecord) {
  try {
    await ElMessageBox.confirm(
      isSupplier.value
        ? `删除供应商“${(row as SupplierRecord).name}”会同时删除其品牌关联，确定删除吗？`
        : `确定删除品牌关联“${(row as SupplierBrandRecord).brandName}”吗？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
    if (isSupplier.value) await deleteSupplierRecord(row)
    else await deleteSupplierBrand(row)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    if (e instanceof Error) ElMessage.error(e.message)
  }
}
function supplierLabel(id?: number | null) {
  const supplier = suppliers.value.find((row) => row.id === id)
  return supplier ? supplier.shortName || supplier.name : ''
}
</script>

<template>
  <section class="purchase-page supplier-business-page">
    <div class="supplier-business-toolbar">
      <div class="supplier-business-actions">
        <el-button type="primary" :icon="Plus" @click="isSupplier ? openSupplier() : openBrand()">
          {{ isSupplier ? '添加供应商' : '关联品牌' }}
        </el-button>
      </div>
      <div class="supplier-business-filters">
        <el-select
          v-if="isSupplier"
          v-model="enabledFilter"
          clearable
          placeholder="全部状态"
          aria-label="筛选状态"
        >
          <el-option
            v-for="option in enabledOptions"
            :key="option.value"
            :value="option.value"
            :label="option.label"
          />
        </el-select>
        <el-select
          v-else
          v-model="supplierFilter"
          clearable
          filterable
          placeholder="全部供应商"
          aria-label="筛选供应商"
        >
          <el-option
            v-for="supplier in suppliers"
            :key="supplier.id"
            :value="supplier.id"
            :label="supplier.shortName || supplier.name"
          />
        </el-select>
        <el-input
          v-model="keyword"
          clearable
          :prefix-icon="Search"
          :placeholder="isSupplier ? '搜索编码、名称、品牌、联系人' : '搜索供应商、品牌或备注'"
          aria-label="搜索记录"
        />
        <el-button :icon="Refresh" title="刷新" aria-label="刷新" :loading="loading" @click="load" />
      </div>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false">
      <el-button link @click="load">重试</el-button>
    </el-alert>
    <el-table
      v-else
      v-loading="loading"
      :data="pageRows"
      border
      stripe
      row-key="id"
      class="supplier-business-table"
      height="100%"
      :empty-text="isSupplier ? '暂无供应商' : '暂无供应商品牌'"
      @row-dblclick="(row: SupplierRecord | SupplierBrandRecord) => openRecord(row)"
    >
      <el-table-column
        type="index"
        label="#"
        width="48"
        :index="(index: number) => (page - 1) * pageSize + index + 1"
      />
      <template v-if="isSupplier">
        <el-table-column prop="code" label="编码" width="120" show-overflow-tooltip />
        <el-table-column prop="name" label="供应商名称" min-width="210" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button link type="primary" @click="openSupplier(row)">{{ row.name }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="shortName" label="简称" width="110" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="95" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="row.category" effect="plain">{{ row.category }}</el-tag>
            <span v-else class="supplier-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="品牌" min-width="190">
          <template #default="{ row }">
            <div v-if="row.brands.length" class="supplier-brand-tags">
              <el-tag v-for="brand in row.brands" :key="brand" size="small" effect="plain">{{
                brand
              }}</el-tag>
            </div>
            <span v-else class="supplier-muted">未维护</span>
          </template>
        </el-table-column>
        <el-table-column prop="contact" label="联系人" width="100" show-overflow-tooltip />
        <el-table-column prop="contactPhone" label="手机/电话" width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">{{
              row.enabled ? '启用' : '停用'
            }}</el-tag>
          </template>
        </el-table-column>
      </template>
      <template v-else>
        <el-table-column label="供应商" min-width="210" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button link type="primary" @click="openBrand(row)">{{ row.supplierName }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="brandName" label="品牌" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag effect="plain">{{ row.brandName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="260" show-overflow-tooltip />
      </template>
      <el-table-column label="操作" :width="isSupplier ? 190 : 105" fixed="right" align="center">
        <template #default="{ row }">
          <div class="supplier-row-actions">
            <el-button v-if="isSupplier" link type="primary" @click="manageBrands(row)"
              >品牌</el-button
            >
            <el-button :icon="Edit" text title="编辑" aria-label="编辑" @click="openRecord(row)" />
            <el-button
              :icon="Delete"
              text
              type="danger"
              title="删除"
              aria-label="删除"
              @click="remove(row)"
            />
          </div>
        </template>
      </el-table-column>
    </el-table>
    <footer class="supplier-business-pagination">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="filtered.length"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
      />
    </footer>
  </section>
  <el-dialog
    v-if="editor"
    :model-value="true"
    :title="editorTitle"
    width="820px"
    top="3vh"
    class="purchase-dialog supplier-business-editor"
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    @close="editor = false"
  >
    <el-form
      ref="form"
      :model="isSupplier ? supplierDraft : brandDraft"
      :rules="rules"
      label-position="top"
      :disabled="saving"
      class="supplier-business-form"
    >
      <template v-if="isSupplier">
        <el-form-item label="供应商名称" prop="name" class="supplier-full">
          <el-input v-model="supplierDraft.name" maxlength="255" />
        </el-form-item>
        <el-form-item label="供应商编码">
          <el-input v-model="supplierDraft.code" maxlength="32" placeholder="留空则不填写" />
        </el-form-item>
        <el-form-item label="简称">
          <el-input v-model="supplierDraft.shortName" maxlength="64" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select
            v-model="supplierDraft.category"
            filterable
            allow-create
            default-first-option
            clearable
            placeholder="选择或输入分类"
          >
            <el-option v-for="option in categoryOptions" :key="option" :value="option" :label="option" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="supplierDraft.enabled"
            active-text="启用"
            inactive-text="停用"
            inline-prompt
          />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="supplierDraft.contact" maxlength="32" />
        </el-form-item>
        <el-form-item label="手机/电话">
          <el-input v-model="supplierDraft.contactPhone" maxlength="32" />
        </el-form-item>
        <el-form-item label="供应商地址" class="supplier-full">
          <el-input v-model="supplierDraft.address" maxlength="255" />
        </el-form-item>
        <el-form-item label="税号">
          <el-input v-model="supplierDraft.taxNo" maxlength="32" />
        </el-form-item>
        <el-form-item label="开户行">
          <el-input v-model="supplierDraft.bankName" maxlength="128" />
        </el-form-item>
        <el-form-item label="银行账号">
          <el-input v-model="supplierDraft.bankAccount" maxlength="64" />
        </el-form-item>
        <el-form-item label="开户户名">
          <el-input v-model="supplierDraft.accountName" maxlength="128" />
        </el-form-item>
        <el-form-item label="备注" class="supplier-full">
          <el-input v-model="supplierDraft.remark" type="textarea" :rows="3" maxlength="255" />
        </el-form-item>
      </template>
      <template v-else>
        <el-form-item label="供应商" prop="supplierId">
          <el-select v-model="brandDraft.supplierId" filterable placeholder="选择供应商">
            <el-option
              v-for="supplier in suppliers"
              :key="supplier.id"
              :value="supplier.id"
              :label="supplier.name"
            >
              <span>{{ supplier.name }}</span>
              <span v-if="supplier.code" class="supplier-option-code">{{ supplier.code }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="品牌" prop="brandId">
          <div class="supplier-brand-picker">
            <el-select v-model="brandDraft.brandId" filterable placeholder="选择品牌">
              <el-option
                v-for="brand in brandOptions"
                :key="brand.id"
                :value="brand.id"
                :label="brand.name"
              >
                <span>{{ brand.name }}</span>
                <span v-if="brand.code" class="supplier-option-code">{{ brand.code }}</span>
              </el-option>
            </el-select>
            <el-button link type="primary" @click="router.push('/brands')">维护品牌</el-button>
          </div>
        </el-form-item>
        <el-form-item label="备注" class="supplier-full">
          <el-input v-model="brandDraft.remark" type="textarea" :rows="3" maxlength="255" />
        </el-form-item>
      </template>
    </el-form>
    <template #footer>
      <el-button :disabled="saving" @click="editor = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.supplier-business-page {
  height: calc(100vh - 122px);
  min-height: 440px;
}
.supplier-business-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 0;
}
.supplier-business-actions,
.supplier-business-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.supplier-business-filters > .el-select {
  width: 155px;
}
.supplier-business-filters > .el-input {
  width: 280px;
}
.supplier-business-table {
  flex: 1;
  min-height: 0;
  width: 100%;
  font-size: 13px;
  --el-table-header-bg-color: #f5f6fa;
  --el-table-tr-bg-color: #fff;
}
.supplier-business-table :deep(.el-table__row--striped > td.el-table__cell) {
  background: #eef2f6;
}
.supplier-business-table :deep(.cell) {
  line-height: 24px;
}
.supplier-brand-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.supplier-muted {
  color: #8491a4;
}
.supplier-row-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: nowrap;
  gap: 2px;
}
.supplier-row-actions .el-button {
  flex-shrink: 0;
  padding: 6px 8px;
}
.supplier-business-pagination {
  display: flex;
  justify-content: flex-end;
  overflow-x: auto;
  padding: 12px 0;
}
.supplier-business-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 20px;
}
.supplier-full {
  grid-column: 1 / -1;
}
.supplier-option-code {
  float: right;
  color: #8491a4;
  font-size: 12px;
}
.supplier-brand-picker {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.supplier-brand-picker > .el-select {
  flex: 1;
  min-width: 0;
}
@media (max-width: 650px) {
  .supplier-business-form {
    grid-template-columns: minmax(0, 1fr);
  }
  .supplier-business-filters {
    width: 100%;
  }
  .supplier-business-filters > .el-input {
    flex: 1;
    min-width: 190px;
  }
}
</style>
