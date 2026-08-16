// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import TeamDetailPage from './TeamDetailPage.vue'
import { api, type MatchGameRecord, type TeamDetailStatisticsResult } from './api'

vi.mock('./api', () => ({
  api: {
    teamDetail: vi.fn(),
  },
}))

function recentGame(overrides: Partial<MatchGameRecord> = {}): MatchGameRecord {
  return {
    sourceSeasonId: 237,
    sourceStageId: 100,
    sourceMatchId: 9001,
    gameNumber: 1,
    startTime: '2026-03-01T10:00:00Z',
    teamAId: 1,
    teamAName: 'TES',
    teamALogo: null,
    teamAKills: 15,
    teamAAssists: 20,
    teamADamage: 60000,
    teamAGold: 70000,
    teamAWardsPlaced: 40,
    teamAWardsKilled: 20,
    teamAMinionKills: 600,
    teamADragons: 3,
    teamABarons: 1,
    teamATurrets: 8,
    teamAFirstBlood: true,
    teamBId: 2,
    teamBName: 'BLG',
    teamBLogo: null,
    teamBKills: 8,
    teamBAssists: 12,
    teamBDamage: 50000,
    teamBGold: 60000,
    teamBWardsPlaced: 35,
    teamBWardsKilled: 15,
    teamBMinionKills: 580,
    teamBDragons: 1,
    teamBBarons: 0,
    teamBTurrets: 2,
    teamBFirstBlood: false,
    winnerTeamId: 1,
    gameDurationSeconds: 2100,
    ...overrides,
  }
}

function detailResult(overrides: Partial<TeamDetailStatisticsResult> = {}): TeamDetailStatisticsResult {
  return {
    dataVersion: 9,
    minimumMatchCount: 5,
    cohortSize: 16,
    team: {
      sourceTeamId: 1,
      teamName: 'TES',
      teamLogo: null,
      matchCount: 5,
      gameCount: 12,
      matchWinCount: 3,
    },
    coreMetrics: [
      { key: 'winningRate', label: '胜率', value: 0.6, formattedValue: '60.00%', rank: 3, cohortSize: 16, higherIsBetter: true },
      { key: 'kda', label: 'KDA', value: 3.2, formattedValue: '3.20', rank: 2, cohortSize: 16, higherIsBetter: true },
      { key: 'damagePerGame', label: '场均伤害', value: 42180.5, formattedValue: '42180.50', rank: 5, cohortSize: 16, higherIsBetter: true },
    ],
    lineupPreferences: [
      {
        position: 'MID', sourceChampionId: 84, championName: 'Akali', championChineseName: '阿卡丽', championLogo: null,
        pickCount: 4, pickRate: 0.4, winningCount: 3, winningRate: 0.75,
      },
    ],
    players: [
      { sourcePlayerId: 101, playerName: 'Knight', playerAvatar: null, position: 'MID', matchCount: 5, gameCount: 12 },
      { sourcePlayerId: 103, playerName: 'Tian', playerAvatar: null, position: 'JUG', matchCount: 5, gameCount: 12 },
      { sourcePlayerId: 104, playerName: 'JackeyLove', playerAvatar: null, position: 'BOT', matchCount: 5, gameCount: 12 },
    ],
    recentGames: [recentGame()],
    latestCollectedAt: '2026-08-01T10:00:00',
    ...overrides,
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(api.teamDetail).mockResolvedValue(detailResult())
})

async function mountAt(path: string, teamId = '1'): Promise<{ wrapper: ReturnType<typeof mount>; router: Router }> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/teams/:teamId', component: TeamDetailPage, props: true },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(TeamDetailPage, {
    global: { plugins: [router] },
    props: { teamId },
  })
  return { wrapper, router }
}

describe('TeamDetailPage', () => {
  it('缺少 stageKeys 参数时提示从战队统计入口打开', async () => {
    const { wrapper } = await mountAt('/teams/1')
    await flushPromises()

    expect(wrapper.get('.detail-error').text()).toContain('stageKeys')
    expect(api.teamDetail).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('无效的战队 ID 时提示错误', async () => {
    const { wrapper } = await mountAt('/teams/abc?stageKeys=237:100', 'abc')
    await flushPromises()

    expect(wrapper.get('.detail-error').text()).toContain('无效的战队 ID')
    expect(api.teamDetail).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('加载战队详情并展示档案与核心指标', async () => {
    const { wrapper } = await mountAt('/teams/1?stageKeys=237:100')
    await flushPromises()

    expect(vi.mocked(api.teamDetail)).toHaveBeenCalledWith(1, ['237:100'], 5)
    expect(wrapper.get('.profile-name').text()).toBe('TES')
    const metricLabels = wrapper.findAll('.core-metric-item .core-metric-label').map((node) => node.text())
    expect(metricLabels).toEqual(['胜率', 'KDA', '场均伤害'])
    expect(wrapper.get('.core-metric-value').text()).toContain('60.00%')
    wrapper.unmount()
  })

  it('阵容偏好表展示分路、英雄与胜率', async () => {
    const { wrapper } = await mountAt('/teams/1?stageKeys=237:100')
    await flushPromises()

    expect(wrapper.get('.lineup-table').text()).toContain('阿卡丽')
    expect(wrapper.get('.lineup-table').text()).toContain('75.0%')
    wrapper.unmount()
  })

  it('阵容偏好提供全部与五路筛选按钮，筛选无结果时提示', async () => {
    const { wrapper } = await mountAt('/teams/1?stageKeys=237:100')
    await flushPromises()

    const chips = wrapper.findAll('button.pos-chip')
    expect(chips.map((chip) => chip.text())).toEqual(['全部', '上单', '打野', '中路', '下路', '辅助'])
    expect(wrapper.get('.lineup-table').text()).toContain('阿卡丽')

    const jungle = chips.find((chip) => chip.text() === '打野')!
    await jungle.trigger('click')
    await flushPromises()
    expect(wrapper.find('.lineup-table').exists()).toBe(false)
    expect(wrapper.text()).toContain('该分路暂无已采集的英雄出场记录')

    const all = wrapper.findAll('button.pos-chip').find((chip) => chip.text() === '全部')!
    await all.trigger('click')
    await flushPromises()
    expect(wrapper.get('.lineup-table').text()).toContain('阿卡丽')
    wrapper.unmount()
  })

  it('阵容偏好分路筛选只展示所选分路的英雄', async () => {
    vi.mocked(api.teamDetail).mockResolvedValue(detailResult({
      lineupPreferences: [
        {
          position: 'TOP', sourceChampionId: 150, championName: 'Gnar', championChineseName: '纳尔', championLogo: null,
          pickCount: 3, pickRate: 0.3, winningCount: 2, winningRate: 0.67,
        },
        {
          position: 'BOT', sourceChampionId: 202, championName: 'Jinx', championChineseName: '金克丝', championLogo: null,
          pickCount: 5, pickRate: 0.5, winningCount: 4, winningRate: 0.8,
        },
      ],
    }))
    const { wrapper } = await mountAt('/teams/1?stageKeys=237:100')
    await flushPromises()

    const bottom = wrapper.findAll('button.pos-chip').find((chip) => chip.text() === '下路')!
    await bottom.trigger('click')
    await flushPromises()
    const tableText = wrapper.get('.lineup-table').text()
    expect(tableText).toContain('金克丝')
    expect(tableText).not.toContain('纳尔')
    wrapper.unmount()
  })

  it('选手名单行链接到选手详情页并映射分路', async () => {
    const { wrapper } = await mountAt('/teams/1?stageKeys=237:100')
    await flushPromises()

    const links = wrapper.findAll('.roster-table a.player-link')
    expect(links).toHaveLength(3)
    expect(links[0].attributes('href')).toContain('/players/101?')
    expect(links[0].attributes('href')).toContain('position=MID')
    expect(links[1].attributes('href')).toContain('position=JUG')
    expect(links[2].attributes('href')).toContain('position=AD')
    wrapper.unmount()
  })

  it('近期对局表渲染对手、比分与胜方徽章，并提供详情链接', async () => {
    const { wrapper } = await mountAt('/teams/1?stageKeys=237:100')
    await flushPromises()

    const recentText = wrapper.get('.recent-table').text()
    expect(recentText).toContain('BLG')
    expect(recentText).toContain('15')
    expect(wrapper.find('.recent-table .result-badge.won').exists()).toBe(true)
    const href = wrapper.get('.recent-table a.view-link').attributes('href') ?? ''
    expect(href).toContain('/matches/9001?')
    expect(href).toContain('stageKeys=237%3A100')
    wrapper.unmount()
  })

  it('未传 minimumMatchCount 时使用默认值 5', async () => {
    const { wrapper } = await mountAt('/teams/1?stageKeys=237:100')
    await flushPromises()
    expect(vi.mocked(api.teamDetail)).toHaveBeenCalledWith(1, ['237:100'], 5)
    wrapper.unmount()
  })
})
