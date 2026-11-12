import { apiRequest } from './api'

export type AttachmentBizType = 'SALES_ORDER' | 'PURCHASE_ORDER' | 'INBOUND_ORDER' | 'OUTBOUND_ORDER'

export interface AttachmentVO {
  id: number
  bizType: AttachmentBizType
  bizId: number
  category: string
  categoryLabel: string
  fileName: string
  fileSize: number
  contentType: string
  /** 短时效预签名下载地址，每次列表刷新重新生成。 */
  downloadUrl: string
  createdAt: string
}

export function fetchOrderAttachments(bizType: AttachmentBizType, bizId: number) {
  return apiRequest<AttachmentVO[]>(
    `/attachment?bizType=${encodeURIComponent(bizType)}&bizId=${bizId}`,
  )
}

export function uploadOrderAttachment(
  bizType: AttachmentBizType,
  bizId: number,
  category: string,
  file: File,
) {
  const form = new FormData()
  form.append('bizType', bizType)
  form.append('bizId', String(bizId))
  form.append('category', category)
  form.append('file', file, file.name)
  return apiRequest<AttachmentVO>('/attachment', { method: 'POST', body: form })
}

export function deleteOrderAttachment(id: number) {
  return apiRequest<void>(`/attachment/${id}`, { method: 'DELETE' })
}
