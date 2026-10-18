import test from 'node:test'
import assert from 'node:assert/strict'
import { nextDocumentNumber } from '../src/domain/documentNumbers.ts'
import { nextPurchaseCode } from '../src/domain/purchase.ts'
import { nextSalesCode } from '../src/domain/sales.ts'

test('document types and business dates have independent three-digit daily sequences', () => {
  const rows = [{ code: 'SO20261018002' }, { code: 'GL20261018009' }, { code: 'IN20261017099' },
    { code: 'SO20261018008' }, { code: 'SO2026101800x' }, { code: 'SO202610180001' }]
  assert.equal(nextSalesCode('SO', rows, '2026-10-18'), 'SO20261018009')
  assert.equal(nextPurchaseCode('GL', rows, '2026-10-18'), 'GL20261018010')
  assert.equal(nextPurchaseCode('IN', rows, '2026-10-18 09:00:00'), 'IN20261018001')
  assert.equal(nextSalesCode('OUT', rows, '2026-10-18'), 'OUT20261018001')
  assert.equal(nextDocumentNumber('SO', rows, '2026-10-19'), 'SO20261019001')
})

test('fixed three-digit sequences reject overflow and unsupported prefixes', () => {
  assert.equal(nextDocumentNumber('SO', [{ code: 'SO20261018098' }], '2026-10-18'), 'SO20261018099')
  assert.equal(nextDocumentNumber('SO', [{ code: 'SO20261018998' }], '2026-10-18'), 'SO20261018999')
  assert.throws(() => nextDocumentNumber('SO', [{ code: 'SO20261018999' }], '2026-10-18'), /999/)
  assert.throws(() => nextDocumentNumber('CGDD', [], '2026-10-18'), /类型或日期/)
})
