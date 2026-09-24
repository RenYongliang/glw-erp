import { ref } from "vue";
import { defineStore } from "pinia";
import seed from "../data/crm.json";
import {
  crmStorageKey,
  nextCustomerCode,
  validateCrm,
  type CrmKind,
  type CrmRow,
  type CrmField,
} from "../domain/crm";
export const crmFields = {
  customers: seed.customers.fields as CrmField[],
  followups: seed.followups.fields as CrmField[],
};
export interface CrmSale {
  id: string;
  customerId: string;
  name: string;
  date: string;
  product: string;
  quantity: number;
  price: number;
}
interface State {
  customers: CrmRow[];
  followups: CrmRow[];
  logs: { id: string; time: string; action: string; name: string }[];
  sales: CrmSale[];
}
const initial = (): State => ({
  customers: structuredClone(seed.customers.rows),
  followups: structuredClone(seed.followups.rows),
  logs: [],
  sales: [],
});
export const useCrmStore = defineStore("crm", () => {
  const state = ref<State>(initial());
  const loadError = ref("");
  try {
    const text = localStorage.getItem(crmStorageKey);
    if (text) {
      const saved = JSON.parse(text);
      if (
        !["customers", "followups", "logs", "sales"].every((k) =>
          Array.isArray(saved[k]),
        ) ||
        ![...saved.customers, ...saved.followups].every(
          (r) =>
            typeof r.id === "string" && r.values && Array.isArray(r.contacts),
        )
      )
        throw Error();
      state.value = saved;
    }
  } catch {
    loadError.value = "本地客户数据无法读取，当前显示初始演示数据。";
  }
  function commit(next: State, action: string, name: string) {
    next.logs.unshift({
      id: crypto.randomUUID(),
      time: new Date().toLocaleString("zh-CN", { hour12: false }),
      action,
      name,
    });
    next.logs = next.logs.slice(0, 200);
    try {
      localStorage.setItem(crmStorageKey, JSON.stringify(next));
    } catch {
      throw new Error("本地保存失败，请检查浏览器存储空间后重试");
    }
    state.value = next;
  }
  const snapshot = (): State => JSON.parse(JSON.stringify(state.value));
  function save(kind: CrmKind, input: CrmRow) {
    const row: CrmRow = JSON.parse(JSON.stringify(input)),
      next = snapshot();
    const error = validateCrm(kind, row, crmFields[kind], next.customers);
    if (error) throw new Error(error);
    if (kind === "customers") {
      row.values.name = String(row.values.name).trim();
      if (!row.values.code) row.values.code = nextCustomerCode(next.customers);
    } else {
      const customer = next.customers.find(
        (c) => c.id === row.values.customerId,
      )!;
      row.values.customerName = customer.values.name!;
      row.values.customerCode = customer.values.code!;
    }
    const index = next[kind].findIndex((r) => r.id === row.id);
    if (index < 0) {
      row.id = crypto.randomUUID();
      next[kind].unshift(row);
    } else next[kind][index] = row;
    if (kind === "customers")
      for (const f of next.followups.filter(
        (f) => f.values.customerId === row.id,
      )) {
        f.values.customerName = row.values.name!;
        f.values.customerCode = row.values.code!;
      }
    commit(
      next,
      index < 0 ? "添加" : "修改",
      String(row.values.name || row.values.content),
    );
    return row;
  }
  function remove(kind: CrmKind, ids: string[]) {
    const next = snapshot();
    if (
      kind === "customers" &&
      (next.followups.some((r) => ids.includes(String(r.values.customerId))) ||
        next.sales.some((r) => ids.includes(r.customerId)))
    )
      throw new Error("选中的客户有关联跟进记录或销售单，请先处理关联记录");
    next[kind] = next[kind].filter((r) => !ids.includes(r.id));
    commit(
      next,
      "删除",
      `${kind === "customers" ? "客户" : "跟进记录"} ${ids.length} 条`,
    );
  }
  function importRows(kind: CrmKind, rows: CrmRow[]) {
    const next = snapshot();
    for (const [i, row] of rows.entries()) {
      const error = validateCrm(kind, row, crmFields[kind], next.customers);
      if (error) throw new Error(`第 ${i + 2} 行：${error}`);
      row.id = crypto.randomUUID();
      if (kind === "customers") {
        row.values.code = nextCustomerCode(next.customers);
        row.values.name = String(row.values.name).trim();
      } else {
        const c = next.customers.find((c) => c.id === row.values.customerId)!;
        row.values.customerName = c.values.name!;
        row.values.customerCode = c.values.code!;
      }
      next[kind].push(row);
    }
    commit(
      next,
      "导入",
      `${rows.length} 条${kind === "customers" ? "客户" : "跟进记录"}`,
    );
  }
  function addSale(sale: CrmSale) {
    const next = snapshot();
    next.sales.unshift(sale);
    commit(next, "新增销售单", sale.name);
  }
  return { state, loadError, save, remove, importRows, addSale };
});
