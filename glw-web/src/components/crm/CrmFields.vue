<script setup lang="ts">
import type { CrmField } from "../../domain/crm";
defineProps<{ fields: CrmField[]; readonly?: boolean }>();
const values = defineModel<Record<string, string | number>>({ required: true });
const members = ["管理员", "张睿", "李明", "陈静"];
const departments = ["杭州大区", "市场部", "生产部", "销售部"];
function memberChanged(key: string, value: string) {
  values.value[key] = value;
  if (key === "owner" && "customerId" in values.value)
    values.value.department =
      (
        {
          管理员: "销售部",
          张睿: "杭州大区",
          李明: "市场部",
          陈静: "生产部",
        } as Record<string, string>
      )[value] || "";
}
</script>
<template>
  <div class="crm-form-grid">
    <el-form-item
      v-for="field in fields"
      :key="field.key"
      :label="field.label"
      :required="field.required"
      :class="{ wide: field.kind === 'textarea' }"
    >
      <el-input
        v-if="field.kind === 'sn'"
        :model-value="values[field.key]"
        disabled
        placeholder="自动生成无需填写"
        :aria-label="field.label"
      />
      <el-select
        v-else-if="
          field.options.length || ['user', 'dept'].includes(field.kind)
        "
        :model-value="values[field.key] || undefined"
        :disabled="readonly || field.disabled"
        clearable
        filterable
        :aria-label="field.label"
        :placeholder="
          field.kind === 'user'
            ? '选择成员'
            : field.kind === 'dept'
              ? '选择部门'
              : '请选择'
        "
        @update:model-value="memberChanged(field.key, $event || '')"
      >
        <el-option
          v-for="option in field.options.length
            ? field.options.map((o) => o.value)
            : field.kind === 'user'
              ? members
              : departments"
          :key="option"
          :label="option"
          :value="option"
        />
      </el-select>
      <el-date-picker
        v-else-if="field.kind === 'datetime'"
        :model-value="values[field.key]"
        type="datetime"
        value-format="YYYY-MM-DD HH:mm"
        format="YYYY-MM-DD HH:mm"
        :disabled="readonly"
        :aria-label="field.label"
        @update:model-value="values[field.key] = $event || ''"
      />
      <el-input-number
        v-else-if="field.kind === 'number'"
        :model-value="
          values[field.key] === '' ? undefined : Number(values[field.key])
        "
        :min="0"
        :max="field.key === 'taxRate' ? 100 : undefined"
        :precision="2"
        :controls="false"
        :disabled="readonly"
        :aria-label="field.label"
        @update:model-value="values[field.key] = $event ?? ''"
      />
      <el-input
        v-else
        v-model="values[field.key]"
        :type="
          ['textarea', 'address'].includes(field.kind) ? 'textarea' : 'text'
        "
        :rows="field.kind === 'textarea' ? 4 : 2"
        :disabled="readonly || field.disabled"
        :aria-label="field.label"
        :placeholder="field.kind === 'address' ? '省、市、区及详细地址' : ''"
      />
    </el-form-item>
  </div>
</template>
