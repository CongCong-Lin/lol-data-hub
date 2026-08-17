// @vitest-environment jsdom

import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ChampionTrendChart from './ChampionTrendChart.vue'
import type { ChampionTrendPoint } from './api'

function trend(overrides: Partial<ChampionTrendPoint>): ChampionTrendPoint {
  return {
    sourceSeasonId: 237,
    sourceStageId: 112,
    stageName: '第一赛段组内赛',
    pickCount: 0,
    banCount: 0,
    winningCount: 0,
    pickRate: 0,
    banRate: 0,
    winningRate: 0,
    ...overrides,
  }
}

// 绘图区高度 = 260 - 18(上) - 36(下) = 206
const PLOT_HEIGHT = 206

describe('ChampionTrendChart', () => {
  it('出场柱以出场数峰值为基准归一化，禁用数不参与缩放', () => {
    const wrapper = mount(ChampionTrendChart, {
      props: {
        trends: [
          trend({ stageName: '第一赛段组内赛', pickCount: 14, banCount: 91, winningRate: 0.5 }),
          trend({ stageName: '第二赛段组内赛', pickCount: 1, banCount: 3, winningRate: 0 }),
          trend({ stageName: '第三赛段组内赛', pickCount: 3, banCount: 1, winningRate: 1 }),
        ],
      },
    })

    const pickBars = wrapper.findAll('rect.trend-bar-pick')
    // 峰值出场柱顶到绘图区顶部（y=18），占满整个绘图区
    expect(pickBars[0].attributes('y')).toBe('18')
    expect(pickBars[0].attributes('height')).toBe(String(PLOT_HEIGHT))
    // 出场 1 的柱高 = 206 * 1/14
    expect(Number(pickBars[1].attributes('height'))).toBeCloseTo((PLOT_HEIGHT * 1) / 14, 1)
    // 图例显示出场峰值而非禁用峰值（禁用数 91 不当作出场基准）
    expect(wrapper.find('.legend-pick').text()).toBe('出场 14')
  })

  it('禁用柱超过出场峰值基准时截断到绘图区顶部', () => {
    const wrapper = mount(ChampionTrendChart, {
      props: {
        trends: [
          trend({ stageName: '第一赛段组内赛', pickCount: 14, banCount: 91 }),
          trend({ stageName: '第一赛段骑士之路', pickCount: 1, banCount: 15 }),
        ],
      },
    })

    const banBars = wrapper.findAll('rect.trend-bar-ban')
    expect(banBars[0].attributes('y')).toBe('18')
    expect(banBars[0].attributes('height')).toBe(String(PLOT_HEIGHT))
    // 禁用 91 的折线点也被截断到顶部，不会越出绘图区
    const banLine = wrapper.find('polyline.trend-line-ban').attributes('points')
    expect(banLine).toContain(',18.0')
  })

  it('胜率折线按 0—100% 直接映射', () => {
    const wrapper = mount(ChampionTrendChart, {
      props: {
        trends: [
          trend({ stageName: 'A', pickCount: 1, banCount: 1, winningRate: 0.5 }),
          trend({ stageName: 'B', pickCount: 1, banCount: 1, winningRate: 0 }),
          trend({ stageName: 'C', pickCount: 1, banCount: 1, winningRate: 1 }),
        ],
      },
    })

    const rateLine = wrapper.find('polyline.trend-line-rate').attributes('points')
    // yRate(0.5)=121、yRate(0)=224、yRate(1)=18
    expect(rateLine).toContain('121.0')
    expect(rateLine).toContain('224.0')
    expect(rateLine).toContain('18.0')
  })
})