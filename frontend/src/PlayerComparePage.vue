<script setup lang="ts">
import { computed, ref } from 'vue'
import { api, type PlayerStatistics } from './api'
import { useI18n } from './i18n'

const props = defineProps<{
  stageKeys: string[]
  positionFilter: string
  minimumMatchCount: number
}>()

const emit = defineEmits<{
  'update:positionFilter': [value: string]
  'update:minimumMatchCount': [value: number]
}>()

const { t } = useI18n()

const MAX_SELECTED_PLAYERS = 5
const MAX_CANDIDATES = 5

const searchKeyword = ref('')
const searching = ref(false)
const searchError = ref('')
const candidates = ref<PlayerStatistics[]>([])
const selectedPlayers = ref<PlayerStatistics[]>([])
const comparing = ref(false)
const compareError = ref('')

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

async function searchPlayers() {
  const keys = props.stageKeys
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
      props.minimumMatchCount,
      props.positionFilter,
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
  return selectedPlayers.value.length >= 2 && props.stageKeys.length > 0
}

/** 返回首页选手对比视图的完整地址，供详情页返回。 */
function returnToUrl(): string {
  const params = new URLSearchParams({ view: 'compare' })
  if (props.stageKeys.length) params.set('stageKeys', props.stageKeys.join(','))
  if (props.positionFilter) params.set('comparePosition', props.positionFilter)
  params.set('compareMinimumMatchCount', String(props.minimumMatchCount))
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
          <button class="primary search-button" :disabled="searching || !props.stageKeys.length" @click="searchPlayers">
            {{ searching ? t('common.searching') : '搜索' }}
          </button>
        </div>
        <span class="selected-count">{{ t('compare.selected', { n: selectedPlayers.length }) }} · {{ t('compare.searchLimit') }}</span>
      </div>
    </div>

    <p v-if="searchError" class="message error">{{ searchError }}</p>
    <p v-if="compareError" class="message error">{{ compareError }}</p>

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

    <div v-if="canCompare()" class="compare-result">
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
    </div>

    <p v-else-if="selectedPlayers.length === 1" class="message success">
      {{ t('compare.noPlayers') }} —— 当前已选择 1 人，请再添加至少 1 人。
    </p>
  </div>
</template>

<style scoped>
.search-wrap { min-width: 560px; }
.search-wrap input { flex: 1; width: auto; min-width: 0; min-height: 40px; padding: 8px 12px; }
.search-button { height: 40px; min-height: 40px; padding: 0 24px; font-size: 14px; line-height: 1; }
@media (max-width: 960px) {
  .search-wrap { min-width: 400px; }
}
@media (max-width: 620px) {
  .search-wrap { min-width: 100%; }
}
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
