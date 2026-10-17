import test from 'node:test'
import assert from 'node:assert/strict'
import { toSalesProductOption, formatUnitCost, formatCostRange } from '../src/services/productApiData.ts'
import { validateOrder, products } from '../src/domain/sales.ts'

test('real product options retain identifiers, snapshots and four decimal batch costs without a sale price', () => {
  const product = toSalesProductOption({id: 17, brand: 'TI', partNo: 'TPS63020', packageType: 'WLCSP12(0.4 Pitch)',
    stockQuantity: 300, estimatedCost: '0.0750', minCost: '0.0500', maxCost: '0.1000'})
  assert.equal(product.id, '17')
  assert.equal(product.name, 'TPS63020')
  assert.equal(product.model, 'WLCSP12(0.4 Pitch)')
  assert.equal(product.stock, 300)
  assert.equal(product.price, 0)
  assert.equal(formatUnitCost(product.estimatedCost), '0.0750')
  assert.equal(formatCostRange(product), '0.0500 ~ 0.1000')
  assert.equal(formatCostRange({...product, minCost: 0, maxCost: 0}), '0.0000')
})

test('products with no inventory preserve unknown costs and remain valid to order above stock', () => {
  const product = toSalesProductOption({id: 99999, brand: null, partNo: 'No stock', packageType: null,
    stockQuantity: 0, estimatedCost: null, minCost: null, maxCost: null})
  assert.equal(formatUnitCost(product.estimatedCost), '—')
  assert.equal(formatCostRange(product), '—')
  const order = {customerId: '', customerName: '客户', name: '订单', signed: '2026-10-17', delivery: '2026-10-18',
    warehouse: '杭州仓', discount: 0, arrivals: [], payments: [],
    lines: [{id: 'line', productId: product.id, quantity: 1000, price: 0.075, tax: 13}]}
  assert.ok(!products.some(p => p.id === product.id))
  assert.equal(validateOrder(order, [product]), '')
  assert.match(validateOrder(order, []), /产品/)
})
