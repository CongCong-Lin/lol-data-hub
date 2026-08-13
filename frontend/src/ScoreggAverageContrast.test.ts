// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ScoreggAverageContrast from './ScoreggAverageContrast.vue'
import type { PlayerAverageContrastMetric } from './api'

const metrics: PlayerAverageContrastMetric[] = [
  {
    key: 'damagePercent', label: '伤害占比', value: 0.25, averageValue: 0.2,
    minValue: 0.1, maxValue: 0.4, rank: 2, cohortSize: 10, higherIsBetter: true, percentage: true,
  },
  {
    key: 'deathPerGame', label: '死亡', value: 1, averageValue: 2,
    minValue: 1, maxValue: 4, rank: 1, cohortSize: 10, higherIsBetter: false, percentage: false,
  },
]

describe('ScoreggAverageContrast', () => {
  it('百分比只显示整数，并将死亡低值绘制得更高', () => {
    const wrapper = mount(ScoreggAverageContrast, { props: { playerName: 'Tarzan', metrics } })

    expect(wrapper.find('.average-contrast-item').text()).toContain('25%')
    expect(wrapper.find('.average-contrast-item').text()).not.toContain('25.00%')

    const damage = wrapper.findAll('.average-contrast-item')[0]
    const damagePlayerHeight = Number.parseFloat(damage.find('.player-bar').element.getAttribute('style')?.match(/height:\s*([\d.]+)%/)?.[1] ?? '0')
    const damageAverageHeight = Number.parseFloat(damage.find('.average-bar').element.getAttribute('style')?.match(/height:\s*([\d.]+)%/)?.[1] ?? '0')
    expect(damagePlayerHeight).toBeGreaterThan(damageAverageHeight)
    expect(damagePlayerHeight).toBeGreaterThan(50)

    const death = wrapper.findAll('.average-contrast-item')[1]
    const playerBarHeight = Number.parseFloat(death.find('.player-bar').element.getAttribute('style')?.match(/height:\s*([\d.]+)%/)?.[1] ?? '0')
    const averageBarHeight = Number.parseFloat(death.find('.average-bar').element.getAttribute('style')?.match(/height:\s*([\d.]+)%/)?.[1] ?? '0')
    expect(playerBarHeight).toBeGreaterThan(averageBarHeight)
  })
})
