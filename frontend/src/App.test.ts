// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import App from './App.vue'
import { api, type ChampionStatisticsResult, type PlayerStatisticsResult, type Stage } from './api'

vi.mock('./api', () => ({
  api: {
    seasons: vi.fn(),
    stages: vi.fn(),
    availability: vi.fn(),
    championStatistics: vi.fn(),
    teamStatistics: vi.fn(),
    playerStatistics: vi.fn(),
    championStatisticsByKeys: vi.fn(),
    teamStatisticsByKeys: vi.fn(),
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

const championResult: ChampionStatisticsResult = {
  dataVersion: 6,
  minimumPickCount: 10,
  total: 1,
  items: [{
    championId: 1,
    championName: '安妮',
    championTitle: '黑暗之女',
    championLogo: null,
    positions: ['MID'],
    sampleBaseCount: 54,
    pickCount: 12,
    banCount: 3,
    bpCount: 15,
    winningCount: 7,
    totalKills: 20,
    totalDeaths: 10,
    totalAssists: 30,
    pickRate: 0.2,
    banRate: 0.05,
    bpRate: 0.25,
    winningRate: 0.58,
    kda: 5,
    killPerGame: 1.67,
    assistPerGame: 2.5,
    deathPerGame: 0.83,
    mostUsedPlayers: ['Tester'],
    sampleQualified: true,
    sourceUpdatedAt: '2026-03-08T12:38:16Z',
  }],
}

const playerResult: PlayerStatisticsResult = {
  dataVersion: 6,
  minimumMatchCount: 5,
  total: 1,
  items: [{
    playerKey: 'id:1',
    sourcePlayerId: 1,
    playerName: 'TopPlayer',
    playerAvatar: null,
    teamNames: ['TES'],
    positions: ['TOP'],
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
    damagePercent: 0.25,
    goldPercent: 0.22,
    sampleQualified: true,
  }],
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
  vi.mocked(api.championStatisticsByKeys).mockResolvedValue(championResult)
  vi.mocked(api.playerStatisticsByKeys).mockResolvedValue(playerResult)
})

describe('查询状态', () => {
  it('修改服务端查询条件后不再展示旧结果', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await wrapper.get('button.primary').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('安妮')

    await wrapper.get('#minimum').setValue('11')
    expect(wrapper.text()).not.toContain('安妮')
    expect(vi.mocked(api.championStatisticsByKeys)).toHaveBeenCalledWith(
      ['237:100'], 10, '', 'bpRate', 'desc',
    )

    wrapper.unmount()
  })

  it('将选手位置作为统计条件传给后端', async () => {
    const wrapper = mount(App)
    await flushPromises()

    const playerTab = wrapper.findAll('button.tab-btn').find((button) => button.text() === '选手统计')
    expect(playerTab).toBeDefined()
    await playerTab!.trigger('click')
    await flushPromises()

    const topPosition = wrapper.findAll('button.pos-chip').find((button) => button.text() === '上单')
    expect(topPosition).toBeDefined()
    await topPosition!.trigger('click')
    await wrapper.get('button.primary').trigger('click')
    await flushPromises()

    expect(vi.mocked(api.playerStatisticsByKeys)).toHaveBeenCalledWith(
      ['237:100'], 5, 'TOP', 'kda', 'desc',
    )
    wrapper.unmount()
  })

  it('将英雄实际分路作为统计条件传给后端', async () => {
    const wrapper = mount(App)
    await flushPromises()

    const topPosition = wrapper.findAll('button.pos-chip').find((button) => button.text() === '上单')
    expect(topPosition).toBeDefined()
    await topPosition!.trigger('click')
    await wrapper.get('button.primary').trigger('click')
    await flushPromises()

    expect(vi.mocked(api.championStatisticsByKeys)).toHaveBeenCalledWith(
      ['237:100'], 10, 'TOP', 'bpRate', 'desc',
    )
    expect(wrapper.text()).toContain('出场、胜负与 KDA 按实际分路独立统计')
    wrapper.unmount()
  })

  it('切换统计类型加载失败时不保留上一类型的赛段', async () => {
    vi.mocked(api.availability)
      .mockReset()
      .mockResolvedValueOnce(stages)
      .mockRejectedValueOnce(new Error('选手赛段加载失败'))
    const wrapper = mount(App)
    await flushPromises()

    const playerTab = wrapper.findAll('button.tab-btn').find((button) => button.text() === '选手统计')
    await playerTab!.trigger('click')
    await flushPromises()

    expect(wrapper.findAll('button.stage-chip')).toHaveLength(0)
    expect(wrapper.get('button.primary').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('选手赛段加载失败')
    wrapper.unmount()
  })

  it('拒绝非整数或超出范围的样本门槛', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await wrapper.get('#minimum').setValue('10000.5')

    expect(wrapper.get('button.primary').attributes('disabled')).toBeDefined()
    expect(wrapper.get('.field-error').text()).toContain('0 到 10000 之间的整数')
    wrapper.unmount()
  })

  it('可以一键清空所有已选赛段', async () => {
    vi.mocked(api.availability).mockResolvedValue([
      stages[0],
      {
        ...stages[0],
        sourceStageId: 101,
        name: '第二赛段',
        sampleBaseCount: 36,
      },
    ])
    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.findAll('.basket-item')).toHaveLength(2)
    expect(wrapper.get('.basket-clear').text()).toBe('清空全部')

    await wrapper.get('.basket-clear').trigger('click')

    expect(wrapper.findAll('.basket-item')).toHaveLength(0)
    expect(wrapper.find('.basket-clear').exists()).toBe(false)
    expect(wrapper.findAll('button.stage-chip.selected')).toHaveLength(0)
    expect(wrapper.get('button.primary').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('请在上方赛段列表中勾选要查询的赛段')
    wrapper.unmount()
  })

  it('对统计结果分页并支持调整每页条数', async () => {
    const items = Array.from({ length: 25 }, (_, index) => ({
      ...championResult.items[0],
      championId: index + 1,
      championName: `英雄${index + 1}`,
    }))
    vi.mocked(api.championStatisticsByKeys).mockResolvedValue({
      ...championResult,
      total: items.length,
      items,
    })
    const wrapper = mount(App)
    await flushPromises()

    await wrapper.get('button.primary').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('.champion-table tbody tr')).toHaveLength(20)
    expect(wrapper.get('.pagination-row-count').text()).toContain('第 1–20 项，共 25 项')

    await wrapper.get('.pagination-next').trigger('click')
    expect(wrapper.findAll('.champion-table tbody tr')).toHaveLength(5)
    expect(wrapper.get('.pagination-row-count').text()).toContain('第 21–25 项，共 25 项')
    expect(wrapper.text()).toContain('英雄25')

    await wrapper.get('.page-size-select').setValue('50')
    expect(wrapper.findAll('.champion-table tbody tr')).toHaveLength(25)
    expect(wrapper.get('.pagination-row-count').text()).toContain('第 1–25 项，共 25 项')
    wrapper.unmount()
  })
})
