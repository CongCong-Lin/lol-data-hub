<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api, type MatchGameDetailResult, type MatchGamePlayerRecord } from './api'
import { useI18n } from './i18n'

const props = defineProps<{ matchId: string }>()

const route = useRoute()
const { t } = useI18n()

const loading = ref(false)
const error = ref('')
const result = ref<MatchGameDetailResult | null>(null)

const POSITION_LABELS: Record<string, string> = {
  TOP: '上单', JUN: '打野', MID: '中路', BOT: '下路', SUP: '辅助',
}

const queryParams = computed(() => {
  const stageKeys = String(route.query.stageKeys ?? '')
    .split(',')
    .map((key) => key.trim())
    .filter(Boolean)
  return { stageKeys }
})

const matchIdNumber = computed(() => Number(props.matchId))

async function load() {
  const { stageKeys } = queryParams.value
  result.value = null
  if (!Number.isInteger(matchIdNumber.value) || matchIdNumber.value <= 0) {
    error.value = '无效的比赛 ID'
    return
  }
  if (!stageKeys.length) {
    error.value = '链接缺少查询参数（stageKeys），请从对局列表页打开本页'
    return
  }
  const seq = ++loadSeq
  loading.value = true
  error.value = ''
  try {
    const data = await api.matchDetail(matchIdNumber.value, stageKeys)
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

function fmtPercent(rate: number | null | undefined): string {
  if (rate == null) return '-'
  return `${(Number(rate) * 100).toFixed(1)}%`
}

function fmtDecimal(value: number | null | undefined): string {
  return value == null ? '-' : Number(value).toFixed(2)
}

function kdaOf(player: MatchGamePlayerRecord): number {
  return (player.kills + player.assists) / Math.max(player.deaths, 1)
}

function fmtK(value: number | null | undefined): string {
  if (value == null) return '-'
  const number = Number(value)
  if (Math.abs(number) >= 10000) return `${(number / 1000).toFixed(1)}k`
  return String(Math.round(number))
}

interface TeamStatRow {
  label: string
  a: string
  b: string
}

function teamStatsOf(gameNumber: number): TeamStatRow[] {
  const game = gameOf(gameNumber)
  if (!game) return []
  return [
    { label: '助攻', a: String(game.teamAAssists), b: String(game.teamBAssists) },
    { label: '伤害', a: fmtK(game.teamADamage), b: fmtK(game.teamBDamage) },
    { label: '经济', a: fmtK(game.teamAGold), b: fmtK(game.teamBGold) },
    { label: '插眼', a: String(game.teamAWardsPlaced), b: String(game.teamBWardsPlaced) },
    { label: '排眼', a: String(game.teamAWardsKilled), b: String(game.teamBWardsKilled) },
    { label: '补刀', a: String(game.teamAMinionKills), b: String(game.teamBMinionKills) },
    { label: '小龙', a: String(game.teamADragons), b: String(game.teamBDragons) },
    { label: '大龙', a: String(game.teamABarons), b: String(game.teamBBarons) },
    { label: '推塔', a: String(game.teamATurrets), b: String(game.teamBTurrets) },
  ]
}

/** 按小局分组，每局内蓝方在前。 */
interface GameGroup {
  gameNumber: number
  blue: MatchGamePlayerRecord[]
  red: MatchGamePlayerRecord[]
}

const gameGroups = computed<GameGroup[]>(() => {
  if (!result.value) return []
  const groups = new Map<number, GameGroup>()
  for (const player of result.value.players) {
    let group = groups.get(player.gameNumber)
    if (!group) {
      group = { gameNumber: player.gameNumber, blue: [], red: [] }
      groups.set(player.gameNumber, group)
    }
    const teamAId = result.value.games.find((g) => g.gameNumber === player.gameNumber)?.teamAId
    if (player.sourceTeamId === teamAId) group.blue.push(player)
    else group.red.push(player)
  }
  return [...groups.values()].sort((a, b) => a.gameNumber - b.gameNumber)
})

function gameOf(number: number) {
  return result.value?.games.find((game) => game.gameNumber === number)
}

function playerHref(player: MatchGamePlayerRecord): string {
  const detailPosition = player.position === 'JUN' ? 'JUG' : player.position === 'BOT' ? 'AD' : player.position
  const params = new URLSearchParams({
    stageKeys: queryParams.value.stageKeys.join(','),
    position: detailPosition,
    minimumMatchCount: '3',
    returnTo: route.fullPath,
  })
  return `/players/${player.sourcePlayerId}?${params.toString()}`
}

const backPath = computed(() => {
  const candidate = String(route.query.returnTo ?? '')
  if (!candidate.startsWith('/') || candidate.startsWith('//')) return '/matches'
  return candidate
})
</script>

<template>
  <div class="match-detail-page">
    <header class="detail-topbar">
      <RouterLink class="back-link" :to="backPath">{{ t('matchDetail.back') }}</RouterLink>
      <span class="topbar-title">{{ t('matchDetail.title') }} #{{ props.matchId }}</span>
    </header>

    <div v-if="loading" class="detail-notice">{{ t('common.loading') }}</div>
    <div v-else-if="error" class="detail-notice detail-error">{{ error }}</div>

    <template v-else-if="result">
      <section v-for="group in gameGroups" :key="group.gameNumber" class="detail-card">
        <div class="game-heading">
          <div class="game-title">
            <h2 class="detail-heading">{{ t('matchDetail.gameOfSeries', { n: group.gameNumber }) }}</h2>
            <p class="detail-subheading">
              {{ fmtDateTime(gameOf(group.gameNumber)?.startTime ?? null) }}
              · {{ fmtDuration(gameOf(group.gameNumber)?.gameDurationSeconds ?? 0) }}
            </p>
          </div>
          <div class="score-board">
            <div class="score-side">
              <img v-if="gameOf(group.gameNumber)?.teamALogo" :src="gameOf(group.gameNumber)?.teamALogo ?? undefined" :alt="gameOf(group.gameNumber)?.teamAName" class="team-logo" />
              <strong>{{ gameOf(group.gameNumber)?.teamAName }}</strong>
              <span v-if="gameOf(group.gameNumber)?.teamAFirstBlood" class="fb-tag">FB</span>
            </div>
            <div class="score-mid">
              <strong>{{ gameOf(group.gameNumber)?.teamAKills }} : {{ gameOf(group.gameNumber)?.teamBKills }}</strong>
              <span v-if="gameOf(group.gameNumber)" class="winner-tag" :class="gameOf(group.gameNumber)?.winnerTeamId === gameOf(group.gameNumber)?.teamAId ? 'won-a' : 'won-b'">
                {{ gameOf(group.gameNumber)?.winnerTeamId === gameOf(group.gameNumber)?.teamAId ? gameOf(group.gameNumber)?.teamAName : gameOf(group.gameNumber)?.winnerTeamId === gameOf(group.gameNumber)?.teamBId ? gameOf(group.gameNumber)?.teamBName : t('matches.win') }}
              </span>
            </div>
            <div class="score-side">
              <img v-if="gameOf(group.gameNumber)?.teamBLogo" :src="gameOf(group.gameNumber)?.teamBLogo ?? undefined" :alt="gameOf(group.gameNumber)?.teamBName" class="team-logo" />
              <strong>{{ gameOf(group.gameNumber)?.teamBName }}</strong>
              <span v-if="gameOf(group.gameNumber)?.teamBFirstBlood" class="fb-tag">FB</span>
            </div>
          </div>
        </div>

        <div class="table-scroll">
          <table class="detail-table">
            <thead>
              <tr>
                <th>{{ t('matchDetail.position') }}</th>
                <th>{{ t('matchDetail.player') }}</th>
                <th>{{ t('matchDetail.champion') }}</th>
                <th>{{ t('matchDetail.kills') }}</th>
                <th>{{ t('matchDetail.deaths') }}</th>
                <th>{{ t('matchDetail.assists') }}</th>
                <th>{{ t('matchDetail.kda') }}</th>
                <th>{{ t('matchDetail.damage') }}</th>
                <th>{{ t('matchDetail.damagePercent') }}</th>
                <th>{{ t('matchDetail.gold') }}</th>
                <th>{{ t('matchDetail.kp') }}</th>
                <th>结果</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="(player, index) in [...group.blue, ...group.red]" :key="`${player.gameNumber}:${player.sourcePlayerId}`">
                <tr :class="{ 'team-divider': index === 5 }">
                  <td><span class="pos-badge">{{ positionLabel(player.position) }}</span></td>
                  <td>
                    <a class="player-link" :href="playerHref(player)">
                      <span class="player-placeholder player-avatar">{{ player.playerName.slice(0, 1) }}</span>
                      <strong>{{ player.playerName }}</strong>
                    </a>
                  </td>
                  <td>
                    <div class="hero-cell">
                      <img v-if="player.championLogo" :src="player.championLogo" :alt="player.championName" class="hero-logo" />
                      <span class="champion-placeholder hero-logo" v-else>{{ player.championChineseName.slice(0, 1) }}</span>
                      <span>
                        <strong>{{ player.championChineseName }}</strong>
                        <small>{{ player.championName }}</small>
                      </span>
                    </div>
                  </td>
                  <td>{{ player.kills }}</td>
                  <td>{{ player.deaths }}</td>
                  <td>{{ player.assists }}</td>
                  <td class="accent">{{ fmtDecimal(kdaOf(player)) }}</td>
                  <td>{{ fmtDecimal(player.heroDamage) }}</td>
                  <td>{{ fmtPercent(player.damagePercent) }}</td>
                  <td>{{ fmtDecimal(player.playerGold) }}</td>
                  <td>{{ fmtPercent(player.killParticipantPercent) }}</td>
                  <td>
                    <span class="result-badge" :class="player.won ? 'won' : 'lost'">
                      {{ player.won ? t('matches.win') : t('matches.loss') }}
                    </span>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
        <div class="team-stats">
          <div class="team-stats-head">
            <span class="team-stats-a">{{ gameOf(group.gameNumber)?.teamAName }}</span>
            <span class="team-stats-title">团队数据</span>
            <span class="team-stats-b">{{ gameOf(group.gameNumber)?.teamBName }}</span>
          </div>
          <div v-for="stat in teamStatsOf(group.gameNumber)" :key="stat.label" class="team-stats-row">
            <span class="team-stats-a">{{ stat.a }}</span>
            <span class="team-stats-title">{{ stat.label }}</span>
            <span class="team-stats-b">{{ stat.b }}</span>
          </div>
        </div>
        <p class="game-footnote">{{ t('matchDetail.playersNote') }}</p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.match-detail-page { max-width: 1240px; margin: 0 auto; padding: 16px 20px 40px; }
.detail-topbar { display: flex; align-items: center; gap: 14px; padding: 8px 0 14px; }
.back-link { color: var(--accent); text-decoration: none; font-weight: 600; }
.back-link:hover { text-decoration: underline; }
.topbar-title { color: var(--text-4); font-size: 13px; }
.detail-card { background: var(--panel); border: 1px solid var(--line); border-radius: 10px; padding: 18px 20px; margin-bottom: 16px; }
.detail-heading { margin: 0 0 6px; font-size: 16px; }
.detail-subheading { margin: 0 0 12px; color: var(--text-4); font-size: 12.5px; }
.detail-notice { background: var(--panel); border: 1px solid var(--line); border-radius: 10px; padding: 26px 20px; text-align: center; color: var(--text-3); }
.detail-error { color: var(--danger); }
.game-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; flex-wrap: wrap; margin-bottom: 12px; }
.score-board { display: flex; align-items: center; gap: 14px; }
.score-side { display: flex; align-items: center; gap: 7px; min-width: 0; }
.score-side strong { white-space: nowrap; }
.team-logo { width: 28px; height: 28px; border-radius: 4px; object-fit: contain; }
.score-mid { display: flex; flex-direction: column; align-items: center; gap: 4px; min-width: 90px; }
.score-mid > strong { font-size: 18px; font-variant-numeric: tabular-nums; }
.fb-tag { padding: 1px 5px; border-radius: 4px; font-size: 10px; font-weight: 700; color: #b07d0e; background: #fdf3d7; }
.winner-tag { display: inline-block; max-width: 180px; overflow: hidden; text-overflow: ellipsis; padding: 1px 8px; border-radius: 999px; font-size: 11px; font-weight: 700; }
.winner-tag.won-a { color: var(--accent-dark); background: var(--accent-soft); }
.winner-tag.won-b { color: var(--danger); background: var(--danger-soft); }
.table-scroll { max-height: 520px; overflow: auto; }
.detail-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.detail-table th, .detail-table td { padding: 7px 10px; border-bottom: 1px solid var(--line); text-align: left; white-space: nowrap; }
.detail-table thead th { color: var(--text-3); font-size: 12px; background: var(--th-bg); }
.detail-table td.accent { color: var(--accent-dark); font-weight: 650; }
.detail-table tr.team-divider td { border-top: 2px solid var(--line-strong); }
.pos-badge {
  display: inline-block; padding: 2px 8px; border: 1px solid var(--accent-line); border-radius: 999px;
  color: var(--accent-dark); background: var(--accent-soft); font-size: 11px; font-weight: 650;
}
.player-link { display: inline-flex; align-items: center; gap: 8px; color: inherit; text-decoration: none; }
.player-link:hover strong { color: var(--accent); }
.player-avatar { width: 26px; height: 26px; border-radius: 50%; object-fit: cover; }
.hero-cell { display: flex; align-items: center; gap: 8px; }
.hero-logo { width: 26px; height: 26px; border-radius: 50%; object-fit: cover; }
.hero-cell strong { display: block; }
.hero-cell small { display: block; color: var(--text-4); font-size: 11px; }
.result-badge { display: inline-block; min-width: 26px; padding: 1px 8px; border-radius: 999px; text-align: center; font-size: 12px; font-weight: 700; }
.result-badge.won { color: var(--accent-dark); background: var(--accent-soft); }
.result-badge.lost { color: var(--danger); background: var(--danger-soft); }
.game-footnote { margin: 10px 0 0; color: var(--text-4); font-size: 12px; }
.team-stats { margin-top: 12px; border: 1px solid var(--line); border-radius: 8px; overflow: hidden; }
.team-stats-head, .team-stats-row {
  display: grid; grid-template-columns: 1fr 110px 1fr; align-items: center;
  padding: 6px 12px; font-size: 12px; font-variant-numeric: tabular-nums;
}
.team-stats-head { background: var(--th-bg); font-weight: 700; color: var(--text-3); }
.team-stats-row { border-top: 1px solid var(--line); color: var(--text-2); }
.team-stats-title { text-align: center; color: var(--muted); font-size: 11.5px; }
.team-stats-a { font-weight: 650; }
.team-stats-b { font-weight: 650; text-align: right; }
</style>
