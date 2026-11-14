import { nextTick, onBeforeUnmount, ref, type Ref } from 'vue'

type TableInstance = { $el?: HTMLElement } | undefined

/**
 * 给 el-table 配一个固定在可视区域底部的横向滚动条。
 * 表格自身无需滚动到最底部即可横向拖动，滚动位置与表格双向同步。
 */
export function useStickyTableScroll(table: Ref<TableInstance>) {
  const scroller = ref<HTMLElement>()
  const scrollWidth = ref(0)
  let wrap: HTMLElement | null = null

  function tableWrap(): HTMLElement | null {
    return (table.value?.$el as HTMLElement | undefined)
      ?.querySelector<HTMLElement>('.el-scrollbar__wrap') ?? null
  }
  function syncFromTable() {
    if (scroller.value && wrap) scroller.value.scrollLeft = wrap.scrollLeft
  }
  function onScroll() {
    if (scroller.value && wrap) wrap.scrollLeft = scroller.value.scrollLeft
  }
  async function refresh() {
    await nextTick()
    wrap = tableWrap()
    if (!wrap) return
    wrap.removeEventListener('scroll', syncFromTable)
    wrap.addEventListener('scroll', syncFromTable, { passive: true })
    scrollWidth.value = wrap.scrollWidth
    syncFromTable()
  }
  const onResize = () => void refresh()
  window.addEventListener('resize', onResize)
  onBeforeUnmount(() => {
    wrap?.removeEventListener('scroll', syncFromTable)
    window.removeEventListener('resize', onResize)
  })
  return { scroller, scrollWidth, refresh, onScroll }
}
