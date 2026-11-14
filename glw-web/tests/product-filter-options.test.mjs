import test from 'node:test'
import assert from 'node:assert/strict'
import {
  brandOptions,
  packageTypeOptions,
  partNoOptions,
  productFilterOptions,
} from '../src/domain/productFilterOptions.ts'

const rows = productFilterOptions([
  { brand: '力芯微', name: 'ET5907', model: 'WLCSP20' },
  { brand: '力芯微', name: 'ET5A033YB', model: 'DFN1*1' },
  { brand: 'TI', name: 'TPS7A02', model: 'SOT23' },
])

test('品牌选项去重', () => {
  assert.deepEqual(brandOptions(rows), ['力芯微', 'TI'])
})

test('型号按品牌级联', () => {
  assert.deepEqual(partNoOptions(rows), ['ET5907', 'ET5A033YB', 'TPS7A02'])
  assert.deepEqual(partNoOptions(rows, '力芯微'), ['ET5907', 'ET5A033YB'])
  assert.deepEqual(partNoOptions(rows, 'TI'), ['TPS7A02'])
})

test('封装按品牌和型号级联', () => {
  assert.deepEqual(packageTypeOptions(rows, '力芯微', 'ET5907'), ['WLCSP20'])
  assert.deepEqual(packageTypeOptions(rows, '力芯微'), ['WLCSP20', 'DFN1*1'])
  assert.deepEqual(packageTypeOptions(rows, '', 'TPS7A02'), ['SOT23'])
})

test('缺少品牌或封装时不会产生空选项', () => {
  const incomplete = productFilterOptions([{ name: 'ONLY-MODEL' }])
  assert.deepEqual(brandOptions(incomplete), [])
  assert.deepEqual(packageTypeOptions(incomplete), [])
  assert.deepEqual(partNoOptions(incomplete), ['ONLY-MODEL'])
})
