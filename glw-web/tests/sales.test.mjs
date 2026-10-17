import test from 'node:test'
import assert from 'node:assert/strict'
import { orderTotals, remainingQuantity, availableStock, returnableQuantity, validateShipment, validateOrder, margin, products } from '../src/domain/sales.ts'

const order = () => ({id:'o1',code:'XSDD1',name:'测试订单',customerId:'c1',signed:'2026-09-24',delivery:'2026-09-30',owner:'张睿',department:'销售部',warehouse:'杭州仓',address:'',discount:0,lines:[{id:'l1',productId:products[0].id,quantity:100,price:2,listPrice:2.5,tax:13}],arrivals:[],payments:[],attachments:[],term:'月结30天',status:'审批通过',reason:'',approver:'张睿',approvedAt:''})
const state = () => ({orders:[order()],shipments:[],payments:[],returns:[],logs:[]})
const shipment = () => ({id:'s1',code:'XSCK1',orderId:'o1',warehouse:'杭州仓',lines:[{lineId:'l1',arrived:40,location:'HZ-A01'}],tag:'计划出库',confirmed:false,shippedAt:'',keeper:'陈晨',department:'仓储部',photos:[],customerConfirmed:false,customerReceivedAt:''})

test('sales totals distinguish list price from actual price and allocate discounts in cents',()=>{
 const o=order();o.discount=10;o.lines.push({id:'l2',productId:products[1].id,quantity:3,price:1.11,listPrice:1.5,tax:13});const t=orderTotals(o)
 assert.equal(t.gross,254.5);assert.equal(t.beforeDiscount,203.33);assert.equal(t.total,193.33)
 assert.equal(Math.round(t.details.reduce((sum,l)=>sum+l.total,0)*100),19333)
 assert.ok(margin(order())>=35);assert.equal(validateOrder(o),'')
 o.discount=204;assert.match(validateOrder(o),/优惠金额/)
})
test('planned and frozen quantities reserve order capacity and inventory; editing excludes itself',()=>{
 const s=state(), planned=shipment();s.shipments.push(planned)
 assert.equal(remainingQuantity(s,'o1','l1'),60);assert.equal(remainingQuantity(s,'o1','l1','s1'),100)
 assert.equal(availableStock(s,products[0].id,'杭州仓'),products[0].stock-40)
 assert.equal(availableStock(s,products[0].id,'无锡仓'),products[0].stock)
 assert.equal(validateShipment(planned,s),'');assert.equal(returnableQuantity(s,'o1','l1'),0)
 const next={...shipment(),id:'s2',lines:[{lineId:'l1',arrived:61,location:'A'}]};assert.match(validateShipment(next,s),/待出库数量/)
})
test('outbound confirmation and receipt confirmation enforce state and date ordering',()=>{
 const s=state(), r=shipment();r.confirmed=true;r.shippedAt='2026-09-24 10:00'
 assert.match(validateShipment(r,s),/切换标签/);r.tag='出库';assert.equal(validateShipment(r,s),'')
 r.customerConfirmed=true;r.customerReceivedAt='2026-09-24 09:00';assert.match(validateShipment(r,s),/收货时间/)
 r.customerReceivedAt='2026-09-24 11:00';assert.equal(validateShipment(r,s),'')
 r.lines[0].location='';assert.equal(validateShipment(r,s),'')
})
test('outbound rejects unapproved orders, duplicate lines, wrong warehouses and stock shortages',()=>{
 const s=state(),r=shipment();s.orders[0].status='待审批';assert.match(validateShipment(r,s),/可发货/)
 for (const status of ['待发货','部分发货']) {s.orders[0].status=status;assert.equal(validateShipment(r,s),'')}
 s.orders[0].status='审批通过';r.warehouse='无锡仓';assert.match(validateShipment(r,s),/仓库/);r.warehouse='杭州仓'
 r.lines.push({...r.lines[0]});assert.match(validateShipment(r,s),/重复/);r.lines.pop()
 r.lines[0].arrived=0;assert.match(validateShipment(r,s),/大于 0/)
 s.orders[0].lines[0].quantity=products[0].stock+1;r.lines[0].arrived=products[0].stock+1;assert.match(validateShipment(r,s),/库存/)
})
test('returns count only confirmed shipments and restore stock without permitting duplicate returns',()=>{
 const s=state(),r=shipment();r.tag='出库';r.confirmed=true;r.shippedAt='2026-09-24 10:00';s.shipments.push(r)
 s.returns.push({id:'ret1',orderId:'o1',lineId:'l1',quantity:10,date:'2026-09-24',reason:'测试'})
 assert.equal(returnableQuantity(s,'o1','l1'),30);assert.equal(availableStock(s,products[0].id,'杭州仓'),products[0].stock-30)
})
