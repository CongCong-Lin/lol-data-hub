// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import MatchesPage from './MatchesPage.vue'
import { api, type MatchGameRecord, type MatchGamesResult, type Stage } from './api'

vi.mock('./api', () => ({
  api: {
    seasons: vi.fn(),
    availability: vi.fn(),
    matchGames: vi.fn(),
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

function gamesResult(overrides: Partial<MatchGamesResult> = {}): MatchGamesResult {
  return {
    dataVersion: 17,
    total: 2,
    offset: 0,
    limit: 50,
    items: [game(), game({ sourceMatchId: 9002, gameNumber: 2, winnerTeamId: 2 })],
    ...overrides,
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
  vi.mocked(api.matchGames).mockResolvedValue(gamesResult())
})

async function mountAt(path: string): Promise<{ wrapper: ReturnType<typeof mount>; router: Router }> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/matches', component: MatchesPage },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(MatchesPage, { global: { plugins: [router] } })
  return { wrapper, router }
}

describe('MatchesPage', () => {
  it('未选择赛段时提示先选择赛段', async () => {
    const { wrapper } = await mountAt('/matches')
    await flushPromises()

    expect(wrapper.get('.matches-page').text()).toContain('请选择要查询的赛段')
    expect(api.matchGames).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('选择赛段后按默认排序查询并渲染对局赛果', async () => {
    const { wrapper } = await mountAt('/matches')
    await flushPromises()

    await wrapper.get('button.stage-chip').trigger('click')
    await flushPromises()
    await flushPromises()

    expect(vi.mocked(api.matchGames)).toHaveBeenCalledWith(['237:100'], 'startTime', 'desc', 0, 50)
    expect(wrapper.text()).toContain('共 2 局')
    expect(wrapper.text()).toContain('TES')
    expect(wrapper.text()).toContain('BLG')
    expect(wrapper.text()).toContain('15 : 8')
    expect(wrapper.findAll('.match-table tbody tr')).toHaveLength(2)
    wrapper.unmount()
  })

  it('为胜方渲染结果徽章与一血标记', async () => {
    const { wrapper } = await mountAt('/matches')
    await flushPromises()

    await wrapper.get('button.stage-chip').trigger('click')
    await flushPromises()
    await flushPromises()

    const firstRow = wrapper.get('.match-table tbody tr')
    expect(firstRow.get('.result-badge.won-a').text()).toContain('TES')
    expect(firstRow.get('.fb-tag').text()).toBe('FB')
    wrapper.unmount()
  })

  it('对局行包含指向对局详情页的链接', async () => {
    const { wrapper } = await mountAt('/matches')
    await flushPromises()

    await wrapper.get('button.stage-chip').trigger('click')
    await flushPromises()
    await flushPromises()

    const href = wrapper.get('a.view-link').attributes('href') ?? ''
    expect(href).toContain('/matches/9001?')
    expect(href).toContain('stageKeys=237%3A100')
    wrapper.unmount()
  })

  it('点击排序按钮切换排序并更新 URL', async () => {
    const { wrapper, router } = await mountAt('/matches')
    await flushPromises()

    await wrapper.get('button.stage-chip').trigger('click')
    await flushPromises()
    await flushPromises()

    await wrapper.findAll('button.pos-chip').find((button) => button.text()!.includes('系列赛'))!.trigger('click')
    await flushPromises()

    expect(vi.mocked(api.matchGames)).toHaveBeenLastCalledWith(['237:100'], 'matchId', 'desc', 0, 50)
    expect(router.currentRoute.value.query.sortBy).toBe('matchId')
    wrapper.unmount()
  })

  it('分页按钮按每页 50 局翻页', async () => {
    vi.mocked(api.matchGames).mockResolvedValue(gamesResult({ total: 120 }))
    const { wrapper } = await mountAt('/matches')
    await flushPromises()

    await wrapper.get('button.stage-chip').trigger('click')
    await flushPromises()
    await flushPromises()

    const nextButton = wrapper.findAll('button').find((button) => button.text() === '下一页')
    expect(nextButton).toBeDefined()
    await nextButton!.trigger('click')
    await flushPromises()

    expect(vi.mocked(api.matchGames)).toHaveBeenLastCalledWith(['237:100'], 'startTime', 'desc', 50, 50)
    wrapper.unmount()
  })

  it('查询失败时展示错误信息', async () => {
    vi.mocked(api.matchGames).mockRejectedValue(new Error('对局数据加载失败'))
    const { wrapper } = await mountAt('/matches')
    await flushPromises()

    await wrapper.get('button.stage-chip').trigger('click')
    await flushPromises()
    await flushPromises()

    expect(wrapper.get('.message.error').text()).toContain('对局数据加载失败')
    wrapper.unmount()
  })
})
