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
  DRAFT: '待发送',
  SENT: '待确认',
  CONFIRMED: '已确认',
  CONFIRMED_UNSETTLED: '待收款',
  CONFIRMED_SETTLED: '已结清',
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
      ? ['DRAFT', 'SENT', 'CONFIRMED_UNSETTLED', 'CONFIRMED_SETTLED', 'VOID']
      : ['RECEIVED', 'PARTIAL_ALLOCATED', 'ALLOCATED', 'VOID'],
)
const money = (value: unknown) =>
  Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const label = (value: string) => statusLabels[value] || value
/** 把“账单结束月 + N 个月”写成“当月 / 次月 / 后第 N 个月”；多个月一结时“当月”指账单最后一个月。 */
const monthAfterBill = (offset: number, cycleMonths = 1) =>
  offset === 0
    ? cycleMonths > 1
      ? '末月'
      : '当月'
    : offset === 1
      ? '次月'
      : `后第 ${offset} 个月`
const monthOffsets = computed(() =>
  Array.from({ length: 13 }, (_, n) => ({
    value: n,
    label: monthAfterBill(n, profileInput.value.cycleMonths),
  })),
)
/** 出单必须晚于账单期末：自然月、截止 31 日都只能次月及以后；截止 1–30 日时才允许当月出单（出单日自动取截止日之后）。 */
const issueMonthOptions = computed(() =>
  monthOffsets.value.filter((option) => {
    const cutoff = profileInput.value.cutoffDay ?? 0
    return option.value > 0 || (cutoff > 0 && cutoff < 31)
  }),
)
/** 约定付款日不能早于出单月，因此从出单月往后给选项。 */
const dueMonthOptions = computed(() =>
  monthOffsets.value.filter((option) => option.value >= profileInput.value.statementLagMonths),
)
const issueDayMin = computed(() =>
  profileInput.value.statementLagMonths === 0
    ? Math.min(31, (profileInput.value.cutoffDay ?? 0) + 1)
    : 1,
)
/** 把弹窗里必然无效的组合自动纠正到最近的合法值，避免出现“当月 1 日出单、25 日才截止”这类设置。 */
function normalizeProfile() {
  const p = profileInput.value
  const cutoff = p.cutoffDay ?? 0
  if (p.statementLagMonths === 0 && (cutoff <= 0 || cutoff >= 31)) p.statementLagMonths = 1
  if (p.statementLagMonths === 0 && p.statementDay <= cutoff) p.statementDay = cutoff + 1
  if (p.dueMonthOffset < p.statementLagMonths) p.dueMonthOffset = p.statementLagMonths
}
const issueError = computed(() => {
  const p = profileInput.value
  if (p.statementLagMonths > 0) return ''
  const cutoff = p.cutoffDay ?? 0
  if (cutoff <= 0) return '自然月账单要到月末才结束，出单月请选“次月”或更晚'
  if (cutoff >= 31) return '截止日是 31 日时账单期末就是月末，出单月请选“次月”或更晚'
  return p.statementDay <= cutoff
    ? `出单日必须晚于截止日：${cutoff} 日截止时，出单日至少 ${cutoff + 1} 日`
    : ''
})
const dueError = computed(() => {
  const p = profileInput.value
  if (p.dueRule !== 'CALENDAR') return ''
  if (p.dueMonthOffset < p.statementLagMonths) return '约定付款日不能早于出单月'
  if (p.dueMonthOffset > p.statementLagMonths) return ''
  return (p.dueDay === 0 ? 31 : p.dueDay) <= p.statementDay
    ? `同一月内到期日要晚于出单日：出单 ${p.statementDay} 日，到期 ${p.dueDay === 0 ? '月末' : `${p.dueDay} 日`}`
    : ''
})
const profileProblem = computed(() => issueError.value || dueError.value)
watch(
  () => [
    profileInput.value.cutoffDay,
    profileInput.value.statementLagMonths,
    profileInput.value.dueRule,
  ],
  normalizeProfile,
)
const statementText = (row: Profile) =>
  `账单${monthAfterBill(row.statementLagMonths, row.cycleMonths)} · ${row.statementDay} 日`
const dueText = (row: Profile) => {
  if (row.dueRule === 'CALENDAR')
    return row.dueDay
      ? `账单${monthAfterBill(row.dueMonthOffset, row.cycleMonths)} · ${row.dueDay} 日`
      : `账单${monthAfterBill(row.dueMonthOffset, row.cycleMonths)}的最后一天`
  const from =
    row.creditBasis === 'PERIOD_END' ? '账单期末' : row.creditBasis === 'ISSUE_DATE' ? '出单' : '出单月末'
  return row.creditDays ? `${from}后 ${row.creditDays} 天` : from
}
/** 29–31 日这类日期遇到短月份会截到月末，悬停时给一句说明。 */
const dayTip = (day?: number | null) => (day && day > 28 ? '该月没有这一天时取月末' : '')
const dueTip = (row: Profile) =>
  row.dueRule !== 'CALENDAR' ? '' : row.dueDay ? dayTip(row.dueDay) : '0 表示该月最后一天'
const companyProfile = computed(() => profiles.value.find((p) => p.partnerId === 0))
/** 公司默认方案固定置顶；还没配置时给一行占位，方便从这里进入配置。 */
const profileRows = computed<Profile[]>(() =>
  companyProfile.value
    ? profiles.value
    : [{ ...newProfile(), partnerId: 0, partnerName: '公司默认（未配置）' }, ...profiles.value],
)
/** 沿用公司默认的客户，方案字段只读，只有自动出单开关可以改。 */
const planLocked = computed(
  () => profileInput.value.partnerId !== 0 && profileInput.value.planSource === 'DEFAULT',
)
const profileTitle = computed(() =>
  profileInput.value.partnerId === 0
    ? '公司默认结算方案'
    : profileInput.value.id
      ? '编辑结算档案'
      : '新增结算档案',
)
function profileRowClass({ row }: { row: Profile }) {
  return row.partnerId === 0 ? 'profile-default-row' : ''
}
function query(): FinanceQuery {
  const combinedStatus = status.value
  const statementStatus =
    combinedStatus === 'CONFIRMED_UNSETTLED' || combinedStatus === 'CONFIRMED_SETTLED'
      ? 'CONFIRMED'
      : combinedStatus
  return {
    direction: direction.value,
    partnerId: partnerId.value,
    period: period.value || undefined,
    keyword: keyword.value.trim(),
    status: statementStatus || undefined,
    unsettledOnly:
      props.kind === 'statements'
        ? combinedStatus === 'CONFIRMED_UNSETTLED' || undefined
        : unsettled.value,
    settledOnly: combinedStatus === 'CONFIRMED_SETTLED' || undefined,
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
    planSource: 'CUSTOM',
  }
}
/** 账单期间展示：2026-09 或 2026-08~2026-09。 */
function periodText(start?: string | null, end?: string | null) {
  if (!start || !end) return ''
  return start.slice(0, 7) === end.slice(0, 7) ? end.slice(0, 7) : `${start.slice(0, 7)}~${end.slice(0, 7)}`
}
/** 已发送/已确认、到期未结清的对账单视为逾期。 */
function statementOverdue(row: Statement) {
  if (!['SENT', 'CONFIRMED'].includes(row.status) || !row.dueDate) return false
  const now = new Date()
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  return row.dueDate < today && Number(row.settledAmount ?? 0) < Number(row.grossAmount ?? 0)
}
/** 对账单展示状态：由单据状态和收款进度合并成一个处理状态。 */
function statementDisplayStatus(row: Statement) {
  if (row.status === 'VOID') return { label: '已作废', type: 'danger' as const }
  if (row.status === 'DRAFT') return { label: '待发送', type: 'info' as const }
  if (row.status === 'SENT') return { label: '待确认', type: 'warning' as const }
  const gross = Number(row.grossAmount ?? 0),
    settled = Number(row.settledAmount ?? 0)
  if (gross <= 0 || settled >= gross) return { label: '已结清', type: 'success' as const }
  return { label: '待收款', type: 'primary' as const }
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
/** 切换到“公司默认”时把默认方案复制到表单，方案字段只读展示。 */
function applyCompanyPlan() {
  const base = companyProfile.value
  if (!base) return
  const keep = profileInput.value
  profileInput.value = {
    ...base,
    id: keep.id,
    direction: keep.direction,
    partnerId: keep.partnerId,
    partnerName: keep.partnerName,
    autoGenerate: keep.autoGenerate,
    planSource: 'DEFAULT',
  }
  normalizeProfile()
}
watch(
  () => profileInput.value.planSource,
  (source) => {
    if (source === 'DEFAULT') applyCompanyPlan()
  },
)
function editProfile(row?: Profile) {
  profileInput.value = row ? { ...row } : newProfile()
  normalizeProfile()
  // 沿用默认方案的客户每次都按当前默认值展示，避免默认行改过后看到旧值。
  if (profileInput.value.planSource === 'DEFAULT') applyCompanyPlan()
  profileOpen.value = true
}
function saveProfile() {
  void action(async () => {
    if (profileInput.value.partnerId === undefined || profileInput.value.partnerId === null)
      throw Error('请选择往来单位')
    if (profileInput.value.planSource === 'DEFAULT' && !companyProfile.value)
      throw Error('请先在「公司默认」里配置统一的结算方案')
    if (profileProblem.value) throw Error(profileProblem.value)
    const company = profileInput.value.partnerId === 0
    const result = await api.saveProfile(profileInput.value)
    profileOpen.value = false
    await load()
    if (company && result?.syncedPartners)
      ElMessage.success(
        `已同步 ${result.syncedPartners} 个客户的默认方案${
          result.recalculatedItems ? `，重算 ${result.recalculatedItems} 条未出账记录` : ''
        }`,
      )
    else ElMessage.success('结算档案已保存')
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
    <div v-if="kind === 'statements'" class="finance-options">
      <el-checkbox v-model="overdue">仅逾期未结</el-checkbox
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
      ><el-table-column label="到期日" width="150"
        ><template #default="{ row }"
          ><span :class="{ 'finance-overdue-date': statementOverdue(row) }">{{
            row.dueDate || '—'
          }}</span
          ><span v-if="statementOverdue(row)" class="finance-overdue-badge">逾期</span></template
        ></el-table-column
      ><el-table-column
        label="金额"
        width="140"
        align="right"
        ><template #default="{ row }">{{ money(row.grossAmount) }}</template></el-table-column
      ><el-table-column label="已结金额" width="140" align="right"
        ><template #default="{ row }">{{ money(row.settledAmount) }}</template></el-table-column
      ><el-table-column label="账单状态" width="110"
        ><template #default="{ row }"
          ><el-tag
            size="small"
            effect="plain"
            :type="statementDisplayStatus(row).type"
            >{{ statementDisplayStatus(row).label }}</el-tag
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
      :data="profileRows"
      :row-class-name="profileRowClass"
      border
      height="calc(100vh - 285px)"
      empty-text="暂无结算档案"
      ><el-table-column
        prop="partnerName"
        label="往来单位"
        min-width="150"
        show-overflow-tooltip
      /><el-table-column label="方案来源" width="105"
        ><template #default="{ row }"
          ><el-tag v-if="row.partnerId === 0" type="warning" size="small">公司默认</el-tag
          ><el-tag v-else :type="row.planSource === 'DEFAULT' ? 'info' : 'success'" size="small">{{
            row.planSource === 'DEFAULT' ? '公司默认' : '自定义'
          }}</el-tag></template
        ></el-table-column
      ><el-table-column
        label="截止日"
        width="105"
        ><template #default="{ row }"
          ><span :title="dayTip(row.cutoffDay)">{{
            row.cutoffDay ? `每月 ${row.cutoffDay} 日` : '按自然月'
          }}</span></template
        ></el-table-column
      ><el-table-column label="账单周期" width="105"
        ><template #default="{ row }">{{ row.cycleMonths }} 个月</template></el-table-column
      ><el-table-column label="出单日" width="185"
        ><template #default="{ row }"
          ><span :title="dayTip(row.statementDay)">{{ statementText(row) }}</span></template
        ></el-table-column
      ><el-table-column label="到期日" min-width="190"
        ><template #default="{ row }"
          ><span :title="dueTip(row)">{{ dueText(row) }}</span></template
        ></el-table-column
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
          ><el-tag
            effect="plain"
            :type="statementDisplayStatus(statementDetail.statement).type"
            >{{ statementDisplayStatus(statementDetail.statement).label }}</el-tag
          ><span v-if="statementDetail.statement.dueDate"
            >到期日 {{ statementDetail.statement.dueDate }}</span
          ><span>合计 {{ money(statementDetail.statement.grossAmount) }}</span
          ><span>已结 {{ money(statementDetail.statement.settledAmount) }}</span>
        </p>
        <FinanceItemTable
          :rows="statementDetail.items"
          height="45vh"
          :show-partner="false"
          :show-biz-date="false"
        />
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
      :title="profileTitle"
      width="min(720px,96vw)"
      top="6vh"
      :close-on-click-modal="false"
      ><el-form label-position="top" :disabled="busy"
        ><el-form-item label="往来单位" required
          ><el-input
            v-if="profileInput.partnerId === 0"
            model-value="公司默认（所有未单独定制的客户通用）"
            disabled /><el-select
            v-else
            v-model="profileInput.partnerId"
            filterable
            placeholder="请选择往来单位"
            :disabled="!!profileInput.id"
            ><el-option
              v-for="p in partners"
              :key="p.id"
              :label="p.name"
              :value="p.id" /></el-select
          ><div v-if="profileInput.partnerId === 0" class="finance-hint">
            这里配置的方案会复制给选择“公司默认”的客户；自动出单开关仍在各客户档案里单独控制。
          </div></el-form-item
        ><el-form-item v-if="profileInput.partnerId !== 0" label="方案来源"
          ><el-radio-group v-model="profileInput.planSource"
            ><el-radio-button value="DEFAULT" :disabled="!companyProfile"
              >公司默认</el-radio-button
            ><el-radio-button value="CUSTOM">自定义</el-radio-button></el-radio-group
          ><div class="finance-hint">
            {{
              companyProfile
                ? '沿用公司默认方案：方案字段只读，默认方案调整后自动同步；自动出单开关仍按客户单独控制。'
                : '还没有配置公司默认方案，请先在列表顶部“公司默认”那一行里配置。'
            }}
          </div></el-form-item
        ><el-form-item label="账单截止日"
          ><el-input-number
            v-model="profileInput.cutoffDay"
            :min="1"
            :max="31"
            :precision="0"
            placeholder="不填按自然月"
            :disabled="planLocked"
            controls-position="right" /></el-form-item
        ><el-form-item label="账单周期（月）" required
          ><el-input-number
            v-model="profileInput.cycleMonths"
            :min="1"
            :max="12"
            :precision="0"
            :disabled="planLocked"
            controls-position="right"
          /><div class="finance-hint">1 = 每月一结，2 = 两个月一结</div></el-form-item
        ><el-form-item label="出单时点" required
          ><div class="finance-inline"
            >账单结束
            <el-select
              v-model="profileInput.statementLagMonths"
              style="width: 120px"
              :disabled="planLocked"
              ><el-option
                v-for="option in issueMonthOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value" /></el-select
            >的
            <el-input-number
              v-model="profileInput.statementDay"
              :min="issueDayMin"
              :max="31"
              :precision="0"
              :disabled="planLocked"
              controls-position="right" />日</div
          ><div v-if="issueError" class="finance-hint finance-hint-error">{{ issueError }}</div
          ><div class="finance-hint">
            出单日必须晚于账单截止日；多个月一结时“末月”指账单覆盖的最后一个月，该月没有这一天时取月末。
          </div></el-form-item
        ><el-form-item label="到期规则" required
          ><el-radio-group v-model="profileInput.dueRule" :disabled="planLocked"
            ><el-radio-button value="CREDIT_DAYS">固定天数</el-radio-button
            ><el-radio-button value="CALENDAR">约定日期</el-radio-button></el-radio-group
          ></el-form-item
        ><el-form-item v-if="profileInput.dueRule === 'CREDIT_DAYS'" label="账期" required
          ><div class="finance-inline"
            ><el-select
              v-model="profileInput.creditBasis"
              style="width: 130px"
              :disabled="planLocked"
              ><el-option label="账单期末" value="PERIOD_END" /><el-option
                label="出单日"
                value="ISSUE_DATE" /><el-option
                label="出单月末"
                value="ISSUE_MONTH_END" /></el-select
            >后<el-input-number
              v-model="profileInput.creditDays"
              :min="0"
              :max="3650"
              :precision="0"
              :disabled="planLocked"
              controls-position="right" />天</div
          ><div class="finance-hint">
            按自然日累加，不受月份长短影响；填 0 天表示到期日就是起算日当天。
          </div></el-form-item
        ><el-form-item v-else label="约定付款日" required
          ><div class="finance-inline"
            >账单结束
            <el-select
              v-model="profileInput.dueMonthOffset"
              style="width: 120px"
              :disabled="planLocked"
              ><el-option
                v-for="option in dueMonthOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value" /></el-select
            >的
            <el-input-number
              v-model="profileInput.dueDay"
              :min="0"
              :max="31"
              :precision="0"
              placeholder="0 表示月末"
              :disabled="planLocked"
              controls-position="right" />日</div
          ><div v-if="dueError" class="finance-hint finance-hint-error">{{ dueError }}</div
          ><div class="finance-hint">
            填 0 表示该月最后一天；填 29–31 日时，该月没有这一天则取月末。
          </div></el-form-item>
        <el-form-item v-if="profileInput.partnerId !== 0" label="自动生成对账单"
          ><el-switch v-model="profileInput.autoGenerate" /></el-form-item
        ><el-form-item v-else label="自动生成对账单"
          ><span class="finance-hint">公司默认方案本身不出单，由各客户自己的开关控制。</span></el-form-item
        ><el-alert
          title="账单月按截止日切分：截止日（含）前的业务归当月，之后的归下月；未填截止日按自然月。自动出单只处理已签收、未对账且在已结束账单期间内的记录。"
          type="info"
          :closable="false" /></el-form
      ><template #footer
        ><el-button :disabled="busy" @click="profileOpen = false">取消</el-button
        ><el-button type="primary" :loading="busy" :disabled="!!profileProblem" @click="saveProfile"
          >保存</el-button
        ></template
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
/* 表单内容区是 flex 容器，提示文字单独占一行，避免和输入框挤在一起 */
.finance-hint {
  flex-basis: 100%;
  width: 100%;
  margin-top: 12px;
  color: #8a94a6;
  font-size: 12px;
  line-height: 1.6;
}
.finance-hint-error {
  color: #d03050;
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
/* 公司默认方案固定置顶，和客户档案区分开。 */
.finance-page :deep(.profile-default-row) {
  font-weight: 600;
  --el-table-tr-bg-color: #fffbf0;
}
.finance-page .finance-overdue-date {
  color: #f56c6c;
  font-weight: 600;
}
.finance-page .finance-overdue-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 18px;
  padding: 0 5px;
  margin-left: 6px;
  border-radius: 3px;
  background: #fdecec;
  color: #d93025;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
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
