// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import MatchDetailPage from './MatchDetailPage.vue'
import { api, type MatchGameDetailResult, type MatchGamePlayerRecord, type MatchGameRecord } from './api'

vi.mock('./api', () => ({
  api: {
    matchDetail: vi.fn(),
  },
}))

function game(overrides: Partial<MatchGameRecord> = {}): MatchGameRecord {
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

function player(overrides: Partial<MatchGamePlayerRecord> = {}): MatchGamePlayerRecord {
  return {
    sourceSeasonId: 237,
    sourceStageId: 100,
    sourceMatchId: 9001,
    gameNumber: 1,
    startTime: '2026-03-01T10:00:00Z',
    sourcePlayerId: 101,
    playerName: 'Knight',
    sourceTeamId: 1,
    teamName: 'TES',
    sourceChampionId: 84,
    championName: 'Akali',
    championChineseName: '阿卡丽',
    championTitle: '离群之刺',
    championLogo: null,
    position: 'MID',
    won: true,
    kills: 6,
    deaths: 1,
    assists: 5,
    heroDamage: 28000,
    playerGold: 15000,
    teamKills: 15,
    teamDamage: 60000,
    teamGold: 70000,
    killParticipantPercent: 0.73,
    damagePercent: 0.47,
    goldPercent: 0.21,
    ...overrides,
  }
}

function detailResult(): MatchGameDetailResult {
  const gameTwoPlayers: MatchGamePlayerRecord[] = [
    player({ gameNumber: 2, sourcePlayerId: 111, playerName: 'Knight', sourceTeamId: 1, position: 'MID', won: false, kills: 3, deaths: 4 }),
    player({ gameNumber: 2, sourcePlayerId: 112, playerName: '369', sourceTeamId: 1, position: 'TOP', won: false }),
    player({ gameNumber: 2, sourcePlayerId: 113, playerName: 'Tian', sourceTeamId: 1, position: 'JUG', won: false }),
    player({ gameNumber: 2, sourcePlayerId: 114, playerName: 'JackeyLove', sourceTeamId: 1, position: 'BOT', won: false }),
    player({ gameNumber: 2, sourcePlayerId: 115, playerName: 'Meiko', sourceTeamId: 1, position: 'SUP', won: false }),
    player({ gameNumber: 2, sourcePlayerId: 211, playerName: 'Bin', sourceTeamId: 2, teamName: 'BLG', position: 'TOP', won: true }),
    player({ gameNumber: 2, sourcePlayerId: 212, playerName: 'Xun', sourceTeamId: 2, teamName: 'BLG', position: 'JUG', won: true }),
    player({ gameNumber: 2, sourcePlayerId: 213, playerName: 'knight', sourceTeamId: 2, teamName: 'BLG', position: 'MID', won: true }),
    player({ gameNumber: 2, sourcePlayerId: 214, playerName: 'Elk', sourceTeamId: 2, teamName: 'BLG', position: 'BOT', won: true }),
    player({ gameNumber: 2, sourcePlayerId: 215, playerName: 'ON', sourceTeamId: 2, teamName: 'BLG', position: 'SUP', won: true }),
  ]
  return {
    dataVersion: 17,
    sourceMatchId: 9001,
    games: [game(), game({ gameNumber: 2, winnerTeamId: 2 })],
    players: [
      player({ sourcePlayerId: 101, playerName: 'Knight', sourceTeamId: 1, position: 'MID' }),
      player({ sourcePlayerId: 102, playerName: '369', sourceTeamId: 1, position: 'TOP', sourceChampionId: 1, championChineseName: '安妮' }),
      player({ sourcePlayerId: 103, playerName: 'Tian', sourceTeamId: 1, position: 'JUG', won: false }),
      player({ sourcePlayerId: 104, playerName: 'JackeyLove', sourceTeamId: 1, position: 'BOT' }),
      player({ sourcePlayerId: 105, playerName: 'Meiko', sourceTeamId: 1, position: 'SUP', won: false }),
      player({ sourcePlayerId: 201, playerName: 'Bin', sourceTeamId: 2, teamName: 'BLG', position: 'TOP', won: false, kills: 2, deaths: 4 }),
      player({ sourcePlayerId: 202, playerName: 'Xun', sourceTeamId: 2, teamName: 'BLG', position: 'JUG', won: false }),
      player({ sourcePlayerId: 203, playerName: 'knight', sourceTeamId: 2, teamName: 'BLG', position: 'MID', won: true, kills: 4 }),
      player({ sourcePlayerId: 204, playerName: 'Elk', sourceTeamId: 2, teamName: 'BLG', position: 'BOT', won: true }),
      player({ sourcePlayerId: 205, playerName: 'ON', sourceTeamId: 2, teamName: 'BLG', position: 'SUP', won: true, kills: 1 }),
      ...gameTwoPlayers,
    ],
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(api.matchDetail).mockResolvedValue(detailResult())
})

async function mountAt(path: string, matchId = '9001'): Promise<{ wrapper: ReturnType<typeof mount>; router: Router }> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/matches', component: { template: '<div />' } },
      { path: '/matches/:matchId', component: MatchDetailPage, props: true },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(MatchDetailPage, {
    global: { plugins: [router] },
    props: { matchId },
  })
  return { wrapper, router }
}

describe('MatchDetailPage', () => {
  it('缺少 stageKeys 参数时提示从对局列表页打开', async () => {
    const { wrapper } = await mountAt('/matches/9001')
    await flushPromises()

    expect(wrapper.get('.detail-error').text()).toContain('stageKeys')
    expect(api.matchDetail).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('无效的比赛 ID 时提示错误', async () => {
    const { wrapper } = await mountAt('/matches/abc?stageKeys=237:100', 'abc')
    await flushPromises()

    expect(wrapper.get('.detail-error').text()).toContain('无效的比赛 ID')
    expect(api.matchDetail).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('加载对局详情并渲染两局比赛的计分板', async () => {
    const { wrapper } = await mountAt('/matches/9001?stageKeys=237:100')
    await flushPromises()

    expect(vi.mocked(api.matchDetail)).toHaveBeenCalledWith(9001, ['237:100'])
    expect(wrapper.findAll('.detail-card')).toHaveLength(2)
    expect(wrapper.get('.score-board .score-side').text()).toContain('TES')
    expect(wrapper.get('.score-mid').text()).toContain('15')
    expect(wrapper.get('.winner-tag.won-a').text()).toBeTruthy()
    wrapper.unmount()
  })

  it('渲染 10 名选手的战绩行并在第 5 行后插入队伍分隔线', async () => {
    const { wrapper } = await mountAt('/matches/9001?stageKeys=237:100')
    await flushPromises()

    const rows = wrapper.findAll('.detail-card tbody tr')
    expect(rows).toHaveLength(20)
    expect(wrapper.findAll('tr.team-divider')).toHaveLength(2)
    wrapper.unmount()
  })

  it('选手行链接到选手详情页并携带分路映射', async () => {
    const { wrapper } = await mountAt('/matches/9001?stageKeys=237:100')
    await flushPromises()

    const jugLink = wrapper.findAll('a.player-link')[2]
    const href = jugLink.attributes('href') ?? ''
    expect(href).toContain('/players/103?')
    expect(href).toContain('stageKeys=237%3A100')
    expect(href).toContain('position=JUG')
    expect(href).toContain('minimumMatchCount=3')

    const botLink = wrapper.findAll('a.player-link')[3]
    expect(botLink.attributes('href')).toContain('position=AD')
    wrapper.unmount()
  })

  it('胜方选手渲染胜场徽章', async () => {
    const { wrapper } = await mountAt('/matches/9001?stageKeys=237:100')
    await flushPromises()

    const firstRow = wrapper.get('.detail-card tbody tr')
    expect(firstRow.get('.result-badge.won').text()).toBeTruthy()
    wrapper.unmount()
  })

  it('团队数据对比块展示助攻伤害经济等维度', async () => {
    const { wrapper } = await mountAt('/matches/9001?stageKeys=237:100')
    await flushPromises()

    expect(wrapper.get('.team-stats-title').text()).toContain('团队数据')
    const rowLabels = wrapper.findAll('.team-stats-row .team-stats-title').map((node) => node.text())
    expect(rowLabels).toContain('助攻')
    expect(rowLabels).toContain('伤害')
    expect(rowLabels).toContain('经济')
    wrapper.unmount()
  })
})
