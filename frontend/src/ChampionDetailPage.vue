<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, type ChampionCounterResult, type ChampionDetailStatisticsResult } from './api'
import ChampionTrendChart from './ChampionTrendChart.vue'
import { useI18n } from './i18n'

const props = defineProps<{ championId: string }>()

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const loading = ref(false)
const error = ref('')
const result = ref<ChampionDetailStatisticsResult | null>(null)
const counters = ref<ChampionCounterResult | null>(null)

const POSITION_LABELS: Record<string, string> = {
  TOP: '上单', JUN: '打野', MID: '中路', BOT: '下路', SUP: '辅助',
}

const queryParams = computed(() => {
  const stageKeys = String(route.query.stageKeys ?? '')
    .split(',')
    .map((key) => key.trim())
    .filter(Boolean)
  const position = String(route.query.position ?? '')
  const parsedMinimum = Number(route.query.minimumPickCount ?? '5')
  const minimumPickCount = Number.isInteger(parsedMinimum) ? parsedMinimum : 5
  return { stageKeys, position, minimumPickCount }
})

const championIdNumber = computed(() => Number(props.championId))

async function load() {
  const { stageKeys, position, minimumPickCount } = queryParams.value
  result.value = null
  counters.value = null
  if (!Number.isInteger(championIdNumber.value) || championIdNumber.value <= 0) {
    error.value = '无效的英雄 ID'
    return
  }
  if (!stageKeys.length) {
    error.value = '链接缺少查询参数（stageKeys），请从英雄统计表格的英雄名称入口打开本页'
    return
  }
  const seq = ++loadSeq
  loading.value = true
  error.value = ''
  try {
    const data = await api.championDetail(championIdNumber.value, stageKeys, minimumPickCount, position)
    if (seq !== loadSeq) return
    result.value = data
    if (position) {
      try {
        const counterData = await api.championCounters(championIdNumber.value, stageKeys, position)
        if (seq === loadSeq) counters.value = counterData
      } catch {
        /* 对位克制依赖逐局阵容数据，未采集时静默降级 */
      }
    }
  } catch (reason) {
    if (seq === loadSeq) error.value = reason instanceof Error ? reason.message : String(reason)
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}
let loadSeq = 0

watch(queryParams, load, { immediate: true, deep: true })

function switchPosition(position: string) {
  if (position === queryParams.value.position) return
  void router.replace({ query: { ...route.query, position } })
}

function positionLabel(position: string): string {
  return POSITION_LABELS[position] ?? position
}

function fmtPercent(rate: number | null | undefined): string {
  if (rate == null) return '-'
  return `${(Number(rate) * 100).toFixed(1)}%`
}

function fmtDecimal(value: number | null | undefined, digits = 2): string {
  return value == null ? '-' : Number(value).toFixed(digits)
}

function fmtDateTime(value: string | null): string {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ').slice(0, 16)
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const stageKeysLabel = computed(() => queryParams.value.stageKeys.join('、'))

const strongAgainst = computed(() =>
  [...(counters.value?.opponents ?? [])]
    .sort((a, b) => b.winRate - a.winRate)
    .slice(0, 5),
)

const weakAgainst = computed(() =>
  [...(counters.value?.opponents ?? [])]
    .sort((a, b) => a.winRate - b.winRate)
    .slice(0, 5),
)

const returnPath = computed(() => {
  const candidate = String(route.query.returnTo ?? '')
  if (!candidate.startsWith('/') || candidate.startsWith('//')) return '/'
  return candidate
})

/** 逐局明细中的位置代码与选手详情页的位置代码不同（JUN→JUG、BOT→AD）。 */
function playerHref(sourcePlayerId: number, position: string): string {
  const detailPosition = position === 'JUN' ? 'JUG' : position === 'BOT' ? 'AD' : position
  const params = new URLSearchParams({
    stageKeys: queryParams.value.stageKeys.join(','),
    position: detailPosition,
    minimumMatchCount: '3',
    returnTo: route.fullPath,
  })
  return `/players/${sourcePlayerId}?${params.toString()}`
}
</script>

<template>
  <div class="champion-detail-page">
    <header class="detail-topbar">
      <RouterLink class="back-link" :to="returnPath">{{ t('championDetail.back') }}</RouterLink>
      <span class="topbar-title">{{ t('championDetail.title') }}</span>
    </header>

    <div v-if="loading" class="detail-notice">{{ t('common.loading') }}</div>
    <div v-else-if="error" class="detail-notice detail-error">{{ error }}</div>

    <template v-else-if="result">
      <section class="detail-card profile-card">
        <img v-if="result.champion.championLogo" :src="result.champion.championLogo" :alt="result.champion.championName" class="profile-logo" />
        <span v-else class="profile-logo profile-placeholder">{{ (result.champion.championChineseName || result.champion.championName).slice(0, 1) }}</span>
        <div class="profile-info">
          <h1 class="profile-name">{{ result.champion.championChineseName || result.champion.championName }}</h1>
          <p class="profile-meta">
            {{ result.champion.championName }}
            <template v-if="result.champion.championTitle"> · {{ result.champion.championTitle }}</template>
          </p>
          <p class="profile-meta muted">
            统计范围：{{ stageKeysLabel }}{{ queryParams.position ? ` · 位置 ${positionLabel(queryParams.position)}` : '' }}
            · 最低出场 {{ result.minimumPickCount }} 次
            · {{ t('common.dataVersion', { n: result.dataVersion }) }}
            · {{ t('common.updatedAt', { time: fmtDateTime(result.latestCollectedAt) }) }}
          </p>
        </div>
        <div v-if="result.champion.positions.length > 1" class="position-tabs">
          <button
            v-for="position in result.champion.positions"
            :key="position"
            type="button"
            class="position-tab"
            :class="{ active: position === queryParams.position }"
            @click="switchPosition(position)"
          >{{ positionLabel(position) }}</button>
        </div>
      </section>

      <section class="detail-card core-metrics-card">
        <div class="core-metrics-heading">
          <div>
            <h2 class="detail-heading">{{ t('championDetail.overall') }}</h2>
            <p class="detail-subheading">{{ t('championDetail.overallNote') }}</p>
          </div>
          <span class="core-metrics-note">{{ t('championDetail.totalGames', { n: result.overall.sampleBaseCount }) }}</span>
        </div>
        <div class="core-metrics-list">
          <article class="core-metric-item">
            <div class="core-metric-label">{{ t('championDetail.pickCount') }}</div>
            <div class="core-metric-value">{{ result.overall.pickCount }}</div>
            <div class="core-metric-rank">{{ t('championDetail.games') }} {{ result.overall.sampleBaseCount }}</div>
          </article>
          <article class="core-metric-item">
            <div class="core-metric-label">{{ t('championDetail.pickRate') }}</div>
            <div class="core-metric-value">{{ fmtPercent(result.overall.pickRate) }}</div>
            <div class="core-metric-rank">{{ t('championDetail.banCount') }} {{ result.overall.banCount }}</div>
          </article>
          <article class="core-metric-item">
            <div class="core-metric-label">{{ t('championDetail.banRate') }}</div>
            <div class="core-metric-value">{{ fmtPercent(result.overall.banRate) }}</div>
            <div class="core-metric-rank">{{ t('championDetail.bpRate') }} {{ fmtPercent(result.overall.bpRate) }}</div>
          </article>
          <article class="core-metric-item">
            <div class="core-metric-label">{{ t('championDetail.winningCount') }}</div>
            <div class="core-metric-value">{{ result.overall.winningCount }}</div>
            <div class="core-metric-rank">{{ t('championDetail.winningRate') }} {{ fmtPercent(result.overall.winningRate) }}</div>
          </article>
          <article class="core-metric-item">
            <div class="core-metric-label">KDA</div>
            <div class="core-metric-value">{{ fmtDecimal(result.overall.kda) }}</div>
            <div class="core-metric-rank">{{ result.overall.totalKills }} / {{ result.overall.totalDeaths }} / {{ result.overall.totalAssists }}</div>
          </article>
          <article class="core-metric-item">
            <div class="core-metric-label">{{ t('championDetail.killPerGame') }}</div>
            <div class="core-metric-value">{{ fmtDecimal(result.overall.killPerGame) }}</div>
            <div class="core-metric-rank">{{ t('championDetail.deathPerGame') }} {{ fmtDecimal(result.overall.deathPerGame) }}</div>
          </article>
          <article class="core-metric-item">
            <div class="core-metric-label">{{ t('championDetail.assistPerGame') }}</div>
            <div class="core-metric-value">{{ fmtDecimal(result.overall.assistPerGame) }}</div>
            <div class="core-metric-rank">&nbsp;</div>
          </article>
          <article class="core-metric-item">
            <div class="core-metric-label">{{ t('championDetail.mostUsedPlayers') }}</div>
            <div class="core-metric-value core-metric-value-text">{{ result.overall.mostUsedPlayers.join('、') || '—' }}</div>
            <div class="core-metric-rank">&nbsp;</div>
          </article>
        </div>
      </section>

      <div class="detail-columns">
        <section class="detail-card">
          <h2 class="detail-heading">{{ t('championDetail.positionStats') }}</h2>
          <p class="detail-subheading">{{ t('championDetail.positionStatsNote') }}</p>
          <template v-if="result.positionStats.length">
            <div class="table-scroll">
              <table class="detail-table">
                <thead>
                  <tr>
                    <th>{{ t('teamDetail.position') }}</th>
                    <th>{{ t('championDetail.pickCount') }}</th>
                    <th>{{ t('championDetail.winningCount') }}</th>
                    <th>出场占比</th>
                    <th>{{ t('championDetail.winningRate') }}</th>
                    <th>KDA</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="stat in result.positionStats" :key="stat.position">
                    <td><span class="pos-badge">{{ positionLabel(stat.position) }}</span></td>
                    <td>{{ stat.pickCount }}</td>
                    <td>{{ stat.winningCount }}</td>
                    <td>{{ fmtPercent(stat.pickRate) }}</td>
                    <td class="accent">{{ fmtPercent(stat.winningRate) }}</td>
                    <td class="accent">{{ fmtDecimal(stat.kda) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </template>
          <p v-else class="detail-notice-inline">所选位置暂无分路数据</p>
        </section>

        <section class="detail-card">
          <h2 class="detail-heading">{{ t('championDetail.topPlayers') }}</h2>
          <p class="detail-subheading">{{ t('championDetail.topPlayersNote', { n: result.minimumPickCount }) }}</p>
          <template v-if="result.topPlayers.length">
            <div class="table-scroll">
              <table class="detail-table">
                <thead>
                  <tr>
                    <th>{{ t('matchDetail.player') }}</th>
                    <th>{{ t('teamDetail.position') }}</th>
                    <th>{{ t('championDetail.pickCount') }}</th>
                    <th>{{ t('championDetail.winningCount') }}</th>
                    <th>{{ t('championDetail.winningRate') }}</th>
                    <th>KDA</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="player in result.topPlayers" :key="`${player.sourcePlayerId}:${player.position}`">
                    <td>
                      <a class="player-link" :href="playerHref(player.sourcePlayerId, player.position)">
                        <img v-if="player.playerAvatar" :src="player.playerAvatar" :alt="player.playerName" class="player-avatar" />
                        <span class="player-placeholder player-avatar" v-else>{{ player.playerName.slice(0, 1) }}</span>
                        <strong>{{ player.playerName }}</strong>
                      </a>
                    </td>
                    <td><span class="pos-badge">{{ positionLabel(player.position) }}</span></td>
                    <td>{{ player.pickCount }}</td>
                    <td>{{ player.winningCount }}</td>
                    <td class="accent">{{ fmtPercent(player.winningRate) }}</td>
                    <td class="accent">{{ fmtDecimal(player.kda) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </template>
          <p v-else class="detail-notice-inline">所选位置暂无满足最低出场次数的选手</p>
        </section>
      </div>

      <section class="detail-card">
        <h2 class="detail-heading">{{ t('championDetail.trends') }}</h2>
        <p class="detail-subheading">{{ t('championDetail.trendsNote') }}</p>
        <ChampionTrendChart v-if="result.trends.length" :trends="result.trends" />
        <p v-else class="detail-notice-inline">{{ t('championDetail.noTrends') }}</p>
      </section>

      <section v-if="counters && counters.opponents.length" class="detail-card">
        <h2 class="detail-heading">{{ t('championDetail.counters') }}</h2>
        <p class="detail-subheading">{{ t('championDetail.countersNote', { n: counters.totalGames }) }}</p>
        <div class="counter-columns">
          <div class="counter-block">
            <h3 class="counter-title strong">{{ t('championDetail.strongAgainst') }}</h3>
            <ul class="counter-list">
              <li v-for="opponent in strongAgainst" :key="opponent.championId">
                <img v-if="opponent.championLogo" :src="opponent.championLogo" :alt="opponent.championChineseName" class="counter-logo" />
                <span class="counter-name">{{ opponent.championChineseName || opponent.championName }}</span>
                <span class="counter-meta accent">{{ fmtPercent(opponent.winRate) }} · {{ opponent.games }}局</span>
              </li>
            </ul>
          </div>
          <div class="counter-block">
            <h3 class="counter-title weak">{{ t('championDetail.weakAgainst') }}</h3>
            <ul class="counter-list">
              <li v-for="opponent in weakAgainst" :key="opponent.championId">
                <img v-if="opponent.championLogo" :src="opponent.championLogo" :alt="opponent.championChineseName" class="counter-logo" />
                <span class="counter-name">{{ opponent.championChineseName || opponent.championName }}</span>
                <span class="counter-meta danger">{{ fmtPercent(opponent.winRate) }} · {{ opponent.games }}局</span>
              </li>
            </ul>
          </div>
        </div>
      </section>

      <section class="detail-card">
        <h2 class="detail-heading">统计口径说明</h2>
        <ul class="caliber-list">
          <li>数据范围继承产生本详情的查询条件：赛段 {{ stageKeysLabel }}{{ queryParams.position ? `、位置 ${positionLabel(queryParams.position)}` : '' }}、最低出场 {{ result.minimumPickCount }} 次。</li>
          <li>分路数据按逐局明细统计：出场占比 = 该分路出场数 ÷ 全部位置出场数之和；禁用指标没有分路归属，按所选赛段整体计算。</li>
          <li>趋势图按赛段开始时间排序；柱高按各赛段出场/禁用数归一化，胜率按 0—100% 独立映射。</li>
          <li>选手使用榜按逐局明细中该英雄的出场次数排序，最低出场次数与整体查询条件一致。</li>
        </ul>
      </section>
    </template>
  </div>
</template>

<style scoped>
.champion-detail-page { max-width: 1180px; margin: 0 auto; padding: 16px 20px 40px; }
.detail-topbar { display: flex; align-items: center; gap: 14px; padding: 8px 0 14px; }
.back-link { color: var(--accent); text-decoration: none; font-weight: 600; }
.back-link:hover { text-decoration: underline; }
.topbar-title { color: var(--text-4); font-size: 13px; }
.detail-card { background: var(--panel); border: 1px solid var(--line); border-radius: 10px; padding: 18px 20px; margin-bottom: 16px; }
.detail-heading { margin: 0 0 6px; font-size: 16px; }
.detail-subheading { margin: 0 0 12px; color: var(--text-4); font-size: 12.5px; }
.detail-notice { background: var(--panel); border: 1px solid var(--line); border-radius: 10px; padding: 26px 20px; text-align: center; color: var(--text-3); }
.detail-error { color: var(--danger); }
.detail-notice-inline { color: var(--text-3); font-size: 13px; }
.profile-card { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.profile-logo { width: 64px; height: 64px; border-radius: 50%; object-fit: cover; background: var(--placeholder-bg); }
.profile-placeholder { display: grid; place-items: center; color: var(--accent); font-weight: 750; font-size: 26px; }
.profile-info { flex: 1 1 320px; min-width: 0; }
.profile-name { margin: 0; font-size: 20px; }
.profile-meta { margin: 4px 0 0; font-size: 13px; color: var(--text); }
.profile-meta.muted { color: var(--text-4); font-size: 12px; }
.position-tabs { display: flex; gap: 6px; flex-wrap: wrap; }
.position-tab { border: 1px solid var(--line); background: var(--panel); border-radius: 999px; padding: 5px 13px; font-size: 12.5px; cursor: pointer; color: var(--text-3); }
.position-tab.active { border-color: var(--accent); background: var(--accent); color: #fff; }
.detail-columns { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; align-items: stretch; }
.detail-columns > .detail-card { height: 100%; box-sizing: border-box; }
@media (max-width: 900px) { .detail-columns { grid-template-columns: 1fr; } }
.core-metrics-card { width: 100%; box-sizing: border-box; }
.core-metrics-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.core-metrics-note { color: var(--text-4); font-size: 11px; white-space: nowrap; }
.core-metrics-list { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 0; margin-top: 12px; border-top: 1px solid var(--line); }
.core-metric-item { min-width: 0; padding: 12px 12px 10px; border-right: 1px solid var(--line); border-bottom: 1px solid var(--line); }
.core-metric-item:nth-child(4n) { border-right: 0; }
.core-metric-item:nth-last-child(-n + 4) { border-bottom: 0; }
.core-metric-item:nth-child(4n + 1) { padding-left: 0; }
.core-metric-item:nth-child(4n) { padding-right: 0; }
.core-metric-label { color: var(--text-3); font-size: 12px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.core-metric-value { margin-top: 6px; color: var(--accent); font-size: 18px; font-weight: 700; line-height: 1.2; white-space: nowrap; }
.core-metric-value-text { font-size: 13px; font-weight: 650; }
.core-metric-rank { margin-top: 5px; color: var(--text); font-size: 11px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
@media (max-width: 700px) {
  .core-metrics-list { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .core-metric-item:nth-child(2n) { border-right: 0; padding-right: 8px; }
  .core-metric-item:nth-child(4n) { border-right: 0; padding-right: 8px; }
  .core-metric-item:nth-last-child(-n + 4) { border-bottom: 1px solid var(--line); }
  .core-metric-item:nth-last-child(-n + 2) { border-bottom: 0; }
  .core-metric-item:nth-child(4n + 1) { padding-left: 8px; }
}
.detail-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.detail-table th, .detail-table td { padding: 8px 10px; border-bottom: 1px solid var(--line); text-align: left; white-space: nowrap; }
.detail-table thead th { color: var(--text-3); font-size: 12px; background: var(--th-bg); }
.detail-table td.accent { color: var(--accent-dark); font-weight: 650; }
.table-scroll { max-height: 400px; overflow: auto; }
.pos-badge {
  display: inline-block; padding: 2px 8px; border: 1px solid var(--accent-line); border-radius: 999px;
  color: var(--accent-dark); background: var(--accent-soft); font-size: 11px; font-weight: 650;
}
.player-link { display: inline-flex; align-items: center; gap: 8px; color: inherit; text-decoration: none; }
.player-link:hover strong { color: var(--accent); }
.player-avatar { width: 28px; height: 28px; border-radius: 50%; object-fit: cover; }
.caliber-list { margin: 0; padding-left: 20px; color: var(--text-3); font-size: 13px; line-height: 1.75; }
.counter-columns { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
@media (max-width: 700px) { .counter-columns { grid-template-columns: 1fr; } }
.counter-title { margin: 0 0 8px; font-size: 13.5px; }
.counter-title.strong { color: var(--accent-dark); }
.counter-title.weak { color: var(--danger); }
.counter-list { margin: 0; padding: 0; list-style: none; display: grid; gap: 7px; }
.counter-list li { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.counter-logo { width: 24px; height: 24px; border-radius: 50%; object-fit: cover; }
.counter-name { font-weight: 600; color: var(--text); }
.counter-meta { margin-left: auto; font-weight: 650; white-space: nowrap; }
.counter-meta.accent { color: var(--accent-dark); }
.counter-meta.danger { color: var(--danger); }
</style>
