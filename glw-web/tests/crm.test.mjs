import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  nextCustomerCode,
  validateCrm,
  parseCsv,
  encodeCsv,
} from "../src/domain/crm.ts";
const seed = JSON.parse(
  readFileSync(new URL("../src/data/crm.json", import.meta.url)),
);
test("customer codes increment across gaps and reset on a new date", () => {
  const rows = [
    { values: { code: "KH260924-01" } },
    { values: { code: "KH260924-07" } },
  ];
  assert.equal(nextCustomerCode(rows, new Date(2026, 8, 24)), "KH260924-08");
  assert.equal(nextCustomerCode(rows, new Date(2026, 8, 25)), "KH260925-01");
});
test("customer validation catches duplicates, invalid financial values and required fields", () => {
  const validate = (row) =>
    validateCrm("customers", row, seed.customers.fields, seed.customers.rows);
  const row = structuredClone(seed.customers.rows[0]);
  assert.equal(validate(row), "");
  row.id = "";
  assert.match(validate(row), /已存在/);
  row.values.name = "测试新客户";
  row.values.taxRate = 101;
  assert.match(validate(row), /100/);
  row.values.taxRate = 13;
  row.values.credit = 0;
  assert.equal(validate(row), "");
  row.values.credit = Number.NaN;
  assert.match(validate(row), /非负数/);
  row.values.credit = 100;
  row.values.owner = "";
  assert.match(validate(row), /销售负责人/);
});
test("followups require valid customer, method, stage and date", () => {
  const row = structuredClone(seed.followups.rows[0]);
  const validate = () =>
    validateCrm("followups", row, seed.followups.fields, seed.customers.rows);
  assert.equal(validate(), "");
  row.values.customerId = "missing";
  assert.match(validate(), /有效客户/);
  row.values.customerId = seed.customers.rows[0].id;
  row.values.method = "";
  assert.match(validate(), /跟进方式/);
  row.values.method = "电话拜访";
  row.values.time = "invalid";
  assert.match(validate(), /跟进时间/);
});
test("CSV preserves quoted commas and multiline text, escapes formulas and rejects incomplete quotes", () => {
  const rows = [
    ["客户名称", "跟进内容"],
    ["测试,公司", '第一行\n第二行"说明"'],
  ];
  assert.deepEqual(parseCsv(encodeCsv(rows)), rows);
  assert.equal(parseCsv(encodeCsv([["=1+1"]]))[0][0], "'=1+1");
  assert.throws(() => parseCsv('"unfinished'), /引号/);
});
