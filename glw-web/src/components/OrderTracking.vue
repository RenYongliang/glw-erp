<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

type State = '已收款' | '待收款' | '待对账' | '待出库' | '部分收款' | '部分退货'
interface Line {
  product: string
  code: string
  quantity: number
  price: number
}
interface Order {
  id: string
  name: string
  customer: string
  warehouse: string
  date: string
  state: State
  note: string
  lines: Line[]
}
const products = [
  { product: 'MTQ8200-HS2F', code: 'CP0001', price: 1400 },
  { product: 'MQM8790-HS2R', code: 'CP0002', price: 1300 },
  { product: 'Catalyst 8300', code: 'CP0003', price: 1500 },
  { product: 'Dusun Pi4', code: 'CP0004', price: 1600 },
  { product: 'UPS', code: 'CP0005', price: 680 },
  { product: 'ST1L05', code: 'CP0006', price: 680 },
]
const states: State[] = ['已收款', '待收款', '待对账', '待出库', '部分收款', '部分退货']
const orders = ref<Order[]>(
  Array.from({ length: 12 }, (_, i) => {
    const customer = ['无锡简道云', '无锡帆软', '杭州简道云', '上海帆软', '杭州帆软', '厦门帆软'][
      i % 6
    ]!
    return {
      id: `XSDD260923-${String(12 - i).padStart(2, '0')}`,
      name: `示例：${customer}-第${12 - i}份销售订单`,
      customer: `示例：${customer}`,
      warehouse: i % 2 ? '无锡仓' : '杭州仓',
      date: `2026-09-${String(23 - i).padStart(2, '0')}`,
      state: states[i % 6]!,
      note: '测试订单，仅用于界面演示。',
      lines: Array.from({ length: 3 + (i % 3) }, (_, j) => ({
        ...products[(i + j) % products.length]!,
        quantity: i % 3 === 0 ? 100 : (j + 1) * 50,
      })),
    }
  }),
)
const keyword = ref('')
const state = ref('')
const page = ref(1)
const pageSize = ref(20)
function resetFilters() {
  keyword.value = ''
  state.value = ''
}
watch([keyword, state, pageSize], () => {
  page.value = 1
})
const filtered = computed(() =>
  orders.value.filter(
    (order) =>
      (!state.value || order.state === state.value) &&
      `${order.id}${order.name}${order.customer}${order.lines.map((line) => line.product).join('')}`
        .toLowerCase()
        .includes(keyword.value.trim().toLowerCase()),
  ),
)
function amounts(order: Order, line: Line) {
  const outbound = order.state === '待出库' ? 0 : line.quantity
  const returned = order.state === '部分退货' ? Math.floor(line.quantity / 5) : 0
  const settled = ['待出库', '待对账'].includes(order.state) ? 0 : outbound - returned
  const receivable = settled * line.price
  const paid =
    order.state === '待收款' ? 0 : order.state === '部分收款' ? receivable / 2 : receivable
  return {
    outbound,
    returned,
    pending: line.quantity - outbound,
    settled,
    unsettled: outbound - returned - settled,
    receivable,
    paid,
    unpaid: receivable - paid,
  }
}
const rows = computed(() =>
  filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value).flatMap((order) =>
    order.lines.map((line, index) => ({
      ...line,
      ...amounts(order, line),
      order,
      span: index === 0 ? order.lines.length : 0,
    })),
  ),
)
type Row = (typeof rows.value)[number]
const money = (value: number) =>
  value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const totals = computed(() =>
  filtered.value
    .flatMap((order) => order.lines.map((line) => amounts(order, line)))
    .reduce(
      (total, line) => ({
        receivable: total.receivable + line.receivable,
        paid: total.paid + line.paid,
        unpaid: total.unpaid + line.unpaid,
      }),
      { receivable: 0, paid: 0, unpaid: 0 },
    ),
)
function spanMethod({ row, columnIndex }: { row: Row; columnIndex: number }) {
  return columnIndex < 3 ? [row.span, row.span ? 1 : 0] : [1, 1]
}
function cellClass({ row, column }: { row: Row; column: { property: string } }) {
  if (column.property === 'unpaid' && row.unpaid > 0) return 'cell-red'
  if (column.property === 'unsettled' && row.unsettled > 0) return 'cell-orange'
  if (column.property === 'pending' && row.pending > 0) return 'cell-yellow'
  return ''
}
const selected = ref<Order>()
const draft = reactive({ customer: '', warehouse: '', date: '', note: '' })
function open(order: Order) {
  selected.value = order
  Object.assign(draft, {
    customer: order.customer,
    warehouse: order.warehouse,
    date: order.date,
    note: order.note,
  })
}
function save() {
  if (!draft.customer.trim() || !draft.warehouse || !draft.date) {
    ElMessage.warning('请填写客户、仓库和订单日期')
    return
  }
  if (selected.value) Object.assign(selected.value, { ...draft, customer: draft.customer.trim() })
  selected.value = undefined
  ElMessage.success('已保存本次演示修改，刷新页面后恢复测试数据')
}
</script>

<template>
  <section class="order-tracking">
    <div class="tracking-heading">
      <div>
        <h2>销售订单-出库-退换货-对账执行跟踪</h2>
        <p>12 笔测试订单 · 48 条产品明细 · 点击订单编号查看表单</p>
      </div>
      <el-tag type="info">演示数据 · 刷新恢复</el-tag>
    </div>
    <div class="tracking-toolbar">
<el-select v-model="state" placeholder="全部订单状态" aria-label="订单状态" clearable
        ><el-option v-for="item in states" :key="item" :label="item" :value="item" /></el-select
      ><el-button @click="resetFilters">重置</el-button
      ><span class="tracking-legend"
        ><i class="yellow" />待出库 <i class="orange" />待对账 <i class="red" />待收款</span
      >
    </div>
    <div class="tracking-totals">
      <span
        >应收金额 <b>¥ {{ money(totals.receivable) }}</b></span
      ><span
        >已收金额 <b>¥ {{ money(totals.paid) }}</b></span
      ><span
        >待收金额 <b class="unpaid-total">¥ {{ money(totals.unpaid) }}</b></span
      >
    </div>
    <el-table
      :data="rows"
      border
      stripe
      :span-method="spanMethod"
      :cell-class-name="cellClass"
      max-height="620"
      empty-text="没有匹配的测试订单"
      class="tracking-table"
    >
      <el-table-column label="销售订单编号" width="140" fixed show-overflow-tooltip
        ><template #default="{ row }"
          ><el-button link type="primary" @click="open(row.order)">{{
            row.order.id
          }}</el-button></template
        ></el-table-column
      >
      <el-table-column prop="order.name" label="销售订单名称" width="160" />
      <el-table-column prop="order.customer" label="客户名称" width="125" />
      <el-table-column prop="product" label="产品名称" width="128" />
      <el-table-column prop="code" label="产品编号" width="85" />
      <el-table-column prop="quantity" label="销售数量" width="80" />
      <el-table-column label="未出库-退货数量" width="122"
        ><template #default>0</template></el-table-column
      >
      <el-table-column prop="outbound" label="出库数量" width="80" />
      <el-table-column prop="returned" label="已出库-退货数量" width="122" />
      <el-table-column label="换货数量" width="80"><template #default>0</template></el-table-column>
      <el-table-column prop="pending" label="待出库数量" width="90" />
      <el-table-column label="库存冻结数量" width="104"
        ><template #default>0</template></el-table-column
      >
      <el-table-column label="计划出库数量" width="104"
        ><template #default>0</template></el-table-column
      >
      <el-table-column prop="settled" label="已对账数量" width="90" />
      <el-table-column prop="unsettled" label="待对账数量" width="90" />
      <el-table-column
        v-for="column in [
          { prop: 'receivable', label: '应收金额/元' },
          { prop: 'paid', label: '收款金额/元' },
          { prop: 'unpaid', label: '待收款金额/元' },
        ]"
        :key="column.prop"
        :prop="column.prop"
        :label="column.label"
        width="125"
        align="right"
        ><template #default="{ row }">{{ money(row[column.prop]) }}</template></el-table-column
      >
      <el-table-column prop="order.state" label="订单状态" width="100" />
    </el-table>
    <div class="tracking-footer">
      <span
        >{{
          filtered.reduce((sum, order) => sum + order.lines.length, 0)
        }}
        条明细（按订单分页）</span
      ><el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :page-sizes="[10,20,50,100]"
        :total="filtered.length"
        layout="total, sizes, prev, pager, next, jumper"
        background
      />
    </div>
    <el-drawer
      :model-value="Boolean(selected)"
      title="销售订单表单"
      size="min(780px, 96vw)"
      @close="selected = undefined"
    >
      <template v-if="selected"
        ><el-alert
          title="本地测试表单：保存仅在当前页面生效，刷新后恢复。"
          type="info"
          :closable="false"
        />
        <el-form class="order-form" :model="draft" label-position="top">
          <el-form-item label="订单编号"
            ><el-input :model-value="selected.id" disabled
          /></el-form-item>
          <el-form-item label="客户名称" required
            ><el-input v-model="draft.customer" aria-label="客户名称"
          /></el-form-item>
          <el-form-item label="所属仓库" required
            ><el-select v-model="draft.warehouse" aria-label="所属仓库"
              ><el-option label="杭州仓" value="杭州仓" /><el-option
                label="无锡仓"
                value="无锡仓" /></el-select
          ></el-form-item>
          <el-form-item label="订单日期" required
            ><el-date-picker
              v-model="draft.date"
              type="date"
              value-format="YYYY-MM-DD"
              aria-label="订单日期"
          /></el-form-item>
          <el-form-item label="执行状态"
            ><el-tag>{{ selected.state }}</el-tag></el-form-item
          >
          <el-form-item label="备注" class="form-wide"
            ><el-input v-model="draft.note" type="textarea" :rows="3" aria-label="备注"
          /></el-form-item>
        </el-form>
        <h3 class="detail-subtitle">产品明细</h3>
        <el-table :data="selected.lines" border
          ><el-table-column prop="product" label="产品" min-width="140" /><el-table-column
            prop="quantity"
            label="数量"
            width="65"
          /><el-table-column label="单价/元" min-width="100"
            ><template #default="{ row }">{{ money(row.price) }}</template></el-table-column
          ><el-table-column label="订单金额/元" min-width="120"
            ><template #default="{ row }">{{
              money(row.quantity * row.price)
            }}</template></el-table-column
          ></el-table
        >
        <p class="order-total">
          订单总额：¥
          {{ money(selected.lines.reduce((sum, line) => sum + line.quantity * line.price, 0)) }}
        </p>
      </template>
      <template #footer
        ><el-button @click="selected = undefined">取消</el-button
        ><el-button type="primary" @click="save">保存测试修改</el-button></template
      >
    </el-drawer>
  </section>
</template>

<style scoped>
.order-tracking {
  background: white;
  padding: 14px;
  margin-top: 12px;
  min-width: 0;
}
.tracking-heading,
.tracking-toolbar,
.tracking-footer,
.tracking-totals {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.tracking-heading,
.tracking-footer {
  justify-content: space-between;
}
.tracking-heading h2 {
  font-size: 14px;
  font-weight: 500;
}
.tracking-heading p {
  font-size: 12px;
  color: #8c97a8;
  margin-top: 7px;
}
.tracking-toolbar {
  margin: 18px 0 12px;
}
.tracking-toolbar .el-input {
  width: 250px;
}
.tracking-toolbar .el-select {
  width: 155px;
}
.tracking-legend {
  display: flex;
  align-items: center;
  gap: 7px;
  color: #77849a;
  font-size: 12px;
  margin-left: auto;
}
.tracking-legend i {
  width: 12px;
  height: 12px;
  display: inline-block;
}
.yellow {
  background: #eddd87;
}
.orange {
  background: #ffd18a;
}
.red {
  background: #fa9286;
}
.tracking-totals {
  padding: 12px;
  background: #f5f8fe;
  margin-bottom: 12px;
  gap: 28px;
  color: #738099;
}
.tracking-totals b {
  font-size: 15px;
  font-weight: 500;
  color: #287fe0;
  margin-left: 8px;
}
.tracking-totals b.unpaid-total {
  color: #e56a59;
}
.tracking-table {
  --el-table-border-color: #accaff;
  --el-table-header-bg-color: #9fc2f5;
  --el-table-header-text-color: #203c65;
  --el-table-text-color: #23456a;
  --el-table-row-hover-bg-color: #d7e7ff;
  font-size: 13px;
}
.tracking-table :deep(.el-table__cell) {
  padding: 7px 0;
  vertical-align: top;
}
.tracking-table :deep(.cell) {
  line-height: 22px;
  overflow-wrap: anywhere;
}
.tracking-table :deep(.el-table__body tr) {
  --el-table-tr-bg-color: #e1ebfb;
}
.tracking-table :deep(.el-table__body tr.el-table__row--striped td.el-table__cell) {
  background: #f8fbff;
}
.tracking-table :deep(td.cell-red) {
  background: #fa9286 !important;
}
.tracking-table :deep(td.cell-orange) {
  background: #ffd18a !important;
}
.tracking-table :deep(td.cell-yellow) {
  background: #eddd87 !important;
}
.tracking-table :deep(.el-button) {
  font-size: 13px;
  white-space: normal;
}
.tracking-footer {
  padding-top: 12px;
  color: #7b879a;
  font-size: 12px;
}
.order-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 20px;
  margin-top: 20px;
}
.form-wide {
  grid-column: 1/-1;
}
.order-form :deep(.el-date-editor) {
  width: 100%;
}
.detail-subtitle {
  margin: 12px 0;
}
.order-total {
  margin-top: 16px;
  text-align: right;
  font-size: 16px;
  color: #1687ff;
}
@media (max-width: 700px) {
  .tracking-legend {
    margin-left: 0;
  }
  .tracking-totals {
    gap: 10px;
  }
  .tracking-toolbar .el-input {
    width: 100%;
  }
  .order-form {
    grid-template-columns: 1fr;
  }
  .tracking-heading .el-tag {
    margin-top: 4px;
  }
}
</style>
