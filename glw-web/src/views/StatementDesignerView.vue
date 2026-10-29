<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Delete, Download, Plus, View } from '@element-plus/icons-vue'
import { financeApi, statementTemplateApi } from '../services/financeApi'
import type {
  Direction,
  DocumentTypeCode,
  LayoutColumn,
  LayoutComponent,
  Partner,
  StatementFormat,
  StatementLayout,
  StatementTemplate,
} from '../services/financeApi'

type ComponentType = LayoutComponent['type']
type DragMode = 'move' | 'nw' | 'ne' | 'sw' | 'se'
interface ItemField {
  value: string
  label: string
  width: number
  align: NonNullable<LayoutColumn['align']>
}

const PPMM = 96 / 25.4
const route = useRoute()
const router = useRouter()
const editingId = Number(route.query.id || 0)

const DOC_LABELS: Record<DocumentTypeCode, string> = {
  STATEMENT: '对账单',
  SALES_CONTRACT: '销售合同',
  PURCHASE_CONTRACT: '采购合同',
  DELIVERY_NOTE: '送货单',
}
const DOC_NAMES: Record<DocumentTypeCode, string> = {
  STATEMENT: 'AR202609001',
  SALES_CONTRACT: 'XSHT202610001',
  PURCHASE_CONTRACT: 'CGHT202610001',
  DELIVERY_NOTE: 'SH202610001',
}
function parseDocType(value: unknown): DocumentTypeCode {
  return typeof value === 'string' && value in DOC_LABELS
    ? (value as DocumentTypeCode)
    : 'STATEMENT'
}
function fixedDirection(type: DocumentTypeCode): Direction {
  if (type === 'PURCHASE_CONTRACT') return 'AP'
  return 'AR'
}
function currentTypeDirectional() {
  return docType.value === 'STATEMENT'
}

const loading = ref(true)
const saving = ref(false)
const previewing = ref(false)
const zoom = ref(1)
const docType = ref<DocumentTypeCode>(parseDocType(route.query.docType))
const direction = ref<Direction>(
  route.query.direction === 'AP' || route.query.direction === 'AR'
    ? (route.query.direction as Direction)
    : fixedDirection(docType.value),
)
const docTypeLabel = computed(() => DOC_LABELS[docType.value])
const partners = ref<Partner[]>([])
const editing = ref<StatementTemplate>()
const selectedId = ref('')
const canvasRef = ref<HTMLElement>()
const paperRef = ref<HTMLElement>()
const zoomBeforePreview = ref(1)
const layout = ref<StatementLayout>(blankLayout())
const saveOpen = ref(false)
const saveForm = ref<{
  partnerId: number
  name: string
  isDefault: boolean
  format: StatementFormat
}>({ partnerId: 0, name: '', isDefault: true, format: 'XLSX' })
let dragState: {
  id: string
  mode: DragMode
  startX: number
  startY: number
  x: number
  y: number
  w: number
  h: number
} | null = null

const selected = computed(() => layout.value.components.find(c => c.id === selectedId.value))
const portrait = computed(() => layout.value.page.orientation !== 'LANDSCAPE')
const paperWidth = computed(() => (portrait.value ? 210 : 297))
const paperHeight = computed(() => (portrait.value ? 297 : 210))
const stageWidth = computed(() => paperWidth.value * PPMM * zoom.value)
const stageHeight = computed(() => paperHeight.value * PPMM * zoom.value)
const grid = computed(() => layout.value.page.gridMm || 3)
const paperStyle = computed(() => ({
  width: `${paperWidth.value}mm`,
  height: `${paperHeight.value}mm`,
  transform: `scale(${zoom.value})`,
  backgroundSize: `${grid.value}mm ${grid.value}mm`,
}))
const marginStyle = computed(() => ({
  inset: `${layout.value.page.marginMm || 10}mm`,
}))

type SampleRow = Record<string, string | number>
const statementItems: SampleRow[] = [
  { seq: 1, bizDateText: '2026/9/25', sourceNo: 'XM202609001', partnerOrderNo: 'MT-PO-260901', partNo: 'LM358DR', quantity: 5000, unit: 'pcs', unitPrice: '1.25', amountGross: '6,250.00' },
  { seq: 2, bizDateText: '2026/9/27', sourceNo: 'XM202609002', partnerOrderNo: 'MT-PO-260905', partNo: 'SN74HC595DR', quantity: 3000, unit: 'pcs', unitPrice: '0.86', amountGross: '2,580.00' },
  { seq: 3, bizDateText: '2026/9/30', sourceNo: 'XM202609003', partnerOrderNo: 'MT-PO-260910', partNo: 'AMS1117-3.3', quantity: 12000, unit: 'pcs', unitPrice: '0.42', amountGross: '5,040.00' },
  { seq: 4, bizDateText: '2026/10/3', sourceNo: 'XM202609004', partnerOrderNo: 'MT-PO-260918', partNo: 'STM32F103C8T6', quantity: 800, unit: 'pcs', unitPrice: '9.80', amountGross: '7,840.00' },
]
const documentItems: SampleRow[] = [
  { seq: 1, code: 'IC-LM358', productName: '运算放大器', spec: 'SOP-8', partNo: 'LM358DR', packageType: 'SOP-8', quantity: 5000, unit: 'pcs', unitPrice: '1.25', amountGross: '6,250.00', remark: '' },
  { seq: 2, code: 'IC-74HC595', productName: '移位寄存器', spec: 'SOP-16', partNo: 'SN74HC595DR', packageType: 'SOP-16', quantity: 3000, unit: 'pcs', unitPrice: '0.86', amountGross: '2,580.00', remark: '' },
  { seq: 3, code: 'IC-AMS1117', productName: '线性稳压器', spec: 'SOT-223', partNo: 'AMS1117-3.3', packageType: 'SOT-223', quantity: 12000, unit: 'pcs', unitPrice: '0.42', amountGross: '5,040.00', remark: '' },
  { seq: 4, code: 'IC-STM32F103', productName: '微控制器', spec: 'LQFP-48', partNo: 'STM32F103C8T6', packageType: 'LQFP-48', quantity: 800, unit: 'pcs', unitPrice: '9.80', amountGross: '7,840.00', remark: '' },
]
const sampleItems = computed(() => (docType.value === 'STATEMENT' ? statementItems : documentItems))

const companySample: Record<string, string | number> = {
  'company.name': '深圳光联微电子有限公司',
  'company.taxNo': '91440300MA5XXXXXXX',
  'company.bankName': '招商银行深圳分行',
  'company.bankAccount': '7559 1234 5678 901',
  'company.phone': '0755-8888 6666',
  'company.contact': '张经理',
}
const statementValues: Record<string, string | number> = {
  ...companySample,
  'partner.name': '深圳市铭泰电子信息科技有限公司',
  'partner.contact': '李小姐',
  'partner.phone': '0755-2666 8899',
  'partner.address': '深圳市南山区科技园 8 号',
  'header.statementNo': 'AR202609001',
  'header.direction': 'AR',
  'header.period': '2026-09',
  'header.periodLabel': '2026年9月',
  'header.statementDate': '2026-10-08',
  'header.statementDateLabel': '10月8日',
  'header.currency': 'CNY',
  'header.currencyLabel': 'RMB',
  'header.taxRateLabel': '13%',
  'header.status': 'CONFIRMED',
  'header.remark': '',
  'header.no': 'AR202609001',
  'header.dateLabel': '10月8日',
  'header.orderNo': '',
  'header.paymentTerms': '',
  'header.deliveryDate': '',
  'header.deliveryPlace': '',
  'totals.totalQuantity': 20800,
  'totals.totalGross': '21,710.00',
  'totals.totalNet': '19,212.39',
  'totals.totalTax': '2,497.61',
  'totals.totalAmount': '21,710.00',
}
const sampleValues = computed<Record<string, string | number>>(() => {
  if (docType.value === 'STATEMENT') {
    return {
      ...statementValues,
      'header.title': direction.value === 'AP' ? '对账单（应付）' : '对账单（应收）',
      'header.partnerLabel': direction.value === 'AP' ? '供应商：' : '客户：',
    }
  }
  return {
    ...companySample,
    'partner.name':
      docType.value === 'PURCHASE_CONTRACT'
        ? '深圳市华芯电子材料有限公司'
        : '深圳市铭泰电子信息科技有限公司',
    'partner.contact': '李小姐',
    'partner.phone': '0755-2666 8899',
    'partner.address': '深圳市南山区科技园 8 号',
    'header.title': docTypeLabel.value,
    'header.no': DOC_NAMES[docType.value],
    'header.dateLabel': '2026年10月29日',
    'header.partnerLabel': '往来单位：',
    'header.periodLabel': '2026年10月',
    'header.currencyLabel': 'RMB',
    'header.remark': '以上货物按约定时间交付，质量按双方确认的规格书执行。',
    'header.paymentTerms': '月结 30 天，验收合格后付款',
    'header.deliveryDate': '2026年11月15日前',
    'header.deliveryPlace': '深圳市宝安区光联微电子仓库',
    'header.orderNo': 'SO202610001',
    'header.purchaseNo': 'PO202610001',
    'header.handler': '王师傅',
    'header.shippingSummary': '承运：顺丰速运；运单号：SF1234567890',
    'totals.totalQuantity': 20800,
    'totals.totalGross': '21,710.00',
    'totals.totalNet': '19,212.39',
    'totals.totalTax': '2,497.61',
    'totals.totalAmount': '21,710.00',
  }
})

const companyOptions = [
  { value: 'company.name', label: '我方公司名称' },
  { value: 'company.taxNo', label: '我方税号' },
  { value: 'company.bankName', label: '开户银行' },
  { value: 'company.bankAccount', label: '银行账号' },
  { value: 'company.phone', label: '我方电话' },
  { value: 'company.contact', label: '我方联系人' },
  { value: 'partner.name', label: '客户/供应商名称' },
  { value: 'partner.contact', label: '对方联系人' },
  { value: 'partner.phone', label: '对方电话' },
]
const statementScalarGroups = [
  { label: '公司与往来单位', options: companyOptions },
  {
    label: '对账单信息',
    options: [
      { value: 'header.statementNo', label: '对账单编号' },
      { value: 'header.periodLabel', label: '对账期间' },
      { value: 'header.statementDateLabel', label: '对账日期' },
      { value: 'header.currencyLabel', label: '币别' },
      { value: 'header.taxRateLabel', label: '税率' },
      { value: 'header.remark', label: '备注' },
      { value: 'header.status', label: '状态' },
    ],
  },
  {
    label: '合计',
    options: [
      { value: 'totals.totalQuantity', label: '合计数量' },
      { value: 'totals.totalGross', label: '对账金额（价税合计）' },
      { value: 'totals.totalNet', label: '不含税金额' },
      { value: 'totals.totalTax', label: '税额' },
    ],
  },
]
const documentScalarGroups = [
  {
    label: '公司与往来单位',
    options: [...companyOptions, { value: 'partner.address', label: '对方地址' }],
  },
  {
    label: '单据信息',
    options: [
      { value: 'header.title', label: '单据标题' },
      { value: 'header.no', label: '单据编号' },
      { value: 'header.dateLabel', label: '单据日期' },
      { value: 'header.orderNo', label: '关联订单号' },
      { value: 'header.purchaseNo', label: '采购单号' },
      { value: 'header.handler', label: '经办人' },
      { value: 'header.shippingSummary', label: '运输信息' },
      { value: 'header.paymentTerms', label: '付款方式' },
      { value: 'header.deliveryDate', label: '交货日期' },
      { value: 'header.deliveryPlace', label: '交货地点' },
      { value: 'header.remark', label: '备注 / 其他约定' },
    ],
  },
  {
    label: '合计',
    options: [
      { value: 'totals.totalQuantity', label: '合计数量' },
      { value: 'totals.totalAmount', label: '合计金额' },
      { value: 'totals.totalNet', label: '不含税金额' },
      { value: 'totals.totalTax', label: '税额' },
    ],
  },
]
const scalarGroups = computed(() =>
  docType.value === 'STATEMENT' ? statementScalarGroups : documentScalarGroups,
)

const statementItemFields: ItemField[] = [
  { value: 'item.seq', label: '序号', width: 12, align: 'center' },
  { value: 'item.bizDateText', label: '日期', width: 22, align: 'center' },
  { value: 'item.sourceNo', label: '送货单', width: 28, align: 'left' },
  { value: 'item.orderNo', label: '订单号', width: 28, align: 'left' },
  { value: 'item.partnerOrderNo', label: '客户单号', width: 28, align: 'left' },
  { value: 'item.brand', label: '品牌', width: 20, align: 'left' },
  { value: 'item.partNo', label: '型号', width: 30, align: 'left' },
  { value: 'item.packageType', label: '封装', width: 18, align: 'left' },
  { value: 'item.quantity', label: '数量', width: 15, align: 'right' },
  { value: 'item.unit', label: '单位', width: 12, align: 'center' },
  { value: 'item.unitPrice', label: '单价', width: 16, align: 'right' },
  { value: 'item.amountGross', label: '金额', width: 18, align: 'right' },
  { value: 'item.remark', label: '备注', width: 20, align: 'left' },
]
const documentItemFields: ItemField[] = [
  { value: 'item.seq', label: '序号', width: 12, align: 'center' },
  { value: 'item.code', label: '物料编码', width: 25, align: 'left' },
  { value: 'item.productName', label: '产品名称', width: 40, align: 'left' },
  { value: 'item.spec', label: '规格型号', width: 35, align: 'left' },
  { value: 'item.partNo', label: '型号', width: 30, align: 'left' },
  { value: 'item.packageType', label: '封装', width: 20, align: 'left' },
  { value: 'item.quantity', label: '数量', width: 15, align: 'right' },
  { value: 'item.unit', label: '单位', width: 12, align: 'center' },
  { value: 'item.unitPrice', label: '单价', width: 18, align: 'right' },
  { value: 'item.amountGross', label: '金额', width: 28, align: 'right' },
  { value: 'item.remark', label: '备注', width: 25, align: 'left' },
]
const itemFields = computed(() =>
  docType.value === 'STATEMENT' ? statementItemFields : documentItemFields,
)
const palette: Array<{ type: ComponentType; name: string; desc: string; icon: string }> = [
  { type: 'text', name: '文本', desc: '标题、说明、固定文字', icon: 'T' },
  { type: 'field', name: '字段', desc: '公司、往来单位、单据信息', icon: '#' },
  { type: 'table', name: '明细表格', desc: '逐行输出单据明细', icon: '▤' },
  { type: 'signature', name: '签章区', desc: '双方确认与盖章位置', icon: '✓' },
]

function uid() {
  return `c${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`
}
function blankLayout(): StatementLayout {
  const base = { fontSize: 10, bold: false, align: 'left' as const, border: false }
  const page = { size: 'A4' as const, orientation: 'PORTRAIT' as const, marginMm: 10, gridMm: 3 }
  if (docType.value === 'STATEMENT') {
    return {
      page,
      components: [
        { id: uid(), type: 'text', x: 15, y: 10, w: 180, h: 12, text: '{{header.title}}', fontSize: 20, bold: true, align: 'center' },
        { id: uid(), type: 'field', x: 15, y: 26, w: 105, h: 7, field: 'company.name', ...base, bold: true },
        { id: uid(), type: 'field', x: 120, y: 26, w: 75, h: 7, label: '对账期间：', field: 'header.periodLabel', ...base, align: 'right' },
        { id: uid(), type: 'field', x: 15, y: 35, w: 105, h: 7, label: direction.value === 'AP' ? '供应商：' : '客户：', field: 'partner.name', ...base },
        { id: uid(), type: 'field', x: 120, y: 35, w: 75, h: 7, label: '对账日期：', field: 'header.statementDateLabel', ...base, align: 'right' },
        {
          id: uid(), type: 'table', x: 15, y: 47, w: 180, h: 36,
          headerHeight: 8, rowHeight: 7, fontSize: 9, headerBold: true, border: true,
          columns: [
            { title: '序号', field: 'item.seq', width: 12, align: 'center' },
            { title: '送货日期', field: 'item.bizDateText', width: 25, align: 'center' },
            { title: '送货单号', field: 'item.sourceNo', width: 35 },
            { title: '客户订单号', field: 'item.partnerOrderNo', width: 33 },
            { title: '型号', field: 'item.partNo', width: 30 },
            { title: '数量', field: 'item.quantity', width: 15, align: 'right' },
            { title: '单价', field: 'item.unitPrice', width: 15, align: 'right' },
            { title: '金额', field: 'item.amountGross', width: 15, align: 'right' },
          ],
        },
        { id: uid(), type: 'text', x: 15, y: 90, w: 80, h: 7, text: '合计（大写）：', ...base, bold: true },
        { id: uid(), type: 'field', x: 100, y: 90, w: 95, h: 7, label: '合计金额：', field: 'totals.totalGross', ...base, align: 'right', bold: true },
        { id: uid(), type: 'text', x: 15, y: 102, w: 180, h: 7, text: '说明：以上明细请核对，如有异议请于收到后 3 个工作日内提出。', ...base },
        { id: uid(), type: 'signature', x: 15, y: 250, w: 180, h: 30, text: '客户确认（盖章）：                              供应商确认（盖章）：', ...base, border: true },
      ],
    }
  }
  if (docType.value === 'DELIVERY_NOTE') {
    // 与销售订单「发货单打印」同版式：A4 横向，三行表头信息 + 七列明细 + 签收与收货须知。
    const label = (x: number, y: number, w: number, h: number, text: string) =>
      ({ id: uid(), type: 'text' as const, x, y, w, h, text, ...base, bold: true, align: 'center' as const, border: true })
    const value = (x: number, y: number, w: number, h: number, field: string, align: 'left' | 'center' = 'left') =>
      ({ id: uid(), type: 'field' as const, x, y, w, h, field, ...base, align, border: true })
    return {
      page: { ...page, orientation: 'LANDSCAPE' },
      components: [
        { id: uid(), type: 'text', x: 10, y: 6, w: 277, h: 9, text: '{{company.name}}', fontSize: 16, bold: true, align: 'center' },
        { id: uid(), type: 'text', x: 10, y: 15, w: 277, h: 11, text: '{{header.title}}', fontSize: 20, bold: true, align: 'center' },
        { id: uid(), type: 'text', x: 177, y: 27, w: 110, h: 7, text: '编号：{{header.no}}', fontSize: 11, align: 'right' },
        label(10, 35, 39, 10, '客户名称：'),
        value(49, 35, 144, 10, 'partner.name'),
        label(193, 35, 39, 10, '日期：'),
        value(232, 35, 55, 10, 'header.dateLabel', 'center'),
        label(10, 45, 39, 12, '收货地址：'),
        value(49, 45, 144, 12, 'partner.address'),
        label(193, 45, 39, 12, '采购单号：'),
        value(232, 45, 55, 12, 'header.purchaseNo', 'center'),
        label(10, 57, 39, 10, '联系方式：'),
        value(49, 57, 144, 10, 'partner.phone'),
        label(193, 57, 39, 10, '联系人：'),
        value(232, 57, 55, 10, 'partner.contact', 'center'),
        {
          id: uid(), type: 'table', x: 10, y: 67, w: 277, h: 49,
          headerHeight: 9, rowHeight: 10, fontSize: 10, headerBold: true, border: true,
          columns: [
            { title: '序号', field: 'item.seq', width: 14, align: 'center' },
            { title: '物料编码', field: 'item.code', width: 61, align: 'center' },
            { title: '品名', field: 'item.productName', width: 44, align: 'center' },
            { title: '产品型号', field: 'item.partNo', width: 44, align: 'center' },
            { title: '单位', field: 'item.unit', width: 19, align: 'center' },
            { title: '数量', field: 'item.quantity', width: 39, align: 'center' },
            { title: '备注', field: 'item.remark', width: 56, align: 'center' },
          ],
        },
        { id: uid(), type: 'signature', x: 10, y: 118, w: 166, h: 11, text: '经办人：{{header.handler}}', ...base },
        { id: uid(), type: 'signature', x: 176, y: 118, w: 111, h: 11, text: '收货人签名：', ...base },
        { id: uid(), type: 'text', x: 10, y: 132, w: 277, h: 6, text: '收货注意事项：', ...base },
        { id: uid(), type: 'text', x: 10, y: 138, w: 277, h: 6, text: '1. 为了您的利益，请仔细检查外观并点数，核对完毕后再签收；', ...base },
        { id: uid(), type: 'text', x: 10, y: 144, w: 277, h: 6, text: '2. 包装箱外观损坏严重的情况下请拒收货物，包装箱破损较轻时请开箱查验后签收。', ...base },
        { id: uid(), type: 'text', x: 10, y: 152, w: 277, h: 6, text: '{{header.shippingSummary}}', ...base, fontSize: 9 },
      ],
    }
  }
  return {
    page,
    components: [
      { id: uid(), type: 'text', x: 15, y: 10, w: 180, h: 14, text: '{{header.title}}', fontSize: 20, bold: true, align: 'center' },
      { id: uid(), type: 'field', x: 15, y: 26, w: 180, h: 7, field: 'company.name', ...base, bold: true },
      { id: uid(), type: 'field', x: 15, y: 34, w: 110, h: 7, label: '往来单位：', field: 'partner.name', ...base },
      { id: uid(), type: 'field', x: 125, y: 34, w: 70, h: 7, label: '合同编号：', field: 'header.no', ...base, align: 'right' },
      { id: uid(), type: 'field', x: 15, y: 42, w: 110, h: 7, label: '联系人：', field: 'partner.contact', ...base },
      { id: uid(), type: 'field', x: 125, y: 42, w: 70, h: 7, label: '签订日期：', field: 'header.dateLabel', ...base, align: 'right' },
      {
        id: uid(), type: 'table', x: 15, y: 52, w: 180, h: 54,
        headerHeight: 8, rowHeight: 7, fontSize: 9, headerBold: true, border: true,
        columns: [
          { title: '序号', field: 'item.seq', width: 12, align: 'center' },
          { title: '产品名称', field: 'item.productName', width: 40 },
          { title: '规格型号', field: 'item.spec', width: 35 },
          { title: '封装', field: 'item.packageType', width: 20 },
          { title: '数量', field: 'item.quantity', width: 15, align: 'right' },
          { title: '单位', field: 'item.unit', width: 12, align: 'center' },
          { title: '单价', field: 'item.unitPrice', width: 18, align: 'right' },
          { title: '金额', field: 'item.amountGross', width: 28, align: 'right' },
        ],
      },
      { id: uid(), type: 'field', x: 15, y: 112, w: 60, h: 7, label: '合计数量：', field: 'totals.totalQuantity', ...base },
      { id: uid(), type: 'field', x: 105, y: 112, w: 90, h: 7, label: '合同总额：', field: 'totals.totalAmount', ...base, align: 'right', bold: true },
      { id: uid(), type: 'field', x: 15, y: 122, w: 180, h: 7, label: '付款方式：', field: 'header.paymentTerms', ...base },
      { id: uid(), type: 'field', x: 15, y: 130, w: 90, h: 7, label: '交货日期：', field: 'header.deliveryDate', ...base },
      { id: uid(), type: 'field', x: 15, y: 138, w: 180, h: 7, label: '交货地点：', field: 'header.deliveryPlace', ...base },
      { id: uid(), type: 'field', x: 15, y: 146, w: 180, h: 16, label: '其他约定：', field: 'header.remark', ...base },
      { id: uid(), type: 'signature', x: 15, y: 250, w: 180, h: 30, text: '甲方（盖章）：                                  乙方（盖章）：', ...base, border: true },
    ],
  }
}
function normalize(input: StatementLayout): StatementLayout {
  const page = input.page || ({} as StatementLayout['page'])
  return {
    page: {
      size: 'A4',
      orientation: page.orientation === 'LANDSCAPE' ? 'LANDSCAPE' : 'PORTRAIT',
      marginMm: page.marginMm || 10,
      gridMm: page.gridMm || 3,
    },
    components: (input.components || []).map(c => ({
      ...c,
      id: c.id || uid(),
      fontSize: c.fontSize || 10,
      bold: Boolean(c.bold),
      align: c.align || 'left',
      border: Boolean(c.border),
      columns: c.columns?.map(col => ({ ...col, width: col.width || 20 })),
    })),
  }
}
function fieldLabel(field?: string) {
  for (const group of scalarGroups.value) {
    const hit = group.options.find(o => o.value === field)
    if (hit) return hit.label
  }
  return field || '未绑定字段'
}
function sampleValue(field?: string) {
  if (!field) return ''
  return sampleValues.value[field] ?? ''
}
function sampleText(text?: string) {
  if (!text) return ''
  return text.replace(/\{\{\s*([A-Za-z0-9_.]+)\s*\}\}/g, (_, key: string) =>
    String(sampleValues.value[key] ?? ''),
  )
}
function textStyle(c: LayoutComponent) {
  return {
    fontSize: `${c.fontSize || 10}pt`,
    fontWeight: c.bold ? 600 : 400,
    textAlign: c.align || 'left',
    justifyContent: c.align === 'center' ? 'center' : c.align === 'right' ? 'flex-end' : 'flex-start',
    border: c.border ? '1px solid #333' : 'none',
  }
}
function itemTitle(field: string) {
  return itemFields.value.find(f => f.value === field)?.label || field
}
function itemValue(field: string, row: SampleRow) {
  const key = field.replace('item.', '')
  return String(row[key] ?? '')
}
function componentLabel(c: LayoutComponent) {
  if (c.type === 'text') return c.text?.slice(0, 12) || '文本'
  if (c.type === 'field') return fieldLabel(c.field)
  if (c.type === 'table') return '明细表格'
  return '签章区'
}
function tableRows(c: LayoutComponent) {
  const body = Math.max(0, Math.floor((c.h - (c.headerHeight || 8)) / (c.rowHeight || 7)))
  return Math.min(body, sampleItems.value.length)
}
function tableColumns(c: LayoutComponent) {
  return c.columns?.length ? c.columns : []
}
function columnWidth(c: LayoutComponent, column: LayoutColumn) {
  const sum = (c.columns || []).reduce((total, item) => total + (item.width || 0), 0)
  const width = sum > 0 ? ((column.width || 0) * c.w) / sum : 0
  return `${width.toFixed(2)}mm`
}

function select(c: LayoutComponent) {
  if (previewing.value) return
  selectedId.value = c.id
}
async function togglePreview() {
  if (previewing.value) {
    previewing.value = false
    zoom.value = zoomBeforePreview.value
    return
  }
  zoomBeforePreview.value = zoom.value
  selectedId.value = ''
  previewing.value = true
  await nextTick()
  const canvas = canvasRef.value
  if (!canvas) return
  const width = canvas.clientWidth - 48
  const height = canvas.clientHeight - 110
  const fit = Math.min(
    width / (paperWidth.value * PPMM),
    height / (paperHeight.value * PPMM),
    1,
  )
  zoom.value = Math.max(0.4, Number(fit.toFixed(2)))
}
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && previewing.value) void togglePreview()
}
function addComponent(type: ComponentType, x?: number, y?: number) {
  const component = defaultComponent(type)
  const maxX = paperWidth.value - component.w
  const left = x === undefined ? 15 : x
  component.x = Math.max(0, Math.min(maxX, snap(left)))
  const top = y === undefined ? freeY(component) : y
  component.y = Math.max(0, Math.min(paperHeight.value - component.h, snap(top)))
  layout.value.components.push(component)
  selectedId.value = component.id
  previewing.value = false
}
function freeY(component: LayoutComponent) {
  for (let y = 10; y <= paperHeight.value - component.h; y += 4) {
    const overlaps = layout.value.components.some(c =>
      component.x < c.x + c.w && component.x + component.w > c.x &&
      y < c.y + c.h && y + component.h > c.y,
    )
    if (!overlaps) return y
  }
  return 10
}
function defaultComponent(type: ComponentType): LayoutComponent {
  if (type === 'table') {
    return {
      id: uid(), type, x: 15, y: 20, w: 180, h: 36,
      headerHeight: 8, rowHeight: 7, fontSize: 9, headerBold: true, border: true,
      columns: blankLayout().components.find(c => c.type === 'table')?.columns?.slice(0, 5),
    }
  }
  if (type === 'field') {
    return { id: uid(), type, x: 15, y: 20, w: 90, h: 7, field: 'partner.name', label: '客户：', fontSize: 10, align: 'left' }
  }
  if (type === 'signature') {
    return {
      id: uid(), type, x: 15, y: 240, w: 180, h: 30,
      text: '客户确认（盖章）：                              供应商确认（盖章）：',
      fontSize: 10, border: true, align: 'left',
    }
  }
  return { id: uid(), type, x: 15, y: 20, w: 90, h: 8, text: '文本内容', fontSize: 10, align: 'left' }
}
function removeSelected() {
  const target = selected.value
  if (!target) return
  layout.value.components = layout.value.components.filter(c => c.id !== target.id)
  selectedId.value = ''
}

function snap(value: number) {
  return Math.round(value)
}
function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value))
}
function startDrag(event: PointerEvent, c: LayoutComponent, mode: DragMode) {
  if (previewing.value) return
  event.preventDefault()
  event.stopPropagation()
  selectedId.value = c.id
  dragState = {
    id: c.id, mode, startX: event.clientX, startY: event.clientY,
    x: c.x, y: c.y, w: c.w, h: c.h,
  }
}
function onPointerMove(event: PointerEvent) {
  if (!dragState) return
  const component = layout.value.components.find(c => c.id === dragState?.id)
  if (!component) return
  const dx = (event.clientX - dragState.startX) / zoom.value / PPMM
  const dy = (event.clientY - dragState.startY) / zoom.value / PPMM
  const min = 6
  if (dragState.mode === 'move') {
    component.x = clamp(snap(dragState.x + dx), 0, paperWidth.value - component.w)
    component.y = clamp(snap(dragState.y + dy), 0, paperHeight.value - component.h)
    return
  }
  let x = dragState.x
  let y = dragState.y
  let w = dragState.w
  let h = dragState.h
  if (dragState.mode.includes('e')) w = dragState.w + dx
  if (dragState.mode.includes('s')) h = dragState.h + dy
  if (dragState.mode.includes('w')) {
    w = dragState.w - dx
    x = dragState.x + dx
  }
  if (dragState.mode.includes('n')) {
    h = dragState.h - dy
    y = dragState.y + dy
  }
  if (w < min) {
    if (dragState.mode.includes('w')) x = dragState.x + dragState.w - min
    w = min
  }
  if (h < min) {
    if (dragState.mode.includes('n')) y = dragState.y + dragState.h - min
    h = min
  }
  component.x = clamp(snap(x), 0, paperWidth.value - min)
  component.y = clamp(snap(y), 0, paperHeight.value - min)
  component.w = clamp(snap(w), min, paperWidth.value - component.x)
  component.h = clamp(snap(h), min, paperHeight.value - component.y)
}
function endDrag() {
  dragState = null
}
function onDrop(event: DragEvent) {
  const type = event.dataTransfer?.getData('text/plain') as ComponentType
  if (!palette.some(p => p.type === type) || !paperRef.value) return
  const rect = paperRef.value.getBoundingClientRect()
  addComponent(
    type,
    (event.clientX - rect.left) / zoom.value / PPMM,
    (event.clientY - rect.top) / zoom.value / PPMM,
  )
}

function addColumn() {
  const table = selected.value
  if (!table || table.type !== 'table') return
  const columns = table.columns || []
  const next =
    itemFields.value.find(f => !columns.some(c => c.field === f.value)) ?? itemFields.value[0]
  if (!next) return
  columns.push({ title: next.label, field: next.value, width: next.width, align: next.align })
}
function removeColumn(index: number) {
  const table = selected.value
  if (!table || (table.columns?.length || 0) <= 1) {
    ElMessage.warning('明细表格至少保留一列')
    return
  }
  table.columns?.splice(index, 1)
}
function moveColumn(index: number, delta: number) {
  const columns = selected.value?.columns
  const target = index + delta
  if (!columns || target < 0 || target >= columns.length) return
  const [column] = columns.splice(index, 1)
  if (column) columns.splice(target, 0, column)
}
function syncColumnTitle(column: LayoutColumn) {
  const option = itemFields.value.find(f => f.value === column.field)
  if (option) column.title = option.label
}

function layoutError(): string | null {
  const components = layout.value.components
  if (!components.length) return '请至少添加一个组件'
  if (!components.some(c => c.type === 'table')) return '请添加明细表格组件'
  for (const c of components) {
    if (c.x < 0 || c.y < 0 || c.x + c.w > paperWidth.value || c.y + c.h > paperHeight.value)
      return `「${componentLabel(c)}」超出纸张范围`
    if (c.type === 'table' && !c.columns?.length) return '明细表格至少需要一列'
  }
  for (let i = 0; i < components.length; i++) {
    for (let j = i + 1; j < components.length; j++) {
      const a = components[i]
      const b = components[j]
      if (!a || !b) continue
      if (a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y)
        return `「${componentLabel(a)}」与「${componentLabel(b)}」位置重叠`
    }
  }
  return null
}
function openSave() {
  const error = layoutError()
  if (error) {
    ElMessage.warning(error)
    return
  }
  saveForm.value.name = saveForm.value.name || editing.value?.name || `${docTypeLabel.value}模板`
  if (editing.value) saveForm.value.partnerId = editing.value.partnerId
  saveOpen.value = true
}
async function submitSave() {
  if (!saveForm.value.name.trim()) {
    ElMessage.warning('请填写模板名称')
    return
  }
  saving.value = true
  try {
    const saved = await statementTemplateApi.saveLayout({
      docType: docType.value,
      direction: direction.value,
      partnerId: saveForm.value.partnerId,
      name: saveForm.value.name.trim(),
      isDefault: saveForm.value.isDefault,
      format: saveForm.value.format,
      layout: layout.value,
    })
    ElMessage.success(editing.value ? `已保存 v${saved.version}，历史单据仍按原版式` : '模板已保存')
    saveOpen.value = false
    router.push({ path: '/finance/templates', query: { docType: docType.value } })
  } catch (e) {
    ElMessage.error((e as Error).message || '模板保存失败')
  } finally {
    saving.value = false
  }
}
async function downloadPreview(format: StatementFormat = saveForm.value.format) {
  const error = layoutError()
  if (error) {
    ElMessage.warning(error)
    return
  }
  try {
    const file = await statementTemplateApi.previewLayout(
      layout.value,
      format,
      docType.value,
      direction.value,
    )
    const url = URL.createObjectURL(file.blob)
    const a = document.createElement('a')
    a.href = url
    a.download = file.fileName || `${docTypeLabel.value}预览.${format === 'PDF' ? 'pdf' : 'xlsx'}`
    a.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (e) {
    ElMessage.error((e as Error).message || '预览生成失败')
  }
}
async function loadPartners() {
  try {
    partners.value = await financeApi.partners(direction.value)
  } catch {
    partners.value = []
  }
}
async function load() {
  loading.value = true
  try {
    if (editingId) {
      const detail = await statementTemplateApi.layout(editingId)
      editing.value = detail.template
      docType.value = parseDocType(detail.template.docType)
      direction.value = detail.template.direction
      layout.value = normalize(detail.layout)
      saveForm.value = {
        partnerId: detail.template.partnerId,
        name: detail.template.name,
        isDefault: detail.template.isDefault === 1,
        format: detail.template.format,
      }
    } else {
      docType.value = parseDocType(route.query.docType)
      if (!['AR', 'AP'].includes(String(route.query.direction)) || !currentTypeDirectional()) {
        direction.value = fixedDirection(docType.value)
      }
      layout.value = blankLayout()
      saveForm.value.partnerId = Number(route.query.partnerId || 0)
      saveForm.value.name = ''
      saveForm.value.format = 'XLSX'
      saveForm.value.isDefault = true
    }
    await loadPartners()
  } catch (e) {
    ElMessage.error((e as Error).message || '模板加载失败')
  } finally {
    loading.value = false
  }
}
onMounted(() => {
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', endDrag)
  window.addEventListener('keydown', onKeydown)
  void load()
})
onBeforeUnmount(() => {
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', endDrag)
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <section v-loading="loading" class="designer-page">
    <header class="designer-toolbar">
      <div class="designer-toolbar-left">
        <el-button text :icon="ArrowLeft" @click="router.push('/finance/templates')">返回模板列表</el-button>
        <span class="designer-divider" />
        <strong>{{ editing ? '编辑' : '新建' }}{{ docTypeLabel }}模板</strong>
        <el-tag v-if="currentTypeDirectional()" size="small" effect="plain">
          {{ direction === 'AR' ? '应收 / 客户' : '应付 / 供应商' }}
        </el-tag>
        <span v-if="editing" class="designer-version">当前 v{{ editing.version }}</span>
      </div>
      <div class="designer-toolbar-right">
        <el-button-group>
          <el-button size="small" @click="zoom = Math.max(0.4, Number((zoom - 0.08).toFixed(2)))">－</el-button>
          <el-button size="small" class="designer-zoom">{{ Math.round(zoom * 100) }}%</el-button>
          <el-button size="small" @click="zoom = Math.min(1.2, Number((zoom + 0.08).toFixed(2)))">＋</el-button>
        </el-button-group>
        <el-button size="small" :type="previewing ? 'warning' : 'default'" :icon="View" @click="togglePreview">
          {{ previewing ? '退出预览' : '预览' }}
        </el-button>
        <el-dropdown trigger="click" @command="downloadPreview">
          <el-button size="small" :icon="Download">下载预览</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="XLSX">下载 Excel 预览</el-dropdown-item>
              <el-dropdown-item command="PDF">下载 PDF 预览</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button size="small" type="primary" @click="openSave">保存模板</el-button>
      </div>
    </header>

    <div class="designer-body" :class="{ 'is-preview': previewing }">
      <aside v-if="!previewing" class="designer-palette">
        <h3>组件</h3>
        <p class="designer-tip">点击或用鼠标拖到右侧纸张上</p>
        <div
          v-for="item in palette"
          :key="item.type"
          class="palette-item"
          draggable="true"
          @dragstart="(event) => event.dataTransfer?.setData('text/plain', item.type)"
          @click="addComponent(item.type)"
        >
          <span class="palette-icon">{{ item.icon }}</span>
          <span>
            <strong>{{ item.name }}</strong>
            <small>{{ item.desc }}</small>
          </span>
        </div>
        <h3 class="designer-palette-title">纸张</h3>
        <el-form label-width="62px" size="small" class="designer-page-form">
          <el-form-item label="方向">
            <el-select v-model="layout.page.orientation">
              <el-option label="A4 纵向" value="PORTRAIT" />
              <el-option label="A4 横向" value="LANDSCAPE" />
            </el-select>
          </el-form-item>
          <el-form-item label="页边距">
            <el-input-number v-model="layout.page.marginMm" :min="0" :max="30" :step="1" controls-position="right" />
          </el-form-item>
          <el-form-item label="网格">
            <el-input-number v-model="layout.page.gridMm" :min="3" :max="10" :step="1" controls-position="right" />
          </el-form-item>
        </el-form>
      </aside>

      <main
        ref="canvasRef"
        class="designer-canvas"
        :class="{ 'is-preview': previewing }"
        @dragover.prevent
        @drop="onDrop"
        @click="selectedId = ''"
      >
        <div v-if="previewing" class="designer-preview-tip">
          预览模式 · 当前显示样例数据，正式导出时按真实明细自动分页
          <el-button text type="warning" size="small" @click="togglePreview">退出预览（Esc）</el-button>
        </div>
        <div class="designer-stage" :style="{ width: `${stageWidth}px`, height: `${stageHeight}px` }">
          <div
            ref="paperRef"
            class="designer-paper"
            :class="{ 'is-preview': previewing }"
            :style="paperStyle"
            @click.stop
          >
            <div v-if="!previewing" class="designer-margin" :style="marginStyle" />
            <div
              v-for="c in layout.components"
              :key="c.id"
              class="designer-component"
              :class="{ selected: selectedId === c.id, 'is-preview': previewing }"
              :style="{ left: `${c.x}mm`, top: `${c.y}mm`, width: `${c.w}mm`, height: `${c.h}mm` }"
              @pointerdown="startDrag($event, c, 'move')"
              @click.stop="select(c)"
            >
              <template v-if="c.type === 'text'">
                <div class="component-text" :style="textStyle(c)">{{ sampleText(c.text) }}</div>
              </template>
              <template v-else-if="c.type === 'field'">
                <div class="component-text" :style="textStyle(c)">{{ c.label || '' }}{{ sampleValue(c.field) }}</div>
              </template>
              <template v-else-if="c.type === 'table'">
                <div
                  class="designer-table"
                  :style="{ fontSize: `${c.fontSize || 9}pt`, borderColor: '#7b8794' }"
                >
                  <div class="designer-table-row header" :style="{ height: `${c.headerHeight || 8}mm` }">
                    <div
                      v-for="(col, index) in tableColumns(c)"
                      :key="index"
                      class="designer-table-cell"
                      :class="`align-${col.align || 'left'}`"
                      :style="{ width: columnWidth(c, col) }"
                    >{{ col.title }}</div>
                  </div>
                  <div
                    v-for="(row, rowIndex) in sampleItems.slice(0, tableRows(c))"
                    :key="rowIndex"
                    class="designer-table-row"
                    :style="{ height: `${c.rowHeight || 7}mm` }"
                  >
                    <div
                      v-for="(col, index) in tableColumns(c)"
                      :key="index"
                      class="designer-table-cell"
                      :class="`align-${col.align || 'left'}`"
                      :style="{ width: columnWidth(c, col) }"
                    >{{ itemValue(col.field, row) }}</div>
                  </div>
                </div>
              </template>
              <template v-else>
                <div
                  class="component-text signature"
                  :style="{ fontSize: `${c.fontSize || 10}pt`, border: '1px solid #333' }"
                >{{ sampleText(c.text) }}</div>
              </template>
              <span v-if="!previewing && selectedId === c.id" class="component-name">{{ componentLabel(c) }}</span>
              <template v-if="!previewing && selectedId === c.id">
                <span class="resize-handle nw" @pointerdown.stop="startDrag($event, c, 'nw')" />
                <span class="resize-handle ne" @pointerdown.stop="startDrag($event, c, 'ne')" />
                <span class="resize-handle sw" @pointerdown.stop="startDrag($event, c, 'sw')" />
                <span class="resize-handle se" @pointerdown.stop="startDrag($event, c, 'se')" />
              </template>
            </div>
          </div>
        </div>
      </main>

      <aside v-if="!previewing" class="designer-inspector">
        <template v-if="selected">
          <div class="inspector-head">
            <h3>{{ componentLabel(selected) }}</h3>
            <el-button text type="danger" :icon="Delete" @click="removeSelected">删除</el-button>
          </div>
          <el-form label-width="64px" size="small" class="inspector-form">
            <div class="inspector-grid">
              <el-form-item label="X">
                <el-input-number v-model="selected.x" :min="0" :max="paperWidth - selected.w" controls-position="right" />
              </el-form-item>
              <el-form-item label="Y">
                <el-input-number v-model="selected.y" :min="0" :max="paperHeight - selected.h" controls-position="right" />
              </el-form-item>
              <el-form-item label="宽">
                <el-input-number v-model="selected.w" :min="6" :max="paperWidth - selected.x" controls-position="right" />
              </el-form-item>
              <el-form-item label="高">
                <el-input-number v-model="selected.h" :min="6" :max="paperHeight - selected.y" controls-position="right" />
              </el-form-item>
            </div>
            <el-form-item v-if="selected.type !== 'table'" label="字号">
              <el-input-number v-model="selected.fontSize" :min="5" :max="48" controls-position="right" />
              <el-checkbox v-model="selected.bold" class="inspector-check">加粗</el-checkbox>
            </el-form-item>
            <el-form-item v-if="selected.type === 'text' || selected.type === 'field'" label="对齐">
              <el-radio-group v-model="selected.align">
                <el-radio-button value="left">左</el-radio-button>
                <el-radio-button value="center">中</el-radio-button>
                <el-radio-button value="right">右</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="selected.type === 'text' || selected.type === 'signature'" label="文字">
              <el-input v-model="selected.text" type="textarea" :rows="3" />
            </el-form-item>
            <template v-if="selected.type === 'field'">
              <el-form-item label="前缀">
                <el-input v-model="selected.label" placeholder="例如：客户：" />
              </el-form-item>
              <el-form-item label="字段">
                <el-select v-model="selected.field" filterable style="width: 100%">
                  <el-option-group v-for="group in scalarGroups" :key="group.label" :label="group.label">
                    <el-option v-for="option in group.options" :key="option.value" :label="option.label" :value="option.value" />
                  </el-option-group>
                </el-select>
              </el-form-item>
            </template>
            <template v-if="selected.type === 'table'">
              <el-form-item label="表头高">
                <el-input-number v-model="selected.headerHeight" :min="5" :max="20" controls-position="right" style="width: 110px" />
                <span class="inspector-unit">mm</span>
              </el-form-item>
              <el-form-item label="行高">
                <el-input-number v-model="selected.rowHeight" :min="5" :max="20" controls-position="right" style="width: 110px" />
                <span class="inspector-unit">mm</span>
              </el-form-item>
              <el-form-item label="字号">
                <el-input-number v-model="selected.fontSize" :min="5" :max="16" controls-position="right" />
              </el-form-item>
              <div class="column-editor">
                <div class="column-editor-title">明细列</div>
                <div v-for="(col, index) in selected.columns" :key="index" class="column-row">
                  <el-select
                    v-model="col.field"
                    size="small"
                    class="column-field"
                    @change="syncColumnTitle(col)"
                  >
                    <el-option v-for="field in itemFields" :key="field.value" :label="itemTitle(field.value)" :value="field.value" />
                  </el-select>
                  <el-input-number v-model="col.width" size="small" :min="8" :max="80" controls-position="right" />
                  <el-button text size="small" :disabled="index === 0" @click="moveColumn(index, -1)">↑</el-button>
                  <el-button text size="small" :disabled="index === (selected.columns?.length || 1) - 1" @click="moveColumn(index, 1)">↓</el-button>
                  <el-button text size="small" type="danger" @click="removeColumn(index)">×</el-button>
                </div>
                <el-button text type="primary" size="small" :icon="Plus" @click="addColumn">添加列</el-button>
                <p class="inspector-hint">
                  列宽合计 {{ selected.columns?.reduce((sum, col) => sum + (col.width || 0), 0) }}mm，表格宽 {{ selected.w }}mm，保存时按比例缩放。
                </p>
              </div>
            </template>
            <el-form-item v-if="selected.type !== 'table'" label="边框">
              <el-switch v-model="selected.border" />
            </el-form-item>
          </el-form>
        </template>
        <div v-else class="inspector-empty">
          <h3>模板设置</h3>
          <p>点击纸张上的组件可调整位置、大小和内容。</p>
          <ul>
            <li>明细表格会按真实行数自动向下撑开，合计和签章区随之下移。</li>
            <li>画布只是版式预览，导出时使用本张单据的真实数据。</li>
            <li>保存后如需修改，在模板列表点「编辑」会生成新版本，历史单据不受影响。</li>
          </ul>
        </div>
      </aside>
    </div>

    <el-dialog v-model="saveOpen" :title="`保存${docTypeLabel}模板`" width="540px" :close-on-click-modal="false">
      <el-form label-width="88px">
        <el-form-item label="往来单位" required>
          <el-select v-model="saveForm.partnerId" filterable style="width: 100%">
            <el-option label="全局默认（所有往来单位）" :value="0" />
            <el-option v-for="p in partners" :key="p.id" :label="p.shortName || p.name" :value="p.id" />
          </el-select>
          <div class="inspector-hint">只影响这个往来单位；选择全局则影响所有同类单据。</div>
        </el-form-item>
        <el-form-item label="模板名称" required>
          <el-input v-model="saveForm.name" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="输出格式">
          <el-radio-group v-model="saveForm.format">
            <el-radio-button value="XLSX">Excel（.xlsx）</el-radio-button>
            <el-radio-button value="PDF">PDF（直接发送）</el-radio-button>
          </el-radio-group>
          <div class="inspector-hint">
            PDF 按 A4 分页，明细跨页自动重复表头；Excel 适合客户自己再编辑。
          </div>
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="saveForm.isDefault" />
          <span class="inspector-hint">同一往来单位只保留一个默认模板，出单时优先使用它。</span>
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon>
          <template #title>
            {{ editing ? `保存后新增 v${editing.version + 1}，出单时锁定版本。` : '保存后会出现在模板列表中，可随时继续设计。' }}
          </template>
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="saveOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitSave">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.designer-page {
  --designer-line: #dfe5ee;
  height: calc(100dvh - 112px);
  min-height: 560px;
  display: flex;
  flex-direction: column;
  background: #eef2f7;
  color: #173456;
}
.designer-toolbar {
  height: 48px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 14px;
  background: #fff;
  border-bottom: 1px solid var(--designer-line);
}
.designer-toolbar-left,
.designer-toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.designer-divider {
  width: 1px;
  height: 18px;
  background: var(--designer-line);
}
.designer-version {
  font-size: 12px;
  color: #8491a4;
}
.designer-zoom {
  min-width: 58px;
  color: #5c6b7f;
}
.designer-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 230px minmax(0, 1fr) 340px;
}
.designer-body.is-preview {
  grid-template-columns: minmax(0, 1fr);
}
.designer-palette,
.designer-inspector {
  background: #fff;
  overflow: auto;
  padding: 12px;
}
.designer-palette {
  border-right: 1px solid var(--designer-line);
}
.designer-inspector {
  border-left: 1px solid var(--designer-line);
}
.designer-palette h3,
.designer-inspector h3 {
  margin: 0 0 4px;
  font-size: 14px;
}
.designer-tip,
.inspector-hint {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.6;
  color: #8491a4;
}
.palette-item {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 8px;
  padding: 9px;
  border: 1px solid var(--designer-line);
  border-radius: 6px;
  background: #fbfcfe;
  cursor: grab;
  user-select: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.palette-item:hover {
  border-color: #00b89c;
  box-shadow: 0 2px 8px rgb(0 184 156 / 12%);
}
.palette-item strong,
.palette-item small {
  display: block;
}
.palette-item strong {
  font-size: 13px;
}
.palette-item small {
  margin-top: 2px;
  color: #8491a4;
  font-size: 11px;
  line-height: 1.35;
}
.palette-icon {
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: 5px;
  background: #e5f8f4;
  color: #009c84;
  font-weight: 700;
}
.designer-palette-title {
  margin-top: 16px !important;
}
.designer-page-form :deep(.el-form-item) {
  margin-bottom: 8px;
}
.designer-page-form :deep(.el-input-number) {
  width: 100%;
}
.designer-canvas {
  overflow: auto;
  padding: 24px;
  background:
    linear-gradient(90deg, rgb(23 52 86 / 3%) 1px, transparent 1px) 0 0 / 24px 24px,
    linear-gradient(rgb(23 52 86 / 3%) 1px, transparent 1px) 0 0 / 24px 24px,
    #eef2f7;
}
.designer-canvas.is-preview {
  background: #e9edf3;
}
.designer-preview-tip {
  width: fit-content;
  margin: 0 auto 16px;
  padding: 6px 14px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #8a5a00;
  background: #fff8e8;
  border: 1px solid #f0c36b;
  border-radius: 6px;
}
.designer-stage {
  position: relative;
  margin: 0 auto;
}
.designer-paper {
  position: absolute;
  top: 0;
  left: 0;
  transform-origin: top left;
  background-color: #fff;
  background-image:
    linear-gradient(to right, rgb(23 52 86 / 7%) 1px, transparent 1px),
    linear-gradient(to bottom, rgb(23 52 86 / 7%) 1px, transparent 1px);
  box-shadow: 0 6px 24px rgb(23 52 86 / 16%);
  user-select: none;
  touch-action: none;
}
.designer-paper.is-preview {
  background-image: none;
}
.designer-margin {
  position: absolute;
  border: 1px dashed #b7c3d3;
  pointer-events: none;
}
.designer-component {
  position: absolute;
  box-sizing: border-box;
  cursor: move;
  color: #111;
}
.designer-component.selected {
  outline: 1px solid #00b89c;
  outline-offset: 1px;
  z-index: 2;
}
.designer-component.is-preview {
  cursor: default;
}
.designer-component.selected.is-preview {
  outline: none;
}
.component-text {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: inherit;
  white-space: pre-wrap;
  line-height: 1.3;
  overflow: hidden;
  box-sizing: border-box;
  padding: 0 1mm;
}
.component-text.signature {
  align-items: flex-start;
  padding: 2mm;
}
.designer-table {
  width: 100%;
  height: 100%;
  overflow: hidden;
  border: 1px solid;
  border-right: 0;
  border-bottom: 0;
  box-sizing: border-box;
}
.designer-table-row {
  display: flex;
  overflow: hidden;
}
.designer-table-row.header {
  background: #f2f4f7;
  font-weight: 600;
}
.designer-table-cell {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: 0 1mm;
  border-right: 1px solid #7b8794;
  border-bottom: 1px solid #7b8794;
  box-sizing: border-box;
  overflow: hidden;
  white-space: nowrap;
}
.designer-table-cell.align-right {
  justify-content: flex-end;
  text-align: right;
}
.designer-table-cell.align-center {
  justify-content: center;
  text-align: center;
}
.component-name {
  position: absolute;
  top: -18px;
  left: -1px;
  padding: 1px 5px;
  border-radius: 3px 3px 0 0;
  background: #00b89c;
  color: #fff;
  font-size: 11px;
  white-space: nowrap;
  pointer-events: none;
}
.resize-handle {
  position: absolute;
  width: 7px;
  height: 7px;
  background: #fff;
  border: 1px solid #00b89c;
  border-radius: 1px;
}
.resize-handle.nw { top: -4px; left: -4px; cursor: nwse-resize; }
.resize-handle.ne { top: -4px; right: -4px; cursor: nesw-resize; }
.resize-handle.sw { bottom: -4px; left: -4px; cursor: nesw-resize; }
.resize-handle.se { right: -4px; bottom: -4px; cursor: nwse-resize; }
.inspector-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--designer-line);
}
.inspector-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 8px;
}
.inspector-grid :deep(.el-form-item) {
  margin-bottom: 10px;
}
.inspector-form :deep(.el-input-number) {
  width: 100%;
}
.inspector-check {
  margin-left: 8px;
  white-space: nowrap;
}
.inspector-unit {
  margin-left: 6px;
  color: #8491a4;
  font-size: 12px;
}
.inspector-hint {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: #8491a4;
}
.column-editor {
  width: 100%;
  margin: 2px 0 4px;
}
.column-editor-title {
  margin-bottom: 6px;
  color: #5c6b7f;
  font-size: 12px;
}
.column-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 6px;
  padding: 4px 6px;
  border: 1px solid #edf0f5;
  border-radius: 5px;
  background: #fbfcfe;
}
.column-field {
  flex: 1;
  min-width: 0;
}
.column-row :deep(.el-input-number) {
  width: 64px;
  flex-shrink: 0;
}
.column-row :deep(.el-button) {
  flex-shrink: 0;
  margin-left: 0;
  padding: 2px 4px;
}
.inspector-empty {
  font-size: 13px;
  line-height: 1.7;
  color: #5c6b7f;
}
.inspector-empty ul {
  padding-left: 18px;
}
@media (max-width: 1200px) {
  .designer-body {
    grid-template-columns: 210px minmax(0, 1fr) 300px;
  }
}
</style>
