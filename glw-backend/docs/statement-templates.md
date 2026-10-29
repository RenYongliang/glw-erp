# 通用文档模板

## 目标

一套可视化设计器覆盖公司内部所有需要打印的单据：

| `doc_type` | 单据 | 方向 |
|---|---|---|
| `STATEMENT` | 对账单 | `AR` 应收 / `AP` 应付 |
| `SALES_CONTRACT` | 销售合同 | `AR` |
| `PURCHASE_CONTRACT` | 采购合同 | `AP` |
| `DELIVERY_NOTE` | 送货单 | `AR` |

模板只决定版式，业务数据仍由各单据自己的 Assembler 提供。新增单据类型时，
在 `DocumentType` 增加枚举、补一份 `DocumentLayoutDefaults` 和样例数据即可。

```
业务单据快照
    │
    ▼
scalars + rows（统一字段契约）
    │
    ▼
LayoutStatementRenderer（XLSX）/ LayoutPdfRenderer（PDF）
    │
    ▼
fin_statement_template：doc_type + direction + partner_id + layout_json + version
```

## 设计原则

- 模板全部通过可视化设计器创建，不再支持上传 Excel/PDF 文件模板。
- 每类单据、每个方向、每个往来单位在首次使用时都会自动获得一份系统全局默认可视化模板。
- 系统默认模板也可以直接编辑；用户新增的模板同样进入设计器，所有模板操作列固定有「编辑」。
- 输出格式在保存模板时选择：`XLSX` 便于二次编辑，`PDF` 用于直接发送/打印。
- 删除为软删除（`deleted=1`），历史单据仍按出单时绑定的版本重新导出。

## 数据表

`fin_statement_template`

| 字段 | 说明 |
|---|---|
| `doc_type` | `STATEMENT` / `SALES_CONTRACT` / `PURCHASE_CONTRACT` / `DELIVERY_NOTE` |
| `direction` / `partner_id` | 方向 + 往来单位；`partner_id=0` 为全局默认 |
| `format` | `XLSX` / `PDF` |
| `version` | 同类型、同方向、同往来单位、同格式的版本号，改版即新增一行 |
| `file_key` | 保存时生成的样例文件；正式导出按 `layout_json` 实时渲染 |
| `layout_json` | 可视化布局；模板中心只展示非空布局 |
| `is_default` | 当前生效版本，同一维度只保留一个默认 |
| `deleted` | 软删除标记；列表和解析都排除 |

`fin_statement` 的 `template_id` / `template_version` 在生成对账单时锁定版本。

## 默认模板补种

启动时 `DocumentTemplateSeeder` 会检查每个单据类型/方向是否存在全局模板，没有则：

1. 读取 `DocumentLayoutDefaults` 的默认布局；
2. 校验布局并用样例数据渲染一份 XLSX 样例；
3. 以 `partner_id=0`、`is_default=1` 写入模板表。

补种幂等；模板列表加载时也会做同样的检查，避免运行期间模板被清空后无底稿可用。

## 导出选择顺序（对账单）

1. 显式 `?format=CSV` → 内置 CSV（调试/兜底）
2. 显式 `?format=XLSX|PDF` → 该客户该格式的默认模板，找不到则报错
3. 未指定格式 → 对账单快照模板；没有快照则取客户默认模板（默认标记优先，其次 XLSX）；再没有则 CSV

选择规则：客户专属优先于全局；默认模板优先，否则按 XLSX → PDF → 最新版本兜底；
`enabled=0` 或 `deleted=1` 的模板不参与选择。其他单据类型目前复用同一套解析和渲染能力，
接入打印时只需要按 `doc_type` 传入对应的数据。

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/fin/statement/{id}/export?format=` | 下载对账单，文件名/类型由渲染器决定 |
| GET | `/api/fin/statement/template/doc-types` | 模板中心支持的单据类型 |
| GET | `/api/fin/statement/template/list?docType=STATEMENT&direction=AR&partnerId=` | 模板列表；首次访问自动补默认模板 |
| POST | `/api/fin/statement/template/{id}/default` | 切换默认版本（ADMIN） |
| POST | `/api/fin/statement/template/{id}/delete` | 软删除模板（ADMIN） |
| POST | `/api/fin/statement/template/layout` | 保存可视化布局，生成新版本（ADMIN） |
| GET | `/api/fin/statement/template/{id}/layout` | 读取布局，供设计器编辑 |
| POST | `/api/fin/statement/template/layout/preview?format=XLSX\|PDF&docType=&direction=` | 用样例数据生成预览（ADMIN） |

保存布局参数：

```json
{
  "docType": "STATEMENT",
  "direction": "AR",
  "partnerId": 0,
  "name": "系统默认对账单（应收）",
  "isDefault": true,
  "format": "PDF",
  "layout": { "page": {}, "components": [] }
}
```

## 可视化设计器

前端菜单「财务 → 文档模板」（`/finance/templates`）：

- 顶部按单据类型切换：对账单、销售合同、采购合同、送货单；对账单再切应收/应付。
- 列表只展示模板名称、适用范围、输出格式、版本、更新时间和操作。
- 操作固定为：编辑、设为默认（非默认时）、删除（系统默认模板保护，不可删除）。
- 新增模板时选择单据类型和往来单位，进入设计器从该类默认版式开始设计。

设计器能力：

- 左侧组件面板：文本、字段、明细表格、签章区；点击或拖到 A4 纸张上。
- 纸张上的组件可拖动、四角缩放；右侧属性面板调整字号、对齐、边框、字段绑定和明细列。
- 明细表格填 4 行样例数据，正式导出时按真实行数向下撑开，合计/签章区自动下移。
- 右上角「预览」进入纯预览模式，纸张自动适配画布，Esc 退出。
- 「下载预览」可选 Excel 或 PDF，均由后端用当前单据类型的样例数据实时渲染。
- 保存时选择输出格式；每次保存新增版本，历史单据仍按出单时版本渲染。
- PDF 由 PDFBox 直接绘制：A4 分页，明细跨页时每页重复表头，合计/签章只出现在最后一页且优先贴底。
  一个 PDF 布局只允许一个明细表格；中文字体默认查找系统黑体/雅黑，也可用 `app.finance.pdf-font` 指定 TTF/TTC。

### 字段契约

单值字段（`field` / 文本占位符 `{{...}}`）：

- 公司与往来单位：`company.name/taxNo/bankName/bankAccount/phone/contact`、
  `partner.name/contact/phone/address`
- 通用单据：`header.title/no/dateLabel/partnerLabel/orderNo/paymentTerms/deliveryDate/deliveryPlace/remark`
- 对账单：`header.statementNo/periodLabel/statementDateLabel/currencyLabel/taxRateLabel/status`
- 合计：`totals.totalQuantity/totalAmount/totalGross/totalNet/totalTax`

明细字段（`item.*`）：`seq`、`productName`、`spec`、`partNo`、`packageType`、`brand`、
`bizDateText`、`sourceNo`、`orderNo`、`partnerOrderNo`、`quantity`、`unit`、`unitPrice`、
`amountGross`、`amountNet`、`taxAmount`、`remark`。

对账单沿用原有明细字段；合同/送货单主要使用 `productName`、`spec`、`packageType`、
`quantity`、`unit`、`unitPrice`、`amountGross`。

布局 JSON 示例：

```json
{
  "page": { "size": "A4", "orientation": "PORTRAIT", "marginMm": 10, "gridMm": 3 },
  "components": [
    { "id": "c1", "type": "text", "x": 15, "y": 10, "w": 180, "h": 12,
      "text": "{{header.title}}", "fontSize": 20, "bold": true, "align": "center" },
    { "id": "c2", "type": "field", "x": 15, "y": 35, "w": 105, "h": 7,
      "label": "客户：", "field": "partner.name" },
    { "id": "c3", "type": "table", "x": 15, "y": 47, "w": 180, "h": 36,
      "headerHeight": 8, "rowHeight": 7, "fontSize": 9,
      "columns": [{ "title": "型号", "field": "item.partNo", "width": 30, "align": "left" }] },
    { "id": "c4", "type": "signature", "x": 15, "y": 250, "w": 180, "h": 30,
      "text": "客户确认（盖章）：        供应商确认（盖章）：", "border": true }
  ]
}
```

坐标单位是毫米，后端按 `gridMm` 映射到 Excel 单元格。组件不能重叠，明细表格必须至少绑定一列
`item.*` 字段。

## 配置

```yaml
app:
  finance:
    template-dir: ${STATEMENT_TEMPLATE_DIR:${user.home}/glw/statement-templates}
    default-unit: pcs
    pdf-font: ${STATEMENT_PDF_FONT:}
    company:
      name: 深圳光联微电子有限公司
      tax-no: ...
      bank-name: ...
      bank-account: ...
      phone: ...
      contact: ...
```

样例模板文件不存数据库；`template-dir` 需要纳入备份。

## 数据库升级

已有库依次执行：

- `src/main/resources/db/migrations/20261029_statement_template.sql`
- `src/main/resources/db/migrations/20261029_statement_layout.sql`
- `src/main/resources/db/migrations/20261030_document_template.sql`

新库直接执行 `finance.sql`。
