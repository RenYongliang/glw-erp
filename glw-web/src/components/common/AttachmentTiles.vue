<script setup lang="ts">
import { Document, Plus } from '@element-plus/icons-vue'
import { attachmentTypeOf, keepsUploadSlot, type AttachmentTypeDef } from '../../domain/attachments'

/** 附件卡片数据；销售、采购、出库回签单共用同一结构。 */
export interface AttachmentItem {
  id?: number
  name: string
  url: string
  type?: string
  fileSize?: number
}

const props = withDefaults(
  defineProps<{
    attachments: AttachmentItem[]
    types: AttachmentTypeDef[]
    loading?: boolean
    error?: string
    /** 是否显示删除按钮；查看态可传 false。 */
    removable?: boolean
    /** 是否显示上传加号；查看态或出库单未保存时传 false。 */
    uploadable?: boolean
  }>(),
  { loading: false, error: '', removable: true, uploadable: true },
)

const emit = defineEmits<{
  upload: [files: File[], type: string]
  remove: [file: AttachmentItem]
}>()

function filesOf(type: string) {
  return props.attachments.filter((file) => attachmentTypeOf(file) === type)
}

function canAdd(slot: AttachmentTypeDef) {
  return keepsUploadSlot(slot) || filesOf(slot.key).length === 0
}

function pick(event: Event, type: string) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (files.length) emit('upload', files, type)
}
</script>

<template>
  <el-alert v-if="error" :title="error" type="error" :closable="false" />
  <div v-loading="loading" class="attachment-tile-grid">
    <template v-for="slot in types" :key="slot.key">
      <div
        v-for="file in filesOf(slot.key)"
        :key="file.id ?? slot.key + '-' + file.name"
        class="attachment-tile attachment-tile-doc"
        :title="file.name"
      >
        <span class="attachment-tile-type">{{ slot.label }}</span>
        <button
          v-if="removable"
          type="button"
          class="attachment-tile-remove"
          aria-label="移除附件"
          @click.stop="emit('remove', file)"
        >
          ×
        </button>
        <a
          class="attachment-tile-body"
          :href="file.url"
          :download="file.name"
          :aria-label="'下载 ' + file.name"
        >
          <el-icon class="attachment-tile-icon"><Document /></el-icon>
          <span class="attachment-tile-name">{{ file.name }}</span>
        </a>
      </div>
      <label
        v-if="uploadable && canAdd(slot)"
        class="attachment-tile attachment-tile-add"
        :title="'上传' + slot.label"
        :aria-label="slot.label + '上传'"
      >
        <span class="attachment-tile-type">{{ slot.label }}</span>
        <el-icon><Plus /></el-icon>
        <input
          type="file"
          :multiple="keepsUploadSlot(slot)"
          @change="pick($event, slot.key)"
        />
      </label>
    </template>
  </div>
</template>
