/** 将 0~1 的比例显示为百分比，并保留指定的小数位。 */
export function formatPercent(value: number, decimals = 2): string {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return `${(0).toFixed(decimals)}%`
  return `${(numeric * 100).toFixed(decimals)}%`
}
