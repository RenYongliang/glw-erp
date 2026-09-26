<script setup lang="ts">
import { computed, ref, watch, onMounted, onBeforeUnmount } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Delete, FullScreen } from "@element-plus/icons-vue";
import DeliveryNoteActions from "./DeliveryNoteActions.vue";
import { useSalesStore } from "../../stores/sales";
import {
  customers,
  orderCustomerName,
  defaultShippingInfo,
  products,
  warehouses,
  today,
  money,
  orderTotals,
  shipmentTotals,
  remainingQuantity,
  availableStock,
  levelPrice,
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
}>();
const emit = defineEmits<{ close: []; saved: []; ship: [order: SalesOrder] }>();
const store = useSalesStore(),
  view = ref(!!props.readonly),
  full = ref(false),
  tab = ref("订单明细"),
  picker = ref(""),
  pickerQuery = ref(""),
  productSelection = ref<string[]>([]),
  dirty = ref(false);
const clone = <T,>(value: T): T => JSON.parse(JSON.stringify(value));
const order = ref<SalesOrder>(
  props.order
    ? clone(props.order)
    : {
        id: crypto.randomUUID(),
        code: "",
        name: "",
        customerId: "",
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
        status: "待审批",
        reason: "",
        approver: "",
        approvedAt: "",
      },
);
const shipment = ref<SalesShipment>(
  props.shipment
    ? clone(props.shipment)
    : {
        id: crypto.randomUUID(),
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
  title = isOrder ? "销售订单" : props.dispatch ? "发货" : "销售出库";
const linkedOrder = computed(() =>
  isOrder
    ? order.value
    : store.state.orders.find((o) => o.id === shipment.value.orderId),
);
const customer = computed(() =>
  customers.find((s) => s.id === linkedOrder.value?.customerId),
);
const shippingInfo = ref(clone(props.shipment?.shipping || defaultShippingInfo(linkedOrder.value)));
const printableShipment = computed(() => ({ ...shipment.value, shipping: shippingInfo.value }));
const dispatchFields = computed(() => [
  ["订单编号", linkedOrder.value?.code], ["客户名称", orderCustomerName(linkedOrder.value)],
]);
const totals = computed(() => orderTotals(order.value)),
  shippedTotals = computed(() =>
    shipmentTotals(shipment.value, linkedOrder.value),
  );
const baseFields = computed(() => [
  ...(!isOrder ? [["客户名称", orderCustomerName(linkedOrder.value)], ["客户编码", customer.value?.code]] : []),
  [isOrder ? "联系人" : "客户联系人姓名", customer.value?.contact],
  [isOrder ? "联系人手机" : "客户联系人手机", customer.value?.phone],
]);
const linkedShipments = computed(() =>
  store.state.shipments.filter((r) => r.orderId === order.value.id),
);
const linkedPayments = computed(() =>
  store.state.payments.filter((r) => r.orderId === order.value.id),
);
const linkedReturns = computed(() =>
  store.state.returns.filter((r) => r.orderId === order.value.id),
);
const locked = computed(
  () =>
    linkedShipments.value.length > 0 ||
    linkedPayments.value.length > 0 ||
    linkedReturns.value.length > 0,
);
const availableOrders = computed(() =>
  store.state.orders.filter(
    (o) =>
      o.status === "审批通过" &&
      o.lines.some(
        (l) =>
          remainingQuantity(store.state, o.id, l.id, shipment.value.id) > 0,
      ) &&
      [o.name, o.code].join().includes(pickerQuery.value),
  ),
);
const availableProducts = computed(() =>
  products.filter(
    (p) =>
      (isOrder ||
        linkedOrder.value?.lines.some(
          (l) =>
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
  ),
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
  order.value.customerId = id;
  const s = customers.find((s) => s.id === id)!;
  order.value.customerName = s.name;
  order.value.name = `${s.name}-第${store.state.orders.filter((o) => o.customerId === id).length + 1}份销售订单`;
  picker.value = "";
  dirty.value = true;
}
function useCustomerName() {
  const name = pickerQuery.value.trim();
  if (!name) return void ElMessage.warning("请填写客户名称");
  if (name.length > 100) return void ElMessage.warning("客户名称不能超过100字");
  const existing = customers.find(c => c.name === name);
  if (existing) return chooseCustomer(existing.id);
  order.value.customerId = "";
  order.value.customerName = name;
  order.value.name = `${name}-销售订单`;
  picker.value = "";
  dirty.value = true;
}
function chooseOrder(o: SalesOrder) {
  shipment.value.orderId = o.id;
  shippingInfo.value = defaultShippingInfo(o);
  shipment.value.warehouse = o.warehouse;
  shipment.value.lines = o.lines
    .filter(
      (l) => remainingQuantity(store.state, o.id, l.id, shipment.value.id) > 0,
    )
    .map((l) => ({
      lineId: l.id,
      arrived: remainingQuantity(store.state, o.id, l.id, shipment.value.id),
      location: "",
    }));
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
          arrived: remainingQuantity(
            store.state,
            shipment.value.orderId,
            line.id,
            shipment.value.id,
          ),
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
      const p = products.find((p) => p.id === id)!;
      order.value.lines.push({
        id: crypto.randomUUID(),
        productId: id,
        quantity: 1,
        price: levelPrice(p, order.value.priceLevel),
        listPrice: levelPrice(p, order.value.priceLevel),
        tax: p.tax,
      });
    }
  productSelection.value = [];
  picker.value = "";
  dirty.value = true;
}
function productValue(id: string | undefined, key: string) {
  const p = products.find((p) => p.id === id);
  return p ? p[key as keyof typeof p] : "";
}
function productFor(lineId: string) {
  return products.find(
    (p) =>
      p.id === linkedOrder.value?.lines.find((l) => l.id === lineId)?.productId,
  );
}
function orderLine(lineId: string) {
  return linkedOrder.value?.lines.find((l) => l.id === lineId);
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
  syncDispatchFont();
  void document.fonts.ready.then(syncDispatchFont);
  window.addEventListener('resize', syncDispatchFont);
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
    quantity: contentWidth('数量', lines.map(l => orderLine(l.lineId)?.quantity)),
    pending: contentWidth('待出库数量', lines.map(l => remainingQuantity(store.state, shipment.value.orderId, l.lineId, shipment.value.id))),
    dispatch: contentWidth('本次发货数量', lines.map(l => l.arrived), 128, 56),
    remark: contentWidth('备注', lines.map(l => l.remark || '填写本产品发货备注'), 156, 24),
  };
});
const dispatchTableWidth = computed(() => Object.values(dispatchWidths.value).reduce((sum, width) => sum + width, 2));
function openPicker(name: string) {
  pickerQuery.value = "";
  productSelection.value = [];
  picker.value = name;
}
function save() {
  try {
    if (isOrder) store.saveOrder(order.value);
    else {
      if (!props.shipment?.confirmed) shipment.value.shipping = clone({ ...shippingInfo.value, purchaseNumber: linkedOrder.value?.purchaseNumber || "" });
      store.saveShipment(shipment.value);
    }
    dirty.value = false;
    ElMessage.success(
      isOrder
        ? "已提交，等待审批"
        : shipment.value.confirmed
          ? "出库及收货信息已保存"
          : `已保存${shipment.value.tag}单据`,
    );
    emit("saved");
  } catch (e) {
    ElMessage.error((e as Error).message);
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

async function approve(passed: boolean) {
  try {
    let reason = "";
    if (!passed) {
      const r = await ElMessageBox.prompt("请填写不通过原因", "合同审批", {
        inputValidator: (v) => !!v?.trim() || "请填写原因",
      });
      reason = r.value;
    }
    store.approve(order.value.id, passed, reason);
    ElMessage.success("审批结果已保存");
    emit("saved");
  } catch (e) {
    if (e instanceof Error) ElMessage.error(e.message);
  }
}
async function uploadContract(event: Event) {
  await attach(event);
  try {
    store.saveAttachments(order.value.id, order.value.attachments);
    ElMessage.success("合同附件已保存");
    emit("saved");
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}
async function attach(event: Event, images = false) {
  const input = event.target as HTMLInputElement;
  for (const file of Array.from(input.files || [])) {
    if (file.size > 2 * 1024 * 1024) {
      ElMessage.warning("本地预览附件每个不超过 2MB");
      continue;
    }
    if (images && !file.type.startsWith("image/")) {
      ElMessage.warning("请选择图片");
      continue;
    }
    const url = await new Promise<string>((resolve, reject) => {
      const r = new FileReader();
      r.onload = () => resolve(String(r.result));
      r.onerror = reject;
      r.readAsDataURL(file);
    }).catch(() => {
      ElMessage.error("文件读取失败");
      return "";
    });
    if (url) {
      const target: Attachment[] = images
        ? shipment.value.photos
        : order.value.attachments;
      target.push({ name: file.name, url });
      dirty.value = true;
    }
  }
  input.value = "";
}
const quick = ref(false),
  quickText = ref("");
function quickFill() {
  const rows = quickText.value
    .trim()
    .split(/\r?\n/)
    .map((l) => l.split(/[\t,，]/));
  const changes = rows.map((r) => ({
    p: products.find((p) => p.code === r[0]?.trim()),
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
          price: levelPrice(r.p!, order.value.priceLevel),
          listPrice: levelPrice(r.p!, order.value.priceLevel),
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
    width="820px"
    top="3vh"
    :fullscreen="full"
    class="purchase-dialog"
    :class="{ 'sales-dispatch-dialog': props.dispatch, 'sales-order-dialog': isOrder }"
    :close-on-click-modal="false"
    :before-close="close"
    destroy-on-close
  >
    <template #header
      ><div class="purchase-dialog-heading">
        <span
          >{{ title
          }}<el-tag
            v-if="isOrder && order.code"
            size="small"
            :type="
              order.status === '审批通过'
                ? 'success'
                : order.status === '不通过'
                  ? 'danger'
                  : 'warning'
            "
            >{{ order.status }}</el-tag
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
        {{ isOrder ? "订单信息" : props.dispatch ? "订单信息" : "销售订单信息" }}
      </h3>
      <p class="purchase-tip">
        {{
          isOrder
            ? "使用Tips：销售订单通过标签页，可以一站式查看销售业务流程"
            : "选择销售订单后自动带入客户和产品明细；确认后计入已出库数量。"
        }}
      </p>
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
        <el-form-item :label="isOrder ? '客户名称' : '选择销售订单'" required
          ><el-button
            class="purchase-picker-button"
            :disabled="!isOrder && !!shipment.code"
            @click="openPicker(isOrder ? 'customer' : 'order')"
            >{{
              isOrder
                ? orderCustomerName(order) || "选择或填写客户名称"
                : linkedOrder?.name || "选择数据"
            }}</el-button
          ></el-form-item
        >
        <template v-if="!isOrder"
          ><el-form-item label="销售订单名称"
            ><div class="purchase-read">
              {{ linkedOrder?.name || "暂无内容" }}
            </div></el-form-item
          ><el-form-item label="销售订单编号"
            ><div class="purchase-read">
              {{ linkedOrder?.code || "暂无内容" }}
            </div></el-form-item
          ></template
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
        <el-form-item v-if="!isOrder" label="销售负责人">
          <div class="purchase-read">{{ linkedOrder?.owner || "暂无内容" }}</div>
        </el-form-item>
        <el-form-item v-if="!isOrder" label="销售归属部门"
          ><el-select
            v-if="isOrder"
            v-model="order.department"
            filterable
            allow-create
            ><el-option
              v-for="n in ['销售部', '业务部', '生产部', '市场部']"
              :key="n"
              :value="n"
          /></el-select>
          <div v-else class="purchase-read">
            {{ linkedOrder?.department || "暂无内容" }}
          </div></el-form-item
        >
        <el-form-item :label="isOrder ? '销售订单编号' : '销售出库单编号'"
          ><div class="purchase-read">
            {{ (isOrder ? order.code : shipment.code) || "自动生成无需填写" }}
          </div></el-form-item
        >
      </el-form>
      <h3 class="purchase-section">
        产品明细
      </h3>
      <el-form v-if="!isOrder && !props.dispatch" label-position="top">
        <el-form-item label="出库仓库"><div class="purchase-read">{{ shipment.warehouse || "选择订单后自动带入" }}</div></el-form-item>
      </el-form>
      <el-form
        v-if="!isOrder && !props.dispatch"
        label-position="top"
        :disabled="view || !!props.shipment?.confirmed"
      >
        <el-form-item label="产品出库标签" required
          ><el-radio-group v-model="shipment.tag" aria-label="产品出库标签"
            ><el-radio-button
              v-for="tag in ['出库', '库存冻结', '计划出库']"
              :key="tag"
              :value="tag"
              >{{ tag }}</el-radio-button
            ></el-radio-group
          ></el-form-item
        >
      </el-form>
      <div v-if="!props.dispatch" class="purchase-field-label">
        <span>*</span> 产品明细
      </div>
      <el-table v-if="isOrder" :data="order.lines" border class="purchase-lines sales-order-products" empty-text="点击“添加”选择销售产品">
        <el-table-column type="index" width="45" />
        <el-table-column v-for="f in [{key:'brand',label:'品牌',width:100},{key:'name',label:'型号',width:150},{key:'model',label:'封装',width:110}]" :key="f.key" :label="f.label" :min-width="f.width">
          <template #default="{row}">{{ productValue(row.productId, f.key) }}</template>
        </el-table-column>
        <el-table-column label="单价" width="150"><template #default="{row}"><el-input-number v-model="row.price" :disabled="view" :min="0" :max="10000000" :precision="2" controls-position="right" aria-label="单价" /></template></el-table-column>
        <el-table-column label="数量" width="150"><template #default="{row}"><el-input-number v-model="row.quantity" :disabled="view" :min="1" :max="100000000" :precision="0" controls-position="right" aria-label="数量" /></template></el-table-column>
        <el-table-column label="金额" width="120"><template #default="{row}">{{ money(row.price * row.quantity) }}</template></el-table-column>
        <el-table-column label="可用库存" width="120"><template #default="{row}">{{ availableStock(store.state, row.productId, order.warehouse) }}</template></el-table-column>
        <el-table-column v-if="!view" fixed="right" label="操作" width="90" align="center" class-name="sales-order-product-actions">
          <template #default="{$index}"><el-button text type="danger" :icon="Delete" aria-label="删除产品" @click="order.lines.splice($index,1); dirty=true" /></template>
        </el-table-column>
      </el-table>
      <el-table
        v-else-if="!props.dispatch"
        :data="shipment.lines"
        border
        class="purchase-lines"
        empty-text="请先选择销售订单"
      >
        <el-table-column type="index" width="45" /><el-table-column
          label="产品名称"
          width="145"
          ><template #default="{ row }">{{
            productFor(row.lineId)?.name
          }}</template></el-table-column
        >
        <el-table-column
          v-for="f in [
            { key: 'code', label: '产品编码' },
            { key: 'brand', label: '品牌' },
            { key: 'model', label: '规格型号' },
            { key: 'unit', label: '单位' },
          ]"
          :key="f.key"
          :label="f.label"
          :width="f.key === 'model' ? 190 : 100"
          ><template #default="{ row }">{{
            productValue(productFor(row.lineId)?.id, f.key)
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
              shipment.orderId,
              row.lineId,
              shipment.id,
            )
          }}</template></el-table-column
        >
        <el-table-column label="当前可用库存数量" width="140"
          ><template #default="{ row }">{{
            availableStock(
              store.state,
              productFor(row.lineId)?.id || "",
              shipment.warehouse,
              shipment.id,
            )
          }}</template></el-table-column
        >
        <el-table-column label="本次出库数量" width="160"
          ><template #default="{ row }"
            ><el-input-number
              v-model="row.arrived"
              :disabled="view || !!props.shipment?.confirmed"
              :min="0"
              :max="
                remainingQuantity(
                  store.state,
                  shipment.orderId,
                  row.lineId,
                  shipment.id,
                )
              "
              :precision="0"
              controls-position="right"
              aria-label="本次出库数量" /></template
        ></el-table-column>
        <el-table-column label="出库仓位" width="145"
          ><template #default="{ row }"
            ><el-input
              v-model="row.location"
              :disabled="view || !!props.shipment?.confirmed"
              placeholder="如 HZ-A01"
              aria-label="出库仓位" /></template
        ></el-table-column>
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
        :fit="false"
        :style="{ width: dispatchTableWidth + 'px', maxWidth: '100%' }"
        empty-text="请选择本次发货的产品"
      >
        <el-table-column type="index" :width="dispatchWidths.index" />
        <el-table-column label="品牌" :width="dispatchWidths.brand"
          ><template #default="{ row }">{{
            productFor(row.lineId)?.brand
          }}</template></el-table-column
        >
        <el-table-column label="型号" :width="dispatchWidths.model"
          ><template #default="{ row }">{{
            productFor(row.lineId)?.name
          }}</template></el-table-column
        >
        <el-table-column label="封装" :width="dispatchWidths.package"
          ><template #default="{ row }">{{
            productFor(row.lineId)?.model
          }}</template></el-table-column
        >
        <el-table-column label="数量" :width="dispatchWidths.quantity"
          ><template #default="{ row }">{{
            orderLine(row.lineId)?.quantity
          }}</template></el-table-column
        >
        <el-table-column label="待出库数量" :width="dispatchWidths.pending"
          ><template #default="{ row }">{{
            remainingQuantity(
              store.state,
              shipment.orderId,
              row.lineId,
              shipment.id,
            )
          }}</template></el-table-column
        >
        <el-table-column label="本次发货数量" :width="dispatchWidths.dispatch"
          ><template #default="{ row }"
            ><el-input-number
              v-model="row.arrived"
              :disabled="view || !!props.shipment?.confirmed"
              :min="0"
              :max="
                remainingQuantity(
                  store.state,
                  shipment.orderId,
                  row.lineId,
                  shipment.id,
                )
              "
              :precision="0"
              controls-position="right"
              aria-label="本次发货数量" /></template
        ></el-table-column>
        <el-table-column label="备注" :width="dispatchWidths.remark">
          <template #default="{ row }">
            <el-input v-model="row.remark" :disabled="view || !!props.shipment?.confirmed" maxlength="200" placeholder="填写本产品发货备注" aria-label="产品备注" />
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
          
          <el-form-item label="销售原价总额(含税)/元"
            ><div class="purchase-read">
              {{ money(totals.gross) }}
            </div></el-form-item
          >
          
          
          <el-form-item label="销售订单金额(含税)/元"
            ><div class="purchase-read">
              {{ money(totals.total) }}
            </div></el-form-item
          ><el-form-item label="销售金额合计(不含税)/元"
            ><div class="purchase-read">
              {{ money(totals.net) }}
            </div></el-form-item
          >
        </template>
        <template v-else
          ><el-form-item label="出库产品总数"
            ><div class="purchase-read">
              {{ shippedTotals.quantity }}
            </div></el-form-item
          ><el-form-item label="出库产品销售价总额(含税)/元"
            ><div class="purchase-read">
              {{ money(shippedTotals.total) }}
            </div></el-form-item
          ></template
        >
      </el-form>
      <template v-if="isOrder">
        <div class="purchase-field-label">合同附件上传</div>
        <label v-if="view && order.code" class="purchase-import-file"
          >补充合同附件<input
            type="file"
            multiple
            aria-label="补充合同附件"
            @change="uploadContract"
        /></label>
        <input
          v-if="!view"
          type="file"
          multiple
          aria-label="合同附件上传"
          @change="attach($event)"
        /><small v-if="!view" class="purchase-tip"
          >本地预览：每个文件不超过 2MB。</small
        >
        <div
          v-for="(file, i) in order.attachments"
          :key="i"
          class="purchase-attachment"
        >
          <a :href="file.url" :download="file.name">{{ file.name }}</a
          ><el-button
            v-if="!view"
            text
            type="danger"
            @click="
              () => {
                order.attachments.splice(i, 1);
                dirty = true;
              }
            "
            >移除</el-button
          >
        </div>
        <nav class="purchase-detail-tabs" aria-label="订单关联信息">
          <button
            v-for="name in [
              '订单明细',
              '财务明细',
              '销售出库',
              '销售退货',
              '销售订单执行跟踪',
            ]"
            :key="name"
            :class="{ active: tab === name }"
            @click="tab = name"
          >
            {{ name }}
          </button>
        </nav>
        <template v-if="tab === '订单明细'">
          <h3 class="purchase-section">送货计划</h3>
          <el-form label-position="top" :disabled="view"
            ><el-form-item label="送货地址"
              ><el-input
                v-model="order.address"
                placeholder="省 / 市 / 区 / 详细地址" /></el-form-item
          ></el-form>
          <el-table :data="order.arrivals" border
            ><el-table-column label="送货批次" width="125"
              ><template #default="{ row }"
                ><el-select v-model="row.batch" :disabled="view"
                  ><el-option
                    v-for="n in ['整批', '第一批', '第二批', '第三批']"
                    :key="n"
                    :value="n" /></el-select></template></el-table-column
            ><el-table-column label="计划送货日期" width="200"
              ><template #default="{ row }"
                ><el-date-picker
                  v-model="row.date"
                  :disabled="view"
                  value-format="YYYY-MM-DD" /></template></el-table-column
            ><el-table-column label="计划送货内容备注" min-width="180"
              ><template #default="{ row }"
                ><el-input
                  v-model="row.note"
                  :disabled="view" /></template></el-table-column
            ><el-table-column v-if="!view" width="65"
              ><template #default="{ $index }"
                ><el-button
                  :icon="Delete"
                  text
                  aria-label="删除送货计划"
                  @click="
                    () => {
                      order.arrivals.splice($index, 1);
                      dirty = true;
                    }
                  " /></template></el-table-column
          ></el-table>
          <el-button
            v-if="!view"
            text
            type="primary"
            :icon="Plus"
            @click="
              () => {
                order.arrivals.push({
                  batch: '整批',
                  date: order.delivery,
                  note: '',
                });
                dirty = true;
              }
            "
            >添加送货计划</el-button
          >
          <h3 class="purchase-section">财务条款</h3>
          <el-form label-position="top" :disabled="view"
            ><el-form-item label="结算期限"
              ><el-input v-model="order.term" /></el-form-item
          ></el-form>
          <el-table :data="order.payments" border
            ><el-table-column label="收款项" width="130"
              ><template #default="{ row }"
                ><el-select v-model="row.name" :disabled="view"
                  ><el-option
                    v-for="n in ['整批货款', '定金', '尾款']"
                    :key="n"
                    :value="n" /></el-select></template></el-table-column
            ><el-table-column label="收款比例 %" width="140"
              ><template #default="{ row }"
                ><el-input-number
                  v-model="row.ratio"
                  :disabled="view"
                  :min="0"
                  :max="100"
                  controls-position="right" /></template></el-table-column
            ><el-table-column label="收款金额/元" width="140"
              ><template #default="{ row }">{{
                money((totals.total * row.ratio) / 100)
              }}</template></el-table-column
            ><el-table-column label="收款方式" width="145"
              ><template #default="{ row }"
                ><el-select v-model="row.method" :disabled="view"
                  ><el-option
                    v-for="n in ['银行转账', '现金', '承兑汇票']"
                    :key="n"
                    :value="n" /></el-select></template></el-table-column
            ><el-table-column label="计划收款日期" width="190"
              ><template #default="{ row }"
                ><el-date-picker
                  v-model="row.date"
                  :disabled="view"
                  value-format="YYYY-MM-DD" /></template></el-table-column
            ><el-table-column label="备注" width="180"
              ><template #default="{ row }"
                ><el-input
                  v-model="row.note"
                  :disabled="view" /></template></el-table-column
            ><el-table-column v-if="!view" fixed="right" width="65"
              ><template #default="{ $index }"
                ><el-button
                  :icon="Delete"
                  text
                  aria-label="删除收款计划"
                  @click="
                    () => {
                      order.payments.splice($index, 1);
                      dirty = true;
                    }
                  " /></template></el-table-column
          ></el-table>
          <el-button
            v-if="!view"
            text
            type="primary"
            :icon="Plus"
            @click="
              () => {
                order.payments.push({
                  name: '整批货款',
                  ratio: 100,
                  method: '银行转账',
                  date: order.delivery,
                  note: '',
                });
                dirty = true;
              }
            "
            >添加收款计划</el-button
          >
        </template>
        <template v-else-if="tab === '财务明细'"
          ><el-descriptions :column="2" border
            ><el-descriptions-item label="发票抬头">{{
              customer?.name
            }}</el-descriptions-item
            ><el-descriptions-item label="税种"
              >增值税专用发票</el-descriptions-item
            ><el-descriptions-item label="银行账户">{{
              customer?.account
            }}</el-descriptions-item
            ><el-descriptions-item label="开户银行">{{
              customer?.bank
            }}</el-descriptions-item></el-descriptions
          ><el-table :data="linkedPayments" border
            ><el-table-column prop="date" label="收款日期" /><el-table-column
              prop="method"
              label="收款方式" /><el-table-column
              prop="amount"
              label="收款金额/元" /><el-table-column
              prop="note"
              label="备注" /></el-table
        ></template>
        <template v-else-if="tab === '销售出库'"
          ><el-table :data="linkedShipments" border
            ><el-table-column
              prop="code"
              label="销售出库单编号"
            /><el-table-column prop="warehouse" label="仓库" /><el-table-column
              label="出库产品总数"
              ><template #default="{ row }">{{
                shipmentTotals(row, order).quantity
              }}</template></el-table-column
            ><el-table-column label="状态"
              ><template #default="{ row }">{{
                row.confirmed ? "已出库" : "待出库"
              }}</template></el-table-column
            ></el-table
          ><el-button
            v-if="view && order.status === '审批通过'"
            type="primary"
            @click="emit('ship', order)"
            >发货</el-button
          ></template
        >
        <el-table v-else-if="tab === '销售退货'" :data="linkedReturns" border
          ><el-table-column prop="date" label="退货日期" /><el-table-column
            label="产品"
            ><template #default="{ row }">{{
              productFor(row.lineId)?.name
            }}</template></el-table-column
          ><el-table-column prop="quantity" label="退货数量" /><el-table-column
            prop="reason"
            label="原因"
        /></el-table>
        <el-table v-else :data="order.lines" border
          ><el-table-column label="产品"
            ><template #default="{ row }">{{
              productFor(row.id)?.name
            }}</template></el-table-column
          ><el-table-column prop="quantity" label="销售数量" /><el-table-column
            label="待收货数量"
            ><template #default="{ row }">{{
              remainingQuantity(store.state, order.id, row.id)
            }}</template></el-table-column
          ><el-table-column label="已确认出库"
            ><template #default="{ row }">{{
              store.state.shipments
                .filter((r) => r.orderId === order.id && r.confirmed)
                .reduce(
                  (s, r) =>
                    s +
                    r.lines
                      .filter((l) => l.lineId === row.id)
                      .reduce((n, l) => n + l.arrived, 0),
                  0,
                )
            }}</template></el-table-column
          ></el-table
        >
        <template v-if="view"
          ><h3 class="purchase-section">合同审批</h3>
          <el-descriptions :column="2" border
            ><el-descriptions-item label="审批结果">{{
              order.status
            }}</el-descriptions-item
            ><el-descriptions-item label="审批人">{{
              order.approver || "—"
            }}</el-descriptions-item
            ><el-descriptions-item label="审批时间">{{
              order.approvedAt || "—"
            }}</el-descriptions-item
            ><el-descriptions-item label="不通过原因">{{
              order.reason || "—"
            }}</el-descriptions-item></el-descriptions
          ></template
        >
      </template>
      <template v-else>
        <h3 class="purchase-section">发货信息</h3>
        <el-form label-position="top" class="purchase-form-grid shipping-info-form" size="default" :disabled="view || !!props.shipment?.confirmed">
          <el-form-item label="收货人" :required="shipment.confirmed"><el-input v-model="shippingInfo.recipient" aria-label="收货人" maxlength="80" placeholder="填写本次收货人" /></el-form-item>
          <el-form-item label="收货联系电话" :required="shipment.confirmed"><el-input v-model="shippingInfo.phone" aria-label="收货联系电话" maxlength="40" placeholder="手机或固定电话" /></el-form-item>
          <el-form-item label="收货地址" :required="shipment.confirmed" style="grid-column: 1 / -1"><el-input v-model="shippingInfo.address" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" aria-label="收货地址" maxlength="300" placeholder="省 / 市 / 区、街道及详细地址；自提请填写提货地点" /></el-form-item>
          <el-form-item label="物流公司"><el-input v-model="shippingInfo.carrier" aria-label="物流公司" maxlength="80" placeholder="如顺丰、德邦" /></el-form-item>
          <el-form-item label="物流单号"><el-input v-model="shippingInfo.trackingNumber" aria-label="物流单号" maxlength="100" /></el-form-item>
          <el-form-item label="发货备注" style="grid-column: 1 / -1"><el-input v-model="shippingInfo.remark" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" aria-label="发货备注" maxlength="200" placeholder="送货时间要求、包装或签收注意事项" /></el-form-item>
        </el-form>
        <DeliveryNoteActions :order="linkedOrder" :shipment="printableShipment" />
        <h3 class="purchase-section">出库确认</h3>
        <el-form
          label-position="top"
          class="purchase-form-grid"
          :disabled="view"
        >
          <el-form-item label="产品出库确认" required
            ><el-checkbox
              v-model="shipment.confirmed"
              :disabled="!!props.shipment?.confirmed || shipment.tag !== '出库'"
              aria-label="产品出库确认"
              >确认</el-checkbox
            ></el-form-item
          >
          <el-form-item label="出库时间" :required="shipment.confirmed"
            ><el-date-picker
              v-model="shipment.shippedAt"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm"
              :disabled="!!props.shipment?.confirmed"
              aria-label="出库时间"
          /></el-form-item>
          <el-form-item label="出库员"
            ><el-input
              v-model="shipment.keeper"
              :disabled="!!props.shipment?.confirmed"
          /></el-form-item>
          <el-form-item label="库管归属部门"
            ><el-input
              v-model="shipment.department"
              :disabled="!!props.shipment?.confirmed"
          /></el-form-item>
        </el-form>
        <h3 class="purchase-section">拍照</h3>
        <input
          v-if="!view && !props.shipment?.confirmed"
          type="file"
          accept="image/*"
          multiple
          aria-label="出库照片"
          @change="attach($event, true)"
        />
        <div
          v-for="(file, i) in shipment.photos"
          :key="i"
          class="purchase-attachment"
        >
          <a :href="file.url" :download="file.name">{{ file.name }}</a
          ><el-button
            v-if="!view && !props.shipment?.confirmed"
            text
            @click="shipment.photos.splice(i, 1)"
            >移除</el-button
          >
        </div>
        <h3 class="purchase-section">收货确认</h3>
        <el-form
          label-position="top"
          class="purchase-form-grid"
          :disabled="view"
        >
          <el-form-item label="客户收货确认"
            ><el-checkbox
              v-model="shipment.customerConfirmed"
              :disabled="!shipment.confirmed"
              aria-label="客户收货确认"
              >确认</el-checkbox
            ></el-form-item
          >
          <el-form-item
            label="收货确认时间"
            :required="shipment.customerConfirmed"
            ><el-date-picker
              v-model="shipment.customerReceivedAt"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm"
              aria-label="收货确认时间"
          /></el-form-item>
        </el-form>
      </template>
    </div>
    <template #footer
      ><div class="purchase-form-footer">
        <template v-if="view"
          ><el-button
            v-if="!isOrder || !locked"
            type="primary"
            @click="view = false"
            >编辑</el-button
          ><el-button
            v-if="isOrder && order.status === '待审批'"
            type="success"
            @click="approve(true)"
            >审批通过</el-button
          ><el-button
            v-if="isOrder && order.status === '待审批'"
            type="danger"
            plain
            @click="approve(false)"
            >不通过</el-button
          ><span v-if="isOrder && locked" class="purchase-tip"
            >已有业务记录，订单基础数据已锁定</span
          ></template
        ><el-button v-else type="primary" @click="save">提交</el-button
        ><el-button @click="close()">{{ view ? "关闭" : "取消" }}</el-button>
      </div></template
    >
  </el-dialog>
  <el-dialog
    :model-value="!!picker"
    :title="
      picker === 'customer'
        ? '选择客户'
        : picker === 'product'
          ? '选择产品'
          : '选择销售订单'
    "
    width="min(880px,94vw)"
    append-to-body
    class="purchase-dialog"
    @close="picker = ''"
  >
    <el-input
      v-model="pickerQuery"
      :placeholder="picker === 'customer' ? '搜索已有客户，或输入新客户名称' : '搜索名称、编码或规格型号'"
      clearable
      class="purchase-picker-search"
    />
    <div v-if="picker === 'customer'" class="sales-new-customer">
      <span>列表中没有该客户？输入客户名称后可直接用于订单，客户资料可稍后维护。</span>
      <el-button type="primary" plain :disabled="!pickerQuery.trim()" @click="useCustomerName">使用此客户名称</el-button>
    </div>
    <el-table
      v-if="picker === 'customer'"
      :data="
        customers.filter((s) => [s.name, s.code].join().includes(pickerQuery))
      "
      border
      @row-dblclick="chooseCustomer($event.id)"
      ><el-table-column prop="name" label="客户名称" /><el-table-column
        prop="code"
        label="客户编码"
        width="125"
      /><el-table-column
        prop="contact"
        label="联系人"
        width="110"
      /><el-table-column
        prop="phone"
        label="手机"
        width="150"
      /><el-table-column width="80"
        ><template #default="{ row }"
          ><el-button link type="primary" @click="chooseCustomer(row.id)"
            >选择</el-button
          ></template
        ></el-table-column
      ></el-table
    >
    <el-table
      v-else-if="picker === 'order'"
      :data="availableOrders"
      border
      max-height="420"
      ><el-table-column
        prop="name"
        label="销售订单名称"
        min-width="230"
      /><el-table-column
        prop="code"
        label="订单编号"
        min-width="165"
      /><el-table-column
        prop="warehouse"
        label="出库仓库"
        width="100"
      /><el-table-column width="80"
        ><template #default="{ row }"
          ><el-button link type="primary" @click="chooseOrder(row)"
            >选择</el-button
          ></template
        ></el-table-column
      ></el-table
    >
    <el-table
      v-else
      :data="availableProducts"
      border
      @selection-change="
        productSelection = $event.map((p: { id: string }) => p.id)
      "
      ><el-table-column type="selection" width="45" /><el-table-column
        prop="name"
        label="产品名称" /><el-table-column
        prop="code"
        label="产品编码"
        width="100" /><el-table-column
        prop="model"
        label="规格型号"
        min-width="180" /><el-table-column
        prop="price"
        label="含税单价"
        width="100"
    /></el-table>
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
</template>

<style>
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
</style>
