import { authClient } from './api'

export type ExportKind = 'customers' | 'followups' | 'sales-orders' | 'outbound-orders'
  | 'purchase-orders' | 'inbound-orders' | 'inventory'

// The server queries and creates the file; the browser only downloads its response.
export async function downloadExport(kind: ExportKind, filters: object, name: string) {
  const file = await authClient.request<Blob>(`/exports/${kind}`, {
    method: 'POST', body: JSON.stringify(filters),
  }, false, true)
  const url = URL.createObjectURL(file)
  const link = document.createElement('a')
  link.href = url
  link.download = `${name}.csv`
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
