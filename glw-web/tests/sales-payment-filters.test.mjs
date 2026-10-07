import test from 'node:test'
import assert from 'node:assert/strict'
import { emptySalesOrderFilters, salesPaymentStatus, matchesSalesOrderFilters, salesFilterCount } from '../src/domain/salesOrderFilters.ts'

test('receipt status uses actual paid amount and cent precision', () => {
  assert.equal(salesPaymentStatus(100,0),'未收款')
  assert.equal(salesPaymentStatus(100,99.99),'部分收款')
  assert.equal(salesPaymentStatus(0.3,0.1+0.2),'已收款')
  assert.equal(salesPaymentStatus(100,100),'已收款')
})
test('customer selection is exact and combines with payment status', () => {
  const filters=emptySalesOrderFilters()
  const order={code:'SO-1',term:'月结',owner:'',lines:[]}
  filters.text.customer='客户甲'
  filters.paymentStatus='部分收款'
  assert.equal(salesFilterCount(filters),2)
  assert.equal(matchesSalesOrderFilters(order,[],'客户甲',100,'待发货',filters,40),true)
  assert.equal(matchesSalesOrderFilters(order,[],'客户甲分公司',100,'待发货',filters,40),false)
  assert.equal(matchesSalesOrderFilters(order,[],'客户甲',100,'待发货',filters,100),false)
  assert.equal(matchesSalesOrderFilters(order,[],'客户甲',100,'待发货',filters,0),false)
})
