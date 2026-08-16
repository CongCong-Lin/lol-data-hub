<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  api,
  type ChampionVersionCompareItem,
  type EloTeamRating,
  type PlayerStatistics,
  type TeamStatistics,
} from './api'

const props = defineProps<{
  stageKeys: string[]
  refreshKey: number
}>()

const emit = defineEmits<{
  loading: [value: boolean]
  loaded: [dataVersion: number | null]
}>()

type LeaderboardTab = 'kings' | 'mvp' | 'elo' | 'versions'

const TAB_OPTIONS: Array<{ value: LeaderboardTab; label: string }> = [
  { value: 'kings', label: '数据王' },
  { value: 'mvp', label: 'MVP 榜' },
  { value: 'elo', label: 'Elo 评分' },
  { value: 'versions', label: '版本变迁' },
]

const activeTab = ref<LeaderboardTab>('kings')
const loading = ref(false)
const error = ref('')
const players = ref<PlayerStatistics[]>([])
const teams = ref<TeamStatistics[]>([])
const eloRatings = ref<EloTeamRating[]>([])
const versionItems = ref<ChampionVersionCompareItem[]>([])
const versionFrom = ref('')
const versionTo = ref('')
let loadSeq = 0

const hasStages = computed(() => props.stageKeys.length > 0)

interface KingRow {
  key: string
  name: string
  team: string
  value: string
}

interface KingBoard {
  key: string
  label: string
  type: 'player' | 'team'
  top: KingRow[]
}

const KING_DEFINITIONS = [
  { key: 'kda', label: 'KDA 王', field: (p: PlayerStatistics) => p.kda, format: fmt2 },
  { key: 'damagePerGame', label: '场均伤害王', field: (p: PlayerStatistics) => p.damagePerGame, format: fmt0 },
  { key: 'killPerGame', label: '场均击杀王', field: (p: PlayerStatistics) => p.killPerGame, format: fmt2 },
  { key: 'killParticipantPercent', label: '参团率王', field: (p: PlayerStatistics) => p.killParticipantPercent, format: fmtPercent },
  { key: 'creepScorePerGame', label: '场均补刀王', field: (p: PlayerStatistics) => p.creepScorePerGame, format: fmt2 },
] as const

const TEAM_KING_DEFINITIONS = [
  { key: 'winningRate', label: '胜率王', field: (t: TeamStatistics) => t.winningRate, format: fmtPercent },
  { key: 'firstBloodRate', label: '一血率王', field: (t: TeamStatistics) => t.firstBloodRate, format: fmtPercent },
] as const

const kingBoards = computed<KingBoard[]>(() => {
  const playerBoards = KING_DEFINITIONS.map((definition) => ({
    key: definition.key,
    label: definition.label,
    type: 'player' as const,
    top: [...players.value]
      .filter((player) => definition.field(player) != null)
      .sort((a, b) => Number(definition.field(b)) - Number(definition.field(a)))
      .slice(0, 10)
      .map((player) => ({
        key: player.playerKey,
        name: player.playerName,
        team: fmtTeamNames(player.teamNames),
        value: definition.format(definition.field(player)),
      })),
  }))
  const teamBoards = TEAM_KING_DEFINITIONS.map((definition) => ({
    key: definition.key,
    label: definition.label,
    type: 'team' as const,
    top: [...teams.value]
      .filter((team) => definition.field(team) != null)
      .sort((a, b) => Number(definition.field(b)) - Number(definition.field(a)))
      .slice(0, 10)
      .map((team) => ({
        key: String(team.teamId),
        name: team.teamName,
        team: '',
        value: definition.format(definition.field(team)),
      })),
  }))
  return [...playerBoards, ...teamBoards]
})

const mvpBoard = computed(() =>
  [...players.value]
    .filter((player) => player.mvpVotes > 0 || player.mvpCount > 0)
    .sort((a, b) => b.mvpVotes - a.mvpVotes || b.mvpCount - a.mvpCount)
    .slice(0, 20),
)

const versionBoard = computed(() => {
  const items = versionItems.value.filter((item) => item.pickDelta !== 0 || item.winRateDelta !== 0)
  return items.filter((item) => item.pickDelta > 0)
})

watch(
  [() => props.stageKeys.join(','), () => props.refreshKey],
  () => {
    void load()
  },
  { immediate: true },
)

async function load() {
  if (!hasStages.value) {
    players.value = []
    teams.value = []
    eloRatings.value = []
    versionItems.value = []
    emit('loaded', null)
    return
  }
  const seq = ++loadSeq
  loading.value = true
  emit('loading', true)
  error.value = ''
  try {
    const [playerResult, teamResult, eloResult] = await Promise.all([
      api.playerStatisticsByKeys(props.stageKeys, 0, '', 'kda', 'desc'),
      api.teamStatisticsByKeys(props.stageKeys, 1, 'winningRate', 'desc'),
      api.eloRatings(props.stageKeys),
    ])
    if (seq !== loadSeq) return
    players.value = playerResult.items
    teams.value = teamResult.items
    eloRatings.value = eloResult.ratings
    emit('loaded', playerResult.dataVersion ?? teamResult.dataVersion ?? null)
  } catch (reason) {
    if (seq === loadSeq) error.value = reason instanceof Error ? reason.message : String(reason)
  } finally {
    if (seq === loadSeq) {
      loading.value = false
      emit('loading', false)
    }
  }
}

async function loadVersions() {
  if (!hasStages.value || !versionFrom.value || !versionTo.value || versionFrom.value >= versionTo.value) {
    error.value = '请选择有效的日期范围（起始早于结束）'
    return
  }
  const seq = ++loadSeq
  loading.value = true
  emit('loading', true)
  error.value = ''
  try {
    const result = await api.championVersionCompare(props.stageKeys, versionFrom.value, versionTo.value)
    if (seq !== loadSeq) return
    versionItems.value = result.items
  } catch (reason) {
    if (seq === loadSeq) error.value = reason instanceof Error ? reason.message : String(reason)
  } finally {
    if (seq === loadSeq) {
      loading.value = false
      emit('loading', false)
    }
  }
}

function fmt0(value: number | null): string {
  return value == null ? '-' : Math.round(value).toLocaleString()
}

function fmt2(value: number | null): string {
  return value == null ? '-' : value.toFixed(2)
}

function fmtPercent(value: number | null): string {
  return value == null ? '-' : `${(value * 100).toFixed(1)}%`
}

/** 出场变化：整数场数带符号，如 +10场 / -3场。 */
function fmtPickDelta(value: number): string {
  return value > 0 ? `+${value}场` : `${value}场`
}

/** 胜率变化：百分点一位小数带符号，如 +1.5% / -0.3%。 */
function fmtWinRateDelta(value: number): string {
  const percent = value * 100
  return `${percent > 0 ? '+' : ''}${percent.toFixed(1)}%`
}

function fmtWinRate(value: number | null): string {
  return value == null ? '-' : `${(value * 100).toFixed(1)}%`
}

function fmtTeamNames(teamNames: string[]): string {
  return teamNames.join(' / ') || '—'
}

/** Elo 战队详情链接：携带赛段、最低场数与返回地址。 */
function teamHref(team: EloTeamRating): string {
  const keys = props.stageKeys.join(',')
  const params = new URLSearchParams({
    stageKeys: keys,
    minimumMatchCount: '1',
    returnTo: `/?view=leaderboards&stageKeys=${keys}`,
  })
  return `/teams/${team.teamId}?${params.toString()}`
}

function sparklinePoints(history: number[]): string {
  if (history.length < 2) return ''
  const width = 120
  const height = 32
  const min = Math.min(...history)
  const max = Math.max(...history)
  const range = max - min || 1
  return history
    .map((value, index) => {
      const x = (index / (history.length - 1)) * width
      const y = height - ((value - min) / range) * height
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
}
</script>

<template>
  <section class="panel table-panel">
    <div class="table-toolbar">
      <div>
        <p class="eyebrow">LEADERBOARDS</p>
        <h2>排行榜</h2>
      </div>
      <div class="toolbar-right">
        <div class="toolbar-options-row">
          <div class="position-filter" aria-label="排行榜类型">
            <button
              v-for="tab in TAB_OPTIONS"
              :key="tab.value"
              class="pos-chip"
              :class="{ active: activeTab === tab.value }"
              :aria-pressed="activeTab === tab.value"
              @click="activeTab = tab.value"
            >{{ tab.label }}</button>
          </div>
        </div>
      </div>
    </div>

    <p v-if="!hasStages" class="detail-notice-inline">请先在上方选择赛段，排行榜会随所选范围实时计算。</p>
    <p v-else-if="loading" class="detail-notice-inline">加载中…</p>
    <p v-else-if="error" class="detail-notice-inline error-text">{{ error }}</p>

    <div v-else-if="activeTab === 'kings'" class="leaderboard-grid">
      <article v-for="board in kingBoards" :key="board.key" class="leaderboard-card">
        <h3>{{ board.label }}</h3>
        <table class="king-table">
          <thead>
            <tr>
              <th>排名</th>
              <th>{{ board.type === 'team' ? '战队' : '选手' }}</th>
              <th v-if="board.type === 'player'">战队</th>
              <th>数值</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in board.top" :key="row.key">
              <td class="king-rank">{{ index + 1 }}</td>
              <td>{{ row.name }}</td>
              <td v-if="board.type === 'player'" class="king-team">{{ row.team }}</td>
              <td class="king-value">{{ row.value }}</td>
            </tr>
          </tbody>
        </table>
        <p v-if="!board.top.length" class="king-empty">暂无数据</p>
      </article>
    </div>

    <div v-else-if="activeTab === 'mvp'" class="table-scroll">
      <table class="team-table">
        <thead>
          <tr>
            <th>排名</th><th>选手</th><th>战队</th><th>位置</th>
            <th>MVP 票数</th><th>MVP 次数</th><th>系列赛</th><th>KDA</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(player, index) in mvpBoard" :key="player.playerKey">
            <td class="accent">{{ index + 1 }}</td>
            <td>{{ player.playerName }}</td>
            <td>{{ player.teamNames.join(' / ') || '-' }}</td>
            <td>{{ player.positions.join(' / ') || '-' }}</td>
            <td class="accent">{{ player.mvpVotes }}</td>
            <td>{{ player.mvpCount }}</td>
            <td>{{ player.matchCount }}</td>
            <td>{{ fmt2(player.kda) }}</td>
          </tr>
        </tbody>
      </table>
      <p v-if="!mvpBoard.length" class="detail-notice-inline">当前范围暂无 MVP 票选数据。</p>
    </div>

    <div v-else-if="activeTab === 'elo'" class="table-scroll">
      <table class="team-table">
        <thead>
          <tr>
            <th>排名</th><th>战队</th><th>Elo 评分</th><th>场次</th><th>大场</th><th>小局胜-负</th><th>评分轨迹</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="team in eloRatings" :key="team.teamId">
            <td class="accent">{{ team.rank }}</td>
            <td><a class="team-link" :href="teamHref(team)">{{ team.teamName }}</a></td>
            <td class="accent">{{ team.rating }}</td>
            <td>{{ team.games }}</td>
            <td>{{ team.seriesCount }}</td>
            <td>{{ team.wins }}-{{ team.losses }}</td>
            <td>
              <svg v-if="team.ratingHistory.length > 1" class="elo-sparkline" viewBox="0 0 120 32" aria-hidden="true">
                <polyline :points="sparklinePoints(team.ratingHistory)" />
              </svg>
              <span v-else>-</span>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!eloRatings.length" class="detail-notice-inline">当前范围暂无对局数据，无法计算 Elo。</p>
      <p v-else class="detail-notice-inline">按开赛时间重放全部小局的无状态 Elo（起始 1500，K=32）。</p>
    </div>

    <div v-else-if="activeTab === 'versions'" class="version-panel">
      <div class="version-controls">
        <label>起始日期 <input v-model="versionFrom" type="date" /></label>
        <label>结束日期 <input v-model="versionTo" type="date" /></label>
        <button class="primary" type="button" @click="loadVersions">对比版本变化</button>
      </div>
      <div class="table-scroll">
        <table class="team-table">
          <thead>
            <tr>
              <th>英雄</th><th>起始出场</th><th>结束出场</th><th>期间胜-负</th><th>出场变化</th>
              <th>起始胜率</th><th>结束胜率</th><th>胜率变化</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in versionBoard" :key="item.championId">
              <td>{{ item.championChineseName || item.championName }}</td>
              <td>{{ item.fromPickCount }}</td>
              <td>{{ item.toPickCount }}</td>
              <td class="accent">{{ item.windowWins }}-{{ item.windowLosses }}</td>
              <td :class="item.pickDelta > 0 ? 'accent' : 'danger-text'">{{ fmtPickDelta(item.pickDelta) }}</td>
              <td>{{ fmtWinRate(item.fromWinRate) }}</td>
              <td>{{ fmtWinRate(item.toWinRate) }}</td>
              <td :class="item.winRateDelta > 0 ? 'accent' : 'danger-text'">{{ fmtWinRateDelta(item.winRateDelta) }}</td>
            </tr>
          </tbody>
        </table>
        <p v-if="!versionItems.length" class="detail-notice-inline">选择两个日期后点击对比，查看英雄出场与胜率的版本变化。</p>
        <p v-else-if="!versionBoard.length" class="detail-notice-inline">该范围内暂无出场增加的英雄。</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.leaderboard-grid {
  display: grid; grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px; padding: 4px 0 8px;
}
@media (max-width: 1200px) {
  .leaderboard-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 760px) {
  .leaderboard-grid { grid-template-columns: 1fr; }
}
.leaderboard-card {
  border: 1px solid var(--line); border-radius: 10px; padding: 14px 16px; background: var(--panel-2);
}
.leaderboard-card h3 { margin: 0 0 10px; font-size: 14px; color: var(--text-2); }
.king-table { width: 100%; border-collapse: collapse; font-size: 12.5px; }
.king-table th, .king-table td { padding: 6px 8px; border-bottom: 1px solid var(--line); text-align: left; white-space: nowrap; }
.king-table thead th { color: var(--text-3); font-size: 11.5px; font-weight: 650; background: var(--th-bg); }
.king-table tbody tr:hover { background: var(--hover-bg); }
.king-rank { color: var(--text-3); font-weight: 650; }
.king-team { color: var(--text-4); font-size: 12px; max-width: 130px; overflow: hidden; text-overflow: ellipsis; }
.king-value { color: var(--accent-dark); font-weight: 750; text-align: right; }
.king-empty { margin: 8px 0 0; color: var(--text-4); font-size: 12px; }
.team-link { color: var(--text); text-decoration: none; font-weight: 600; }
.team-link:hover { color: var(--accent); text-decoration: underline; }
.elo-sparkline { width: 120px; height: 32px; }
.elo-sparkline polyline { fill: none; stroke: var(--accent); stroke-width: 1.6; }
.version-controls { display: flex; flex-wrap: nowrap; align-items: center; gap: 12px; padding: 6px 0 12px; overflow-x: auto; }
.version-controls label { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; color: var(--text-2); white-space: nowrap; flex: 0 0 auto; }
.version-controls input[type='date'] { padding: 5px 8px; border: 1px solid var(--line); border-radius: 6px; flex: 0 0 auto; }
.error-text { color: var(--danger, #c93c37); }
.detail-notice-inline { color: var(--text-3); font-size: 13px; }
.danger-text { color: var(--danger, #c93c37); font-weight: 650; }
</style>
