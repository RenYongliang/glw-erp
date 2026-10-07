<script setup lang="ts">
import { useListPagination } from "../composables/useListPagination";
import { computed, ref, watch, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Plus,
  Upload,
  Download,
  Delete,
  Search,
  Filter,
  Refresh,
  Sort,
  Setting,
  Operation,
  Clock,
} from "@element-plus/icons-vue";
import CrmRecordDialog from "../components/crm/CrmRecordDialog.vue";
import { crmFields, useCrmStore } from "../stores/crm";
import {
  encodeCsv,
  parseCsv,
  type CrmKind,
  type CrmRow,
  type CrmField,
} from "../domain/crm";
import orderData from "../data/orderBoards.json";
import "../assets/crm.css";
const props = defineProps<{ kind: CrmKind }>();
const store = useCrmStore();
const customerPage = computed(() => props.kind === "customers");
const tab = ref("全部客户");
const keyword = ref("");
const filterOpen = ref(false);
const filterField = ref("");
const filterOperator = ref("包含");
const filterText = ref("");
const grouped = ref(true);
const collapsed = ref<string[]>([]);
const selected = ref<string[]>([]);
const sortKey = ref("");
const sortOrder = ref("asc");
const sortOpen = ref(false);
const logsOpen = ref(false);
const editor = ref(false);
const editorKind = ref<CrmKind>(props.kind);
const editing = ref<CrmRow>();
const detail = ref(false);
const followCustomer = ref<CrmRow>();
const importOpen = ref(false);
const importError = ref("");
const importPreview = ref<CrmRow[]>([]);
const fileInput = ref<HTMLInputElement>();
const saleOpen = ref(false);
const saleCustomer = ref<CrmRow>();
const sale = ref({
  name: "",
  date: "",
  product: "MTQ8200-HS2F",
  quantity: 1,
  price: 1400,
});
const reconcileOpen = ref(false);
const fields = computed(() =>
  crmFields[props.kind].filter(
    (f) => !["separator", "subform", "linkquery", "linkfield"].includes(f.kind),
  ),
);
const hidden = ref<string[]>(
  customerPage.value
    ? ["category", "contactName", "contactPhone", "contactPosition"]
    : ["stage"],
);
const columns = computed(() =>
  fields.value.filter((f) => !hidden.value.includes(f.key)),
);
const groupField = computed(() => (customerPage.value ? "category" : "stage"));
const allGroupNames = computed(() =>
  crmFields[props.kind]
    .find((f) => f.key === groupField.value)!
    .options.map((o) => o.value),
);
const filtered = computed(() =>
  store.state[props.kind].filter((row) => {
    if (
      customerPage.value &&
      tab.value !== "全部客户" &&
      row.values.category !==
        (tab.value === "战略客户" ? "战略合作" : tab.value)
    )
      return false;
    if (
      keyword.value &&
      !Object.values(row.values).some((v) =>
        String(v).toLowerCase().includes(keyword.value.trim().toLowerCase()),
      )
    )
      return false;
    if (!filterField.value) return true;
    const value = String(row.values[filterField.value] ?? "");
    if (filterOperator.value === "为空") return !value;
    if (filterOperator.value === "不为空") return !!value;
    if (!filterText.value) return true;
    if (filterOperator.value === "等于") return value === filterText.value;
    if (filterOperator.value === "不等于") return value !== filterText.value;
    return value.toLowerCase().includes(filterText.value.toLowerCase());
  }),
);
const sorted = computed(() =>
  !sortKey.value
    ? filtered.value
    : [...filtered.value].sort((a, b) => {
        const x = a.values[sortKey.value] ?? "",
          y = b.values[sortKey.value] ?? "";
        return (
          (typeof x === "number" && typeof y === "number"
            ? x - y
            : String(x).localeCompare(String(y), "zh-CN")) *
          (sortOrder.value === "asc" ? 1 : -1)
        );
      }),
);
const allGroups = computed(() =>
  grouped.value
    ? [...allGroupNames.value, "未填写"]
        .map((name) => ({
          name,
          rows: sorted.value.filter(
            (r) => (r.values[groupField.value] || "未填写") === name,
          ),
        }))
        .filter((g) => g.rows.length)
    : [{ name: "", rows: sorted.value }],
);
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
watch([tab, keyword, filterField, filterText, filterOperator], () => {
  selected.value = [];
});
onMounted(() => {
  if (store.loadError) ElMessage.warning(store.loadError);
});
function toggleGroup(name: string) {
  collapsed.value = collapsed.value.includes(name)
    ? collapsed.value.filter((n) => n !== name)
    : [...collapsed.value, name];
}
function selectRows(rows: CrmRow[], checked: boolean) {
  selected.value = checked
    ? [...new Set([...selected.value, ...rows.map((r) => r.id)])]
    : selected.value.filter((id) => !rows.some((r) => r.id === id));
}
function openRecord(row?: CrmRow, read = false) {
  editorKind.value = props.kind;
  editing.value = row;
  detail.value = read;
  followCustomer.value = undefined;
  editor.value = true;
}
function follow(row: CrmRow) {
  editor.value = false;
  setTimeout(() => {
    editorKind.value = "followups";
    editing.value = undefined;
    followCustomer.value = row;
    detail.value = false;
    editor.value = true;
  }, 0);
}
async function remove(ids = selected.value) {
  if (!ids.length) return ElMessage.info("请先选择需要删除的数据");
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${ids.length} 条数据吗？`,
      "删除数据",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消" },
    );
    store.remove(props.kind, ids);
    selected.value = [];
    ElMessage.success("已删除");
  } catch (e) {
    if (e instanceof Error) ElMessage.error(e.message);
  }
}
function display(row: CrmRow, f: CrmField) {
  const v = row.values[f.key];
  return f.key === "credit" && v !== "" && v != null
    ? Number(v).toLocaleString("zh-CN", { minimumFractionDigits: 2 })
    : f.key === "taxRate" && v !== "" && v != null
      ? `${v}%`
      : v;
}
function tagColor(field: CrmField, value: unknown) {
  return field.options.find((o) => o.value === value)?.color || "#7c8fa8";
}
function downloadCsv(name: string, rows: unknown[][]) {
  const url = URL.createObjectURL(
    new Blob([encodeCsv(rows)], { type: "text/csv;charset=utf-8" }),
  );
  const a = document.createElement("a");
  a.href = url;
  a.download = `${name}.csv`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
function exportRows() {
  const rows = selected.value.length
    ? sorted.value.filter((r) => selected.value.includes(r.id))
    : sorted.value;
  downloadCsv(customerPage.value ? "客户" : "跟进记录", [
    fields.value.map((f) => f.label),
    ...rows.map((r) => fields.value.map((f) => r.values[f.key])),
  ]);
  ElMessage.success(`已导出 ${rows.length} 条数据`);
}
const importFields = computed(() =>
  fields.value.filter(
    (f) => !["code", "customerCode", "location"].includes(f.key),
  ),
);
function importTemplate() {
  downloadCsv(customerPage.value ? "客户导入模板" : "跟进记录导入模板", [
    importFields.value.map((f) => f.label),
  ]);
}
async function readImport(event: Event) {
  importError.value = "";
  importPreview.value = [];
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  try {
    if (file.size > 2 * 1024 * 1024) throw new Error("文件不能超过2MB");
    const [headers, ...rows] = parseCsv(await file.text());
    if (!headers || !rows.length)
      throw new Error("文件没有数据，请下载模板填写后重试");
    if (rows.length > 1000) throw new Error("单次最多导入1000条");
    const mapped = headers.map((h) =>
      fields.value.find((f) => f.label === h.trim()),
    );
    const required = fields.value.filter((f) => f.required).map((f) => f.label);
    if (props.kind === "followups") required.push("客户名称");
    if (required.some((label) => !headers.includes(label)))
      throw new Error(
        `缺少必填列：${required.filter((label) => !headers.includes(label)).join("、")}`,
      );
    importPreview.value = rows.map((cells, i) => {
      if (cells.length !== headers.length)
        throw new Error(`第${i + 2}行列数与表头不一致`);
      const values: CrmRow["values"] = {};
      mapped.forEach((f, j) => {
        if (f)
          values[f.key] =
            f.kind === "number" && cells[j] !== ""
              ? Number(cells[j])
              : cells[j] || "";
      });
      if (props.kind === "followups")
        values.customerId =
          store.state.customers.find(
            (c) => c.values.name === values.customerName,
          )?.id || "";
      return { id: "", values, contacts: [] };
    });
  } catch (e) {
    importError.value = (e as Error).message;
  }
  if (fileInput.value) fileInput.value.value = "";
}
function commitImport() {
  try {
    store.importRows(props.kind, importPreview.value);
    importOpen.value = false;
    ElMessage.success("导入成功");
  } catch (e) {
    importError.value = (e as Error).message;
  }
}
function startImport() {
  importPreview.value = [];
  importError.value = "";
  importOpen.value = true;
}
function openSale(row: CrmRow) {
  saleCustomer.value = row;
  sale.value = {
    name: `${row.values.name}-销售订单`,
    date: new Date().toLocaleDateString("sv-SE"),
    product: "MTQ8200-HS2F",
    quantity: 1,
    price: 1400,
  };
  saleOpen.value = true;
}
function saveSale() {
  if (
    !sale.value.name.trim() ||
    !sale.value.date ||
    !(sale.value.quantity > 0) ||
    !Number.isFinite(sale.value.price) ||
    sale.value.price < 0
  )
    return ElMessage.error("请填写订单名称、日期和有效数量、单价");
  try {
    store.addSale({
      id: `XSDD-${crypto.randomUUID().slice(0, 8)}`,
      customerId: saleCustomer.value!.id,
      ...sale.value,
    });
    saleOpen.value = false;
    ElMessage.success("销售单已保存，可在客户详情的销售订单页签查看");
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}
const reconcileRows = computed(() => [
  ...orderData.sales.orders
    .filter((o) => o.partner === saleCustomer.value?.values.name)
    .map((o) => ({ name: o.name, date: o.signed, amount: o.amount })),
  ...store.state.sales
    .filter((o) => o.customerId === saleCustomer.value?.id)
    .map((o) => ({ name: o.name, date: o.date, amount: o.price * o.quantity })),
]);
</script>
<template>
  <section class="crm-page">
    <div class="crm-view-tabs" role="tablist" aria-label="数据视图">
      <button
        v-for="name in customerPage
          ? ['全部客户', '潜在客户', '成交客户', '战略客户']
          : ['跟进列表']"
        :key="name"
        role="tab"
        :aria-selected="!customerPage || tab === name"
        :class="{ active: !customerPage || tab === name }"
        @click="tab = name"
      >
        <el-icon><Operation /></el-icon>{{ name }}
      </button>
    </div>
    <div class="crm-toolbar">
      <div class="crm-toolbar-actions">
        <el-button type="primary" :icon="Plus" @click="openRecord()"
          >添加</el-button
        ><el-button text :icon="Upload" @click="startImport">导入</el-button
        ><el-button text :icon="Download" @click="exportRows">导出</el-button
        ><el-button text :icon="Delete" @click="remove()">删除</el-button
        >
      </div>
      <div class="crm-toolbar-search">
        <el-button
          :icon="Filter"
          :type="filterOpen ? 'primary' : 'default'"
          @click="filterOpen = !filterOpen"
          >筛选</el-button
        ><el-button
          :icon="Operation"
          :type="grouped ? 'primary' : 'default'"
          plain
          title="分组显示"
          aria-label="切换分组显示"
          @click="grouped = !grouped"
        /><el-popover trigger="click" width="230"
          ><template #reference
            ><el-button :icon="Setting" title="显示字段" aria-label="显示字段"
          /></template>
          <div class="crm-column-options">
            <el-checkbox
              v-for="field in fields"
              :key="field.key"
              :model-value="!hidden.includes(field.key)"
              @change="
                (value: string | number | boolean) =>
                  (hidden = value
                    ? hidden.filter((k) => k !== field.key)
                    : [...hidden, field.key])
              "
              >{{ field.label }}</el-checkbox
            >
          </div></el-popover
        ><el-button
          :icon="Refresh"
          title="刷新"
          aria-label="刷新列表"
          @click="
            () => {
              selected = [];
              ElMessage.success('列表已刷新');
            }
          "
        />
      </div>
    </div>
    <div v-if="filterOpen" class="crm-filters">
      <el-select
        v-model="filterField"
        placeholder="选择筛选字段"
        clearable
        aria-label="筛选字段"
        ><el-option
          v-for="field in fields"
          :key="field.key"
          :label="field.label"
          :value="field.key" /></el-select
      ><el-select v-model="filterOperator" aria-label="筛选条件"
        ><el-option
          v-for="op in ['包含', '等于', '不等于', '为空', '不为空']"
          :key="op"
          :value="op" /></el-select
      ><el-input
        v-if="!['为空', '不为空'].includes(filterOperator)"
        v-model="filterText"
        placeholder="输入筛选值"
        aria-label="筛选值"
        clearable
      /><el-button
        @click="
          () => {
            filterField = '';
            filterText = '';
          }
        "
        >重置筛选</el-button
      >
    </div>
    <div class="crm-table-container">
      <table class="crm-table">
        <thead>
          <tr>
            <th class="crm-check">
              <el-checkbox
                :model-value="allSelected"
                :indeterminate="selected.length > 0 && !allSelected"
                aria-label="全选当前结果"
                @change="
                  (value: string | number | boolean) =>
                    selectRows(pageRows, Boolean(value))
                "
              />
            </th>
            <th v-for="field in columns" :key="field.key">{{ field.label }}</th>
            <th v-if="customerPage" class="crm-actions-cell">操作</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="group in groups" :key="group.name"
            ><tr v-if="grouped" class="crm-group">
              <td :colspan="columns.length + (customerPage ? 2 : 1)">
                <button
                  :aria-label="`${collapsed.includes(group.name) ? '展开' : '收起'}${group.name}`"
                  :aria-expanded="!collapsed.includes(group.name)"
                  @click="toggleGroup(group.name)"
                >
                  {{ collapsed.includes(group.name) ? "▸" : "▾" }}</button
                ><el-checkbox
                  :model-value="
                    group.rows.every((r) => selected.includes(r.id))
                  "
                  :aria-label="`选择${group.name}组`"
                  @change="
                    (value: string | number | boolean) =>
                      selectRows(group.rows, Boolean(value))
                  "
                /><span
                  class="crm-tag"
                  :style="{
                    background: tagColor(
                      crmFields[kind].find((f) => f.key === groupField)!,
                      group.name,
                    ),
                  }"
                  >{{ group.name }}</span
                ><span class="crm-group-count">{{ group.rows.length }}</span>
              </td>
            </tr>
            <template v-if="!grouped || !collapsed.includes(group.name)"
              ><tr
                v-for="row in group.rows"
                :key="row.id"
                class="crm-data-row"
                @dblclick="openRecord(row, true)"
              >
                <td class="crm-check">
                  <el-checkbox
                    :model-value="selected.includes(row.id)"
                    :aria-label="`选择${row.values.name || row.values.content}`"
                    @change="
                      (value: string | number | boolean) =>
                        selectRows([row], Boolean(value))
                    "
                  />
                </td>
                <td
                  v-for="field in columns"
                  :key="field.key"
                  :class="{ 'crm-numeric': field.kind === 'number' }"
                >
                  <button
                    v-if="field.key === (customerPage ? 'name' : 'content')"
                    class="crm-row-link"
                    @click="openRecord(row, true)"
                  >
                    {{ row.values[field.key] }}</button
                  ><span
                    v-else-if="field.options.length && row.values[field.key]"
                    class="crm-tag"
                    :style="{
                      background: tagColor(field, row.values[field.key]),
                    }"
                    >{{ display(row, field) }}</span
                  ><span v-else>{{ display(row, field) }}</span>
                </td>
                <td v-if="customerPage" class="crm-actions-cell">
                  <el-button
                    size="small"
                    class="crm-follow-button"
                    @click="follow(row)"
                    >客户跟进</el-button
                  ><el-button
                    size="small"
                    class="crm-sale-button"
                    @click="openSale(row)"
                    >＋ 新增销售单</el-button
                  ><el-button
                    size="small"
                    class="crm-reconcile-button"
                    @click="
                      () => {
                        saleCustomer = row;
                        reconcileOpen = true;
                      }
                    "
                    >对账</el-button
                  ><el-dropdown trigger="click"
                    ><el-button
                      size="small"
                      :aria-label="`${row.values.name}更多操作`"
                      >···</el-button
                    ><template #dropdown
                      ><el-dropdown-menu
                        ><el-dropdown-item @click="openRecord(row, true)"
                          >查看详情</el-dropdown-item
                        ><el-dropdown-item @click="openRecord(row)"
                          >编辑</el-dropdown-item
                        ><el-dropdown-item @click="remove([row.id])"
                          >删除</el-dropdown-item
                        ></el-dropdown-menu
                      ></template
                    ></el-dropdown
                  >
                </td>
              </tr></template
            >
          </template>
          <tr v-if="!sorted.length">
            <td :colspan="columns.length + 2">
              <el-empty description="暂无数据" />
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <footer class="crm-table-footer">
      <span>共 {{ sorted.length }} 条</span
      ><span v-if="selected.length">已选 {{ selected.length }} 条</span
      ><el-button v-if="grouped" text @click="collapsed = []"
        >⌄ 展开全部</el-button
      ><el-button
        v-if="grouped"
        text
        @click="collapsed = groups.map((g) => g.name)"
        >⌃ 收起全部</el-button
      >
    <el-pagination class="list-pagination" v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10,20,50,100]" :total="sorted.length" layout="total, sizes, prev, pager, next, jumper" /></footer>
    <CrmRecordDialog
      v-model="editor"
      :kind="editorKind"
      :row="editing"
      :customer="followCustomer"
      :detail="detail"
      @saved="selected = []"
      @followup="follow"
    />
    <el-dialog v-model="sortOpen" title="排序" width="400px"
      ><el-select
        v-model="sortKey"
        clearable
        placeholder="默认排序"
        aria-label="排序字段"
        ><el-option
          v-for="f in fields"
          :key="f.key"
          :label="f.label"
          :value="f.key" /></el-select
      ><el-radio-group v-model="sortOrder" class="crm-sort-order"
        ><el-radio value="asc">升序</el-radio
        ><el-radio value="desc">降序</el-radio></el-radio-group
      ><template #footer
        ><el-button type="primary" @click="sortOpen = false"
          >完成</el-button
        ></template
      ></el-dialog
    >
    <el-dialog v-model="logsOpen" title="操作记录" width="760px"
      ><el-table :data="store.state.logs" height="400" empty-text="暂无操作记录"
        ><el-table-column
          prop="time"
          label="时间"
          width="180" /><el-table-column
          prop="action"
          label="操作"
          width="120" /><el-table-column prop="name" label="数据" /></el-table
    ></el-dialog>
    <el-dialog
      v-model="importOpen"
      title="导入数据"
      width="700px"
      :close-on-click-modal="false"
      ><p>下载 CSV 模板填写数据后导入。跟进记录通过客户名称关联已有客户。</p>
      <div class="crm-import-actions">
        <el-button @click="importTemplate">下载导入模板</el-button
        ><el-button type="primary" @click="fileInput?.click()"
          >选择 CSV 文件</el-button
        ><input
          ref="fileInput"
          type="file"
          accept=".csv,text/csv"
          hidden
          @change="readImport"
        />
      </div>
      <el-alert
        v-if="importError"
        type="error"
        :title="importError"
        :closable="false"
      />
      <p v-if="importPreview.length">
        待导入 {{ importPreview.length }} 条（预览前5条）
      </p>
      <el-table
        v-if="importPreview.length"
        :data="importPreview.slice(0, 5)"
        max-height="250"
        ><el-table-column
          v-for="field in importFields.slice(0, 4)"
          :key="field.key"
          :label="field.label"
          :prop="`values.${field.key}`" /></el-table
      ><template #footer
        ><el-button @click="importOpen = false">取消</el-button
        ><el-button
          type="primary"
          :disabled="!importPreview.length || !!importError"
          @click="commitImport"
          >确认导入</el-button
        ></template
      ></el-dialog
    >
    <el-dialog v-model="saleOpen" title="新增销售单" width="650px"
      ><el-form label-position="top"
        ><el-form-item label="客户名称"
          ><el-input
            :model-value="saleCustomer?.values.name"
            disabled /></el-form-item
        ><el-form-item label="订单名称" required
          ><el-input v-model="sale.name" /></el-form-item
        ><el-form-item label="订单日期" required
          ><el-date-picker
            v-model="sale.date"
            value-format="YYYY-MM-DD" /></el-form-item
        ><el-form-item label="产品名称"
          ><el-select v-model="sale.product"
            ><el-option
              v-for="name in [
                'MTQ8200-HS2F',
                'MQM8790-HS2R',
                'Catalyst 8300',
                'Dusun Pi4',
                'UPS',
                'ST1L05',
              ]"
              :key="name"
              :value="name" /></el-select
        ></el-form-item>
        <div class="crm-form-grid">
          <el-form-item label="数量"
            ><el-input-number
              v-model="sale.quantity"
              :min="1"
              :precision="0" /></el-form-item
          ><el-form-item label="单价/元"
            ><el-input-number v-model="sale.price" :min="0" :precision="2"
          /></el-form-item>
        </div>
        <p>
          订单金额：{{ (sale.quantity * sale.price).toFixed(2) }} 元
        </p></el-form
      ><template #footer
        ><el-button @click="saleOpen = false">取消</el-button
        ><el-button type="primary" @click="saveSale">提交</el-button></template
      ></el-dialog
    >
    <el-dialog
      v-model="reconcileOpen"
      :title="`${saleCustomer?.values.name || ''} · 对账`"
      width="800px"
      ><el-table
        :data="reconcileRows"
        show-summary
        :summary-method="
          () => [
            '合计',
            '',
            reconcileRows.reduce((s, r) => s + r.amount, 0).toFixed(2),
          ]
        "
        ><el-table-column prop="name" label="订单名称" /><el-table-column
          prop="date"
          label="日期" /><el-table-column
          prop="amount"
          label="订单金额/元" /></el-table
      ><template #footer
        ><el-button @click="reconcileOpen = false">关闭</el-button
        ><el-button
          type="primary"
          @click="
            downloadCsv('客户对账单', [
              ['订单名称', '日期', '订单金额/元'],
              ...reconcileRows.map((r) => [r.name, r.date, r.amount]),
            ])
          "
          >导出对账单</el-button
        ></template
      ></el-dialog
    >
  </section>
</template>
