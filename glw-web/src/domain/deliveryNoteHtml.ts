import type { DeliveryNote } from './deliveryNote'

const escape = (value: string | number) => String(value).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!)
export const deliveryNoteStyles = `
.delivery-paper{box-sizing:border-box;background:#fff;color:#000;font-family:SimSun,"Songti SC",serif;padding:28px;width:1120px;min-height:792px;margin:0 auto 18px;line-height:1.4}
.delivery-paper *{box-sizing:border-box}.delivery-paper h1{font-size:28px;text-align:center;margin:0 0 14px;font-weight:bold}.delivery-paper h2{font-size:30px;text-decoration:underline;text-underline-offset:6px;text-align:center;margin:0 0 12px}
.delivery-note-number{text-align:right;font-size:17px;margin-bottom:8px}.delivery-paper table{width:100%;border-collapse:collapse;table-layout:fixed;font-size:18px}.delivery-paper td,.delivery-paper th{border:1px solid #111;padding:6px 8px;overflow-wrap:anywhere;vertical-align:middle}.delivery-paper th{font-weight:bold;text-align:center}.delivery-meta th{width:14%}.delivery-meta td{height:36px}.delivery-meta .delivery-address{height:52px}.delivery-products th{height:34px;white-space:nowrap}.delivery-products th:first-child{padding:4px}.delivery-products td{text-align:center;height:44px}.delivery-paper .delivery-products{margin-top:-1px}.delivery-signatures{border:1px solid #111;border-top:0;display:flex;padding:8px;font-size:20px}.delivery-signatures span{width:60%}.delivery-signatures strong{width:40%}.delivery-notices{font-size:17px;margin-top:8px}.delivery-notices p{margin:3px 0}.delivery-shipping-summary{font-size:15px;overflow-wrap:anywhere}.delivery-page-number{text-align:right;font-size:13px;margin-top:10px}
@media print{@page{size:A4 landscape;margin:0}html,body{margin:0;padding:0}.delivery-paper{width:297mm;height:auto;min-height:210mm;padding:7mm;margin:0;break-after:page;box-shadow:none}.delivery-paper:last-child{break-after:auto}}
`

export function deliveryNoteHtml(note: DeliveryNote): string {
  const count = Math.ceil(note.lines.length / 6)
  return Array.from({length: count}, (_, page) => {
    const rows = note.lines.slice(page * 6, page * 6 + 6)
    const body = rows.map((row, index) => `<tr>${[page * 6 + index + 1, row.code, row.name, row.model, row.unit, row.quantity.toLocaleString('zh-CN'), row.remark].map(v => `<td>${escape(v)}</td>`).join('')}</tr>`)
    for (let i = rows.length; i < 3; i++) body.push(`<tr><td>${page * 6 + i + 1}</td><td>${i === rows.length ? '以下空白' : ''}</td><td></td><td></td><td></td><td></td><td></td></tr>`)
    return `<article class="delivery-paper" aria-label="送货单第 ${page + 1} 页">
      <h1>深圳市光联微电子有限公司</h1><h2>发货单</h2>
      <div class="delivery-note-number">出库单号：${escape(note.number)}</div>
      <table class="delivery-meta"><colgroup><col style="width:14%"><col style="width:52%"><col style="width:14%"><col style="width:20%"></colgroup><tbody>
      <tr><th>客户名称：</th><td>${escape(note.customer)}</td><th>日期：</th><td>${escape(note.date)}</td></tr>
      <tr><th>收货地址：</th><td class="delivery-address">${escape(note.address)}</td><th>采购单号：</th><td>${escape(note.purchaseNumber)}</td></tr>
      <tr><th>联系方式：</th><td>${escape(note.phone)}</td><th>联系人：</th><td>${escape(note.contact)}</td></tr></tbody></table>
      <table class="delivery-products"><colgroup>${[5,22,16,16,7,14,20].map(w=>`<col style="width:${w}%">`).join('')}</colgroup><thead><tr>${['序号','物料编码','品名','产品型号','单位','数量','备注'].map(v=>`<th>${v}</th>`).join('')}</tr></thead><tbody>${body.join('')}</tbody></table>
      <div class="delivery-signatures"><span>经办人：${escape(note.handler)}</span><strong>收货人签名：</strong></div>
      <div class="delivery-notices"><p>收货注意事项：</p><p>1. 为了您的利益，请仔细检查外观并点数，核对完毕后再签收；</p><p>2. 包装箱外观损坏严重的情况下请拒收货物，包装箱破损较轻时请开箱查验后签收。</p></div>
      <p class="delivery-shipping-summary">${escape(note.shippingSummary)}</p>
      ${count > 1 ? `<div class="delivery-page-number">第 ${page + 1} / ${count} 页</div>` : ''}</article>`
  }).join('')
}

