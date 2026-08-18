/** 将 0~1 的比例显示为百分比，并保留指定的小数位。 */
export function formatPercent(value: number, decimals = 2): string {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return `${(0).toFixed(decimals)}%`
  return `${(numeric * 100).toFixed(decimals)}%`
}

const RADAR_PERCENT_KEYS = new Set(['killParticipantPercent', 'damagePercent', 'goldPercent'])

/** 雷达图指标数值显示口径：占比类显示为百分比，过万按 K 缩写，其余保留两位小数。
 *  选手详情页雷达与选手对比页的数据方框共用此口径，保证同一指标两处数值一致。 */
export function formatRadarMetricValue(key: string, value: number | null): string {
  if (value == null) return '暂无数据'
  const number = Number(value)
  if (RADAR_PERCENT_KEYS.has(key)) return formatPercent(number)
  if (Math.abs(number) >= 10000) return `${(number / 1000).toFixed(1)}K`
  return number.toFixed(2)
}
