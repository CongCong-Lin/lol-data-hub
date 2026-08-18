<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api, type PlayerRadarMetric, type PlayerStatistics } from './api'
import PlayerRadarChart, { type RadarAxisBox } from './PlayerRadarChart.vue'
import { formatRadarMetricValue } from './formatters'
import { useI18n } from './i18n'

const props = defineProps<{
  stageKeys: string[]
  positionFilter: string
  minimumMatchCount: number
  /** 实时过滤关键字（与 URL compareKeyword 双向同步） */
  searchKeyword: string
  /** 已选选手 sourcePlayerId（与 URL comparePlayers 双向同步） */
  selectedPlayerIds: number[]
}>()

const emit = defineEmits<{
  'update:positionFilter': [value: string]
  'update:minimumMatchCount': [value: number]
  'update:searchKeyword': [value: string]
  'update:selectedPlayerIds': [value: number[]]
}>()

const { t } = useI18n()

const MAX_SELECTED_PLAYERS = 5
const MAX_VISIBLE_PLAYERS = 100

const players = ref<PlayerStatistics[]>([])
const listLoading = ref(false)
const listError = ref('')
const compareError = ref('')
const selectedPlayers = ref<PlayerStatistics[]>([])
/** URL 恢复的选手 id：列表首次加载完成后按 id 重建选择（只生效一次） */
let restoredIds: number[] | null = props.selectedPlayerIds.length
  ? [...new Set(props.selectedPlayerIds)].slice(0, MAX_SELECTED_PLAYERS)
  : null
/** 上次上抛给父组件的 id 序列，用于区分“自己的回写”与外部变更 */
let lastEmittedIds = ''
let listSeq = 0

interface CompareMetric {
  key: keyof PlayerStatistics
  label: string
  format: (value: number | null) => string
  higherIsBetter: boolean
}

function fmtPercent(value: number | null): string {
  return value == null ? '-' : `${(value * 100).toFixed(1)}%`
}

function fmtDecimal(value: number | null): string {
  return value == null ? '-' : Number(value).toFixed(2)
}

function fmtGold(value: number | null): string {
  if (value == null) return '-'
  if (value < 0) return '-' + ((-value) / 1000).toFixed(1) + 'k'
  return (value / 1000).toFixed(1) + 'k'
}

const COMPARE_METRICS: CompareMetric[] = [
  { key: 'kda', label: 'KDA', format: fmtDecimal, higherIsBetter: true },
  { key: 'killPerGame', label: '场均击杀', format: fmtDecimal, higherIsBetter: true },
  { key: 'deathPerGame', label: '场均死亡', format: fmtDecimal, higherIsBetter: false },
  { key: 'assistPerGame', label: '场均助攻', format: fmtDecimal, higherIsBetter: true },
  { key: 'goldPerGame', label: '场均经济', format: fmtGold, higherIsBetter: true },
  { key: 'creepScorePerGame', label: '场均补刀', format: fmtDecimal, higherIsBetter: true },
  { key: 'killParticipantPercent', label: '参团率', format: fmtPercent, higherIsBetter: true },
  { key: 'damagePerGame', label: '场均伤害', format: fmtDecimal, higherIsBetter: true },
  { key: 'damagePercent', label: '伤害占比', format: fmtPercent, higherIsBetter: true },
  { key: 'goldPercent', label: '经济占比', format: fmtPercent, higherIsBetter: true },
  { key: 'mvpCount', label: 'MVP', format: (v) => (v == null ? '-' : String(Math.round(v))), higherIsBetter: true },
  { key: 'mvpVotes', label: 'MVP 票数', format: (v) => (v == null ? '-' : String(Math.round(v))), higherIsBetter: true },
]

function bestOf(metric: CompareMetric, players: PlayerStatistics[]): number | null {
  const values = players
    .map((player) => Number(player[metric.key]))
    .filter((value) => Number.isFinite(value))
  if (!values.length) return null
  return metric.higherIsBetter ? Math.max(...values) : Math.min(...values)
}

function isBest(metric: CompareMetric, player: PlayerStatistics): boolean {
  const value = Number(player[metric.key])
  if (!Number.isFinite(value)) return false
  return value === bestOf(metric, selectedPlayers.value)
}

function metricValue(metric: CompareMetric, player: PlayerStatistics): string {
  const value = player[metric.key] as number | null
  return metric.format(value)
}

/* ---- 选手列表：随赛段/位置/最低场数自动拉取，关键字实时过滤，点击行选择 ---- */

const searchModel = computed({
  get: () => props.searchKeyword,
  set: (value: string) => emit('update:searchKeyword', value),
})

async function fetchPlayerList() {
  const keys = props.stageKeys
  const seq = ++listSeq
  if (!keys.length) {
    /* 无赛段：清空列表即可；已选选手与待恢复的 id 保留（App 切换视图时会瞬时清空赛段再恢复） */
    players.value = []
    listLoading.value = false
    listError.value = ''
    return
  }
  listLoading.value = true
  listError.value = ''
  try {
    const data = await api.playerStatisticsByKeys(keys, props.minimumMatchCount, props.positionFilter, 'kda', 'desc')
    if (seq !== listSeq) return
    players.value = data.items
    if (restoredIds) {
      /* 从 URL 恢复的选择：仅在列表内重建，不在范围内的忽略 */
      const byId = new Map<number, PlayerStatistics>()
      for (const item of data.items) if (item.sourcePlayerId != null) byId.set(item.sourcePlayerId, item)
      const rebuilt: PlayerStatistics[] = []
      for (const id of restoredIds) {
        const item = byId.get(id)
        if (item && !rebuilt.some((p) => p.playerKey === item.playerKey)) rebuilt.push(item)
      }
      const missing = restoredIds.length - rebuilt.length
      restoredIds = null
      selectedPlayers.value = rebuilt
      emitSelection()
      if (missing > 0) {
        compareError.value = `恢复对比时 ${missing} 名选手不在当前筛选范围内，已忽略`
      }
    } else {
      /* 列表刷新后同步已选选手的统计数据 */
      const byKey = new Map(data.items.map((item) => [item.playerKey, item]))
      selectedPlayers.value = selectedPlayers.value.map((p) => byKey.get(p.playerKey) ?? p)
    }
  } catch (reason) {
    if (seq === listSeq) listError.value = reason instanceof Error ? reason.message : String(reason)
  } finally {
    if (seq === listSeq) listLoading.value = false
  }
}

watch(
  [() => props.stageKeys.join(','), () => props.positionFilter, () => props.minimumMatchCount],
  () => {
    void fetchPlayerList()
  },
  { immediate: true },
)

/** 外部（URL 恢复等）变更选择时按当前列表重建；自己 emit 的回写直接忽略。 */
watch(
  () => props.selectedPlayerIds,
  (ids) => {
    if (ids.join(',') === lastEmittedIds) return
    const byId = new Map<number, PlayerStatistics>()
    for (const item of players.value) if (item.sourcePlayerId != null) byId.set(item.sourcePlayerId, item)
    const rebuilt: PlayerStatistics[] = []
    for (const id of ids) {
      const item = byId.get(id)
      if (item && !rebuilt.some((p) => p.playerKey === item.playerKey)) rebuilt.push(item)
    }
    selectedPlayers.value = rebuilt
    emitSelection()
  },
)

function isSelected(player: PlayerStatistics): boolean {
  return selectedPlayers.value.some((p) => p.playerKey === player.playerKey)
}

function addPlayer(player: PlayerStatistics) {
  if (isSelected(player)) return
  if (selectedPlayers.value.length >= MAX_SELECTED_PLAYERS) {
    compareError.value = `最多同时对比 ${MAX_SELECTED_PLAYERS} 名选手`
    return
  }
  compareError.value = ''
  selectedPlayers.value = [...selectedPlayers.value, player]
  emitSelection()
}

function removePlayer(playerKey: string) {
  selectedPlayers.value = selectedPlayers.value.filter((p) => p.playerKey !== playerKey)
  emitSelection()
}

function togglePlayer(player: PlayerStatistics) {
  if (isSelected(player)) removePlayer(player.playerKey)
  else addPlayer(player)
}

function clearPlayers() {
  selectedPlayers.value = []
  emitSelection()
}

function emitSelection() {
  const ids = selectedPlayers.value
    .map((p) => p.sourcePlayerId)
    .filter((id): id is number => id != null)
  const key = ids.join(',')
  if (key === lastEmittedIds) return
  lastEmittedIds = key
  emit('update:selectedPlayerIds', ids)
}

function canCompare(): boolean {
  return selectedPlayers.value.length >= 2 && props.stageKeys.length > 0
}

const filteredPlayers = computed(() => {
  const keyword = props.searchKeyword.trim().toLowerCase()
  if (!keyword) return players.value
  return players.value.filter((player) =>
    `${player.playerName}${player.teamNames.join('')}`.toLowerCase().includes(keyword),
  )
})

const visiblePlayers = computed(() => filteredPlayers.value.slice(0, MAX_VISIBLE_PLAYERS))

/* ---- 雷达叠加：与选手详情页同口径（同位置分位归一化），逐选手拉取 ---- */

const radarLoading = ref(false)
const radarLabels = ref<string[]>([])
const radarWarnings = ref<string[]>([])
/* 与 PlayerRadarChart 叠加调色板一致：颜色按选手顺序分配，图例色块与雷达描边同源 */
const OVERLAY_COLORS = ['#7fb0f7', '#f0a3a3', '#b39ce8', '#f0bd7e', '#7fd0c5']
const radarOverlay = ref<{ name: string; scores: number[]; color: string }[]>([])
/** 每个雷达轴的指标数据方框：列出各选手该项指标的具体数值，名字前带对应颜色 */
const radarAxisBoxes = ref<RadarAxisBox[]>([])
let radarSeq = 0

watch(
  () => selectedPlayers.value.map((p) => p.playerKey).join(','),
  () => {
    void refreshRadar()
  },
  { immediate: true },
)

async function refreshRadar() {
  const seq = ++radarSeq
  const list = selectedPlayers.value
  if (list.length < 2) {
    radarOverlay.value = []
    radarLabels.value = []
    radarAxisBoxes.value = []
    radarWarnings.value = []
    radarLoading.value = false
    return
  }
  radarLoading.value = true
  radarWarnings.value = []
  const overlay: { name: string; scores: number[]; color: string }[] = []
  /** 每个成功获取雷达数据的选手：保留原始指标（key/value 与 label 对齐）供方框使用 */
  const detailRows: Array<{ name: string; color: string; metrics: PlayerRadarMetric[] }> = []
  let labels: string[] = []
  for (const player of list) {
    if (player.sourcePlayerId == null) {
      radarWarnings.value.push(`${player.playerName}：缺少选手 ID，无法获取雷达数据`)
      continue
    }
    const position = props.positionFilter || player.positions[0] || ''
    if (!position) {
      radarWarnings.value.push(`${player.playerName}：无位置信息，无法获取雷达数据`)
      continue
    }
    try {
      const detail = await api.playerDetail(
        player.sourcePlayerId,
        props.stageKeys,
        position,
        props.minimumMatchCount,
      )
      if (seq !== radarSeq) return
      if (!labels.length) labels = detail.radarMetrics.map((metric) => metric.label)
      const color = OVERLAY_COLORS[overlay.length % OVERLAY_COLORS.length]
      overlay.push({
        name: player.playerName,
        scores: detail.radarMetrics.map((metric) => Number(metric.playerScore)),
        color,
      })
      detailRows.push({ name: player.playerName, color, metrics: detail.radarMetrics })
    } catch {
      if (seq === radarSeq) radarWarnings.value.push(`${player.playerName}：雷达数据获取失败，已跳过`)
    }
  }
  if (seq !== radarSeq) return
  radarOverlay.value = overlay
  radarLabels.value = labels
  radarAxisBoxes.value = labels.map((label, index) => ({
    label,
    rows: detailRows.map((entry) => ({
      name: entry.name,
      color: entry.color,
      text: formatRadarMetricValue(
        entry.metrics[index]?.key ?? '',
        entry.metrics[index]?.value != null ? entry.metrics[index].value : null,
      ),
    })),
  }))
  radarLoading.value = false
}

/** 雷达轴标签：与详情页一致的八维指标（无 value，叠加模式下不展示数值文字）。 */
const radarFakeMetrics = computed<PlayerRadarMetric[]>(() =>
  radarLabels.value.map((label) => ({
    key: label,
    label,
    value: null,
    averageValue: 0,
    playerScore: 0,
    averageScore: 0,
    rank: 0,
    cohortSize: 0,
    available: true,
  })),
)

/* ---- 详情页链接与返回地址 ---- */

/** 返回首页选手对比视图的完整地址（含搜索与已选选手），供详情页返回。 */
function returnToUrl(): string {
  const params = new URLSearchParams({ view: 'compare' })
  if (props.stageKeys.length) params.set('stageKeys', props.stageKeys.join(','))
  if (props.positionFilter) params.set('comparePosition', props.positionFilter)
  params.set('compareMinimumMatchCount', String(props.minimumMatchCount))
  if (props.searchKeyword.trim()) params.set('compareKeyword', props.searchKeyword.trim())
  if (selectedPlayers.value.length) {
    params.set('comparePlayers', selectedPlayers.value
      .map((p) => p.sourcePlayerId)
      .filter((id): id is number => id != null)
      .join(','))
  }
  return `/?${params.toString()}`
}

function playerDetailHref(player: PlayerStatistics): string {
  const position = props.positionFilter || player.positions[0] || ''
  const params = new URLSearchParams({
    stageKeys: props.stageKeys.join(','),
    position,
    minimumMatchCount: String(props.minimumMatchCount),
    returnTo: returnToUrl(),
  })
  if (player.sourcePlayerId == null) return '#'
  return `/players/${player.sourcePlayerId}?${params.toString()}`
}

function fmtPositions(positions: string[]): string {
  return positions.join(' / ') || '—'
}

function fmtTeamNames(teamNames: string[]): string {
  return teamNames.join(' / ') || '—'
}
</script>

<template>
  <div class="compare-panel">
    <div class="table-toolbar">
      <div>
        <p class="eyebrow">SELECT PLAYERS</p>
        <h2>{{ t('compare.title') }}</h2>
      </div>
      <div class="toolbar-right">
        <div class="search-wrap">
          <input
            v-model="searchModel"
            type="search"
            :placeholder="t('compare.placeholder')"
          />
        </div>
        <span class="selected-count">
          {{ t('compare.selected', { n: selectedPlayers.length }) }} / {{ MAX_SELECTED_PLAYERS }}
        </span>
      </div>
    </div>

    <p v-if="listError" class="message error">{{ listError }}</p>
    <p v-if="compareError" class="message error">{{ compareError }}</p>

    <!-- 选手列表：点击行添加/移除 -->
    <div class="player-list-panel">
      <div class="player-list-head">
        <span v-if="listLoading">正在加载选手列表…</span>
        <span v-else-if="players.length">共 {{ players.length }} 名选手 · 按 KDA 排序</span>
        <span v-else>选手列表</span>
        <small>点击行添加 / 移除 · 受顶部位置筛选与最低场数约束</small>
      </div>
      <p v-if="!listLoading && !players.length" class="empty-inline">
        {{ props.stageKeys.length ? '当前筛选范围内暂无选手。' : '请先选择赛段。' }}
      </p>
      <p v-else-if="!listLoading && !visiblePlayers.length" class="empty-inline">
        没有匹配「{{ props.searchKeyword }}」的选手。
      </p>
      <div v-else class="player-list">
        <button
          v-for="player in visiblePlayers"
          :key="player.playerKey"
          type="button"
          class="player-row"
          :class="{ selected: isSelected(player) }"
          @click="togglePlayer(player)"
        >
          <img v-if="player.playerAvatar" :src="player.playerAvatar" :alt="player.playerName" class="player-avatar" />
          <span v-else class="player-placeholder player-avatar">{{ player.playerName.slice(0, 1) }}</span>
          <span class="player-row-info">
            <strong>{{ player.playerName }}</strong>
            <small>{{ fmtTeamNames(player.teamNames) }} · {{ fmtPositions(player.positions) }}</small>
          </span>
          <span class="player-row-kda">KDA {{ player.kda.toFixed(2) }}</span>
          <span class="player-row-check">{{ isSelected(player) ? '✓ 已选' : '添加' }}</span>
        </button>
        <p v-if="filteredPlayers.length > MAX_VISIBLE_PLAYERS" class="empty-inline list-more">
          列表较长，仅显示前 {{ MAX_VISIBLE_PLAYERS }} 名，可在搜索框输入关键字缩小范围。
        </p>
      </div>
    </div>

    <!-- 已选选手：快捷移除与详情入口 -->
    <div v-if="selectedPlayers.length" class="selected-players">
      <div class="selected-heading">
        <span>{{ t('compare.selected', { n: selectedPlayers.length }) }}</span>
        <button type="button" class="basket-clear" @click="clearPlayers">清空</button>
      </div>
      <div class="basket-list">
        <div v-for="player in selectedPlayers" :key="player.playerKey" class="basket-item">
          <img v-if="player.playerAvatar" :src="player.playerAvatar" :alt="player.playerName" class="player-avatar small-avatar" />
          <span class="player-placeholder small-avatar" v-else>{{ player.playerName.slice(0, 1) }}</span>
          <a class="selected-name" :href="playerDetailHref(player)">{{ player.playerName }}</a>
          <button
            class="basket-remove"
            :aria-label="`移除 ${player.playerName}`"
            @click="removePlayer(player.playerKey)"
          >&times;</button>
        </div>
      </div>
    </div>

    <div v-if="canCompare()" class="compare-result">
      <div class="table-toolbar">
        <div>
          <p class="eyebrow">COMPARISON</p>
          <h2>对比结果</h2>
        </div>
        <span class="highlight-note">{{ t('compare.bestHighlight') }}</span>
      </div>
      <div class="compare-radar">
        <p v-if="radarLoading" class="radar-status">正在获取雷达数据（与详情页同口径）…</p>
        <template v-else>
          <PlayerRadarChart :metrics="radarFakeMetrics" :overlay="radarOverlay" :axis-boxes="radarAxisBoxes" />
          <ul v-if="radarOverlay.length" class="radar-legend-overlay" aria-label="选手图例">
            <li v-for="series in radarOverlay" :key="series.name">
              <span class="radar-legend-swatch" :style="{ background: series.color }"></span>
              <span class="radar-legend-text">{{ series.name }}</span>
            </li>
          </ul>
          <p class="radar-note">八维雷达与选手详情页同口径：按同位置 10%-90% 分位归一化（0-100），浅色叠加便于观察相对强弱；各轴旁方框内为该指标的原始数值，颜色与选手对应。</p>
        </template>
        <p v-if="radarWarnings.length" class="radar-warning">{{ radarWarnings.join('；') }}</p>
      </div>
      <table class="compare-table">
        <thead>
          <tr>
            <th>{{ t('compare.metric') }}</th>
            <th v-for="player in selectedPlayers" :key="player.playerKey">
              <div class="compare-player-head">
                <img v-if="player.playerAvatar" :src="player.playerAvatar" :alt="player.playerName" class="player-avatar" />
                <span class="player-placeholder player-avatar" v-else>{{ player.playerName.slice(0, 1) }}</span>
                <div>
                  <strong>{{ player.playerName }}</strong>
                  <small>{{ fmtPositions(player.positions) }}</small>
                </div>
              </div>
            </th>
          </tr>
          <tr class="compare-subhead">
            <th>{{ t('compare.teams') }}</th>
            <th v-for="player in selectedPlayers" :key="`teams-${player.playerKey}`">{{ fmtTeamNames(player.teamNames) || '—' }}</th>
          </tr>
          <tr class="compare-subhead">
            <th>{{ t('compare.games') }}</th>
            <th v-for="player in selectedPlayers" :key="`games-${player.playerKey}`">{{ player.matchCount }} 系列 / {{ player.gameCount }} 局</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="metric in COMPARE_METRICS" :key="metric.key">
            <td class="metric-label">{{ metric.label }}</td>
            <td
              v-for="player in selectedPlayers"
              :key="`${metric.key}:${player.playerKey}`"
              :class="{ 'best-value': isBest(metric, player) }"
            >{{ metricValue(metric, player) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <p v-else-if="selectedPlayers.length === 1" class="message success">
      {{ t('compare.noPlayers') }} —— 当前已选择 1 人，请再添加至少 1 人。
    </p>
  </div>
</template>

<style scoped>
.search-wrap { min-width: 460px; }
.search-wrap input { flex: 1; width: auto; min-width: 0; min-height: 40px; padding: 8px 12px; }
@media (max-width: 960px) {
  .search-wrap { min-width: 320px; }
}
@media (max-width: 620px) {
  .search-wrap { min-width: 100%; }
}
.selected-count { color: var(--muted); font-size: 12px; white-space: nowrap; }
.player-list-panel { padding: 10px 20px; border-bottom: 1px solid var(--line); }
.player-list-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; margin-bottom: 8px; flex-wrap: wrap; }
.player-list-head span { font-size: 12.5px; font-weight: 650; color: var(--text-3); }
.player-list-head small { color: var(--text-4); font-size: 11.5px; }
.player-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 6px; }
.player-row {
  display: flex; align-items: center; gap: 9px; padding: 7px 10px;
  border: 1px solid var(--line); border-radius: 8px; background: var(--panel-2);
  cursor: pointer; text-align: left; min-width: 0;
}
.player-row:hover { border-color: var(--accent-line); }
.player-row.selected { border-color: var(--accent); background: var(--accent-soft); }
.player-row-info { flex: 1; min-width: 0; }
.player-row-info strong { display: block; font-size: 13px; }
.player-row-info small { display: block; margin-top: 1px; color: var(--text-4); font-size: 11.5px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.player-row-kda { font-size: 11.5px; color: var(--text-3); font-variant-numeric: tabular-nums; white-space: nowrap; }
.player-row-check { flex: 0 0 auto; padding: 3px 9px; border-radius: 5px; font-size: 11.5px; font-weight: 650; color: var(--accent); border: 1px solid var(--accent-line); }
.player-row.selected .player-row-check { color: #fff; background: var(--accent); border-color: var(--accent); }
.list-more { margin: 8px 0 0; }
.selected-players { padding: 12px 20px 16px; border-bottom: 1px solid var(--line); }
.selected-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.selected-heading span { color: var(--text-3); font-size: 12px; font-weight: 650; }
.small-avatar { width: 22px; height: 22px; border-radius: 50%; object-fit: cover; font-size: 11px; }
.selected-name { color: var(--text-2); text-decoration: none; font-weight: 600; }
.selected-name:hover { color: var(--accent); text-decoration: underline; }
.highlight-note { color: var(--muted); font-size: 12px; }
.compare-table { width: 100%; border-collapse: collapse; font-size: 13px; min-width: 640px; }
.compare-radar { padding: 6px 0 10px; position: relative; }
.radar-legend-overlay { position: absolute; top: 10px; right: 12px; margin: 0; padding: 8px 12px; list-style: none; background: #fff; border: 1px solid var(--line); border-radius: 8px; display: flex; flex-direction: column; gap: 6px; }
.radar-legend-overlay li { display: flex; align-items: center; gap: 7px; }
.radar-legend-swatch { width: 10px; height: 10px; border-radius: 2px; flex: 0 0 auto; }
.radar-legend-text { font-size: 12px; font-weight: 650; color: #24292f; }
.radar-status { margin: 0; text-align: center; color: var(--text-3); font-size: 12.5px; padding: 40px 0; }
.radar-note { margin: 4px 0 0; text-align: center; color: var(--text-4); font-size: 12px; }
.radar-warning { margin: 6px 0 0; text-align: center; color: var(--danger, #c0392b); font-size: 12px; }
.compare-table th, .compare-table td { padding: 9px 12px; border-bottom: 1px solid var(--line); text-align: left; white-space: nowrap; }
.compare-table thead th { color: var(--text-3); font-size: 12px; background: var(--th-bg); }
.compare-table td { text-align: center; font-variant-numeric: tabular-nums; }
.compare-table td.metric-label { text-align: left; color: var(--text-2); font-weight: 600; }
.compare-table td.best-value { color: var(--accent-dark); font-weight: 750; background: var(--accent-soft); }
.compare-player-head { display: flex; align-items: center; gap: 8px; min-width: 150px; }
.compare-player-head strong { display: block; color: var(--text); font-size: 13px; }
.compare-player-head small { display: block; color: var(--text-4); font-size: 11px; }
.player-avatar { width: 28px; height: 28px; border-radius: 50%; object-fit: cover; flex: 0 0 auto; }
.compare-subhead th { font-weight: 600; color: var(--muted); background: var(--panel-2); font-size: 11.5px; }
.compare-result { margin-top: 16px; }
</style>
