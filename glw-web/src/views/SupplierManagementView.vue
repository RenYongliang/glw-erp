<script setup lang="ts">
import SupplierBusinessPage from '../components/supplier/SupplierBusinessPage.vue'
defineProps<{ kind: 'suppliers' | 'brands' }>()
</script>

<template>
  <SupplierBusinessPage :key="kind" :kind="kind" />
</template>
