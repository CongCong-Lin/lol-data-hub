/**
 * 将 0~1 的比例显示为百分比，并截断到指定小数位。
 * 末尾的 toFixed 只负责补零，不参与数值四舍五入。
 */
export function formatPercent(value: number, decimals = 2): string {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return `${(0).toFixed(decimals)}%`

  const factor = 10 ** decimals
  const percentage = numeric * 100
  // 补偿常见的二进制浮点误差（例如 0.29 * 100 可能略小于 29），不改变有效十进制位。
  const epsilon = Number.EPSILON * Math.max(1, Math.abs(percentage)) * 10
  const truncated = Math.trunc((percentage + Math.sign(percentage || 1) * epsilon) * factor) / factor
  return `${truncated.toFixed(decimals)}%`
}
