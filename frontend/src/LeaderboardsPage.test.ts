// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import LeaderboardsPage from './LeaderboardsPage.vue'
import { api, type PlayerStatistics, type PlayerStatisticsResult, type TeamStatistics, type TeamStatisticsResult } from './api'

vi.mock('./api', () => ({
  api: {
    playerStatisticsByKeys: vi.fn(),
    teamStatisticsByKeys: vi.fn(),
    eloRatings: vi.fn(),
    championVersionCompare: vi.fn(),
  },
}))

function player(overrides: Partial<PlayerStatistics> = {}): PlayerStatistics {
  return {
    playerKey: 'key-1',
    sourcePlayerId: 1,
    playerName: 'Knight',
    playerAvatar: null,
    teamNames: ['TES'],
    positions: ['MID'],
    matchCount: 10,
    gameCount: 22,
    mvpCount: 3,
    mvpVotes: 45,
    totalKills: 180,
    totalAssists: 220,
    totalDeaths: 90,
    kda: 4.4,
    killPerGame: 3.8,
    assistPerGame: 4.1,
    deathPerGame: 1.6,
    goldPerGame: 12000,
    creepScorePerGame: 210,
    wardPlacedPerGame: 8,
    wardKilledPerGame: 3,
    killParticipantPercent: 0.72,
    goldGapPerGame: 300,
    damagePerGame: 28000,
    damagePercent: 0.28,
    goldPercent: 0.24,
    sampleQualified: true,
    ...overrides,
  }
}

function team(overrides: Partial<TeamStatistics> = {}): TeamStatistics {
  return {
    teamId: 1,
    teamName: 'TES',
    teamLogo: null,
    matchCount: 10,
    gameCount: 22,
    matchWinCount: 7,
    sampleQualified: true,
    winningRate: 0.7,
    kda: 3.9,
    totalKills: 380,
    killPerGame: 17.3,
    totalDeaths: 300,
    deathPerGame: 13.6,
    damagePerGame: 95000,
    averageGameDurationSeconds: 1900,
    goldPerMinute: 2000,
    wardPlacedPerMinute: 3.8,
    wardKilledPerMinute: 1.6,
    drakeControlRate: 0.55,
    baronControlRate: 0.6,
    firstBloodRate: 0.65,
    damagePerMinute: 2900,
    creepScorePerMinute: 34,
    turretKillPerGame: 6.9,
    turretLostPerGame: 4.2,
    wardPlacedPerGame: 118,
    wardKilledPerGame: 51,
    goldPerGame: 62000,
    baronKillPerGame: 0.7,
    drakeKillPerGame: 2.8,
    ...overrides,
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  const playerResult: PlayerStatisticsResult = {
    dataVersion: 130,
    minimumMatchCount: 0,
    total: 2,
    items: [
      player(),
      player({ playerKey: 'key-2', sourcePlayerId: 2, playerName: 'Rookie', kda: 3.1, mvpVotes: 20, damagePerGame: 24000 }),
    ],
  }
  const teamResult: TeamStatisticsResult = {
    dataVersion: 130,
    minimumMatchCount: 1,
    total: 1,
    items: [team()],
  }
  vi.mocked(api.playerStatisticsByKeys).mockResolvedValue(playerResult)
  vi.mocked(api.teamStatisticsByKeys).mockResolvedValue(teamResult)
  vi.mocked(api.eloRatings).mockResolvedValue({
    totalGames: 22,
    ratings: [
      { teamId: 1, teamName: 'TES', teamLogo: null, rating: 1540, rank: 1, games: 22, wins: 15, losses: 7, ratingHistory: [1500, 1516, 1540] },
    ],
  })
})

function mountPage() {
  return mount(LeaderboardsPage, { props: { stageKeys: ['237:106'], refreshKey: 0 } })
}

describe('LeaderboardsPage', () => {
  it('随赛段自动加载四个数据源', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(api.playerStatisticsByKeys).toHaveBeenCalledWith(['237:106'], 0, '', 'kda', 'desc')
    expect(api.teamStatisticsByKeys).toHaveBeenCalledWith(['237:106'], 1, 'winningRate', 'desc')
    expect(api.eloRatings).toHaveBeenCalledWith(['237:106'])
    wrapper.unmount()
  })

  it('数据王标签渲染选手与战队卡片', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const cards = wrapper.findAll('.leaderboard-card')
    expect(cards.length).toBeGreaterThanOrEqual(7)
    expect(cards[0].text()).toContain('Knight')
    expect(wrapper.text()).toContain('胜率王（战队）')
    wrapper.unmount()
  })

  it('MVP 榜按票数排序展示', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const mvpTab = wrapper.findAll('.position-filter .pos-chip').find((button) => button.text() === 'MVP 榜')
    await mvpTab!.trigger('click')

    const rows = wrapper.findAll('.team-table tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('Knight')
    expect(rows[0].text()).toContain('45')
    wrapper.unmount()
  })

  it('Elo 标签展示评分与轨迹折线', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const eloTab = wrapper.findAll('.position-filter .pos-chip').find((button) => button.text() === 'Elo 评分')
    await eloTab!.trigger('click')

    expect(wrapper.get('.team-table').text()).toContain('1540')
    expect(wrapper.find('svg.elo-sparkline').exists()).toBe(true)
    wrapper.unmount()
  })

  it('版本变迁标签按日期对比并筛选上升英雄', async () => {
    vi.mocked(api.championVersionCompare).mockResolvedValue({
      fromDate: '2026-07-01',
      toDate: '2026-08-01',
      items: [
        { championId: 1, championName: 'Ahri', championChineseName: '阿狸', championLogo: null, fromPickCount: 5, toPickCount: 18, pickDelta: 13, fromWinRate: 0.5, toWinRate: 0.61, winRateDelta: 0.11 },
        { championId: 2, championName: 'Azir', championChineseName: '阿兹尔', championLogo: null, fromPickCount: 18, toPickCount: 4, pickDelta: -14, fromWinRate: 0.55, toWinRate: 0.4, winRateDelta: -0.15 },
      ],
    })
    const wrapper = mountPage()
    await flushPromises()

    const versionTab = wrapper.findAll('.position-filter .pos-chip').find((button) => button.text() === '版本变迁')
    await versionTab!.trigger('click')

    const dateInputs = wrapper.findAll('input[type="date"]')
    await dateInputs[0].setValue('2026-07-01')
    await dateInputs[1].setValue('2026-08-01')
    await wrapper.get('button.primary').trigger('click')
    await flushPromises()

    expect(api.championVersionCompare).toHaveBeenCalledWith(['237:106'], '2026-07-01', '2026-08-01')
    let rows = wrapper.findAll('.team-table tbody tr')
    expect(rows).toHaveLength(1)
    expect(rows[0].text()).toContain('阿狸')

    const falling = wrapper.findAll('.version-controls .pos-chip')
      .find((button) => button.text() === '跌出版本')
    await falling!.trigger('click')
    rows = wrapper.findAll('.team-table tbody tr')
    expect(rows).toHaveLength(1)
    expect(rows[0].text()).toContain('阿兹尔')
    wrapper.unmount()
  })

  it('未选赛段时提示且不请求数据', async () => {
    const wrapper = mount(LeaderboardsPage, { props: { stageKeys: [], refreshKey: 0 } })
    await flushPromises()

    expect(api.playerStatisticsByKeys).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('请先在上方选择赛段')
    wrapper.unmount()
  })
})
