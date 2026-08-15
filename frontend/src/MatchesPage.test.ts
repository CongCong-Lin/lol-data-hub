// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import MatchesPage from './MatchesPage.vue'
import { api, type MatchGameRecord, type MatchGamesResult, type Stage } from './api'

vi.mock('./api', () => ({
  api: {
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
  vi.mocked(api.availability).mockResolvedValue(stages)
  vi.mocked(api.matchGames).mockResolvedValue(gamesResult())
})

function mountPage(props: Partial<InstanceType<typeof MatchesPage>['$props']> = {}) {
  return mount(MatchesPage, {
    props: {
      stageKeys: ['237:100'],
      submitted: true,
      sortBy: 'startTime',
      sortDirection: 'desc',
      offset: 0,
      ...props,
    },
  })
}

describe('MatchesPage', () => {
  it('未选择赛段时提示先选择赛段且不查询', async () => {
    const wrapper = mountPage({ stageKeys: [], submitted: false })
    await flushPromises()

    expect(wrapper.text()).toContain('请先在上方选择要查询的赛段')
    expect(api.matchGames).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('已选赛段但未提交查询时展示提示且不查询', async () => {
    const wrapper = mountPage({ submitted: false })
    await flushPromises()

    expect(wrapper.text()).toContain('选择赛段后点击查询')
    expect(api.matchGames).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('提交查询后按传入赛段与排序加载并渲染对局赛果', async () => {
    const wrapper = mountPage({ submitted: false })
    await flushPromises()
    expect(api.matchGames).not.toHaveBeenCalled()

    await wrapper.setProps({ submitted: true })
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
    const wrapper = mountPage()
    await flushPromises()
    await flushPromises()

    const firstRow = wrapper.get('.match-table tbody tr')
    expect(firstRow.get('.result-badge.won-a').text()).toContain('TES')
    expect(firstRow.get('.fb-tag').text()).toBe('FB')
    wrapper.unmount()
  })

  it('对局行包含指向对局详情页的链接', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await flushPromises()

    const href = wrapper.get('a.view-link').attributes('href') ?? ''
    expect(href).toContain('/matches/9001?')
    expect(href).toContain('stageKeys=237%3A100')
    wrapper.unmount()
  })

  it('点击排序按钮通过事件切换排序', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await flushPromises()

    await wrapper.findAll('button.pos-chip').find((button) => button.text()!.includes('系列赛'))!.trigger('click')
    await flushPromises()

    expect(wrapper.emitted('update:sortBy')).toEqual([['matchId']])
    expect(wrapper.emitted('update:sortDirection')).toEqual([['desc']])
    expect(wrapper.emitted('update:offset')).toEqual([[0]])
    wrapper.unmount()
  })

  it('排序状态变化时重新查询', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await flushPromises()

    await wrapper.setProps({ sortBy: 'matchId', sortDirection: 'asc', offset: 0 })
    await flushPromises()

    expect(vi.mocked(api.matchGames)).toHaveBeenLastCalledWith(['237:100'], 'matchId', 'asc', 0, 50)
    wrapper.unmount()
  })

  it('分页按钮按每页 50 局翻页', async () => {
    vi.mocked(api.matchGames).mockResolvedValue(gamesResult({ total: 120 }))
    const wrapper = mountPage()
    await flushPromises()
    await flushPromises()

    const nextButton = wrapper.findAll('button').find((button) => button.text() === '下一页')
    expect(nextButton).toBeDefined()
    await nextButton!.trigger('click')
    await flushPromises()

    expect(wrapper.emitted('update:offset')).toEqual([[50]])
    wrapper.unmount()
  })

  it('offset 变化时重新查询', async () => {
    vi.mocked(api.matchGames).mockResolvedValue(gamesResult({ total: 120, offset: 50 }))
    const wrapper = mountPage({ offset: 50 })
    await flushPromises()
    await flushPromises()

    expect(vi.mocked(api.matchGames)).toHaveBeenLastCalledWith(['237:100'], 'startTime', 'desc', 50, 50)
    wrapper.unmount()
  })

  it('未提交时赛段变化不查询；重新提交后按新赛段查询', async () => {
    const wrapper = mountPage({ submitted: false })
    await flushPromises()
    expect(api.matchGames).not.toHaveBeenCalled()

    // 赛段变化时父组件（App）会重置 submitted，结果清空且不查询
    await wrapper.setProps({ stageKeys: ['237:100', '237:101'] })
    await flushPromises()
    expect(api.matchGames).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('选择赛段后点击查询')

    await wrapper.setProps({ submitted: true })
    await flushPromises()

    expect(vi.mocked(api.matchGames)).toHaveBeenLastCalledWith(
      ['237:100', '237:101'], 'startTime', 'desc', 0, 50,
    )
    wrapper.unmount()
  })

  it('提交后取消提交时清空结果', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await flushPromises()
    expect(wrapper.find('.match-table').exists()).toBe(true)

    await wrapper.setProps({ submitted: false })
    await flushPromises()

    expect(wrapper.find('.match-table').exists()).toBe(false)
    expect(wrapper.text()).toContain('选择赛段后点击查询')
    wrapper.unmount()
  })

  it('查询失败时展示错误信息', async () => {
    vi.mocked(api.matchGames).mockRejectedValue(new Error('对局数据加载失败'))
    const wrapper = mountPage()
    await flushPromises()
    await flushPromises()

    expect(wrapper.get('.message.error').text()).toContain('对局数据加载失败')
    wrapper.unmount()
  })
})
