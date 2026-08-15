// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import PlayerComparePage from './PlayerComparePage.vue'
import { api, type PlayerStatistics, type PlayerStatisticsResult, type Stage } from './api'

vi.mock('./api', () => ({
  api: {
    seasons: vi.fn(),
    availability: vi.fn(),
    playerStatisticsByKeys: vi.fn(),
  },
}))

const stages: Stage[] = [{
  sourceSeasonId: 237,
  sourceStageId: 100,
  seasonName: '2026LPL',
  name: '第一赛段',
  collected: true,
  sampleBaseCount: 54,
  collectedAt: '2026-03-08T12:38:16Z',
}]

function player(overrides: Partial<PlayerStatistics> = {}): PlayerStatistics {
  return {
    playerKey: 'id:1',
    sourcePlayerId: 1,
    playerName: 'Knight',
    playerAvatar: null,
    teamNames: ['TES'],
    positions: ['MID'],
    matchCount: 5,
    gameCount: 12,
    mvpCount: 2,
    mvpVotes: 10,
    totalKills: 30,
    totalAssists: 40,
    totalDeaths: 20,
    kda: 3.5,
    killPerGame: 2.5,
    assistPerGame: 3.33,
    deathPerGame: 1.67,
    goldPerGame: 12000,
    creepScorePerGame: 250,
    wardPlacedPerGame: 10,
    wardKilledPerGame: 5,
    killParticipantPercent: 0.7,
    goldGapPerGame: 100,
    damagePerGame: 500,
    damagePercent: 0.25,
    goldPercent: 0.22,
    sampleQualified: true,
    ...overrides,
  }
}

function playersResult(items: PlayerStatistics[]): PlayerStatisticsResult {
  return {
    dataVersion: 6,
    minimumMatchCount: 5,
    total: items.length,
    items,
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(api.seasons).mockResolvedValue([{
    sourceSeasonId: 237,
    name: '2026LPL',
    startTime: null,
    endTime: null,
    open: true,
  }])
  vi.mocked(api.availability).mockResolvedValue(stages)
  vi.mocked(api.playerStatisticsByKeys).mockResolvedValue(playersResult([
    player(),
    player({ playerKey: 'id:2', sourcePlayerId: 2, playerName: 'knight', teamNames: ['BLG'], positions: ['MID'] }),
    player({ playerKey: 'id:3', sourcePlayerId: 3, playerName: '369', teamNames: ['TES'], positions: ['TOP'], kda: 2.1 }),
  ]))
})

async function mountAt(path = '/compare'): Promise<ReturnType<typeof mount>> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/compare', component: PlayerComparePage }],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(PlayerComparePage, { global: { plugins: [router] } })
  return wrapper
}

describe('PlayerComparePage', () => {
  it('未选择赛段时搜索提示先选择赛段', async () => {
    const wrapper = await mountAt()
    await flushPromises()

    await wrapper.get('input[type="search"]').setValue('Knight')
    await wrapper.get('.primary.search-button').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('请先选择赛段')
    expect(api.playerStatisticsByKeys).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('选择赛段后搜索并添加选手到对比列表', async () => {
    const wrapper = await mountAt()
    await flushPromises()

    await wrapper.get('button.stage-chip').trigger('click')
    await flushPromises()
    await wrapper.get('input[type="search"]').setValue('Knight')
    await wrapper.get('.primary.search-button').trigger('click')
    await flushPromises()

    expect(vi.mocked(api.playerStatisticsByKeys)).toHaveBeenCalledWith(
      ['237:100'], 0, '', 'kda', 'desc',
    )
    const candidates = wrapper.findAll('.candidate-item')
    expect(candidates).toHaveLength(2)

    await candidates[0].get('.add-button').trigger('click')
    expect(wrapper.findAll('.selected-players .basket-item')).toHaveLength(1)
    expect(wrapper.findAll('.selected-players .basket-item')[0].text()).toContain('Knight')
    wrapper.unmount()
  })

  it('仅选择一名选手时提示需要至少两位', async () => {
    const wrapper = await mountAt()
    await flushPromises()

    await wrapper.get('button.stage-chip').trigger('click')
    await flushPromises()
    await wrapper.get('input[type="search"]').setValue('Knight')
    await wrapper.get('.primary.search-button').trigger('click')
    await flushPromises()

    await wrapper.findAll('.candidate-item')[0].get('.add-button').trigger('click')

    expect(wrapper.text()).toContain('请先选择至少两位选手')
    expect(wrapper.find('table.compare-table').exists()).toBe(false)
    wrapper.unmount()
  })

  it('选择两位选手后渲染对比表并高亮最优值', async () => {
    const wrapper = await mountAt()
    await flushPromises()

    await wrapper.get('button.stage-chip').trigger('click')
    await flushPromises()
    await wrapper.get('input[type="search"]').setValue('Knight')
    await wrapper.get('.primary.search-button').trigger('click')
    await flushPromises()

    const candidates = wrapper.findAll('.candidate-item')
    expect(candidates).toHaveLength(2)

    await candidates[0].get('.add-button').trigger('click')
    await wrapper.findAll('.candidate-item')[0].get('.add-button').trigger('click')

    const table = wrapper.get('table.compare-table')
    const metricRows = wrapper.findAll('.compare-table .metric-label')
    expect(metricRows.length).toBeGreaterThanOrEqual(12)
    expect(wrapper.findAll('.compare-table .best-value').length).toBeGreaterThan(0)
    expect(wrapper.text()).toContain('对比结果')
    wrapper.unmount()
  })

  it('候选列表最多展示 5 名并全部可添加', async () => {
    vi.mocked(api.playerStatisticsByKeys).mockResolvedValue(playersResult([
      player(),
      player({ playerKey: 'id:2', sourcePlayerId: 2, playerName: 'Alpha', teamNames: ['T1'] }),
      player({ playerKey: 'id:3', sourcePlayerId: 3, playerName: 'Beta', teamNames: ['T2'] }),
      player({ playerKey: 'id:4', sourcePlayerId: 4, playerName: 'Gamma', teamNames: ['T3'] }),
      player({ playerKey: 'id:5', sourcePlayerId: 5, playerName: 'Delta', teamNames: ['T4'] }),
      player({ playerKey: 'id:6', sourcePlayerId: 6, playerName: 'Falcon', teamNames: ['T5'] }),
      player({ playerKey: 'id:7', sourcePlayerId: 7, playerName: 'Artemis', teamNames: ['T6'] }),
    ]))
    const wrapper = await mountAt()
    await flushPromises()

    await wrapper.get('button.stage-chip').trigger('click')
    await flushPromises()
    await wrapper.get('input[type="search"]').setValue('a')
    await wrapper.get('.primary.search-button').trigger('click')
    await flushPromises()

    const candidates = wrapper.findAll('.candidate-item')
    expect(candidates).toHaveLength(5)
    for (let index = 0; index < 5; index += 1) {
      await wrapper.findAll('.candidate-item')[0].get('.add-button').trigger('click')
    }
    expect(wrapper.findAll('.selected-players .basket-item')).toHaveLength(5)
    expect(wrapper.findAll('.candidate-item')).toHaveLength(0)
    wrapper.unmount()
  })

  it('按分路筛选搜索候选', async () => {
    const wrapper = await mountAt()
    await flushPromises()

    await wrapper.get('button.stage-chip').trigger('click')
    await flushPromises()
    const positionSelect = wrapper.findAll('select')[1]
    expect(positionSelect).toBeDefined()
    await positionSelect.setValue('TOP')
    await flushPromises()
    await wrapper.get('input[type="search"]').setValue('369')
    await wrapper.get('.primary.search-button').trigger('click')
    await flushPromises()

    expect(vi.mocked(api.playerStatisticsByKeys)).toHaveBeenLastCalledWith(
      ['237:100'], 0, 'TOP', 'kda', 'desc',
    )
    wrapper.unmount()
  })
})
