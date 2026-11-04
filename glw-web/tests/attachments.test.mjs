import test from 'node:test'
import assert from 'node:assert/strict'
import {
  SALES_ATTACHMENT_TYPES,
  attachmentTypeLabel,
  keepsUploadSlot,
  attachmentTypeOf,
  fileExtension,
  renumberAttachments,
  standardAttachmentName,
} from '../src/domain/attachments.ts'

const name = (overrides = {}) =>
  standardAttachmentName({
    orderCode: 'SO20260715001',
    typeLabel: '销售合同',
    partnerName: '华芯电子',
    date: '20260930',
    index: 0,
    total: 1,
    extension: '.pdf',
    ...overrides,
  })

test('单份附件不带序号', () => {
  assert.equal(name(), 'SO20260715001_销售合同_华芯电子_20260930.pdf')
})

test('同类多份按 _01、_02 编号', () => {
  assert.equal(name({ index: 0, total: 2 }), 'SO20260715001_销售合同_华芯电子_20260930_01.pdf')
  assert.equal(name({ index: 1, total: 2 }), 'SO20260715001_销售合同_华芯电子_20260930_02.pdf')
})

test('文件名中的空白和非法字符会被去掉', () => {
  assert.equal(
    name({ orderCode: 'SO 20260715001', partnerName: '华芯/电子 有限公司' }),
    'SO20260715001_销售合同_华芯电子有限公司_20260930.pdf',
  )
})

test('未保存订单使用 DRAFT 占位', () => {
  assert.equal(name({ orderCode: '' }), 'DRAFT_销售合同_华芯电子_20260930.pdf')
})

test('扩展名统一小写，无扩展名返回空串', () => {
  assert.equal(fileExtension('合同.PDF'), '.pdf')
  assert.equal(fileExtension('合同'), '')
})

test('重新编号只处理同一类型，并保留各文件上传日期', () => {
  const files = [
    { type: 'SALES_CONTRACT', name: '旧名', originalName: '合同A.PDF', uploadedAt: '20260930' },
    { type: 'STATEMENT', name: '对账单.pdf', originalName: '对账单.pdf', uploadedAt: '20261008' },
    { name: '历史附件.pdf' },
  ]
  renumberAttachments(files, 'SALES_CONTRACT', (file, index, total) =>
    standardAttachmentName({
      orderCode: 'SO20260715001',
      typeLabel: '销售合同',
      partnerName: '华芯电子',
      date: file.uploadedAt,
      index,
      total,
      extension: fileExtension(file.originalName || file.name),
    }),
  )
  assert.equal(files[0].name, 'SO20260715001_销售合同_华芯电子_20260930.pdf')
  assert.equal(files[1].name, '对账单.pdf')
  assert.equal(files[2].name, '历史附件.pdf')
  assert.equal(attachmentTypeOf(files[2]), 'OTHER')
})

test('同类型减少到一份后去掉序号', () => {
  const files = [
    { type: 'SALES_CONTRACT', name: 'a', originalName: 'a.pdf', uploadedAt: '20260930' },
  ]
  renumberAttachments(files, 'SALES_CONTRACT', (file, index, total) =>
    standardAttachmentName({
      orderCode: 'SO20260715001',
      typeLabel: '销售合同',
      partnerName: '华芯电子',
      date: file.uploadedAt,
      index,
      total,
      extension: '.pdf',
    }),
  )
  assert.equal(files[0].name, 'SO20260715001_销售合同_华芯电子_20260930.pdf')
})

test('固定类型填满后不再占格，多份类型始终保留待上传格', () => {
  const contract = SALES_ATTACHMENT_TYPES.find((t) => t.key === 'SALES_CONTRACT')
  const receipt = SALES_ATTACHMENT_TYPES.find((t) => t.key === 'DELIVERY_RECEIPT')
  const other = SALES_ATTACHMENT_TYPES.find((t) => t.key === 'OTHER')
  assert.equal(keepsUploadSlot(contract), false)
  assert.equal(keepsUploadSlot(receipt), true)
  assert.equal(keepsUploadSlot(other), true)
})

test('类型标签缺失时回退为其他', () => {
  assert.equal(attachmentTypeLabel(SALES_ATTACHMENT_TYPES, 'UNKNOWN'), '其他')
  assert.equal(attachmentTypeLabel(SALES_ATTACHMENT_TYPES, 'CUSTOMER_PO'), '客户采购订单')
})
