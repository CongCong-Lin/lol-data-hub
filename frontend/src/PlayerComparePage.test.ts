// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import PlayerComparePage from './PlayerComparePage.vue'
import { api, type PlayerDetailStatisticsResult, type PlayerStatistics, type PlayerStatisticsResult } from './api'

vi.mock('./api', () => ({
  api: {
    playerStatisticsByKeys: vi.fn(),
    playerDetail: vi.fn(),
  },
}))

const RADAR_METRICS = [
  { key: 'kda', label: 'KDA', value: 1.31 },
  { key: 'killParticipantPercent', label: '参团率', value: 0.5965 },
  { key: 'creepScorePerGame', label: '场均补刀', value: 283.55 },
  { key: 'goldGapPerGame', label: '场均经济差', value: -1507.59 },
  { key: 'killPerGame', label: '场均击杀', value: 2.64 },
  { key: 'damagePercent', label: '伤害占比', value: 0.2245 },
  { key: 'damagePerGame', label: '伤害', value: 18700 },
  { key: 'deathPerGame', label: '场均死亡', value: 4.73 },
]

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

function detailResult(playerName: string): PlayerDetailStatisticsResult {
  return {
    dataVersion: 6,
    minimumMatchCount: 5,
    position: 'MID',
    cohortSize: 10,
    player: {
      sourcePlayerId: 1,
      playerName,
      playerAvatar: null,
      teamNames: ['TES'],
      positions: ['MID'],
      matchCount: 5,
      gameCount: 12,
    },
    coreMetrics: [],
    radarMetrics: RADAR_METRICS.map((metric) => ({
      key: metric.key,
      label: metric.label,
      value: metric.value,
      averageValue: metric.value,
      playerScore: 60,
      averageScore: 50,
      rank: 3,
      cohortSize: 10,
      available: true,
    })),
    heroUsageAvailable: false,
    missingHeroStageKeys: [],
    heroUsageTotalGames: 0,
    heroes: [],
    latestCollectedAt: null,
    averageContrastMetrics: [],
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(api.playerStatisticsByKeys).mockResolvedValue(playersResult([
    player(),
    player({ playerKey: 'id:2', sourcePlayerId: 2, playerName: 'knight', teamNames: ['BLG'], positions: ['MID'] }),
    player({ playerKey: 'id:3', sourcePlayerId: 3, playerName: '369', teamNames: ['TES'], positions: ['TOP'], kda: 2.1 }),
  ]))
  vi.mocked(api.playerDetail).mockResolvedValue(detailResult('Knight'))
})

function mountPage(props: Partial<InstanceType<typeof PlayerComparePage>['$props']> = {}) {
  return mount(PlayerComparePage, {
    props: {
      stageKeys: ['237:100'],
      positionFilter: '',
      minimumMatchCount: 5,
      searchKeyword: '',
      selectedPlayerIds: [],
      ...props,
    },
  })
}

describe('PlayerComparePage', () => {
  it('未选择赛段时不发起查询并提示', async () => {
    const wrapper = mountPage({ stageKeys: [] })
    await flushPromises()

    expect(api.playerStatisticsByKeys).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('请先选择赛段')
    wrapper.unmount()
  })

  it('进入视图自动拉取全量列表并按 KDA 排序', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(api.playerStatisticsByKeys).toHaveBeenCalledWith(['237:100'], 5, '', 'kda', 'desc')
    expect(wrapper.findAll('.player-row')).toHaveLength(3)
    wrapper.unmount()
  })

  it('搜索框实时过滤列表且无需搜索按钮', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.get('input[type="search"]').setValue('369')
    await flushPromises()
    expect(wrapper.emitted('update:searchKeyword')![0]).toEqual(['369'])

    await wrapper.setProps({ searchKeyword: '369' })
    await flushPromises()
    const rows = wrapper.findAll('.player-row')
    expect(rows).toHaveLength(1)
    expect(rows[0].text()).toContain('369')
    expect(wrapper.find('.primary.search-button').exists()).toBe(false)
    wrapper.unmount()
  })

  it('点击行添加并在已选列表移除', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.findAll('.player-row')[0].trigger('click')
    expect(wrapper.findAll('.selected-players .basket-item')).toHaveLength(1)
    expect(wrapper.findAll('.selected-players .basket-item')[0].text()).toContain('Knight')
    expect(wrapper.emitted('update:selectedPlayerIds')![0]).toEqual([[1]])

    await wrapper.findAll('.player-row')[0].trigger('click')
    expect(wrapper.findAll('.selected-players .basket-item')).toHaveLength(0)
    wrapper.unmount()
  })

  it('仅选择一名选手时提示需要至少两位', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.findAll('.player-row')[0].trigger('click')

    expect(wrapper.text()).toContain('请先选择至少两位选手')
    expect(wrapper.find('table.compare-table').exists()).toBe(false)
    wrapper.unmount()
  })

  it('选择两位选手后渲染对比表并高亮最优值', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.findAll('.player-row')[0].trigger('click')
    await wrapper.findAll('.player-row')[1].trigger('click')
    await flushPromises()

    const metricRows = wrapper.findAll('.compare-table .metric-label')
    expect(metricRows.length).toBeGreaterThanOrEqual(12)
    expect(wrapper.findAll('.compare-table .best-value').length).toBeGreaterThan(0)
    expect(wrapper.text()).toContain('对比结果')
    wrapper.unmount()
  })

  it('雷达叠加逐选手调用 playerDetail 并使用详情页分位分值', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.findAll('.player-row')[0].trigger('click')
    await wrapper.findAll('.player-row')[1].trigger('click')
    await flushPromises()

    expect(api.playerDetail).toHaveBeenCalledWith(1, ['237:100'], 'MID', 5)
    expect(api.playerDetail).toHaveBeenCalledWith(2, ['237:100'], 'MID', 5)
    expect(wrapper.find('.player-radar-chart').exists()).toBe(true)
    const cards = wrapper.findAll('.radar-metric-card')
    expect(cards).toHaveLength(RADAR_METRICS.length)
    expect(cards.map((card) => card.get('h3').text())).toEqual(RADAR_METRICS.map((metric) => metric.label))
    expect(wrapper.findAll('.radar-metric-row')).toHaveLength(2 * RADAR_METRICS.length)
    expect(wrapper.findAll('.radar-metric-swatch')).toHaveLength(2 * RADAR_METRICS.length)
    expect(wrapper.findAll('rect.radar-axis-box')).toHaveLength(0)
    expect(wrapper.findAll('.radar-metric-name').map((node) => node.text())).toContain('Knight')
    expect(wrapper.findAll('.radar-metric-name').map((node) => node.text())).toContain('knight')
    expect(wrapper.text()).toContain('59.65%')
    expect(wrapper.text()).toContain('18.7K')
    expect(wrapper.text()).toContain('-1507.59')
    expect(wrapper.text()).toContain('1.31')
    const swatchStyles = wrapper.findAll('.radar-metric-swatch').slice(0, 2).map((node) => node.attributes('style'))
    expect(swatchStyles[0]).not.toBe(swatchStyles[1])
    wrapper.unmount()
  })

  it('某位选手详情加载失败时只跳过该选手并保留其余八张指标卡片', async () => {
    vi.mocked(api.playerDetail).mockImplementation(async (sourcePlayerId) => {
      if (sourcePlayerId === 2) throw new Error('detail failed')
      return detailResult('Knight')
    })
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.findAll('.player-row')[0].trigger('click')
    await wrapper.findAll('.player-row')[1].trigger('click')
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('knight：雷达数据获取失败，已跳过')
    })

    expect(wrapper.findAll('.radar-metric-card')).toHaveLength(8)
    expect(wrapper.findAll('.radar-metric-row')).toHaveLength(8)
    expect(wrapper.findAll('.radar-metric-name').every((node) => node.text() === 'Knight')).toBe(true)
    wrapper.unmount()
  })

  it('最多同时选择 5 名选手', async () => {
    vi.mocked(api.playerStatisticsByKeys).mockResolvedValue(playersResult([
      player(),
      player({ playerKey: 'id:2', sourcePlayerId: 2, playerName: 'Alpha', teamNames: ['T1'] }),
      player({ playerKey: 'id:3', sourcePlayerId: 3, playerName: 'Beta', teamNames: ['T2'] }),
      player({ playerKey: 'id:4', sourcePlayerId: 4, playerName: 'Gamma', teamNames: ['T3'] }),
      player({ playerKey: 'id:5', sourcePlayerId: 5, playerName: 'Delta', teamNames: ['T4'] }),
      player({ playerKey: 'id:6', sourcePlayerId: 6, playerName: 'Echo', teamNames: ['T5'] }),
    ]))
    const wrapper = mountPage()
    await flushPromises()

    for (let index = 0; index < 5; index += 1) {
      await wrapper.findAll('.player-row')[index].trigger('click')
    }
    expect(wrapper.findAll('.selected-players .basket-item')).toHaveLength(5)
    await vi.waitFor(() => {
      expect(wrapper.findAll('.radar-metric-row')).toHaveLength(40)
    })

    await wrapper.findAll('.player-row')[5].trigger('click')
    expect(wrapper.text()).toContain('最多同时对比 5 名选手')
    expect(wrapper.findAll('.selected-players .basket-item')).toHaveLength(5)
    wrapper.unmount()
  })

  it('位置筛选变化时自动刷新列表', async () => {
    const wrapper = mountPage()
    await flushPromises()
    expect(api.playerStatisticsByKeys).toHaveBeenCalledWith(['237:100'], 5, '', 'kda', 'desc')

    await wrapper.setProps({ positionFilter: 'TOP' })
    await flushPromises()
    expect(api.playerStatisticsByKeys).toHaveBeenLastCalledWith(['237:100'], 5, 'TOP', 'kda', 'desc')
    wrapper.unmount()
  })

  it('从 URL 恢复已选选手（列表加载后重建选择）', async () => {
    const wrapper = mountPage({ selectedPlayerIds: [1, 3] })
    await flushPromises()

    const basketItems = wrapper.findAll('.selected-players .basket-item')
    expect(basketItems).toHaveLength(2)
    expect(basketItems[0].text()).toContain('Knight')
    expect(basketItems[1].text()).toContain('369')
    wrapper.unmount()
  })

  it('已选选手详情链接携带对比视图返回地址（含关键字与已选选手）', async () => {
    const wrapper = mountPage({ positionFilter: 'TOP', searchKeyword: 'Kni' })
    await flushPromises()

    await wrapper.findAll('.player-row')[0].trigger('click')
    await wrapper.findAll('.player-row')[1].trigger('click')

    const href = wrapper.get('a.selected-name').attributes('href') ?? ''
    expect(href).toContain('/players/1?')
    expect(href).toContain('stageKeys=237%3A100')
    expect(href).toContain('position=TOP')
    expect(href).toContain('minimumMatchCount=5')
    const decoded = decodeURIComponent(decodeURIComponent(href))
    expect(decoded).toContain('returnTo=/?view=compare')
    expect(decoded).toContain('compareKeyword=Kni')
    expect(decoded).toContain('comparePlayers=1,2')
    wrapper.unmount()
  })
})
