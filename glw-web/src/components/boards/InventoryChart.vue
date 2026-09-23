<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { init, use, type EChartsType } from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
use([BarChart, GridComponent, TooltipComponent, CanvasRenderer])
const props = defineProps<{ data: { name: string; value: number }[] }>()
const host = ref<HTMLElement>()
let chart: EChartsType | undefined
let observer: ResizeObserver | undefined
function render() {
  chart?.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { top: 28, left: 42, right: 15, bottom: 65 },
    xAxis: {
      type: 'category',
      data: props.data.map((d) => d.name),
      axisLabel: { rotate: 25, interval: 0, fontSize: 11 },
      axisTick: { show: false },
    },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#edf0f5' } } },
    series: [
      {
        name: '当前可用库存数量',
        type: 'bar',
        barMaxWidth: 42,
        label: { show: true, position: 'top', color: '#1384ff' },
        data: props.data.map((d, i) => ({
          value: d.value,
          itemStyle: {
            color: ['#63b2ee', '#76bcf0', '#489fe8', '#9cd2f2', '#78bfee', '#5aa8e9'][i % 6],
          },
        })),
      },
    ],
  })
}
watch(() => props.data, render, { deep: true })
onMounted(() => {
  chart = init(host.value!)
  render()
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(host.value!)
})
onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
})
</script>
<template>
  <div
    ref="host"
    class="inventory-chart"
    role="img"
    :aria-label="`产品可用库存：${data.map((d) => `${d.name} ${d.value}件`).join('，')}`"
  />
</template>
