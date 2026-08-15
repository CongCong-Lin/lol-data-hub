// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import PlayerComparePage from './PlayerComparePage.vue'
import { api, type PlayerStatistics, type PlayerStatisticsResult } from './api'

vi.mock('./api', () => ({
  api: {
    playerStatisticsByKeys: vi.fn(),
  },
}))

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
  vi.mocked(api.playerStatisticsByKeys).mockResolvedValue(playersResult([
    player(),
    player({ playerKey: 'id:2', sourcePlayerId: 2, playerName: 'knight', teamNames: ['BLG'], positions: ['MID'] }),
    player({ playerKey: 'id:3', sourcePlayerId: 3, playerName: '369', teamNames: ['TES'], positions: ['TOP'], kda: 2.1 }),
  ]))
})

function mountPage(props: Partial<InstanceType<typeof PlayerComparePage>['$props']> = {}) {
  return mount(PlayerComparePage, {
    props: {
      stageKeys: ['237:100'],
      positionFilter: '',
      minimumMatchCount: 5,
      ...props,
    },
  })
}

describe('PlayerComparePage', () => {
  it('未选择赛段时搜索按钮禁用且不会发起查询', async () => {
    const wrapper = mountPage({ stageKeys: [] })
    await flushPromises()

    const searchButton = wrapper.get('.primary.search-button')
    expect((searchButton.element as HTMLButtonElement).disabled).toBe(true)

    await wrapper.get('input[type="search"]').setValue('Knight')
    await searchButton.trigger('click')
    await flushPromises()

    expect(api.playerStatisticsByKeys).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('选择赛段后搜索并添加选手到对比列表', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.get('input[type="search"]').setValue('Knight')
    await wrapper.get('.primary.search-button').trigger('click')
    await flushPromises()

    expect(vi.mocked(api.playerStatisticsByKeys)).toHaveBeenCalledWith(
      ['237:100'], 5, '', 'kda', 'desc',
    )
    const candidates = wrapper.findAll('.candidate-item')
    expect(candidates).toHaveLength(2)

    await candidates[0].get('.add-button').trigger('click')
    expect(wrapper.findAll('.selected-players .basket-item')).toHaveLength(1)
    expect(wrapper.findAll('.selected-players .basket-item')[0].text()).toContain('Knight')
    wrapper.unmount()
  })

  it('仅选择一名选手时提示需要至少两位', async () => {
    const wrapper = mountPage()
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
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.get('input[type="search"]').setValue('Knight')
    await wrapper.get('.primary.search-button').trigger('click')
    await flushPromises()

    const candidates = wrapper.findAll('.candidate-item')
    expect(candidates).toHaveLength(2)

    await candidates[0].get('.add-button').trigger('click')
    await wrapper.findAll('.candidate-item')[0].get('.add-button').trigger('click')

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
    const wrapper = mountPage()
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

  it('按分路筛选搜索候选（分路由父组件传入）', async () => {
    const wrapper = mountPage({ positionFilter: 'TOP' })
    await flushPromises()

    await wrapper.get('input[type="search"]').setValue('369')
    await wrapper.get('.primary.search-button').trigger('click')
    await flushPromises()

    expect(vi.mocked(api.playerStatisticsByKeys)).toHaveBeenLastCalledWith(
      ['237:100'], 5, 'TOP', 'kda', 'desc',
    )
    wrapper.unmount()
  })

  it('已选选手详情链接携带对比视图返回地址', async () => {
    const wrapper = mountPage({ positionFilter: 'TOP' })
    await flushPromises()

    await wrapper.get('input[type="search"]').setValue('Knight')
    await wrapper.get('.primary.search-button').trigger('click')
    await flushPromises()

    await wrapper.findAll('.candidate-item')[0].get('.add-button').trigger('click')

    const href = wrapper.get('a.selected-name').attributes('href') ?? ''
    expect(href).toContain('/players/1?')
    expect(href).toContain('stageKeys=237%3A100')
    expect(href).toContain('position=TOP')
    expect(href).toContain('minimumMatchCount=5')
    expect(decodeURIComponent(href)).toContain('returnTo=/?view=compare')
    wrapper.unmount()
  })
})
