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
    value: percentageMetric ? playerScore / 100 : playerScore / 10,
    averageValue: percentageMetric ? averageScore / 100 : averageScore / 10,
    playerScore,
    averageScore,
    rank: 1,
    cohortSize: 18,
  }
}

const metrics: PlayerRadarMetric[] = [
  metric('kda', 'KDA', 80, 50),
  metric('killPerGame', '场均击杀', 70, 50),
  metric('killParticipantPercent', '参团率', 60, 50),
  metric('damagePercent', '伤害占比', 55, 50),
  metric('creepScorePerGame', '场均补刀', 65, 50),
  metric('goldGapPerGame', '场均经济差', 75, 50),
]

describe('PlayerRadarChart', () => {
  it('绘制四层网格、选手多边形与平均多边形', () => {
    const wrapper = mount(PlayerRadarChart, { props: { metrics } })

    expect(wrapper.find('svg').exists()).toBe(true)
    expect(wrapper.findAll('polygon.radar-grid')).toHaveLength(4)
    expect(wrapper.find('polygon.radar-player').exists()).toBe(true)
    expect(wrapper.find('polygon.radar-average').exists()).toBe(true)
    expect(wrapper.findAll('circle.radar-point')).toHaveLength(6)
  })

  it('选手得分 100 落在轴顶点、0 收缩到中心', () => {
    const full = mount(PlayerRadarChart, {
      props: { metrics: metrics.map((item, index) => index === 0 ? { ...item, playerScore: 100 } : item) },
    })
    const topPoint = full.findAll('circle.radar-point')[0]
    expect(Number(topPoint.attributes('cy'))).toBeCloseTo(52, 0)

    const zero = mount(PlayerRadarChart, {
      props: { metrics: metrics.map((item, index) => index === 0 ? { ...item, playerScore: 0 } : item) },
    })
    const centerPoint = zero.findAll('circle.radar-point')[0]
    expect(Number(centerPoint.attributes('cx'))).toBeCloseTo(180, 0)
    expect(Number(centerPoint.attributes('cy'))).toBeCloseTo(152, 0)
  })

  it('展示六个维度标签与原始值、均值', () => {
    const wrapper = mount(PlayerRadarChart, { props: { metrics } })

    const labels = wrapper.findAll('text.radar-label').map((node) => node.text())
    expect(labels).toEqual(['KDA', '场均击杀', '参团率', '伤害占比', '场均补刀', '场均经济差'])
    expect(wrapper.text()).toContain('8.00（均值 5.00）')
    expect(wrapper.text()).toContain('60.00%（均值 50.00%）')
  })

  it('指标为空时不渲染图表', () => {
    const wrapper = mount(PlayerRadarChart, { props: { metrics: [] } })

    expect(wrapper.find('svg').exists()).toBe(false)
  })
})
