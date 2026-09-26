import { products, purchaseSupplierName, purchaseContact, purchasePhone, orderTotals, chineseMoney, money, type PurchaseOrder } from './purchase'
import { deliveryNotePdf } from './deliveryNote'

export const contractHeaders = ['序号', '规格', '数量', '单位', '税率', '单价(含税)', '合计', '交付日期', '备注']
const widths = [48, 174, 90, 54, 65, 100, 108, 116, 293]
const W = 1120, H = 792
export function purchaseContractData(order: PurchaseOrder) {
  const totals = orderTotals(order)
  return {
    number: order.code || '待生成（草稿）',
    supplier: purchaseSupplierName(order), contact: purchaseContact(order), phone: purchasePhone(order), date: order.signed,
    rows: order.lines.map((line, index) => {
      const product = products.find(p => p.id === line.productId)
      return [String(index + 1), product?.model || '', String(line.quantity ?? ''), product?.unit || '', `${line.tax ?? 0}%`, money(line.price || 0), money(totals.details[index]?.total || 0), line.delivery ?? order.delivery, line.remark || '']
    }),
    quantity: order.lines.reduce((n,l) => n + (l.quantity || 0), 0),
    total: money(totals.total), uppercase: chineseMoney(totals.total),
    discount: order.discount,
  }
}
type Contract = ReturnType<typeof purchaseContractData>
type RowLayout = { cells: string[][]; height: number }
type PageLayout = { rows: RowLayout[] }
function wrap(ctx: CanvasRenderingContext2D, value: string, width: number): string[] {
  const result: string[] = []
  for (const paragraph of String(value || '').split(/\r?\n/)) {
    let line = ''
    for (const char of paragraph) {
      if (line && ctx.measureText(line + char).width > width) { result.push(line); line = '' }
      line += char
    }
    result.push(line)
  }
  return result
}
function layout(data: Contract, ctx: CanvasRenderingContext2D): PageLayout[] {
  ctx.font = '14px Arial, "Microsoft YaHei", sans-serif'
  const pages: PageLayout[] = [{ rows: [] }]
  let used = 0
  for (const values of data.rows) {
    const cells = values.map((v,i) => wrap(ctx, v, widths[i]! - 16))
    const height = Math.max(44, ...cells.map(c => c.length * 20 + 16))
    if (used + height > 332 && pages.at(-1)!.rows.length) { pages.push({rows: []}); used = 0 }
    pages.at(-1)!.rows.push({cells, height}); used += height
  }
  return pages
}
const escape = (s: unknown) => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]!))
export const purchaseContractStyles = `
 .purchase-contract-paper { box-sizing:border-box; width:1120px; min-height:792px; padding:32px 36px; margin:0 auto 20px; background:white; color:#111; font:14px Arial,"Microsoft YaHei",sans-serif; }
 .purchase-contract-paper * { box-sizing:border-box; }
 .purchase-contract-paper h1 { margin:0 0 20px; font-size:28px; text-align:center; }
 .purchase-contract-meta { display:grid; grid-template-columns:1fr 1fr; gap:12px 28px; min-height:115px; margin-bottom:16px; }
 .purchase-contract-meta .supplier { grid-column:1 / -1; overflow-wrap:anywhere; }
 .purchase-contract-paper table { border-collapse:collapse; width:100%; table-layout:fixed; }
 .purchase-contract-paper th,.purchase-contract-paper td { border:1px solid #333; padding:8px; font-size:14px; line-height:20px; vertical-align:middle; text-align:center; }
 .purchase-contract-paper th { height:40px; background:#f3f4f6; }
 .purchase-contract-paper td:last-child { text-align:left; }
 .purchase-contract-summary { margin-top:20px; display:flex; gap:60px; }
 .purchase-contract-uppercase { margin:14px 0; overflow-wrap:anywhere; }
 .purchase-contract-signatures { display:flex; justify-content:space-between; margin-top:36px; padding-right:90px; }
 .purchase-contract-page-number { margin-top:22px; text-align:right; color:#555; }
 @media print { @page {size:A4 landscape;margin:5mm;} body {margin:0;} .purchase-contract-paper {width:100%;min-height:0;margin:0;padding:0;break-after:page;page-break-after:always;box-shadow:none;} .purchase-contract-paper:last-child {break-after:auto;page-break-after:auto;} }
`
export function purchaseContractHtml(order: PurchaseOrder): { html: string; pages: number } {
  const data = purchaseContractData(order)
  const ctx = document.createElement('canvas').getContext('2d')!
  const pages = layout(data, ctx)
  const html = pages.map((page, i) => `<section class="purchase-contract-paper">
    <h1>采购合同</h1>
    <div class="purchase-contract-meta"><div>采购单号：${escape(data.number)}</div><div>采购日期：${escape(data.date)}</div><div class="supplier">供应商名称：${escape(data.supplier)}</div><div>联系人：${escape(data.contact)}</div><div>联系人手机：${escape(data.phone)}</div></div>
    <table><colgroup>${widths.map(w=>`<col style="width:${w/1048*100}%">`).join('')}</colgroup><thead><tr>${contractHeaders.map(h=>`<th>${h}</th>`).join('')}</tr></thead><tbody>${page.rows.map(row=>`<tr style="height:${row.height}px">${row.cells.map(cell=>`<td>${cell.map(escape).join('<br>')}</td>`).join('')}</tr>`).join('')}</tbody></table>
    <div class="purchase-contract-summary"><span>合计数量（整单）：${data.quantity}</span><span>合计金额（整单）：￥${escape(data.total)}</span></div><div class="purchase-contract-uppercase">合计金额大写：${escape(data.uppercase)}</div>
    ${data.discount ? `<div>已含原订单优惠：￥${escape(money(data.discount))}</div>` : ''}
    <div class="purchase-contract-signatures"><span>采购方签章：________________</span><span>供应商签章：________________</span></div>
    <div class="purchase-contract-page-number">第 ${i+1} / ${pages.length} 页</div>
  </section>`).join('')
  return { html, pages: pages.length }
}
export async function purchaseContractPdf(order: PurchaseOrder): Promise<Blob> {
  await document.fonts.ready
  const data = purchaseContractData(order)
  const canvas = document.createElement('canvas'); canvas.width = W * 2; canvas.height = H * 2
  const ctx = canvas.getContext('2d')!
  ctx.scale(2,2)
  const pages = layout(data,ctx)
  const images = pages.map((page,index) => {
    ctx.fillStyle = '#fff'; ctx.fillRect(0,0,W,H); ctx.fillStyle = '#111'; ctx.textBaseline = 'top'; ctx.textAlign = 'left'
    ctx.font = 'bold 28px Arial, "Microsoft YaHei", sans-serif'; ctx.textAlign = 'center'; ctx.fillText('采购合同',W/2,32); ctx.textAlign = 'left'
    ctx.font = '14px Arial, "Microsoft YaHei", sans-serif'
    ctx.fillText(`采购单号：${data.number}`,36,90); ctx.fillText(`采购日期：${data.date}`,580,90)
    wrap(ctx,`供应商名称：${data.supplier}`,1048).forEach((v,i)=>ctx.fillText(v,36,120+i*20))
    ctx.fillText(`联系人：${data.contact}`,36,168,500); ctx.fillText(`联系人手机：${data.phone}`,580,168)
    let y=208
    const drawRow=(cells:string[][],height:number,header=false)=>{
      let x=36
      cells.forEach((cell,i)=>{
        const width=widths[i]!
        if(header){ctx.fillStyle='#f3f4f6';ctx.fillRect(x,y,width,height)}
        ctx.strokeStyle='#333';ctx.lineWidth=1;ctx.strokeRect(x,y,width,height);ctx.fillStyle='#111'
        cell.forEach((line,j)=>{ctx.textAlign=i===8?'left':'center';ctx.fillText(line,i===8?x+8:x+width/2,y+(height-cell.length*20)/2+j*20)})
        x+=width
      });y+=height;ctx.textAlign='left'
    }
    drawRow(contractHeaders.map(h=>[h]),40,true)
    page.rows.forEach(row=>drawRow(row.cells,row.height))
    y+=20;ctx.fillText(`合计数量（整单）：${data.quantity}`,36,y);ctx.fillText(`合计金额（整单）：￥${data.total}`,580,y)
    y+=30;wrap(ctx,`合计金额大写：${data.uppercase}`,1048).forEach((line,i)=>ctx.fillText(line,36,y+i*20))
    if(data.discount){y+=26;ctx.fillText(`已含原订单优惠：￥${money(data.discount)}`,36,y)}
    y+=52;ctx.fillText('采购方签章：________________',36,y);ctx.fillText('供应商签章：________________',640,y)
    ctx.textAlign='right';ctx.fillText(`第 ${index+1} / ${pages.length} 页`,1084,756)
    return canvas.toDataURL('image/jpeg',0.95)
  })
  return deliveryNotePdf(images)
}
