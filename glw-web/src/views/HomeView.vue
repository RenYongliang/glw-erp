<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  User,
  Document,
  Right,
  OfficeBuilding,
  ShoppingCart,
  DataAnalysis,
  Box,
  ArrowRight,
} from '@element-plus/icons-vue'
import OrderTracking from '../components/OrderTracking.vue'
import { useRouter } from 'vue-router'
import MetricCard from '../components/MetricCard.vue'
import TrendChart from '../components/TrendChart.vue'

const router = useRouter()
function openShortcut(title: string) {
  if (title === '销售订单看板') router.push('/boards/sales')
  else if (title === '采购订单看板') router.push('/boards/purchase')
  else if (title === '销售订单') router.push('/orders')
  else detail.value = title
}
const mode = ref('sales')
const warehouse = ref('')
const period = ref('')
const expanded = ref(['outbound'])
const detail = ref('')
const periods = ['今天', '昨天', '本周', '上周', '本月', '上月', '今年']
const business = [
  { title: '客户', icon: User, color: 'blue' },
  { title: '销售订单', icon: Document, color: 'orange' },
  { title: '销售出库', icon: Right, color: 'blue' },
  { title: '供应商信息', icon: OfficeBuilding, color: 'blue' },
  { title: '采购订单', icon: ShoppingCart, color: 'orange' },
  { title: '采购入库', icon: Right, color: 'blue' },
]
const reports = ['库存统计看板', '供应商信息查询', '销售订单看板', '采购订单看板', '仓库经营看板']
const isPurchase = computed(() => mode.value === 'purchase')
const prefix = computed(() => (isPurchase.value ? '采购' : '销售'))
const filtered = computed(() => Boolean(warehouse.value || period.value))
// 本地演示记录用于验证筛选联动；全量历史指标保留截图中的展示值。
const records = Array.from({ length: 28 }, (_, i) => {
  const date = new Date()
  date.setDate(date.getDate() - i * 3)
  date.setHours(12, 0, 0, 0)
  return {
    id: `DEMO-${String(i + 1).padStart(3, '0')}`,
    date,
    warehouse: i % 2 ? '无锡仓' : '杭州仓',
    customer: ['示例：无锡简道云', '示例：杭州帆软', '示例：厦门帆软'][i % 3]!,
    amount: 15800 + i * 2750,
    quantity: 40 + i * 9,
  }
})
const selectedRecords = computed(() => {
  const now = new Date()
  const start = new Date(now)
  start.setHours(0, 0, 0, 0)
  const end = new Date(start)
  end.setDate(end.getDate() + 1)
  switch (period.value) {
    case '昨天':
      start.setDate(start.getDate() - 1)
      end.setDate(end.getDate() - 1)
      break
    case '本周':
      start.setDate(start.getDate() - ((start.getDay() + 6) % 7))
      break
    case '上周':
      start.setDate(start.getDate() - ((start.getDay() + 6) % 7))
      end.setTime(start.getTime())
      start.setDate(start.getDate() - 7)
      break
    case '本月':
      start.setDate(1)
      break
    case '上月':
      start.setDate(1)
      end.setTime(start.getTime())
      start.setMonth(start.getMonth() - 1)
      break
    case '今年':
      start.setMonth(0, 1)
      break
  }
  return records.filter(
    (record) =>
      (!warehouse.value || record.warehouse === warehouse.value) &&
      (!period.value || (record.date >= start && record.date < end)),
  )
})
const factor = computed(() => (isPurchase.value ? 0.62 : 1))
const metrics = computed(() => {
  const data = selectedRecords.value
  const values = filtered.value
    ? [
        data.reduce((sum, row) => sum + Math.round(row.amount * factor.value), 0),
        data.length,
        data.length,
        data.reduce((sum, row) => sum + row.quantity, 0),
      ]
    : isPurchase.value
      ? [3280600, 12, 10, 2856]
      : [6510150, 19, 17, 4684]
  return [
    { title: `${prefix.value}订单金额`, value: values[0]!, unit: '元', money: true },
    { title: `${prefix.value}订单签订数`, value: values[1]!, unit: '份' },
    {
      title: `${prefix.value}${isPurchase.value ? '入' : '出'}库单数`,
      value: values[2]!,
      unit: '单',
    },
    {
      title: `${prefix.value}${isPurchase.value ? '入' : '出'}库产品数`,
      value: values[3]!,
      unit: '件',
    },
  ]
})
const chartData = computed(() => {
  if (!filtered.value)
    return {
      labels: [
        '2023年05月',
        '2023年06月',
        '2023年07月',
        '2023年08月',
        '2023年09月',
        '2024年07月',
        '2026年06月',
        '2026年07月',
        '2026年08月',
      ],
      values: [381000, 356000, 471000, 636000, 164000, 9150, 1843000, 1520000, 1130000].map(
        (value) => Math.round(value * factor.value),
      ),
    }
  const groups = new Map<string, number>()
  for (const row of [...selectedRecords.value].reverse()) {
    const label = `${row.date.getMonth() + 1}月${row.date.getDate()}日`
    groups.set(label, (groups.get(label) ?? 0) + Math.round(row.amount * factor.value))
  }
  return { labels: [...groups.keys()], values: [...groups.values()] }
})
const flowItems = computed(() => [
  {
    id: 'outbound',
    title: `${prefix.value}${isPurchase.value ? '入' : '出'}库`,
    count: filtered.value ? selectedRecords.value.length : isPurchase.value ? 12 : 19,
  },
  { id: 'returns', title: `${prefix.value}退货`, count: 6 },
  { id: 'exchange', title: `${prefix.value}换货`, count: 3 },
])
const flowRecords = computed(() => (filtered.value ? selectedRecords.value : records).slice(0, 8))
function reset() {
  warehouse.value = ''
  period.value = ''
}
</script>

<template>
  <div class="home-page">
    <section class="hero-banner">
      <div class="banner-ribbon ribbon-one" />
      <div class="banner-ribbon ribbon-two" />
      <h1>进销存管理</h1>
      <span>让业务协同更简单</span>
    </section>
    <div class="shortcut-grid">
      <section class="shortcut-panel" aria-label="业务快捷入口">
        <button
          v-for="item in business"
          :key="item.title"
          class="shortcut"
          @click="openShortcut(item.title)"
        >
          <span class="shortcut-icon" :class="item.color"
            ><el-icon><component :is="item.icon" /></el-icon></span
          >{{ item.title }}
        </button>
      </section>
      <section class="shortcut-panel" aria-label="报表快捷入口">
        <button v-for="title in reports" :key="title" class="shortcut" @click="openShortcut(title)">
          <span class="shortcut-icon purple"
            ><el-icon><DataAnalysis /></el-icon></span
          >{{ title }}
        </button>
      </section>
    </div>
    <section class="dashboard">
      <el-tabs v-model="mode" class="report-tabs"
        ><el-tab-pane label="销售报表" name="sales" /><el-tab-pane label="采购报表" name="purchase"
      /></el-tabs>
      <div class="dashboard-grid">
        <aside class="flow-panel">
          <h2>{{ prefix }}业务流</h2>
          <el-collapse v-model="expanded">
            <el-collapse-item v-for="item in flowItems" :key="item.id" :name="item.id">
              <template #title
                ><span class="flow-icon"
                  ><el-icon><Document /></el-icon></span
                ><span>{{ item.title }}</span
                ><span class="count-badge">{{ item.count }}</span></template
              >
              <template v-if="item.id === 'outbound'"
                ><button
                  v-for="row in flowRecords"
                  :key="row.id"
                  class="flow-record"
                  :title="`${row.customer}-${row.warehouse}-${row.quantity}`"
                  @click="detail = `${item.title} · ${row.id}`"
                >
                  {{ row.customer }}-{{ row.warehouse }}-{{ row.quantity }}
                </button>
                <p v-if="!flowRecords.length" class="menu-empty">当前条件下暂无记录</p></template
              >
              <button v-else class="flow-record" @click="openShortcut(item.title)">
                查看{{ item.title }}记录 <el-icon><ArrowRight /></el-icon>
              </button>
            </el-collapse-item>
          </el-collapse>
        </aside>
        <div class="report-main">
          <div class="filter-row">
            <div class="filter-panel warehouse-filters">
              <el-button
                v-for="name in ['杭州仓', '无锡仓']"
                :key="name"
                :type="warehouse === name ? 'primary' : 'default'"
                :plain="warehouse === name"
                :aria-pressed="warehouse === name"
                @click="warehouse = warehouse === name ? '' : name"
                >{{ name }}</el-button
              >
            </div>
            <div class="filter-panel period-filters">
              <el-button
                v-for="name in periods"
                :key="name"
                :type="period === name ? 'primary' : 'default'"
                :plain="period === name"
                :aria-pressed="period === name"
                @click="period = period === name ? '' : name"
                >{{ name }}</el-button
              >
            </div>
          </div>
          <div v-if="filtered" class="filter-summary">
            <span
              >演示记录 · {{ warehouse || '全部仓库' }} / {{ period || '全部时间' }} ·
              {{ selectedRecords.length }} 条</span
            ><el-button link type="primary" @click="reset">重置筛选</el-button>
          </div>
          <div class="metrics-grid">
            <MetricCard v-for="metric in metrics" :key="metric.title" v-bind="metric" />
          </div>
          <TrendChart
            v-if="chartData.values.length"
            :title="isPurchase ? '采购金额趋势' : '销售业绩趋势'"
            :labels="chartData.labels"
            :values="chartData.values"
          />
          <section v-else class="chart-card">
            <h3>{{ prefix }}业绩趋势</h3>
            <el-empty description="当前筛选条件下暂无数据" :image-size="64" />
          </section>
        </div>
      </div>
    </section>
    <OrderTracking v-if="!isPurchase" />
    <div class="page-note">
      <el-icon><Box /></el-icon>当前为首页演示，统计与筛选使用本地示例数据。
    </div>
    <el-drawer
      :model-value="Boolean(detail)"
      :title="detail"
      size="min(480px, 92vw)"
      @close="detail = ''"
      ><el-empty :description="`${detail}页面建设中`" />
      <p class="drawer-note">此入口已预留，后续将根据对应截图逐步接入表单、明细和业务操作。</p>
      <el-button type="primary" @click="detail = ''">返回工作台</el-button></el-drawer
    >
  </div>
</template>
