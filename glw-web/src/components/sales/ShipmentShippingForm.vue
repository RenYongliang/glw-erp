<script setup lang="ts">
import type { ShippingInfo } from '../../domain/sales'

const model = defineModel<ShippingInfo>({ required: true })
defineProps<{ disabled?: boolean; required?: boolean }>()
const emit = defineEmits<{ change: [] }>()
</script>

<template>
  <el-form
    label-position="top"
    class="shipment-shipping-form"
    :disabled="disabled"
    @input="emit('change')"
    @change="emit('change')"
  >
    <el-form-item label="收货人" :required="required"
      ><el-input
        v-model="model.recipient"
        aria-label="收货人"
        maxlength="80"
        placeholder="填写本次收货人"
    /></el-form-item>
    <el-form-item label="收货联系电话" :required="required"
      ><el-input
        v-model="model.phone"
        aria-label="收货联系电话"
        maxlength="40"
        placeholder="手机或固定电话"
    /></el-form-item>
    <el-form-item label="收货地址" :required="required" class="shipping-wide"
      ><el-input
        v-model="model.address"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 4 }"
        aria-label="收货地址"
        maxlength="300"
        placeholder="省 / 市 / 区、街道及详细地址；自提请填写提货地点"
    /></el-form-item>
    <el-form-item label="物流公司"
      ><el-input
        v-model="model.carrier"
        aria-label="物流公司"
        maxlength="80"
        placeholder="如顺丰、德邦"
    /></el-form-item>
    <el-form-item label="物流单号"
      ><el-input v-model="model.trackingNumber" aria-label="物流单号" maxlength="100"
    /></el-form-item>
    <el-form-item label="发货备注" class="shipping-wide"
      ><el-input
        v-model="model.remark"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 4 }"
        aria-label="发货备注"
        maxlength="200"
        placeholder="送货时间要求、包装或签收注意事项"
    /></el-form-item>
  </el-form>
</template>

<style scoped>
.shipment-shipping-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 0 18px;
}
.shipping-wide {
  grid-column: 1 / -1;
}
.shipment-shipping-form :deep(.el-form-item) {
  margin-bottom: 16px;
  min-width: 0;
}
@media (max-width: 600px) {
  .shipment-shipping-form {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
