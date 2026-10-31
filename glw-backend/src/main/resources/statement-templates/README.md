# 对账单 Excel 模板

每个往来单位可以把日常在用的对账单 Excel 直接改成模板：保留排版、合并单元格、边框和打印设置，
只把要填数据的单元格换成占位符即可。模板改版后上传为新版本，已生成的对账单仍按出单时的版本重导。

## 明细行

把需要重复的明细区整行（或连续多行）填上 `item.*` 占位符，例如：

| 序号 | 送货日期 | 销售送货单号 | 客户订单号 | 型号 | 数量 | 单位 | 含税单价 | 含税金额 | 备注 |
|---|---|---|---|---|---|---|---|---|---|
| `{{item.seq}}` | `{{item.bizDate}}` | `{{item.sourceNo}}` | `{{item.partnerOrderNo}}` | `{{item.partNo}}` | `{{item.quantity}}` | `{{item.unit}}` | `{{item.unitPrice}}` | `{{item.amountGross}}` | `{{item.remark}}` |

规则：

- 连续的、含 `item.*` 占位符的行组成固定明细区；明细不足时留空，超出时自动插行并复制最后一行样式，合计区和落款区随之下移。
- `{{item.bizDate}}`、`{{item.dueDate}}` 输出为日期值，所在单元格请设置日期格式（如 `yyyy/m/d`）；需要纯文本时用 `{{item.bizDateText}}`、`{{item.dueDateText}}`。
- 金额、数量输出为数值，保留模板单元格的数字格式。

## 合计行

合计跟表格是同一个框：在设计器选中明细表格，用「合计行」添加单元格，每个单元格可以跨多列
（`span`），文字里可以写 `{{totals.totalGross}}` 这类占位符，也可以直接绑定合计字段。
渲染时合计行紧跟在最后一行明细后面，明细跨页时只在最后一页出现，表格下方组件随之下移。

```json
"footer": [
  {"text": "合计数量：", "span": 5, "align": "right"},
  {"field": "totals.totalQuantity", "span": 1, "align": "center"},
  {"text": "对账金额", "span": 2, "align": "center"},
  {"field": "totals.totalGross", "span": 1, "align": "right"}
]
```

## 单值字段

| 占位符 | 含义 |
|---|---|
| `{{company.name}}` / `{{company.taxNo}}` / `{{company.phone}}` | 我方名称、税号、电话 |
| `{{company.bankName}}` / `{{company.bankAccount}}` / `{{company.contact}}` | 我方开户行、账号、联系人 |
| `{{partner.name}}` / `{{partner.contact}}` / `{{partner.phone}}` | 客户/供应商名称、联系人、电话 |
| `{{header.statementNo}}` / `{{header.direction}}` / `{{header.status}}` | 对账单号、方向、状态 |
| `{{header.period}}` / `{{header.periodLabel}}` | 对账期间：`2026-09` / `2026年9月` |
| `{{header.statementDate}}` / `{{header.statementDateLabel}}` | 出单日期 / `10月26日` |
| `{{header.currency}}` / `{{header.currencyLabel}}` | `CNY` / `RMB` |
| `{{header.taxRateLabel}}` / `{{header.remark}}` | 税率（如 `13%`）/ 备注 |
| `{{totals.totalQuantity}}` / `{{totals.totalGross}}` | 合计数量、价税合计 |
| `{{totals.totalNet}}` / `{{totals.totalTax}}` | 未税合计、税额合计 |

## 明细字段

`item.seq`、`item.bizDate`、`item.bizDateText`、`item.dueDate`、`item.dueDateText`、
`item.sourceNo`、`item.orderNo`、`item.partnerOrderNo`、`item.brand`、`item.partNo`、
`item.packageType`、`item.quantity`、`item.unit`、`item.unitPrice`、`item.unitPriceNet`、
`item.taxIncluded`、`item.taxRate`、`item.amountGross`、`item.amountNet`、`item.taxAmount`、`item.remark`。

## 内置样例

- `qintai-AR.xlsx`：勤泰原始 Excel 对账单改造的模板
- `mingtai-AR.xlsx`：按铭泰纸质对账单版式重建的模板（扫描件无法直接套打，需转成 Excel 模板）
- `mingtai-AR.pdf.json`：铭泰 PDF 版对账单布局，按回传的纸质扫描件重建：A4 纵向、10 列明细、
  表头客户/供应商信息、合计行、币别、说明与签章区位置与原件一致
- `mingtai-AR.pdf`：由上述布局渲染的样例文件

PDF 版式在模板中心保存为「PDF」输出格式后，导出时按布局和真实明细实时渲染，不依赖固定行数；
明细跨页会自动重复表头。布局字段说明见上文占位符表。
