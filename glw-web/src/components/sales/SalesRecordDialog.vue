<script setup lang="ts">
import { computed, ref, watch, onMounted, onBeforeUnmount } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Delete, Document, FullScreen } from "@element-plus/icons-vue";
import DeliveryNoteActions from "./DeliveryNoteActions.vue";
import ShipmentShippingForm from "./ShipmentShippingForm.vue";
import ShipmentBatchPicker from "./ShipmentBatchPicker.vue";
import AttachmentTiles from "../common/AttachmentTiles.vue";
import { validateBatchSelection, type ShipmentBatch } from "../../domain/shipmentBatches";
import { useStickyTableScroll } from "../../composables/useStickyTableScroll";
import { useSalesStore } from "../../stores/sales";
import { fetchSalesProducts, fetchProductCostBatches } from "../../services/productApi";
import { fetchCustomerOptions, type CustomerOption } from "../../services/customerApi";
import { fetchCustomerProductCodes } from "../../services/customerProductCodeApi";
import { financeApi, financeStatementJumpKey, type OpenItem } from "../../services/financeApi";
import {
  deleteOrderAttachment,
  fetchOrderAttachments,
  uploadOrderAttachment,
  type AttachmentVO,
} from "../../services/attachmentApi";
import { formatUnitCost, formatCostRange, type SalesProductOption, type ProductCostBatch } from "../../services/productApiData";
import {
  SALES_ATTACHMENT_TYPES,
  attachmentTypeLabel,
  type AttachmentTypeDef,
} from "../../domain/attachments";
import {
  orderCustomerName,
  salesOrderRestoreKey,
  salesListJumpKey,
  salesLineProduct,
  defaultShippingInfo,
  warehouses,
  today,
  money,
  orderTotals,
  shipmentTotals,
  shipmentMatchesOrder,
  shipmentOrders,
  shipmentLineSource,
  remainingQuantity,
  pendingShipmentQuantity,
  type SalesKind,
  type SalesOrder,
  type SalesShipment,
  type Attachment,
} from "../../domain/sales";
const props = defineProps<{
  kind: SalesKind;
  order?: SalesOrder;
  shipment?: SalesShipment;
  relatedOrder?: SalesOrder;
  readonly?: boolean;
  dispatch?: boolean;
  /** 从其他列表返回时恢复上次停留的执行跟踪页签。 */
  initialTab?: string;
}>();
const emit = defineEmits<{ close: []; saved: []; ship: [order: SalesOrder] }>();
const store = useSalesStore(),
  router = useRouter(),
  view = ref(!!props.readonly && props.kind === "orders"),
  full = ref(false),
  tab = ref(props.initialTab || "订单附件"),
  picker = ref(""),
  pickerQuery = ref(""),
  productSelection = ref<string[]>([]),
  dirty = ref(false);
const clone = <T,>(value: T): T => JSON.parse(JSON.stringify(value));
const productCatalog = ref<SalesProductOption[]>([]);
const customers = ref<CustomerOption[]>([]);
const customersLoading = ref(false), customersError = ref('');
const availableCustomers = computed(() => {
  const query = pickerQuery.value.trim().toLowerCase();
  return customers.value.filter(customer => [customer.name, customer.shortName, customer.contact, customer.phone]
    .join(' ').toLowerCase().includes(query));
});
async function loadCustomers() {
  if (customersLoading.value) return;
  customersLoading.value = true;
  customersError.value = '';
  try {
    customers.value = await fetchCustomerOptions();
    applyCustomerShippingDefaults();
  }
  catch (error) { customersError.value = error instanceof Error ? error.message : '客户加载失败'; }
  finally { customersLoading.value = false; }
}
/** 后端订单只保存客户 id，发货信息按客户档案补全空字段，避免提交时缺收货信息。 */
function applyCustomerShippingDefaults() {
  if (props.shipment) return;
  const order = linkedOrder.value;
  const customer = customers.value.find((c) => c.id === order?.customerId);
  if (!customer) return;
  const info = shippingInfo.value;
  if (!info.address.trim()) info.address = customer.address || order?.address || '';
  if (!info.recipient.trim()) info.recipient = customer.contact || '';
  if (!info.phone.trim()) info.phone = customer.phone || '';
}
const productsLoading = ref(false), productsError = ref('');
const costBatchesOpen = ref(false), costBatchesLoading = ref(false), costBatchesError = ref('');
const costProduct = ref<SalesProductOption>();
const costBatches = ref<ProductCostBatch[]>([]);
let costBatchRequest = 0;
async function openCostBatches(product: SalesProductOption) {
  const request = ++costBatchRequest;
  costProduct.value = product;
  costBatchesOpen.value = true;
  costBatches.value = [];
  costBatchesError.value = '';
  costBatchesLoading.value = true;
  try {
    const batches = await fetchProductCostBatches(product.id);
    if (request === costBatchRequest) costBatches.value = batches;
  } catch (error) {
    if (request === costBatchRequest) costBatchesError.value = error instanceof Error ? error.message : '批次成本加载失败';
  } finally {
    if (request === costBatchRequest) costBatchesLoading.value = false;
  }
}
async function loadProducts() {
  if (productsLoading.value) return;
  productsLoading.value = true;
  productsError.value = '';
  try { productCatalog.value = await fetchSalesProducts(); }
  catch (error) { productsError.value = error instanceof Error ? error.message : '产品加载失败'; }
  finally { productsLoading.value = false; }
}
const order = ref<SalesOrder>(
  props.order
    ? clone(props.order)
    : {
        id: crypto.randomUUID(),
        code: "",
        name: "",
        customerId: "",
        taxIncluded: true,
        priceLevel: "标准售价",
        quoteCode: "",
        purchaseNumber: "",
        signed: today(),
        delivery: "",
        owner: "张睿",
        department: "销售部",
        warehouse: warehouses[0]!,
        address: "",
        discount: 0,
        lines: [],
        arrivals: [],
        payments: [],
        attachments: [],
        term: "月结30天",
        orderStatus: "执行中",
        deliveryStatus: "未发货",
        reason: "",
        approver: "",
        approvedAt: "",
      },
);
/** 附件异步加载，有附件时再展开执行跟踪，避免入口藏在折叠区里。 */
// 返回销售订单时跳过弹窗的出现动画，进入页面即呈现打开状态。
const instantOpen = ref(!!props.initialTab);
const shipment = ref<SalesShipment>(
  props.shipment
    ? clone(props.shipment)
    : {
        id: crypto.randomUUID(),
        requestKey: crypto.randomUUID(),
        code: "",
        orderId: "",
        warehouse: "",
        lines: [],
        tag: "出库",
        customerConfirmed: false,
        customerReceivedAt: "",
        confirmed: false,
        shippedAt: "",
        keeper: "陈晨",
        department: "仓储部",
        photos: [],
      },
);
const isOrder = props.kind === "orders",
  title = isOrder ? "销售订单" : props.dispatch ? "发货" : "产品出库";
// 含税口径是订单级的，后端随订单一起返回；缺省按含税处理。
order.value.taxIncluded ??= true;
/** 切换订单级含税口径：不换算已填单价，只改变价格的口径解释。 */
function setTaxIncluded(value: boolean | string | number) {
  const included = value !== false && value !== "false";
  order.value.taxIncluded = included;
  dirty.value = true;
}
const linkedOrders = computed(() =>
  shipmentOrders(shipment.value, store.state.orders),
);
const linkedOrder = computed(() =>
  isOrder
    ? order.value
    : linkedOrders.value[0] ??
      store.state.orders.find((o) => o.id === shipment.value.orderId),
);
const customer = computed(() =>
  customers.value.find((s) => s.id === linkedOrder.value?.customerId),
);
const shippingInfo = ref(clone(props.shipment?.shipping || defaultShippingInfo(linkedOrder.value)));
const dispatchFields = computed(() => [
  ["订单编号", linkedOrder.value?.code], ["客户名称", orderCustomerName(linkedOrder.value)],
]);
const taxIncludedOrder = computed(() => order.value.taxIncluded !== false),
  totals = computed(() => orderTotals(order.value)),
  shippedTotals = computed(() =>
    shipmentTotals(shipment.value, linkedOrder.value, store.state.orders),
  );
const baseFields = computed(() => [
  ...(!isOrder ? [["客户名称", orderCustomerName(linkedOrder.value)]] : []),
  [isOrder ? "联系人" : "客户联系人姓名", customer.value?.contact],
  [isOrder ? "联系人手机" : "客户联系人手机", customer.value?.phone],
]);
const linkedShipments = computed(() =>
  store.state.shipments.filter((r) => shipmentMatchesOrder(r, order.value.id)),
);
/** 与采购订单“产品入库”一致的出库明细：同一出库单下相同产品/Date Code/Lot Code 合并并累加数量。 */
const linkedShipmentRows = computed(() =>
  linkedShipments.value.flatMap((shipment) => {
    const grouped = new Map<string, {
      id: string
      shipment: SalesShipment
      brand: string
      model: string
      packaging: string
      dateCode: string
      lotCode: string
      quantity: number
    }>()
    for (const line of shipment.lines) {
      const source = shipmentLineSource(shipment, line.lineId, store.state.orders)
      const product = salesLineProduct(source?.line)
      const brand = product.brand || '—'
      const model = product.name || '—'
      const packaging = product.model || '—'
      const batches = line.batches?.length
        ? line.batches
        : [{ dateCode: '', lotCode: '', quantity: line.arrived }]
      for (const batch of batches) {
        const dateCode = batch.dateCode || '—'
        const lotCode = batch.lotCode || '—'
        const key = [brand, model, packaging, dateCode, lotCode].join('\u0000')
        const existing = grouped.get(key)
        const quantity = Number(batch.quantity) || 0
        if (existing) existing.quantity += quantity
        else
          grouped.set(key, {
            id: `${shipment.id}-${key}`,
            shipment,
            brand,
            model,
            packaging,
            dateCode,
            lotCode,
            quantity,
          })
      }
    }
    return [...grouped.values()]
  }),
);
/** 斑马纹按出库单分组，同一出库单的所有产品行使用同一底色。 */
function shipmentRowClass({ row }: { row: (typeof linkedShipmentRows.value)[number] }) {
  const index = linkedShipments.value.findIndex((item) => item.id === row.shipment.id);
  return index % 2 === 1 ? "doc-striped" : "";
}
function shipmentSpan({ rowIndex, columnIndex }: { rowIndex: number; columnIndex: number }): [number, number] {
  if (columnIndex > 1) return [1, 1]
  const list = linkedShipmentRows.value
  const current = list[rowIndex]
  if (!current) return [1, 1]
  let start = rowIndex
  while (start > 0 && list[start - 1]!.shipment.id === current.shipment.id) start--
  let end = rowIndex
  while (end + 1 < list.length && list[end + 1]!.shipment.id === current.shipment.id) end++
  return rowIndex === start ? [end - start + 1, 1] : [0, 0]
}
const linkedPayments = computed(() =>
  store.state.payments.filter((r) => r.orderId === order.value.id),
);
const linkedReturns = computed(() =>
  store.state.returns.filter((r) => r.orderId === order.value.id),
);
// 财务明细改为后端应收数据，不再使用前端本地收款记录。
const financeItems = ref<OpenItem[]>([]),
  financeLoading = ref(false),
  financeError = ref("");
const financeTableRef = ref<{ $el?: HTMLElement }>();
const {
  scroller: financeScroller,
  scrollWidth: financeScrollWidth,
  refresh: refreshFinanceScroll,
  onScroll: onFinanceScroll,
} = useStickyTableScroll(financeTableRef);
async function loadFinance() {
  if (!isOrder || !order.value.code || !/^\d+$/.test(order.value.id)) return;
  financeLoading.value = true;
  financeError.value = "";
  try {
    const page = await financeApi.openItems({
      direction: "AR",
      keyword: order.value.code,
      pageNo: 1,
      pageSize: 200,
    });
    financeItems.value = page.records.filter((item) => item.orderNo === order.value.code);
  } catch (e) {
    financeError.value = e instanceof Error ? e.message : "财务数据加载失败";
  } finally {
    financeLoading.value = false;
    void refreshFinanceScroll();
  }
}
watch(tab, (value) => {
  if (value === "财务明细") void loadFinance();
});
function financeOverdue(row: OpenItem) {
  if (!row.dueDate) return false;
  const now = new Date(),
    today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
  return row.dueDate < today && Number(row.settledAmount ?? 0) < Number(row.grossAmount ?? 0);
}
const financeSummary = computed(() => {
  const gross = financeItems.value.reduce((sum, item) => sum + Number(item.grossAmount ?? 0), 0);
  const settled = financeItems.value.reduce((sum, item) => sum + Number(item.settledAmount ?? 0), 0);
  const overdue = financeItems.value
    .filter(financeOverdue)
    .reduce((sum, item) => sum + Number(item.grossAmount ?? 0) - Number(item.settledAmount ?? 0), 0);
  return { gross, settled, outstanding: Math.max(0, gross - settled), overdue };
});
function financeStatusLabel(status: string) {
  return ({ OPEN: "待对账", IN_STATEMENT: "对账中", RECONCILED: "已确认" } as Record<string, string>)[status] || status;
}
/** 离开订单详情前记住当前订单和页签，返回销售订单页时恢复弹窗。 */
function rememberOrderDetail() {
  sessionStorage.setItem(
    salesOrderRestoreKey,
    JSON.stringify({
      order: { ...order.value, attachments: [] },
      orderId: order.value.id,
      tab: tab.value,
      at: Date.now(),
    }),
  );
}
/** 跳转到产品出库列表，并把当前出库单号带过去作为筛选条件。 */
function openShipmentList(code?: string) {
  if (!code) return;
  rememberOrderDetail();
  // 通过会话暂存单号，由产品出库列表直接填入搜索框，不在 URL 中携带筛选条件。
  sessionStorage.setItem(
    `${salesListJumpKey}:shipments`,
    JSON.stringify({ keyword: code, at: Date.now() }),
  );
  void router.push({ name: "sales-shipments" });
}
/** 跳转到对账单列表，并把当前对账单号带过去作为筛选条件。 */
function openStatementList(statementNo?: string | null) {
  if (!statementNo) return;
  rememberOrderDetail();
  // 通过会话暂存对账单号，由对账单列表直接填入搜索框，不在 URL 中携带筛选条件。
  sessionStorage.setItem(
    financeStatementJumpKey,
    JSON.stringify({ keyword: statementNo, direction: "AR", at: Date.now() }),
  );
  void router.push({ name: "finance-statements" });
}
const locked = computed(
  () =>
    linkedShipments.value.length > 0 ||
    linkedPayments.value.length > 0 ||
    linkedReturns.value.length > 0,
);
const availableProducts = computed(() =>
  productCatalog.value.filter(
    (p) =>
      (isOrder ||
        linkedOrder.value?.lines.some(          (l) =>
            l.productId === p.id &&
            remainingQuantity(
              store.state,
              shipment.value.orderId,
              l.id,
              shipment.value.id,
            ) > 0,
        )) &&
      [p.name, p.code, p.model, p.brand]
        .join()
        .toLowerCase()
        .includes(pickerQuery.value.toLowerCase()),
  ).sort((a, b) => Number(b.stock > 0) - Number(a.stock > 0)
    || a.name.localeCompare(b.name, 'zh-CN', { numeric: true, sensitivity: 'base' })),
);
watch(
  () => shipment.value.tag,
  (tag) => {
    if (tag !== "出库") {
      shipment.value.confirmed = false;
      shipment.value.shippedAt = "";
      shipment.value.customerConfirmed = false;
      shipment.value.customerReceivedAt = "";
    }
  },
);
function chooseCustomer(id: string) {
  const s = customers.value.find((s) => s.id === id);
  if (!s) return;
  resetCustomerPartCodes();
  order.value.customerId = id;
  order.value.customerName = s.name;
  order.value.customerShortName = s.shortName || undefined;
  order.value.name = `${s.name}-第${store.state.orders.filter((o) => o.customerId === id).length + 1}份销售订单`;
  picker.value = "";
  dirty.value = true;
  void loadCustomerPartCodes(id);
}
const customerPartCodes = ref<Record<string, string>>({});
let partCodeRequest = 0;
async function loadCustomerPartCodes(customerId: string) {
  const request = ++partCodeRequest;
  customerPartCodes.value = {};
  if (!/^\d+$/.test(customerId)) return;
  try {
    const rows = await fetchCustomerProductCodes(customerId);
    if (request !== partCodeRequest) return;
    customerPartCodes.value = Object.fromEntries(rows
      .filter(row => row.enabled)
      .map(row => [String(row.productId), row.customerCode]));
    fillCustomerPartCodes();
  } catch {
    // 客件编码加载失败不影响下单，用户可以手工填写。
  }
}
function fillCustomerPartCodes() {
  for (const line of order.value.lines)
    if (!line.customerPartNo && customerPartCodes.value[line.productId])
      line.customerPartNo = customerPartCodes.value[line.productId];
}
/** 切换客户时清掉上一客户自动带出的编码，避免打印出错；手工填写且不匹配旧编码的保留。 */
function resetCustomerPartCodes() {
  const known = new Set(Object.values(customerPartCodes.value));
  for (const line of order.value.lines)
    if (!line.customerPartNo || known.has(line.customerPartNo)) line.customerPartNo = '';
}
function customerPartCodeFor(productId: string) {
  return customerPartCodes.value[productId] || '';
}
function useCustomerName() {
  const name = pickerQuery.value.trim();
  if (!name) return void ElMessage.warning("请填写客户名称");
  if (name.length > 100) return void ElMessage.warning("客户名称不能超过100字");
  const existing = customers.value.find(c => c.name === name);
  if (existing) return chooseCustomer(existing.id);
  resetCustomerPartCodes();
  customerPartCodes.value = {};
  order.value.customerId = "";
  order.value.customerName = name;
  order.value.customerShortName = undefined;
  order.value.name = `${name}-销售订单`;
  picker.value = "";
  dirty.value = true;
}
function chooseOrder(o: SalesOrder) {
  shipment.value.orderId = o.id;
  shipment.value.orderIds = [o.id];
  shippingInfo.value = defaultShippingInfo(o);
  shipment.value.warehouse = o.warehouse;
  shipment.value.lines = o.lines
    .filter(
      (l) => remainingQuantity(store.state, o.id, l.id, shipment.value.id) > 0,
    )
    .map((l) => ({
      lineId: l.id,
      arrived: 0,
      batches: [],
      location: "",
    }));
  // 从销售订单点“发货”进入时，提交即代表确认出库，不再先落本地待出库草稿。
  if (props.dispatch) {
    shipment.value.confirmed = true;
    if (!shipment.value.shippedAt)
      shipment.value.shippedAt = new Date().toLocaleString("sv-SE").slice(0, 16);
  }
  picker.value = "";
  dirty.value = true;
}
if (props.relatedOrder && !props.shipment) chooseOrder(props.relatedOrder);
function addProducts() {
  if (!isOrder) {
    for (const id of productSelection.value) {
      const line = linkedOrder.value?.lines.find((l) => l.productId === id);
      if (line && !shipment.value.lines.some((l) => l.lineId === line.id)) {
        shipment.value.lines.push({
          lineId: line.id,
          arrived: 0,
          batches: [],
          location: "",
        });
      }
    }
    productSelection.value = [];
    picker.value = "";
    dirty.value = true;
    return;
  }
  for (const id of productSelection.value)
    if (!order.value.lines.some((l) => l.productId === id)) {
      const p = productCatalog.value.find((p) => p.id === id);
      if (!p) continue;
      order.value.lines.push({
        id: crypto.randomUUID(),
        productId: id,
        quantity: 1,
        brand: p.brand,
        model: p.name,
        packaging: p.model,
        customerPartNo: customerPartCodeFor(id),
        price: 0,
        tax: p.tax,
      });
    }
  productSelection.value = [];
  picker.value = "";
  dirty.value = true;
}
function productValue(id: string | undefined, key: string) {
  const p = productCatalog.value.find((p) => p.id === id);
  return p ? p[key as keyof typeof p] : "";
}
function lineProductValue(line: SalesOrder['lines'][number] | undefined, key: string) {
  if (key === 'brand' || key === 'name' || key === 'model') return salesLineProduct(line)[key];
  return productValue(line?.productId, key);
}
function productFor(lineId: string) {
  const line = orderLine(lineId);
  return line ? { ...salesLineProduct(line), id: line.productId } : undefined;
}
function orderLine(lineId: string) {
  if (isOrder) return order.value.lines.find((l) => l.id === lineId);
  return (
    shipmentLineSource(shipment.value, lineId, store.state.orders)?.line ??
    linkedOrder.value?.lines.find((l) => l.id === lineId)
  );
}
/** 出库行所属销售订单；单订单出库时回退到单据主订单。 */
function lineOrderId(lineId: string) {
  return shipmentLineSource(shipment.value, lineId, store.state.orders)?.order.id ??
    shipment.value.orderId;
}
const batchLineId = ref('');
const batchLine = computed(() => shipment.value.lines.find(l => l.lineId === batchLineId.value));
const batchProduct = computed(() => orderLine(batchLineId.value));
const batchUsedQuantities = computed(() => {
  const used: Record<string, number> = {};
  for (const line of shipment.value.lines) {
    if (line.lineId === batchLineId.value) continue;
    for (const batch of line.batches ?? []) used[batch.inventoryId] = (used[batch.inventoryId] || 0) + batch.quantity;
  }
  return used;
});
function selectShipmentBatches(batches: ShipmentBatch[]) {
  if (!batchLine.value) return;
  batchLine.value.batches = batches;
  batchLine.value.arrived = batches.reduce((sum, b) => sum + b.quantity, 0);
  dirty.value = true;
  batchLineId.value = '';
}
function batchDescription(line: SalesShipment['lines'][number]) {
  return line.batches?.map(b => `${b.batchNo || b.inventoryId}：${b.quantity}`).join('\n') || '尚未选择批次';
}
const dispatchTable = ref<{ $el: HTMLElement }>();
const dispatchFont = ref('14px Arial'), dispatchHeaderFont = ref('bold 14px Arial');
const measurement = document.createElement('canvas').getContext('2d');
function syncDispatchFont() {
  const table = dispatchTable.value?.$el;
  const cell = table?.querySelector('tbody .cell');
  const header = table?.querySelector('thead .cell');
  if (cell) dispatchFont.value = getComputedStyle(cell).font;
  if (header) dispatchHeaderFont.value = getComputedStyle(header).font;
}
onMounted(() => {
  void loadProducts();
  void loadCustomers();
  if (isOrder && order.value.code) void loadFinance();
  if (isOrder && order.value.code) void loadAttachments();
  if (!isOrder) void loadReceiptAttachments();
  if (!isOrder) void loadShipmentPhotos();
  if (isOrder && /^\d+$/.test(order.value.customerId))
    void loadCustomerPartCodes(order.value.customerId);
  syncDispatchFont();
  void document.fonts.ready.then(syncDispatchFont);
  window.addEventListener('resize', syncDispatchFont);
  if (instantOpen.value) setTimeout(() => { instantOpen.value = false; }, 200);
});
onBeforeUnmount(() => window.removeEventListener('resize', syncDispatchFont));
function contentWidth(label: string, values: unknown[], minimum = 0, controlPadding = 0) {
  const measure = (value: unknown, font: string) => {
    const text = String(value ?? '').replace(/\s+/g, ' ');
    if (!measurement) return text.length * 14;
    measurement.font = font;
    return measurement.measureText(text).width;
  };
  return Math.ceil(Math.max(minimum, measure(label, dispatchHeaderFont.value) + 18,
    ...values.map(value => measure(value, dispatchFont.value) + 18 + controlPadding)));
}
const dispatchWidths = computed(() => {
  const lines = shipment.value.lines;
  return {
    index: contentWidth('', [lines.length], 34),
    brand: contentWidth('品牌', lines.map(l => productFor(l.lineId)?.brand)),
    model: contentWidth('型号', lines.map(l => productFor(l.lineId)?.name)),
    package: contentWidth('封装', lines.map(l => productFor(l.lineId)?.model)),
    quantity: contentWidth('销售数量', lines.map(l => orderLine(l.lineId)?.quantity)),
    pending: contentWidth('待出库数量', lines.map(l => remainingQuantity(store.state, lineOrderId(l.lineId), l.lineId, shipment.value.id))),
    dispatch: contentWidth('本次发货数量', lines.map(l => l.arrived), 105),
    remark: 180,
  };
});
const remarkOpen = ref(false);
const remarkText = ref('');
const remarkLine = ref<SalesShipment['lines'][number]>();
function openRemark(line: SalesShipment['lines'][number]) {
  remarkLine.value = line;
  remarkText.value = line.remark || '';
  remarkOpen.value = true;
}
function saveRemark() {
  if (remarkLine.value && !view.value && !props.shipment?.confirmed) {
    remarkLine.value.remark = remarkText.value;
    dirty.value = true;
  }
  remarkOpen.value = false;
}
function openPicker(name: string) {
  pickerQuery.value = "";
  productSelection.value = [];
  picker.value = name;
  if (name === 'product' && (productsError.value || !productCatalog.value.length)) void loadProducts();
  if (name === 'customer') void loadCustomers();
}
const saving = ref(false);
const savedShipment = ref<SalesShipment | null>(null),
  successOpen = ref(false);
/** 提交成功后先展示出库单号和打印入口，关闭后再通知父级刷新并关闭编辑弹窗。 */
function finishSaved() {
  successOpen.value = false;
  savedShipment.value = null;
  emit("saved");
  emit("close");
}
async function save() {
  if (saving.value) return;
  saving.value = true;
  try {
    if (isOrder) await store.saveOrder(order.value, productCatalog.value);
    else {
      // 出库不再单独确认：保存即生成正式出库单，未填时间的补当前时间。
      if (!props.shipment?.confirmed) {
        shipment.value.confirmed = true;
        shipment.value.tag = "出库";
        shipment.value.shippedAt ||= new Date().toLocaleString("sv-SE").slice(0, 16);
      }
      if (!props.shipment?.confirmed) {
        const stockByProduct = new Map<string, ProductCostBatch[]>();
        const quantities = new Map<string, number>();
        for (const line of shipment.value.lines) {
          if (!line.batches?.length) continue;
          const source = orderLine(line.lineId);
          if (!source) throw Error('销售产品已变更，请刷新');
          let stock = stockByProduct.get(source.productId);
          if (!stock) {
            stock = await fetchProductCostBatches(source.productId);
            stockByProduct.set(source.productId, stock);
          }
          const message = validateBatchSelection(line.batches, stock, remainingQuantity(store.state, lineOrderId(line.lineId), line.lineId, shipment.value.id));
          if (message) throw Error(message);
          for (const batch of line.batches) {
            const current = stock.find(b => b.inventoryId === batch.inventoryId)!;
            const quantity = (quantities.get(batch.inventoryId) || 0) + batch.quantity;
            if (quantity > current.quantity) throw Error('同一批次的发货总数超过剩余库存，请重新选择');
            if (batch.unitCost !== current.unitCost) throw Error('批次成本已变更，请重新选择批次');
            quantities.set(batch.inventoryId, quantity);
          }
        }
      }
      shipment.value.shipping = clone({ ...shippingInfo.value, purchaseNumber: linkedOrder.value?.purchaseNumber || "" });
      if (!props.dispatch && shipment.value.customerConfirmed) {
        if (!receiptAttachments.value.length) await loadReceiptAttachments();
        if (!receiptAttachments.value.length)
          throw Error("客户确认收货时请先上传回签单");
      }
      const saved = await store.saveShipment(shipment.value);
      await flushPendingPhotos(saved.id);
      dirty.value = false;
      if (props.dispatch && saved.code) {
        shipment.value = clone(saved);
        savedShipment.value = saved;
        successOpen.value = true;
        return;
      }
    }
    dirty.value = false;
    const editingPersistedShipment = !isOrder && !!props.shipment?.confirmed;
    ElMessage.success(
      isOrder
        ? "销售订单已保存"
        : editingPersistedShipment
          ? props.shipment?.customerConfirmed && !shipment.value.customerConfirmed
            ? "客户签收已取消"
            : shipment.value.customerConfirmed
              ? "客户签收已保存"
              : "发货信息已保存"
          : shipment.value.confirmed
            ? "出库成功，可在“产品出库”查看并打印发货单"
            : `已保存${shipment.value.tag}单据`,
    );
    emit("saved");
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    saving.value = false;
  }
}
async function close(done?: () => void) {
  if (!view.value && dirty.value) {
    try {
      await ElMessageBox.confirm("尚有未保存的修改，确认关闭？", "放弃修改", {
        confirmButtonText: "放弃",
        cancelButtonText: "继续编辑",
        type: "warning",
      });
    } catch {
      return;
    }
  }
  done?.();
  emit("close");
}

const attachmentTypes = SALES_ATTACHMENT_TYPES;
const receiptTypes = SALES_ATTACHMENT_TYPES.filter((type) => type.key === "DELIVERY_RECEIPT");
// 附件由后端统一存储到 MinIO，列表读取接口数据，不再随订单写本地存档。
const attachments = ref<Attachment[]>([]),
  attachmentsLoading = ref(false),
  attachmentError = ref("");
function toAttachment(item: AttachmentVO): Attachment {
  return {
    id: item.id,
    name: item.fileName,
    url: item.downloadUrl,
    fileSize: item.fileSize,
    type: item.category,
    uploadedAt: item.createdAt ? item.createdAt.slice(0, 10).replaceAll("-", "") : undefined,
  };
}
async function loadAttachments() {
  if (!isOrder || !order.value.code || !/^\d+$/.test(order.value.id)) return;
  attachmentsLoading.value = true;
  attachmentError.value = "";
  try {
    const rows = await fetchOrderAttachments("SALES_ORDER", Number(order.value.id));
    attachments.value = rows.map(toAttachment);
  } catch (e) {
    attachmentError.value = e instanceof Error ? e.message : "附件加载失败";
  } finally {
    attachmentsLoading.value = false;
  }
}
// 出库单的客户回签单挂在关联销售订单的“发货回签”类型下，订单附件页可统一查看。
const receiptAttachments = ref<Attachment[]>([]),
  receiptLoading = ref(false),
  receiptError = ref("");
async function loadReceiptAttachments() {
  const target = linkedOrder.value;
  if (isOrder || !target || !/^\d+$/.test(target.id)) return;
  receiptLoading.value = true;
  receiptError.value = "";
  try {
    const rows = await fetchOrderAttachments("SALES_ORDER", Number(target.id));
    receiptAttachments.value = rows
      .filter((item) => item.category === "DELIVERY_RECEIPT")
      .map(toAttachment);
  } catch (e) {
    receiptError.value = e instanceof Error ? e.message : "回签单加载失败";
  } finally {
    receiptLoading.value = false;
  }
}
async function uploadReceipt(files: File[]) {
  const target = linkedOrder.value;
  if (!files.length || !target || !/^\d+$/.test(target.id)) return;
  let uploaded = 0;
  for (const file of files) {
    if (file.size > 10 * 1024 * 1024) {
      ElMessage.warning(`${file.name} 超过 10MB，未上传`);
      continue;
    }
    try {
      await uploadOrderAttachment("SALES_ORDER", Number(target.id), "DELIVERY_RECEIPT", file);
      uploaded++;
    } catch (e) {
      ElMessage.error(`${file.name}：${(e as Error).message}`);
    }
  }
  if (!uploaded) return;
  await loadReceiptAttachments();
  ElMessage.success("回签单已上传");
}
async function removeReceipt(file: Attachment) {
  if (!file.id) return;
  try {
    await ElMessageBox.confirm(`确认删除回签单「${file.name}」？`, "删除回签单", {
      confirmButtonText: "删除",
      cancelButtonText: "取消",
      type: "warning",
    });
  } catch {
    return;
  }
  try {
    await deleteOrderAttachment(file.id);
    await loadReceiptAttachments();
    ElMessage.success("回签单已删除");
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}
/** 删除附件：后端软删除并重新编号，列表随后刷新。 */
async function removeAttachment(file: Attachment) {
  if (!file.id) return;
  try {
    await ElMessageBox.confirm(`确认删除附件「${file.name}」？`, "删除附件", {
      confirmButtonText: "删除",
      cancelButtonText: "取消",
      type: "warning",
    });
  } catch {
    return;
  }
  try {
    await deleteOrderAttachment(file.id);
    await loadAttachments();
    ElMessage.success("附件已删除");
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}
/** 上传到后端；统一命名和同类编号由服务端完成。 */
async function uploadAttachment(files: File[], type: string) {
  if (!files.length) return;
  if (!order.value.code || !/^\d+$/.test(order.value.id)) {
    ElMessage.warning("请先保存订单后再上传附件");
    return;
  }
  let uploaded = 0;
  for (const file of files) {
    if (file.size > 10 * 1024 * 1024) {
      ElMessage.warning(`${file.name} 超过 10MB，未上传`);
      continue;
    }
    try {
      await uploadOrderAttachment("SALES_ORDER", Number(order.value.id), type, file);
      uploaded++;
    } catch (e) {
      ElMessage.error(`${file.name}：${(e as Error).message}`);
    }
  }
  if (!uploaded) return;
  await loadAttachments();
  ElMessage.success(`${attachmentTypeLabel(attachmentTypes, type)}已上传`);
}
/** 出库照片统一存 MinIO；未保存的出库单先暂存 File，提交后再上传。 */
const photoType: AttachmentTypeDef = { key: "OUTBOUND_PHOTO", label: "发货照片", slots: "unlimited" };
const pendingPhotos = ref<{ file: File; preview: Attachment }[]>([]);
async function loadShipmentPhotos() {
  if (!/^\d+$/.test(shipment.value.id)) return;
  const rows = await fetchOrderAttachments("OUTBOUND_ORDER", Number(shipment.value.id));
  shipment.value.photos = rows
    .filter((item) => item.category === "OUTBOUND_PHOTO")
    .map(toAttachment);
}
async function uploadPhotos(files: File[]) {
  for (const file of files) {
    if (file.size > 10 * 1024 * 1024) {
      ElMessage.warning("图片每个不超过 10MB");
      continue;
    }
    if (!file.type.startsWith("image/")) {
      ElMessage.warning("请选择图片");
      continue;
    }
    if (/^\d+$/.test(shipment.value.id)) {
      try {
        const uploaded = await uploadOrderAttachment(
          "OUTBOUND_ORDER",
          Number(shipment.value.id),
          "OUTBOUND_PHOTO",
          file,
        );
        shipment.value.photos.push(toAttachment(uploaded));
        dirty.value = true;
      } catch (e) {
        ElMessage.error(e instanceof Error ? e.message : "照片上传失败");
      }
      continue;
    }
    const url = await new Promise<string>((resolve, reject) => {
      const r = new FileReader();
      r.onload = () => resolve(String(r.result));
      r.onerror = reject;
      r.readAsDataURL(file);
    }).catch(() => "");
    if (url) {
      const preview: Attachment = { name: file.name, url, type: "OUTBOUND_PHOTO" };
      shipment.value.photos.push(preview);
      pendingPhotos.value.push({ file, preview });
      dirty.value = true;
    }
  }
}
async function flushPendingPhotos(outboundId: string) {
  if (!pendingPhotos.value.length || !/^\d+$/.test(outboundId)) return;
  let failed = 0;
  for (const item of pendingPhotos.value) {
    try {
      await uploadOrderAttachment("OUTBOUND_ORDER", Number(outboundId), "OUTBOUND_PHOTO", item.file);
    } catch {
      failed++;
    }
  }
  pendingPhotos.value = [];
  if (failed) ElMessage.warning(`${failed} 张照片上传失败，可在出库详情中重新上传`);
}
async function removePhoto(file: Attachment) {
  const index = shipment.value.photos.indexOf(file);
  if (index >= 0) shipment.value.photos.splice(index, 1);
  const pendingIndex = pendingPhotos.value.findIndex((item) => item.preview === file);
  if (pendingIndex >= 0) {
    pendingPhotos.value.splice(pendingIndex, 1);
    dirty.value = true;
    return;
  }
  if (file.id) {
    try {
      await deleteOrderAttachment(file.id);
    } catch (e) {
      ElMessage.error(e instanceof Error ? e.message : "照片删除失败");
    }
  }
}
/** 勾选客户收货确认时默认带出当前时间。 */
function onCustomerConfirmChange(value: boolean | string | number) {
  const confirmed = value === true || value === "true";
  if (confirmed && !shipment.value.customerReceivedAt)
    shipment.value.customerReceivedAt = new Date().toLocaleString("sv-SE").slice(0, 16);
  if (!confirmed) shipment.value.customerReceivedAt = "";
  dirty.value = true;
}
const quick = ref(false),
  quickText = ref("");
function quickFill() {
  if (!isOrder) return void ElMessage.warning('请通过选择批次填写出库数量');
  const rows = quickText.value
    .trim()
    .split(/\r?\n/)
    .map((l) => l.split(/[\t,，]/));
  const changes = rows.map((r) => ({
    p: productCatalog.value.find((p) => p.code === r[0]?.trim()),
    q: Number(r[1]),
  }));
  if (
    !changes.length ||
    changes.some((r) => !r.p || !Number.isSafeInteger(r.q) || r.q <= 0)
  )
    return ElMessage.error("每行填写产品编码和正整数数量，以逗号或 Tab 分隔");
  if (
    !isOrder &&
    changes.some(
      (r) =>
        !shipment.value.lines.some(
          (row) => orderLine(row.lineId)?.productId === r.p!.id,
        ),
    )
  )
    return ElMessage.error("产品不在本次出库订单中");
  for (const r of changes) {
    if (isOrder) {
      const old = order.value.lines.find((l) => l.productId === r.p!.id);
      if (old) old.quantity = r.q;
      else
        order.value.lines.push({
          id: crypto.randomUUID(),
          productId: r.p!.id,
          quantity: r.q,
          brand: r.p!.brand,
          model: r.p!.name,
          packaging: r.p!.model,
          price: 0,
          tax: r.p!.tax,
        });
    } else {
      const l = linkedOrder.value?.lines.find((l) => l.productId === r.p!.id),
        row = shipment.value.lines.find((x) => x.lineId === l?.id);
      if (!row) return ElMessage.error("产品不在本次出库订单中");
      row.arrived = r.q;
    }
  }
  dirty.value = true;
  quick.value = false;
}
</script>

<template>
  <el-dialog
    :model-value="true"
    :title="title"
    width="min(1000px, 96vw)"
    top="3vh"
    :fullscreen="full"
    class="purchase-dialog"
    :class="{ 'sales-dispatch-dialog': props.dispatch, 'sales-order-dialog': isOrder, 'sales-shipment-dialog': !isOrder }"
    :close-on-click-modal="false"
    :before-close="close"
    destroy-on-close
    :transition="instantOpen ? 'none' : undefined"
  >
    <template #header
      ><div class="purchase-dialog-heading">
        <span
          >{{ title
          }}<el-tag
            v-if="isOrder && order.code"
            size="small"
            :type="
              order.orderStatus === '已完结'
                ? 'success'
                : order.orderStatus === '执行中'
                  ? 'warning'
                  : 'info'
            "
            >{{ order.orderStatus }}</el-tag
          ><el-tag
            v-if="isOrder && order.code"
            size="small"
            type="info"
            style="margin-left: 6px"
            >{{ order.deliveryStatus }}</el-tag
          ><el-tag
            v-if="isOrder && order.code && Number(order.prepaymentAmount ?? 0) > 0"
            size="small"
            type="warning"
            effect="plain"
            style="margin-left: 6px"
            >预收款 ¥{{ money(Number(order.prepaymentAmount ?? 0)) }}</el-tag
          ></span
        ><el-button
          :icon="FullScreen"
          text
          aria-label="切换表单全屏"
          @click="full = !full"
        /></div
    ></template>
    <div
      class="purchase-form-body"
      @change="dirty = true"
      @input="dirty = true"
    >
      <h3 class="purchase-section">
        {{ isOrder ? "销售订单信息" : props.dispatch ? "订单信息" : "销售订单信息" }}
      </h3>

      <el-form
        v-if="props.dispatch"
        label-position="top"
        class="purchase-form-grid dispatch-order-fields"
        size="small"
      >
        <el-form-item
          v-for="[label, value] in dispatchFields"
          :key="label"
          :label="label"
          ><div class="purchase-read">{{ value || "—" }}</div></el-form-item
        >
      <el-form-item label="客户采购单号"><div class="purchase-read" data-testid="dispatch-purchase-number">{{ linkedOrder?.purchaseNumber || "—" }}</div></el-form-item>
      </el-form>
      <el-form
        v-else
        label-position="top"
        class="purchase-form-grid"
        :class="{ 'sales-order-info': isOrder }"
        :disabled="view"
      >
        <el-form-item v-if="isOrder" label="客户名称" required
          ><el-button
            class="purchase-picker-button"
            @click="openPicker('customer')"
            >{{ orderCustomerName(order) || "选择或填写客户名称" }}</el-button
          ></el-form-item
        >
        <el-form-item v-else label="销售订单编号"
          ><div class="purchase-read">
            {{ linkedOrder?.code || "—" }}
          </div></el-form-item
        >
        <template v-for="[label, value] in baseFields" :key="label">

          <el-form-item :label="label"
            ><div class="purchase-read">
              {{ value || "暂无内容" }}
            </div></el-form-item
          >
        </template>
        <template v-if="isOrder">

          <el-form-item label="订单日期" required
            ><el-date-picker
              v-model="order.signed"
              value-format="YYYY-MM-DD"
              aria-label="订单日期"
          /></el-form-item>
          <el-form-item label="交付日期" required
            ><el-date-picker
              v-model="order.delivery"
              value-format="YYYY-MM-DD"
              aria-label="交付日期"
          /></el-form-item>
        </template>
        <el-form-item v-if="isOrder" label="客户采购单号"><el-input v-model="order.purchaseNumber" aria-label="客户采购单号" maxlength="80" placeholder="填写客户提供的采购单号" /></el-form-item>
        <el-form-item :label="isOrder ? '销售订单编号' : '产品出库单编号'"
          ><div class="purchase-read">
            {{ (isOrder ? order.code : shipment.code) || "自动生成无需填写" }}
          </div></el-form-item
        >
      </el-form>
      <div class="purchase-products-head">
        <h3 class="purchase-section">
          {{ isOrder ? "销售产品信息" : "产品明细" }}
        </h3>
        <div v-if="isOrder && !props.dispatch" class="purchase-price-mode">
          <span class="purchase-price-mode-label">单价口径</span>
          <el-radio-group
            :model-value="order.taxIncluded !== false"
            :disabled="view"
            size="small"
            @update:model-value="setTaxIncluded"
          >
            <el-radio-button :value="true">含税</el-radio-button>
            <el-radio-button :value="false">未税</el-radio-button>
          </el-radio-group>
        </div>
      </div>
      <el-table v-if="isOrder" :data="order.lines" border class="purchase-lines sales-order-products" empty-text="点击“添加”选择销售产品">
        <el-table-column type="index" width="45" />
        <el-table-column label="型号" min-width="105" show-overflow-tooltip>
          <template #default="{row}">{{ lineProductValue(row, 'name') }}</template>
        </el-table-column>
        <el-table-column label="品牌" min-width="65" show-overflow-tooltip>
          <template #default="{row}">{{ lineProductValue(row, 'brand') }}</template>
        </el-table-column>
        <el-table-column label="封装" width="190" show-overflow-tooltip>
          <template #default="{row}">{{ lineProductValue(row, 'model') }}</template>
        </el-table-column>
        <el-table-column label="客户物料编码" width="150">
          <template #default="{row}"><el-input v-model="row.customerPartNo" :disabled="view" maxlength="64" placeholder="客户提供时填写" aria-label="客户物料编码" /></template>
        </el-table-column>
        <el-table-column label="数量" width="130"><template #default="{row}"><el-input-number v-model="row.quantity" :step="100" :disabled="view" :min="1" :max="100000000" :precision="0" controls-position="right" aria-label="数量" /></template></el-table-column>
        <el-table-column :label="taxIncludedOrder ? '含税单价' : '未税单价'" width="120"><template #default="{row}"><el-input-number v-model="row.price" :disabled="view" :min="0" :max="10000000" :precision="4" controls-position="right" aria-label="单价" /></template></el-table-column>
        <el-table-column label="税率(%)" width="100"><template #default="{row}"><el-select v-if="taxIncludedOrder" v-model="row.tax" :disabled="view" aria-label="税率(%)"><el-option v-for="rate in [13,6,3,0]" :key="rate" :label="rate + '%'" :value="rate" /></el-select><span v-else class="sales-tax-empty">—</span></template></el-table-column>
        <el-table-column :label="taxIncludedOrder ? '含税金额' : '未税金额'" width="120"><template #default="{row}">{{ money(row.price * row.quantity) }}</template></el-table-column>
        <el-table-column label="当前库存" width="120"><template #default="{row}">{{ productValue(row.productId, 'stock') }}</template></el-table-column>
        <el-table-column v-if="!view" fixed="right" label="操作" width="90" align="center" class-name="sales-order-product-actions">
          <template #default="{$index}"><el-button text type="danger" :icon="Delete" aria-label="删除产品" @click="order.lines.splice($index,1); dirty=true" /></template>
        </el-table-column>
      </el-table>
      <el-table
        v-else-if="!props.dispatch"
        :data="shipment.lines"
        border
        class="purchase-lines"
        empty-text="暂无产品明细"
      >
        <el-table-column type="index" width="45" />
        <el-table-column label="型号" min-width="130" show-overflow-tooltip
          ><template #default="{ row }">{{
            orderLine(row.lineId)?.model || "—"
          }}</template></el-table-column
        >
        <el-table-column label="品牌" width="90" show-overflow-tooltip
          ><template #default="{ row }">{{
            orderLine(row.lineId)?.brand || "—"
          }}</template></el-table-column
        >
        <el-table-column label="封装" width="190" show-overflow-tooltip
          ><template #default="{ row }">{{
            orderLine(row.lineId)?.packaging || "—"
          }}</template></el-table-column
        >
        <el-table-column label="销售数量" width="105"
          ><template #default="{ row }">{{
            orderLine(row.lineId)?.quantity
          }}</template></el-table-column
        >
        <el-table-column label="待出库数量" width="110"
          ><template #default="{ row }">{{
            remainingQuantity(
              store.state,
              lineOrderId(row.lineId),
              row.lineId,
              shipment.id,
            )
          }}</template></el-table-column
        >
        <el-table-column label="当前可用库存数量" width="140"
          ><template #default="{ row }">{{
            productValue(orderLine(row.lineId)?.productId, 'stock')
          }}</template></el-table-column
        >
        <el-table-column label="本次出库数量" width="110"><template #default="{row}">{{ row.arrived }}</template></el-table-column>
        <el-table-column label="发货批次" width="140"><template #default="{row}">
          <el-tooltip :content="batchDescription(row)" placement="top"><el-button link type="primary" :disabled="view || !!props.shipment?.confirmed" @click="batchLineId = row.lineId">{{ row.batches?.length ? `已选 ${row.batches.length} 个批次` : '选择批次' }}</el-button></el-tooltip>
        </template></el-table-column>
        <el-table-column label="备注" min-width="200">
          <template #default="{ row }">
            <el-input v-model="row.remark" type="textarea" :autosize="{ minRows: 1, maxRows: 3 }" :disabled="view || !!props.shipment?.confirmed" maxlength="200" placeholder="填写本产品发货备注" aria-label="产品备注" />
          </template>
        </el-table-column>
        <el-table-column
          v-if="!view && !props.shipment?.confirmed"
          fixed="right"
          label="操作"
          width="65"
        >
          <template #default="{ $index }"
            ><el-button
              text
              type="danger"
              :icon="Delete"
              aria-label="删除出库产品"
              @click="
                shipment.lines.splice($index, 1);
                dirty = true;
              "
          /></template>
        </el-table-column>
      </el-table>
      <el-table
        v-else
        :data="shipment.lines"
        border
        class="purchase-lines dispatch-product-table"
        ref="dispatchTable"
        :fit="true"
        style="width: 100%"
        empty-text="请选择本次发货的产品"
      >
        <el-table-column type="index" :width="dispatchWidths.index" />
        <el-table-column label="型号" :width="dispatchWidths.model"
          ><template #default="{ row }">{{
            productFor(row.lineId)?.name
          }}</template></el-table-column
        >
        <el-table-column label="品牌" :min-width="dispatchWidths.brand" show-overflow-tooltip
          ><template #default="{ row }">{{
            productFor(row.lineId)?.brand
          }}</template></el-table-column
        >
        <el-table-column label="封装" :width="190" show-overflow-tooltip
          ><template #default="{ row }">{{
            productFor(row.lineId)?.model
          }}</template></el-table-column
        >
        <el-table-column label="销售数量" :min-width="dispatchWidths.quantity"
          ><template #default="{ row }">{{
            orderLine(row.lineId)?.quantity
          }}</template></el-table-column
        >
        <el-table-column label="待出库数量" :min-width="dispatchWidths.pending"
          ><template #default="{ row }">{{
            remainingQuantity(
              store.state,
              lineOrderId(row.lineId),
              row.lineId,
              shipment.id,
            )
          }}</template></el-table-column
        >
        <el-table-column label="本次发货数量" :min-width="dispatchWidths.dispatch"><template #default="{row}">{{ row.arrived }}</template></el-table-column>
        <el-table-column label="发货批次" min-width="130"><template #default="{row}">
          <el-tooltip :content="batchDescription(row)" placement="top"><el-button link type="primary" :disabled="view || !!props.shipment?.confirmed" @click="batchLineId = row.lineId">{{ row.batches?.length ? `已选 ${row.batches.length} 个批次` : '选择批次' }}</el-button></el-tooltip>
        </template></el-table-column>
        <el-table-column label="备注" :min-width="dispatchWidths.remark">
          <template #default="{ row }">
            <el-tooltip :content="row.remark || '填写本产品发货备注'" placement="top" :show-after="300" popper-class="shipment-remark-tooltip">
              <el-input :model-value="row.remark" readonly class="shipment-remark-preview" placeholder="填写本产品发货备注" aria-label="产品备注" @click="openRemark(row)" />
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
      <div
        v-if="!props.dispatch && !view && (isOrder || !props.shipment?.confirmed)"
        class="purchase-line-actions"
      >
        <el-button
          :disabled="!isOrder && !linkedOrder"
          text
          type="primary"
          :icon="Plus"
          @click="openPicker('product')"
          >添加</el-button
        ><el-button
          v-if="isOrder"
          text
          type="primary"
          @click="
            () => {
              quickText = '';
              quick = true;
            }
          "
          >快速填报</el-button
        >
      </div>
      <el-form v-if="isOrder || !props.dispatch" label-position="top" class="purchase-form-grid" :disabled="view">
        <template v-if="isOrder">
          <el-form-item label="销售订单金额/元" class="sales-amount-cell"
            ><div class="purchase-read sales-amount-read">
              <span class="sales-tax-tag" :class="{ 'sales-tax-tag-net': !taxIncludedOrder }">{{ taxIncludedOrder ? "含税" : "不含税" }}</span>
              <span class="sales-amount-value">{{ money(totals.total) }}</span>
              <span v-if="taxIncludedOrder" class="sales-net-hint">其中不含税 {{ money(totals.net) }}</span>
            </div></el-form-item
          >
        </template>
        <template v-else
          ><el-form-item label="出库产品销售价总额(含税)/元"
            ><div class="purchase-read">
              {{ money(shippedTotals.total) }}
            </div></el-form-item
          ></template
        >
      </el-form>
      <template v-if="isOrder">
        <section v-if="order.code" class="sales-order-business">
            <header class="sales-business-header">
              <span class="sales-business-title">执行跟踪</span>
              <span class="sales-business-counts">附件 {{ attachments.length }} · 出库 {{ linkedShipments.length }} · 退货 {{ linkedReturns.length }} · 财务 {{ financeItems.length }}</span>
            </header>
          <nav class="purchase-detail-tabs" aria-label="订单关联信息">
          <button
            v-for="name in [
              '订单附件',
              '产品出库',
              '销售退货',
              '财务明细',
            ]"
            :key="name"
            :class="{ active: tab === name }"
            @click="tab = name"
          >
            {{ name }}
          </button>
          </nav>
        <template v-if="tab === '订单附件'">
          <div class="attachment-groups">
            <el-alert v-if="attachmentError" :title="attachmentError" type="error" :closable="false" />
            <p class="purchase-tip">
              上传后由服务端统一命名为「订单号_类型_客户_日期_序号」，单个文件不超过 10MB。
            </p>
            <AttachmentTiles
              :attachments="attachments"
              :types="attachmentTypes"
              :loading="attachmentsLoading"
              :error="attachmentError"
              @upload="uploadAttachment"
              @remove="removeAttachment"
            />
          </div>
        </template>
        <template v-else-if="tab === '产品出库'">
          <el-table
            :data="linkedShipmentRows"
            row-key="id"
            size="small"
            border
            class="business-zebra-doc"
            empty-text="暂无出库记录"
            :span-method="shipmentSpan"
            :row-class-name="shipmentRowClass"
          >
            <el-table-column label="出库单号" min-width="160">
              <template #default="{ row }">
                <div class="sales-shipment-code">
                  <el-button link type="primary" @click="openShipmentList(row.shipment.code)">{{ row.shipment.code }}</el-button>
                  <DeliveryNoteActions compact :order="order" :orders="shipmentOrders(row.shipment, store.state.orders)" :shipment="row.shipment" />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="出库日期" width="95">
              <template #default="{ row }">{{ row.shipment.shippedAt?.slice(0, 10) || '—' }}</template>
            </el-table-column>
            <el-table-column prop="model" label="型号" min-width="110" show-overflow-tooltip />
            <el-table-column prop="brand" label="品牌" width="75" show-overflow-tooltip />
            <el-table-column prop="packaging" label="封装" min-width="150" show-overflow-tooltip />
            <el-table-column prop="dateCode" label="Date Code" width="90" show-overflow-tooltip />
            <el-table-column prop="lotCode" label="Lot Code" width="110" show-overflow-tooltip />
            <el-table-column label="出库数量" width="105">
              <template #default="{ row }">{{ row.quantity.toLocaleString('zh-CN') }}</template>
            </el-table-column>
          </el-table>
          <el-button v-if="view && order.lines.some(l => pendingShipmentQuantity(order, l, store.state.shipments) > 0)" type="primary" @click="emit('ship', order)">发货</el-button>
        </template>
        <el-table v-else-if="tab === '销售退货'" :data="linkedReturns" border empty-text="暂无退货记录">
          <el-table-column prop="date" label="退货日期" width="110" />
          <el-table-column label="产品" min-width="140">
            <template #default="{ row }">{{ productFor(row.lineId)?.name }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="退货数量" width="95" align="right" />
          <el-table-column prop="reason" label="原因" min-width="180" />
        </el-table>
        <template v-else>
          <el-alert v-if="financeError" :title="financeError" type="error" :closable="false" />
          <div v-else v-loading="financeLoading">
            <el-descriptions :column="4" border size="small" class="sales-finance-summary">
              <el-descriptions-item label="应收合计">{{ money(financeSummary.gross) }}</el-descriptions-item>
              <el-descriptions-item label="已核销">{{ money(financeSummary.settled) }}</el-descriptions-item>
              <el-descriptions-item label="未收金额">{{ money(financeSummary.outstanding) }}</el-descriptions-item>
              <el-descriptions-item label="逾期金额">{{ money(financeSummary.overdue) }}</el-descriptions-item>
            </el-descriptions>
            <el-table ref="financeTableRef" :data="financeItems" border stripe class="business-zebra finance-table" empty-text="暂无财务记录">
              <el-table-column prop="sourceNo" label="出库单号" width="150" />
              <el-table-column label="对账单号" width="150">
                <template #default="{ row }">
                  <el-button
                    v-if="row.statementNo"
                    link
                    type="primary"
                    @click="openStatementList(row.statementNo)"
                    >{{ row.statementNo }}</el-button
                  >
                  <span v-else>—</span>
                </template>
              </el-table-column>
              <el-table-column prop="bizDate" label="业务日期" width="105" />
              <el-table-column prop="partNo" label="型号" min-width="120" />
              <el-table-column prop="quantity" label="数量" width="85" align="right" />
              <el-table-column label="价税合计" width="110" align="right">
                <template #default="{ row }">{{ money(row.grossAmount) }}</template>
              </el-table-column>
              <el-table-column label="已结金额" width="110" align="right">
                <template #default="{ row }">{{ money(row.settledAmount) }}</template>
              </el-table-column>
              <el-table-column label="未结金额" width="110" align="right">
                <template #default="{ row }">{{ money(Number(row.grossAmount || 0) - Number(row.settledAmount || 0)) }}</template>
              </el-table-column>
              <el-table-column prop="settleMonth" label="账单月" width="90" />
              <el-table-column label="到期日" width="105">
                <template #default="{ row }">
                  <span :class="{ 'sales-finance-overdue': financeOverdue(row) }">{{ row.dueDate || '—' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="对账状态" width="100">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain">{{ financeStatusLabel(row.reconcileStatus) }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </template>
        </section>
      </template>
      <template v-else>
        <h3 class="purchase-section">发货信息</h3>
        <ShipmentShippingForm v-model="shippingInfo" :required="shipment.confirmed" :disabled="view" @change="dirty = true" />
        <div v-if="!props.dispatch" class="shipment-photos">
          <div class="shipment-photos-head">
            <strong>发货照片</strong>
          </div>
          <AttachmentTiles
            v-if="shipment.photos.length || !view"
            :attachments="shipment.photos"
            :types="[photoType]"
            :uploadable="!view"
            :removable="!view"
            @upload="uploadPhotos"
            @remove="removePhoto"
          />
          <p v-else class="purchase-tip">暂无发货照片</p>
        </div>
        <template v-if="!props.dispatch">
        <h3 class="purchase-section">收货确认</h3>
        <div class="shipment-confirm-row">
          <div class="shipment-confirm-check">
            <span>客户已确认收货</span>
            <el-checkbox
              v-model="shipment.customerConfirmed"
              :disabled="!shipment.confirmed"
              aria-label="客户已确认收货"
              @change="onCustomerConfirmChange"
            />
          </div>
          <div class="shipment-confirm-time">
            <span>收货确认时间</span>
            <el-date-picker
              v-model="shipment.customerReceivedAt"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm"
              :disabled="!shipment.customerConfirmed"
              placeholder="勾选后自动填写当前时间"
              aria-label="收货确认时间"
            />
          </div>
        </div>
        <div v-if="shipment.code || props.shipment" class="shipment-receipt">
          <div class="shipment-receipt-head">
            <strong
              >回签单<span v-if="shipment.customerConfirmed" style="color: #f56c6c"> *</span></strong
            >
          </div>
          <AttachmentTiles
            :attachments="receiptAttachments"
            :types="receiptTypes"
            :loading="receiptLoading"
            :error="receiptError"
            :uploadable="!view && !!linkedOrder"
            :removable="!view"
            @upload="uploadReceipt"
            @remove="removeReceipt"
          />
        </div>
        </template>
      </template>
    </div>
    <template #footer
      ><div class="purchase-form-footer">
        <div
          v-if="isOrder && tab === '财务明细'"
          class="finance-footer-scroll"
          ref="financeScroller"
          @scroll.passive="onFinanceScroll"
        >
          <div :style="{ width: financeScrollWidth + 'px' }"></div>
        </div>
        <template v-if="view"
          ><el-button
            v-if="!isOrder || (!locked && order.orderStatus !== '已取消')"
            type="primary"
            @click="view = false"
            >编辑</el-button
          ><span v-if="isOrder && locked" class="purchase-tip"
            >已有业务记录，订单基础数据已锁定</span
          ></template
        ><el-button v-else type="primary" :loading="saving" @click="save">{{
          isOrder || !props.dispatch ? "保存" : "提交"
        }}</el-button
        ><el-button @click="close()">{{ view ? "关闭" : "取消" }}</el-button>
      </div></template
    >
  </el-dialog>
  <el-dialog
    v-model="successOpen"
    title="出库成功"
    width="min(560px,94vw)"
    append-to-body
    :close-on-click-modal="false"
    class="purchase-dialog shipment-success-dialog"
    @closed="finishSaved"
  >
    <div class="shipment-success">
      <p>
        出库单 <strong>{{ savedShipment?.code }}</strong> 已生成，可以打印或下载发货单。
      </p>
      <DeliveryNoteActions
        v-if="savedShipment"
        :order="linkedOrder"
        :orders="linkedOrders"
        :shipment="savedShipment"
      />
    </div>
    <template #footer
      ><el-button type="primary" @click="successOpen = false">完成</el-button></template
    >
  </el-dialog>
  <el-dialog
    :model-value="!!picker"
    :title="picker === 'product' ? '选择产品' : '选择客户'"
    :width="picker === 'product' ? 'min(1000px,94vw)' : 'min(880px,94vw)'"
    append-to-body
    class="purchase-dialog"
    @close="picker = ''"
  >
    <el-input
      v-model="pickerQuery"
      :placeholder="picker === 'customer' ? '搜索客户名称、简称、联系人或手机' : '搜索品牌、型号、封装或产品ID'"
      clearable
      class="purchase-picker-search"
    />
    <div v-if="picker === 'customer'" class="sales-new-customer">
      <span>列表中没有该客户？输入客户名称后可直接用于订单，客户资料可稍后维护。</span>
      <el-button type="primary" plain :disabled="!pickerQuery.trim() || customersLoading || !!customersError" @click="useCustomerName">使用此客户名称</el-button>
    </div>
    <el-alert v-if="picker === 'customer' && customersError" :title="customersError" type="error" :closable="false">
      <el-button link type="primary" @click="loadCustomers">重试</el-button>
    </el-alert>
    <el-table
      v-if="picker === 'customer' && !customersError"
      v-loading="customersLoading"
      :data="availableCustomers"
      max-height="420"
      class="sales-customer-options"
      :empty-text="pickerQuery.trim() ? '没有匹配的客户' : '暂无客户数据'"
      border
      @row-dblclick="chooseCustomer($event.id)"
      ><el-table-column prop="name" label="客户名称" min-width="200" show-overflow-tooltip /><el-table-column
        prop="contact"
        label="联系人"
        width="110"
      /><el-table-column
        prop="phone"
        label="手机"
        width="150"
      /><el-table-column prop="settlement" label="结算方式" width="125" show-overflow-tooltip
      /><el-table-column width="80"
        ><template #default="{ row }"
          ><el-button link type="primary" @click="chooseCustomer(row.id)"
            >选择</el-button
          ></template
        ></el-table-column
      ></el-table
    >
    <el-alert v-if="picker === 'product' && productsError" :title="productsError" type="error" :closable="false">
      <el-button link type="primary" @click="loadProducts">重试</el-button>
    </el-alert>
    <el-table
      v-if="picker === 'product' && !productsError"
      :data="availableProducts"
      v-loading="productsLoading"
      row-key="id"
      max-height="440"
      :empty-text="productsLoading ? '加载中…' : '暂无匹配产品'"
      border
      @selection-change="
        productSelection = $event.map((p: { id: string }) => p.id)
      "
      ><el-table-column type="selection" width="45" reserve-selection />
      <el-table-column prop="brand" label="品牌" min-width="65" show-overflow-tooltip />
      <el-table-column prop="name" label="型号" min-width="105" show-overflow-tooltip />
      <el-table-column prop="model" label="封装" width="190" show-overflow-tooltip />
      <el-table-column prop="stock" label="当前库存" min-width="105" align="right" />
      <el-table-column label="平均成本" min-width="120" align="right"><template #default="{row}">{{ formatUnitCost(row.estimatedCost) }}</template></el-table-column>
      <el-table-column label="成本范围" min-width="190" align="right"><template #default="{row}">
        <el-button v-if="row.minCost !== null && row.maxCost !== null" link type="primary" :aria-label="`${row.name}批次成本明细`" @click.stop="openCostBatches(row)">{{ formatCostRange(row) }}</el-button>
        <span v-else>—</span>
      </template></el-table-column>
    </el-table>
    <template #footer
      ><el-button @click="picker = ''">取消</el-button
      ><el-button
        v-if="picker === 'product'"
        type="primary"
        :disabled="!productSelection.length"
        @click="addProducts"
        >确定添加</el-button
      ></template
    >
  </el-dialog>
  <ShipmentBatchPicker v-if="batchLine && batchProduct" :key="batchLineId" :product-id="batchProduct.productId" :product-name="[batchProduct.brand, batchProduct.model, batchProduct.packaging].filter(Boolean).join(' / ')" :pending="remainingQuantity(store.state, lineOrderId(batchLine.lineId), batchLine.lineId, shipment.id)" :batches="batchLine.batches ?? []" :used-quantities="batchUsedQuantities" @close="batchLineId = ''" @selected="selectShipmentBatches" />
  <el-dialog v-model="costBatchesOpen" title="批次成本明细" width="min(720px,94vw)" append-to-body class="purchase-dialog sales-cost-batches-dialog">
    <div class="sales-cost-product">{{ [costProduct?.brand, costProduct?.name, costProduct?.model].filter(Boolean).join(' / ') }}</div>
    <el-alert v-if="costBatchesError" :title="costBatchesError" type="error" :closable="false">
      <el-button link type="primary" @click="costProduct && openCostBatches(costProduct)">重试</el-button>
    </el-alert>
    <el-table v-else v-loading="costBatchesLoading" :data="costBatches" row-key="inventoryId" border max-height="400" :empty-text="costBatchesLoading ? '加载中…' : '暂无有库存的批次'">
      <el-table-column prop="batchNo" label="批次号" min-width="240" show-overflow-tooltip />
      <el-table-column prop="quantity" label="剩余库存" min-width="110" align="right" />
      <el-table-column label="单位成本" min-width="120" align="right"><template #default="{row}">{{ formatUnitCost(row.unitCost) }}</template></el-table-column>
    </el-table>
    <template #footer><el-button @click="costBatchesOpen = false">关闭</el-button></template>
  </el-dialog>
  <el-dialog
    v-model="quick"
    title="快速填报"
    width="min(520px,94vw)"
    append-to-body
    class="purchase-dialog"
    ><p>每行填写“产品编码,数量”，例如 LCW0001,100。相同产品更新数量。</p>
    <el-input
      v-model="quickText"
      type="textarea"
      :rows="7"
      placeholder="LCW0001,100&#10;LCW0002,200"
    /><template #footer
      ><el-button @click="quick = false">取消</el-button
      ><el-button type="primary" @click="quickFill"
        >填入明细</el-button
      ></template
    ></el-dialog
  >
  <el-dialog v-model="remarkOpen" title="产品发货备注" width="min(600px,92vw)" append-to-body class="shipment-remark-editor">
    <el-input v-model="remarkText" type="textarea" :rows="8" maxlength="2000" show-word-limit :readonly="view || !!props.shipment?.confirmed" placeholder="填写本产品发货备注" aria-label="完整产品备注" />
    <template #footer><el-button @click="remarkOpen = false">取消</el-button><el-button type="primary" @click="saveRemark">确定</el-button></template>
  </el-dialog>
</template>

<style>
.sales-cost-product { margin-bottom: 16px; overflow-wrap: anywhere; }
.sales-dispatch-dialog .dispatch-order-fields {
  margin: 10px 0 0;
  column-gap: 16px;
  row-gap: 0;
}
.sales-dispatch-dialog .dispatch-order-fields .el-form-item {
  margin-bottom: 8px;
}
.sales-dispatch-dialog .dispatch-order-fields + .purchase-section {
  margin-top: 12px;
}
.sales-dispatch-dialog .dispatch-order-fields .purchase-read {
  min-height: 28px;
  padding: 4px 9px;
}
.sales-dispatch-dialog .shipping-info-form {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 10px 0 4px;
  column-gap: 16px;
  row-gap: 0;
}
.sales-dispatch-dialog .shipping-info-form .el-form-item {
  margin-bottom: 10px;
}
.sales-dispatch-dialog .dispatch-order-fields .el-form-item__label,
.sales-dispatch-dialog .shipping-info-form .el-form-item__label {
  margin-bottom: 4px;
  line-height: 20px;
}
.sales-dispatch-dialog .shipping-info-form + .purchase-section {
  margin-top: 14px;
}
@media (max-width: 600px) {
  .sales-dispatch-dialog .shipping-info-form { grid-template-columns: 1fr; }
}
</style>

<style>
.dispatch-product-table .cell { padding-left: 7px; padding-right: 7px; white-space: nowrap; word-break: normal; }
.dispatch-product-table .el-input-number { max-width: 100%; }
</style>

<style>
.sales-order-products .cell { white-space: nowrap; padding: 0 8px; }
.sales-order-products .el-input-number { width: 100%; min-width: 0; }
.sales-order-products .el-select { width: 100%; }
.sales-order-products .sales-tax-empty { color: #a8abb2; }
.sales-order-products .sales-order-product-actions .el-button { padding: 8px; margin: 0; }
.sales-new-customer { display: flex; align-items: center; gap: 16px; margin: 12px 0; color: #909399; font-size: 13px; }
</style>

<style>
.purchase-dialog .sales-order-info {
  grid-template-columns: repeat(3, minmax(0, calc((100% - 48px) / 3)));
  column-gap: 16px;
  row-gap: 0;
  margin: 12px 0 8px;
}
.purchase-dialog .sales-order-info .el-form-item {
  margin-bottom: 10px;
}
.purchase-dialog .sales-order-info .el-form-item__label {
  margin-bottom: 5px;
}
@media (max-width: 600px) {
  .purchase-dialog .sales-order-info {
    grid-template-columns: 1fr;
    column-gap: 0;
  }
}
</style>

<style>
.sales-order-dialog .purchase-section:not(:first-child) {
  margin-top: 20px;
}
.sales-order-dialog .purchase-field-label {
  margin-top: 18px;
  margin-bottom: 8px;
}
.sales-order-dialog .purchase-line-actions {
  margin-bottom: 18px;
}
.sales-order-dialog .purchase-form-grid:not(.sales-order-info) {
  margin-top: 18px;
  margin-bottom: 8px;
}
.sales-order-dialog .purchase-detail-tabs {
  margin: 20px 0 16px;
}
.sales-order-business {
  margin-top: 16px;
  border-top: 0;
}
.sales-order-business .sales-business-header {
  position: relative;
  display: flex;
  align-items: center;
  height: auto;
  padding: 0 0 12px;
  border-bottom: 4px solid #d2e6ff;
  color: #1384ff;
  font-size: 14px;
  font-weight: 600;
  line-height: 22px;
}
.sales-order-business .sales-business-header:after {
  content: '';
  position: absolute;
  bottom: -4px;
  left: 0;
  width: 112px;
  height: 7px;
  background: #1384ff;
  border-radius: 0 8px 0 6px;
}
.sales-shipment-code {
  display: flex;
  align-items: center;
  gap: 6px;
}
.sales-business-counts {
  margin-left: 12px;
  color: #8a94a6;
  font-size: 12px;
  font-weight: 400;
}
.sales-finance-summary {
  margin-bottom: 12px;
}
.sales-finance-overdue {
  color: #f56c6c;
  font-weight: 600;
}
.sales-amount-cell {
  grid-column: span 2;
}
.sales-amount-read {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.sales-tax-tag {
  flex: none;
  padding: 0 6px;
  border-radius: 3px;
  background: #fff4e6;
  color: #c26b00;
  font-size: 12px;
  line-height: 18px;
}
.sales-tax-tag-net {
  background: #eaf3ff;
  color: #2563eb;
}
.sales-amount-value {
  font-weight: 600;
  color: #17283f;
}
.sales-net-hint {
  color: #8a94a6;
  font-size: 12px;
}
</style>

<style>
.shipment-remark-preview .el-input__inner { text-overflow: ellipsis; cursor: text; }
.shipment-remark-tooltip { max-width: min(600px,80vw); white-space: pre-wrap; overflow-wrap: anywhere; }
.sales-order-products .el-input-number { width: 100%; }
</style>
