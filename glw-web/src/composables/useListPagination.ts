import { computed, ref, watch, type Ref } from 'vue'

export function useListPagination<T>(rows: Readonly<Ref<T[]>>) {
  const page = ref(1)
  const pageSize = ref(20)
  const pageRows = computed(() => rows.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
  watch([rows, pageSize], () => { page.value = 1 }, { deep: true })
  return { page, pageSize, pageRows }
}
