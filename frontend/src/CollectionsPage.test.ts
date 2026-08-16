// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import CollectionsPage from './CollectionsPage.vue'
import { api, type CollectionStatusRow } from './api'

vi.mock('./api', () => ({
  api: {
    collectionStatus: vi.fn(),
    collectionCoverage: vi.fn(),
  },
}))

function row(overrides: Partial<CollectionStatusRow> = {}): CollectionStatusRow {
  return {
    id: 1,
    collectionType: 'MATCH_GAME',
    sourceSeasonId: 237,
    requestedStageIds: '237:100,237:101',
    status: 'SUCCESS',
    startedAt: '2026-08-01T09:00:00',
    finishedAt: '2026-08-01T09:05:00',
    changedRecords: 120,
    errorMessage: null,
    ...overrides,
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(api.collectionStatus).mockResolvedValue([
    row(),
    row({ id: 2, collectionType: 'HERO', status: 'FAILED', errorMessage: '上游接口超时' }),
    row({ id: 3, collectionType: 'TEAM', status: 'RUNNING', finishedAt: null, changedRecords: 0 }),
    row({ id: 4, collectionType: 'PLAYER', status: 'NO_CHANGE', changedRecords: 0 }),
  ])
})

describe('CollectionsPage', () => {
  it('挂载时加载最近采集记录并渲染表格', async () => {
    const wrapper = mount(CollectionsPage)
    await flushPromises()

    expect(vi.mocked(api.collectionStatus)).toHaveBeenCalledWith(50)
    const table = wrapper.get('table.collection-table')
    expect(table.text()).toContain('对局明细')
    expect(table.text()).toContain('英雄数据')
    expect(table.text()).toContain('战队数据')
    expect(table.findAll('tbody tr')).toHaveLength(4)
    wrapper.unmount()
  })

  it('按状态渲染徽章样式与文案', async () => {
    const wrapper = mount(CollectionsPage)
    await flushPromises()

    expect(wrapper.findAll('.status-badge')[0].classes()).toContain('status-success')
    expect(wrapper.findAll('.status-badge')[0].text()).toContain('成功')
    expect(wrapper.findAll('.status-badge')[1].classes()).toContain('status-failed')
    expect(wrapper.findAll('.status-badge')[1].text()).toContain('失败')
    expect(wrapper.findAll('.status-badge')[2].classes()).toContain('status-running')
    expect(wrapper.findAll('.status-badge')[2].text()).toContain('进行中')
    expect(wrapper.findAll('.status-badge')[3].text()).toContain('未变更')
    wrapper.unmount()
  })

  it('展示失败任务的错误信息', async () => {
    const wrapper = mount(CollectionsPage)
    await flushPromises()

    expect(wrapper.get('.collection-table').text()).toContain('上游接口超时')
    wrapper.unmount()
  })

  it('展示变更记录数', async () => {
    const wrapper = mount(CollectionsPage)
    await flushPromises()

    expect(wrapper.get('.collection-table').text()).toContain('120')
    wrapper.unmount()
  })

  it('无记录时展示空状态', async () => {
    vi.mocked(api.collectionStatus).mockResolvedValue([])
    const wrapper = mount(CollectionsPage)
    await flushPromises()

    expect(wrapper.get('.empty-state').text()).toContain('暂无采集记录')
    wrapper.unmount()
  })

  it('加载失败时展示错误提示', async () => {
    vi.mocked(api.collectionStatus).mockRejectedValue(new Error('网络错误'))
    const wrapper = mount(CollectionsPage)
    await flushPromises()

    expect(wrapper.get('.message.error').text()).toContain('网络错误')
    wrapper.unmount()
  })

  it('渲染数据覆盖矩阵并支持缺口筛选', async () => {
    vi.mocked(api.collectionCoverage).mockResolvedValue({
      stages: [
        { sourceSeasonId: 237, sourceStageId: 106, seasonName: '2026职业联赛', stageName: '第三赛段组内赛', heroCollected: true, teamCollected: true, playerCollected: true, matchGameCount: 36 },
        { sourceSeasonId: 206, sourceStageId: 91, seasonName: '2024职业联赛', stageName: '第二赛段淘汰赛', heroCollected: true, teamCollected: true, playerCollected: true, matchGameCount: 0 },
      ],
    })
    const wrapper = mount(CollectionsPage)
    await flushPromises()

    const matrix = wrapper.get('.coverage-section')
    expect(matrix.text()).toContain('2026职业联赛')
    expect(matrix.text()).toContain('36 局')
    expect(matrix.text()).toContain('未回填')

    const gapButton = wrapper.findAll('.coverage-heading .pos-chip')
      .find((button) => button.text().includes('存在缺口'))
    expect(gapButton).toBeDefined()
    await gapButton!.trigger('click')
    const rows = wrapper.findAll('.coverage-table tbody tr')
    expect(rows).toHaveLength(1)
    expect(rows[0].text()).toContain('2024职业联赛')
    wrapper.unmount()
  })

  it('缺口筛选下无缺口时展示完整空态', async () => {
    vi.mocked(api.collectionCoverage).mockResolvedValue({
      stages: [
        { sourceSeasonId: 237, sourceStageId: 106, seasonName: '2026职业联赛', stageName: '第三赛段组内赛', heroCollected: true, teamCollected: true, playerCollected: true, matchGameCount: 36 },
        { sourceSeasonId: 206, sourceStageId: 91, seasonName: '2024职业联赛', stageName: '第二赛段淘汰赛', heroCollected: true, teamCollected: true, playerCollected: true, matchGameCount: 12 },
      ],
    })
    const wrapper = mount(CollectionsPage)
    await flushPromises()

    const gapButton = wrapper.findAll('.coverage-heading .pos-chip')
      .find((button) => button.text().includes('存在缺口'))
    await gapButton!.trigger('click')

    const empty = wrapper.get('.coverage-empty')
    expect(empty.text()).toContain('全部赛段数据完整，无缺口')
    expect(empty.text()).toContain('2 个赛段')
    expect(wrapper.findAll('.coverage-table tbody tr')).toHaveLength(0)
    wrapper.unmount()
  })

  it('覆盖矩阵加载失败时不影响采集记录展示', async () => {
    vi.mocked(api.collectionCoverage).mockRejectedValue(new Error('覆盖查询失败'))
    const wrapper = mount(CollectionsPage)
    await flushPromises()

    expect(wrapper.get('table.collection-table').text()).toContain('对局明细')
    expect(wrapper.find('.coverage-section table').exists()).toBe(false)
    wrapper.unmount()
  })
})
