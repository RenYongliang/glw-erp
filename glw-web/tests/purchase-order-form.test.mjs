import test from 'node:test'
import assert from 'node:assert/strict'
import { validateOrder, purchaseSupplierName, purchaseContact, purchasePhone, orderTotals } from '../src/domain/purchase.ts'

const order = () => ({id:'new-order',code:'',name:'新供应商-采购订单',supplierId:'',supplierName:'新供应商',contact:'联系人',phone:'13800138000',signed:'2026-09-27',delivery:'',owner:'张睿',department:'采购部',warehouse:'杭州仓',address:'',discount:0,lines:[{id:'l1',productId:'p1',quantity:3,price:10,tax:13,delivery:'2026-12-20',remark:'独立包装'},{id:'l2',productId:'p2',quantity:2,price:20,tax:13,delivery:'2026-12-25',remark:''}],arrivals:[],payments:[],attachments:[],term:'月结30天',status:'待审批',reason:'',approver:'',approvedAt:''})
test('unregistered supplier and per-line dates persist without a hidden order delivery field',()=>{
 const o=JSON.parse(JSON.stringify(order()))
 assert.equal(validateOrder(o),'')
 assert.equal(purchaseSupplierName(o),'新供应商')
 assert.equal(purchaseContact(o),'联系人')
 assert.equal(purchasePhone(o),'13800138000')
 assert.equal(o.lines[0].remark,'独立包装')
 assert.equal(orderTotals(o).total,70)
})
test('new supplier validation rejects blank names, stale IDs and invalid line delivery dates',()=>{
 const o=order();o.supplierName='  ';assert.match(validateOrder(o),/供应商/)
 o.supplierName='新供应商';o.supplierId='missing';assert.match(validateOrder(o),/有效供应商/)
 o.supplierId='';o.lines[0].delivery='';assert.match(validateOrder(o),/交付日期/)
 o.lines[0].delivery='2026-09-01';assert.match(validateOrder(o),/不能早于采购日期/)
 o.lines[0].delivery='2026-12-20';o.lines[0].remark='字'.repeat(201);assert.match(validateOrder(o),/备注/)
})
test('legacy supplier contacts and order-level dates remain readable and valid',()=>{
 const o=order();o.supplierId='s1';delete o.supplierName;delete o.contact;delete o.phone;o.delivery='2026-12-20';o.lines.forEach(l=>delete l.delivery)
 assert.equal(validateOrder(o),'')
 assert.equal(purchaseSupplierName(o),'示例：杭州道友云')
 assert.equal(purchaseContact(o),'纪泽')
 assert.equal(purchasePhone(o),'17777777772')
 o.contact='自定义联系人';assert.equal(purchaseContact(o),'自定义联系人')
})
