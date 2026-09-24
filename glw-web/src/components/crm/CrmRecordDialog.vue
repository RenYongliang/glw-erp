<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { FullScreen } from "@element-plus/icons-vue";
import CrmFields from "./CrmFields.vue";
import { useCrmStore, crmFields } from "../../stores/crm";
import type { CrmKind, CrmRow } from "../../domain/crm";
import orderData from "../../data/orderBoards.json";
const props = defineProps<{
  kind: CrmKind;
  row?: CrmRow;
  customer?: CrmRow;
  detail?: boolean;
}>();
const visible = defineModel<boolean>({ required: true });
const emit = defineEmits<{ saved: [row: CrmRow]; followup: [row: CrmRow] }>();
const store = useCrmStore();
const draft = ref<CrmRow>({ id: "", values: {}, contacts: [] });
const readonly = ref(false);
const full = ref(false);
const subtab = ref("finance");
const error = ref("");
const picker = ref(false);
const pickerSearch = ref("");
const selectedCustomer = ref<CrmRow>();
const dirtyStart = ref("");
const fields = computed(() => crmFields[props.kind]);
const basic = computed(() =>
  fields.value.filter((f) =>
    props.kind === "customers"
      ? [
          "code",
          "name",
          "category",
          "source",
          "tag",
          "priceLevel",
          "settlement",
          "credit",
          "owner",
          "department",
          "address",
        ].includes(f.key)
      : !["separator", "linkfield", "location"].includes(f.kind),
  ),
);
const finance = computed(() =>
  fields.value.filter((f) =>
    [
      "invoiceTitle",
      "taxNumber",
      "taxType",
      "taxRate",
      "invoicePhone",
      "email",
      "bank",
      "bankAccount",
    ].includes(f.key),
  ),
);
const contacts = computed(() =>
  fields.value.filter((f) =>
    ["contactName", "contactPhone", "contactPosition"].includes(f.key),
  ),
);
const customerChoices = computed(() =>
  store.state.customers.filter((c) =>
    Object.values(c.values).some((v) => String(v).includes(pickerSearch.value)),
  ),
);
const relatedFollowups = computed(() =>
  store.state.followups.filter((r) => r.values.customerId === draft.value.id),
);
const relatedOrders = computed(() => [
  ...orderData.sales.orders
    .filter((r) => r.partner === draft.value.values.name)
    .map((r) => ({ id: r.id, name: r.name, date: r.signed, amount: r.amount })),
  ...store.state.sales
    .filter((r) => r.customerId === draft.value.id)
    .map((r) => ({ ...r, amount: r.quantity * r.price })),
]);
function chooseCustomer(customer: CrmRow) {
  Object.assign(draft.value.values, {
    customerId: customer.id,
    customerName: customer.values.name,
    customerCode: customer.values.code,
  });
  picker.value = false;
}
watch(visible, (value) => {
  if (!value) return;
  readonly.value = !!props.detail;
  subtab.value = "finance";
  error.value = "";
  full.value = false;
  if (props.row) draft.value = JSON.parse(JSON.stringify(props.row));
  else {
    const values = Object.fromEntries(
      fields.value
        .filter((f) => !["separator", "subform", "linkquery"].includes(f.kind))
        .map((f) => [f.key, ""]),
    );
    Object.assign(values, { owner: "管理员", department: "销售部" });
    if (props.kind === "followups") {
      const d = new Date(Date.now() - new Date().getTimezoneOffset() * 60000);
      values.time = d.toISOString().slice(0, 16).replace("T", " ");
    }
    draft.value = { id: "", values, contacts: [] };
    if (props.customer) chooseCustomer(props.customer);
  }
  dirtyStart.value = JSON.stringify(draft.value);
});
async function close(done?: () => void) {
  if (!readonly.value && JSON.stringify(draft.value) !== dirtyStart.value) {
    try {
      await ElMessageBox.confirm("尚有未提交的修改，确定关闭吗？", "放弃修改", {
        confirmButtonText: "放弃修改",
        cancelButtonText: "继续编辑",
        type: "warning",
      });
    } catch {
      return;
    }
  }
  visible.value = false;
  done?.();
}
function submit() {
  try {
    const saved = store.save(props.kind, draft.value);
    visible.value = false;
    emit("saved", saved);
    ElMessage.success("提交成功");
  } catch (e) {
    error.value = (e as Error).message;
  }
}
function copy() {
  draft.value.id = "";
  if (props.kind === "customers") {
    draft.value.values.code = "";
    draft.value.values.name = `${draft.value.values.name}（副本）`;
  }
  readonly.value = false;
}
async function quickCustomer() {
  try {
    const { value } = await ElMessageBox.prompt("客户名称", "新增客户", {
      inputValidator: (v) => !!v?.trim() || "请输入客户名称",
      confirmButtonText: "创建并选择",
      cancelButtonText: "取消",
    });
    const row = store.save("customers", {
      id: "",
      values: {
        name: value.trim(),
        owner: "管理员",
        department: "销售部",
        category: "潜在客户",
      },
      contacts: [],
    });
    chooseCustomer(row);
  } catch (e) {
    if (e instanceof Error) ElMessage.error(e.message);
  }
}
</script>
<template>
  <el-dialog
    v-model="visible"
    :title="kind === 'customers' ? '客户' : '跟进记录'"
    width="780px"
    top="24px"
    :fullscreen="full"
    class="crm-record-dialog"
    :close-on-click-modal="false"
    :before-close="close"
    destroy-on-close
  >
    <template #header
      ><div class="crm-dialog-heading">
        <strong>{{ kind === "customers" ? "客户" : "跟进记录" }}</strong
        ><el-button
          text
          :icon="FullScreen"
          aria-label="表单全屏"
          @click="full = !full"
        /></div
    ></template>
    <div v-if="readonly" class="crm-detail-actions">
      <el-button type="primary" @click="readonly = false">编辑</el-button
      ><el-button @click="copy">复制</el-button
      ><el-button v-if="kind === 'customers'" @click="emit('followup', draft)"
        >客户跟进</el-button
      >
    </div>
    <el-form label-position="top" @submit.prevent="submit">
      <h3 class="crm-form-section">
        {{ kind === "customers" ? "客户基本信息" : "客户跟进记录" }}
      </h3>
      <template v-if="kind === 'followups'">
        <CrmFields
          v-model="draft.values"
          :fields="basic.filter((f) => f.key === 'content')"
          :readonly="readonly"
        />
        <el-form-item label="选择客户" required
          ><el-button
            class="crm-customer-picker"
            :disabled="readonly"
            @click="
              () => {
                picker = true;
                pickerSearch = '';
                selectedCustomer = undefined;
              }
            "
            >{{ draft.values.customerName || "＋ 选择数据" }}</el-button
          ></el-form-item
        >
      </template>
      <CrmFields
        v-model="draft.values"
        :fields="
          kind === 'followups'
            ? basic.filter((f) => f.key !== 'content')
            : basic
        "
        :readonly="readonly"
      />
      <el-tabs
        v-if="kind === 'customers'"
        v-model="subtab"
        type="card"
        class="crm-form-tabs"
      >
        <el-tab-pane label="财务信息" name="finance"
          ><CrmFields
            v-model="draft.values"
            :fields="finance"
            :readonly="readonly"
        /></el-tab-pane>
        <el-tab-pane label="联系人信息" name="contacts">
          <CrmFields
            v-model="draft.values"
            :fields="contacts"
            :readonly="readonly"
          />
          <h4>其他联系人信息</h4>
          <el-table :data="draft.contacts" border empty-text="暂无联系人"
            ><el-table-column label="联系人姓名"
              ><template #default="{ row }"
                ><el-input
                  v-model="row.name"
                  :disabled="readonly"
                  aria-label="其他联系人姓名" /></template></el-table-column
            ><el-table-column label="联系人手机"
              ><template #default="{ row }"
                ><el-input
                  v-model="row.phone"
                  :disabled="readonly"
                  aria-label="其他联系人手机" /></template></el-table-column
            ><el-table-column label="联系人职位"
              ><template #default="{ row }"
                ><el-input
                  v-model="row.position"
                  :disabled="readonly"
                  aria-label="其他联系人职位" /></template></el-table-column
            ><el-table-column v-if="!readonly" width="65"
              ><template #default="{ $index }"
                ><el-button
                  link
                  type="danger"
                  @click="draft.contacts.splice($index, 1)"
                  >删除</el-button
                ></template
              ></el-table-column
            ></el-table
          >
          <el-button
            v-if="!readonly"
            class="crm-add-contact"
            @click="draft.contacts.push({ name: '', phone: '', position: '' })"
            >＋ 添加联系人</el-button
          >
        </el-tab-pane>
        <el-tab-pane label="跟进记录" name="followups"
          ><el-table :data="relatedFollowups" border empty-text="暂无跟进记录"
            ><el-table-column
              prop="values.content"
              label="跟进内容记录" /><el-table-column
              prop="values.time"
              label="跟进时间" /><el-table-column
              prop="values.method"
              label="跟进方式" /><el-table-column
              prop="values.stage"
              label="跟进内容" /></el-table
        ></el-tab-pane>
        <el-tab-pane label="销售订单" name="orders"
          ><el-table :data="relatedOrders" border empty-text="暂无销售订单"
            ><el-table-column prop="name" label="销售订单" /><el-table-column
              prop="date"
              label="订单日期" /><el-table-column
              prop="amount"
              label="订单金额/元" /></el-table
        ></el-tab-pane>
      </el-tabs>
      <el-alert
        v-if="error"
        :title="error"
        type="error"
        show-icon
        :closable="false"
        class="crm-form-error"
      />
    </el-form>
    <template #footer
      ><el-button
        v-if="!readonly"
        type="primary"
        class="crm-submit"
        @click="submit"
        >提交</el-button
      ><el-button @click="close()">{{
        readonly ? "关闭" : "取消"
      }}</el-button></template
    >
  </el-dialog>
  <el-dialog
    v-model="picker"
    title="选择客户"
    width="850px"
    append-to-body
    class="crm-picker-dialog"
    ><div class="crm-picker-toolbar">
      <el-input
        v-model="pickerSearch"
        placeholder="搜索客户名称、编码"
        clearable
      /><el-button @click="quickCustomer">＋ 新增客户</el-button>
    </div>
    <el-table
      :data="customerChoices"
      highlight-current-row
      height="350"
      @current-change="selectedCustomer = $event"
      @row-dblclick="chooseCustomer"
      ><el-table-column width="50"
        ><template #default="{ row }"
          ><el-radio
            :model-value="selectedCustomer?.id"
            :value="row.id"
            :aria-label="`选择${row.values.name}`"
            @change="selectedCustomer = row"
            >&nbsp;</el-radio
          ></template
        ></el-table-column
      ><el-table-column prop="values.name" label="客户名称" /><el-table-column
        prop="values.tag"
        label="客户标签" /><el-table-column
        prop="values.category"
        label="客户分类" /><el-table-column
        prop="values.priceLevel"
        label="价格等级" /><el-table-column
        prop="values.owner"
        label="负责人" /></el-table
    ><template #footer
      ><el-button @click="picker = false">取消</el-button
      ><el-button
        type="primary"
        :disabled="!selectedCustomer"
        @click="selectedCustomer && chooseCustomer(selectedCustomer)"
        >确定</el-button
      ></template
    ></el-dialog
  >
</template>
