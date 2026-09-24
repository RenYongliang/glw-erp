<script setup lang="ts">
import type { BoardOrder } from '../../domain/orderBoards'
import { money } from '../../domain/orderBoards'
defineProps<{ orders: BoardOrder[] }>()
defineEmits<{ open: [order: BoardOrder] }>()
const rowsFor = (order: BoardOrder) =>
  Array.from({ length: Math.max(order.lines.length, order.plans.length) }, (_, index) => ({
    line: order.lines[index],
    plan: order.plans[index],
  }))
</script>

<template>
  <div class="sales-order-cards">
    <el-empty v-if="!orders.length" description="暂无符合条件的订单" />
    <article
      v-for="order in orders"
      :key="order.id"
      class="sales-order-card"
      :aria-label="`订单 ${order.id}`"
    >
      <header class="sales-card-header">
        <div class="sales-order-field sales-card-customer">
          <span>客户名称</span><strong>{{ order.partner }}</strong>
        </div>
        <div class="sales-order-field">
          <span>账期</span><strong>{{ order.paymentTerm || '待维护' }}</strong>
        </div>
        <div class="sales-order-field sales-card-total">
          <span>订单金额</span><strong>¥ {{ money(order.amount) }}</strong>
        </div>
        <div class="sales-order-field">
          <span>销售负责人</span><strong>{{ order.owner || '—' }}</strong>
        </div>
        <button class="sales-view-order" @click="$emit('open', order)">查看详情 →</button>
      </header>
      <div class="sales-table-scroll">
        <table class="sales-products-table" :aria-label="`${order.id} 产品明细及送货计划`">
          <colgroup>
            <col style="width: 11%" />
            <col style="width: 8%" />
            <col style="width: 8%" />
            <col style="width: 6%" />
            <col style="width: 6%" />
            <col style="width: 13%" />
            <col style="width: 8%" />
            <col style="width: 5%" />
            <col style="width: 9%" />
            <col style="width: 6%" />
            <col style="width: 8%" />
            <col style="width: 12%" />
          </colgroup>
          <thead>
            <tr>
              <th rowspan="2" scope="col">订单编号</th>
              <th rowspan="2" scope="col">订单日期</th>
              <th rowspan="2" scope="col">交付日期</th>
              <th colspan="6" scope="colgroup" class="sales-group-heading">产品明细</th>
              <th colspan="3" scope="colgroup" class="sales-group-heading">送货计划</th>
            </tr>
            <tr>
              <th>品牌</th>
              <th>型号</th>
              <th>封装</th>
              <th class="numeric">单价</th>
              <th class="numeric">数量</th>
              <th class="numeric">金额</th>
              <th>送货批次</th>
              <th>计划送货日期</th>
              <th>计划送货内容备注</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in rowsFor(order)" :key="index">
              <template v-if="index === 0">
                <td :rowspan="rowsFor(order).length" class="sales-order-cell sales-card-number">
                  <button @click="$emit('open', order)">{{ order.id }}</button>
                </td>
                <td :rowspan="rowsFor(order).length" class="sales-order-cell">
                  {{ order.signed || '—' }}
                </td>
                <td :rowspan="rowsFor(order).length" class="sales-order-cell">
                  {{ order.delivery || '—' }}
                </td>
              </template>
              <td>{{ row.line ? row.line.brand || '待维护' : '' }}</td>
              <td class="sales-product-model">{{ row.line?.product }}</td>
              <td>{{ row.line ? row.line.package || '待维护' : '' }}</td>
              <td class="numeric">{{ row.line ? money(row.line.price) : '' }}</td>
              <td class="numeric">{{ row.line?.quantity.toLocaleString('zh-CN') }}</td>
              <td class="numeric">{{ row.line ? money(row.line.amount) : '' }}</td>
              <td>{{ row.plan?.batch }}</td>
              <td>{{ row.plan?.date }}</td>
              <td>{{ row.plan?.note }}</td>
            </tr>
            <tr v-if="!order.lines.length && !order.plans.length">
              <td class="sales-card-number">
                <button @click="$emit('open', order)">{{ order.id }}</button>
              </td>
              <td>{{ order.signed || '—' }}</td>
              <td>{{ order.delivery || '—' }}</td>
              <td colspan="9">暂无产品明细及送货计划</td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>
  </div>
</template>

<style scoped>
.sales-order-cards {
  margin: 0 10px;
  border: 1px solid #accaff;
  background: #fff;
}
.sales-order-card {
  min-width: 0;
  border-bottom: 5px solid #fff;
}
.sales-order-card:last-child {
  border-bottom: 0;
}
.sales-card-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 5px 20px;
  padding: 8px 10px;
  background: #f0f6ff;
  border-bottom: 1px solid #accaff;
  line-height: 21px;
}
.sales-order-field {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
}
.sales-order-field > span {
  font-size: 12px;
  color: #6d83a0;
  white-space: nowrap;
}
.sales-order-field > strong {
  font-size: 13px;
  font-weight: 500;
  color: #224268;
  overflow-wrap: anywhere;
}
.sales-card-number button {
  border: 0;
  background: none;
  padding: 0;
  color: #2568c5;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  overflow-wrap: anywhere;
  line-height: 21px;
}
.sales-card-number button:hover,
.sales-view-order:hover {
  text-decoration: underline;
}
.sales-card-total strong {
  color: #224268;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.sales-view-order {
  border: 0;
  background: none;
  padding: 0;
  color: #3477c8;
  font-size: 12px;
  line-height: 21px;
  cursor: pointer;
  margin-left: auto;
  white-space: nowrap;
}
.sales-table-scroll {
  overflow-x: auto;
}
.sales-products-table {
  width: 100%;
  table-layout: fixed;
  border-collapse: collapse;
  font-size: 13px;
  line-height: 21px;
  color: #224268;
}
.sales-products-table th {
  background: #9bbff3;
  color: #17385f;
  font-weight: 400;
}
.sales-products-table td,
.sales-products-table th {
  padding: 3px 8px;
  text-align: left;
  border-bottom: 1px solid #accaff;
  border-right: 1px solid #accaff;
  overflow-wrap: anywhere;
  vertical-align: top;
}
.sales-products-table th {
  vertical-align: middle;
}
.sales-products-table .sales-group-heading {
  text-align: center;
  padding: 2px 8px;
}
.sales-products-table th:last-child,
.sales-products-table td:last-child {
  border-right: 0;
}
.sales-products-table .numeric {
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.sales-products-table tbody tr {
  background: #e1ebfb;
}
.sales-products-table tbody tr:nth-child(even) {
  background: #f8fbff;
}
.sales-products-table tbody tr:hover {
  background: #d4e5fd;
}
.sales-order-cell {
  background: #e1ebfb;
}
.sales-products-table th[rowspan],
.sales-products-table .sales-order-cell {
  text-align: center;
  vertical-align: middle;
}
.sales-products-table .sales-order-cell button {
  text-align: center;
}
.sales-product-model {
  font-weight: 500;
}
@media (min-width: 1450px) {
  .sales-order-field > strong,
  .sales-card-number button {
    font-size: 14px;
  }
}
@media (max-width: 1100px) {
  .sales-card-header {
    gap: 3px 16px;
  }
  .sales-products-table td,
  .sales-products-table th {
    padding: 3px 6px;
  }
}
@media (max-width: 700px) {
  .sales-order-cards {
    margin: 0 4px;
  }
  .sales-card-header {
    gap: 3px 12px;
    padding: 7px 8px;
  }
  .sales-order-field {
    flex-wrap: wrap;
    gap: 2px 5px;
  }
  .sales-products-table {
    min-width: 780px;
  }
}
</style>
