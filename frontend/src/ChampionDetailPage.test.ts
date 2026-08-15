// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import ChampionDetailPage from './ChampionDetailPage.vue'
import { api, type ChampionDetailStatisticsResult, type ChampionStatistics } from './api'

vi.mock('./api', () => ({
  api: {
    championDetail: vi.fn(),
  },
}))

vi.mock('./ChampionTrendChart.vue', () => ({
  default: {
    name: 'ChampionTrendChart',
    template: '<div class="trend-chart-stub" />',
  },
}))

function overall(): ChampionStatistics {
  return {
    championId: 84,
    championName: 'Akali',
    championTitle: '离群之刺',
    championLogo: null,
    positions: ['MID', 'TOP'],
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
    mostUsedPlayers: ['Knight'],
    sampleQualified: true,
    sourceUpdatedAt: '2026-03-08T12:38:16Z',
  }
}

function detailResult(overrides: Partial<ChampionDetailStatisticsResult> = {}): ChampionDetailStatisticsResult {
  return {
    dataVersion: 8,
    minimumPickCount: 3,
    position: 'MID',
    champion: {
      sourceChampionId: 84,
      championName: 'Akali',
      championChineseName: '阿卡丽',
      championTitle: '离群之刺',
      championLogo: null,
      positions: ['MID', 'TOP'],
    },
    overall: overall(),
    positionStats: [
      { position: 'MID', pickCount: 8, winningCount: 5, pickRate: 0.15, winningRate: 0.62, kda: 5.2 },
      { position: 'TOP', pickCount: 4, winningCount: 2, pickRate: 0.08, winningRate: 0.5, kda: 4.4 },
    ],
    topPlayers: [
      { sourcePlayerId: 101, playerName: 'Knight', playerAvatar: null, position: 'MID', pickCount: 5, winningCount: 3, winningRate: 0.6, kda: 5.5 },
    ],
    trends: [
      {
        sourceSeasonId: 237, sourceStageId: 100, stageName: '第一赛段',
        pickCount: 12, banCount: 3, winningCount: 7, pickRate: 0.2, banRate: 0.05, winningRate: 0.58,
      },
    ],
    latestCollectedAt: '2026-08-01T10:00:00',
    ...overrides,
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(api.championDetail).mockResolvedValue(detailResult())
})

async function mountAt(path: string, championId = '84'): Promise<{ wrapper: ReturnType<typeof mount>; router: Router }> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/champions/:championId', component: ChampionDetailPage, props: true },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(ChampionDetailPage, {
    global: { plugins: [router] },
    props: { championId },
  })
  return { wrapper, router }
}

describe('ChampionDetailPage', () => {
  it('缺少 stageKeys 参数时提示从英雄统计入口打开', async () => {
    const { wrapper } = await mountAt('/champions/84')
    await flushPromises()

    expect(wrapper.get('.detail-error').text()).toContain('stageKeys')
    expect(api.championDetail).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('无效的英雄 ID 时提示错误', async () => {
    const { wrapper } = await mountAt('/champions/abc?stageKeys=237:100', 'abc')
    await flushPromises()

    expect(wrapper.get('.detail-error').text()).toContain('无效的英雄 ID')
    expect(api.championDetail).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('加载英雄详情并展示档案与总体指标', async () => {
    const { wrapper } = await mountAt('/champions/84?stageKeys=237:100')
    await flushPromises()

    expect(vi.mocked(api.championDetail)).toHaveBeenCalledWith(84, ['237:100'], 5, '')
    expect(wrapper.get('.profile-name').text()).toBe('阿卡丽')
    expect(wrapper.text()).toContain('12')
    expect(wrapper.text()).toContain('58.0%')
    wrapper.unmount()
  })

  it('多分路英雄渲染位置标签并可切换分路', async () => {
    const { wrapper, router } = await mountAt('/champions/84?stageKeys=237:100')
    await flushPromises()

    const tabs = wrapper.findAll('.position-tab')
    expect(tabs).toHaveLength(2)
    expect(tabs.map((tab) => tab.text())).toEqual(['中路', '上单'])

    await tabs[1].trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.position).toBe('TOP')
    expect(vi.mocked(api.championDetail)).toHaveBeenLastCalledWith(84, ['237:100'], 5, 'TOP')
    wrapper.unmount()
  })

  it('选手使用榜行链接到选手详情页并映射分路', async () => {
    const { wrapper } = await mountAt('/champions/84?stageKeys=237:100&position=MID')
    await flushPromises()

    const href = wrapper.get('.detail-table a.player-link').attributes('href') ?? ''
    expect(href).toContain('/players/101?')
    expect(href).toContain('stageKeys=237%3A100')
    expect(href).toContain('position=MID')
    expect(href).toContain('minimumMatchCount=3')
    wrapper.unmount()
  })

  it('渲染赛段趋势图组件', async () => {
    const { wrapper } = await mountAt('/champions/84?stageKeys=237:100')
    await flushPromises()

    expect(wrapper.find('.trend-chart-stub').exists()).toBe(true)
    wrapper.unmount()
  })

  it('URL 中的分路参数直接传给后端', async () => {
    const { wrapper } = await mountAt('/champions/84?stageKeys=237:100&position=TOP')
    await flushPromises()

    expect(vi.mocked(api.championDetail)).toHaveBeenCalledWith(84, ['237:100'], 5, 'TOP')
    wrapper.unmount()
  })
})
