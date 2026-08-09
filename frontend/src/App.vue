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

type ActiveView = 'champion' | 'team' | 'player'

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

const CHAMPION_SORT_OPTIONS = [
  { value: 'bpRate', label: 'BP 率' },
  { value: 'winningRate', label: '胜率' },
  { value: 'pickCount', label: '出场次数' },
  { value: 'pickRate', label: '出场率' },
  { value: 'banRate', label: '禁用率' },
]

const TEAM_SORT_OPTIONS = [
  { value: 'winningRate', label: '胜率' },
  { value: 'totalKills', label: '总击杀' },
  { value: 'killPerGame', label: '场均击杀' },
  { value: 'matchCount', label: '比赛场数' },
  { value: 'baronKillPerGame', label: '场均大龙' },
]

const PLAYER_SORT_OPTIONS = [
  { value: 'kda', label: 'KDA' },
  { value: 'totalKills', label: '总击杀' },
  { value: 'mvpCount', label: 'MVP' },
  { value: 'killPerGame', label: '场均击杀' },
  { value: 'goldPerGame', label: '场均经济' },
  { value: 'damagePercent', label: '伤害占比' },
  { value: 'matchCount', label: '比赛场数' },
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
const sortDirection = ref('desc')
const positionFilter = ref('')
const playerPositionFilter = ref('')
const search = ref('')
const teamSearch = ref('')
const playerSearch = ref('')
const result = ref<ChampionStatisticsResult | null>(null)
const teamResult = ref<TeamStatisticsResult | null>(null)
const playerResult = ref<PlayerStatisticsResult | null>(null)
const busy = ref(false)
const availabilityLoading = ref(false)
const notice = ref('')
const error = ref('')
let loadAvailabilitySeq = 0
let querySeq = 0

/* ---- computed ---- */

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
  clearStatisticsResults()
  notice.value = ''
  error.value = ''
}

watch(
  [minimumPickCount, minimumMatchCount, sortBy, teamSortBy, playerSortBy, sortDirection,
    positionFilter, playerPositionFilter],
  invalidateQueryResults,
  { flush: 'sync' },
)

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
        sortDirection.value,
      )
      if (seq === querySeq && activeView.value === view) result.value = data
    } else if (view === 'team') {
      const data = await api.teamStatisticsByKeys(keys, selectedMinimumMatchCount, teamSortBy.value, sortDirection.value)
      if (seq === querySeq && activeView.value === view) teamResult.value = data
    } else {
      const data = await api.playerStatisticsByKeys(
        keys,
        selectedMinimumMatchCount,
        selectedPlayerPosition,
        playerSortBy.value,
        sortDirection.value,
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

function switchView(view: ActiveView) {
  if (activeView.value === view) return
  activeView.value = view
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
          <option v-for="season in seasons" :key="season.sourceSeasonId" :value="season.sourceSeasonId">
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
      <div v-if="activeView === 'champion'" class="field compact">
        <label for="sort">排序指标</label>
        <select id="sort" v-model="sortBy">
          <option v-for="opt in CHAMPION_SORT_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </select>
      </div>
      <div v-else-if="activeView === 'team'" class="field compact">
        <label for="teamSort">排序指标</label>
        <select id="teamSort" v-model="teamSortBy">
          <option v-for="opt in TEAM_SORT_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </select>
      </div>
      <div v-else class="field compact">
        <label for="playerSort">排序指标</label>
        <select id="playerSort" v-model="playerSortBy">
          <option v-for="opt in PLAYER_SORT_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </select>
      </div>
      <div class="field compact">
        <label for="direction">排序方向</label>
        <select id="direction" v-model="sortDirection">
          <option value="desc">降序</option>
          <option value="asc">升序</option>
        </select>
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
          <div class="search-wrap">
            <input v-model="search" type="search" placeholder="搜索英雄、称号" />
            <span>{{ filteredChampionItems.length }} 项</span>
          </div>
          </div>
        </div>
        <p v-if="positionFilter" class="position-note">
          出场、胜负与 KDA 按实际分路独立统计；英雄被禁用时没有实际分路，禁用指标按所选赛段整体计算，BP 率为该分路出场率与整体禁用率之和。
        </p>

      <div v-if="filteredChampionItems.length" class="table-scroll">
        <table class="champion-table">
          <thead>
            <tr>
              <th>英雄</th>
              <th>分路</th>
              <th>出场</th>
              <th>出场率</th>
              <th>禁用</th>
              <th>禁用率</th>
              <th>BP 率</th>
              <th>胜场</th>
              <th>胜率</th>
              <th>总击杀</th>
              <th>场均击杀</th>
              <th>总助攻</th>
              <th>场均助攻</th>
              <th>总死亡</th>
              <th>场均死亡</th>
              <th>KDA</th>
              <th>常用选手</th>
              <th>样本基数</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredChampionItems" :key="item.championId">
              <td>
                <div class="champion-cell">
                  <img v-if="item.championLogo" :src="item.championLogo" :alt="item.championName" />
                  <span class="champion-placeholder" v-else>{{ item.championName.slice(0, 1) }}</span>
                  <div><strong>{{ item.championName }}</strong><small>{{ item.championTitle }}</small></div>
                </div>
              </td>
              <td>{{ item.positions.join(' / ') || '—' }}</td>
              <td>{{ item.pickCount }}</td>
              <td>{{ percent(item.pickRate) }}</td>
              <td>{{ item.banCount }}</td>
              <td>{{ percent(item.banRate) }}</td>
              <td class="accent">{{ percent(item.bpRate) }}</td>
              <td>{{ item.winningCount }}</td>
              <td class="accent">{{ percent(item.winningRate) }}</td>
              <td>{{ item.totalKills }}</td>
              <td>{{ fmtDecimal(item.killPerGame) }}</td>
              <td>{{ item.totalAssists }}</td>
              <td>{{ fmtDecimal(item.assistPerGame) }}</td>
              <td>{{ item.totalDeaths }}</td>
              <td>{{ fmtDecimal(item.deathPerGame) }}</td>
              <td>{{ fmtDecimal(item.kda) }}</td>
              <td>{{ item.mostUsedPlayers.join('、') || '—' }}</td>
              <td>{{ item.sampleBaseCount }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state">
        <strong v-if="selectedStageKeys.size === 0">选择赛段后点击查询</strong>
        <strong v-else-if="!result">选择赛段后点击查询</strong>
        <strong v-else>无匹配结果</strong>
      </div>
    </section>

    <!-- 战队统计面板 -->
    <section v-if="activeView === 'team'" class="panel table-panel">
      <div class="table-toolbar">
        <div>
          <p class="eyebrow">TEAM STATISTICS</p>
          <h2>战队统计</h2>
        </div>
        <div class="toolbar-right">
          <div class="search-wrap">
            <input v-model="teamSearch" type="search" placeholder="搜索战队" />
            <span>{{ filteredTeamItems.length }} 项</span>
          </div>
        </div>
      </div>

      <div v-if="filteredTeamItems.length" class="table-scroll">
        <table class="team-table">
          <thead>
            <tr>
              <th>战队</th>
              <th>系列赛</th>
              <th>对局</th>
              <th>胜场</th>
              <th>胜率</th>
              <th>总击杀</th>
              <th>场均击杀</th>
              <th>场均死亡</th>
              <th>场均插眼</th>
              <th>场均排眼</th>
              <th>场均经济</th>
              <th>场均大龙</th>
              <th>场均小龙</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredTeamItems" :key="item.teamId">
              <td>
                <div class="team-cell">
                  <img v-if="item.teamLogo" :src="item.teamLogo" :alt="item.teamName" class="team-logo" />
                  <span class="team-placeholder" v-else>{{ item.teamName.slice(0, 1) }}</span>
                  <strong>{{ item.teamName }}</strong>
                </div>
              </td>
              <td>{{ item.matchCount }}</td>
              <td>{{ item.gameCount }}</td>
              <td>{{ item.matchWinCount }}</td>
              <td class="accent">{{ percent(item.winningRate) }}</td>
              <td>{{ item.totalKills }}</td>
              <td>{{ fmtDecimal(item.killPerGame) }}</td>
              <td>{{ fmtDecimal(item.deathPerGame) }}</td>
              <td>{{ fmtDecimal(item.wardPlacedPerGame) }}</td>
              <td>{{ fmtDecimal(item.wardKilledPerGame) }}</td>
              <td>{{ fmtGold(item.goldPerGame) }}</td>
              <td>{{ fmtDecimal(item.baronKillPerGame) }}</td>
              <td>{{ fmtDecimal(item.drakeKillPerGame) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state">
        <strong v-if="selectedStageKeys.size === 0">选择赛段后点击查询</strong>
        <strong v-else-if="!teamResult">选择赛段后点击查询</strong>
        <strong v-else>无匹配结果</strong>
      </div>
    </section>

    <!-- 选手统计面板 -->
    <section v-if="activeView === 'player'" class="panel table-panel">
      <div class="table-toolbar">
        <div>
          <p class="eyebrow">PLAYER STATISTICS</p>
          <h2>选手统计</h2>
        </div>
        <div class="toolbar-right">
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
          <div class="search-wrap">
            <input v-model="playerSearch" type="search" placeholder="搜索选手、战队" />
            <span>{{ filteredPlayerItems.length }} 项</span>
          </div>
        </div>
      </div>

      <div v-if="filteredPlayerItems.length" class="table-scroll">
        <table class="player-table">
          <thead>
            <tr>
              <th>选手</th>
              <th>位置</th>
              <th>系列赛</th>
              <th>对局</th>
              <th>MVP</th>
              <th>MVP 票数</th>
              <th>KDA</th>
              <th>总击杀</th>
              <th>场均击杀</th>
              <th>总助攻</th>
              <th>场均助攻</th>
              <th>总死亡</th>
              <th>场均死亡</th>
              <th>场均经济</th>
              <th>场均补刀</th>
              <th>参团率</th>
              <th>场均经济差</th>
              <th>伤害占比</th>
              <th>经济占比</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredPlayerItems" :key="item.playerKey">
              <td>
                <div class="player-cell">
                  <img v-if="item.playerAvatar" :src="item.playerAvatar" :alt="item.playerName" class="player-avatar" />
                  <span class="player-placeholder" v-else>{{ item.playerName.slice(0, 1) }}</span>
                  <div>
                    <strong>{{ item.playerName }}</strong>
                    <small>{{ fmtTeamNames(item.teamNames) }}</small>
                  </div>
                </div>
              </td>
              <td>{{ fmtPositions(item.positions) }}</td>
              <td>{{ item.matchCount }}</td>
              <td>{{ item.gameCount }}</td>
              <td>{{ item.mvpCount }}</td>
              <td>{{ item.mvpVotes }}</td>
              <td class="accent">{{ fmtDecimal(item.kda) }}</td>
              <td>{{ item.totalKills }}</td>
              <td>{{ fmtDecimal(item.killPerGame) }}</td>
              <td>{{ item.totalAssists }}</td>
              <td>{{ fmtDecimal(item.assistPerGame) }}</td>
              <td>{{ item.totalDeaths }}</td>
              <td>{{ fmtDecimal(item.deathPerGame) }}</td>
              <td>{{ fmtGold(item.goldPerGame) }}</td>
              <td>{{ fmtDecimal(item.creepScorePerGame) }}</td>
              <td class="accent">{{ percent(item.killParticipantPercent) }}</td>
              <td>{{ fmtGold(item.goldGapPerGame) }}</td>
              <td class="accent">{{ percent(item.damagePercent) }}</td>
              <td>{{ percent(item.goldPercent) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state">
        <strong v-if="selectedStageKeys.size === 0">选择赛段后点击查询</strong>
        <strong v-else-if="!playerResult">选择赛段后点击查询</strong>
        <strong v-else>无匹配结果</strong>
      </div>
    </section>
  </main>
</template>
