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
const versionDirection = ref<'rising' | 'falling'>('rising')
let loadSeq = 0

const hasStages = computed(() => props.stageKeys.length > 0)

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

const kingBoards = computed(() => {
  return KING_DEFINITIONS.map((definition) => ({
    ...definition,
    top: [...players.value]
      .filter((player) => definition.field(player) != null)
      .sort((a, b) => Number(definition.field(b)) - Number(definition.field(a)))
      .slice(0, 5),
  }))
})

const teamKingBoards = computed(() => {
  return TEAM_KING_DEFINITIONS.map((definition) => ({
    ...definition,
    top: [...teams.value]
      .filter((team) => definition.field(team) != null)
      .sort((a, b) => Number(definition.field(b)) - Number(definition.field(a)))
      .slice(0, 5),
  }))
})

const mvpBoard = computed(() =>
  [...players.value]
    .filter((player) => player.mvpVotes > 0 || player.mvpCount > 0)
    .sort((a, b) => b.mvpVotes - a.mvpVotes || b.mvpCount - a.mvpCount)
    .slice(0, 20),
)

const versionBoard = computed(() => {
  const items = versionItems.value.filter((item) => item.pickDelta !== 0 || item.winRateDelta !== 0)
  return versionDirection.value === 'rising'
    ? items.filter((item) => item.pickDelta > 0)
    : items.filter((item) => item.pickDelta < 0)
})

watch(
  [() => props.stageKeys, () => props.refreshKey],
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
    return
  }
  const seq = ++loadSeq
  loading.value = true
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
  } catch (reason) {
    if (seq === loadSeq) error.value = reason instanceof Error ? reason.message : String(reason)
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

async function loadVersions() {
  if (!hasStages.value || !versionFrom.value || !versionTo.value || versionFrom.value >= versionTo.value) {
    error.value = '请选择有效的日期范围（起始早于结束）'
    return
  }
  const seq = ++loadSeq
  loading.value = true
  error.value = ''
  try {
    const result = await api.championVersionCompare(props.stageKeys, versionFrom.value, versionTo.value)
    if (seq !== loadSeq) return
    versionItems.value = result.items
  } catch (reason) {
    if (seq === loadSeq) error.value = reason instanceof Error ? reason.message : String(reason)
  } finally {
    if (seq === loadSeq) loading.value = false
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

function fmtDelta(value: number): string {
  return value > 0 ? `+${value.toFixed(2)}` : value.toFixed(2)
}

function fmtWinRate(value: number | null): string {
  return value == null ? '-' : `${(value * 100).toFixed(1)}%`
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
        <ol class="king-list">
          <li v-for="player in board.top" :key="player.playerKey">
            <span class="king-rank">{{ player.playerName }}</span>
            <span class="king-value">{{ board.format(board.field(player)) }}</span>
          </li>
        </ol>
        <p v-if="!board.top.length" class="king-empty">暂无数据</p>
      </article>
      <article v-for="board in teamKingBoards" :key="board.key" class="leaderboard-card">
        <h3>{{ board.label }}（战队）</h3>
        <ol class="king-list">
          <li v-for="team in board.top" :key="team.teamId">
            <span class="king-rank">{{ team.teamName }}</span>
            <span class="king-value">{{ board.format(board.field(team)) }}</span>
          </li>
        </ol>
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
            <th>排名</th><th>战队</th><th>Elo 评分</th><th>场次</th><th>胜-负</th><th>评分轨迹</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="team in eloRatings" :key="team.teamId">
            <td class="accent">{{ team.rank }}</td>
            <td>{{ team.teamName }}</td>
            <td class="accent">{{ team.rating }}</td>
            <td>{{ team.games }}</td>
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
        <div class="position-filter" aria-label="涨跌筛选">
          <button class="pos-chip" :class="{ active: versionDirection === 'rising' }" @click="versionDirection = 'rising'">版本答案</button>
          <button class="pos-chip" :class="{ active: versionDirection === 'falling' }" @click="versionDirection = 'falling'">跌出版本</button>
        </div>
      </div>
      <div class="table-scroll">
        <table class="team-table">
          <thead>
            <tr>
              <th>英雄</th><th>起始出场</th><th>结束出场</th><th>出场变化</th>
              <th>起始胜率</th><th>结束胜率</th><th>胜率变化</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in versionBoard" :key="item.championId">
              <td>{{ item.championChineseName || item.championName }}</td>
              <td>{{ item.fromPickCount }}</td>
              <td>{{ item.toPickCount }}</td>
              <td :class="item.pickDelta > 0 ? 'accent' : 'danger-text'">{{ fmtDelta(item.pickDelta) }}</td>
              <td>{{ fmtWinRate(item.fromWinRate) }}</td>
              <td>{{ fmtWinRate(item.toWinRate) }}</td>
              <td :class="item.winRateDelta > 0 ? 'accent' : 'danger-text'">{{ fmtDelta(item.winRateDelta * 100) }}pp</td>
            </tr>
          </tbody>
        </table>
        <p v-if="!versionItems.length" class="detail-notice-inline">选择两个日期后点击对比，查看英雄出场与胜率的版本变化。</p>
        <p v-else-if="!versionBoard.length" class="detail-notice-inline">该方向暂无变化英雄。</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.leaderboard-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 12px; padding: 4px 0 8px;
}
.leaderboard-card {
  border: 1px solid var(--line); border-radius: 10px; padding: 12px 14px; background: var(--panel-2);
}
.leaderboard-card h3 { margin: 0 0 8px; font-size: 13px; color: var(--text-2); }
.king-list { margin: 0; padding: 0; list-style: none; display: grid; gap: 6px; }
.king-list li { display: flex; align-items: center; justify-content: space-between; gap: 8px; font-size: 13px; }
.king-rank { color: var(--text); font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.king-value { color: var(--accent-dark); font-weight: 750; white-space: nowrap; }
.king-empty { margin: 0; color: var(--text-4); font-size: 12px; }
.elo-sparkline { width: 120px; height: 32px; }
.elo-sparkline polyline { fill: none; stroke: var(--accent); stroke-width: 1.6; }
.version-controls { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; padding: 6px 0 12px; }
.version-controls label { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; color: var(--text-2); }
.version-controls input[type='date'] { padding: 5px 8px; border: 1px solid var(--line); border-radius: 6px; }
.error-text { color: var(--danger, #c93c37); }
.danger-text { color: var(--danger, #c93c37); font-weight: 650; }
</style>
