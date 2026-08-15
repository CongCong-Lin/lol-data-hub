// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import App from './App.vue'
import { api, type ChampionStatisticsResult, type PlayerStatisticsResult, type Stage, type TeamCombinationStatisticsResult, type TeamStatisticsResult } from './api'

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
    teamCombinationStatisticsByKeys: vi.fn(),
    matchGames: vi.fn(),
    collectionStatus: vi.fn(),
    playerDetail: vi.fn(),
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
    damagePerGame: 500,
    damagePercent: 0.25,
    goldPercent: 0.22,
    sampleQualified: true,
  }],
}

const teamResult: TeamStatisticsResult = {
  dataVersion: 6,
  minimumMatchCount: 5,
  total: 1,
  items: [{
    teamId: 1,
    teamName: 'TES',
    teamLogo: null,
    matchCount: 5,
    gameCount: 12,
    matchWinCount: 3,
    winningRate: 0.6,
    kda: 3.2,
    totalKills: 120,
    killPerGame: 10,
    totalDeaths: 80,
    deathPerGame: 6.67,
    damagePerGame: 42180.5,
    averageGameDurationSeconds: 1800,
    goldPerMinute: 2000,
    wardPlacedPerMinute: 0.67,
    wardKilledPerMinute: 0.33,
    drakeControlRate: 0.6,
    baronControlRate: 0.5,
    firstBloodRate: 0.4,
    damagePerMinute: 1406.02,
    creepScorePerMinute: 8.1,
    turretKillPerGame: 4.2,
    turretLostPerGame: 2.3,
    wardPlacedPerGame: 20,
    wardKilledPerGame: 10,
    goldPerGame: 60000,
    baronKillPerGame: 0.5,
    drakeKillPerGame: 2,
    sampleQualified: true,
  }],
}

const combinationResult: TeamCombinationStatisticsResult = {
  dataVersion: 7,
  combinationType: 'MID_JUNGLE',
  minimumPickCount: 3,
  total: 1,
  items: [{
    teamId: 1,
    teamName: 'TES',
    teamLogo: null,
    combinationType: 'MID_JUNGLE',
    firstPosition: 'JUN',
    firstChampionId: 62,
    firstChampionName: '孙悟空',
    firstChampionTitle: '齐天大圣',
    firstChampionLogo: null,
    secondPosition: 'MID',
    secondChampionId: 84,
    secondChampionName: '阿卡丽',
    secondChampionTitle: '离群之刺',
    secondChampionLogo: null,
    pickCount: 4,
    validGameCount: 10,
    pickRate: 0.4,
    winningCount: 3,
    winningRate: 0.75,
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
  vi.mocked(api.teamStatisticsByKeys).mockResolvedValue(teamResult)
  vi.mocked(api.playerStatisticsByKeys).mockResolvedValue(playerResult)
  vi.mocked(api.teamCombinationStatisticsByKeys).mockResolvedValue(combinationResult)
  vi.mocked(api.matchGames).mockResolvedValue({
    dataVersion: 17,
    total: 2,
    offset: 0,
    limit: 50,
    items: [{
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
    }],
  })
  vi.mocked(api.collectionStatus).mockResolvedValue([{
    id: 1,
    collectionType: 'MATCH_GAME',
    sourceSeasonId: 237,
    requestedStageIds: '237:100',
    status: 'SUCCESS',
    startedAt: '2026-08-01T09:00:00',
    finishedAt: '2026-08-01T09:05:00',
    changedRecords: 120,
    errorMessage: null,
  }])
})

afterEach(() => {
  window.history.replaceState({}, '', '/')
})

async function selectFirstStage(wrapper: ReturnType<typeof mount>) {
  await wrapper.get('button.stage-chip').trigger('click')
}

describe('查询状态', () => {
  it('显示精简后的页面主标题', async () => {
    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('赛事数据')
    wrapper.unmount()
  })

  it('首次进入时不自动选择任何赛段', async () => {
    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.findAll('button.stage-chip.selected')).toHaveLength(0)
    expect(wrapper.findAll('.basket-item')).toHaveLength(0)
    expect(wrapper.get('button.primary').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('请在上方赛段列表中勾选要查询的赛段')
    wrapper.unmount()
  })

  it('赛事下拉选项按赛事 ID 从小到大排列并保留默认选择', async () => {
    vi.mocked(api.seasons).mockResolvedValue([
      { sourceSeasonId: 237, name: '2026职业联赛', startTime: null, endTime: null, open: true },
      { sourceSeasonId: 190, name: '2023职业联赛', startTime: null, endTime: null, open: true },
      { sourceSeasonId: 239, name: '2026季中冠军赛', startTime: null, endTime: null, open: true },
      { sourceSeasonId: 218, name: '2025职业联赛', startTime: null, endTime: null, open: true },
    ])
    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.findAll('#season option').map((option) => Number(option.attributes('value'))))
      .toEqual([190, 218, 237, 239])
    expect((wrapper.get('#season').element as HTMLSelectElement).value).toBe('237')
    wrapper.unmount()
  })

  it('修改服务端查询条件后不再展示旧结果', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await selectFirstStage(wrapper)
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

    await selectFirstStage(wrapper)
    const playerTab = wrapper.findAll('button.tab-btn').find((button) => button.text() === '选手统计')
    expect(playerTab).toBeDefined()
    await playerTab!.trigger('click')
    await flushPromises()

    const topPosition = wrapper.findAll('button.pos-chip').find((button) => button.text() === '上单')
    expect(topPosition).toBeDefined()
    await topPosition!.trigger('click')
    await flushPromises()

    expect(vi.mocked(api.playerStatisticsByKeys)).toHaveBeenCalledWith(
      ['237:100'], 5, 'TOP', 'kda', 'desc',
    )
    wrapper.unmount()
  })

  it('提交选手查询后选手信息区域包裹当前页面打开的详情链接', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await selectFirstStage(wrapper)
    const playerTab = wrapper.findAll('button.tab-btn').find((button) => button.text() === '选手统计')
    await playerTab!.trigger('click')
    await flushPromises()
    await wrapper.get('button.primary').trigger('click')
    await flushPromises()

    const link = wrapper.get('.player-detail-link')
    expect(link.attributes('target')).toBeUndefined()
    expect(link.attributes('rel')).toBeUndefined()
    const href = link.attributes('href') ?? ''
    expect(href).toContain('/players/1?')
    expect(href).toContain('stageKeys=237%3A100')
    expect(href).toContain('position=TOP')
    expect(href).toContain('minimumMatchCount=5')
    expect(href).toContain('returnTo=%2F%3Fview%3Dplayer')
    expect(decodeURIComponent(href)).toContain('playerSortBy=kda')
    expect(link.text()).toContain('TopPlayer')
    expect(link.find('.player-cell strong').text()).toBe('TopPlayer')
    wrapper.unmount()
  })

  it('从选手详情返回时恢复原查询条件并自动重新加载结果', async () => {
    window.history.replaceState(
      {},
      '',
      '/?view=player&season=237&stageKeys=237%3A100&minimumMatchCount=7&playerPosition=TOP&playerSearch=Top&playerSortBy=goldPercent&playerSortDirection=asc&page=1&pageSize=20&playerColumns=player%2Ckda',
    )

    const wrapper = mount(App)
    await flushPromises()
    await flushPromises()

    expect(wrapper.find('button.tab-btn.active').text()).toBe('选手统计')
    expect(wrapper.findAll('button.stage-chip.selected')).toHaveLength(1)
    expect(wrapper.findAll('button.pos-chip.active').map((button) => button.text())).toEqual(['上单'])
    expect(wrapper.find('.search-wrap input').element).toHaveProperty('value', 'Top')
    expect(wrapper.find('.page-size-select').element).toHaveProperty('value', '20')
    expect(wrapper.findAll('.player-table thead th')).toHaveLength(2)
    expect(vi.mocked(api.playerStatisticsByKeys)).toHaveBeenLastCalledWith(
      ['237:100'], 7, 'TOP', 'goldPercent', 'asc',
    )
    expect(wrapper.text()).toContain('TopPlayer')
    wrapper.unmount()
  })

  it('位置筛选优先写入详情链接的快照参数', async () => {
    vi.mocked(api.playerStatisticsByKeys).mockResolvedValue({
      ...playerResult,
      items: playerResult.items.map((item) => ({ ...item, positions: ['TOP', 'JUG'] })),
    })
    const wrapper = mount(App)
    await flushPromises()

    await selectFirstStage(wrapper)
    const playerTab = wrapper.findAll('button.tab-btn').find((button) => button.text() === '选手统计')
    await playerTab!.trigger('click')
    await flushPromises()
    const jungleChip = wrapper.findAll('button.pos-chip').find((button) => button.text() === '打野')
    expect(jungleChip).toBeDefined()
    await jungleChip!.trigger('click')
    await flushPromises()

    const href = wrapper.get('.player-detail-link').attributes('href') ?? ''
    expect(href).toContain('position=JUG')
    wrapper.unmount()
  })

  it('查询条件变化清空结果后详情链接同步移除', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await selectFirstStage(wrapper)
    const playerTab = wrapper.findAll('button.tab-btn').find((button) => button.text() === '选手统计')
    await playerTab!.trigger('click')
    await flushPromises()
    await wrapper.get('button.primary').trigger('click')
    await flushPromises()
    expect(wrapper.find('.player-detail-link').exists()).toBe(true)

    await wrapper.get('#minimumMatch').setValue('6')

    expect(wrapper.find('.player-detail-link').exists()).toBe(false)
    wrapper.unmount()
  })

  it('将英雄实际分路作为统计条件传给后端', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await selectFirstStage(wrapper)
    const topPosition = wrapper.findAll('button.pos-chip').find((button) => button.text() === '上单')
    expect(topPosition).toBeDefined()
    await topPosition!.trigger('click')
    await flushPromises()

    expect(vi.mocked(api.championStatisticsByKeys)).toHaveBeenCalledWith(
      ['237:100'], 10, 'TOP', 'bpRate', 'desc',
    )
    expect(wrapper.text()).toContain('出场、胜负与 KDA 按实际分路独立统计')
    wrapper.unmount()
  })

  it('点击位置筛选后自动重新查询，无需再次点击查询按钮', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await selectFirstStage(wrapper)
    await wrapper.get('button.primary').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('安妮')

    const topPosition = wrapper.findAll('button.pos-chip').find((button) => button.text() === '上单')
    expect(topPosition).toBeDefined()
    await topPosition!.trigger('click')
    await flushPromises()

    expect(vi.mocked(api.championStatisticsByKeys)).toHaveBeenLastCalledWith(
      ['237:100'], 10, 'TOP', 'bpRate', 'desc',
    )
    expect(wrapper.text()).toContain('安妮')
    wrapper.unmount()
  })

  it('切换组合类型后自动重新查询', async () => {
    const wrapper = mount(App)
    await flushPromises()

    const comboTab = wrapper.findAll('button.tab-btn').find((button) => button.text() === '英雄组合')
    await comboTab!.trigger('click')
    await flushPromises()
    await selectFirstStage(wrapper)
    await wrapper.get('button.primary').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('孙悟空')

    const supportCombo = wrapper.findAll('button.pos-chip').find((button) => button.text() === 'AD 辅助组合')
    expect(supportCombo).toBeDefined()
    await supportCombo!.trigger('click')
    await flushPromises()

    expect(vi.mocked(api.teamCombinationStatisticsByKeys)).toHaveBeenLastCalledWith(
      ['237:100'], 'BOT_SUPPORT', 3, 'pickCount', 'desc',
    )
    wrapper.unmount()
  })

  it('按同队同局口径查询并展示组合选取率和胜率', async () => {
    const wrapper = mount(App)
    await flushPromises()

    const comboTab = wrapper.findAll('button.tab-btn').find((button) => button.text() === '英雄组合')
    await comboTab!.trigger('click')
    await flushPromises()
    await selectFirstStage(wrapper)
    await wrapper.get('button.primary').trigger('click')
    await flushPromises()

    expect(vi.mocked(api.teamCombinationStatisticsByKeys)).toHaveBeenCalledWith(
      ['237:100'], 'MID_JUNGLE', 3, 'pickCount', 'desc',
    )
    expect(wrapper.text()).toContain('孙悟空')
    expect(wrapper.text()).toContain('阿卡丽')
    expect(wrapper.text()).toContain('40.00%')
    expect(wrapper.text()).toContain('75.00%')
    expect(wrapper.text()).toContain('同一战队、同一系列赛且同一小局')
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

    const stageChips = wrapper.findAll('button.stage-chip')
    await stageChips[0].trigger('click')
    await stageChips[1].trigger('click')
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

    await selectFirstStage(wrapper)
    await wrapper.get('button.primary').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('.champion-table tbody tr')).toHaveLength(10)
    expect(wrapper.get('.pagination-row-count').text()).toContain('第 1–10 项，共 25 项')
    expect(wrapper.findAll('.page-size-select option').map((option) => option.text()))
      .toEqual(['10', '20', '50', '100'])

    await wrapper.get('.pagination-next').trigger('click')
    expect(wrapper.findAll('.champion-table tbody tr')).toHaveLength(10)
    expect(wrapper.get('.pagination-row-count').text()).toContain('第 11–20 项，共 25 项')
    expect(wrapper.text()).toContain('英雄20')

    await wrapper.get('button[aria-label="第 3 页"]').trigger('click')
    expect(wrapper.findAll('.champion-table tbody tr')).toHaveLength(5)
    expect(wrapper.get('.pagination-row-count').text()).toContain('第 21–25 项，共 25 项')
    expect(wrapper.text()).toContain('英雄25')

    await wrapper.get('.page-size-select').setValue('50')
    expect(wrapper.findAll('.champion-table tbody tr')).toHaveLength(25)
    expect(wrapper.get('.pagination-row-count').text()).toContain('第 1–25 项，共 25 项')
    wrapper.unmount()
  })

  it('默认显示全部列并可按统计类型独立隐藏列', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await selectFirstStage(wrapper)
    await wrapper.get('button.primary').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.champion-table .sort-header')).toHaveLength(17)

    await wrapper.get('.column-menu-trigger').trigger('click')
    const championCheckboxes = wrapper.findAll('.column-menu-option input')
    expect(championCheckboxes).toHaveLength(17)
    expect(championCheckboxes.every((checkbox) => (checkbox.element as HTMLInputElement).checked)).toBe(true)

    const banRateOption = wrapper.findAll('.column-menu-option')
      .find((option) => option.text() === '禁用率')
    expect(banRateOption).toBeDefined()
    await banRateOption!.get('input').setValue(false)

    expect(wrapper.findAll('.champion-table th').map((header) => header.text())).not.toContain('禁用率')
    expect(wrapper.findAll('.champion-table tbody td')).toHaveLength(16)

    const teamTab = wrapper.findAll('button.tab-btn').find((button) => button.text() === '战队统计')
    await teamTab!.trigger('click')
    await flushPromises()
    await wrapper.get('button.primary').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.team-table .sort-header')).toHaveLength(26)
    await wrapper.get('.column-menu-trigger').trigger('click')
    expect(wrapper.findAll('.column-menu-option input')).toHaveLength(26)

    const playerTab = wrapper.findAll('button.tab-btn').find((button) => button.text() === '选手统计')
    await playerTab!.trigger('click')
    await flushPromises()
    await wrapper.get('button.primary').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.player-table .sort-header')).toHaveLength(20)
    await wrapper.get('.column-menu-trigger').trigger('click')
    expect(wrapper.findAll('.column-menu-option input')).toHaveLength(20)
    wrapper.unmount()
  })

  it('点击可排序表头切换升序和降序并重新查询', async () => {
    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.find('#sort').exists()).toBe(false)
    expect(wrapper.find('#direction').exists()).toBe(false)
    await selectFirstStage(wrapper)
    await wrapper.get('button.primary').trigger('click')
    await flushPromises()

    const bpRateHeader = wrapper.findAll('.sort-header').find((button) => button.text().includes('BP 率'))
    expect(bpRateHeader).toBeDefined()
    expect(bpRateHeader!.text()).toContain('▼')
    expect(wrapper.findAll('.champion-table .sort-indicator')).toHaveLength(17)
    expect(wrapper.findAll('.champion-table .sort-indicator.is-active')).toHaveLength(1)
    expect(wrapper.get('.champion-table [data-sort-field="bpRate"]').attributes('aria-sort')).toBe('descending')
    expect(wrapper.get('.champion-table').attributes('style')).toContain('width: 1679px')

    let resolveSortedResult!: (value: ChampionStatisticsResult) => void
    vi.mocked(api.championStatisticsByKeys).mockImplementationOnce(() => new Promise((resolve) => {
      resolveSortedResult = resolve
    }))
    await bpRateHeader!.trigger('click')
    expect(wrapper.find('.champion-table').exists()).toBe(true)
    expect(wrapper.get('.table-scroll').attributes('aria-busy')).toBe('true')
    expect(wrapper.text()).toContain('安妮')

    resolveSortedResult(championResult)
    await flushPromises()
    expect(vi.mocked(api.championStatisticsByKeys)).toHaveBeenLastCalledWith(
      ['237:100'], 10, '', 'bpRate', 'asc',
    )
    expect(wrapper.get('.table-scroll').attributes('aria-busy')).toBe('false')
    expect(wrapper.findAll('.sort-header').find((button) => button.text().includes('BP 率'))!.text()).toContain('▲')
    expect(wrapper.findAll('.champion-table .sort-indicator.is-active')).toHaveLength(1)
    expect(wrapper.get('.champion-table').attributes('style')).toContain('width: 1679px')

    const winningRateHeader = wrapper.findAll('.sort-header').find((button) => button.text().includes('胜率'))
    await winningRateHeader!.trigger('click')
    await flushPromises()
    expect(vi.mocked(api.championStatisticsByKeys)).toHaveBeenLastCalledWith(
      ['237:100'], 10, '', 'winningRate', 'desc',
    )
    expect(wrapper.findAll('.sort-header').find((button) => button.text().includes('胜率'))!.text()).toContain('▼')
    wrapper.unmount()
  })
})

describe('详情链接与 URL 状态同步', () => {
  it('英雄统计行包裹指向英雄详情页的链接', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await selectFirstStage(wrapper)
    await wrapper.get('button.primary').trigger('click')
    await flushPromises()

    const link = wrapper.get('.champion-detail-link')
    const href = link.attributes('href') ?? ''
    expect(href).toContain('/champions/1?')
    expect(href).toContain('stageKeys=237%3A100')
    expect(href).toContain('minimumPickCount=10')
    expect(href).toContain('returnTo=%2F%3Fview%3Dchampion')
    wrapper.unmount()
  })

  it('战队统计行包裹指向战队详情页的链接', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await selectFirstStage(wrapper)
    const teamTab = wrapper.findAll('button.tab-btn').find((button) => button.text() === '战队统计')
    await teamTab!.trigger('click')
    await flushPromises()
    await wrapper.get('button.primary').trigger('click')
    await flushPromises()

    const link = wrapper.get('.team-detail-link')
    const href = link.attributes('href') ?? ''
    expect(href).toContain('/teams/1?')
    expect(href).toContain('stageKeys=237%3A100')
    expect(href).toContain('minimumMatchCount=5')
    expect(href).toContain('returnTo=%2F%3Fview%3Dteam')
    wrapper.unmount()
  })

  it('未选择赛段时英雄与战队不渲染详情链接', async () => {
    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.find('.champion-detail-link').exists()).toBe(false)
    expect(wrapper.find('.team-detail-link').exists()).toBe(false)
    expect(wrapper.find('.player-detail-link').exists()).toBe(false)
    wrapper.unmount()
  })

  it('点击上野组合按钮后按 TOP_JUNGLE 口径重新查询', async () => {
    const wrapper = mount(App)
    await flushPromises()

    const comboTab = wrapper.findAll('button.tab-btn').find((button) => button.text() === '英雄组合')
    await comboTab!.trigger('click')
    await flushPromises()
    await selectFirstStage(wrapper)
    await wrapper.get('button.primary').trigger('click')
    await flushPromises()

    const topJungle = wrapper.findAll('button.pos-chip').find((button) => button.text() === '上野组合')
    expect(topJungle).toBeDefined()
    await topJungle!.trigger('click')
    await flushPromises()

    expect(vi.mocked(api.teamCombinationStatisticsByKeys)).toHaveBeenLastCalledWith(
      ['237:100'], 'TOP_JUNGLE', 3, 'pickCount', 'desc',
    )
    // 上野组合口径下列名应为「上单英雄 / 打野英雄」
    const comboHeaders = wrapper.findAll('th').map((th) => th.text())
    expect(comboHeaders.some((text) => text.includes('上单英雄'))).toBe(true)
    expect(comboHeaders.some((text) => text.includes('打野英雄'))).toBe(true)
    wrapper.unmount()
  })

  it('点击上中组合按钮后按 TOP_MID 口径重新查询', async () => {
    const wrapper = mount(App)
    await flushPromises()

    const comboTab = wrapper.findAll('button.tab-btn').find((button) => button.text() === '英雄组合')
    await comboTab!.trigger('click')
    await flushPromises()
    await selectFirstStage(wrapper)
    await wrapper.get('button.primary').trigger('click')
    await flushPromises()

    const topMid = wrapper.findAll('button.pos-chip').find((button) => button.text() === '上中组合')
    expect(topMid).toBeDefined()
    await topMid!.trigger('click')
    await flushPromises()

    expect(vi.mocked(api.teamCombinationStatisticsByKeys)).toHaveBeenLastCalledWith(
      ['237:100'], 'TOP_MID', 3, 'pickCount', 'desc',
    )
    wrapper.unmount()
  })

  it('点击中下组合按钮后按 MID_BOT 口径重新查询', async () => {
    const wrapper = mount(App)
    await flushPromises()

    const comboTab = wrapper.findAll('button.tab-btn').find((button) => button.text() === '英雄组合')
    await comboTab!.trigger('click')
    await flushPromises()
    await selectFirstStage(wrapper)
    await wrapper.get('button.primary').trigger('click')
    await flushPromises()

    const midBot = wrapper.findAll('button.pos-chip').find((button) => button.text() === '中下组合')
    expect(midBot).toBeDefined()
    await midBot!.trigger('click')
    await flushPromises()

    expect(vi.mocked(api.teamCombinationStatisticsByKeys)).toHaveBeenLastCalledWith(
      ['237:100'], 'MID_BOT', 3, 'pickCount', 'desc',
    )
    wrapper.unmount()
  })

  it('查询条件变化时将状态写入地址栏', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await selectFirstStage(wrapper)
    expect(window.location.search).toContain('stageKeys=237%3A100')
    expect(window.location.search).toContain('view=champion')
    expect(window.location.search).toContain('minimumPickCount=10')

    await wrapper.get('#minimum').setValue('11')
    expect(window.location.search).toContain('minimumPickCount=11')
    wrapper.unmount()
  })

  it('带查询参数进入时恢复英雄分路筛选并保留在 URL 中', async () => {
    window.history.replaceState(
      {},
      '',
      '/?view=champion&season=237&stageKeys=237%3A100&minimumPickCount=6&position=TOP&championSortBy=winningRate&championSortDirection=asc',
    )

    const wrapper = mount(App)
    await flushPromises()
    await flushPromises()

    expect(wrapper.findAll('button.pos-chip.active').map((button) => button.text())).toEqual(['上单'])
    expect(vi.mocked(api.championStatisticsByKeys)).toHaveBeenLastCalledWith(
      ['237:100'], 6, 'TOP', 'winningRate', 'asc',
    )
    expect(window.location.search).toContain('position=TOP')
    wrapper.unmount()
  })
})

describe('页内视图切换（对局赛果 / 选手对比 / 采集状态）', () => {
  async function clickNav(wrapper: ReturnType<typeof mount>, text: string) {
    const link = wrapper.findAll('a.site-link').find((item) => item.text() === text)
    expect(link).toBeDefined()
    await link!.trigger('click')
    await flushPromises()
    await flushPromises()
  }

  it('点击导航切换到对局赛果视图并写入地址栏', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await clickNav(wrapper, '对局赛果')

    expect(wrapper.find('.matches-panel').exists()).toBe(true)
    expect(wrapper.find('.controls').exists()).toBe(true)
    expect(wrapper.find('#minimum').exists()).toBe(false)
    expect(wrapper.find('button.tab-btn.active').exists()).toBe(false)
    expect(window.location.search).toContain('view=matches')
    expect(window.location.search).toContain('matchesSortBy=startTime')

    // 选择赛段后对局面板自动查询
    await wrapper.get('button.stage-chip').trigger('click')
    await flushPromises()
    await flushPromises()
    expect(vi.mocked(api.matchGames)).toHaveBeenCalledWith(['237:100'], 'startTime', 'desc', 0, 50)
    expect(wrapper.text()).toContain('TES')
    wrapper.unmount()
  })

  it('导航高亮跟随当前视图', async () => {
    const wrapper = mount(App)
    await flushPromises()

    const statisticsLink = wrapper.findAll('a.site-link').find((item) => item.text() === '统计查询')
    const matchesLink = wrapper.findAll('a.site-link').find((item) => item.text() === '对局赛果')
    expect(statisticsLink!.classes()).toContain('active')

    await matchesLink!.trigger('click')
    await flushPromises()
    await flushPromises()

    expect(statisticsLink!.classes()).not.toContain('active')
    expect(matchesLink!.classes()).toContain('active')

    // 统计视图下「统计查询」重新高亮
    await statisticsLink!.trigger('click')
    await flushPromises()
    expect(statisticsLink!.classes()).toContain('active')
    wrapper.unmount()
  })

  it('带 view=matches 参数进入时恢复对局赛果视图与排序状态', async () => {
    window.history.replaceState(
      {},
      '',
      '/?view=matches&season=237&stageKeys=237%3A100&matchesSortBy=matchId&matchesSortDirection=asc&matchesOffset=50',
    )

    const wrapper = mount(App)
    await flushPromises()
    await flushPromises()
    await flushPromises()

    expect(wrapper.find('.matches-panel').exists()).toBe(true)
    expect(vi.mocked(api.matchGames)).toHaveBeenCalledWith(['237:100'], 'matchId', 'asc', 50, 50)
    expect(window.location.search).toContain('matchesSortBy=matchId')
    wrapper.unmount()
  })

  it('对局赛果视图下点击排序按钮更新地址栏参数', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await clickNav(wrapper, '对局赛果')
    await wrapper.get('button.stage-chip').trigger('click')
    await flushPromises()
    await flushPromises()

    await wrapper.findAll('button.pos-chip').find((button) => button.text()!.includes('系列赛'))!.trigger('click')
    await flushPromises()

    expect(window.location.search).toContain('matchesSortBy=matchId')
    expect(window.location.search).toContain('matchesSortDirection=desc')
    expect(vi.mocked(api.matchGames)).toHaveBeenLastCalledWith(['237:100'], 'matchId', 'desc', 0, 50)
    wrapper.unmount()
  })

  it('带 view=compare 参数进入时恢复选手对比视图与筛选条件', async () => {
    window.history.replaceState(
      {},
      '',
      '/?view=compare&season=237&stageKeys=237%3A100&comparePosition=TOP&compareMinimumMatchCount=7',
    )

    const wrapper = mount(App)
    await flushPromises()
    await flushPromises()

    expect(wrapper.find('.compare-panel').exists()).toBe(true)
    expect((wrapper.get('#comparePosition').element as HTMLSelectElement).value).toBe('TOP')
    expect((wrapper.get('#compareMinimum').element as HTMLInputElement).value).toBe('7')
    expect(wrapper.find('.controls button.primary').exists()).toBe(false)
    expect(window.location.search).toContain('comparePosition=TOP')
    wrapper.unmount()
  })

  it('带 view=collections 参数进入时隐藏查询控件并渲染采集状态', async () => {
    window.history.replaceState({}, '', '/?view=collections')

    const wrapper = mount(App)
    await flushPromises()
    await flushPromises()

    expect(wrapper.find('.controls').exists()).toBe(false)
    expect(wrapper.find('.collections-panel').exists()).toBe(true)
    expect(vi.mocked(api.collectionStatus)).toHaveBeenCalledWith(50)
    expect(wrapper.text()).toContain('对局明细')
    wrapper.unmount()
  })
})
