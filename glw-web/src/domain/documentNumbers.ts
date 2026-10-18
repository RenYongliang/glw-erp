export function nextDocumentNumber(prefix: string, rows: { code: string }[], date: string) {
  const day = date.slice(0, 10)
  if (!['SO', 'GL', 'IN', 'OUT'].includes(prefix) || !/^\d{4}-\d{2}-\d{2}$/.test(day))
    throw Error('单据类型或日期不正确')
  const start = prefix + day.replaceAll('-', '')
  const suffixes = rows.map(row => row.code.startsWith(start) ? row.code.slice(start.length) : '')
    .filter(suffix => /^\d{3}$/.test(suffix)).map(Number)
  const sequence = Math.max(0, ...suffixes) + 1
  if (sequence > 999) throw Error('该类型当日单据流水号已用完（最多 999 单）')
  return start + String(sequence).padStart(3, '0')
}
