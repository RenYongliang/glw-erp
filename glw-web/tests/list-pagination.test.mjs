import test from 'node:test'
import assert from 'node:assert/strict'
import { ref, nextTick, effectScope } from 'vue'
import { useListPagination } from '../src/composables/useListPagination.ts'

test('pagination slices records, changes size and resets when filtered records shrink', async () => {
  const scope = effectScope()
  try {
    const rows = ref(Array.from({length:45}, (_,id) => ({id})))
    const pagination = scope.run(() => useListPagination(rows))
    assert.equal(pagination.pageRows.value.length,20)
    pagination.page.value=3
    assert.deepEqual(pagination.pageRows.value.map(r=>r.id),[40,41,42,43,44])
    pagination.pageSize.value=10
    await nextTick()
    assert.equal(pagination.page.value,1)
    assert.equal(pagination.pageRows.value.length,10)
    pagination.page.value=4
    rows.value=rows.value.slice(0,2)
    await nextTick()
    assert.equal(pagination.page.value,1)
    assert.deepEqual(pagination.pageRows.value.map(r=>r.id),[0,1])
    rows.value=[]
    await nextTick()
    assert.equal(pagination.page.value,1)
    assert.deepEqual(pagination.pageRows.value,[])
  } finally {scope.stop()}
})
