import test from 'node:test'
import assert from 'node:assert/strict'
import {emptySalesOrderFilters, matchesSalesOrderFilters, salesFulfillmentStatus, salesOrderMonth, salesFilterCount} from '../src/domain/salesOrderFilters.ts'

const catalog=[{id:'a',brand:'品牌甲',name:'型号A',model:'封装A'},{id:'b',brand:'品牌乙',name:'型号B',model:'封装B'}]
const order=()=>({id:'o',code:'SO-001',signed:'2026-09-29',delivery:'2026-10-01',purchaseNumber:'PO-12',term:'月结30天',owner:'张睿',orderStatus:'执行中',deliveryStatus:'未发货',lines:[{id:'l1',productId:'a',quantity:10,price:2},{id:'l2',productId:'b',quantity:5,price:10}]})
const shipment=(lines,confirmed=true,tag='出库')=>({orderId:'o',confirmed,tag,lines})
test('fulfillment counts only confirmed outbound quantities and checks every product line',()=>{
 const o=order();o.deliveryStatus=undefined;const full=[{lineId:'l1',arrived:10},{lineId:'l2',arrived:5}]
 assert.equal(salesFulfillmentStatus(o,[]),'未发货')
 for(const tag of ['出库','计划出库','库存冻结'])assert.equal(salesFulfillmentStatus(o,[shipment(full,false,tag)]),'未发货')
 assert.equal(salesFulfillmentStatus(o,[shipment(full,true,'库存冻结')]),'未发货')
 assert.equal(salesFulfillmentStatus(o,[shipment([{lineId:'l1',arrived:15}])]),'部分发货')
 assert.equal(salesFulfillmentStatus(o,[shipment([{lineId:'l1',arrived:6}]),shipment([{lineId:'l1',arrived:4},{lineId:'l2',arrived:5}])]),'全部发货')
 assert.equal(salesFulfillmentStatus({...o,lines:[]},[]),'未发货')
})
test('filters combine order text, dates, numeric limits and status inclusively',()=>{
 const f=emptySalesOrderFilters(),o=order();f.text.customer='客户甲';f.text.purchaseNumber='po-';f.dates.signed=['2026-09-29','2026-09-29'];f.numbers.total=[70,70];f.status='部分发货'
 assert.equal(matchesSalesOrderFilters(o,catalog,'客户甲',70,'部分发货',f),true)
 assert.equal(matchesSalesOrderFilters(o,catalog,'客户乙',70,'部分发货',f),false)
 assert.equal(matchesSalesOrderFilters(o,catalog,'客户甲',71,'部分发货',f),false)
 assert.equal(matchesSalesOrderFilters(o,catalog,'客户甲',70,'待发货',f),false)
 assert.equal(salesFilterCount(f),5)
 f.dates.signed=['2026-10-01','2026-10-30'];assert.equal(matchesSalesOrderFilters(o,catalog,'客户甲',70,'部分发货',f),false)
})
test('product criteria must match one line and zero is a valid range boundary',()=>{
 const f=emptySalesOrderFilters(),o=order();f.text.brands='品牌甲';f.text.productNames='型号B'
 assert.equal(matchesSalesOrderFilters(o,catalog,'客户甲',70,'待发货',f),false)
 f.text.productNames='型号A';f.numbers.prices=[0,2];f.numbers.ordered=[10,10];f.numbers.amounts=[20,20]
 assert.equal(matchesSalesOrderFilters(o,catalog,'客户甲',70,'待发货',f),true)
 f.numbers.prices=[0,0];assert.equal(matchesSalesOrderFilters(o,catalog,'客户甲',70,'待发货',f),false)
 assert.equal(salesFilterCount(emptySalesOrderFilters()),0)
})
test('month groups distinguish years and use sortable month names',()=>{
 assert.equal(salesOrderMonth('2026-09-29'),'2026年09月')
 assert.equal(salesOrderMonth('2025-09-01'),'2025年09月')
 assert.equal(salesOrderMonth(''),'未填写订单日期')
 assert.equal(salesOrderMonth('2026-13-01'),'未填写订单日期')
})
