<script setup lang="ts">
import { computed, ref, watch, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Plus,
  Upload,
  Download,
  Delete,
  Clock,
  Search,
  Filter,
  Refresh,
  Sort,
  Setting,
  Operation,
  FullScreen,
  Document,
} from "@element-plus/icons-vue";
import SalesRecordDialog from "../components/sales/SalesRecordDialog.vue";
import { useSalesStore } from "../stores/sales";
import {
  customers,
  orderCustomerName,
  products,
  money,
  today,
  orderTotals,
  shipmentTotals,
  returnableQuantity,
  remainingQuantity,
  chineseMoney,
  type SalesKind,
  type SalesOrder,
  type SalesShipment,
  type SalesPayment,
  type SalesReturn,
} from "../domain/sales";
import { encodeCsv, parseCsv } from "../domain/crm";
import "../assets/purchase.css";
const props = defineProps<{ kind: SalesKind }>(),
  store = useSalesStore(),
  isOrder = props.kind === "orders";
const tabs = isOrder
  ? ["全部订单", "待审批订单", "已审批订单", "待上传合同附件", "不通过订单"]
  : ["全部出库", "已出库", "库存冻结", "计划出库"];
const tab = ref(tabs[0]!),
  keyword = ref(""),
  filterOpen = ref(false),
  filterKey = ref(isOrder ? "code" : "name"),
  filterOperator = ref("包含"),
  filterValue = ref(""),
  grouped = ref(true),
  collapsed = ref<string[]>([]),
  selected = ref<string[]>([]),
  sortKey = ref(""),
  sortDirection = ref("asc"),
  hidden = ref<string[]>([]),
  logsOpen = ref(false),
  expanded = ref(false),
  rowHeight = ref("normal");
const editor = ref(false),
  editorKind = ref<SalesKind>(props.kind),
  editingOrder = ref<SalesOrder>(),
  editingShipment = ref<SalesShipment>(),
  relatedOrder = ref<SalesOrder>(),
  readonly = ref(false),
  editorVersion = ref(0);
type TableRow = {
  id: string;
  group: string;
  values: Record<string, string | number>;
  order?: SalesOrder;
  shipment?: SalesShipment;
};
const orderColumns = [
  ["code", "订单编号"],
  ["signed", "订单日期"],
  ["customer", "客户名称"],
  ["brands", "品牌"],
  ["productNames", "型号"],
  ["models", "封装"],
  ["prices", "单价"],
  ["ordered", "数量"],
  ["amounts", "金额"],
  ["total", "订单金额"],
  ["term", "账期"],
  ["delivery", "交付日期"],
  ["owner", "销售负责人"],
];
const orderWidths: Record<string, number> = {
  code: 140,
  signed: 95,
  customer: 135,
  brands: 65,
  productNames: 85,
  models: 85,
  prices: 65,
  ordered: 65,
  amounts: 90,
  total: 100,
  term: 90,
  delivery: 95,
  owner: 95,
};
function columnWidth(key: string) {
  return isOrder ? orderWidths[key] || 100 : key === "name" ? 230 : 130;
}
const shipmentColumns = [
  ["name", "销售订单名称"],
  ["orderCode", "销售订单编号"],
  ["contact", "客户联系人姓名"],
  ["phone", "客户联系人手机"],
  ["owner", "销售负责人"],
  ["department", "销售归属部门"],
  ["code", "销售出库单编号"],
  ["tag", "产品出库标签"],
  ["warehouse", "出库仓库"],
  ["productNames", "产品名称"],
  ["productCodes", "产品编码"],
  ["brands", "品牌"],
  ["models", "规格型号"],
  ["units", "单位"],
  ["ordered", "销售数量"],
  ["pending", "待出库数量"],
  ["quantity", "本次出库数量"],
  ["total", "出库产品售价总额(含税)/元"],
  ["status", "出库状态"],
];
function productSummary(
  order: SalesOrder | undefined,
  shipment?: SalesShipment,
) {
  const lines = shipment
    ? order?.lines.filter((l) =>
        shipment.lines.some((s) => s.lineId === l.id),
      ) || []
    : order?.lines || [];
  const values = (key: "name" | "code" | "brand" | "model" | "unit") =>
    lines
      .map((l) => products.find((p) => p.id === l.productId)?.[key] || "—")
      .join("\n");
  return {
    productNames: values("name"),
    productCodes: values("code"),
    brands: values("brand"),
    models: values("model"),
    prices: lines.map((l) => money(l.price)).join("\n"),
    amounts: lines.map((l) => money(l.price * l.quantity)).join("\n"),
    units: values("unit"),
    ordered: lines.map((l) => l.quantity).join("\n"),
    pending: lines
      .map((l) => remainingQuantity(store.state, order!.id, l.id, shipment?.id))
      .join("\n"),
  };
}
const columns = (isOrder ? orderColumns : shipmentColumns).map(
  ([key, label]) => ({
    key: key!,
    label: label!,
  }),
);
const shownColumns = computed(() =>
  columns.filter((c) => !hidden.value.includes(c.key)),
);
const productKeys = isOrder
  ? ["brands", "productNames", "models", "prices", "ordered", "amounts"]
  : [
      "productNames",
      "productCodes",
      "brands",
      "models",
      "units",
      "ordered",
      "pending",
    ];
const productColumns = computed(() =>
  shownColumns.value.filter((c) => productKeys.includes(c.key)),
);
const leadingColumns = computed(() =>
  shownColumns.value.filter((c) =>
    isOrder
      ? ["code", "signed", "customer"].includes(c.key)
      : (!productKeys.includes(c.key) &&
          !["total", "quantity", "status"].includes(c.key)) ||
        (isOrder && c.key === "status"),
  ),
);
const trailingColumns = computed(() =>
  shownColumns.value.filter((c) =>
    isOrder
      ? ["total", "term", "delivery", "owner"].includes(c.key)
      : ["total", "quantity", "status"].includes(c.key) &&
        !(isOrder && c.key === "status"),
  ),
);

const records = computed<TableRow[]>(() =>
  isOrder
    ? store.state.orders.map((o) => {
        const t = orderTotals(o);
        return {
          id: o.id,
          order: o,
          group: orderCustomerName(o) || "未填写",
          values: {
            ...productSummary(o),
            customer: orderCustomerName(o) || "—",
            term: o.term,
            priceLevel: o.priceLevel || "标准售价",
            signed: o.signed,
            delivery: o.delivery,
            contact:
              customers.find((c) => c.id === o.customerId)?.contact || "",
            phone: customers.find((c) => c.id === o.customerId)?.phone || "",
            name: o.name,
            code: o.code,
            owner: o.owner,
            department: o.department,
            warehouse: o.warehouse,
            gross: t.gross,
            discount: o.discount,
            rate: t.rate.toFixed(2) + "%",
            total: t.total,
            uppercase: chineseMoney(t.total),
            net: t.net,
            attachments: o.attachments.map((a) => a.name).join("、"),
            address: o.address,
            status: o.status,
          },
        };
      })
    : store.state.shipments.map((r) => {
        const o = store.state.orders.find((o) => o.id === r.orderId),
          s = customers.find((s) => s.id === o?.customerId),
          t = shipmentTotals(r, o);
        return {
          id: r.id,
          shipment: r,
          order: o,
          group: orderCustomerName(o) || "未填写",
          values: {
            ...productSummary(o, r),
            tag: r.tag,
            warehouse: r.warehouse,
            name: o?.name || "—",
            orderCode: o?.code || "—",
            customer: orderCustomerName(o) || "—",
            customerCode: s?.code || "—",
            contact: s?.contact || "—",
            phone: s?.phone || "—",
            owner: o?.owner || "—",
            department: o?.department || "—",
            code: r.code,
            quantity: t.quantity,
            total: t.total,
            status: r.confirmed ? "已出库" : "待出库",
          },
        };
      }),
);
const filtered = computed(() => {
  const rows = records.value.filter((r) => {
    if (
      isOrder &&
      tab.value !== tabs[0] &&
      tab.value !== "待上传合同附件" &&
      r.values.status !==
        (
          {
            待审批订单: "待审批",
            已审批订单: "审批通过",
            不通过订单: "不通过",
          } as Record<string, string>
        )[tab.value]
    )
      return false;
    if (
      isOrder &&
      tab.value === "待上传合同附件" &&
      r.order?.attachments.length
    )
      return false;
    if (!isOrder && tab.value === "已出库" && !r.shipment?.confirmed)
      return false;
    if (
      !isOrder &&
      ["库存冻结", "计划出库"].includes(tab.value) &&
      r.shipment?.tag !== tab.value
    )
      return false;
    if (
      keyword.value.trim() &&
      ![r.group, ...Object.values(r.values)]
        .join(" ")
        .toLowerCase()
        .includes(keyword.value.trim().toLowerCase())
    )
      return false;
    if (!filterOpen.value) return true;
    const v = String(r.values[filterKey.value] ?? "");
    if (filterOperator.value === "为空") return !v;
    if (filterOperator.value === "不为空") return !!v;
    if (!filterValue.value) return true;
    if (filterOperator.value === "等于") return v === filterValue.value;
    if (filterOperator.value === "不等于") return v !== filterValue.value;
    return filterOperator.value === "不包含"
      ? !v.includes(filterValue.value)
      : v.includes(filterValue.value);
  });
  if (sortKey.value)
    rows.sort((a, b) => {
      const x = a.values[sortKey.value] ?? "",
        y = b.values[sortKey.value] ?? "";
      return (
        (typeof x === "number" && typeof y === "number"
          ? x - y
          : String(x).localeCompare(String(y), "zh-CN")) *
        (sortDirection.value === "asc" ? 1 : -1)
      );
    });
  return rows;
});
const groups = computed(() =>
  grouped.value
    ? [...new Set(filtered.value.map((r) => r.group))].map((name) => ({
        name,
        rows: filtered.value.filter((r) => r.group === name),
      }))
    : [{ name: "", rows: filtered.value }],
);
const allSelected = computed(
  () =>
    !!filtered.value.length &&
    filtered.value.every((r) => selected.value.includes(r.id)),
);
watch(
  [tab, keyword, filterOpen, filterKey, filterValue, filterOperator],
  () => (selected.value = []),
);
function selectRows(rows: TableRow[], checked: boolean) {
  selected.value = checked
    ? [...new Set([...selected.value, ...rows.map((r) => r.id)])]
    : selected.value.filter((id) => !rows.some((r) => r.id === id));
}
function toggleGroup(name: string) {
  collapsed.value = collapsed.value.includes(name)
    ? collapsed.value.filter((n) => n !== name)
    : [...collapsed.value, name];
}
function display(row: TableRow, key: string) {
  const value = row.values[key];
  return typeof value === "number" && key !== "quantity" ? money(value) : value;
}
function open(row?: TableRow, read = false) {
  editorKind.value = props.kind;
  editingOrder.value = isOrder ? row?.order : undefined;
  editingShipment.value = isOrder ? undefined : row?.shipment;
  relatedOrder.value = undefined;
  readonly.value = read;
  editorVersion.value++;
  editor.value = true;
}
function ship(order: SalesOrder) {
  editor.value = false;
  editorKind.value = "shipments";
  editingOrder.value = undefined;
  editingShipment.value = undefined;
  relatedOrder.value = order;
  readonly.value = false;
  editorVersion.value++;
  editor.value = true;
}
function saved() {
  editor.value = false;
  selected.value = [];
}
async function remove() {
  if (!selected.value.length) return ElMessage.info("请先选择需要删除的数据");
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${selected.value.length} 条数据吗？`,
      "删除数据",
      {
        type: "warning",
        confirmButtonText: "删除",
        cancelButtonText: "取消",
      },
    );
    store.remove(props.kind, selected.value);
    selected.value = [];
    ElMessage.success("已删除");
  } catch (e) {
    if (e instanceof Error) ElMessage.error(e.message);
  }
}
function refresh() {
  store.load();
  selected.value = [];
  if (store.loadError) ElMessage.warning(store.loadError);
  else ElMessage.success("数据已刷新");
}
onMounted(() => {
  if (store.loadError) ElMessage.warning(store.loadError);
});
const operation = ref(""),
  activeOrder = ref<SalesOrder>(),
  payment = ref<SalesPayment>(),
  returned = ref<SalesReturn>();
const unpaid = computed(() =>
  activeOrder.value
    ? orderTotals(activeOrder.value).total -
      store.state.payments
        .filter((p) => p.orderId === activeOrder.value!.id)
        .reduce((s, p) => s + p.amount, 0)
    : 0,
);
function business(order: SalesOrder, kind: string) {
  activeOrder.value = order;
  operation.value = kind;
  if (kind === "收款")
    payment.value = {
      id: crypto.randomUUID(),
      orderId: order.id,
      amount: Math.max(0, Math.round(unpaid.value * 100) / 100),
      date: today(),
      method: "银行转账",
      note: "",
    };
  else
    returned.value = {
      id: crypto.randomUUID(),
      orderId: order.id,
      lineId:
        order.lines.find(
          (l) => returnableQuantity(store.state, order.id, l.id) > 0,
        )?.id || "",
      quantity: 1,
      date: today(),
      reason: "",
    };
}
function submitBusiness() {
  try {
    if (operation.value === "收款") store.pay(payment.value!);
    else store.returnGoods(returned.value!);
    operation.value = "";
    ElMessage.success("已保存");
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}
function download(name: string, rows: unknown[][]) {
  const url = URL.createObjectURL(
    new Blob([encodeCsv(rows)], { type: "text/csv;charset=utf-8" }),
  );
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
function exportData() {
  const rows = selected.value.length
    ? filtered.value.filter((r) => selected.value.includes(r.id))
    : filtered.value;
  download(`${isOrder ? "销售订单" : "销售出库"}-${today()}.csv`, [
    shownColumns.value.map((c) => c.label),
    ...rows.map((r) => shownColumns.value.map((c) => r.values[c.key])),
  ]);
  ElMessage.success(`已导出 ${rows.length} 条数据`);
}
const importOpen = ref(false),
  importRows = ref<Record<string, string>[]>([]),
  importError = ref(""),
  fileName = ref("");
const importHeaders = isOrder
  ? [
      "销售订单名称",
      "客户编码",
      "签订日期",
      "交付日期",
      "出库仓库",
      "产品编码",
      "销售数量",
      "含税单价",
      "优惠金额",
    ]
  : [
      "销售订单编号",
      "出库仓库",
      "产品编码",
      "出库数量",
      "出库仓位",
      "产品出库标签",
    ];
function downloadTemplate() {
  const sampleOrder = store.state.orders.find(
    (o) =>
      o.status === "审批通过" &&
      o.lines.some((l) => remainingQuantity(store.state, o.id, l.id) > 0),
  );
  const sampleLine = sampleOrder?.lines.find(
    (l) => remainingQuantity(store.state, sampleOrder.id, l.id) > 0,
  );
  download(`${isOrder ? "销售订单" : "销售出库"}导入模板.csv`, [
    importHeaders,
    isOrder
      ? [
          "测试销售订单",
          "KH001",
          today(),
          today(),
          "杭州仓",
          "LCW0001",
          100,
          8.5,
          0,
        ]
      : [
          sampleOrder?.code || "请填写订单编号",
          sampleOrder?.warehouse || "请填写出库仓库",
          products.find((p) => p.id === sampleLine?.productId)?.code ||
            "请填写产品编码",
          1,
          "HZ-A01",
          "计划出库",
        ],
  ]);
}
async function readImport(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  importRows.value = [];
  importError.value = "";
  if (!file) return;
  fileName.value = file.name;
  try {
    if (file.size > 2 * 1024 * 1024) throw Error("导入文件不能超过 2MB");
    const rows = parseCsv(await file.text());
    const head = rows.shift() || [];
    if (importHeaders.some((h) => !head.includes(h)))
      throw Error("列名不完整，请下载导入模板填写");
    const data = rows.filter((r) => r.some((v) => v.trim()));
    if (!data.length || data.length > 500) throw Error("每次导入 1 至 500 行");
    importRows.value = data.map((r) =>
      Object.fromEntries(head.map((h, i) => [h, r[i]?.trim() || ""])),
    );
  } catch (e) {
    importError.value = (e as Error).message;
  }
}
function confirmImport() {
  try {
    if (!importRows.value.length) throw Error("请先选择有效文件");
    if (isOrder) {
      const result: SalesOrder[] = [];
      for (const r of importRows.value) {
        const s = customers.find((s) => s.code === r["客户编码"]),
          p = products.find((p) => p.code === r["产品编码"]);
        if (!s || !p) throw Error("客户编码或产品编码不存在");
        let o = result.find((o) => o.name === r["销售订单名称"]);
        if (!o) {
          o = {
            id: crypto.randomUUID(),
            code: "",
            name: r["销售订单名称"]!,
            customerId: s.id,
            signed: r["签订日期"]!,
            delivery: r["交付日期"]!,
            warehouse: r["出库仓库"]!,
            owner: "张睿",
            department: "销售部",
            address: "",
            discount: Number(r["优惠金额"]),
            lines: [],
            arrivals: [],
            payments: [],
            attachments: [],
            term: "月结30天",
            status: "待审批",
            reason: "",
            approver: "",
            approvedAt: "",
          };
          result.push(o);
        }
        if (
          o.customerId !== s.id ||
          o.warehouse !== r["出库仓库"] ||
          o.signed !== r["签订日期"] ||
          o.delivery !== r["交付日期"] ||
          o.discount !== Number(r["优惠金额"])
        )
          throw Error("同名订单的基础信息必须一致");
        o.lines.push({
          id: crypto.randomUUID(),
          productId: p.id,
          quantity: Number(r["销售数量"]),
          price: r["含税单价"] === "" ? p.price : Number(r["含税单价"]),
          tax: p.tax,
        });
      }
      store.importOrders(result);
    } else {
      const result: SalesShipment[] = [];
      for (const r of importRows.value) {
        const o = store.state.orders.find((o) => o.code === r["销售订单编号"]),
          p = products.find((p) => p.code === r["产品编码"]),
          line = o?.lines.find((l) => l.productId === p?.id);
        if (!o || !line) throw Error("订单编号或对应产品不存在");
        let shipment = result.find((t) => t.orderId === o.id);
        if (!shipment) {
          shipment = {
            id: crypto.randomUUID(),
            code: "",
            orderId: o.id,
            warehouse: r["出库仓库"]!,
            lines: [],
            tag: (r["产品出库标签"] || "计划出库") as SalesShipment["tag"],
            customerConfirmed: false,
            customerReceivedAt: "",
            confirmed: false,
            shippedAt: "",
            keeper: "陈晨",
            department: "仓储部",
            photos: [],
          };
          result.push(shipment);
        }
        if (
          shipment.warehouse !== r["出库仓库"] ||
          shipment.tag !== (r["产品出库标签"] || "计划出库")
        )
          throw Error("同一订单出库仓库和标签必须一致");
        shipment.lines.push({
          lineId: line.id,
          arrived: Number(r["出库数量"]),
          location: r["出库仓位"]!,
        });
      }
      store.importShipments(result);
    }
    importOpen.value = false;
    ElMessage.success("导入成功");
  } catch (e) {
    importError.value = (e as Error).message;
  }
}
</script>

<template>
  <section
    class="purchase-page"
    :class="{ 'purchase-expanded': expanded, 'sales-order-page': isOrder }"
  >
    <nav class="purchase-view-tabs" aria-label="销售数据视图">
      <button
        v-for="name in tabs"
        :key="name"
        :class="{ active: tab === name }"
        :aria-selected="tab === name"
        @click="tab = name"
      >
        <el-icon><Document /></el-icon>{{ name }}
      </button>
    </nav>
    <div class="purchase-toolbar">
      <div class="purchase-toolbar-actions">
        <el-button type="primary" :icon="Plus" @click="open()">添加</el-button
        ><el-button
          text
          :icon="Upload"
          @click="
            () => {
              importOpen = true;
              importRows = [];
              importError = '';
              fileName = '';
            }
          "
          >导入</el-button
        ><el-button text :icon="Download" @click="exportData">导出</el-button
        ><el-button text :icon="Delete" @click="remove">删除</el-button
        ><el-button text :icon="Clock" @click="logsOpen = true"
          >操作记录</el-button
        >
      </div>
      <div class="purchase-toolbar-search">
        <el-input
          v-model="keyword"
          :prefix-icon="Search"
          placeholder="搜索数据"
          aria-label="搜索销售数据"
          clearable
        /><el-button
          :icon="Filter"
          :type="filterOpen ? 'primary' : ''"
          plain
          @click="filterOpen = !filterOpen"
          >筛选</el-button
        >
        <el-popover trigger="click" width="220"
          ><template #reference
            ><el-button
              :icon="Setting"
              aria-label="显示字段"
              title="显示字段" /></template
          ><strong>显示字段</strong>
          <div class="purchase-column-options">
            <el-checkbox
              v-for="c in columns"
              :key="c.key"
              :model-value="!hidden.includes(c.key)"
              :disabled="shownColumns.length === 1 && !hidden.includes(c.key)"
              @change="
                () => {
                  hidden = hidden.includes(c.key)
                    ? hidden.filter((k) => k !== c.key)
                    : [...hidden, c.key];
                }
              "
              >{{ c.label }}</el-checkbox
            >
          </div>
          <el-button text type="primary" @click="hidden = []"
            >恢复默认</el-button
          ></el-popover
        >
        <el-popover trigger="click" width="260"
          ><template #reference
            ><el-button :icon="Sort" aria-label="排序" title="排序"
          /></template>
          <p>排序字段</p>
          <el-select v-model="sortKey" clearable placeholder="默认顺序"
            ><el-option
              v-for="c in columns"
              :key="c.key"
              :value="c.key"
              :label="c.label" /></el-select
          ><el-radio-group v-model="sortDirection"
            ><el-radio value="asc">升序</el-radio
            ><el-radio value="desc">降序</el-radio></el-radio-group
          ><el-button text @click="sortKey = ''">重置</el-button></el-popover
        >
        <el-button
          :icon="Operation"
          :type="grouped ? 'primary' : ''"
          plain
          aria-label="切换分组"
          title="按客户分组"
          @click="grouped = !grouped"
        /><el-button
          :icon="Refresh"
          text
          aria-label="刷新销售数据"
          @click="refresh"
        /><el-button
          :icon="FullScreen"
          text
          aria-label="切换表格全屏"
          @click="expanded = !expanded"
        />
      </div>
    </div>
    <div v-if="filterOpen" class="purchase-filters">
      <el-select v-model="filterKey" aria-label="筛选字段"
        ><el-option
          v-for="c in columns"
          :key="c.key"
          :value="c.key"
          :label="c.label" /></el-select
      ><el-select v-model="filterOperator" aria-label="筛选方式"
        ><el-option
          v-for="n in ['包含', '不包含', '等于', '不等于', '为空', '不为空']"
          :key="n"
          :value="n" /></el-select
      ><el-input
        v-if="!['为空', '不为空'].includes(filterOperator)"
        v-model="filterValue"
        placeholder="请输入筛选内容"
        clearable
      /><el-button
        @click="
          () => {
            filterValue = '';
            filterOperator = '包含';
            filterOpen = false;
          }
        "
        >重置</el-button
      >
    </div>
    <div
      class="purchase-table-scroll"
      :class="{ 'purchase-comfortable': rowHeight === 'comfortable' }"
    >
      <table class="purchase-table sales-management-table">
        <thead>
          <tr>
            <th
              class="purchase-selection"
              :rowspan="productColumns.length ? 2 : 1"
            >
              <el-checkbox
                :model-value="allSelected"
                :indeterminate="selected.length > 0 && !allSelected"
                aria-label="选择全部销售数据"
                @change="selectRows(filtered, !!$event)"
              />
            </th>
            <th
              v-for="c in leadingColumns"
              :key="c.key"
              :rowspan="productColumns.length ? 2 : 1"
              :class="{ 'purchase-name-col': c.key === 'name' }"
              :style="{ minWidth: columnWidth(c.key) + 'px' }"
            >
              {{ c.label }}
            </th>
            <th
              v-if="productColumns.length"
              :colspan="productColumns.length"
              class="sales-product-group"
            >
              {{ isOrder ? "产品明细" : "出库产品明细" }}
            </th>
            <th
              v-for="c in trailingColumns"
              :key="c.key"
              :rowspan="productColumns.length ? 2 : 1"
              :style="{ minWidth: columnWidth(c.key) + 'px' }"
            >
              {{ c.label }}
            </th>
            <th
              v-if="isOrder"
              class="purchase-fixed-actions"
              :rowspan="productColumns.length ? 2 : 1"
            >
              操作
            </th>
          </tr>
          <tr v-if="productColumns.length">
            <th
              v-for="c in productColumns"
              :key="c.key"
              :style="{ minWidth: columnWidth(c.key) + 'px' }"
            >
              {{ c.label }}
            </th>
          </tr>
        </thead>
        <tbody>
          <template v-for="group in groups" :key="group.name">
            <tr v-if="grouped" class="purchase-group">
              <td>
                <el-checkbox
                  :model-value="
                    group.rows.every((r) => selected.includes(r.id))
                  "
                  :indeterminate="
                    group.rows.some((r) => selected.includes(r.id)) &&
                    !group.rows.every((r) => selected.includes(r.id))
                  "
                  :aria-label="'选择分组 ' + group.name"
                  @change="selectRows(group.rows, !!$event)"
                />
              </td>
              <td :colspan="shownColumns.length + (isOrder ? 1 : 0)">
                <button
                  :aria-expanded="!collapsed.includes(group.name)"
                  @click="toggleGroup(group.name)"
                >
                  <span>{{ collapsed.includes(group.name) ? "▸" : "▾" }}</span
                  >{{ group.name }}<small>{{ group.rows.length }}</small>
                </button>
              </td>
            </tr>
            <template v-if="!grouped || !collapsed.includes(group.name)"
              ><tr
                v-for="row in group.rows"
                :key="row.id"
                :class="{ selected: selected.includes(row.id) }"
                @dblclick="open(row, true)"
              >
                <td class="purchase-selection">
                  <el-checkbox
                    :model-value="selected.includes(row.id)"
                    :aria-label="'选择 ' + row.values.code"
                    @change="selectRows([row], !!$event)"
                  />
                </td>
                <td
                  v-for="c in [
                    ...leadingColumns,
                    ...productColumns,
                    ...trailingColumns,
                  ]"
                  :key="c.key"
                  :title="String(display(row, c.key) || '')"
                  :class="{
                    'sales-product-cell': productKeys.includes(c.key),
                    'sales-code-cell': ['code', 'orderCode'].includes(c.key),
                    'purchase-money': [
                      'gross',
                      'discount',
                      'total',
                      'net',
                      'quantity',
                    ].includes(c.key),
                  }"
                >
                  <button
                    v-if="['name', 'code', 'orderCode'].includes(c.key)"
                    class="purchase-record-link"
                    @click="open(row, true)"
                  >
                    {{ display(row, c.key) }}</button
                  ><el-tag
                    v-else-if="c.key === 'status'"
                    size="small"
                    :type="
                      ['审批通过', '已出库'].includes(String(row.values.status))
                        ? 'success'
                        : row.values.status === '不通过'
                          ? 'danger'
                          : 'warning'
                    "
                    >{{ row.values.status }}</el-tag
                  >
                  <div
                    v-else-if="productKeys.includes(c.key)"
                    class="sales-product-values"
                  >
                    <div
                      v-for="(value, index) in String(
                        row.values[c.key] ?? '',
                      ).split('\n')"
                      :key="index"
                    >
                      {{ value || "—" }}
                    </div>
                  </div>
                  <span v-else>{{ display(row, c.key) ?? "—" }}</span>
                </td>
                <td v-if="isOrder" class="purchase-fixed-actions">
                  <el-button
                    class="purchase-pay"
                    size="small"
                    plain
                    :disabled="row.order?.status !== '审批通过'"
                    @click="business(row.order!, '收款')"
                    >销售收款</el-button
                  ><el-button
                    class="purchase-ship"
                    size="small"
                    plain
                    :disabled="
                      row.order?.status !== '审批通过' ||
                      !row.order.lines.some(
                        (l) => remainingQuantity(store.state, row.id, l.id) > 0,
                      )
                    "
                    @click="ship(row.order!)"
                    >发货</el-button
                  ><el-button
                    class="purchase-return"
                    size="small"
                    plain
                    :disabled="
                      !row.order?.lines.some(
                        (l) =>
                          returnableQuantity(store.state, row.id, l.id) > 0,
                      )
                    "
                    @click="business(row.order!, '退货')"
                    >销售退货</el-button
                  >
                </td>
              </tr></template
            >
          </template>
          <tr v-if="!filtered.length">
            <td :colspan="shownColumns.length + (isOrder ? 2 : 1)">
              <el-empty description="暂无符合条件的数据" />
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <footer class="purchase-table-footer">
      <el-select v-model="rowHeight" aria-label="行高" size="small"
        ><el-option label="紧凑行高" value="normal" /><el-option
          label="舒适行高"
          value="comfortable" /></el-select
      ><span>共 {{ filtered.length }} 条</span
      ><span v-if="selected.length">已选择 {{ selected.length }} 条</span
      ><el-button v-if="grouped" text @click="collapsed = []"
        >⌄ 展开全部</el-button
      ><el-button
        v-if="grouped"
        text
        @click="collapsed = groups.map((g) => g.name)"
        >⌃ 收起全部</el-button
      ><span class="purchase-footer-hint">{{
        isOrder ? "单击订单编号查看详情" : "单击名称查看详情"
      }}</span>
    </footer>
  </section>
  <SalesRecordDialog
    v-if="editor"
    :key="editorVersion"
    :kind="editorKind"
    :order="editingOrder"
    :shipment="editingShipment"
    :related-order="relatedOrder"
    :dispatch="!!relatedOrder"
    :readonly="readonly"
    @close="editor = false"
    @saved="saved"
    @ship="ship"
  />
  <el-drawer
    v-model="logsOpen"
    title="操作记录"
    size="min(600px,95vw)"
    class="purchase-dialog"
    ><el-empty
      v-if="!store.state.logs.length"
      description="暂无操作记录"
    /><el-timeline v-else
      ><el-timeline-item
        v-for="log in store.state.logs"
        :key="log.id"
        :timestamp="log.time"
        ><strong>{{ log.action }}</strong>
        <p>{{ log.name }}</p></el-timeline-item
      ></el-timeline
    ></el-drawer
  >
  <el-dialog
    :model-value="!!operation"
    :title="'销售' + operation"
    width="min(550px,94vw)"
    class="purchase-dialog"
    @close="operation = ''"
  >
    <p>{{ activeOrder?.name }}</p>
    <el-form v-if="operation === '收款' && payment" label-position="top"
      ><p>
        剩余应收金额：<strong>¥ {{ money(unpaid) }}</strong>
      </p>
      <el-form-item label="收款金额/元" required
        ><el-input-number
          v-model="payment.amount"
          :min="0"
          :max="Math.max(0, unpaid)"
          :precision="2" /></el-form-item
      ><el-form-item label="收款日期" required
        ><el-date-picker
          v-model="payment.date"
          value-format="YYYY-MM-DD" /></el-form-item
      ><el-form-item label="收款方式"
        ><el-select v-model="payment.method"
          ><el-option
            v-for="n in ['银行转账', '现金', '承兑汇票']"
            :key="n"
            :value="n" /></el-select></el-form-item
      ><el-form-item label="备注"
        ><el-input v-model="payment.note" type="textarea" /></el-form-item
    ></el-form>
    <el-form v-else-if="returned && activeOrder" label-position="top"
      ><el-form-item label="退货产品" required
        ><el-select v-model="returned.lineId"
          ><el-option
            v-for="line in activeOrder.lines"
            :key="line.id"
            :value="line.id"
            :label="products.find((p) => p.id === line.productId)?.name"
            :disabled="
              returnableQuantity(store.state, activeOrder.id, line.id) <= 0
            " /></el-select
      ></el-form-item>
      <p>
        可退数量：{{
          returnableQuantity(store.state, activeOrder.id, returned.lineId)
        }}
      </p>
      <el-form-item label="退货数量" required
        ><el-input-number
          v-model="returned.quantity"
          :min="1"
          :max="
            Math.max(
              1,
              returnableQuantity(store.state, activeOrder.id, returned.lineId),
            )
          "
          :precision="0" /></el-form-item
      ><el-form-item label="退货日期"
        ><el-date-picker
          v-model="returned.date"
          value-format="YYYY-MM-DD" /></el-form-item
      ><el-form-item label="退货原因" required
        ><el-input v-model="returned.reason" type="textarea" /></el-form-item
    ></el-form>
    <template #footer
      ><el-button @click="operation = ''">取消</el-button
      ><el-button type="primary" @click="submitBusiness"
        >提交</el-button
      ></template
    >
  </el-dialog>
  <el-dialog
    v-model="importOpen"
    :title="'导入' + (isOrder ? '销售订单' : '销售出库')"
    width="min(850px,94vw)"
    class="purchase-dialog"
    destroy-on-close
  >
    <p>
      使用 UTF-8 CSV 模板，每行一条产品明细。{{
        isOrder
          ? "同名订单会合并，导入后进入待审批。"
          : "同一订单会合并，导入后进入待出库。"
      }}
    </p>
    <el-button :icon="Download" @click="downloadTemplate"
      >下载导入模板</el-button
    ><label class="purchase-import-file"
      >选择 CSV 文件<input type="file" accept=".csv" @change="readImport"
    /></label>
    <p>
      {{ fileName }}
      <span v-if="importRows.length">· 共 {{ importRows.length }} 行</span>
    </p>
    <el-alert
      v-if="importError"
      :title="importError"
      type="error"
      :closable="false"
    /><el-table
      v-if="importRows.length"
      :data="importRows.slice(0, 10)"
      max-height="300"
      border
      ><el-table-column
        v-for="h in importHeaders"
        :key="h"
        :prop="h"
        :label="h"
        min-width="130"
    /></el-table>
    <p v-if="importRows.length > 10">仅预览前 10 行，提交时导入全部数据。</p>
    <template #footer
      ><el-button @click="importOpen = false">取消</el-button
      ><el-button
        type="primary"
        :disabled="!importRows.length"
        @click="confirmImport"
        >确认导入</el-button
      ></template
    >
  </el-dialog>
</template>

<style scoped>
.sales-product-group {
  text-align: center;
}
.sales-management-table td {
  vertical-align: top;
}
.sales-management-table thead tr:first-child th {
  height: 38px;
}
.sales-management-table thead tr:nth-child(2) th {
  top: 38px;
}
.sales-management-table .sales-product-cell {
  padding: 0;
}
.sales-product-values > div {
  min-height: 35px;
  padding: 8px 10px;
  border-bottom: 1px solid #e3e8f0;
  box-sizing: border-box;
}
.sales-product-values > div:last-child {
  border-bottom: 0;
}
.purchase-comfortable .sales-product-values > div {
  min-height: 45px;
}
.sales-code-cell .purchase-record-link {
  white-space: nowrap;
}
.sales-management-table .purchase-fixed-actions {
  position: sticky;
  right: 0;
  box-shadow: -3px 0 5px #20334d0a;
}
.sales-management-table th.purchase-fixed-actions {
  z-index: 4;
}
@media (max-width: 1050px) {
  .sales-management-table .purchase-fixed-actions {
    position: static;
    box-shadow: none;
  }
}
.sales-order-page .sales-management-table td:not(.sales-product-cell),
.sales-order-page .sales-management-table th {
  text-align: center;
  vertical-align: middle;
}
.sales-order-page .sales-management-table td.sales-product-cell,
.sales-order-page .sales-management-table thead tr:nth-child(2) th {
  text-align: left;
}
.sales-order-page .sales-management-table .purchase-record-link {
  text-align: center;
}
.sales-order-page .sales-management-table .purchase-group td {
  text-align: left;
}
</style>
