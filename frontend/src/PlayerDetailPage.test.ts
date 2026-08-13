// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import PlayerDetailPage from './PlayerDetailPage.vue'
import { api, type PlayerDetailStatisticsResult } from './api'

vi.mock('./api', () => ({
  api: {
    playerDetail: vi.fn(),
  },
}))

function detailResult(overrides: Partial<PlayerDetailStatisticsResult> = {}): PlayerDetailStatisticsResult {
  return {
    dataVersion: 9,
    minimumMatchCount: 5,
    position: 'TOP',
    cohortSize: 18,
    player: {
      sourcePlayerId: 2687,
      playerName: 'Bin',
      playerAvatar: null,
      teamNames: ['BLG'],
      positions: ['TOP'],
      matchCount: 10,
      gameCount: 25,
    },
    coreMetrics: [
      { key: 'kda', label: 'KDA', value: 4.2, formattedValue: '4.20', rank: 3, cohortSize: 18, higherIsBetter: true },
      { key: 'deathPerGame', label: '场均死亡', value: 1.6, formattedValue: '1.60', rank: 1, cohortSize: 18, higherIsBetter: false },
      { key: 'damagePercent', label: '伤害占比', value: 0.25, formattedValue: '25.00%', rank: 4, cohortSize: 18, higherIsBetter: true },
    ],
    radarMetrics: [
      { key: 'kda', label: 'KDA', value: 4.2, averageValue: 3.1, playerScore: 80, averageScore: 50, rank: 3, cohortSize: 18 },
      { key: 'killPerGame', label: '场均击杀', value: 3, averageValue: 2.5, playerScore: 70, averageScore: 50, rank: 5, cohortSize: 18 },
      { key: 'killParticipantPercent', label: '参团率', value: 0.7, averageValue: 0.65, playerScore: 60, averageScore: 50, rank: 6, cohortSize: 18 },
      { key: 'damagePercent', label: '伤害占比', value: 0.25, averageValue: 0.24, playerScore: 55, averageScore: 50, rank: 7, cohortSize: 18 },
      { key: 'creepScorePerGame', label: '场均补刀', value: 300, averageValue: 280, playerScore: 65, averageScore: 50, rank: 4, cohortSize: 18 },
      { key: 'goldGapPerGame', label: '场均经济差', value: 100, averageValue: 50, playerScore: 75, averageScore: 50, rank: 2, cohortSize: 18 },
    ],
    heroUsageAvailable: true,
    missingHeroStageKeys: [],
    heroUsageTotalGames: 10,
    heroes: [
      {
        sourceChampionId: 1, championName: 'Annie', championChineseName: '安妮', championTitle: '黑暗之女', championLogo: null,
        pickCount: 6, pickRate: 0.6, winningCount: 3, winningRate: 0.5,
        totalKills: 12, totalDeaths: 6, totalAssists: 18,
        kda: 5, killPerGame: 2, deathPerGame: 1, assistPerGame: 3,
      },
      {
        sourceChampionId: 2, championName: 'Garen', championChineseName: '盖伦', championTitle: '德玛西亚之力', championLogo: null,
        pickCount: 4, pickRate: 0.4, winningCount: 3, winningRate: 0.75,
        totalKills: 8, totalDeaths: 4, totalAssists: 10,
        kda: 4.5, killPerGame: 2, deathPerGame: 1, assistPerGame: 2.5,
      },
    ],
    latestCollectedAt: '2026-08-01T10:00:00',
    averageContrastMetrics: [
      { key: 'killPerGame', label: '击杀', value: 3, averageValue: 2.5, minValue: 1, maxValue: 5, rank: 5, cohortSize: 18, higherIsBetter: true, percentage: false },
      { key: 'deathPerGame', label: '死亡', value: 1.6, averageValue: 2.1, minValue: 0.8, maxValue: 4, rank: 1, cohortSize: 18, higherIsBetter: false, percentage: false },
      { key: 'assistPerGame', label: '助攻', value: 4, averageValue: 3.5, minValue: 1, maxValue: 7, rank: 4, cohortSize: 18, higherIsBetter: true, percentage: false },
      { key: 'creepScorePerGame', label: '补刀', value: 300, averageValue: 280, minValue: 200, maxValue: 340, rank: 4, cohortSize: 18, higherIsBetter: true, percentage: false },
      { key: 'damagePerGame', label: '伤害', value: 800, averageValue: 700, minValue: 500, maxValue: 1100, rank: 3, cohortSize: 18, higherIsBetter: true, percentage: false },
      { key: 'damagePercent', label: '伤害占比', value: 0.25, averageValue: 0.24, minValue: 0.18, maxValue: 0.38, rank: 4, cohortSize: 18, higherIsBetter: true, percentage: true },
      { key: 'goldPerGame', label: '经济', value: 12000, averageValue: 11000, minValue: 9000, maxValue: 15000, rank: 3, cohortSize: 18, higherIsBetter: true, percentage: false },
    ],
    ...overrides,
  }
}

async function mountAt(path: string): Promise<{ wrapper: ReturnType<typeof mount>; router: Router }> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/players/:playerId', component: PlayerDetailPage, props: true },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(PlayerDetailPage, {
    global: { plugins: [router] },
    props: { playerId: String(router.currentRoute.value.params.playerId ?? '') },
  })
  return { wrapper, router }
}

const DETAIL_URL = '/players/2687?stageKeys=237:102,237:103&position=TOP&minimumMatchCount=5'

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(api.playerDetail).mockResolvedValue(detailResult())
})

describe('选手详情页', () => {
  it('按 URL 参数加载详情并展示竞赛排名格式', async () => {
    const { wrapper } = await mountAt(DETAIL_URL)
    await flushPromises()

    expect(vi.mocked(api.playerDetail)).toHaveBeenCalledWith(2687, ['237:102', '237:103'], 'TOP', 5)
    expect(wrapper.text()).toContain('Bin')
    expect(wrapper.text()).toContain('BLG')
    expect(wrapper.text()).toContain('4.20')
    expect(wrapper.text()).toContain('25.00%')
    expect(wrapper.text()).toContain('第 3 名 / 共 18 人')
    expect(wrapper.text()).toContain('越低越好')
    expect(wrapper.text()).toContain('共 18 名同位置合格选手参与比较')
    expect(wrapper.text()).toContain('职业场均对比')
    expect(wrapper.findAll('.average-contrast-item')).toHaveLength(7)
    wrapper.unmount()
  })

  it('展示统计范围与数据更新时间', async () => {
    const { wrapper } = await mountAt(DETAIL_URL)
    await flushPromises()

    expect(wrapper.text()).toContain('237:102、237:103')
    expect(wrapper.text()).toContain('最低样本 5 场')
    expect(wrapper.text()).toContain('数据版本 v9')
    expect(wrapper.text()).toContain('数据更新至')
    wrapper.unmount()
  })

  it('单人样本时提示样本不足', async () => {
    vi.mocked(api.playerDetail).mockResolvedValue(detailResult({
      cohortSize: 1,
      coreMetrics: [
        { key: 'kda', label: 'KDA', value: 4.2, formattedValue: '4.20', rank: 1, cohortSize: 1, higherIsBetter: true },
      ],
    }))
    const { wrapper } = await mountAt(DETAIL_URL)
    await flushPromises()

    expect(wrapper.text()).toContain('样本不足')
    wrapper.unmount()
  })

  it('多位置选手提供位置切换并带相同赛段与门槛重新查询', async () => {
    vi.mocked(api.playerDetail).mockResolvedValue(detailResult({
      player: {
        sourcePlayerId: 2687, playerName: 'Xun', playerAvatar: null,
        teamNames: ['BLG'], positions: ['TOP', 'JUG'], matchCount: 10, gameCount: 25,
      },
    }))
    const { wrapper, router } = await mountAt(DETAIL_URL)
    await flushPromises()

    const tabs = wrapper.findAll('.position-tab')
    expect(tabs.map((tab) => tab.text())).toEqual(['上单', '打野'])
    await tabs[1].trigger('click')
    await flushPromises()

    expect(vi.mocked(api.playerDetail)).toHaveBeenLastCalledWith(2687, ['237:102', '237:103'], 'JUG', 5)
    expect(String(router.currentRoute.value.query.position)).toBe('JUG')
    wrapper.unmount()
  })

  it('英雄明细部分赛段缺失时不展示部分统计并说明原因', async () => {
    vi.mocked(api.playerDetail).mockResolvedValue(detailResult({
      heroUsageAvailable: false,
      missingHeroStageKeys: ['237:104'],
      heroes: [],
      heroUsageTotalGames: 0,
    }))
    const { wrapper } = await mountAt(DETAIL_URL)
    await flushPromises()

    expect(wrapper.text()).toContain('英雄使用统计暂不可用')
    expect(wrapper.text()).toContain('237:104')
    expect(wrapper.find('.hero-table').exists()).toBe(false)
    expect(wrapper.text()).toContain('核心数据与同位置排名')
    wrapper.unmount()
  })

  it('后端 404 消息直接展示', async () => {
    vi.mocked(api.playerDetail).mockRejectedValue(new Error('选手 99 不存在'))
    const { wrapper } = await mountAt('/players/99?stageKeys=237:102&position=TOP')
    await flushPromises()

    expect(wrapper.text()).toContain('选手 99 不存在')
    wrapper.unmount()
  })

  it('缺少查询参数时引导用户从列表入口打开', async () => {
    const { wrapper } = await mountAt('/players/2687')
    await flushPromises()

    expect(wrapper.text()).toContain('缺少查询参数')
    expect(vi.mocked(api.playerDetail)).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('英雄表支持点击表头排序', async () => {
    const { wrapper } = await mountAt(DETAIL_URL)
    await flushPromises()

    const heroNames = () => wrapper.findAll('.hero-table tbody strong').map((node) => node.text())
    expect(heroNames()).toEqual(['安妮', '盖伦'])

    const winningRateHeader = wrapper.findAll('.hero-table thead th')
      .find((header) => header.text().includes('胜率'))
    await winningRateHeader!.trigger('click')
    expect(heroNames()).toEqual(['盖伦', '安妮'])

    await winningRateHeader!.trigger('click')
    expect(heroNames()).toEqual(['安妮', '盖伦'])
    wrapper.unmount()
  })

  it('提供统计口径说明且不含英雄级 MVP', async () => {
    const { wrapper } = await mountAt(DETAIL_URL)
    await flushPromises()

    expect(wrapper.text()).toContain('统计口径说明')
    expect(wrapper.text()).toContain('竞赛排名')
    expect(wrapper.text()).toContain('不提供英雄级 MVP 统计')
    wrapper.unmount()
  })
})
