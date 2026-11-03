import test from 'node:test'
import assert from 'node:assert/strict'
import { emptyPurchaseOrderFilters, purchaseFilterCount, purchaseReceiptStatus, matchesPurchaseOrderFilters } from '../src/domain/purchaseOrderFilters.ts'
const order={id:'o',code:'PO-123',signed:'2026-10-08',orderStatus:'执行中',lines:[{id:'l1',productId:'a',quantity:10},{id:'l2',productId:'b',quantity:5}]}
const catalog=[{id:'a',brand:'甲品牌',model:'ABC',packaging:'QFN14'},{id:'b',brand:'乙品牌',model:'XYZ',packaging:'SOP16'}]
const receipt=(confirmed,qty=10,rejected=0)=>({orderId:'o',confirmed,lines:[{lineId:'l1',arrived:qty,rejected},{lineId:'l2',arrived:5,rejected:0}]})
test('receipt status counts only confirmed accepted quantities for every ordered product',()=>{
 assert.equal(purchaseReceiptStatus(order,[]),'未收货')
 assert.equal(purchaseReceiptStatus(order,[receipt(false)]),'未收货')
 assert.equal(purchaseReceiptStatus(order,[receipt(true,10,1)]),'部分收货')
 assert.equal(purchaseReceiptStatus(order,[receipt(true)]),'全部收货')
 assert.equal(purchaseReceiptStatus(order,[{...receipt(true),orderId:'other'}]),'未收货')
 assert.equal(purchaseReceiptStatus(order,[{...receipt(true),orderId:'other',orderIds:['other','o']}]),'全部收货')
})
test('supplier, payment, receipt, order code and inclusive dates combine',()=>{
 const f=emptyPurchaseOrderFilters();Object.assign(f,{code:'po-1',supplier:'供应商甲',paymentStatus:'部分付款',receiptStatus:'全部收货',dates:['2026-10-08','2026-10-08']})
 assert.equal(purchaseFilterCount(f),5)
 const match=(paid,supplier='供应商甲',receipts=[receipt(true)])=>matchesPurchaseOrderFilters(order,catalog,supplier,100,paid,receipts,f)
 assert.equal(match(30),true);assert.equal(match(0),false);assert.equal(match(100),false)
 assert.equal(match(30,'供应商甲分公司'),false);assert.equal(match(30,'供应商甲',[receipt(false)]),false)
 f.dates=['2026-10-09','2026-10-10'];assert.equal(match(30),false)
})
test('all product filters match the same line using purchase model and packaging fields',()=>{
 const f=emptyPurchaseOrderFilters();f.product={brand:'甲品牌',model:'abc',packaging:'qfn14'}
 assert.equal(matchesPurchaseOrderFilters(order,catalog,'',100,0,[],f),true)
 f.product.packaging='SOP16';assert.equal(matchesPurchaseOrderFilters(order,catalog,'',100,0,[],f),false)
 f.product={brand:'',model:'',packaging:''};f.paymentStatus='已付款'
 assert.equal(matchesPurchaseOrderFilters(order,catalog,'',0.3,0.1+0.2,[],f),true)
})
