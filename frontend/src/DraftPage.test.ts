// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import DraftPage from './DraftPage.vue'
import { api, type ChampionStatistics, type ChampionStatisticsResult, type Stage } from './api'

vi.mock('./api', () => ({
  api: {
    availability: vi.fn(),
    championStatisticsByKeys: vi.fn(),
    championCounters: vi.fn(),
    teamCombinationStatisticsByKeys: vi.fn(),
  },
}))

const stages: Stage[] = [{
  sourceSeasonId: 237,
  sourceStageId: 106,
  seasonName: '2026职业联赛',
  name: '第三赛段组内赛',
  collected: true,
  sampleBaseCount: 60,
  collectedAt: '2026-08-15T17:00:00Z',
}]

function champion(overrides: Partial<ChampionStatistics> = {}): ChampionStatistics {
  return {
    championId: 84,
    championName: '阿卡丽',
    championTitle: '离群之刺',
    championLogo: null,
    positions: ['MID'],
    sampleBaseCount: 60,
    pickCount: 12,
    banCount: 5,
    bpCount: 17,
    winningCount: 7,
    totalKills: 60,
    totalDeaths: 40,
    totalAssists: 50,
    pickRate: 0.2,
    banRate: 0.08,
    bpRate: 0.28,
    winningRate: 0.583,
    kda: 2.75,
    killPerGame: 5,
    assistPerGame: 4.2,
    deathPerGame: 3.3,
    mostUsedPlayers: ['Knight'],
    sampleQualified: true,
    sourceUpdatedAt: '2026-08-15T17:00:00Z',
    ...overrides,
  }
}

function championResult(items: ChampionStatistics[]): ChampionStatisticsResult {
  return { dataVersion: 130, minimumPickCount: 1, total: items.length, items }
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(api.availability).mockResolvedValue(stages)
  vi.mocked(api.championCounters).mockResolvedValue({
    championId: 1,
    position: 'MID',
    totalGames: 8,
    opponents: [
      { championId: 84, championName: 'Akali', championChineseName: '阿卡丽', championTitle: null, championLogo: null, games: 6, wins: 4, winRate: 0.667 },
    ],
  })
  vi.mocked(api.teamCombinationStatisticsByKeys).mockResolvedValue({
    dataVersion: 130,
    combinationType: 'MID_JUNGLE',
    minimumPickCount: 1,
    total: 0,
    items: [],
  })
})

describe('DraftPage', () => {
  it('挂载后加载已采集赛段并按赛段拉取英雄池', async () => {
    vi.mocked(api.championStatisticsByKeys).mockResolvedValue(championResult([champion()]))
    const wrapper = mount(DraftPage)
    await flushPromises()

    expect(api.availability).toHaveBeenCalledWith('HERO', true)
    expect(api.championStatisticsByKeys).toHaveBeenCalledWith(['237:106'], 1, '', 'winningRate', 'desc')
    expect(wrapper.text()).toContain('BP 模拟器')
    expect(wrapper.findAll('.draft-slot')).toHaveLength(16)
    wrapper.unmount()
  })

  it('按顺序禁用英雄并推进到下一个槽位', async () => {
    vi.mocked(api.championStatisticsByKeys).mockResolvedValue(championResult([
      champion(),
      champion({ championId: 1, championName: '阿狸', positions: ['MID'], winningRate: 0.5, pickCount: 20 }),
    ]))
    const wrapper = mount(DraftPage)
    await flushPromises()

    const firstCandidate = wrapper.get('.candidate-chip')
    expect(firstCandidate.text()).toContain('阿狸')
    await firstCandidate.trigger('click')

    const filled = wrapper.findAll('.draft-slot.filled')
    expect(filled).toHaveLength(1)
    expect(filled[0].text()).toContain('Ban')
    expect(filled[0].text()).toContain('阿狸')
    // 已被禁用的英雄不再出现在候选中
    expect(wrapper.findAll('.candidate-chip').every((chip) => !chip.text().includes('阿狸'))).toBe(true)
    wrapper.unmount()
  })

  it('重置按钮清空全部槽位', async () => {
    vi.mocked(api.championStatisticsByKeys).mockResolvedValue(championResult([champion()]))
    const wrapper = mount(DraftPage)
    await flushPromises()

    await wrapper.get('.candidate-chip').trigger('click')
    expect(wrapper.findAll('.draft-slot.filled')).toHaveLength(1)

    await wrapper.get('.actions .secondary').trigger('click')
    expect(wrapper.findAll('.draft-slot.filled')).toHaveLength(0)
    wrapper.unmount()
  })

  it('加载失败时展示错误信息', async () => {
    vi.mocked(api.championStatisticsByKeys).mockRejectedValue(new Error('赛段数据加载失败'))
    const wrapper = mount(DraftPage)
    await flushPromises()
    await flushPromises()

    expect(wrapper.get('.message.error').text()).toContain('赛段数据加载失败')
    wrapper.unmount()
  })
})
