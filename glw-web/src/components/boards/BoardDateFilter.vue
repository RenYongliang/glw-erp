<script setup lang="ts">
import { ref } from 'vue'
import { ArrowDown, Close } from '@element-plus/icons-vue'
import { dateOperators, presets, type DateFilter } from '../../domain/boardFilters'
defineProps<{ label: string }>()
const model = defineModel<DateFilter>({ required: true })
const visible = ref(false)
function changeOperator(operator: string) {
  model.value = { operator, value: '', range: [], preset: '' }
}
function choose(preset: string) {
  model.value = { ...model.value, preset, range: [] }
  visible.value = false
}
</script>
<template>
  <div class="board-filter date-filter" :data-filter="label">
    <div class="board-filter-heading">
      <span>{{ label }}</span
      ><el-dropdown trigger="click" @command="changeOperator"
        ><button class="operator-button" :aria-label="`${label}筛选方式`">
          {{ model.operator }}<el-icon><ArrowDown /></el-icon></button
        ><template #dropdown
          ><el-dropdown-menu
            ><el-dropdown-item
              v-for="operator in dateOperators"
              :key="operator"
              :command="operator"
              >{{ operator }}</el-dropdown-item
            ></el-dropdown-menu
          ></template
        ></el-dropdown
      >
    </div>
    <div v-if="['为空', '不为空'].includes(model.operator)" class="filter-empty-value">
      无需填写筛选值
    </div>
    <div v-else-if="model.operator === '动态筛选'" class="dynamic-date-controls">
      <el-popover v-model:visible="visible" trigger="click" :width="310" placement="bottom-start"
        ><template #reference
          ><button class="filter-value" :aria-label="label" :aria-expanded="visible">
            <span>{{ model.preset === 'custom' ? '自定义' : model.preset || ' ' }}</span
            ><el-icon><ArrowDown /></el-icon></button
        ></template>
        <div class="date-presets">
          <button class="date-custom" @click="choose('custom')">自定义</button>
          <div class="date-preset-grid">
            <button
              v-for="preset in presets"
              :key="preset"
              :class="{ selected: model.preset === preset }"
              @click="choose(preset)"
            >
              {{ preset }}
            </button>
          </div>
          <el-button v-if="model.preset" link type="primary" @click="choose('')"
            >清空日期筛选</el-button
          >
        </div></el-popover
      >
      <el-date-picker
        v-if="model.preset === 'custom'"
        :model-value="model.range"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        :aria-label="`${label}自定义范围`"
        unlink-panels
        @update:model-value="model = { ...model, range: $event || [] }"
      />
      <button
        v-else-if="model.preset"
        class="filter-clear"
        :aria-label="`清空${label}`"
        @click="choose('')"
      >
        <el-icon><Close /></el-icon>
      </button>
    </div>
    <el-date-picker
      v-else-if="model.operator === '选择范围'"
      :model-value="model.range"
      type="daterange"
      value-format="YYYY-MM-DD"
      start-placeholder="开始日期"
      end-placeholder="结束日期"
      :aria-label="label"
      unlink-panels
      @update:model-value="model = { ...model, range: $event || [] }"
    />
    <el-date-picker
      v-else
      :model-value="model.value"
      type="date"
      value-format="YYYY-MM-DD"
      placeholder="选择日期"
      :aria-label="label"
      @update:model-value="model = { ...model, value: $event || '' }"
    />
  </div>
</template>
