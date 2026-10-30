<script setup lang="ts">
import type { OpenItem } from '../../services/financeApi'
defineProps<{ rows: OpenItem[]; selectable?: boolean; loading?: boolean; height?: string }>()
defineEmits<{ selection: [rows: OpenItem[]] }>()
const money = (v: number | string | null | undefined) =>
  v == null
    ? '待确认'
    : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const statuses: Record<string, string> = {
  PENDING_TAX: '税率待确认',
  OPEN: '待对账',
  IN_STATEMENT: '对账中',
  RECONCILED: '已确认',
}
</script>
<template>
  <el-table
    v-loading="loading"
    :data="rows"
    row-key="id"
    border
    :height="height"
    empty-text="暂无财务记录"
    @selection-change="(rows: OpenItem[]) => $emit('selection', rows)"
  >
    <el-table-column
      v-if="selectable"
      type="selection"
      width="42"
      :selectable="(row: OpenItem) => row.reconcileStatus === 'OPEN' && row.basisReady !== 0"
    />
    <el-table-column label="往来单位" min-width="145" prop="partnerName" show-overflow-tooltip />
    <el-table-column label="出入库单号" width="155" prop="sourceNo" show-overflow-tooltip />
    <el-table-column label="型号" min-width="145" prop="partNo" show-overflow-tooltip />
    <el-table-column label="数量" width="100" align="right" prop="quantity" />
    <el-table-column label="价税合计" width="125" align="right"
      ><template #default="{ row }">{{ money(row.grossAmount) }}</template></el-table-column
    >
    <el-table-column label="已结金额" width="115" align="right"
      ><template #default="{ row }">{{ money(row.settledAmount) }}</template></el-table-column
    >
    <el-table-column label="未结金额" width="115" align="right"
      ><template #default="{ row }"
        ><strong>{{
          row.grossAmount == null
            ? '待确认'
            : money(Number(row.grossAmount) - Number(row.settledAmount))
        }}</strong></template
      ></el-table-column
    >
    <el-table-column label="签收/入库日" width="120"
      ><template #default="{ row }">{{
        row.basisDate || (row.basisReady === 0 ? '待签收' : '')
      }}</template></el-table-column
    >
    <el-table-column label="账单月" width="95" prop="settleMonth" />
    <el-table-column label="到期日" width="110" prop="dueDate" />
    <el-table-column label="对账状态" width="100"
      ><template #default="{ row }"
        ><el-tag v-if="row.basisReady === 0" type="danger" effect="plain">待签收</el-tag>
        <el-tag
          :type="
            row.reconcileStatus === 'RECONCILED'
              ? 'success'
              : row.reconcileStatus === 'OPEN'
                ? 'info'
                : 'warning'
          "
          effect="plain"
          >{{ statuses[row.reconcileStatus] || row.reconcileStatus }}</el-tag
        ></template
      ></el-table-column
    >
    <slot />
    <el-table-column label="业务日期" width="110" prop="bizDate" />
    <el-table-column label="订单号" width="150" prop="orderNo" show-overflow-tooltip />
    <el-table-column label="客户采购单号" width="155" prop="partnerOrderNo" show-overflow-tooltip />
    <el-table-column label="品牌" width="65" prop="brand" show-overflow-tooltip />
    <el-table-column label="封装" width="160" prop="packageType" show-overflow-tooltip />
    <el-table-column label="单价" width="105" align="right"
      ><template #default="{ row }">{{
        Number(row.unitPrice).toFixed(4)
      }}</template></el-table-column
    >
    <el-table-column label="税率" width="90" align="right"
      ><template #default="{ row }">{{
        row.taxRate == null ? '待确认' : `${row.taxRate}%`
      }}</template></el-table-column
    >
    <el-table-column label="未税金额" width="115" align="right"
      ><template #default="{ row }">{{ money(row.netAmount) }}</template></el-table-column
    >
    <el-table-column label="税额" width="105" align="right"
      ><template #default="{ row }">{{ money(row.taxAmount) }}</template></el-table-column
    >
  </el-table>
</template>
