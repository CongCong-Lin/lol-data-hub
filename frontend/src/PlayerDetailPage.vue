<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, type PlayerDetailStatisticsResult, type PlayerGamesResult } from './api'
import PlayerRadarChart from './PlayerRadarChart.vue'
import ScoreggAverageContrast from './ScoreggAverageContrast.vue'

const props = defineProps<{ playerId: string }>()

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const error = ref('')
const result = ref<PlayerDetailStatisticsResult | null>(null)
const gameRecords = ref<PlayerGamesResult | null>(null)
const gameRecordsLoading = ref(false)
const gameRecordsError = ref('')

const PLAYER_POSITION_LABELS: Record<string, string> = {
  TOP: '上单', JUG: '打野', MID: '中路', AD: '下路', SUP: '辅助',
}

const queryParams = computed(() => {
  const stageKeys = String(route.query.stageKeys ?? '')
    .split(',')
    .map((key) => key.trim())
    .filter(Boolean)
  const position = String(route.query.position ?? '')
  const parsedMinimum = Number(route.query.minimumMatchCount ?? '5')
  const minimumMatchCount = Number.isInteger(parsedMinimum) ? parsedMinimum : 5
  return { stageKeys, position, minimumMatchCount }
})

const playerIdNumber = computed(() => Number(props.playerId))

async function load() {
  const { stageKeys, position, minimumMatchCount } = queryParams.value
  result.value = null
  gameRecords.value = null
  gameRecordsError.value = ''
  if (!Number.isInteger(playerIdNumber.value) || playerIdNumber.value <= 0) {
    error.value = '无效的选手 ID'
    return
  }
  if (!stageKeys.length || !position) {
    error.value = '链接缺少查询参数（stageKeys / position），请从选手统计表格的头像入口打开本页'
    return
  }
  const seq = ++loadSeq
  loading.value = true
  error.value = ''
  try {
    const data = await api.playerDetail(playerIdNumber.value, stageKeys, position, minimumMatchCount)
    if (seq === loadSeq) result.value = data
    if (seq === loadSeq) void loadGameRecords(seq)
  } catch (reason) {
    if (seq === loadSeq) error.value = reason instanceof Error ? reason.message : String(reason)
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}
let loadSeq = 0

async function loadGameRecords(seq: number) {
  const { stageKeys } = queryParams.value
  gameRecordsLoading.value = true
  gameRecordsError.value = ''
  try {
    const data = await api.playerGames(playerIdNumber.value, stageKeys, 50)
    if (seq === loadSeq) gameRecords.value = data
  } catch (reason) {
    if (seq === loadSeq) gameRecordsError.value = reason instanceof Error ? reason.message : String(reason)
  } finally {
    if (seq === loadSeq) gameRecordsLoading.value = false
  }
}

watch(queryParams, load, { immediate: true, deep: true })

function switchPosition(position: string) {
  if (position === queryParams.value.position) return
  void router.replace({ query: { ...route.query, position } })
}

function positionLabel(position: string): string {
  return PLAYER_POSITION_LABELS[position] ?? position
}

function fmtDecimal(value: number): string {
  return Number(value).toFixed(2)
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

type HeroSortKey = 'pickCount' | 'pickRate' | 'winningCount' | 'winningRate' | 'kda' | 'killPerGame' | 'deathPerGame' | 'assistPerGame'

const heroSortBy = ref<HeroSortKey>('pickCount')
const heroSortDesc = ref(true)

function changeHeroSort(key: HeroSortKey) {
  if (heroSortBy.value === key) heroSortDesc.value = !heroSortDesc.value
  else {
    heroSortBy.value = key
    heroSortDesc.value = true
  }
}

const sortedHeroes = computed(() => {
  const heroes = [...(result.value?.heroes ?? [])]
  const key = heroSortBy.value
  const direction = heroSortDesc.value ? -1 : 1
  heroes.sort((a, b) => (Number(a[key]) - Number(b[key])) * direction)
  return heroes
})

function heroSortIndicator(key: HeroSortKey): string {
  if (heroSortBy.value !== key) return ''
  return heroSortDesc.value ? ' ↓' : ' ↑'
}

const stageKeysLabel = computed(() => queryParams.value.stageKeys.join('、'))

function matchHref(record: { sourceMatchId: number }): string {
  const params = new URLSearchParams({ stageKeys: queryParams.value.stageKeys.join(',') })
  return `/matches/${record.sourceMatchId}?${params.toString()}`
}

function fmtDateTimeShort(value: string | null): string {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ').slice(0, 10)
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

const returnPath = computed(() => {
  const candidate = String(route.query.returnTo ?? '')
  // 只允许返回站内绝对路径，避免把查询参数变成外部跳转地址。
  if (!candidate.startsWith('/') || candidate.startsWith('//')) return '/'
  return candidate
})
</script>

<template>
  <div class="player-detail-page">
    <header class="detail-topbar">
      <RouterLink class="back-link" :to="returnPath">← 返回统计查询</RouterLink>
      <span class="topbar-title">选手详情</span>
    </header>

    <div v-if="loading" class="detail-notice">加载中…</div>
    <div v-else-if="error" class="detail-notice detail-error">{{ error }}</div>

    <template v-else-if="result">
      <section class="detail-card profile-card">
        <img
          v-if="result.player.playerAvatar"
          :src="result.player.playerAvatar"
          :alt="result.player.playerName"
          class="profile-avatar"
        />
        <span v-else class="profile-avatar profile-placeholder">{{ result.player.playerName.slice(0, 1) }}</span>
        <div class="profile-info">
          <h1 class="profile-name">
            {{ result.player.playerName }}
          </h1>
          <p class="profile-meta">
            {{ result.player.teamNames.join(' / ') || '未知战队' }}
            · 系列赛 {{ result.player.matchCount }} 场 · 对局 {{ result.player.gameCount }} 局
          </p>
          <p class="profile-meta muted">
            统计范围：{{ stageKeysLabel }} · 位置 {{ positionLabel(result.position) }}
            · 最低样本 {{ result.minimumMatchCount }} 场 · 数据版本 v{{ result.dataVersion }}
            · 数据更新至 {{ fmtDateTime(result.latestCollectedAt) }}
          </p>
        </div>
        <div v-if="result.player.positions.length > 1" class="position-tabs">
          <button
            v-for="position in result.player.positions"
            :key="position"
            type="button"
            class="position-tab"
            :class="{ active: position === queryParams.position }"
            @click="switchPosition(position)"
          >{{ positionLabel(position) }}</button>
        </div>
      </section>

      <div v-if="result.cohortSize <= 1" class="detail-warning">
        当前查询条件下同位置合格选手仅 {{ result.cohortSize }} 人，排名与百分位得分样本不足，仅供参考。
      </div>

      <section class="detail-card core-metrics-card">
        <div class="core-metrics-heading">
          <div>
            <h2 class="detail-heading">核心数据与同位置排名</h2>
            <p class="detail-subheading">共 {{ result.cohortSize }} 名同位置合格选手参与比较</p>
          </div>
          <span class="core-metrics-note">指标数值与同位置排名一并展示</span>
        </div>
        <div class="core-metrics-list">
          <article v-for="metric in result.coreMetrics" :key="metric.key" class="core-metric-item">
            <div class="core-metric-label">{{ metric.label }}</div>
            <div class="core-metric-value">{{ metric.formattedValue }}</div>
            <div class="core-metric-rank">第 {{ metric.rank }} 名 <span>/ 共 {{ metric.cohortSize }} 人</span></div>
          </article>
        </div>
      </section>

      <div class="detail-columns">
        <section class="detail-card">
          <ScoreggAverageContrast
            :player-name="result.player.playerName"
            :metrics="result.averageContrastMetrics"
          />
        </section>

        <section class="detail-card radar-card">
          <h2 class="detail-heading">八维能力雷达图</h2>
          <p class="detail-subheading">按同位置 10%—90% 分位区间归一化；虚线区域为同位置选手平均，场均死亡按越低越好计算</p>
          <PlayerRadarChart :metrics="result.radarMetrics" />
        </section>
      </div>

      <section class="detail-card">
        <h2 class="detail-heading">英雄使用统计</h2>
        <template v-if="result.heroUsageAvailable">
          <p class="detail-subheading">
            跨赛段求和后重新计算，共 {{ result.heroUsageTotalGames }} 局（该选手英雄明细 pickCount 总和）
          </p>
          <table class="detail-table hero-table">
            <thead>
              <tr>
                <th>英雄</th>
                <th class="sortable" @click="changeHeroSort('pickCount')">出场{{ heroSortIndicator('pickCount') }}</th>
                <th class="sortable" @click="changeHeroSort('pickRate')">选取率{{ heroSortIndicator('pickRate') }}</th>
                <th class="sortable" @click="changeHeroSort('winningCount')">胜场{{ heroSortIndicator('winningCount') }}</th>
                <th class="sortable" @click="changeHeroSort('winningRate')">胜率{{ heroSortIndicator('winningRate') }}</th>
                <th class="sortable" @click="changeHeroSort('kda')">KDA{{ heroSortIndicator('kda') }}</th>
                <th class="sortable" @click="changeHeroSort('killPerGame')">场均击杀{{ heroSortIndicator('killPerGame') }}</th>
                <th class="sortable" @click="changeHeroSort('deathPerGame')">场均死亡{{ heroSortIndicator('deathPerGame') }}</th>
                <th class="sortable" @click="changeHeroSort('assistPerGame')">场均助攻{{ heroSortIndicator('assistPerGame') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="hero in sortedHeroes" :key="hero.sourceChampionId">
                <td>
                  <div class="hero-cell">
                    <img v-if="hero.championLogo" :src="hero.championLogo" :alt="hero.championChineseName" class="hero-logo" />
                    <span>
                      <strong>{{ hero.championChineseName }}</strong>
                      <small v-if="hero.championTitle">{{ hero.championTitle }}</small>
                    </span>
                  </div>
                </td>
                <td>{{ hero.pickCount }}</td>
                <td>{{ fmtPercent(hero.pickRate) }}</td>
                <td>{{ hero.winningCount }}</td>
                <td>{{ fmtPercent(hero.winningRate) }}</td>
                <td class="accent">{{ fmtDecimal(hero.kda) }}</td>
                <td>{{ fmtDecimal(hero.killPerGame) }}</td>
                <td>{{ fmtDecimal(hero.deathPerGame) }}</td>
                <td>{{ fmtDecimal(hero.assistPerGame) }}</td>
              </tr>
            </tbody>
          </table>
        </template>
        <p v-else class="detail-notice-inline">
          英雄使用统计暂不可用：以下赛段尚未采集逐局英雄明细数据（{{ result.missingHeroStageKeys.join('、') }}），
          为保证口径一致不展示部分赛段的英雄统计，核心数据不受影响。
        </p>
      </section>

      <section class="detail-card">
        <h2 class="detail-heading">单局战绩</h2>
        <p class="detail-subheading">
          最近 {{ gameRecords?.items.length ?? 0 }} 局单场数据（按开始时间倒序），来自已回填的逐局明细；
          点击「查看对局」可进入该系列赛完整赛果。
        </p>
        <template v-if="gameRecordsLoading">
          <p class="detail-notice-inline">加载中…</p>
        </template>
        <template v-else-if="gameRecordsError">
          <p class="detail-notice-inline">单局战绩暂不可用：{{ gameRecordsError }}</p>
        </template>
        <template v-else-if="gameRecords && gameRecords.items.length">
          <div class="table-scroll game-record-scroll">
            <table class="detail-table game-record-table">
              <thead>
                <tr>
                  <th>时间</th>
                  <th>赛段</th>
                  <th>对手</th>
                  <th>英雄</th>
                  <th>结果</th>
                  <th>KDA</th>
                  <th>击杀</th>
                  <th>死亡</th>
                  <th>助攻</th>
                  <th>伤害</th>
                  <th>伤害占比</th>
                  <th>参团率</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="record in gameRecords.items" :key="`${record.sourceMatchId}:${record.gameNumber}`">
                  <td>{{ fmtDateTimeShort(record.startTime) }}</td>
                  <td>{{ record.stageName }}</td>
                  <td>{{ record.opponentTeamName }}</td>
                  <td>
                    <div class="hero-cell">
                      <img v-if="record.championLogo" :src="record.championLogo" :alt="record.championChineseName" class="hero-logo" />
                      <span class="hero-logo hero-placeholder" v-else>{{ record.championChineseName.slice(0, 1) }}</span>
                      <strong>{{ record.championChineseName }}</strong>
                    </div>
                  </td>
                  <td>
                    <span class="record-result" :class="record.won ? 'won' : 'lost'">{{ record.won ? '胜' : '负' }}</span>
                  </td>
                  <td class="accent">{{ fmtDecimal(record.kda) }}</td>
                  <td>{{ record.kills }}</td>
                  <td>{{ record.deaths }}</td>
                  <td>{{ record.assists }}</td>
                  <td>{{ fmtDecimal(record.heroDamage) }}</td>
                  <td>{{ fmtPercent(record.damagePercent) }}</td>
                  <td>{{ fmtPercent(record.killParticipantPercent) }}</td>
                  <td>
                    <a class="view-match-link" :href="matchHref(record)">查看对局</a>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
        <p v-else class="detail-notice-inline">暂无单局战绩：所选赛段尚未回填逐局明细数据。</p>
      </section>

      <section class="detail-card">
        <h2 class="detail-heading">统计口径说明</h2>
        <ul class="caliber-list">
          <li>数据范围继承产生本详情的查询条件：赛段 {{ stageKeysLabel }}、位置 {{ positionLabel(result.position) }}、最低样本 {{ result.minimumMatchCount }} 场。</li>
          <li>同位置排名仅比较相同查询条件下达到样本门槛的选手，采用竞赛排名（1 + 严格优于当前选手的人数），数值并列获得相同排名；场均死亡为越低越好。</li>
          <li>雷达图按位置选取 6 项指标，数值按同位置选手百分位归一化到 0～100，相同数值获得相同得分；虚线为同位置选手平均得分。</li>
          <li>英雄使用统计在逐局英雄明细上跨赛段求和后重新计算：KDA =（总击杀 + 总助攻）÷ max(总死亡, 1)；选取率分母为该选手英雄明细全部 pickCount 之和。</li>
          <li>逐局英雄明细不包含 MVP 信息，因此本页不提供英雄级 MVP 统计。</li>
          <li>核心数据与选手统计列表使用完全相同的聚合口径（加权平均、MVP 票数按赛事去重等）。</li>
        </ul>
      </section>
    </template>
  </div>
</template>

<style scoped>
.player-detail-page { max-width: 1180px; margin: 0 auto; padding: 16px 20px 40px; }
.detail-topbar { display: flex; align-items: center; gap: 14px; padding: 8px 0 14px; }
.back-link { color: var(--accent); text-decoration: none; font-weight: 600; }
.back-link:hover { text-decoration: underline; }
.topbar-title { color: #8b949e; font-size: 13px; }
.detail-card { background: #fff; border: 1px solid var(--line); border-radius: 10px; padding: 18px 20px; margin-bottom: 16px; }
.detail-heading { margin: 0 0 6px; font-size: 16px; }
.detail-subheading { margin: 0 0 12px; color: #8b949e; font-size: 12.5px; }
.detail-notice { background: #fff; border: 1px solid var(--line); border-radius: 10px; padding: 26px 20px; text-align: center; color: #57606a; }
.detail-error { color: #cf222e; }
.detail-warning { background: #fff8e6; border: 1px solid #f0dfa8; border-radius: 10px; padding: 10px 14px; margin-bottom: 16px; color: #7a5b00; font-size: 13px; }
.detail-notice-inline { color: #57606a; font-size: 13px; }
.profile-card { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.profile-avatar { width: 64px; height: 64px; border-radius: 50%; object-fit: cover; background: #edf0f2; }
.profile-placeholder { display: grid; place-items: center; color: var(--accent); font-weight: 750; font-size: 26px; }
.profile-info { flex: 1 1 320px; min-width: 0; }
.profile-name { margin: 0; font-size: 20px; display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.share-card-btn {
  padding: 5px 11px; border: 1px solid var(--accent-line); border-radius: 6px;
  color: var(--accent-dark); background: var(--accent-soft); font-size: 12px; font-weight: 700; cursor: pointer;
}
.share-card-btn:hover { border-color: var(--accent); }
.profile-meta { margin: 4px 0 0; font-size: 13px; color: #24292f; }
.profile-meta.muted { color: #8b949e; font-size: 12px; }
.position-tabs { display: flex; gap: 6px; }
.position-tab { border: 1px solid var(--line); background: #fff; border-radius: 999px; padding: 5px 13px; font-size: 12.5px; cursor: pointer; color: #57606a; }
.position-tab.active { border-color: var(--accent); background: var(--accent); color: #fff; }
.detail-columns { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; align-items: stretch; }
.detail-columns > .detail-card { height: 100%; box-sizing: border-box; }
@media (max-width: 900px) { .detail-columns { grid-template-columns: 1fr; } }
.core-metrics-card { width: 100%; box-sizing: border-box; }
.core-metrics-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.core-metrics-note { color: #8b949e; font-size: 11px; white-space: nowrap; }
.core-metrics-list { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 0; margin-top: 12px; border-top: 1px solid var(--line); }
.core-metric-item { min-width: 0; padding: 12px 12px 10px; border-right: 1px solid var(--line); border-bottom: 1px solid var(--line); }
.core-metric-item:nth-child(5n) { border-right: 0; }
.core-metric-item:nth-last-child(-n + 5) { border-bottom: 0; }
.core-metric-item:nth-child(5n + 1) { padding-left: 0; }
.core-metric-item:nth-child(5n) { padding-right: 0; }
.core-metric-label { color: #57606a; font-size: 12px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.core-metric-value { margin-top: 6px; color: var(--accent); font-size: 18px; font-weight: 700; line-height: 1.2; white-space: nowrap; }
.core-metric-rank { margin-top: 5px; color: #24292f; font-size: 11px; white-space: nowrap; }
.core-metric-rank span { color: #8b949e; }
.radar-card { min-width: 0; }
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
.detail-table th, .detail-table td { padding: 7px 10px; border-bottom: 1px solid var(--line); text-align: left; white-space: nowrap; }
.detail-table thead th { color: #57606a; font-size: 12px; background: #f6f8fa; }
.detail-table th.sortable { cursor: pointer; user-select: none; }
.detail-table th.sortable:hover { color: var(--accent); }
.detail-table td.accent { color: var(--accent); font-weight: 650; }
.muted { color: #8b949e; }
.metric-table td:nth-child(3) { color: #57606a; }
.hero-cell { display: flex; align-items: center; gap: 8px; }
.hero-logo { width: 28px; height: 28px; border-radius: 50%; object-fit: cover; background: #edf0f2; }
.hero-cell strong { display: block; }
.hero-cell small { display: block; color: #8b949e; font-size: 11px; }
.game-record-scroll { max-height: 480px; overflow: auto; }
.game-record-table { min-width: 960px; }
.hero-placeholder {
  display: grid; place-items: center; color: var(--accent); font-weight: 750; font-size: 12px;
  background: #edf0f2; flex: 0 0 auto;
}
.record-result { display: inline-block; min-width: 28px; padding: 1px 8px; border-radius: 999px; text-align: center; font-size: 12px; font-weight: 700; }
.record-result.won { color: #246b47; background: #eef8f2; }
.record-result.lost { color: #c2414b; background: #fff5f5; }
.view-match-link { color: var(--accent); text-decoration: none; font-weight: 600; white-space: nowrap; }
.view-match-link:hover { text-decoration: underline; }
.caliber-list { margin: 0; padding-left: 20px; color: #57606a; font-size: 13px; line-height: 1.75; }
</style>
