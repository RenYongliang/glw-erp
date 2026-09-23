<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowDown, Close } from '@element-plus/icons-vue'
import { textOperators, type TextFilter } from '../../domain/boardFilters'
const props = defineProps<{ label: string; options: string[] }>()
const model = defineModel<TextFilter>({ required: true })
const search = ref('')
const visible = ref(false)
const choices = computed(() =>
  ['', ...props.options].filter((value) => (value || '未填写').includes(search.value)),
)
const multiple = computed(() => model.value.operator.includes('任意'))
const empty = computed(() => ['为空', '不为空'].includes(model.value.operator))
const input = computed(() => ['包含', '不包含'].includes(model.value.operator))
const selectedText = computed(() => model.value.values.map((value) => value || '未填写').join('、'))
function changeOperator(operator: string) {
  model.value = { operator, values: [], text: '' }
  search.value = ''
}
function toggle(value: string) {
  const values = multiple.value
    ? model.value.values.includes(value)
      ? model.value.values.filter((v) => v !== value)
      : [...model.value.values, value]
    : [value]
  model.value = { ...model.value, values }
  if (!multiple.value) visible.value = false
}
function selectAll() {
  const all = choices.value.every((value) => model.value.values.includes(value))
  model.value = {
    ...model.value,
    values: all
      ? model.value.values.filter((v) => !choices.value.includes(v))
      : [...new Set([...model.value.values, ...choices.value])],
  }
}
</script>
<template>
  <div class="board-filter" :data-filter="label">
    <div class="board-filter-heading">
      <span>{{ label }}</span
      ><el-dropdown trigger="click" @command="changeOperator"
        ><button class="operator-button" :aria-label="`${label}筛选方式`">
          {{ model.operator }}<el-icon><ArrowDown /></el-icon></button
        ><template #dropdown
          ><el-dropdown-menu
            ><el-dropdown-item
              v-for="operator in textOperators"
              :key="operator"
              :command="operator"
              >{{ operator }}</el-dropdown-item
            ></el-dropdown-menu
          ></template
        ></el-dropdown
      >
    </div>
    <el-input
      v-if="input"
      :model-value="model.text"
      :aria-label="label"
      placeholder="请输入关键字"
      clearable
      @update:model-value="model = { ...model, text: $event }"
    />
    <div v-else-if="empty" class="filter-empty-value">无需填写筛选值</div>
    <el-popover
      v-else
      v-model:visible="visible"
      trigger="click"
      :width="300"
      placement="bottom-start"
    >
      <template #reference
        ><button
          class="filter-value"
          :aria-label="label"
          aria-haspopup="listbox"
          :aria-expanded="visible"
        >
          <span>{{ selectedText || ' ' }}</span
          ><el-icon><ArrowDown /></el-icon></button
      ></template>
      <div class="filter-options">
        <el-input v-model="search" :aria-label="`搜索${label}`" placeholder="搜索" clearable />
        <div class="filter-option-actions">
          <el-button v-if="multiple" link type="primary" @click="selectAll">全选</el-button
          ><el-button link @click="model = { ...model, values: [] }">清空</el-button>
        </div>
        <div
          class="filter-option-list"
          role="listbox"
          :aria-label="`${label}选项`"
          :aria-multiselectable="multiple"
        >
          <button
            v-for="value in choices"
            :key="value"
            class="filter-option"
            role="option"
            :aria-selected="model.values.includes(value)"
            @click="toggle(value)"
          >
            <span class="option-check" :class="{ checked: model.values.includes(value) }">{{
              model.values.includes(value) ? '✓' : ''
            }}</span
            >{{ value || '未填写' }}</button
          ><el-empty v-if="!choices.length" description="无匹配选项" :image-size="40" />
        </div>
      </div>
    </el-popover>
    <button
      v-if="!empty && !input && model.values.length"
      class="filter-clear"
      :aria-label="`清空${label}`"
      @click="model = { ...model, values: [] }"
    >
      <el-icon><Close /></el-icon>
    </button>
  </div>
</template>
