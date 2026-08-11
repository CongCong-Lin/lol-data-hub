<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  api,
  type ChampionStatisticsResult,
  type PlayerStatisticsResult,
  type Season,
  type Stage,
  type StatisticType,
  type TeamStatisticsResult,
} from './api'
import PaginationControls from './PaginationControls.vue'
import ColumnVisibilityMenu, { type ColumnOption } from './ColumnVisibilityMenu.vue'
import SortableHeader from './SortableHeader.vue'

type ActiveView = 'champion' | 'team' | 'player'

const CHAMPION_COLUMNS: ColumnOption[] = [
  { key: 'champion', label: '英雄' }, { key: 'positions', label: '分路' },
  { key: 'pickCount', label: '出场' }, { key: 'pickRate', label: '出场率' },
  { key: 'banCount', label: '禁用' }, { key: 'banRate', label: '禁用率' },
  { key: 'bpRate', label: 'BP 率' }, { key: 'winningCount', label: '胜场' },
  { key: 'winningRate', label: '胜率' }, { key: 'totalKills', label: '总击杀' },
  { key: 'killPerGame', label: '场均击杀' }, { key: 'totalAssists', label: '总助攻' },
  { key: 'assistPerGame', label: '场均助攻' }, { key: 'totalDeaths', label: '总死亡' },
  { key: 'deathPerGame', label: '场均死亡' }, { key: 'kda', label: 'KDA' },
  { key: 'mostUsedPlayers', label: '常用选手' },
]

const TEAM_COLUMNS: ColumnOption[] = [
  { key: 'team', label: '战队' }, { key: 'matchCount', label: '系列赛' },
  { key: 'gameCount', label: '对局' }, { key: 'matchWinCount', label: '胜场' },
  { key: 'winningRate', label: '胜率' }, { key: 'totalKills', label: '总击杀' },
  { key: 'killPerGame', label: '场均击杀' }, { key: 'deathPerGame', label: '场均死亡' },
  { key: 'wardPlacedPerGame', label: '场均插眼' }, { key: 'wardKilledPerGame', label: '场均排眼' },
  { key: 'goldPerGame', label: '场均经济' }, { key: 'baronKillPerGame', label: '场均大龙' },
  { key: 'drakeKillPerGame', label: '场均小龙' },
]

const PLAYER_COLUMNS: ColumnOption[] = [
  { key: 'player', label: '选手' }, { key: 'positions', label: '位置' },
  { key: 'matchCount', label: '系列赛' }, { key: 'gameCount', label: '对局' },
  { key: 'mvpCount', label: 'MVP' }, { key: 'mvpVotes', label: 'MVP 票数' },
  { key: 'kda', label: 'KDA' }, { key: 'totalKills', label: '总击杀' },
  { key: 'killPerGame', label: '场均击杀' }, { key: 'totalAssists', label: '总助攻' },
  { key: 'assistPerGame', label: '场均助攻' }, { key: 'totalDeaths', label: '总死亡' },
  { key: 'deathPerGame', label: '场均死亡' }, { key: 'goldPerGame', label: '场均经济' },
  { key: 'creepScorePerGame', label: '场均补刀' }, { key: 'killParticipantPercent', label: '参团率' },
  { key: 'goldGapPerGame', label: '场均经济差' }, { key: 'damagePercent', label: '伤害占比' },
  { key: 'goldPercent', label: '经济占比' },
]

const CHAMPION_POSITION_OPTIONS = [
  { value: '', label: '全部' },
  { value: 'TOP', label: '上单' },
  { value: 'JUN', label: '打野' },
  { value: 'MID', label: '中路' },
  { value: 'BOT', label: '下路' },
  { value: 'SUP', label: '辅助' },
]

const PLAYER_POSITION_OPTIONS = [
  { value: '', label: '全部' },
  { value: 'TOP', label: '上单' },
  { value: 'JUG', label: '打野' },
  { value: 'MID', label: '中路' },
  { value: 'AD', label: '下路' },
  { value: 'SUP', label: '辅助' },
]

const VIEW_STAT_TYPE: Record<ActiveView, StatisticType> = {
  champion: 'HERO',
  team: 'TEAM',
  player: 'PLAYER',
}

const MAX_STAGE_SELECTION = 50

function makeKey(seasonId: number, stageId: number): string {
  return `${seasonId}:${stageId}`
}

const activeView = ref<ActiveView>('champion')
const seasons = ref<Season[]>([])
const allAvailability = ref<Stage[]>([])
const browsedSeasonId = ref(0)
const selectedStageKeys = ref<Set<string>>(new Set())
const minimumPickCount = ref(10)
const minimumMatchCount = ref(5)
const sortBy = ref('bpRate')
const teamSortBy = ref('winningRate')
const playerSortBy = ref('kda')
const championSortDirection = ref<'asc' | 'desc'>('desc')
const teamSortDirection = ref<'asc' | 'desc'>('desc')
const playerSortDirection = ref<'asc' | 'desc'>('desc')
const positionFilter = ref('')
const playerPositionFilter = ref('')
const search = ref('')
const teamSearch = ref('')
const playerSearch = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const championVisibleColumns = ref(CHAMPION_COLUMNS.map((column) => column.key))
const teamVisibleColumns = ref(TEAM_COLUMNS.map((column) => column.key))
const playerVisibleColumns = ref(PLAYER_COLUMNS.map((column) => column.key))
const result = ref<ChampionStatisticsResult | null>(null)
const teamResult = ref<TeamStatisticsResult | null>(null)
const playerResult = ref<PlayerStatisticsResult | null>(null)
const busy = ref(false)
const availabilityLoading = ref(false)
const notice = ref('')
const error = ref('')
let loadAvailabilitySeq = 0
let querySeq = 0

function isColumnVisible(visibleColumns: string[], key: string): boolean {
  return visibleColumns.includes(key)
}

/* ---- computed ---- */

const sortedSeasons = computed(() =>
  [...seasons.value].sort((left, right) => left.sourceSeasonId - right.sourceSeasonId),
)

const browsedSeasonName = computed(() => {
  const season = seasons.value.find((s) => s.sourceSeasonId === browsedSeasonId.value)
  return season?.name ?? `赛事 #${browsedSeasonId.value}`
})

const browsedStages = computed(() =>
  allAvailability.value.filter((s) => s.sourceSeasonId === browsedSeasonId.value),
)

const selectedStageDetails = computed(() => {
  const keys = selectedStageKeys.value
  return allAvailability.value.filter((s) => keys.has(makeKey(s.sourceSeasonId, s.sourceStageId)))
})

const selectedSeasonCount = computed(() => {
  const ids = new Set(selectedStageDetails.value.map((s) => s.sourceSeasonId))
  return ids.size
})

const totalSampleBase = computed(() =>
  selectedStageDetails.value.reduce((sum, s) => sum + (s.sampleBaseCount ?? 0), 0),
)

const filteredChampionItems = computed(() => {
  let items = result.value?.items ?? []
  const keyword = search.value.trim().toLowerCase()
  if (keyword) {
    items = items.filter((item) =>
      `${item.championName}${item.championTitle ?? ''}${item.positions.join('')}`.toLowerCase().includes(keyword),
    )
  }
  return items
})

const filteredTeamItems = computed(() => {
  let items = teamResult.value?.items ?? []
  const keyword = teamSearch.value.trim().toLowerCase()
  if (keyword) {
    items = items.filter((item) => item.teamName.toLowerCase().includes(keyword))
  }
  return items
})

const filteredPlayerItems = computed(() => {
  let items = playerResult.value?.items ?? []
  if (playerPositionFilter.value) {
    items = items.filter((item) => item.positions.includes(playerPositionFilter.value))
  }
  const keyword = playerSearch.value.trim().toLowerCase()
  if (keyword) {
    items = items.filter((item) =>
      `${item.playerName}${item.teamNames.join('')}`.toLowerCase().includes(keyword),
    )
  }
  return items
})

function paginate<T>(items: T[]): T[] {
  const start = (currentPage.value - 1) * pageSize.value
  return items.slice(start, start + pageSize.value)
}

const paginatedChampionItems = computed(() => paginate(filteredChampionItems.value))
const paginatedTeamItems = computed(() => paginate(filteredTeamItems.value))
const paginatedPlayerItems = computed(() => paginate(filteredPlayerItems.value))

const latestCollectedAt = computed(() => {
  const timestamps = selectedStageDetails.value
    .map((s) => s.collectedAt)
    .filter(Boolean) as string[]
  return timestamps.sort().at(-1) ?? null
})

const latestUpdatedAt = computed(() => {
  if (activeView.value === 'team' || activeView.value === 'player') return latestCollectedAt.value
  const timestamps = (result.value?.items ?? [])
    .map((item) => item.sourceUpdatedAt)
    .filter(Boolean) as string[]
  return timestamps.sort().at(-1) ?? latestCollectedAt.value
})

const currentDataVersion = computed(() => {
  if (activeView.value === 'champion') return result.value?.dataVersion
  if (activeView.value === 'team') return teamResult.value?.dataVersion
  return playerResult.value?.dataVersion
})

function isValidMinimum(value: number): boolean {
  return typeof value === 'number' && Number.isInteger(value) && value >= 0 && value <= 10_000
}

const minimumPickCountValid = computed(() => isValidMinimum(minimumPickCount.value))
const minimumMatchCountValid = computed(() => isValidMinimum(minimumMatchCount.value))
const activeMinimumValid = computed(() =>
  activeView.value === 'champion' ? minimumPickCountValid.value : minimumMatchCountValid.value,
)

const canQuery = computed(() => {
  if (busy.value || availabilityLoading.value) return false
  return selectedStageKeys.value.size > 0 && activeMinimumValid.value
})

function clearStatisticsResults() {
  result.value = null
  teamResult.value = null
  playerResult.value = null
}

function clearActiveResult(view: ActiveView) {
  if (view === 'champion') result.value = null
  else if (view === 'team') teamResult.value = null
  else playerResult.value = null
}

function invalidateQueryResults() {
  querySeq++
  busy.value = false
  currentPage.value = 1
  clearStatisticsResults()
  notice.value = ''
  error.value = ''
}

watch(
  [minimumPickCount, minimumMatchCount, sortBy, teamSortBy, playerSortBy,
    championSortDirection, teamSortDirection, playerSortDirection,
    positionFilter, playerPositionFilter],
  invalidateQueryResults,
  { flush: 'sync' },
)

watch([search, teamSearch, playerSearch], () => {
  currentPage.value = 1
}, { flush: 'sync' })

watch(pageSize, () => {
  currentPage.value = 1
}, { flush: 'sync' })

/* ---- methods ---- */

async function loadSeasons() {
  seasons.value = await api.seasons()
  if (seasons.value.length && !seasons.value.some((item) => item.sourceSeasonId === browsedSeasonId.value)) {
    browsedSeasonId.value = seasons.value[0].sourceSeasonId
  }
}

async function loadAvailability() {
  const seq = ++loadAvailabilitySeq
  const previouslySelected = new Set(selectedStageKeys.value)
  querySeq++
  busy.value = false
  const type = VIEW_STAT_TYPE[activeView.value]
  clearStatisticsResults()
  allAvailability.value = []
  selectedStageKeys.value = new Set()
  notice.value = ''
  error.value = ''
  availabilityLoading.value = true
  try {
    const data = await api.availability(type, false)
    if (seq !== loadAvailabilitySeq) return
    allAvailability.value = data

    /* 保留仍然 collected 的已选复合键 */
    const collectedKeys = new Set(
      data.filter((s) => s.collected).map((s) => makeKey(s.sourceSeasonId, s.sourceStageId)),
    )
    const preserved = new Set([...previouslySelected].filter((k) => collectedKeys.has(k)))

    /* 若交集为空，自动选择默认赛事所有已采集赛段 */
    if (preserved.size === 0) {
      autoSelectDefaults(data, preserved)
    }
    selectedStageKeys.value = preserved

    /* 确保 browsedSeasonId 指向有数据的赛事 */
    if (!data.some((s) => s.sourceSeasonId === browsedSeasonId.value)) {
      const first = data.find((s) => s.collected) ?? data[0]
      if (first) browsedSeasonId.value = first.sourceSeasonId
    }
  } catch (reason) {
    if (seq !== loadAvailabilitySeq) return
    error.value = reason instanceof Error ? reason.message : `加载赛段失败：${String(reason)}`
  } finally {
    if (seq === loadAvailabilitySeq) availabilityLoading.value = false
  }
}

function autoSelectDefaults(data: Stage[], target: Set<string>) {
  const groups = new Map<number, Stage[]>()
  for (const s of data) {
    if (!s.collected) continue
    const arr = groups.get(s.sourceSeasonId) ?? []
    arr.push(s)
    groups.set(s.sourceSeasonId, arr)
  }
  const first = groups.entries().next().value
  if (first) {
    for (const s of first[1]) {
      if (target.size >= MAX_STAGE_SELECTION) break
      target.add(makeKey(s.sourceSeasonId, s.sourceStageId))
    }
    browsedSeasonId.value = first[0]
  }
}

async function query() {
  if (!activeMinimumValid.value) {
    error.value = '最低样本数必须是 0 到 10000 之间的整数'
    return
  }
  if (!canQuery.value) return
  const seq = ++querySeq
  const view = activeView.value
  const keys = [...selectedStageKeys.value]
  const selectedChampionPosition = positionFilter.value
  const selectedPlayerPosition = playerPositionFilter.value
  const selectedMinimumPickCount = minimumPickCount.value
  const selectedMinimumMatchCount = minimumMatchCount.value
  currentPage.value = 1
  clearActiveResult(view)
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    if (view === 'champion') {
      const data = await api.championStatisticsByKeys(
        keys,
        selectedMinimumPickCount,
        selectedChampionPosition,
        sortBy.value,
        championSortDirection.value,
      )
      if (seq === querySeq && activeView.value === view) result.value = data
    } else if (view === 'team') {
      const data = await api.teamStatisticsByKeys(keys, selectedMinimumMatchCount, teamSortBy.value, teamSortDirection.value)
      if (seq === querySeq && activeView.value === view) teamResult.value = data
    } else {
      const data = await api.playerStatisticsByKeys(
        keys,
        selectedMinimumMatchCount,
        selectedPlayerPosition,
        playerSortBy.value,
        playerSortDirection.value,
      )
      if (seq === querySeq && activeView.value === view) playerResult.value = data
    }
    if (seq === querySeq) notice.value = '查询完成'
  } catch (reason) {
    if (seq === querySeq) error.value = reason instanceof Error ? reason.message : String(reason)
  } finally {
    if (seq === querySeq) busy.value = false
  }
}

function changeSort(view: ActiveView, field: string) {
  if (view !== activeView.value || busy.value) return
  if (view === 'champion') {
    if (sortBy.value === field) championSortDirection.value = championSortDirection.value === 'desc' ? 'asc' : 'desc'
    else {
      sortBy.value = field
      championSortDirection.value = 'desc'
    }
  } else if (view === 'team') {
    if (teamSortBy.value === field) teamSortDirection.value = teamSortDirection.value === 'desc' ? 'asc' : 'desc'
    else {
      teamSortBy.value = field
      teamSortDirection.value = 'desc'
    }
  } else {
    if (playerSortBy.value === field) playerSortDirection.value = playerSortDirection.value === 'desc' ? 'asc' : 'desc'
    else {
      playerSortBy.value = field
      playerSortDirection.value = 'desc'
    }
  }
  void query()
}

function switchView(view: ActiveView) {
  if (activeView.value === view) return
  activeView.value = view
  currentPage.value = 1
  clearStatisticsResults()
  notice.value = ''
  error.value = ''
  void loadAvailability()
}

function toggleStage(compositeKey: string, collected: boolean) {
  if (!collected) return
  const newSet = new Set(selectedStageKeys.value)
  if (newSet.has(compositeKey)) {
    newSet.delete(compositeKey)
  } else {
    if (newSet.size >= MAX_STAGE_SELECTION) {
      error.value = `最多选择 ${MAX_STAGE_SELECTION} 个赛段，请先移除部分赛段后再添加`
      return
    }
    newSet.add(compositeKey)
  }
  selectedStageKeys.value = newSet
  invalidateQueryResults()
}

function removeStage(compositeKey: string) {
  const newSet = new Set(selectedStageKeys.value)
  newSet.delete(compositeKey)
  selectedStageKeys.value = newSet
  invalidateQueryResults()
}

function clearSelectedStages() {
  if (selectedStageKeys.value.size === 0) return
  selectedStageKeys.value = new Set()
  invalidateQueryResults()
}

function percent(value: number) {
  return `${(value * 100).toFixed(2)}%`
}

function fmtDecimal(value: number, digits = 2) {
  return value.toFixed(digits)
}

function fmtGold(value: number) {
  if (value < 0) return '-' + ((-value) / 1000).toFixed(1) + 'k'
  return (value / 1000).toFixed(1) + 'k'
}

function fmtPositions(positions: string[]) {
  return positions.join(' / ') || '—'
}

function fmtTeamNames(teamNames: string[]) {
  return teamNames.join(' / ') || '—'
}

onMounted(async () => {
  try {
    await loadSeasons()
    if (seasons.value.length > 0 && browsedSeasonId.value === 0) {
      browsedSeasonId.value = seasons.value[0].sourceSeasonId
    }
    await loadAvailability()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : String(reason)
  }
})
</script>

<template>
  <main class="shell">
    <header class="hero">
      <div>
        <p class="eyebrow">LOL DATA HUB</p>
        <h1>赛事数据，<span class="title-tail">不止看一个赛段</span></h1>
        <p class="hero-copy">基于本地持久化数据重新计算跨赛事指标，并用明确的样本门槛隔离低样本噪声。支持跨赛事赛段选择，如 LPL + MSI（需已有采集数据）。</p>
      </div>
      <div class="status-card">
        <span>数据版本</span>
        <strong>{{ currentDataVersion ?? '—' }}</strong>
        <small>{{ latestUpdatedAt ? `更新于 ${new Date(latestUpdatedAt).toLocaleString()}` : '尚未查询数据' }}</small>
      </div>
    </header>

    <nav class="view-tabs">
      <button
        class="tab-btn"
        :class="{ active: activeView === 'champion' }"
        @click="switchView('champion')"
      >
        英雄统计
      </button>
      <button
        class="tab-btn"
        :class="{ active: activeView === 'team' }"
        @click="switchView('team')"
      >
        战队统计
      </button>
      <button
        class="tab-btn"
        :class="{ active: activeView === 'player' }"
        @click="switchView('player')"
      >
        选手统计
      </button>
    </nav>

    <section class="panel controls">
      <div class="field">
        <label for="season">赛事/赛季（浏览）</label>
        <select id="season" v-model.number="browsedSeasonId">
          <option v-if="!seasons.length" :value="browsedSeasonId">赛事 #{{ browsedSeasonId }}</option>
          <option v-for="season in sortedSeasons" :key="season.sourceSeasonId" :value="season.sourceSeasonId">
            {{ season.name }} · #{{ season.sourceSeasonId }}
          </option>
        </select>
      </div>
      <div v-if="activeView === 'champion'" class="field compact">
        <label for="minimum">最低出场次数</label>
        <input id="minimum" v-model.number="minimumPickCount" type="number" min="0" max="10000" step="1" />
        <small v-if="!minimumPickCountValid" class="field-error">请输入 0 到 10000 之间的整数</small>
      </div>
      <div v-else class="field compact">
        <label for="minimumMatch">最低比赛场数</label>
        <input id="minimumMatch" v-model.number="minimumMatchCount" type="number" min="0" max="10000" step="1" />
        <small v-if="!minimumMatchCountValid" class="field-error">请输入 0 到 10000 之间的整数</small>
      </div>
      <div class="actions">
        <button class="primary" :disabled="!canQuery" @click="query">{{ busy ? '处理中…' : '查询统计' }}</button>
      </div>

      <!-- 赛段浏览器 -->
      <div class="stage-block">
        <div class="stage-heading">
          <span>选择赛段（支持跨赛事选择，如 LPL + MSI）</span>
          <small>当前浏览：{{ browsedSeasonName }}</small>
        </div>
        <div v-if="availabilityLoading" class="empty-inline">正在加载赛段…</div>
        <div v-else-if="browsedStages.length" class="stage-list">
          <button
            v-for="stage in browsedStages"
            :key="makeKey(stage.sourceSeasonId, stage.sourceStageId)"
            class="stage-chip"
            :class="{ selected: selectedStageKeys.has(makeKey(stage.sourceSeasonId, stage.sourceStageId)), disabled: !stage.collected }"
            :disabled="!stage.collected"
            @click="toggleStage(makeKey(stage.sourceSeasonId, stage.sourceStageId), stage.collected)"
          >
            <span>{{ stage.name }}</span>
            <small v-if="!stage.collected" class="uncollected-tag">未采集</small>
            <small v-else-if="stage.sampleBaseCount != null">{{ stage.sampleBaseCount }} 场</small>
          </button>
        </div>
        <p v-else class="empty-inline">
          {{ activeView === 'team' ? '该赛季暂无已采集战队数据。' : activeView === 'player' ? '该赛季暂无已采集选手数据。' : '该赛季暂无赛段数据。' }}
        </p>

        <!-- 跨赛事选择篮 -->
        <div class="basket-section">
          <div class="basket-heading">
            <span>已选跨赛事赛段</span>
            <div v-if="selectedStageKeys.size > 0" class="basket-heading-actions">
              <small>
                {{ selectedSeasonCount }} 个赛事 · {{ selectedStageKeys.size }} 个赛段
                <template v-if="activeView === 'champion'"> · 样本合计 {{ totalSampleBase }}</template>
              </small>
              <button
                type="button"
                class="basket-clear"
                aria-label="取消所有已选赛段"
                @click="clearSelectedStages"
              >清空全部</button>
            </div>
          </div>
          <div v-if="selectedStageKeys.size === 0" class="empty-inline">
            请在上方赛段列表中勾选要查询的赛段，支持跨赛事选择（如 LPL 赛段 + MSI 赛段，需已有采集数据）。
          </div>
          <div v-else class="basket-list">
            <div
              v-for="stage in selectedStageDetails"
              :key="makeKey(stage.sourceSeasonId, stage.sourceStageId)"
              class="basket-item"
            >
              <span class="basket-season">{{ stage.seasonName ?? `赛事#${stage.sourceSeasonId}` }}</span>
              <span class="basket-stage">{{ stage.name }}</span>
              <small v-if="stage.sampleBaseCount != null" class="basket-sample">{{ stage.sampleBaseCount }} 场</small>
              <button
                class="basket-remove"
                :aria-label="`移除 ${stage.seasonName ?? ''} ${stage.name}`"
                @click="removeStage(makeKey(stage.sourceSeasonId, stage.sourceStageId))"
              >&times;</button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section v-if="selectedStageKeys.size > 0" class="query-summary">
      <span>已选 <strong>{{ selectedSeasonCount }}</strong> 个赛事 · <strong>{{ selectedStageKeys.size }}</strong> 个赛段</span>
      <span v-if="activeView === 'champion'">样本基数合计 <strong>{{ totalSampleBase }}</strong></span>
      <span>数据版本 <strong>{{ currentDataVersion ?? '—' }}</strong></span>
      <span v-if="latestUpdatedAt">最近更新 <strong>{{ new Date(latestUpdatedAt).toLocaleString() }}</strong></span>
    </section>

    <p v-if="error" class="message error">{{ error }}</p>
    <p v-else-if="notice" class="message success">{{ notice }}</p>

    <!-- 英雄统计面板 -->
    <section v-if="activeView === 'champion'" class="panel table-panel">
      <div class="table-toolbar">
        <div>
          <p class="eyebrow">CHAMPION STATISTICS</p>
          <h2>英雄统计</h2>
        </div>
        <div class="toolbar-right">
          <div class="toolbar-options-row">
            <div class="position-filter">
              <button
                v-for="opt in CHAMPION_POSITION_OPTIONS"
                :key="opt.value"
                class="pos-chip"
                :class="{ active: positionFilter === opt.value }"
                :aria-pressed="positionFilter === opt.value"
                @click="positionFilter = opt.value"
              >
                {{ opt.label }}
              </button>
            </div>
            <ColumnVisibilityMenu v-model="championVisibleColumns" :columns="CHAMPION_COLUMNS" />
          </div>
          <div class="search-wrap">
            <input v-model="search" type="search" placeholder="搜索英雄、称号" />
            <span>{{ filteredChampionItems.length }} 项</span>
          </div>
          </div>
        </div>
        <p v-if="positionFilter" class="position-note">
          出场、胜负与 KDA 按实际分路独立统计；英雄被禁用时没有实际分路，禁用指标按所选赛段整体计算，BP 率为该分路出场率与整体禁用率之和。
        </p>

      <div v-if="filteredChampionItems.length" class="table-scroll" tabindex="0" aria-label="英雄统计表，可横向和纵向滚动">
        <table class="champion-table">
          <thead>
            <tr>
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'champion')" label="英雄" field="championName" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'positions')" label="分路" field="positions" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'pickCount')" label="出场" field="pickCount" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'pickRate')" label="出场率" field="pickRate" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'banCount')" label="禁用" field="banCount" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'banRate')" label="禁用率" field="banRate" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'bpRate')" label="BP 率" field="bpRate" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'winningCount')" label="胜场" field="winningCount" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'winningRate')" label="胜率" field="winningRate" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'totalKills')" label="总击杀" field="totalKills" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'killPerGame')" label="场均击杀" field="killPerGame" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'totalAssists')" label="总助攻" field="totalAssists" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'assistPerGame')" label="场均助攻" field="assistPerGame" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'totalDeaths')" label="总死亡" field="totalDeaths" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'deathPerGame')" label="场均死亡" field="deathPerGame" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'kda')" label="KDA" field="kda" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'mostUsedPlayers')" label="常用选手" field="mostUsedPlayers" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in paginatedChampionItems" :key="item.championId">
              <td v-if="isColumnVisible(championVisibleColumns, 'champion')">
                <div class="champion-cell">
                  <img v-if="item.championLogo" :src="item.championLogo" :alt="item.championName" />
                  <span class="champion-placeholder" v-else>{{ item.championName.slice(0, 1) }}</span>
                  <div><strong>{{ item.championName }}</strong><small>{{ item.championTitle }}</small></div>
                </div>
              </td>
              <td v-if="isColumnVisible(championVisibleColumns, 'positions')">{{ item.positions.join(' / ') || '—' }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'pickCount')">{{ item.pickCount }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'pickRate')">{{ percent(item.pickRate) }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'banCount')">{{ item.banCount }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'banRate')">{{ percent(item.banRate) }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'bpRate')" class="accent">{{ percent(item.bpRate) }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'winningCount')">{{ item.winningCount }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'winningRate')" class="accent">{{ percent(item.winningRate) }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'totalKills')">{{ item.totalKills }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'killPerGame')">{{ fmtDecimal(item.killPerGame) }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'totalAssists')">{{ item.totalAssists }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'assistPerGame')">{{ fmtDecimal(item.assistPerGame) }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'totalDeaths')">{{ item.totalDeaths }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'deathPerGame')">{{ fmtDecimal(item.deathPerGame) }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'kda')">{{ fmtDecimal(item.kda) }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'mostUsedPlayers')">{{ item.mostUsedPlayers.join('、') || '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state">
        <strong v-if="selectedStageKeys.size === 0">选择赛段后点击查询</strong>
        <strong v-else-if="!result">选择赛段后点击查询</strong>
        <strong v-else>无匹配结果</strong>
      </div>
      <PaginationControls
        v-if="filteredChampionItems.length"
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total-items="filteredChampionItems.length"
      />
    </section>

    <!-- 战队统计面板 -->
    <section v-if="activeView === 'team'" class="panel table-panel">
      <div class="table-toolbar">
        <div>
          <p class="eyebrow">TEAM STATISTICS</p>
          <h2>战队统计</h2>
        </div>
        <div class="toolbar-right">
          <ColumnVisibilityMenu v-model="teamVisibleColumns" :columns="TEAM_COLUMNS" />
          <div class="search-wrap">
            <input v-model="teamSearch" type="search" placeholder="搜索战队" />
            <span>{{ filteredTeamItems.length }} 项</span>
          </div>
        </div>
      </div>

      <div v-if="filteredTeamItems.length" class="table-scroll" tabindex="0" aria-label="战队统计表，可横向和纵向滚动">
        <table class="team-table">
          <thead>
            <tr>
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'team')" label="战队" field="teamName" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'matchCount')" label="系列赛" field="matchCount" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'gameCount')" label="对局" field="gameCount" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'matchWinCount')" label="胜场" field="matchWinCount" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'winningRate')" label="胜率" field="winningRate" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'totalKills')" label="总击杀" field="totalKills" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'killPerGame')" label="场均击杀" field="killPerGame" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'deathPerGame')" label="场均死亡" field="deathPerGame" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'wardPlacedPerGame')" label="场均插眼" field="wardPlacedPerGame" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'wardKilledPerGame')" label="场均排眼" field="wardKilledPerGame" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'goldPerGame')" label="场均经济" field="goldPerGame" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'baronKillPerGame')" label="场均大龙" field="baronKillPerGame" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'drakeKillPerGame')" label="场均小龙" field="drakeKillPerGame" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in paginatedTeamItems" :key="item.teamId">
              <td v-if="isColumnVisible(teamVisibleColumns, 'team')">
                <div class="team-cell">
                  <img v-if="item.teamLogo" :src="item.teamLogo" :alt="item.teamName" class="team-logo" />
                  <span class="team-placeholder" v-else>{{ item.teamName.slice(0, 1) }}</span>
                  <strong>{{ item.teamName }}</strong>
                </div>
              </td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'matchCount')">{{ item.matchCount }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'gameCount')">{{ item.gameCount }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'matchWinCount')">{{ item.matchWinCount }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'winningRate')" class="accent">{{ percent(item.winningRate) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'totalKills')">{{ item.totalKills }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'killPerGame')">{{ fmtDecimal(item.killPerGame) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'deathPerGame')">{{ fmtDecimal(item.deathPerGame) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'wardPlacedPerGame')">{{ fmtDecimal(item.wardPlacedPerGame) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'wardKilledPerGame')">{{ fmtDecimal(item.wardKilledPerGame) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'goldPerGame')">{{ fmtGold(item.goldPerGame) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'baronKillPerGame')">{{ fmtDecimal(item.baronKillPerGame) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'drakeKillPerGame')">{{ fmtDecimal(item.drakeKillPerGame) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state">
        <strong v-if="selectedStageKeys.size === 0">选择赛段后点击查询</strong>
        <strong v-else-if="!teamResult">选择赛段后点击查询</strong>
        <strong v-else>无匹配结果</strong>
      </div>
      <PaginationControls
        v-if="filteredTeamItems.length"
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total-items="filteredTeamItems.length"
      />
    </section>

    <!-- 选手统计面板 -->
    <section v-if="activeView === 'player'" class="panel table-panel">
      <div class="table-toolbar">
        <div>
          <p class="eyebrow">PLAYER STATISTICS</p>
          <h2>选手统计</h2>
        </div>
        <div class="toolbar-right">
          <div class="toolbar-options-row">
            <div class="position-filter">
              <button
                v-for="opt in PLAYER_POSITION_OPTIONS"
                :key="opt.value"
                class="pos-chip"
                :class="{ active: playerPositionFilter === opt.value }"
                @click="playerPositionFilter = opt.value"
              >
                {{ opt.label }}
              </button>
            </div>
            <ColumnVisibilityMenu v-model="playerVisibleColumns" :columns="PLAYER_COLUMNS" />
          </div>
          <div class="search-wrap">
            <input v-model="playerSearch" type="search" placeholder="搜索选手、战队" />
            <span>{{ filteredPlayerItems.length }} 项</span>
          </div>
        </div>
      </div>

      <div v-if="filteredPlayerItems.length" class="table-scroll" tabindex="0" aria-label="选手统计表，可横向和纵向滚动">
        <table class="player-table">
          <thead>
            <tr>
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'player')" label="选手" field="playerName" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'positions')" label="位置" field="positions" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'matchCount')" label="系列赛" field="matchCount" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'gameCount')" label="对局" field="gameCount" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'mvpCount')" label="MVP" field="mvpCount" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'mvpVotes')" label="MVP 票数" field="mvpVotes" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'kda')" label="KDA" field="kda" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'totalKills')" label="总击杀" field="totalKills" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'killPerGame')" label="场均击杀" field="killPerGame" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'totalAssists')" label="总助攻" field="totalAssists" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'assistPerGame')" label="场均助攻" field="assistPerGame" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'totalDeaths')" label="总死亡" field="totalDeaths" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'deathPerGame')" label="场均死亡" field="deathPerGame" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'goldPerGame')" label="场均经济" field="goldPerGame" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'creepScorePerGame')" label="场均补刀" field="creepScorePerGame" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'killParticipantPercent')" label="参团率" field="killParticipantPercent" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'goldGapPerGame')" label="场均经济差" field="goldGapPerGame" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'damagePercent')" label="伤害占比" field="damagePercent" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'goldPercent')" label="经济占比" field="goldPercent" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in paginatedPlayerItems" :key="item.playerKey">
              <td v-if="isColumnVisible(playerVisibleColumns, 'player')">
                <div class="player-cell">
                  <img v-if="item.playerAvatar" :src="item.playerAvatar" :alt="item.playerName" class="player-avatar" />
                  <span class="player-placeholder" v-else>{{ item.playerName.slice(0, 1) }}</span>
                  <div>
                    <strong>{{ item.playerName }}</strong>
                    <small>{{ fmtTeamNames(item.teamNames) }}</small>
                  </div>
                </div>
              </td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'positions')">{{ fmtPositions(item.positions) }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'matchCount')">{{ item.matchCount }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'gameCount')">{{ item.gameCount }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'mvpCount')">{{ item.mvpCount }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'mvpVotes')">{{ item.mvpVotes }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'kda')" class="accent">{{ fmtDecimal(item.kda) }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'totalKills')">{{ item.totalKills }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'killPerGame')">{{ fmtDecimal(item.killPerGame) }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'totalAssists')">{{ item.totalAssists }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'assistPerGame')">{{ fmtDecimal(item.assistPerGame) }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'totalDeaths')">{{ item.totalDeaths }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'deathPerGame')">{{ fmtDecimal(item.deathPerGame) }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'goldPerGame')">{{ fmtGold(item.goldPerGame) }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'creepScorePerGame')">{{ fmtDecimal(item.creepScorePerGame) }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'killParticipantPercent')" class="accent">{{ percent(item.killParticipantPercent) }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'goldGapPerGame')">{{ fmtGold(item.goldGapPerGame) }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'damagePercent')" class="accent">{{ percent(item.damagePercent) }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'goldPercent')">{{ percent(item.goldPercent) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state">
        <strong v-if="selectedStageKeys.size === 0">选择赛段后点击查询</strong>
        <strong v-else-if="!playerResult">选择赛段后点击查询</strong>
        <strong v-else>无匹配结果</strong>
      </div>
      <PaginationControls
        v-if="filteredPlayerItems.length"
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total-items="filteredPlayerItems.length"
      />
    </section>
  </main>
</template>
