/** 毫秒耗时 → 易读文本：1200 → "1.2 s"。 */
export function formatDuration(millis: number): string {
  if (!Number.isFinite(millis) || millis < 0) return '—'
  if (millis < 1000) return `${Math.round(millis)} ms`
  return `${(millis / 1000).toFixed(millis < 10_000 ? 1 : 0)} s`
}

/** ISO 时间 → 相对时间，用于会话列表。 */
export function formatRelativeTime(iso?: string): string {
  if (!iso) return ''
  const time = new Date(iso).getTime()
  if (Number.isNaN(time)) return ''

  const diff = Date.now() - time
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  if (diff < 604_800_000) return `${Math.floor(diff / 86_400_000)} 天前`

  const date = new Date(time)
  return `${date.getMonth() + 1} 月 ${date.getDate()} 日`
}
