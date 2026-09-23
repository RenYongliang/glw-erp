<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { use, init, type EChartsType } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { LabelLayout } from 'echarts/features'
import { CanvasRenderer } from 'echarts/renderers'

use([LineChart, GridComponent, TooltipComponent, CanvasRenderer, LabelLayout])
const props = defineProps<{ title: string; labels: string[]; values: number[] }>()
const host = ref<HTMLDivElement>()
let chart: EChartsType | undefined
let observer: ResizeObserver | undefined
function render() {
  const compact = (host.value?.clientWidth ?? 1000) < 500
  chart?.setOption({
    color: ['#318aff'],
    tooltip: {
      trigger: 'axis',
      valueFormatter: (value: unknown) =>
        `${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })} 元`,
    },
    grid: { left: compact ? 55 : 100, right: compact ? 25 : 52, top: 36, bottom: 36 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: props.labels,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e8ebf2' } },
      axisLabel: { color: '#657084', fontSize: 12, hideOverlap: true },
    },
    yAxis: {
      type: 'value',
      splitNumber: 4,
      axisLabel: {
        color: '#657084',
        fontSize: 12,
        formatter: (value: number) =>
          compact && Math.abs(value) >= 10000
            ? String(Number((value / 10000).toFixed(1))) + '万'
            : value.toLocaleString('en-US', { minimumFractionDigits: compact ? 0 : 2 }),
      },
      splitLine: { lineStyle: { color: '#edf0f5', type: 'dashed' } },
    },
    series: [
      {
        name: props.title,
        type: 'line',
        smooth: 0.25,
        symbol: 'circle',
        symbolSize: 6,
        showAllSymbol: true,
        data: props.values,
        lineStyle: { width: 1.5 },
        labelLayout: (params: { dataIndex?: number }) => ({
          hideOverlap: true,
          align: (params.dataIndex === 0
            ? 'left'
            : params.dataIndex === props.values.length - 1
              ? 'right'
              : 'center') as 'left' | 'right' | 'center',
        }),
        label: {
          show: !compact,
          position: 'top',
          fontSize: 12,
          color: '#536176',
          formatter: (params: { value: unknown }) =>
            Number(params.value).toLocaleString('en-US', { minimumFractionDigits: 2 }),
        },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: '#85bcf5' },
              { offset: 1, color: '#f5faff' },
            ],
          },
        },
      },
    ],
  })
}
watch(() => [props.title, props.labels, props.values], render)
onMounted(() => {
  if (!host.value) return
  chart = init(host.value)
  render()
  observer = new ResizeObserver(() => {
    chart?.resize()
    render()
  })
  observer.observe(host.value)
})
onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
})
</script>
<template>
  <section class="chart-card">
    <h3>{{ title }}</h3>
    <div
      ref="host"
      class="trend-chart"
      role="img"
      :aria-label="`${title}：${labels.map((label, i) => `${label} ${values[i]}元`).join('，')}`"
    />
  </section>
</template>
