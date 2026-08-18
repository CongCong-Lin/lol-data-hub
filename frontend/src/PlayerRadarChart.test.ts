// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import PlayerRadarChart from './PlayerRadarChart.vue'
import type { PlayerRadarMetric } from './api'

function metric(key: string, label: string, playerScore: number, averageScore: number): PlayerRadarMetric {
  const percentageMetric = ['killParticipantPercent', 'damagePercent', 'goldPercent'].includes(key)
  return {
    key,
    label,
    value: percentageMetric ? playerScore / 100 : key === 'damagePerGame' ? playerScore * 300 : playerScore / 10,
    averageValue: percentageMetric ? averageScore / 100 : key === 'damagePerGame' ? averageScore * 300 : averageScore / 10,
    playerScore,
    averageScore,
    rank: 1,
    cohortSize: 18,
    available: true,
  }
}

const metrics: PlayerRadarMetric[] = [
  metric('kda', 'KDA', 80, 55),
  metric('killParticipantPercent', '参团率', 70, 55),
  metric('creepScorePerGame', '场均补刀', 65, 55),
  metric('goldGapPerGame', '场均经济差', 75, 55),
  metric('killPerGame', '场均击杀', 60, 55),
  metric('damagePercent', '伤害占比', 55, 55),
  metric('damagePerGame', '伤害', 85, 55),
  metric('deathPerGame', '场均死亡', 45, 55),
]

describe('PlayerRadarChart', () => {
  it('绘制五层网格、八维选手多边形与平均多边形', () => {
    const wrapper = mount(PlayerRadarChart, { props: { metrics } })

    expect(wrapper.find('svg').exists()).toBe(true)
    expect(wrapper.attributes('aria-label')).toBe('选手八维能力雷达图')
    expect(wrapper.findAll('polygon.radar-grid')).toHaveLength(5)
    expect(wrapper.find('polygon.radar-player').exists()).toBe(true)
    expect(wrapper.find('polygon.radar-average').exists()).toBe(true)
    expect(wrapper.findAll('circle.radar-point')).toHaveLength(8)
  })

  it('选手得分 100 落在轴顶点，0 收缩到中心', () => {
    const full = mount(PlayerRadarChart, {
      props: { metrics: metrics.map((item, index) => index === 0 ? { ...item, playerScore: 100 } : item) },
    })
    const topPoint = full.findAll('circle.radar-point')[0]
    expect(Number(topPoint.attributes('cy'))).toBeCloseTo(152, 0)

    const zero = mount(PlayerRadarChart, {
      props: { metrics: metrics.map((item, index) => index === 0 ? { ...item, playerScore: 0 } : item) },
    })
    const centerPoint = zero.findAll('circle.radar-point')[0]
    expect(Number(centerPoint.attributes('cx'))).toBeCloseTo(350, 0)
    expect(Number(centerPoint.attributes('cy'))).toBeCloseTo(280, 0)
  })

  it('按指定顺序展示八项指标、原始值和排名', () => {
    const wrapper = mount(PlayerRadarChart, { props: { metrics } })

    const labels = wrapper.findAll('text.radar-label').map((node) => node.text())
    expect(labels).toEqual(['KDA', '参团率', '场均补刀', '场均经济差', '场均击杀', '伤害占比', '伤害', '场均死亡'])
    expect(wrapper.text()).toContain('排名: 1')
    expect(wrapper.text()).toContain('25.5K')
    expect(wrapper.text()).toContain('70.00%')
    // 核心模式：左右水平轴标签保持在半径 163 处
    const labelEls = wrapper.findAll('text.radar-label')
    expect(Number(labelEls[2].attributes('x'))).toBeCloseTo(513, 0)
    expect(Number(labelEls[6].attributes('x'))).toBeCloseTo(187, 0)
  })

  it('百分比保留小数点后两位', () => {
    const wrapper = mount(PlayerRadarChart, {
      props: { metrics: [{ ...metrics[1], value: 0.256789, averageValue: 0.218999 }] },
    })

    expect(wrapper.text()).toContain('25.68%')
    expect(wrapper.text()).not.toContain('25.67%')
  })

  it('伤害数据不可用时明确显示暂无数据', () => {
    const wrapper = mount(PlayerRadarChart, {
      props: { metrics: [{ ...metrics[6], value: null, available: false, rank: 0 }] },
    })

    expect(wrapper.text()).toContain('暂无数据')
    expect(wrapper.text()).not.toContain('排名: 0')
  })

  it('指标为空时不渲染图表', () => {
    const wrapper = mount(PlayerRadarChart, { props: { metrics: [] } })

    expect(wrapper.find('svg').exists()).toBe(false)
  })

  it('叠加模式下绘制多选手浅色多边形', () => {
    const wrapper = mount(PlayerRadarChart, {
      props: {
        metrics,
        overlay: [
          { name: 'Knight', scores: [80, 70, 65, 75, 60, 55, 85, 45] },
          { name: 'Rookie', scores: [60, 80, 55, 65, 70, 75, 60, 55] },
        ],
      },
    })

    const polygons = wrapper.findAll('svg polygon')
    const overlays = polygons.filter((polygon) => polygon.attributes('stroke') === '#7fb0f7' || polygon.attributes('stroke') === '#f0a3a3')
    expect(overlays).toHaveLength(2)
    expect(overlays[0].attributes('stroke')).toBe('#7fb0f7')
    expect(overlays[1].attributes('stroke')).toBe('#f0a3a3')
    expect(overlays[0].attributes('fill')).toBe('#7fb0f733')
    expect(overlays[0].attributes('stroke-width')).toBe('2')
    // 叠加模式下隐藏单人多边形与数值文字
    expect(wrapper.find('polygon.radar-player').exists()).toBe(false)
    expect(wrapper.find('text.radar-label-value').exists()).toBe(false)
    // 图例不再由图表组件绘制（移至对比页 HTML 图例）
    expect(wrapper.find('g.radar-legend').exists()).toBe(false)
  })

  it('叠加模式下每个轴标签旁渲染指标数据方框（含颜色与数值）', () => {
    const axisBoxes = metrics.map((item) => ({
      label: item.label,
      rows: [
        { name: 'Knight', color: '#7fb0f7', text: '8.00' },
        { name: 'Rookie', color: '#f0a3a3', text: '6.00' },
      ],
    }))
    const wrapper = mount(PlayerRadarChart, {
      props: {
        metrics,
        overlay: [
          { name: 'Knight', scores: [80, 70, 65, 75, 60, 55, 85, 45] },
          { name: 'Rookie', scores: [60, 80, 55, 65, 70, 75, 60, 55] },
        ],
        axisBoxes,
      },
    })

    expect(wrapper.findAll('rect.radar-axis-box')).toHaveLength(8)
    expect(wrapper.findAll('rect.radar-axis-swatch')).toHaveLength(16)
    const text = wrapper.text()
    expect(text).toContain('Knight:')
    expect(text).toContain('Rookie:')
    expect(text).toContain('8.00')
    expect(text).toContain('6.00')
    // 方框色块一一对应选手颜色
    const swatches = wrapper.findAll('rect.radar-axis-swatch')
    expect(swatches.slice(0, 2).map((node) => node.attributes('fill'))).toEqual(['#7fb0f7', '#f0a3a3'])
    // 叠加模式下左右水平轴标签移到方框外侧（右侧 x=632、左侧 x=68），不被白底方框覆盖
    const labelEls = wrapper.findAll('text.radar-label')
    expect(Number(labelEls[2].attributes('x'))).toBeCloseTo(632, 0)
    expect(labelEls[2].attributes('text-anchor')).toBe('start')
    expect(Number(labelEls[6].attributes('x'))).toBeCloseTo(68, 0)
    expect(labelEls[6].attributes('text-anchor')).toBe('end')
  })
})
