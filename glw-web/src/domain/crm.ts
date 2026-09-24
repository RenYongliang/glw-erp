export type CrmKind = "customers" | "followups";
export interface Contact {
  name: string;
  phone: string;
  position: string;
}
export interface CrmRow {
  id: string;
  values: Record<string, string | number>;
  contacts: Contact[];
}
export interface CrmField {
  key: string;
  label: string;
  kind: string;
  required: boolean;
  disabled: boolean;
  options: { value: string; color: string }[];
}
export const crmStorageKey = "glw.crm.v1";
export function nextCustomerCode(rows: CrmRow[], now = new Date()) {
  const prefix = `KH${String(now.getFullYear()).slice(-2)}${String(now.getMonth() + 1).padStart(2, "0")}${String(now.getDate()).padStart(2, "0")}-`;
  const last = Math.max(
    0,
    ...rows.map((r) =>
      String(r.values.code).startsWith(prefix)
        ? Number(String(r.values.code).slice(prefix.length)) || 0
        : 0,
    ),
  );
  return prefix + String(last + 1).padStart(2, "0");
}
export function validateCrm(
  kind: CrmKind,
  row: CrmRow,
  fields: CrmField[],
  customers: CrmRow[],
) {
  for (const f of fields) {
    const value = row.values[f.key];
    if (f.required && (value == null || String(value).trim() === ""))
      return `请填写${f.label}`;
    if (
      value !== "" &&
      value != null &&
      f.options.length &&
      !f.options.some((o) => o.value === value)
    )
      return `${f.label}不在可选范围内`;
  }
  if (kind === "customers") {
    if (
      customers.some(
        (r) =>
          r.id !== row.id &&
          String(r.values.name).trim() === String(row.values.name).trim(),
      )
    )
      return "客户名称已存在，请勿重复提交";
    for (const key of ["credit", "taxRate"])
      if (
        row.values[key] !== "" &&
        row.values[key] != null &&
        (!Number.isFinite(Number(row.values[key])) ||
          Number(row.values[key]) < 0)
      )
        return "信用额度和税率须为非负数";
    if (Number(row.values.taxRate) > 100) return "税率不能大于100%";
    if (
      row.values.email &&
      !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(row.values.email))
    )
      return "请填写正确的收票邮箱";
    if (row.contacts.some((c) => c.phone && !/^1\d{10}$/.test(c.phone)))
      return "其他联系人手机须为11位手机号";
  } else {
    if (!customers.some((c) => c.id === row.values.customerId))
      return "请选择有效客户";
    if (
      !Number.isFinite(
        new Date(String(row.values.time).replace(" ", "T")).getTime(),
      )
    )
      return "请填写正确的跟进时间";
  }
  return "";
}
export function encodeCsv(rows: unknown[][]) {
  return (
    "\uFEFF" +
    rows
      .map((row) =>
        row
          .map((value) => {
            let text = String(value ?? "");
            if (/^[=+@-]/.test(text)) text = "'" + text;
            return `"${text.replaceAll('"', '""')}"`;
          })
          .join(","),
      )
      .join("\r\n")
  );
}
export function parseCsv(text: string): string[][] {
  const result: string[][] = [];
  let row: string[] = [],
    cell = "",
    quoted = false;
  text = text.replace(/^\uFEFF/, "");
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (c === '"') {
      if (quoted && text[i + 1] === '"') {
        cell += '"';
        i++;
      } else quoted = !quoted;
    } else if (c === "," && !quoted) {
      row.push(cell);
      cell = "";
    } else if ((c === "\n" || c === "\r") && !quoted) {
      if (c === "\r" && text[i + 1] === "\n") i++;
      row.push(cell);
      if (row.some(Boolean)) result.push(row);
      row = [];
      cell = "";
    } else cell += c;
  }
  if (quoted) throw new Error("CSV 引号未闭合，请检查文件");
  row.push(cell);
  if (row.some(Boolean)) result.push(row);
  return result;
}
