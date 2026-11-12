<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { financeApi, type FinanceAudit } from '../services/financeApi'
import '../assets/purchase.css'

const rows = ref<FinanceAudit[]>([])
const loading = ref(false)
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)
const entityType = ref('')
const action = ref('')
const keyword = ref('')
const dates = ref<string[]>([])
const entityLabels: Record<string, string> = {
  PAYMENT: '收付款',
  ALLOCATION: '核销',
  STATEMENT: '对账单',
  OPEN_ITEM: '应收应付',
  PROFILE: '结算档案',
  STATEMENT_TEMPLATE: '文档模板',
}
const actionLabels: Record<string, string> = {
  CREATE: '创建',
  VOID: '作废',
  ALLOCATE: '核销',
  REVERSE: '反核销',
  MARK_PREPAYMENT: '标记预付款',
  PROJECT: '生成',
  REFRESH_BASIS: '刷新账单依据',
  REBUILD: '历史回填',
  RECALC_BILLING: '重算账单',
  SAVE: '保存',
  SYNC_DEFAULT: '同步默认方案',
  AUTO_CREATE: '自动出单',
  SEND: '发送',
  CONFIRM: '确认',
  VOID_BY_REVERSAL: '撤销来源自动作废',
  EXPORT: '导出',
  DEFAULT: '设为默认',
  DELETE: '删除',
}

async function load() {
  loading.value = true
  try {
    const page = await financeApi.audits({
      entityType: entityType.value || undefined,
      action: action.value.trim() || undefined,
      keyword: keyword.value.trim() || undefined,
      startDate: dates.value[0] || undefined,
      endDate: dates.value[1] || undefined,
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    })
    rows.value = page.records
    total.value = page.total
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}
function search() {
  pageNo.value = 1
  void load()
}
function reset() {
  entityType.value = ''
  action.value = ''
  keyword.value = ''
  dates.value = []
  search()
}
onMounted(load)
</script>

<template>
  <div class="finance-audit-page">
    <div class="finance-audit-toolbar">
      <h2>财务审计</h2>
      <div class="finance-audit-filters">
        <el-select v-model="entityType" clearable placeholder="单据类型" style="width: 140px">
          <el-option v-for="(label, value) in entityLabels" :key="value" :value="value" :label="label" />
        </el-select>
        <el-input v-model="action" clearable placeholder="动作" style="width: 120px" @keyup.enter="search" />
        <el-input v-model="keyword" clearable placeholder="单号 / 摘要关键词" style="width: 190px" @keyup.enter="search" />
        <el-date-picker
          v-model="dates"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 250px"
        />
        <el-button :icon="Search" type="primary" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="reset">重置</el-button>
      </div>
    </div>
    <el-table v-loading="loading" :data="rows" border height="calc(100vh - 250px)" empty-text="暂无财务审计记录">
      <el-table-column prop="createdAt" label="时间" width="170" />
      <el-table-column label="单据类型" width="110">
        <template #default="{ row }">{{ entityLabels[row.entityType] || row.entityType }}</template>
      </el-table-column>
      <el-table-column prop="entityId" label="单据ID" width="100" />
      <el-table-column label="操作" width="170">
        <template #default="{ row }">{{ actionLabels[row.action] || row.action }}</template>
      </el-table-column>
      <el-table-column prop="actorName" label="操作人" width="120" />
      <el-table-column prop="departmentName" label="部门" width="120" />
      <el-table-column prop="detail" label="摘要" min-width="260" show-overflow-tooltip />
    </el-table>
    <div class="finance-audit-pagination">
      <el-pagination
        v-model:current-page="pageNo"
        v-model:page-size="pageSize"
        :page-sizes="[20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @change="load"
      />
    </div>
  </div>
</template>

<style scoped>
.finance-audit-page {
  padding: 18px 22px;
  height: 100%;
  box-sizing: border-box;
}
.finance-audit-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}
.finance-audit-toolbar h2 {
  margin: 0;
  font-size: 18px;
  white-space: nowrap;
}
.finance-audit-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.finance-audit-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}
</style>
