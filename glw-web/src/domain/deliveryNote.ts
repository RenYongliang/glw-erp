import catalog from '../data/products.json' with { type: 'json' }
import {
  customers,
  orderCustomerName,
  products,
  shipmentMatchesOrder,
  today,
  type SalesOrder,
  type SalesShipment,
} from './sales'

export interface DeliveryNoteLine {
  code: string
  name: string
  model: string
  unit: string
  quantity: number
  remark: string
}
export interface DeliveryNote {
  number: string
  date: string
  customer: string
  address: string
  phone: string
  contact: string
  purchaseNumber: string
  handler: string
  shippingSummary: string
  lines: DeliveryNoteLine[]
}

export function createDeliveryNote(
  order: SalesOrder,
  shipment: SalesShipment,
): DeliveryNote {
  const customer = customers.find((c) => c.id === order.customerId)
  const date = shipment.shippedAt.slice(0, 10) || today()
  const suffix = shipment.code
    ? shipment.code.replace(/^(?:OUT|XSCK)/, '')
    : shipment.id.replace(/-/g, '').slice(0, 8).toUpperCase()
  const shipmentLines = shipment.lines.filter((line) =>
    order.lines.some((item) => item.id === line.lineId),
  )
  if (
    !shipmentMatchesOrder(shipment, order.id) ||
    new Set(shipmentLines.map((l) => l.lineId)).size !==
      shipmentLines.length ||
    shipmentLines.some((l) => {
      const source = order.lines.find((item) => item.id === l.lineId)
      return (
        !source ||
        !Number.isSafeInteger(l.arrived) ||
        l.arrived < 0 ||
        l.arrived > source.quantity
      )
    })
  )
    throw Error('请核对发货产品及数量，不能超过订单数量')
  const lines = shipmentLines
    .filter((line) => line.arrived > 0)
    .map((line) => {
      const orderLine = order.lines.find((l) => l.id === line.lineId)
      const product = products.find((p) => p.id === orderLine?.productId)
      if (!product || !Number.isSafeInteger(line.arrived))
        throw Error('请填写有效的本次发货数量')
      return {
        code: product.code,
        name: catalog.find((p) => p.code === product.code)?.type || '集成电路',
        model: product.name,
        unit: 'pcs',
        quantity: line.arrived,
        remark: line.remark || '',
      }
    })
  if (!lines.length) throw Error('请先添加产品并填写本次发货数量')
  return {
    number: shipment.code
      ? `GL${suffix}`
      : `GL${date.replace(/-/g, '')}-${suffix}`,
    date: date.replace(/-/g, '/'),
    customer: orderCustomerName(order),
    address: shipment.shipping?.address ?? order.address ?? '',
    phone: shipment.shipping?.phone ?? customer?.phone ?? '',
    contact: shipment.shipping?.recipient ?? customer?.contact ?? '',
    purchaseNumber: order.purchaseNumber || '',
    handler: order.owner,
    shippingSummary: [shipment.shipping?.carrier, shipment.shipping?.trackingNumber && `运单号：${shipment.shipping.trackingNumber}`, shipment.shipping?.remark && `备注：${shipment.shipping.remark}`].filter(Boolean).join('；'),
    lines,
  }
}

const W = 1120,
  H = 792
export async function renderDeliveryNote(
  note: DeliveryNote,
): Promise<string[]> {
  await document.fonts.ready
  const pages: string[] = []
  for (let start = 0; start < note.lines.length; start += 6) {
    const canvas = document.createElement('canvas')
    canvas.width = W * 2
    canvas.height = H * 2
    const ctx = canvas.getContext('2d')
    if (!ctx) throw Error('浏览器无法生成发货单，请更换浏览器后重试')
    ctx.scale(2, 2)
    ctx.fillStyle = '#fff'
    ctx.fillRect(0, 0, W, H)
    ctx.fillStyle = '#000'
    ctx.strokeStyle = '#000'
    ctx.lineWidth = 1
    const font = (size: number, bold = false) =>
      `${bold ? 'bold ' : ''}${size}px SimSun, "Songti SC", serif`
    const wrap = (value: string, width: number): string[] => {
      const result: string[] = []
      let line = ''
      for (const c of value) {
        if (c === '\n' || ctx.measureText(line + c).width > width) {
          result.push(line)
          line = c === '\n' ? '' : c
        } else line += c
      }
      result.push(line)
      return result
    }
    const text = (
      value: string,
      x: number,
      y: number,
      width: number,
      height: number,
      size = 18,
      bold = false,
      align: CanvasTextAlign = 'left',
    ) => {
      let lines: string[]
      do {
        ctx.font = font(size, bold)
        lines = wrap(value, width - 12)
        if (lines.length * size * 1.25 <= height - 4) break
        size -= 0.5
      } while (size > 6)
      ctx.textBaseline = 'middle'
      ctx.textAlign = align
      const tx =
        align === 'center'
          ? x + width / 2
          : align === 'right'
            ? x + width - 6
            : x + 6
      lines.forEach((line, i) =>
        ctx.fillText(
          line,
          tx,
          y + height / 2 + (i - (lines.length - 1) / 2) * size * 1.25,
        ),
      )
    }
    const line = (x1: number, y1: number, x2: number, y2: number) => {
      ctx.beginPath()
      ctx.moveTo(x1, y1)
      ctx.lineTo(x2, y2)
      ctx.stroke()
    }
    const box = (x: number, y: number, width: number, height: number) =>
      ctx.strokeRect(x, y, width, height)
    text('深圳市光联微电子有限公司', 28, 20, 1064, 42, 29, true, 'center')
    text('送货单', 28, 68, 1064, 42, 32, true, 'center')
    line(493, 109, 627, 109)
    text(`编号：${note.number}`, 600, 112, 492, 30, 17, true, 'right')
    const labels = ['客户名称：', '收货地址：', '联系方式：']
    const values = [note.customer, note.address, note.phone]
    const rightLabels = ['日期：', '采购单号：', '联系人：']
    const rightValues = [note.date, note.purchaseNumber, note.contact]
    const heights = [36, 52, 36]
    let y = 146
    for (let i = 0; i < 3; i++) {
      const h = heights[i]!
      box(28, y, 1064, h)
      line(178, y, 178, y + h)
      line(735, y, 735, y + h)
      line(880, y, 880, y + h)
      text(labels[i]!, 28, y, 150, h, 20, true, 'center')
      text(values[i]!, 178, y, 557, h)
      text(rightLabels[i]!, 735, y, 145, h, 20, i !== 2, 'center')
      text(rightValues[i]!, 880, y, 212, h)
      y += h
    }
    const widths = [55, 230, 170, 175, 77, 145, 212]
    const tableRow = (values: string[], h: number, bold = false) => {
      let x = 28
      values.forEach((value, index) => {
        box(x, y, widths[index]!, h)
        text(value, x, y, widths[index]!, h, 19, bold, 'center')
        x += widths[index]!
      })
      y += h
    }
    tableRow(
      ['序号', '物料编码', '品名', '产品型号', '单位', '数量', '备注'],
      34,
      true,
    )
    const rows = note.lines.slice(start, start + 6)
    rows.forEach((row, index) =>
      tableRow(
        [
          String(start + index + 1),
          row.code,
          row.name,
          row.model,
          row.unit,
          row.quantity.toLocaleString('zh-CN'),
          row.remark,
        ],
        44,
      ),
    )
    for (let i = rows.length; i < 3; i++)
      tableRow(
        [
          String(start + i + 1),
          i === rows.length ? '以下空白' : '',
          '',
          '',
          '',
          '',
          '',
        ],
        44,
      )
    box(28, y, 1064, 38)
    text(`经办人： ${note.handler}`, 28, y, 570, 38, 20)
    text('收货人签名：', 650, y, 400, 38, 20, true)
    y += 42
    text('收货注意事项：', 28, y, 1064, 26, 18)
    text(
      '1. 为了您的利益，请仔细检查外观并点数，核对完毕后再签收；',
      28,
      y + 28,
      1064,
      26,
      18,
    )
    text(
      '2. 包装箱外观损坏严重的情况下请拒收货物，包装箱破损较轻时请开箱查验后签收。',
      28,
      y + 56,
      1064,
      28,
      18,
    )
    if (note.shippingSummary) text(note.shippingSummary, 28, y + 88, 1064, 32, 15)
    if (note.lines.length > 6)
      text(
        `第 ${start / 6 + 1} / ${Math.ceil(note.lines.length / 6)} 页`,
        28,
        754,
        1064,
        22,
        13,
        false,
        'right',
      )
    pages.push(canvas.toDataURL('image/jpeg', 0.96))
  }
  return pages
}

// Embed the exact preview pages into an A4 landscape PDF; Chinese glyphs are
// rasterized by the browser, so printing/downloading needs no remote font service.
export function deliveryNotePdf(pages: string[]): Blob {
  if (!pages.length) throw Error('发货单内容为空')
  const encoder = new TextEncoder(),
    chunks: Uint8Array[] = [],
    offsets: number[] = [0]
  let length = 0
  const push = (value: string | Uint8Array) => {
    const bytes = typeof value === 'string' ? encoder.encode(value) : value
    chunks.push(bytes)
    length += bytes.length
  }
  const object = (id: number, body: string) => {
    offsets[id] = length
    push(`${id} 0 obj\n${body}\nendobj\n`)
  }
  push('%PDF-1.4\n')
  object(1, '<< /Type /Catalog /Pages 2 0 R >>')
  object(
    2,
    `<< /Type /Pages /Count ${pages.length} /Kids [${pages.map((_, i) => `${3 + i * 3} 0 R`).join(' ')}] >>`,
  )
  pages.forEach((page, i) => {
    const id = 3 + i * 3
    object(
      id,
      `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 841.89 595.28] /Resources << /XObject << /PageImage ${id + 1} 0 R >> >> /Contents ${id + 2} 0 R >>`,
    )
    const bytes = Uint8Array.from(atob(page.split(',')[1]!), (c) =>
      c.charCodeAt(0),
    )
    offsets[id + 1] = length
    push(
      `${id + 1} 0 obj\n<< /Type /XObject /Subtype /Image /Width ${W * 2} /Height ${H * 2} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${bytes.length} >>\nstream\n`,
    )
    push(bytes)
    push('\nendstream\nendobj\n')
    const stream = 'q\n841.89 0 0 595.28 0 0 cm\n/PageImage Do\nQ\n'
    object(
      id + 2,
      `<< /Length ${encoder.encode(stream).length} >>\nstream\n${stream}endstream`,
    )
  })
  const xref = length,
    count = 3 + pages.length * 3
  push(`xref\n0 ${count}\n0000000000 65535 f \n`)
  for (let i = 1; i < count; i++)
    push(`${String(offsets[i]).padStart(10, '0')} 00000 n \n`)
  push(`trailer\n<< /Size ${count} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`)
  return new Blob(chunks as BlobPart[], { type: 'application/pdf' })
}
