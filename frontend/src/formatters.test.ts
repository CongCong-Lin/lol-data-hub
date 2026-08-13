import { describe, expect, it } from 'vitest'
import { formatPercent } from './formatters'

describe('百分比格式化', () => {
  it('保留百分号后两位并按常规规则显示', () => {
    expect(formatPercent(0.256789)).toBe('25.68%')
    expect(formatPercent(0.218999)).toBe('21.90%')
  })

  it('对整数百分比补齐两位小数', () => {
    expect(formatPercent(0.61)).toBe('61.00%')
  })
})
