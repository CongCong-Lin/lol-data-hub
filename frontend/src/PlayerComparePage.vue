<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, type PlayerStatistics, type Season, type Stage } from './api'
import { useI18n } from './i18n'

const route = useRoute()
const { t } = useI18n()

const MAX_STAGE_SELECTION = 50
const MAX_SELECTED_PLAYERS = 5
const MAX_CANDIDATES = 5

function makeKey(seasonId: number, stageId: number): string {
  return `${seasonId}:${stageId}`
}

const seasons = ref<Season[]>([])
const allStages = ref<Stage[]>([])
const browsedSeasonId = ref(0)
const selectedStageKeys = ref<Set<string>>(new Set())
const positionFilter = ref('')
const minimumMatchCount = ref(5)

const searchKeyword = ref('')
const searching = ref(false)
const searchError = ref('')
const candidates = ref<PlayerStatistics[]>([])
const selectedPlayers = ref<PlayerStatistics[]>([])
const comparing = ref(false)
const compareError = ref('')

const PLAYER_POSITION_OPTIONS = [
  { value: '', label: '全部' },
  { value: 'TOP', label: '上单' },
  { value: 'JUG', label: '打野' },
  { value: 'MID', label: '中路' },
  { value: 'AD', label: '下路' },
  { value: 'SUP', label: '辅助' },
]

const sortedSeasons = computed(() =>
  [...seasons.value].sort((left, right) => left.sourceSeasonId - right.sourceSeasonId),
)

const browsedStages = computed(() =>
  allStages.value.filter((s) => s.sourceSeasonId === browsedSeasonId.value),
)

const selectedStageDetails = computed(() =>
  allStages.value.filter((s) => selectedStageKeys.value.has(makeKey(s.sourceSeasonId, s.sourceStageId))),
)

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

function toggleStage(compositeKey: string) {
  const newSet = new Set(selectedStageKeys.value)
  if (newSet.has(compositeKey)) newSet.delete(compositeKey)
  else {
    if (newSet.size >= MAX_STAGE_SELECTION) {
      searchError.value = `最多选择 ${MAX_STAGE_SELECTION} 个赛段`
      return
    }
    newSet.add(compositeKey)
  }
  selectedStageKeys.value = newSet
  candidates.value = []
}

function removeStage(compositeKey: string) {
  const newSet = new Set(selectedStageKeys.value)
  newSet.delete(compositeKey)
  selectedStageKeys.value = newSet
  candidates.value = []
}

function clearSelectedStages() {
  selectedStageKeys.value = new Set()
  candidates.value = []
}

async function searchPlayers() {
  const keys = [...selectedStageKeys.value]
  if (!keys.length) {
    searchError.value = '请先选择赛段再搜索选手'
    return
  }
  const keyword = searchKeyword.value.trim().toLowerCase()
  if (!keyword) {
    searchError.value = '请输入选手姓名关键字'
    return
  }
  const seq = ++searchSeq
  searching.value = true
  searchError.value = ''
  try {
    const data = await api.playerStatisticsByKeys(
      keys,
      minimumMatchCount.value,
      positionFilter.value,
      'kda',
      'desc',
    )
    if (seq !== searchSeq) return
    const matches = data.items.filter(
      (item) => `${item.playerName}${item.teamNames.join('')}`.toLowerCase().includes(keyword),
    )
    candidates.value = matches.slice(0, MAX_CANDIDATES)
    if (!candidates.value.length) searchError.value = t('compare.notFound')
  } catch (reason) {
    if (seq === searchSeq) searchError.value = reason instanceof Error ? reason.message : String(reason)
  } finally {
    if (seq === searchSeq) searching.value = false
  }
}
let searchSeq = 0

function addPlayer(player: PlayerStatistics) {
  if (selectedPlayers.value.some((p) => p.playerKey === player.playerKey)) return
  if (selectedPlayers.value.length >= MAX_SELECTED_PLAYERS) {
    searchError.value = `最多同时对比 ${MAX_SELECTED_PLAYERS} 名选手`
    return
  }
  selectedPlayers.value = [...selectedPlayers.value, player]
  candidates.value = candidates.value.filter((p) => p.playerKey !== player.playerKey)
  searchError.value = ''
}

function removePlayer(playerKey: string) {
  selectedPlayers.value = selectedPlayers.value.filter((p) => p.playerKey !== playerKey)
}

function clearPlayers() {
  selectedPlayers.value = []
}

function canCompare(): boolean {
  return selectedPlayers.value.length >= 2 && selectedStageKeys.value.size > 0
}

function playerDetailHref(player: PlayerStatistics): string {
  const position = positionFilter.value || player.positions[0] || ''
  const params = new URLSearchParams({
    stageKeys: [...selectedStageKeys.value].join(','),
    position,
    minimumMatchCount: String(minimumMatchCount.value),
    returnTo: route.fullPath,
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

async function loadSeasons() {
  seasons.value = await api.seasons()
  if (seasons.value.length && !seasons.value.some((item) => item.sourceSeasonId === browsedSeasonId.value)) {
    browsedSeasonId.value = seasons.value[0].sourceSeasonId
  }
}

async function loadStages() {
  try {
    allStages.value = await api.availability('PLAYER', false)
  } catch (reason) {
    compareError.value = reason instanceof Error ? reason.message : `加载赛段失败：${String(reason)}`
  }
}

function restoreFromUrl(): boolean {
  if (typeof window === 'undefined') return false
  const params = new URLSearchParams(window.location.search)
  const keys = (params.get('stageKeys') ?? '')
    .split(',')
    .map((key) => key.trim())
    .filter((key) => /^\d+:\d+$/.test(key))
    .slice(0, MAX_STAGE_SELECTION)
  if (keys.length) selectedStageKeys.value = new Set(keys)
  const position = params.get('position') ?? ''
  if (PLAYER_POSITION_OPTIONS.some((opt) => opt.value === position)) positionFilter.value = position
  const minimum = Number(params.get('minimumMatchCount'))
  if (Number.isInteger(minimum) && minimum >= 0 && minimum <= 10000) minimumMatchCount.value = minimum
  return keys.length > 0
}

onMounted(async () => {
  try {
    await loadSeasons()
    await loadStages()
    restoreFromUrl()
  } catch (reason) {
    compareError.value = reason instanceof Error ? reason.message : String(reason)
  }
})
</script>

<template>
  <div class="compare-page shell">
    <header class="hero">
      <div>
        <p class="eyebrow">PLAYER COMPARISON</p>
        <h1>{{ t('compare.title') }}</h1>
      </div>
      <RouterLink class="back-link" to="/">{{ t('compare.back') }}</RouterLink>
      <p class="hero-copy">选择赛段后搜索选手加入对比，同一指标的最优值自动高亮（场均死亡为越低越好）。对比数据与选手统计列表口径一致。</p>
    </header>

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
      <div class="field compact">
        <label>{{ t('compare.positionFilter') }}</label>
        <select v-model="positionFilter">
          <option v-for="opt in PLAYER_POSITION_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </select>
      </div>
      <div class="field compact">
        <label>{{ t('compare.minimumMatches') }}</label>
        <input v-model.number="minimumMatchCount" type="number" min="0" max="10000" step="1" />
      </div>
      <div class="actions">
        <button class="ghost" :disabled="!selectedStageKeys.size" @click="clearPlayers">{{ t('common.close') }}</button>
      </div>

      <div class="stage-block">
        <div class="stage-heading">
          <span>选择赛段（支持跨赛事选择）</span>
          <small>当前浏览：{{ seasons.find((s) => s.sourceSeasonId === browsedSeasonId)?.name ?? `赛事 #${browsedSeasonId}` }}</small>
        </div>
        <div v-if="browsedStages.length" class="stage-list">
          <button
            v-for="stage in browsedStages"
            :key="makeKey(stage.sourceSeasonId, stage.sourceStageId)"
            class="stage-chip"
            :class="{ selected: selectedStageKeys.has(makeKey(stage.sourceSeasonId, stage.sourceStageId)), disabled: !stage.collected }"
            :disabled="!stage.collected"
            @click="toggleStage(makeKey(stage.sourceSeasonId, stage.sourceStageId))"
          >
            <span>{{ stage.name }}</span>
            <small v-if="!stage.collected" class="uncollected-tag">未采集</small>
          </button>
        </div>
        <p v-else class="empty-inline">该赛事暂无赛段数据。</p>

        <div class="basket-section">
          <div class="basket-heading">
            <span>已选赛段</span>
            <div v-if="selectedStageKeys.size > 0" class="basket-heading-actions">
              <small>{{ selectedStageKeys.size }} 个赛段</small>
              <button type="button" class="basket-clear" @click="clearSelectedStages">清空全部</button>
            </div>
          </div>
          <div v-if="selectedStageKeys.size === 0" class="empty-inline">
            请先选择赛段，再搜索要对比的选手。
          </div>
          <div v-else class="basket-list">
            <div
              v-for="stage in selectedStageDetails"
              :key="makeKey(stage.sourceSeasonId, stage.sourceStageId)"
              class="basket-item"
            >
              <span class="basket-season">{{ stage.seasonName ?? `赛事#${stage.sourceSeasonId}` }}</span>
              <span class="basket-stage">{{ stage.name }}</span>
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

    <p v-if="searchError" class="message error">{{ searchError }}</p>
    <p v-if="compareError" class="message error">{{ compareError }}</p>

    <section class="panel table-panel">
      <div class="table-toolbar">
        <div>
          <p class="eyebrow">SEARCH &amp; SELECT</p>
          <h2>{{ t('compare.title') }}</h2>
        </div>
        <div class="toolbar-right">
          <div class="search-wrap">
            <input
              v-model="searchKeyword"
              type="search"
              :placeholder="t('compare.placeholder')"
              @keyup.enter="searchPlayers"
            />
            <button class="primary search-button" :disabled="searching || !selectedStageKeys.size" @click="searchPlayers">
              {{ searching ? t('common.searching') : '搜索' }}
            </button>
          </div>
          <span class="selected-count">{{ t('compare.selected', { n: selectedPlayers.length }) }} · {{ t('compare.searchLimit') }}</span>
        </div>
      </div>

      <div v-if="candidates.length" class="candidate-list">
        <div v-for="player in candidates" :key="player.playerKey" class="candidate-item">
          <img v-if="player.playerAvatar" :src="player.playerAvatar" :alt="player.playerName" class="player-avatar" />
          <span class="player-placeholder player-avatar" v-else>{{ player.playerName.slice(0, 1) }}</span>
          <div class="candidate-info">
            <strong>{{ player.playerName }}</strong>
            <small>{{ fmtTeamNames(player.teamNames) }} · {{ fmtPositions(player.positions) }} · KDA {{ player.kda.toFixed(2) }}</small>
          </div>
          <button
            type="button"
            class="add-button"
            :disabled="selectedPlayers.some((p) => p.playerKey === player.playerKey)"
            @click="addPlayer(player)"
          >
            {{ selectedPlayers.some((p) => p.playerKey === player.playerKey) ? '已添加' : '添加' }}
          </button>
        </div>
      </div>

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
    </section>

    <section v-if="canCompare()" class="panel table-panel compare-result">
      <div class="table-toolbar">
        <div>
          <p class="eyebrow">COMPARISON</p>
          <h2>对比结果</h2>
        </div>
        <span class="highlight-note">{{ t('compare.bestHighlight') }}</span>
      </div>
      <div class="table-scroll">
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
    </section>

    <p v-else-if="selectedPlayers.length === 1" class="message success">
      {{ t('compare.noPlayers') }} —— 当前已选择 1 人，请再添加至少 1 人。
    </p>
  </div>
</template>

<style scoped>
.back-link { color: var(--accent); text-decoration: none; font-weight: 600; justify-self: end; }
.back-link:hover { text-decoration: underline; }
.search-button { min-height: 36px; }
.selected-count { color: var(--muted); font-size: 12px; }
.candidate-list { padding: 10px 20px; border-bottom: 1px solid var(--line); }
.candidate-item { display: flex; align-items: center; gap: 10px; padding: 8px 0; }
.candidate-item + .candidate-item { border-top: 1px solid var(--line); }
.candidate-info { flex: 1; min-width: 0; }
.candidate-info strong { display: block; }
.candidate-info small { display: block; margin-top: 2px; color: var(--text-4); font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.add-button {
  flex: 0 0 auto; padding: 5px 12px; border: 1px solid var(--accent); border-radius: 5px;
  color: #fff; background: var(--accent); font-size: 12px; font-weight: 650;
}
.add-button:hover:not(:disabled) { background: var(--accent-strong); }
.selected-players { padding: 12px 20px 16px; border-bottom: 1px solid var(--line); }
.selected-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.selected-heading span { color: var(--text-3); font-size: 12px; font-weight: 650; }
.small-avatar { width: 22px; height: 22px; border-radius: 50%; object-fit: cover; font-size: 11px; }
.selected-name { color: var(--text-2); text-decoration: none; font-weight: 600; }
.selected-name:hover { color: var(--accent); text-decoration: underline; }
.highlight-note { color: var(--muted); font-size: 12px; }
.compare-table { width: 100%; border-collapse: collapse; font-size: 13px; min-width: 640px; }
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
