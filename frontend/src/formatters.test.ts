import { describe, expect, it } from 'vitest'
import { formatPercent } from './formatters'

describe('百分比格式化', () => {
  it('保留百分号后两位并截断而不是四舍五入', () => {
    expect(formatPercent(0.256789)).toBe('25.67%')
    expect(formatPercent(0.218999)).toBe('21.89%')
  })

  it('对整百分比补齐两个小数位', () => {
    expect(formatPercent(0.61)).toBe('61.00%')
  })
})
