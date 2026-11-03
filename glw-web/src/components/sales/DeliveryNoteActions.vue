<script setup lang="ts">
import { computed, ref } from 'vue'
import { deliveryNoteHtml, deliveryNoteStyles } from '../../domain/deliveryNoteHtml'
import { Printer, Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  createDeliveryNote,
  renderDeliveryNote,
  deliveryNotePdf,
} from '../../domain/deliveryNote'
import type { SalesOrder, SalesShipment } from '../../domain/sales'

const props = defineProps<{
  order?: SalesOrder
  orders?: SalesOrder[]
  shipment: SalesShipment
  /** 列表行内的紧凑模式：只显示一个“发货单”按钮，点击打开预览。 */
  compact?: boolean
  /** 尚未提交生成的出库单：提示正式单号在提交后生成。 */
  draft?: boolean
}>()
const preview = ref(false),
  busy = ref(false),
  pages = ref<string[]>([]),
  documentNumber = ref('')
const currentNote = computed(() => {
  if (!props.order) return undefined
  try { return createDeliveryNote(props.order, props.shipment, props.orders) } catch { return undefined }
})
const previewHtml = computed(() => currentNote.value ? deliveryNoteHtml(currentNote.value) : '')
const pageCount = computed(() => Math.ceil((currentNote.value?.lines.length || 0) / 6))
async function generate() {
  if (!props.order) throw Error('请先选择销售订单')
  const note = createDeliveryNote(props.order, props.shipment, props.orders)
  pages.value = await renderDeliveryNote(note)
  documentNumber.value = note.number
}
async function showPrint() {
  try {
    if (!props.order) throw Error('请先选择销售订单')
    createDeliveryNote(props.order, props.shipment, props.orders)
    preview.value = true
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}
async function download() {
  busy.value = true
  try {
    await generate()
    const url = URL.createObjectURL(deliveryNotePdf(pages.value))
    const a = document.createElement('a')
    a.href = url
    a.download = `发货单-${documentNumber.value}.pdf`
    a.click()
    setTimeout(() => URL.revokeObjectURL(url), 60000)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    busy.value = false
  }
}
function print() {
  if (!currentNote.value) { ElMessage.error('请检查发货数据'); return }
  const popup = window.open('', '_blank')
  if (!popup) {
    ElMessage.warning('浏览器阻止了打印窗口，请允许弹出窗口后重试')
    return
  }
  popup.document.write(
    `<!doctype html><html lang="zh-CN"><head><meta charset="UTF-8"><title>发货单</title><style>${deliveryNoteStyles}</style></head><body>${previewHtml.value}</body></html>`,
  )
  popup.document.close()
  popup.document.fonts.ready
    .then(() => {
      popup.focus()
      popup.print()
    })
    .catch(() => ElMessage.error('打印内容加载失败，请重试'))
}
</script>

<template>
  <div class="delivery-document-actions" :class="{ compact: props.compact }">
    <el-button
      v-if="props.compact"
      size="small"
      plain
      :icon="Printer"
      :disabled="!order"
      @click="showPrint"
      >发货单</el-button
    >
    <template v-else>
      <el-button
        text
        :icon="Printer"
        :disabled="busy || !order"
        @click="showPrint"
        >发货单打印</el-button
      >
      <el-button
        text
        :icon="Download"
        :loading="busy"
        :disabled="!order"
        @click="download"
        >发货单PDF下载</el-button
      >
      <span v-if="props.draft" class="delivery-note-draft"
        >草稿单号，提交后生成正式单号</span
      >
    </template>
  </div>
  <el-dialog
    v-model="preview"
    title="打印预览"
    width="min(1180px,96vw)"
    top="3vh"
    append-to-body
    class="delivery-print-preview"
    destroy-on-close
  >
    <div class="delivery-preview-toolbar">
      <span
        >发货单 · A4 横向 · 共 {{ pageCount }} 页<template v-if="props.draft">
          · 草稿单号，提交后生成正式单号</template
        ></span
      >
      <div class="delivery-preview-actions">
        <el-button :icon="Download" :loading="busy" @click="download"
          >下载PDF</el-button
        ><el-button :icon="Printer" type="primary" @click="print"
          >打印</el-button
        >
      </div>
    </div>
    <component :is="'style'">{{ deliveryNoteStyles }}</component>
    <div class="delivery-preview-pages" v-html="previewHtml"></div>
    <template #footer
      ><el-button @click="preview = false">关闭</el-button></template
    >
  </el-dialog>
</template>

<style>
.delivery-document-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 12px 0 24px;
}
.delivery-document-actions.compact {
  display: inline-flex;
  margin: 0;
  gap: 0;
}
.delivery-note-draft {
  color: #c26b00;
  font-size: 12px;
}
.delivery-document-actions .el-button {
  color: #4b5565;
  font-size: 14px;
  margin: 0;
}
.delivery-document-actions .el-button:hover {
  color: #00a98f;
  background: #effaf7;
}
.delivery-document-actions .el-icon {
  font-size: 17px;
}
.delivery-preview-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  gap: 12px;
}
.delivery-preview-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.delivery-preview-pages {
  background: #e9ebef;
  padding: 18px;
  overflow: auto;
  max-height: 70vh;
}
.delivery-preview-pages .delivery-paper {
  display: block;
  width: 100%;
  min-width: 760px;
  margin: 0 auto 18px;
  box-shadow: 0 2px 8px #0002;
}
.delivery-preview-pages .delivery-paper:last-child {
  margin-bottom: 0;
}
@media (max-width: 600px) {
  .delivery-preview-pages {
    padding: 4px;
  }
  .delivery-preview-toolbar {
    font-size: 12px;
  }
  .delivery-document-actions {
    gap: 0;
  }
}
</style>
