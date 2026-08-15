<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api, type TeamDetailStatisticsResult } from './api'
import { useI18n } from './i18n'

const props = defineProps<{ teamId: string }>()

const route = useRoute()
const { t } = useI18n()

const loading = ref(false)
const error = ref('')
const result = ref<TeamDetailStatisticsResult | null>(null)

const POSITION_LABELS: Record<string, string> = {
  TOP: '上单', JUN: '打野', MID: '中路', BOT: '下路', SUP: '辅助',
}

const queryParams = computed(() => {
  const stageKeys = String(route.query.stageKeys ?? '')
    .split(',')
    .map((key) => key.trim())
    .filter(Boolean)
  const parsedMinimum = Number(route.query.minimumMatchCount ?? '5')
  const minimumMatchCount = Number.isInteger(parsedMinimum) ? parsedMinimum : 5
  return { stageKeys, minimumMatchCount }
})

const teamIdNumber = computed(() => Number(props.teamId))

async function load() {
  const { stageKeys, minimumMatchCount } = queryParams.value
  result.value = null
  if (!Number.isInteger(teamIdNumber.value) || teamIdNumber.value <= 0) {
    error.value = '无效的战队 ID'
    return
  }
  if (!stageKeys.length) {
    error.value = '链接缺少查询参数（stageKeys），请从战队统计表格的战队名称入口打开本页'
    return
  }
  const seq = ++loadSeq
  loading.value = true
  error.value = ''
  try {
    const data = await api.teamDetail(teamIdNumber.value, stageKeys, minimumMatchCount)
    if (seq === loadSeq) result.value = data
  } catch (reason) {
    if (seq === loadSeq) error.value = reason instanceof Error ? reason.message : String(reason)
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}
let loadSeq = 0

watch(queryParams, load, { immediate: true, deep: true })

function positionLabel(position: string): string {
  return POSITION_LABELS[position] ?? position
}

function fmtPercent(rate: number | null | undefined): string {
  if (rate == null) return '-'
  return `${(Number(rate) * 100).toFixed(1)}%`
}

function fmtDateTime(value: string | null): string {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ').slice(0, 16)
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function fmtDuration(seconds: number): string {
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

const stageKeysLabel = computed(() => queryParams.value.stageKeys.join('、'))

const returnPath = computed(() => {
  const candidate = String(route.query.returnTo ?? '')
  if (!candidate.startsWith('/') || candidate.startsWith('//')) return '/'
  return candidate
})

/** 近期对局按当前战队视角整理：对手名称、己方/对手击杀、是否获胜。 */
interface RecentGameView {
  matchId: number
  startTime: string | null
  opponentName: string
  opponentLogo: string | null
  myKills: number
  opponentKills: number
  won: boolean
  gameNumber: number
  durationSeconds: number
}

const recentGameViews = computed<RecentGameView[]>(() => {
  const teamId = teamIdNumber.value
  return (result.value?.recentGames ?? []).map((game) => {
    const mineIsA = game.teamAId === teamId
    return {
      matchId: game.sourceMatchId,
      startTime: game.startTime,
      opponentName: mineIsA ? game.teamBName : game.teamAName,
      opponentLogo: mineIsA ? game.teamBLogo : game.teamALogo,
      myKills: mineIsA ? game.teamAKills : game.teamBKills,
      opponentKills: mineIsA ? game.teamBKills : game.teamAKills,
      won: game.winnerTeamId === teamId,
      gameNumber: game.gameNumber,
      durationSeconds: game.gameDurationSeconds,
    }
  })
})

function matchHref(game: RecentGameView): string {
  const params = new URLSearchParams({ stageKeys: queryParams.value.stageKeys.join(',') })
  return `/matches/${game.matchId}?${params.toString()}`
}

function playerHref(sourcePlayerId: number, position: string): string {
  const detailPosition = position === 'JUN' ? 'JUG' : position === 'BOT' ? 'AD' : position
  const params = new URLSearchParams({
    stageKeys: queryParams.value.stageKeys.join(','),
    position: detailPosition,
    minimumMatchCount: String(queryParams.value.minimumMatchCount),
    returnTo: route.fullPath,
  })
  return `/players/${sourcePlayerId}?${params.toString()}`
}
</script>

<template>
  <div class="team-detail-page">
    <header class="detail-topbar">
      <RouterLink class="back-link" :to="returnPath">{{ t('teamDetail.back') }}</RouterLink>
      <span class="topbar-title">{{ t('teamDetail.title') }}</span>
    </header>

    <div v-if="loading" class="detail-notice">{{ t('common.loading') }}</div>
    <div v-else-if="error" class="detail-notice detail-error">{{ error }}</div>

    <template v-else-if="result">
      <section class="detail-card profile-card">
        <img v-if="result.team.teamLogo" :src="result.team.teamLogo" :alt="result.team.teamName" class="profile-logo" />
        <span v-else class="profile-logo profile-placeholder">{{ result.team.teamName.slice(0, 1) }}</span>
        <div class="profile-info">
          <h1 class="profile-name">{{ result.team.teamName }}</h1>
          <p class="profile-meta">
            {{ t('teamDetail.matches') }} {{ result.team.matchCount }} ·
            {{ t('teamDetail.games') }} {{ result.team.gameCount }} ·
            {{ t('teamDetail.wins') }} {{ result.team.matchWinCount }}
          </p>
          <p class="profile-meta muted">
            统计范围：{{ stageKeysLabel }} · 最低样本 {{ result.minimumMatchCount }} 场
            · {{ t('common.dataVersion', { n: result.dataVersion }) }}
            · {{ t('common.updatedAt', { time: fmtDateTime(result.latestCollectedAt) }) }}
          </p>
        </div>
      </section>

      <div v-if="result.cohortSize <= 1" class="detail-warning">
        当前查询条件下合格战队仅 {{ result.cohortSize }} 支，排名样本不足，仅供参考。
      </div>

      <section class="detail-card core-metrics-card">
        <div class="core-metrics-heading">
          <div>
            <h2 class="detail-heading">{{ t('teamDetail.coreMetrics') }}</h2>
            <p class="detail-subheading">{{ t('teamDetail.cohort', { n: result.cohortSize }) }}</p>
          </div>
          <span class="core-metrics-note">{{ t('teamDetail.rankNote') }}</span>
        </div>
        <div class="core-metrics-list">
          <article v-for="metric in result.coreMetrics" :key="metric.key" class="core-metric-item">
            <div class="core-metric-label">{{ metric.label }}</div>
            <div class="core-metric-value">{{ metric.formattedValue }}</div>
            <div class="core-metric-rank">{{ t('teamDetail.rank', { rank: metric.rank }) }}<span> {{ t('teamDetail.rankSuffix', { n: metric.cohortSize }) }}</span></div>
          </article>
        </div>
      </section>

      <section class="detail-card">
        <h2 class="detail-heading">{{ t('teamDetail.lineup') }}</h2>
        <p class="detail-subheading">{{ t('teamDetail.lineupNote') }}</p>
        <template v-if="result.lineupPreferences.length">
          <div class="table-scroll lineup-table">
            <table class="detail-table">
              <thead>
                <tr>
                  <th>{{ t('teamDetail.position') }}</th>
                  <th>英雄</th>
                  <th>出场</th>
                  <th>{{ t('teamDetail.pickRate') }}</th>
                  <th>胜场</th>
                  <th>{{ t('teamDetail.winningRate') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="pref in result.lineupPreferences" :key="`${pref.position}:${pref.sourceChampionId}`">
                  <td>
                    <span class="pos-badge">{{ positionLabel(pref.position) }}</span>
                  </td>
                  <td>
                    <div class="hero-cell">
                      <img v-if="pref.championLogo" :src="pref.championLogo" :alt="pref.championName" class="hero-logo" />
                      <span class="champion-placeholder hero-logo" v-else>{{ pref.championChineseName.slice(0, 1) }}</span>
                      <span>
                        <strong>{{ pref.championChineseName }}</strong>
                        <small>{{ pref.championName }}</small>
                      </span>
                    </div>
                  </td>
                  <td>{{ pref.pickCount }}</td>
                  <td>{{ fmtPercent(pref.pickRate) }}</td>
                  <td>{{ pref.winningCount }}</td>
                  <td class="accent">{{ fmtPercent(pref.winningRate) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
        <p v-else class="detail-notice-inline">{{ t('teamDetail.noRecentGames') }}</p>
      </section>

      <section class="detail-card">
        <h2 class="detail-heading">{{ t('teamDetail.players') }}</h2>
        <p class="detail-subheading">{{ t('teamDetail.playersNote') }}</p>
        <template v-if="result.players.length">
          <div class="table-scroll roster-table">
            <table class="detail-table">
              <thead>
                <tr>
                  <th>{{ t('matchDetail.player') }}</th>
                  <th>{{ t('teamDetail.position') }}</th>
                  <th>{{ t('teamDetail.matches') }}</th>
                  <th>{{ t('teamDetail.games') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="player in result.players" :key="`${player.sourcePlayerId}:${player.position}`">
                  <td>
                    <a class="player-link" :href="playerHref(player.sourcePlayerId, player.position)">
                      <img v-if="player.playerAvatar" :src="player.playerAvatar" :alt="player.playerName" class="player-avatar" />
                      <span class="player-placeholder player-avatar" v-else>{{ player.playerName.slice(0, 1) }}</span>
                      <strong>{{ player.playerName }}</strong>
                    </a>
                  </td>
                  <td><span class="pos-badge">{{ positionLabel(player.position) }}</span></td>
                  <td>{{ player.matchCount }}</td>
                  <td>{{ player.gameCount }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
        <p v-else class="detail-notice-inline">{{ t('teamDetail.noRecentGames') }}</p>
      </section>

      <section class="detail-card">
        <h2 class="detail-heading">{{ t('teamDetail.recentGames') }}</h2>
        <p class="detail-subheading">{{ t('teamDetail.recentGamesNote', { n: recentGameViews.length }) }}</p>
        <template v-if="recentGameViews.length">
          <div class="table-scroll recent-table">
            <table class="detail-table">
              <thead>
                <tr>
                  <th>时间</th>
                  <th>对手</th>
                  <th>比分</th>
                  <th>结果</th>
                  <th>{{ t('matches.duration') }}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="game in recentGameViews" :key="`${game.matchId}:${game.gameNumber}`">
                  <td>{{ fmtDateTime(game.startTime) }}</td>
                  <td>
                    <div class="team-cell">
                      <img v-if="game.opponentLogo" :src="game.opponentLogo" :alt="game.opponentName" class="team-logo" />
                      <span class="team-placeholder team-logo" v-else>{{ game.opponentName.slice(0, 1) }}</span>
                      <strong>{{ game.opponentName }}</strong>
                    </div>
                  </td>
                  <td>{{ game.myKills }} : {{ game.opponentKills }}</td>
                  <td>
                    <span class="result-badge" :class="game.won ? 'won' : 'lost'">
                      {{ game.won ? t('teamDetail.win') : t('teamDetail.loss') }}
                    </span>
                  </td>
                  <td>{{ fmtDuration(game.durationSeconds) }}</td>
                  <td>
                    <a class="view-link" :href="matchHref(game)">{{ t('playerGames.viewMatch') }}</a>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
        <p v-else class="detail-notice-inline">{{ t('teamDetail.noRecentGames') }}</p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.team-detail-page { max-width: 1180px; margin: 0 auto; padding: 16px 20px 40px; }
.detail-topbar { display: flex; align-items: center; gap: 14px; padding: 8px 0 14px; }
.back-link { color: var(--accent); text-decoration: none; font-weight: 600; }
.back-link:hover { text-decoration: underline; }
.topbar-title { color: var(--text-4); font-size: 13px; }
.detail-card { background: var(--panel); border: 1px solid var(--line); border-radius: 10px; padding: 18px 20px; margin-bottom: 16px; }
.detail-heading { margin: 0 0 6px; font-size: 16px; }
.detail-subheading { margin: 0 0 12px; color: var(--text-4); font-size: 12.5px; }
.detail-notice { background: var(--panel); border: 1px solid var(--line); border-radius: 10px; padding: 26px 20px; text-align: center; color: var(--text-3); }
.detail-error { color: var(--danger); }
.detail-warning { background: #fff8e6; border: 1px solid #f0dfa8; border-radius: 10px; padding: 10px 14px; margin-bottom: 16px; color: #7a5b00; font-size: 13px; }
.detail-notice-inline { color: var(--text-3); font-size: 13px; }
.profile-card { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.profile-logo { width: 64px; height: 64px; border-radius: 12px; object-fit: contain; background: var(--placeholder-bg); }
.profile-placeholder { display: grid; place-items: center; color: var(--accent); font-weight: 750; font-size: 26px; }
.profile-info { flex: 1 1 320px; min-width: 0; }
.profile-name { margin: 0; font-size: 20px; }
.profile-meta { margin: 4px 0 0; font-size: 13px; color: var(--text); }
.profile-meta.muted { color: var(--text-4); font-size: 12px; }
.core-metrics-card { width: 100%; box-sizing: border-box; }
.core-metrics-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.core-metrics-note { color: var(--text-4); font-size: 11px; white-space: nowrap; }
.core-metrics-list { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 0; margin-top: 12px; border-top: 1px solid var(--line); }
.core-metric-item { min-width: 0; padding: 12px 12px 10px; border-right: 1px solid var(--line); border-bottom: 1px solid var(--line); }
.core-metric-item:nth-child(5n) { border-right: 0; }
.core-metric-item:nth-last-child(-n + 5) { border-bottom: 0; }
.core-metric-item:nth-child(5n + 1) { padding-left: 0; }
.core-metric-item:nth-child(5n) { padding-right: 0; }
.core-metric-label { color: var(--text-3); font-size: 12px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.core-metric-value { margin-top: 6px; color: var(--accent); font-size: 18px; font-weight: 700; line-height: 1.2; white-space: nowrap; }
.core-metric-rank { margin-top: 5px; color: var(--text); font-size: 11px; white-space: nowrap; }
.core-metric-rank span { color: var(--text-4); }
@media (max-width: 700px) {
  .core-metrics-heading { display: block; }
  .core-metrics-note { display: block; margin-top: 4px; }
  .core-metrics-list { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .core-metric-item { padding: 10px 8px; }
  .core-metric-item:nth-child(5n) { border-right: 1px solid var(--line); padding-right: 8px; }
  .core-metric-item:nth-child(3n) { border-right: 0; padding-right: 8px; }
  .core-metric-item:nth-last-child(-n + 5) { border-bottom: 1px solid var(--line); }
  .core-metric-item:nth-last-child(-n + 3) { border-bottom: 0; }
  .core-metric-item:nth-child(5n + 1) { padding-left: 8px; }
}
.detail-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.detail-table th, .detail-table td { padding: 8px 10px; border-bottom: 1px solid var(--line); text-align: left; white-space: nowrap; }
.detail-table thead th { color: var(--text-3); font-size: 12px; background: var(--th-bg); }
.detail-table td.accent { color: var(--accent-dark); font-weight: 650; }
.table-scroll { max-height: 460px; overflow: auto; }
.pos-badge {
  display: inline-block; padding: 2px 8px; border: 1px solid var(--accent-line); border-radius: 999px;
  color: var(--accent-dark); background: var(--accent-soft); font-size: 11px; font-weight: 650;
}
.hero-cell { display: flex; align-items: center; gap: 8px; }
.hero-logo { width: 28px; height: 28px; border-radius: 50%; object-fit: cover; }
.hero-cell strong { display: block; }
.hero-cell small { display: block; color: var(--text-4); font-size: 11px; }
.player-link { display: inline-flex; align-items: center; gap: 8px; color: inherit; text-decoration: none; }
.player-link:hover strong { color: var(--accent); }
.player-avatar { width: 28px; height: 28px; border-radius: 50%; object-fit: cover; }
.team-cell { display: flex; align-items: center; gap: 8px; }
.team-logo { width: 28px; height: 28px; border-radius: 4px; object-fit: contain; }
.result-badge { display: inline-block; min-width: 30px; padding: 2px 9px; border-radius: 999px; text-align: center; font-size: 12px; font-weight: 700; }
.result-badge.won { color: var(--accent-dark); background: var(--accent-soft); }
.result-badge.lost { color: var(--danger); background: var(--danger-soft); }
.view-link { color: var(--accent); text-decoration: none; font-weight: 600; }
.view-link:hover { text-decoration: underline; }
</style>
