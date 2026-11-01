<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Plus, Search, Refresh, Edit, Delete } from '@element-plus/icons-vue'
import {
  fetchBrands,
  saveBrand,
  deleteBrandRecord,
  type BrandRecord,
} from '../../services/brandApi'
import '../../assets/purchase.css'

const brands = ref<BrandRecord[]>([])
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const enabledFilter = ref('')
const page = ref(1)
const pageSize = ref(20)
const enabledOptions = [
  { value: 'enabled', label: '启用' },
  { value: 'disabled', label: '停用' },
]

const filtered = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return brands.value.filter(
    (row) =>
      (!enabledFilter.value ||
        (enabledFilter.value === 'enabled' ? row.enabled : !row.enabled)) &&
      [row.code, row.name, row.manufacturer, row.remark]
        .join(' ')
        .toLowerCase()
        .includes(query),
  )
})
const pageRows = computed(() =>
  filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value),
)
watch([keyword, enabledFilter, pageSize], () => {
  page.value = 1
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    brands.value = await fetchBrands()
    page.value = Math.min(page.value, Math.max(1, Math.ceil(filtered.value.length / pageSize.value)))
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}
onMounted(load)

const editor = ref(false)
const editingId = ref<number>()
const version = ref<number>()
const saving = ref(false)
const form = ref<FormInstance>()
const draft = ref({
  code: '',
  name: '',
  manufacturer: '',
  enabled: true,
  remark: '',
})
const editorTitle = computed(() => `${editingId.value ? '编辑' : '新增'}品牌`)
const rules = {
  name: [{ required: true, whitespace: true, message: '请填写品牌名称', trigger: 'blur' }],
}

function open(row?: BrandRecord) {
  editingId.value = row?.id
  version.value = row?.version
  draft.value = {
    code: row?.code || '',
    name: row?.name || '',
    manufacturer: row?.manufacturer || '',
    enabled: row?.enabled ?? true,
    remark: row?.remark || '',
  }
  editor.value = true
  void nextTick(() => form.value?.clearValidate())
}
async function submit() {
  if (!(await form.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    await saveBrand(
      { ...draft.value, name: draft.value.name.trim(), version: version.value },
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
async function remove(row: BrandRecord) {
  try {
    await ElMessageBox.confirm(`确定删除品牌“${row.name}”吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteBrandRecord(row)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    if (e instanceof Error) ElMessage.error(e.message)
  }
}
</script>

<template>
  <section class="purchase-page brand-page">
    <div class="brand-toolbar">
      <div class="brand-actions">
        <el-button type="primary" :icon="Plus" @click="open()">添加品牌</el-button>
      </div>
      <div class="brand-filters">
        <el-select
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
        <el-input
          v-model="keyword"
          clearable
          :prefix-icon="Search"
          placeholder="搜索编码、品牌、原厂"
          aria-label="搜索品牌"
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
      class="brand-table"
      height="100%"
      empty-text="暂无品牌"
      @row-dblclick="open"
    >
      <el-table-column
        type="index"
        label="#"
        width="48"
        :index="(index: number) => (page - 1) * pageSize + index + 1"
      />
      <el-table-column prop="code" label="编码" width="130" show-overflow-tooltip />
      <el-table-column prop="name" label="品牌名称" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <el-button link type="primary" @click="open(row)">{{ row.name }}</el-button>
        </template>
      </el-table-column>
      <el-table-column prop="manufacturer" label="原厂/生产厂商" min-width="220" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">{{
            row.enabled ? '启用' : '停用'
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="240" show-overflow-tooltip />
      <el-table-column label="操作" width="105" fixed="right" align="center">
        <template #default="{ row }">
          <div class="brand-row-actions">
            <el-button :icon="Edit" text title="编辑" aria-label="编辑" @click="open(row)" />
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
    <footer class="brand-pagination">
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
    width="640px"
    class="purchase-dialog brand-editor"
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    @close="editor = false"
  >
    <el-form
      ref="form"
      :model="draft"
      :rules="rules"
      label-position="top"
      :disabled="saving"
      class="brand-form"
    >
      <el-form-item label="品牌名称" prop="name">
        <el-input v-model="draft.name" maxlength="64" />
      </el-form-item>
      <el-form-item label="品牌编码">
        <el-input v-model="draft.code" maxlength="32" placeholder="留空则不填写" />
      </el-form-item>
      <el-form-item label="原厂/生产厂商">
        <el-input v-model="draft.manufacturer" maxlength="128" />
      </el-form-item>
      <el-form-item label="状态">
        <el-switch v-model="draft.enabled" active-text="启用" inactive-text="停用" inline-prompt />
      </el-form-item>
      <el-form-item label="备注" class="brand-full">
        <el-input v-model="draft.remark" type="textarea" :rows="3" maxlength="255" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="saving" @click="editor = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.brand-page {
  height: calc(100vh - 122px);
  min-height: 440px;
}
.brand-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 0;
}
.brand-actions,
.brand-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.brand-filters > .el-select {
  width: 120px;
}
.brand-filters > .el-input {
  width: 260px;
}
.brand-table {
  flex: 1;
  min-height: 0;
  width: 100%;
  font-size: 13px;
  --el-table-header-bg-color: #f5f6fa;
  --el-table-tr-bg-color: #fff;
}
.brand-table :deep(.el-table__row--striped > td.el-table__cell) {
  background: #eef2f6;
}
.brand-table :deep(.cell) {
  line-height: 24px;
}
.brand-row-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
}
.brand-row-actions .el-button {
  padding: 6px 8px;
}
.brand-pagination {
  display: flex;
  justify-content: flex-end;
  overflow-x: auto;
  padding: 12px 0;
}
.brand-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 20px;
}
.brand-full {
  grid-column: 1 / -1;
}
@media (max-width: 650px) {
  .brand-form {
    grid-template-columns: minmax(0, 1fr);
  }
  .brand-filters {
    width: 100%;
  }
  .brand-filters > .el-input {
    flex: 1;
    min-width: 180px;
  }
}
</style>
