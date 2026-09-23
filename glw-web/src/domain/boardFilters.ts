export const textOperators = [
  '等于',
  '不等于',
  '等于任意一个',
  '不等于任意一个',
  '包含',
  '不包含',
  '为空',
  '不为空',
]
export const dateOperators = [
  '等于',
  '不等于',
  '大于等于',
  '小于等于',
  '选择范围',
  '动态筛选',
  '为空',
  '不为空',
]
export const presets = [
  '今天',
  '昨天',
  '明天',
  '近7天',
  '近30天',
  '本周',
  '上周',
  '下周',
  '本月',
  '上月',
  '下月',
  '本季度',
  '上季度',
  '下季度',
  '今年',
  '去年',
  '明年',
]
export interface TextFilter {
  operator: string
  values: string[]
  text: string
}
export interface DateFilter {
  operator: string
  preset: string
  value: string
  range: string[]
}
export const emptyTextFilter = (): TextFilter => ({
  operator: '等于任意一个',
  values: [],
  text: '',
})
export const emptyDateFilter = (): DateFilter => ({
  operator: '动态筛选',
  preset: '',
  value: '',
  range: [],
})
export function matchesText(value: string, filter: TextFilter) {
  if (filter.operator === '为空') return !value
  if (filter.operator === '不为空') return Boolean(value)
  if (filter.operator === '包含' || filter.operator === '不包含') {
    if (!filter.text.trim()) return true
    const found = value.toLowerCase().includes(filter.text.trim().toLowerCase())
    return filter.operator === '包含' ? found : !found
  }
  if (!filter.values.length) return true
  const found = filter.values.includes(value)
  return filter.operator.startsWith('不等于') ? !found : found
}
export function presetRange(preset: string, now = new Date()): [Date, Date] {
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const end = new Date(start)
  end.setDate(end.getDate() + 1)
  const shift = (days: number) => {
    start.setDate(start.getDate() + days)
    end.setDate(end.getDate() + days)
  }
  if (preset === '昨天') shift(-1)
  else if (preset === '明天') shift(1)
  else if (preset === '近7天' || preset === '近30天')
    start.setDate(start.getDate() - (preset === '近7天' ? 6 : 29))
  else if (preset.endsWith('周')) {
    start.setDate(
      start.getDate() -
        ((start.getDay() + 6) % 7) +
        (preset === '上周' ? -7 : preset === '下周' ? 7 : 0),
    )
    end.setTime(start.getTime())
    end.setDate(end.getDate() + 7)
  } else if (preset.endsWith('月')) {
    start.setDate(1)
    start.setMonth(start.getMonth() + (preset === '上月' ? -1 : preset === '下月' ? 1 : 0))
    end.setTime(start.getTime())
    end.setMonth(end.getMonth() + 1)
  } else if (preset.endsWith('季度')) {
    start.setDate(1)
    start.setMonth(
      Math.floor(start.getMonth() / 3) * 3 +
        (preset === '上季度' ? -3 : preset === '下季度' ? 3 : 0),
    )
    end.setTime(start.getTime())
    end.setMonth(end.getMonth() + 3)
  } else if (preset.endsWith('年')) {
    start.setMonth(0, 1)
    start.setFullYear(start.getFullYear() + (preset === '去年' ? -1 : preset === '明年' ? 1 : 0))
    end.setTime(start.getTime())
    end.setFullYear(end.getFullYear() + 1)
  }
  return [start, end]
}
export function matchesDate(value: string, filter: DateFilter) {
  if (filter.operator === '为空') return !value
  if (filter.operator === '不为空') return Boolean(value)
  if (filter.operator === '动态筛选') {
    if (!filter.preset) return true
    if (filter.preset === 'custom')
      return filter.range.length !== 2 || (value >= filter.range[0]! && value <= filter.range[1]!)
    const [start, end] = presetRange(filter.preset)
    const date = new Date(`${value}T00:00:00`)
    return date >= start && date < end
  }
  if (filter.operator === '选择范围')
    return filter.range.length !== 2 || (value >= filter.range[0]! && value <= filter.range[1]!)
  if (!filter.value) return true
  if (filter.operator === '不等于') return value !== filter.value
  if (filter.operator === '大于等于') return value >= filter.value
  if (filter.operator === '小于等于') return value <= filter.value
  return value === filter.value
}
