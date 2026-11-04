/**
 * 订单附件：固定类型占位 + 统一命名。
 *
 * 附件按业务类型排好固定槽位，上传后统一命名为
 * 「单据号_类型_往来单位_日期[_序号].扩展名」，同类多份时按 _01、_02 顺延。
 */
export interface AttachmentTypeDef {
  key: string
  label: string
  /** 界面上标记为必传；当前只做提示，不阻断保存。 */
  required?: boolean
  /**
   * 槽位数量来源：fixed=固定 1 个；shipments/receipts/statements=按出库/入库/对账单数量；
   * unlimited=不限数量，始终保留一个加号占位。
   */
  slots?: 'fixed' | 'shipments' | 'receipts' | 'statements' | 'unlimited'
}

export const SALES_ATTACHMENT_TYPES: AttachmentTypeDef[] = [
  { key: 'SALES_CONTRACT', label: '销售合同', required: true, slots: 'fixed' },
  { key: 'COUNTERSIGNED_CONTRACT', label: '客户盖章合同', slots: 'fixed' },
  { key: 'CUSTOMER_PO', label: '客户采购订单', slots: 'fixed' },
  { key: 'DELIVERY_RECEIPT', label: '发货回签', slots: 'shipments' },
  { key: 'STATEMENT', label: '对账单', slots: 'statements' },
  { key: 'OTHER', label: '其他', slots: 'unlimited' },
]

export const PURCHASE_ATTACHMENT_TYPES: AttachmentTypeDef[] = [
  { key: 'PURCHASE_CONTRACT', label: '采购合同', required: true, slots: 'fixed' },
  { key: 'SUPPLIER_COUNTERSIGNED', label: '供应商盖章合同', slots: 'fixed' },
  { key: 'SUPPLIER_QUOTE', label: '供应商报价单', slots: 'fixed' },
  { key: 'RECEIPT_SIGN', label: '收货回签', slots: 'receipts' },
  { key: 'INVOICE', label: '发票', slots: 'receipts' },
  { key: 'OTHER', label: '其他', slots: 'unlimited' },
]

/** 可能有多份的类型（发/收货回签、对账单、发票、其他）始终保留一个待上传格。 */
export function keepsUploadSlot(type: AttachmentTypeDef): boolean {
  return type.slots !== 'fixed'
}

/** 历史附件没有类型字段，统一归入“其他”。 */
export function attachmentTypeOf(file: { type?: string }): string {
  return file.type || 'OTHER'
}

export function attachmentTypeLabel(types: AttachmentTypeDef[], key?: string): string {
  return types.find((item) => item.key === key)?.label ?? '其他'
}

/** 取原始扩展名（含点、转小写）；无扩展名时返回空串。 */
export function fileExtension(name: string): string {
  const index = name.lastIndexOf('.')
  return index > 0 ? name.slice(index).toLowerCase() : ''
}

/** 文件名中不允许的字符与空白统一去掉，避免下载后被系统截断。 */
function clean(value: string): string {
  return (value || '').replace(/[\\/:*?"<>|\s]+/g, '')
}

/** 生成统一文件名：单据号_类型_往来单位_日期[_序号].扩展名 */
export function standardAttachmentName(options: {
  orderCode: string
  typeLabel: string
  partnerName: string
  date: string
  index: number
  total: number
  extension: string
}): string {
  const base = [
    clean(options.orderCode) || 'DRAFT',
    clean(options.typeLabel),
    clean(options.partnerName),
    clean(options.date),
  ].join('_')
  const seq = options.total > 1 ? `_${String(options.index + 1).padStart(2, '0')}` : ''
  return `${base}${seq}${options.extension}`
}

/** 同一类型下统一重命名：只有一份时不带序号，多份时按 _01、_02 依次编号。 */
export function renumberAttachments<T extends { name: string; type?: string }>(
  files: T[],
  type: string,
  nameFor: (file: T, index: number, total: number) => string,
): void {
  const group = files.filter((file) => attachmentTypeOf(file) === type)
  group.forEach((file, index) => {
    file.name = nameFor(file, index, group.length)
  })
}
