<script setup lang="ts">
defineProps<{ title: string; value: number; unit: string; money?: boolean }>()
</script>
<template>
  <article class="metric-card">
    <h3>{{ title }}</h3>
    <div class="metric-number">
      {{
        value.toLocaleString('zh-CN', {
          minimumFractionDigits: money ? 2 : 0,
          maximumFractionDigits: money ? 2 : 0,
        })
      }}<span>{{ unit }}</span>
    </div>
  </article>
</template>
