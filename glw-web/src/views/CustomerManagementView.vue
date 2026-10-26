<script setup lang="ts">
import CrmBusinessPage from '../components/crm/CrmBusinessPage.vue'
defineProps<{ kind: 'customers' | 'followups' }>()
</script>

<template>
  <CrmBusinessPage :key="kind" :kind="kind" />
</template>
