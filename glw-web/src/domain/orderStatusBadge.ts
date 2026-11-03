export interface OrderStatusBadge {
  text: string
  className: string
}

/** 订单编号尾部的小方标：只显示一个字，完整状态通过 tooltip 展示。 */
export function orderStatusBadge(status: string): OrderStatusBadge {
  if (status === '待审核') return { text: '审', className: 'is-pending' }
  if (status === '已完结') return { text: '结', className: 'is-completed' }
  if (status === '已取消') return { text: '消', className: 'is-cancelled' }
  return { text: status, className: '' }
}
