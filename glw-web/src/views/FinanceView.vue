<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh, Download, DocumentChecked } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import {
  financeApi as api,
  exportStatement,
  type Direction,
  type FinanceQuery,
  type OpenItem,
  type Statement,
  type Payment,
  type Profile,
  type Partner,
  type StatementDetail,
  type PaymentDetail,
  type PaymentInput,
  type Summary,
} from '../services/financeApi'
import FinanceItemTable from '../components/finance/FinanceItemTable.vue'
import '../assets/purchase.css'

const props = defineProps<{ kind: 'open-items' | 'statements' | 'payments' | 'profiles' }>()
const auth = useAuthStore()
const direction = ref<Direction>('AR'),
  partnerId = ref<number>(),
  period = ref(''),
  keyword = ref(''),
  status = ref('')
const unsettled = ref(false),
  overdue = ref(false),
  pendingBasis = ref(false),
  page = ref(1),
  size = ref(20),
  total = ref(0)
const rows = ref<OpenItem[]>([]),
  statements = ref<Statement[]>([]),
  payments = ref<Payment[]>([]),
  profiles = ref<Profile[]>([]),
  partners = ref<Partner[]>([])
const selected = ref<OpenItem[]>([]),
  loading = ref(false),
  busy = ref(false),
  error = ref('')
const summary = ref<Summary>()
const statementDetail = ref<StatementDetail>(),
  paymentDetail = ref<PaymentDetail>()
const statementOpen = ref(false),
  paymentOpen = ref(false),
  createOpen = ref(false),
  previewOpen = ref(false),
  profileOpen = ref(false)
const previewRows = ref<OpenItem[]>([]),
  statementRemark = ref('')
const paymentInput = ref<PaymentInput>(newPayment())
const profileInput = ref<Profile>(newProfile())
const eligible = ref<OpenItem[]>([]),
  eligiblePage = ref(1),
  eligibleTotal = ref(0),
  eligibleLoading = ref(false)
const allocating = ref<OpenItem>(),
  allocationAmount = ref<number>(),
  allocationKey = ref('')
let generation = 0,
  eligibleGeneration = 0,
  timer: ReturnType<typeof setTimeout> | undefined
const statusLabels: Record<string, string> = {
  OPEN: '待对账',
  IN_STATEMENT: '对账中',
  RECONCILED: '已确认',
  DRAFT: '草稿',
  SENT: '已发送',
  CONFIRMED: '已确认',
  VOID: '已作废',
  RECEIVED: '未核销',
  PARTIAL_ALLOCATED: '部分核销',
  ALLOCATED: '已核销',
  ACTIVE: '有效',
  REVERSED: '已反核销',
}
const statuses = computed(() =>
  props.kind === 'open-items'
    ? ['OPEN', 'IN_STATEMENT', 'RECONCILED']
    : props.kind === 'statements'
      ? ['DRAFT', 'SENT', 'CONFIRMED', 'VOID']
      : ['RECEIVED', 'PARTIAL_ALLOCATED', 'ALLOCATED', 'VOID'],
)
const money = (value: unknown) =>
  Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const label = (value: string) => statusLabels[value] || value
function query(): FinanceQuery {
  return {
    direction: direction.value,
    partnerId: partnerId.value,
    period: period.value || undefined,
    keyword: keyword.value.trim(),
    status: status.value || undefined,
    unsettledOnly: unsettled.value,
    overdueOnly: overdue.value,
    pendingBasisOnly: pendingBasis.value || undefined,
    pageNo: page.value,
    pageSize: size.value,
  }
}
/** 结算档案默认值：当月结上月、自然月、月结 0 天。 */
function newProfile(): Profile {
  return {
    direction: direction.value,
    cutoffDay: null,
    cycleMonths: 1,
    statementLagMonths: 1,
    statementDay: 1,
    dueRule: 'CREDIT_DAYS',
    creditDays: 0,
    creditBasis: 'PERIOD_END',
    dueMonthOffset: 0,
    dueDay: 0,
    autoGenerate: false,
    reconcileBasis: direction.value === 'AR' ? 'SIGNED' : 'INBOUND',
  }
}
/** 账单期间展示：2026-09 或 2026-08~2026-09。 */
function periodText(start?: string | null, end?: string | null) {
  if (!start || !end) return ''
  return start.slice(0, 7) === end.slice(0, 7) ? end.slice(0, 7) : `${start.slice(0, 7)}~${end.slice(0, 7)}`
}
const previewPeriod = computed(() => {
  const starts = previewRows.value.map((o) => o.periodStart).filter(Boolean) as string[]
  const ends = previewRows.value.map((o) => o.periodEnd).filter(Boolean) as string[]
  if (!starts.length || !ends.length) return ''
  return periodText(starts.sort()[0], ends.sort().at(-1))
})
function newPayment(): PaymentInput {
  const now = new Date(),
    date = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  return {
    requestKey: crypto.randomUUID(),
    direction: direction.value,
    partnerId: partnerId.value,
    paymentDate: date,
    amount: undefined,
    bankReference: '',
    remark: '',
  }
}
async function load() {
  const current = ++generation
  loading.value = true
  error.value = ''
  selected.value = []
  const q = query(),
    kind = props.kind
  try {
    const [options, result, totals] = await Promise.all([
      api.partners(q.direction),
      kind === 'open-items'
        ? api.openItems(q)
        : kind === 'statements'
          ? api.statements(q)
          : kind === 'payments'
            ? api.payments(q)
            : api.profiles(q.direction),
      kind === 'open-items' ? api.summary(q) : Promise.resolve(undefined),
    ])
    if (current !== generation) return
    partners.value = options
    summary.value = totals
    if (Array.isArray(result)) {
      profiles.value = result
      total.value = result.length
    } else {
      total.value = Number(result.total)
      if (kind === 'open-items') rows.value = result.records as OpenItem[]
      if (kind === 'statements') statements.value = result.records as Statement[]
      if (kind === 'payments') payments.value = result.records as Payment[]
      if (!result.records.length && total.value > 0 && page.value > 1)
        page.value = Math.ceil(total.value / size.value)
    }
  } catch (e) {
    if (current === generation) {
      error.value = (e as Error).message
      rows.value = []
      statements.value = []
      payments.value = []
      profiles.value = []
      total.value = 0
      summary.value = undefined
    }
  } finally {
    if (current === generation) loading.value = false
  }
}
function firstPage() {
  if (page.value === 1) void load()
  else page.value = 1
}
async function action(work: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  try {
    await work()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error((e as Error).message || String(e))
  } finally {
    busy.value = false
  }
}
function rebuild() {
  void action(async () => {
    await ElMessageBox.confirm(
      '补齐已完成出入库的应收应付记录。已有财务记录不变；历史收付款需另行登记。',
      '历史回填',
      { type: 'warning', confirmButtonText: '确认回填' },
    )
    const count = await api.rebuild()
    ElMessage.success(`已补齐 ${count} 条记录`)
    await load()
  })
}
function autoGenerate() {
  void action(async () => {
    await ElMessageBox.confirm(
      '按客户档案的账单周期，为账单期间已结束、已签收、未对账的记录自动生成草稿。不会补出漏掉的月份。',
      '自动出单',
      { type: 'info', confirmButtonText: '立即出单' },
    )
    const count = await api.autoGenerate()
    ElMessage.success(count ? `已生成 ${count} 张草稿对账单` : '没有符合条件的记录，无需出单')
    await load()
  })
}
function preview() {
  if (!selected.value.length) {
    if (!partnerId.value || !period.value) return
    const q = query()
    void action(async () => {
      const items = await api.preview(q)
      if (!items.length) {
        ElMessage.warning('当前筛选范围没有待对账记录')
        return
      }
      if (items.length > 1000) {
        ElMessage.warning('单张对账单最多 1000 条，请分批勾选生成')
        return
      }
      previewRows.value = items
      statementRemark.value = ''
      previewOpen.value = true
    })
    return
  }
  const first = selected.value[0]
  if (!first) return
  if (selected.value.some((o) => o.partnerId !== first.partnerId || o.currency !== first.currency)) {
    ElMessage.warning('请选择同往来单位、同币种的对账项')
    return
  }
  if (selected.value.some((o) => o.basisReady === 0)) {
    ElMessage.warning('存在待客户签收的记录，签收后才能生成对账单')
    return
  }
  previewRows.value = [...selected.value]
  statementRemark.value = ''
  previewOpen.value = true
}
function generate() {
  void action(async () => {
    const created = await api.generate(
      previewRows.value.map((o) => o.id),
      statementRemark.value,
    )
    previewOpen.value = false
    if (created.length === 1 && created[0]) {
      statementDetail.value = await api.statement(created[0].id)
      statementOpen.value = true
    } else {
      ElMessage.success(`已按账单周期生成 ${created.length} 张对账单`)
    }
    await load()
  })
}
function showStatement(id: number) {
  void action(async () => {
    statementDetail.value = await api.statement(id)
    statementOpen.value = true
  })
}
function transition(next: 'send' | 'confirm' | 'void') {
  void action(async () => {
    if (!statementDetail.value) return
    const names = { send: '标记已发送', confirm: '确认对账', void: '作废对账单' }
    await ElMessageBox.confirm(
      next === 'confirm' ? '确认后对账金额将冻结，并允许进行收付款核销。' : `确定${names[next]}？`,
      names[next],
      { type: next === 'void' ? 'warning' : 'info' },
    )
    statementDetail.value = await api.transition(statementDetail.value.statement.id, next)
    await load()
  })
}
function download() {
  void action(async () => {
    if (statementDetail.value)
      await exportStatement(
        statementDetail.value.statement.id,
        statementDetail.value.statement.statementNo,
      )
  })
}
function openPaymentForm() {
  paymentInput.value = newPayment()
  createOpen.value = true
}
function createPayment() {
  void action(async () => {
    if (
      !paymentInput.value.partnerId ||
      !paymentInput.value.paymentDate ||
      !paymentInput.value.amount ||
      paymentInput.value.amount <= 0
    )
      throw Error('请填写往来单位、收付款日期和金额')
    paymentDetail.value = await api.createPayment(paymentInput.value)
    createOpen.value = false
    paymentOpen.value = true
    eligiblePage.value = 1
    await Promise.all([load(), loadEligible()])
  })
}
function showPayment(id: number) {
  void action(async () => {
    paymentDetail.value = await api.payment(id)
    paymentOpen.value = true
    eligiblePage.value = 1
    await loadEligible()
  })
}
async function loadEligible() {
  const p = paymentDetail.value?.payment
  if (!p) return
  const current = ++eligibleGeneration
  eligibleLoading.value = true
  try {
    const result = await api.openItems({
      direction: p.direction,
      partnerId: p.partnerId,
      status: 'RECONCILED',
      unsettledOnly: true,
      pageNo: eligiblePage.value,
      pageSize: 10,
    })
    if (current === eligibleGeneration) {
      eligible.value = result.records
      eligibleTotal.value = Number(result.total)
    }
  } catch (e) {
    if (current === eligibleGeneration) {
      eligible.value = []
      ElMessage.error((e as Error).message)
    }
  } finally {
    if (current === eligibleGeneration) eligibleLoading.value = false
  }
}
function chooseAllocation(row: OpenItem) {
  allocating.value = row
  allocationAmount.value = undefined
  allocationKey.value = crypto.randomUUID()
}
function allocate() {
  void action(async () => {
    if (
      !allocating.value ||
      !paymentDetail.value ||
      !allocationAmount.value ||
      allocationAmount.value <= 0
    )
      throw Error('请输入核销金额')
    await api.allocate(
      paymentDetail.value.payment.id,
      allocating.value.id,
      allocationAmount.value,
      allocationKey.value,
    )
    allocating.value = undefined
    await refreshPayment()
    ElMessage.success('核销成功')
  })
}
async function refreshPayment() {
  if (paymentDetail.value) paymentDetail.value = await api.payment(paymentDetail.value.payment.id)
  await Promise.all([loadEligible(), load()])
}
function reverse(id: number) {
  void action(async () => {
    await ElMessageBox.confirm('反核销后将恢复对应未结金额和收付款未分配余额。', '确认反核销', {
      type: 'warning',
    })
    await api.reverse(id)
    await refreshPayment()
  })
}
function voidPayment() {
  void action(async () => {
    if (!paymentDetail.value) return
    await ElMessageBox.confirm('作废后该笔收付款将不再计入有效余额。', '作废收付款单', {
      type: 'warning',
    })
    await api.voidPayment(paymentDetail.value.payment.id)
    await refreshPayment()
  })
}
function editProfile(row?: Profile) {
  profileInput.value = row ? { ...row } : newProfile()
  profileOpen.value = true
}
function saveProfile() {
  void action(async () => {
    if (!profileInput.value.partnerId) throw Error('请选择往来单位')
    await api.saveProfile(profileInput.value)
    profileOpen.value = false
    await load()
  })
}
watch(page, load)
watch(
  () => [
    props.kind,
    direction.value,
    partnerId.value,
    period.value,
    status.value,
    size.value,
    unsettled.value,
    overdue.value,
    pendingBasis.value,
  ],
  firstPage,
)
watch(
  direction,
  () => {
    partnerId.value = undefined
    status.value = ''
  },
  { flush: 'sync' },
)
watch(
  () => props.kind,
  () => {
    status.value = ''
    statementOpen.value = false
    paymentOpen.value = false
  },
  { flush: 'sync' },
)
watch(keyword, () => {
  clearTimeout(timer)
  timer = setTimeout(firstPage, 300)
})
onMounted(load)
onBeforeUnmount(() => {
  generation++
  eligibleGeneration++
  clearTimeout(timer)
})
</script>

<template>
  <section class="purchase-page finance-page">
    <nav class="purchase-view-tabs" aria-label="财务方向">
      <button :class="{ active: direction === 'AR' }" @click="direction = 'AR'">应收 / 客户</button
      ><button :class="{ active: direction === 'AP' }" @click="direction = 'AP'">
        应付 / 供应商
      </button>
    </nav>
    <div v-if="summary && kind === 'open-items'" class="finance-summary">
      <div
        v-for="metric in [
          { name: '价税合计', value: summary.grossAmount },
          { name: '已结金额', value: summary.settledAmount },
          { name: '未结金额', value: summary.outstandingAmount },
          { name: '逾期未结', value: summary.overdueAmount },
          { name: '未分配收付款', value: summary.unallocatedAmount },
        ]"
        :key="metric.name"
      >
        <span>{{ metric.name }}</span
        ><strong>{{ money(metric.value) }}</strong>
      </div>
    </div>
    <div class="purchase-toolbar finance-toolbar">
      <div class="purchase-toolbar-actions">
        <el-button
          v-if="kind === 'open-items'"
          type="primary"
          :icon="DocumentChecked"
          :disabled="(!selected.length && (!partnerId || !period)) || loading || busy"
          @click="preview"
          >{{ selected.length ? '生成对账单' : '按筛选生成对账单' }}</el-button
        >
        <el-button
          v-if="kind === 'open-items' && auth.user?.role === 'ADMIN'"
          text
          :disabled="busy || loading"
          @click="rebuild"
          >历史回填</el-button
        >
        <el-button
          v-if="kind === 'open-items' && auth.user?.role === 'ADMIN'"
          text
          :disabled="busy || loading"
          @click="autoGenerate"
          >自动出单</el-button
        >
        <el-button
          v-if="kind === 'payments'"
          type="primary"
          :icon="Plus"
          :disabled="busy || loading"
          @click="openPaymentForm"
          >{{ direction === 'AR' ? '登记收款' : '登记付款' }}</el-button
        >
        <el-button
          v-if="kind === 'profiles'"
          type="primary"
          :icon="Plus"
          :disabled="busy || loading"
          @click="editProfile()"
          >添加档案</el-button
        >
      </div>
      <div v-if="kind !== 'profiles'" class="finance-filters">
        <el-select
          v-model="partnerId"
          filterable
          clearable
          :placeholder="direction === 'AR' ? '客户' : '供应商'"
          aria-label="往来单位筛选"
          ><el-option
            v-for="p in partners"
            :key="p.id"
            :label="p.shortName || p.name"
            :value="p.id"
        /></el-select>
        <el-date-picker
          v-model="period"
          type="month"
          value-format="YYYY-MM"
          placeholder="月份"
          aria-label="月份筛选"
        />
        <el-select v-model="status" clearable placeholder="状态" aria-label="状态筛选"
          ><el-option v-for="s in statuses" :key="s" :label="label(s)" :value="s"
        /></el-select>
        <el-input
          v-model="keyword"
          :prefix-icon="Search"
          clearable
          placeholder="名称 / 单号"
          maxlength="128"
          aria-label="搜索财务记录"
        />
      </div>
      <el-tooltip content="刷新"
        ><el-button
          text
          :icon="Refresh"
          aria-label="刷新财务记录"
          :disabled="loading || busy"
          @click="load"
      /></el-tooltip>
    </div>
    <div v-if="kind === 'open-items'" class="finance-options">
      <el-checkbox v-model="unsettled">仅未结清</el-checkbox
      ><el-checkbox v-model="overdue">仅逾期</el-checkbox
      ><el-checkbox v-if="direction === 'AR'" v-model="pendingBasis">仅待签收</el-checkbox
      ><span>人民币 CNY</span>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert
      v-if="kind === 'open-items' && summary && Number(summary.settledAmount) === 0"
      title="当前未结金额为财务账未核销余额；历史已收付款需按真实凭据补齐，不能据此直接认定实际欠款。"
      type="info"
      :closable="false"
      class="finance-notice"
    />
    <FinanceItemTable
      v-if="kind === 'open-items'"
      :rows="rows"
      :loading="loading"
      selectable
      height="calc(100vh - 388px)"
      @selection="selected = $event"
      ><el-table-column label="操作" width="100" fixed="right"
        ><template #default="{ row }"
          ><el-button
            v-if="row.statementId"
            link
            type="primary"
            :disabled="busy"
            @click="showStatement(row.statementId)"
            >对账单</el-button
          ></template
        ></el-table-column
      ></FinanceItemTable
    >
    <el-table
      v-if="kind === 'statements'"
      v-loading="loading"
      :data="statements"
      border
      height="calc(100vh - 285px)"
      empty-text="暂无对账单"
    >
      <el-table-column label="对账单号" width="165"
        ><template #default="{ row }"
          ><el-button link type="primary" @click="showStatement(row.id)">{{
            row.statementNo
          }}</el-button></template
        ></el-table-column
      >
      <el-table-column
        prop="partnerName"
        label="往来单位"
        min-width="180"
        show-overflow-tooltip
      /><el-table-column label="账单期间" width="150"
        ><template #default="{ row }">{{
          row.periodStart ? periodText(row.periodStart, row.periodEnd) : row.period
        }}</template></el-table-column
      ><el-table-column label="到期日" width="110" prop="dueDate" /><el-table-column
        label="金额"
        width="140"
        align="right"
        ><template #default="{ row }">{{ money(row.grossAmount) }}</template></el-table-column
      ><el-table-column label="已结金额" width="140" align="right"
        ><template #default="{ row }">{{ money(row.settledAmount) }}</template></el-table-column
      ><el-table-column label="状态" width="110"
        ><template #default="{ row }"
          ><el-tag
            :type="
              row.status === 'CONFIRMED' ? 'success' : row.status === 'VOID' ? 'info' : 'warning'
            "
            effect="plain"
            >{{ label(row.status) }}</el-tag
          ><el-tag v-if="row.autoGenerated" class="finance-tag-gap" type="info" effect="plain"
            >自动</el-tag
          ></template
        ></el-table-column
      ><el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
    </el-table>
    <el-table
      v-if="kind === 'payments'"
      v-loading="loading"
      :data="payments"
      border
      height="calc(100vh - 285px)"
      empty-text="暂无收付款记录"
    >
      <el-table-column label="收付款单号" width="165"
        ><template #default="{ row }"
          ><el-button link type="primary" @click="showPayment(row.id)">{{
            row.paymentNo
          }}</el-button></template
        ></el-table-column
      ><el-table-column
        prop="partnerName"
        label="往来单位"
        min-width="170"
        show-overflow-tooltip
      /><el-table-column prop="paymentDate" label="日期" width="110" /><el-table-column
        label="金额"
        width="125"
        align="right"
        ><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column
      ><el-table-column label="已核销" width="125" align="right"
        ><template #default="{ row }">{{ money(row.allocatedAmount) }}</template></el-table-column
      ><el-table-column label="未分配" width="125" align="right"
        ><template #default="{ row }">{{
          money(Number(row.amount) - Number(row.allocatedAmount))
        }}</template></el-table-column
      ><el-table-column label="状态" width="115"
        ><template #default="{ row }">{{ label(row.status) }}</template></el-table-column
      ><el-table-column
        prop="bankReference"
        label="银行流水号"
        width="160"
        show-overflow-tooltip
      /><el-table-column prop="remark" label="备注" min-width="130" show-overflow-tooltip />
    </el-table>
    <el-table
      v-if="kind === 'profiles'"
      v-loading="loading"
      :data="profiles"
      border
      height="calc(100vh - 285px)"
      empty-text="暂无结算档案"
      ><el-table-column prop="partnerName" label="往来单位" min-width="180" /><el-table-column
        label="截止日"
        width="95"
        ><template #default="{ row }">{{
          row.cutoffDay ? `每月 ${row.cutoffDay} 日` : '自然月'
        }}</template></el-table-column
      ><el-table-column label="账单周期" width="105"
        ><template #default="{ row }">{{ row.cycleMonths }} 个月</template></el-table-column
      ><el-table-column label="出单" width="135"
        ><template #default="{ row }"
          >结束月 +{{ row.statementLagMonths }} 月 · {{ row.statementDay }} 日</template
        ></el-table-column
      ><el-table-column label="到期" min-width="150"
        ><template #default="{ row }">{{
          row.dueRule === 'CALENDAR'
            ? `结束月 +${row.dueMonthOffset} 月 ${row.dueDay ? row.dueDay + ' 日' : '月末'}`
            : `${row.creditBasis === 'PERIOD_END' ? '账单期末' : row.creditBasis === 'ISSUE_DATE' ? '出单日' : '出单月末'} + ${row.creditDays} 天`
        }}</template></el-table-column
      ><el-table-column label="自动出单" width="95"
        ><template #default="{ row }">{{ row.autoGenerate ? '是' : '否' }}</template></el-table-column
      ><el-table-column label="操作" width="90"
        ><template #default="{ row }"
          ><el-button link type="primary" @click="editProfile(row)">编辑</el-button></template
        ></el-table-column
      ></el-table
    >
    <footer v-if="kind !== 'profiles'" class="finance-pagination">
      <span>共 {{ total }} 条</span
      ><el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="sizes, prev, pager, next, jumper"
      />
    </footer>
    <el-dialog
      v-model="previewOpen"
      title="生成对账单"
      width="min(1100px,96vw)"
      top="4vh"
      :close-on-click-modal="false"
      ><p class="finance-detail-line">
        {{ previewRows[0]?.partnerName }}<span v-if="previewPeriod"> · 账单期间 {{ previewPeriod }}</span>
        · {{ previewRows.length }} 条
      </p>
      <el-alert
        v-if="previewRows.length && previewPeriod"
        title="生成时按客户结算档案的账单周期分组；跨周期选择会生成多张对账单。"
        type="info"
        :closable="false"
      />
      <FinanceItemTable :rows="previewRows" height="45vh" /><el-input
        v-model="statementRemark"
        class="finance-remark"
        placeholder="对账备注"
        maxlength="500"
      /><template #footer
        ><el-button :disabled="busy" @click="previewOpen = false">取消</el-button
        ><el-button type="primary" :loading="busy" @click="generate">生成草稿</el-button></template
      ></el-dialog
    >
    <el-dialog
      v-model="statementOpen"
      :title="statementDetail?.statement.statementNo || '对账单'"
      width="min(1100px,96vw)"
      top="4vh"
      :close-on-click-modal="false"
    >
      <template v-if="statementDetail"
        ><p class="finance-detail-line">
          <strong>{{ statementDetail.statement.partnerName }}</strong
          ><span>账单期间 {{ statementDetail.statement.period }}</span
          ><el-tag>{{ label(statementDetail.statement.status) }}</el-tag
          ><span v-if="statementDetail.statement.dueDate"
            >到期日 {{ statementDetail.statement.dueDate }}</span
          ><span>合计 {{ money(statementDetail.statement.grossAmount) }}</span
          ><span>已结 {{ money(statementDetail.statement.settledAmount) }}</span>
        </p>
        <FinanceItemTable :rows="statementDetail.items" height="45vh" />
        <p v-if="statementDetail.statement.remark">{{ statementDetail.statement.remark }}</p>
        <h4 v-if="statementDetail.allocations.length">核销记录</h4>
        <el-table
          v-if="statementDetail.allocations.length"
          :data="statementDetail.allocations"
          border
          max-height="220"
          ><el-table-column prop="paymentNo" label="收付款单号" min-width="155" /><el-table-column
            prop="sourceNo"
            label="出入库单号"
            min-width="155"
          /><el-table-column label="金额" width="130"
            ><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column
          ><el-table-column label="状态" width="110"
            ><template #default="{ row }">{{ label(row.status) }}</template></el-table-column
          ></el-table
        ></template
      >
      <template #footer
        ><el-button :icon="Download" :disabled="busy" @click="download">导出</el-button
        ><el-button
          v-if="['DRAFT', 'SENT'].includes(statementDetail?.statement.status || '')"
          type="danger"
          plain
          :disabled="busy"
          @click="transition('void')"
          >作废</el-button
        ><el-button
          v-if="statementDetail?.statement.status === 'DRAFT'"
          type="primary"
          :loading="busy"
          @click="transition('send')"
          >标记已发送</el-button
        ><el-button
          v-if="statementDetail?.statement.status === 'SENT'"
          type="primary"
          :loading="busy"
          @click="transition('confirm')"
          >确认对账</el-button
        ><el-button @click="statementOpen = false">关闭</el-button></template
      >
    </el-dialog>
    <el-dialog
      v-model="createOpen"
      :title="direction === 'AR' ? '登记收款' : '登记付款'"
      width="min(620px,96vw)"
      top="6vh"
      :close-on-click-modal="false"
      ><el-form label-position="top" class="finance-form" :disabled="busy"
        ><el-form-item label="往来单位" required
          ><el-select v-model="paymentInput.partnerId" filterable
            ><el-option
              v-for="p in partners"
              :key="p.id"
              :label="p.name"
              :value="p.id" /></el-select></el-form-item
        ><el-form-item label="日期" required
          ><el-date-picker
            v-model="paymentInput.paymentDate"
            type="date"
            value-format="YYYY-MM-DD" /></el-form-item
        ><el-form-item label="金额（CNY）" required
          ><el-input-number
            v-model="paymentInput.amount"
            :min="0.01"
            :max="999999999999.99"
            :precision="2"
            :controls="false" /></el-form-item
        ><el-form-item label="银行流水号"
          ><el-input v-model="paymentInput.bankReference" maxlength="128" /></el-form-item
        ><el-form-item label="备注" class="finance-form-wide"
          ><el-input
            v-model="paymentInput.remark"
            type="textarea"
            maxlength="500" /></el-form-item></el-form
      ><template #footer
        ><el-button :disabled="busy" @click="createOpen = false">取消</el-button
        ><el-button type="primary" :loading="busy" @click="createPayment">保存</el-button></template
      ></el-dialog
    >
    <el-dialog
      v-model="paymentOpen"
      :title="paymentDetail?.payment.paymentNo || '收付款详情'"
      width="min(1100px,96vw)"
      top="4vh"
      :close-on-click-modal="false"
    >
      <template v-if="paymentDetail"
        ><p class="finance-detail-line">
          <strong>{{ paymentDetail.payment.partnerName }}</strong
          ><el-tag>{{ label(paymentDetail.payment.status) }}</el-tag
          ><span>金额 {{ money(paymentDetail.payment.amount) }}</span
          ><span
            >未分配
            {{
              money(
                Number(paymentDetail.payment.amount) -
                  Number(paymentDetail.payment.allocatedAmount),
              )
            }}</span
          >
        </p>
        <p>
          {{ paymentDetail.payment.paymentDate
          }}<span v-if="paymentDetail.payment.bankReference">
            · 流水号 {{ paymentDetail.payment.bankReference }}</span
          ><span v-if="paymentDetail.payment.remark"> · {{ paymentDetail.payment.remark }}</span>
        </p>
        <h4>核销记录</h4>
        <el-table
          :data="paymentDetail.allocations"
          border
          max-height="250"
          empty-text="暂无核销记录"
          ><el-table-column prop="sourceNo" label="出入库单号" width="160" /><el-table-column
            prop="orderNo"
            label="订单号"
            width="155"
          /><el-table-column label="金额" width="120"
            ><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column
          ><el-table-column label="状态" width="120"
            ><template #default="{ row }">{{ label(row.status) }}</template></el-table-column
          ><el-table-column prop="createdAt" label="核销时间" min-width="175" /><el-table-column
            label="操作"
            width="100"
            ><template #default="{ row }"
              ><el-button
                v-if="row.status === 'ACTIVE'"
                type="danger"
                link
                :disabled="busy"
                @click="reverse(row.id)"
                >反核销</el-button
              ></template
            ></el-table-column
          ></el-table
        >
        <template
          v-if="
            paymentDetail.payment.status !== 'VOID' &&
            Number(paymentDetail.payment.amount) > Number(paymentDetail.payment.allocatedAmount)
          "
          ><h4>待核销应收应付</h4>
          <FinanceItemTable :rows="eligible" :loading="eligibleLoading" height="280px"
            ><el-table-column label="操作" width="85" fixed="right"
              ><template #default="{ row }"
                ><el-button type="primary" link :disabled="busy" @click="chooseAllocation(row)"
                  >核销</el-button
                ></template
              ></el-table-column
            ></FinanceItemTable
          ><el-pagination
            v-model:current-page="eligiblePage"
            @current-change="loadEligible"
            :page-size="10"
            :total="eligibleTotal"
            layout="total, prev, pager, next"
        /></template> </template
      ><template #footer
        ><el-button
          v-if="paymentDetail?.payment.status !== 'VOID'"
          type="danger"
          plain
          :disabled="busy || Number(paymentDetail?.payment.allocatedAmount) !== 0"
          @click="voidPayment"
          >作废</el-button
        ><el-button @click="paymentOpen = false">关闭</el-button></template
      >
    </el-dialog>
    <el-dialog
      :model-value="!!allocating"
      title="核销金额"
      width="min(440px,96vw)"
      top="12vh"
      append-to-body
      :close-on-click-modal="false"
      @update:model-value="!$event && (allocating = undefined)"
      ><p>{{ allocating?.sourceNo }} · {{ allocating?.partNo }}</p>
      <p>
        未结金额 {{ money(Number(allocating?.grossAmount) - Number(allocating?.settledAmount)) }}
      </p>
      <el-input-number
        v-model="allocationAmount"
        :min="0.01"
        :precision="2"
        :controls="false"
        aria-label="核销金额"
      /><template #footer
        ><el-button :disabled="busy" @click="allocating = undefined">取消</el-button
        ><el-button type="primary" :loading="busy" @click="allocate">确认核销</el-button></template
      ></el-dialog
    >
    <el-dialog
      v-model="profileOpen"
      title="结算档案"
      width="min(540px,96vw)"
      top="6vh"
      :close-on-click-modal="false"
      ><el-form label-position="top" :disabled="busy"
        ><el-form-item label="往来单位" required
          ><el-select
            v-model="profileInput.partnerId"
            filterable
            placeholder="请选择往来单位"
            :disabled="!!profileInput.id"
            ><el-option
              v-for="p in partners"
              :key="p.id"
              :label="p.name"
              :value="p.id" /></el-select></el-form-item
        ><el-form-item label="账单截止日"
          ><el-input-number
            v-model="profileInput.cutoffDay"
            :min="1"
            :max="31"
            :precision="0"
            placeholder="不填按自然月"
            controls-position="right" /></el-form-item
        ><el-form-item label="账单周期（月）" required
          ><el-input-number
            v-model="profileInput.cycleMonths"
            :min="1"
            :max="12"
            :precision="0"
            controls-position="right" /></el-form-item
        ><el-form-item label="出单时点" required
          ><div class="finance-inline"
            >账单结束月后
            <el-input-number
              v-model="profileInput.statementLagMonths"
              :min="0"
              :max="12"
              :precision="0"
              controls-position="right" />月的
            <el-input-number
              v-model="profileInput.statementDay"
              :min="1"
              :max="31"
              :precision="0"
              controls-position="right" />日</div
          ></el-form-item
        ><el-form-item label="到期规则" required
          ><el-radio-group v-model="profileInput.dueRule"
            ><el-radio-button value="CREDIT_DAYS">固定天数</el-radio-button
            ><el-radio-button value="CALENDAR">约定日期</el-radio-button></el-radio-group
          ></el-form-item
        ><el-form-item v-if="profileInput.dueRule === 'CREDIT_DAYS'" label="账期" required
          ><div class="finance-inline"
            ><el-select v-model="profileInput.creditBasis" style="width: 130px"
              ><el-option label="账单期末" value="PERIOD_END" /><el-option
                label="出单日"
                value="ISSUE_DATE" /><el-option
                label="出单月末"
                value="ISSUE_MONTH_END" /></el-select
            >+<el-input-number
              v-model="profileInput.creditDays"
              :min="0"
              :max="3650"
              :precision="0"
              controls-position="right" />天</div
          ></el-form-item
        ><el-form-item v-else label="约定付款日" required
          ><div class="finance-inline"
            >账单结束月后
            <el-input-number
              v-model="profileInput.dueMonthOffset"
              :min="0"
              :max="12"
              :precision="0"
              controls-position="right" />月的
            <el-input-number
              v-model="profileInput.dueDay"
              :min="0"
              :max="31"
              :precision="0"
              placeholder="0 表示月末"
              controls-position="right" />日</div
          ></el-form-item
        ><el-form-item label="自动生成对账单"
          ><el-switch v-model="profileInput.autoGenerate" /></el-form-item
        ><el-alert
          title="账单月按截止日切分：截止日（含）前的业务归当月，之后的归下月；未填截止日按自然月。自动出单只处理已签收、未对账且在已结束账单期间内的记录。"
          type="info"
          :closable="false" /></el-form
      ><template #footer
        ><el-button :disabled="busy" @click="profileOpen = false">取消</el-button
        ><el-button type="primary" :loading="busy" @click="saveProfile">保存</el-button></template
      ></el-dialog
    >
  </section>
</template>

<style scoped>
.finance-page {
  min-width: 0;
}
.finance-notice {
  margin-bottom: 8px;
}
.finance-summary {
  display: flex;
  flex-wrap: wrap;
  border-bottom: 1px solid #e3e8f0;
  padding: 16px 12px;
  gap: 18px 36px;
  background: #fafbfd;
}
.finance-summary div {
  display: grid;
  gap: 6px;
  min-width: 115px;
}
.finance-summary span {
  color: #738092;
  font-size: 12px;
}
.finance-summary strong {
  font-size: 18px;
  font-variant-numeric: tabular-nums;
  color: #28394d;
}
.finance-summary div:nth-child(3) strong {
  color: #009c85;
}
.finance-summary div:nth-child(4) strong {
  color: #c55563;
}
.finance-toolbar {
  flex-wrap: wrap;
  gap: 12px;
}
.finance-filters {
  display: flex;
  flex: 1;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}
.finance-filters :deep(.el-select) {
  width: 140px;
}
.finance-filters :deep(.el-date-editor) {
  width: 125px;
}
.finance-filters > .el-input {
  width: 170px;
}
.finance-options {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 12px 8px;
  color: #738092;
  font-size: 12px;
}
.finance-options > span {
  margin-left: auto;
}
.finance-pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 10px;
  gap: 12px;
  flex-wrap: wrap;
  color: #738092;
  font-size: 13px;
}
.finance-detail-line {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  margin: 0 0 16px;
}
.finance-remark {
  margin-top: 16px;
}
.finance-inline {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.finance-inline :deep(.el-input-number) {
  /* 步进按钮固定在右侧，宽度要容得下两位数字（如 15 日、31 日） */
  width: 96px;
}
.finance-tag-gap {
  margin-left: 6px;
}
.finance-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 0 16px;
}
.finance-form-wide {
  grid-column: 1 / -1;
}
.finance-form :deep(.el-date-editor),
.finance-form :deep(.el-input-number) {
  width: 100%;
}
.finance-page :deep(.el-table) {
  font-size: 13px;
  --el-table-header-bg-color: #f5f6fa;
}
@media (max-width: 600px) {
  .finance-summary {
    gap: 14px 20px;
  }
  .finance-summary div {
    min-width: 125px;
  }
  .finance-filters {
    justify-content: flex-start;
  }
  .finance-form {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
