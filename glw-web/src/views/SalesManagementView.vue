<script setup lang="ts">
import { useListPagination } from "../composables/useListPagination";
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
import { emptySalesOrderFilters, salesFulfillmentStatus, salesOrderMonth, salesFilterCount, matchesSalesOrderFilters, type FulfillmentStatus } from "../domain/salesOrderFilters";
import SalesRecordDialog from "../components/sales/SalesRecordDialog.vue";
import { useSalesStore } from "../stores/sales";
import {
  customers,
  orderCustomerName,
  salesLineProduct,
  products,
  money,
  today,
  orderTotals,
  shipmentTotals,
  shipmentOrders,
  returnableQuantity,
  remainingQuantity,
  pendingShipmentQuantity,
  chineseMoney,
  type SalesKind,
  type SalesOrder,
  type SalesShipment,
  type SalesPayment,
  type SalesReturn,
} from "../domain/sales";
import { encodeCsv, parseCsv } from "../domain/crm";
import { downloadExport } from '../services/exportApi';
import "../assets/purchase.css";
const props = defineProps<{ kind: SalesKind }>(),
  store = useSalesStore(),
  isOrder = props.kind === "orders";
const tabs = isOrder
  ? ["全部订单", "待发货订单", "部分发货订单", "已完成订单", "剩余未交取消订单"]
  : ["全部出库", "已出库", "待出库"];
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
const groupMode = ref<'month' | 'customer' | 'none'>('month');
const orderFilterOpen = ref(false);
const orderFilters = ref(emptySalesOrderFilters());
const draftFilters = ref(emptySalesOrderFilters());
const activeFilterCount = computed(() => salesFilterCount(orderFilters.value));
const orderTextFilters = [
  {key:'code',label:'订单编号'},
  {key:'purchaseNumber',label:'客户采购单号'}, {key:'term',label:'账期'},
] as const;
const productTextFilters = [{key:'brands',label:'品牌'}, {key:'productNames',label:'型号'}, {key:'models',label:'封装'}] as const;
const filterCustomers = computed(() => [...new Set(store.state.orders.map(orderCustomerName).filter(Boolean))]);
function openFilters() {
  if (!isOrder) { filterOpen.value = !filterOpen.value; return; }
  draftFilters.value = structuredClone(JSON.parse(JSON.stringify(orderFilters.value)));
  orderFilterOpen.value = true;
}
function applyOrderFilters() {
  for (const range of Object.values(draftFilters.value.numbers)) {
    if (range.some(n => n != null && (!Number.isFinite(n) || n < 0))) return void ElMessage.warning('请输入有效的非负数值');
    if (range[0] != null && range[1] != null && range[0] > range[1]) return void ElMessage.warning('范围最小值不能大于最大值');
  }
  orderFilters.value = JSON.parse(JSON.stringify(draftFilters.value));
  orderFilterOpen.value = false;
  collapsed.value = [];
}
function changeGrouping(mode: 'month' | 'customer' | 'none') {
  groupMode.value = mode;
  grouped.value = mode !== 'none';
  collapsed.value = [];
}
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
  ["purchaseNumber", "客户采购单号"],
  ["customer", "客户名称"],
  ["signed", "订单日期"],
  ["brands", "品牌"],
  ["productNames", "型号"],
  ["models", "封装"],
  ["prices", "单价"],
  ["ordered", "销售数量"],
  ["pendingShipment", "待发数量"],
  ["amounts", "金额"],
  ["total", "订单金额"],
  ["term", "账期"],
  ["delivery", "交付日期"],
  ["owner", "销售负责人"],
];
const orderWidths: Record<string, number> = {
  code: 140,
  purchaseNumber: 140,
  signed: 95,
  customer: 135,
  brands: 65,
  productNames: 85,
  models: 160,
  prices: 65,
  ordered: 85,
  pendingShipment: 85,
  amounts: 90,
  total: 100,
  term: 90,
  delivery: 95,
  owner: 95,
};
function columnWidth(key: string) {
  if (['code', 'orderCode', 'purchaseNumber'].includes(key)) return 140;
  if (key === 'brands') return 65;
  if (key === 'productNames') return 85;
  if (key === 'models') return 160;
  return isOrder ? orderWidths[key] || 100 : key === "name" ? 230 : 130;
}
const shipmentColumns = [
  ["code", "出库单号"],
  ["purchaseNumber", "客户采购单号"],
  ["customer", "客户名称"],
  ["shippedAt", "出库日期"],
  ["brands", "品牌"],
  ["productNames", "型号"],
  ["models", "封装"],
  ["ordered", "销售数量"],
  ["pending", "出库数量"],
  ["status", "出库状态"],
];
function productSummary(
  order: SalesOrder | undefined,
  shipment?: SalesShipment,
  orders: SalesOrder[] = [],
) {
  const sources = shipment
    ? shipmentOrders(shipment, orders.length ? orders : order ? [order] : [])
    : order
      ? [order]
      : [];
  const lines = shipment
    ? sources.flatMap((source) =>
        source.lines.filter((l) => shipment.lines.some((s) => s.lineId === l.id)),
      )
    : order?.lines || [];
  const orderFor = (lineId: string) =>
    sources.find((source) => source.lines.some((l) => l.id === lineId));
  const values = (key: "name" | "code" | "brand" | "model" | "unit") =>
    lines
      .map((l) => {
        if (key === 'brand' || key === 'name' || key === 'model') return salesLineProduct(l)[key] || '—';
        return products.find((p) => p.id === l.productId)?.[key] || '—';
      })
      .join("\n");
  return {
    productNames: values("name"),
    productCodes: values("code"),
    brands: values("brand"),
    models: values("model"),
    prices: lines.map((l) => l.price.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 4 })).join("\n"),
    amounts: lines.map((l) => money(l.amount ?? l.price * l.quantity)).join("\n"),
    units: values("unit"),
    ordered: lines.map((l) => l.quantity).join("\n"),
    pendingShipment: lines.map(l => {
      const source = orderFor(l.id) ?? order;
      return source ? pendingShipmentQuantity(source, l, store.state.shipments) : 0;
    }).join('\n'),
    pending: lines
      .map((l) => shipment ? shipment.lines.filter(s => s.lineId === l.id).reduce((sum, s) => sum + s.arrived, 0) : remainingQuantity(store.state, orderFor(l.id)?.id ?? order!.id, l.id))
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
  ? ["brands", "productNames", "models", "prices", "ordered", "pendingShipment", "amounts"]
  : ["brands", "productNames", "models", "ordered", "pending"];
const productColumns = computed(() =>
  shownColumns.value.filter((c) => productKeys.includes(c.key)),
);
const leadingColumns = computed(() =>
  shownColumns.value.filter((c) =>
    isOrder
      ? ["code", "purchaseNumber", "signed", "customer"].includes(c.key)
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
            customerShortName: o.customerShortName || orderCustomerName(o) || '—',
            term: o.term,
            priceLevel: o.priceLevel || "标准售价",
            signed: o.signed,
            delivery: o.delivery,
            contact:
              customers.find((c) => c.id === o.customerId)?.contact || "",
            phone: customers.find((c) => c.id === o.customerId)?.phone || "",
            name: o.name,
            code: o.code,
            purchaseNumber: o.purchaseNumber || "—",
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
            fulfillmentStatus: salesFulfillmentStatus(o, store.state.shipments),
          },
        };
      })
    : store.state.shipments.map((r) => {
        const linked = shipmentOrders(r, store.state.orders),
          o = linked[0] ?? store.state.orders.find((o) => o.id === r.orderId),
          s = customers.find((s) => s.id === o?.customerId),
          t = shipmentTotals(r, o, store.state.orders);
        return {
          id: r.id,
          shipment: r,
          order: o,
          group: orderCustomerName(o) || "未填写",
          values: {
            ...productSummary(o, r, store.state.orders),
            tag: r.tag,
            warehouse: r.warehouse,
            name: linked.map((item) => item.name).join("、") || "—",
            orderCode: linked.map((item) => item.code).join("、") || o?.code || "—",
            customer: r.customerName || orderCustomerName(o) || "—",
            customerShortName: r.customerShortName || r.customerName || o?.customerShortName || orderCustomerName(o) || '—',
            customerCode: s?.code || "—",
            contact: s?.contact || "—",
            phone: s?.phone || "—",
            owner: o?.owner || "—",
            department: o?.department || "—",
            code: r.code,
            purchaseNumber: linked.map((item) => item.purchaseNumber).filter(Boolean).join("、") || o?.purchaseNumber || "—",
            shippedAt: r.shippedAt || "—",
            quantity: t.quantity,
            total: t.total,
            status: r.confirmed ? "已出库" : "待出库",
          },
        };
      }),
);
const filtered = computed(() => {
  const rows = records.value.filter((r) => {
    if (isOrder && tab.value !== tabs[0] && r.values.fulfillmentStatus !== tab.value.replace(/订单$/, '')) return false;
    if (!isOrder && tab.value === "已出库" && !r.shipment?.confirmed)
      return false;
    if (!isOrder && tab.value === "待出库" && r.shipment?.confirmed)
      return false;
    if (
      keyword.value.trim() &&
      ![r.group, ...Object.values(r.values)]
        .join(" ")
        .toLowerCase()
        .includes(keyword.value.trim().toLowerCase())
    )
      return false;
    if (isOrder && r.order) return matchesSalesOrderFilters(r.order, products, orderCustomerName(r.order), Number(r.values.total), r.values.fulfillmentStatus as FulfillmentStatus, orderFilters.value, store.state.payments.filter(p => p.orderId === r.order!.id).reduce((sum,p) => sum + p.amount,0));
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
const allGroups = computed(() => {
  if (!grouped.value) return [{name: '', rows: filtered.value}];
  const missingDate = isOrder ? '未填写订单日期' : '未填写出库日期';
  const key = (row: TableRow) => {
    if (groupMode.value !== 'month') return row.group;
    const month = salesOrderMonth((isOrder ? row.order?.signed : row.shipment?.shippedAt) || '');
    return month === '未填写订单日期' ? missingDate : month;
  };
  const names = [...new Set(filtered.value.map(key))];
  if (groupMode.value === 'month') names.sort((a,b) => a === b ? 0 : a === missingDate ? 1 : b === missingDate ? -1 : b.localeCompare(a));
  return names.map(name => ({name, rows: filtered.value.filter(row => key(row) === name)}));
});
const paginationSource = computed(() => allGroups.value.flatMap(group => group.rows));
const { page, pageSize, pageRows } = useListPagination(paginationSource);
watch([page, pageSize], () => { selected.value = []; collapsed.value = []; });
const groups = computed(() => {
  const visible = new Set(pageRows.value.map(row => row.id));
  return allGroups.value.map(group => ({ ...group, rows: group.rows.filter(row => visible.has(row.id)) })).filter(group => group.rows.length);
});
const allSelected = computed(
  () =>
    !!pageRows.value.length &&
    pageRows.value.every((r) => selected.value.includes(r.id)),
);
watch(
  [tab, keyword, filterOpen, filterKey, filterValue, filterOperator, orderFilters, groupMode],
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
async function refresh(showSuccess = true) {
  await store.load();
  selected.value = [];
  if (store.loadError) ElMessage.warning(store.loadError);
  else if (showSuccess) ElMessage.success("数据已刷新");
}
onMounted(() => {
  void refresh(false);
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
const exporting = ref(false);
async function exportData() {
  if (exporting.value) return;
  exporting.value = true;
  try {
    await downloadExport(isOrder ? 'sales-orders' : 'outbound-orders', {
      keyword: keyword.value, tab: tab.value, sales: orderFilters.value,
      selectedIds: selected.value.map(Number), columns: shownColumns.value.map(c => c.key),
      sortKey: sortKey.value, sortDirection: sortDirection.value,
      filterOpen: filterOpen.value, filterKey: filterKey.value,
      filterOperator: filterOperator.value, filterValue: filterValue.value,
    }, `${isOrder ? '销售订单' : '销售出库'}-${today()}`);
    ElMessage.success('导出成功');
  } catch (e) { ElMessage.error((e as Error).message); }
  finally { exporting.value = false; }
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
        ><el-button text :icon="Download" :loading="exporting" :disabled="store.loading || !!store.loadError" @click="exportData">导出</el-button
        ><el-button text :icon="Delete" @click="remove">删除</el-button
        >
      </div>
      <div class="purchase-toolbar-search">
        <el-button
          :icon="Filter"
          :type="(isOrder ? activeFilterCount > 0 : filterOpen) ? 'primary' : ''"
          plain
          @click="openFilters"
          >筛选{{ isOrder && activeFilterCount ? `（${activeFilterCount}）` : "" }}</el-button
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

        <el-dropdown trigger="click" @command="changeGrouping">
          <el-button :icon="Operation" :type="grouped ? 'primary' : ''" plain :aria-label="isOrder ? '订单分组方式' : '销售出库分组方式'">{{ groupMode === 'month' ? '按月份分组' : groupMode === 'customer' ? '按客户分组' : '不分组' }}</el-button>
          <template #dropdown><el-dropdown-menu>
            <el-dropdown-item command="month">{{ isOrder ? '按订单日期月份分组' : '按出库日期月份分组' }}</el-dropdown-item>
            <el-dropdown-item command="customer">按客户分组</el-dropdown-item>
            <el-dropdown-item command="none">不分组</el-dropdown-item>
          </el-dropdown-menu></template>
        </el-dropdown>
        <el-button
          :icon="Refresh"
          text
          aria-label="刷新销售数据"
          :loading="store.loading"
          @click="refresh()"
        /><el-button
          :icon="FullScreen"
          text
          aria-label="切换表格全屏"
          @click="expanded = !expanded"
        />
      </div>
    </div>
    <div v-if="isOrder && activeFilterCount" class="sales-active-filters">
      <span>已应用 {{ activeFilterCount }} 项筛选条件</span><el-button text type="primary" @click="orderFilters = emptySalesOrderFilters(); collapsed = []">清空筛选</el-button>
    </div>
    <div v-if="!isOrder && filterOpen" class="purchase-filters">
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
                @change="selectRows(pageRows, !!$event)"
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
              {{ isOrder ? "销售产品明细" : "出库产品明细" }}
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
                    'sales-packaging-cell': c.key === 'models',
                    'shipment-centered-cell': !isOrder && ['code', 'purchaseNumber', 'customer', 'shippedAt', 'status'].includes(c.key),
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
                      :title="value || '—'"
                    >
                      {{ value || "—" }}
                    </div>
                  </div>
                  <span v-else-if="c.key === 'customer'">{{ row.values.customerShortName }}</span>
                  <span v-else>{{ display(row, c.key) ?? "—" }}</span>
                </td>
                <td v-if="isOrder" class="purchase-fixed-actions">
                  <el-button
                    class="purchase-pay"
                    size="small"
                    plain
                    :disabled="row.order?.status !== '审批通过'"
                    @click="business(row.order!, '收款')"
                    >收款</el-button
                  ><el-button
                    class="purchase-ship"
                    size="small"
                    plain
                    :disabled="
                      !row.order?.lines.some(
                        (l) => pendingShipmentQuantity(row.order!, l, store.state.shipments) > 0,
                      )
                    "
                    @click="ship(row.order!)"
                    >发货</el-button
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
      <span v-if="selected.length">已选择 {{ selected.length }} 条</span
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
    <el-pagination class="list-pagination" v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10,20,50,100]" :total="filtered.length" layout="total, sizes, prev, pager, next, jumper" /></footer>
  </section>
  <el-dialog v-if="isOrder" v-model="orderFilterOpen" title="销售订单筛选" width="min(900px,94vw)" top="5vh" append-to-body class="sales-order-filter-dialog">
    <p class="sales-filter-tip">填写的条件同时生效；留空表示不限。产品条件匹配同一条产品明细。</p>
    <el-form label-position="top" @submit.prevent="applyOrderFilters">
      <h3>订单信息</h3>
      <div class="sales-filter-grid">
        <el-form-item v-for="field in orderTextFilters" :key="field.key" :label="field.label"><el-input v-model="draftFilters.text[field.key]" :aria-label="'筛选' + field.label" placeholder="包含关键词" clearable /></el-form-item>
        <el-form-item label="客户名称"><el-select v-model="draftFilters.text.customer" filterable clearable placeholder="全部客户" aria-label="筛选客户名称"><el-option v-for="name in filterCustomers" :key="name" :label="name" :value="name" /></el-select></el-form-item>
        <el-form-item label="收款状态"><el-select v-model="draftFilters.paymentStatus" clearable placeholder="不限" aria-label="筛选收款状态"><el-option v-for="status in ['未收款','部分收款','已收款']" :key="status" :label="status" :value="status" /></el-select></el-form-item>
        <el-form-item label="发货状态"><el-select v-model="draftFilters.status" aria-label="筛选发货状态" placeholder="不限" clearable><el-option v-for="status in ['待发货','部分发货','已完成','剩余未交取消']" :key="status" :value="status" /></el-select></el-form-item>
        <el-form-item label="订单日期"><el-date-picker v-model="draftFilters.dates.signed" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" aria-label="筛选订单日期" /></el-form-item>
      </div>
      <h3>产品信息</h3>
      <div class="sales-filter-grid">
        <el-form-item v-for="field in productTextFilters" :key="field.key" :label="field.label"><el-input v-model="draftFilters.text[field.key]" :aria-label="'筛选' + field.label" placeholder="包含关键词" clearable /></el-form-item>
      </div>
    </el-form>
    <template #footer><el-button @click="draftFilters = emptySalesOrderFilters()">重置条件</el-button><el-button @click="orderFilterOpen = false">取消</el-button><el-button type="primary" @click="applyOrderFilters">应用筛选</el-button></template>
  </el-dialog>
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
.sales-management-table td.shipment-centered-cell {
  vertical-align: middle;
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
.sales-management-table .sales-code-cell { width: 1%; padding-left: 8px; padding-right: 8px; }
.sales-management-table .sales-packaging-cell { width: 160px; max-width: 160px; }
.sales-packaging-cell .sales-product-values { width: 160px; }
.sales-packaging-cell .sales-product-values > div { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.sales-management-table .purchase-fixed-actions {
  min-width: 116px;
  width: 116px;
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
.sales-management-table tbody > tr:nth-child(even of :not(.purchase-group)) > td {
  background: #eef2f6;
}
.sales-management-table tbody > tr:nth-child(even of :not(.purchase-group)):hover > td {
  background: #f4fbfa;
}
.sales-management-table tbody tr.selected td {
  background: #eaf8f4;
}
</style>

<style>
.sales-filter-grid { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 0 18px; }
.sales-order-filter-dialog .el-form-item { margin-bottom: 14px; }
.sales-order-filter-dialog h3 { font-size: 14px; margin: 12px 0; }
.sales-filter-tip { color: #8491a4; font-size: 13px; margin: 0 0 16px; }
.sales-filter-range { display: flex; align-items: center; gap: 8px; width: 100%; }
.sales-filter-range .el-input-number { flex: 1; width: 0; min-width: 0; }
.sales-filter-grid .el-date-editor { width: 100%; min-width: 0; }
.sales-active-filters { display: flex; align-items: center; gap: 12px; padding: 0 16px 8px; font-size: 13px; color: #65738a; }
@media(max-width: 700px) { .sales-filter-grid { grid-template-columns: 1fr; } }
</style>
