import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { emptyTextFilter, emptyDateFilter, matchesText, matchesDate, presetRange } from '../src/domain/boardFilters.ts'

test('text operators support inclusion, exclusion, empty and reset', () => {
  const f = { ...emptyTextFilter(), values: ['杭州仓', '无锡仓'] }
  assert.equal(matchesText('杭州仓', f), true)
  assert.equal(matchesText('上海仓', f), false)
  assert.equal(matchesText('杭州仓', { ...f, operator: '不等于任意一个' }), false)
  assert.equal(matchesText('上海仓', { ...f, operator: '不等于任意一个' }), true)
  assert.equal(matchesText('无锡简道云', { ...f, operator: '包含', text: '简道' }), true)
  assert.equal(matchesText('无锡简道云', { ...f, operator: '不包含', text: '简道' }), false)
  assert.equal(matchesText('', { ...f, operator: '为空' }), true)
  assert.equal(matchesText('', { ...f, operator: '不为空' }), false)
  assert.equal(matchesText('', { ...f, values: [''] }), true)
  assert.equal(matchesText('任意值', emptyTextFilter()), true)
})
test('date operators and ranges include both selected endpoints', () => {
  const f = { ...emptyDateFilter(), operator: '选择范围', range: ['2026-07-01', '2026-08-31'] }
  for (const day of f.range) assert.equal(matchesDate(day, f), true)
  assert.equal(matchesDate('2026-09-01', f), false)
  assert.equal(matchesDate('2026-06-30', f), false)
  assert.equal(matchesDate('2026-08-31', { ...f, operator: '动态筛选', preset: 'custom' }), true)
  assert.equal(matchesDate('2026-08-31', { ...f, operator: '大于等于', value: '2026-08-31' }), true)
  assert.equal(matchesDate('2026-08-31', { ...f, operator: '不等于', value: '2026-08-31' }), false)
  assert.equal(matchesDate('2026-08-31', emptyDateFilter()), true)
})
test('dynamic dates handle month end, leap years, quarters and Monday-based weeks', () => {
  const date = d => `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
  const range = (preset, now) => presetRange(preset, new Date(now + 'T12:00:00')).map(date)
  assert.deepEqual(range('上月','2024-03-31'), ['2024-02-01','2024-03-01'])
  assert.deepEqual(range('下月','2026-12-31'), ['2027-01-01','2027-02-01'])
  assert.deepEqual(range('上季度','2026-01-31'), ['2025-10-01','2026-01-01'])
  assert.deepEqual(range('下季度','2026-12-31'), ['2027-01-01','2027-04-01'])
  assert.deepEqual(range('本周','2026-09-27'), ['2026-09-21','2026-09-28'])
  assert.deepEqual(range('上周','2026-09-21'), ['2026-09-14','2026-09-21'])
  assert.deepEqual(range('近7天','2026-01-03'), ['2025-12-28','2026-01-04'])
})
test('source data is complete and uses the original sales/purchase metric definitions', () => {
  const data = JSON.parse(readFileSync(new URL('../src/data/orderBoards.json', import.meta.url),'utf8'))
  assert.equal(data.sales.orders.length,19)
  assert.equal(data.purchase.orders.length,24)
  assert.equal(data.sales.execution.length,29)
  assert.equal(data.purchase.execution.length,47)
  const distribution = { 1: 0, 2: 0, 3: 0 }
  const packages = { ET7303Y: 'QFN14', ET8211: 'SOP16', ET5A033YB: 'DFN*1', ET5907: 'WLCSP6(0.4 Pitch)' }
  for (const order of data.sales.orders) {
    distribution[order.lines.length]++
    assert.equal(order.amount, order.lines.reduce((sum, line) => sum + line.amount, 0))
    for (const line of order.lines) {
      assert.equal(line.brand, '力芯微')
      assert.ok(Object.hasOwn(packages, line.product))
      assert.equal(line.package, packages[line.product])
      assert.equal(line.amount, Math.round(line.quantity * line.price * 100) / 100)
      const progress = data.sales.execution.find(row => row.id === order.id && row.code === line.code)
      assert.ok(progress)
      assert.equal(progress.product, line.product)
      assert.equal(progress.quantity, line.quantity)
      assert.equal(progress.shipped + progress.pending, line.quantity)
      assert.equal(progress.settled + progress.unsettled, progress.shipped)
      assert.equal(progress.planned + progress.unplanned, progress.pending)
      assert.equal(progress.payable, Math.round(progress.settled * line.price * 100) / 100)
      assert.equal(progress.paid + progress.unpaid, progress.payable)
    }
  }
  assert.deepEqual(distribution, { 1: 13, 2: 2, 3: 4 })
  assert.equal(data.purchase.orders.flatMap(o=>o.lines).reduce((sum,l)=>sum+l.amount,0),3435860)
  for(const kind of ['sales','purchase']) {
    for(const line of data[kind].execution) assert.ok(data[kind].orders.some(o=>o.id===line.id && o.name===line.name))
    assert.equal(new Set(data[kind].execution.map(l=>l.id+'|'+l.code)).size,data[kind].execution.length)
  }
})

