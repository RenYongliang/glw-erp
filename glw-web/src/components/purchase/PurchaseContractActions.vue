<script setup lang="ts">
import { computed, ref } from 'vue'
import { Printer, Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { purchaseSupplierName, type PurchaseOrder } from '../../domain/purchase'
import { purchaseContractHtml, purchaseContractPdf, purchaseContractStyles } from '../../domain/purchaseContract'
const props = defineProps<{order: PurchaseOrder}>()
const preview = ref(false), busy = ref(false)
const document = computed(() => purchaseContractHtml(props.order))
function check() {
  if (!purchaseSupplierName(props.order) || !props.order.lines.length) {
    ElMessage.warning('请先填写供应商并添加采购产品')
    return false
  }
  return true
}
function showPrint() { if(check()) preview.value = true }
function print() {
  const popup = window.open('', '_blank')
  if(!popup) return void ElMessage.warning('请允许浏览器打开打印窗口')
  popup.document.write(`<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>采购合同</title><style>${purchaseContractStyles}</style></head><body>${document.value.html}</body></html>`)
  popup.document.close()
  void popup.document.fonts.ready.then(()=>{popup.focus();popup.print()})
}
async function download() {
  if(!check()) return
  busy.value = true
  try {
    const blob = await purchaseContractPdf(props.order)
    const url = URL.createObjectURL(blob), a = window.document.createElement('a')
    a.href=url; a.download=`采购合同-${props.order.code || '草稿'}.pdf`; a.click()
    setTimeout(()=>URL.revokeObjectURL(url),60000)
  } catch(e) { ElMessage.error(e instanceof Error ? e.message : '采购合同下载失败') }
  finally { busy.value=false }
}
</script>
<template>
  <div class="purchase-contract-actions">
    <el-button text :icon="Printer" @click="showPrint">采购合同打印</el-button>
    <el-button text :icon="Download" :loading="busy" @click="download">采购合同下载</el-button>
  </div>
  <el-dialog v-model="preview" title="采购合同打印预览" width="min(1180px,96vw)" top="3vh" append-to-body destroy-on-close class="purchase-contract-preview">
    <div class="purchase-contract-toolbar"><span>A4 横向 · 共 {{ document.pages }} 页</span><el-button :icon="Printer" type="primary" @click="print">打印</el-button></div>
    <component :is="'style'">{{ purchaseContractStyles }}</component>
    <div class="purchase-contract-pages" v-html="document.html"></div>
    <template #footer><el-button @click="preview=false">关闭</el-button></template>
  </el-dialog>
</template>
<style>
.purchase-contract-actions { display:flex; gap:12px; margin:12px 0 16px; }
.purchase-contract-actions .el-button { color:#4b5565; margin:0; font-size:14px; }
.purchase-contract-actions .el-button:hover { color:#00a98f; background:#effaf7; }
.purchase-contract-actions .el-icon { font-size:17px; }
.purchase-contract-toolbar { display:flex; justify-content:space-between; align-items:center; margin-bottom:14px; }
.purchase-contract-pages { overflow:auto; max-height:70vh; background:#e9ebef; padding:18px; }
</style>
