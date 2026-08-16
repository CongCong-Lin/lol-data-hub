<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { api, type MatchGameRecord, type MatchGamesResult, type Stage } from './api'
import { useI18n } from './i18n'

const props = defineProps<{
  stageKeys: string[]
  submitted: boolean
  sortBy: 'startTime' | 'matchId'
  sortDirection: 'asc' | 'desc'
  offset: number
}>()

const emit = defineEmits<{
  'update:sortBy': [value: 'startTime' | 'matchId']
  'update:sortDirection': [value: 'asc' | 'desc']
  'update:offset': [value: number]
  loaded: []
}>()

const { t } = useI18n()

const PAGE_SIZE = 50
const DAY_PAGE_SIZE = 100

const allStages = ref<Stage[]>([])
const loading = ref(false)
const error = ref('')
const result = ref<MatchGamesResult | null>(null)
let querySeq = 0

async function load() {
  const keys = props.stageKeys
  if (!keys.length) {
    result.value = null
    return
  }
  const seq = ++querySeq
  loading.value = true
  error.value = ''
  try {
    const data = await api.matchGames(keys, props.sortBy, props.sortDirection, props.offset, PAGE_SIZE)
    if (seq === querySeq) {
      result.value = data
      emit('loaded')
    }
  } catch (reason) {
    if (seq === querySeq) error.value = reason instanceof Error ? reason.message : String(reason)
  } finally {
    if (seq === querySeq) loading.value = false
  }
}

/* 查询提交后加载结果；未提交（赛段变化/切视图等由 App 重置）时清空结果 */
watch(() => props.submitted, (value) => {
  if (value) void load()
  else result.value = null
})

watch([() => props.sortBy, () => props.sortDirection, () => props.offset], () => {
  if (props.submitted) void load()
})

onMounted(async () => {
  try {
    allStages.value = await api.availability('HERO', false)
  } catch {
    /* 赛段名称仅用于展示，失败时回退到编号显示 */
  }
  if (props.submitted) void load()
})

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

function stageNameOf(game: MatchGameRecord): string {
  const detail = allStages.value.find(
    (s) => s.sourceSeasonId === game.sourceSeasonId && s.sourceStageId === game.sourceStageId,
  )
  return detail?.name ?? `赛事#${game.sourceSeasonId} 赛段#${game.sourceStageId}`
}

function winnerOf(game: MatchGameRecord): string {
  if (game.winnerTeamId === game.teamAId) return game.teamAName
  if (game.winnerTeamId === game.teamBId) return game.teamBName
  return '—'
}

function detailHref(game: MatchGameRecord): string {
  const params = new URLSearchParams({ stageKeys: props.stageKeys.join(',') })
  return `/matches/${game.sourceMatchId}?${params.toString()}`
}

const totalPages = computed(() => Math.max(1, Math.ceil((result.value?.total ?? 0) / PAGE_SIZE)))
const currentPage = computed(() => Math.floor(props.offset / PAGE_SIZE) + 1)

/* 页码窗口：最多 5 个数字，以当前页为中心 */
const pageNumbers = computed(() => {
  const visibleCount = Math.min(5, totalPages.value)
  let start = Math.max(1, currentPage.value - 2)
  let end = Math.min(totalPages.value, start + visibleCount - 1)
  start = Math.max(1, end - visibleCount + 1)
  return Array.from({ length: end - start + 1 }, (_, index) => start + index)
})

function gotoPage(page: number) {
  if (page < 1 || page > totalPages.value) return
  emit('update:offset', (page - 1) * PAGE_SIZE)
}

function changeSort(field: 'startTime' | 'matchId') {
  if (props.sortBy === field) {
    emit('update:sortDirection', props.sortDirection === 'desc' ? 'asc' : 'desc')
  } else {
    emit('update:sortBy', field)
    emit('update:sortDirection', 'desc')
  }
  emit('update:offset', 0)
}

function sortIndicator(field: 'startTime' | 'matchId'): string {
  if (props.sortBy !== field) return ''
  return props.sortDirection === 'desc' ? ' ↓' : ' ↑'
}

/* ---- 比赛日视图：按日期分组的赛程卡片 + Elo 预测 + 可展开交锋记录 ---- */

const viewMode = ref<'list' | 'matchday'>('list')
const dayGames = ref<MatchGameRecord[]>([])
const dayTotal = ref(0)
const dayLoading = ref(false)
const dayMoreLoading = ref(false)
const dayError = ref('')
const selectedDay = ref('')
const eloRatingsByTeam = ref<Map<number, number>>(new Map())
const expandedMatchId = ref<number | null>(null)
const h2hLoading = ref(false)
const h2hSummary = ref<{ wins: number; losses: number; gameWins: number; gameLosses: number } | null>(null)
let daySeq = 0
let eloLoaded = false

const dayLoadedAll = computed(() => dayGames.value.length >= dayTotal.value)

function switchViewMode(mode: 'list' | 'matchday') {
  if (viewMode.value === mode) return
  viewMode.value = mode
  expandedMatchId.value = null
  h2hSummary.value = null
  if (mode === 'matchday') void loadMatchDays()
}

async function loadMatchDays() {
  const keys = props.stageKeys
  if (!keys.length || !props.submitted) return
  const seq = ++daySeq
  dayLoading.value = true
  dayMoreLoading.value = false
  dayError.value = ''
  try {
    const [games, elo] = await Promise.all([
      api.matchGames(keys, 'startTime', 'desc', 0, DAY_PAGE_SIZE),
      eloLoaded ? Promise.resolve(null) : api.eloRatings(keys),
    ])
    if (seq !== daySeq) return
    dayGames.value = games.items
    dayTotal.value = games.total
    if (elo) {
      const map = new Map<number, number>()
      for (const rating of elo.ratings) map.set(rating.teamId, rating.rating)
      eloRatingsByTeam.value = map
      eloLoaded = true
    }
    const days = availableDays.value
    if (days.length && !days.some((day) => day.date === selectedDay.value)) {
      selectedDay.value = days[0].date
    }
  } catch (reason) {
    if (seq === daySeq) dayError.value = reason instanceof Error ? reason.message : String(reason)
  } finally {
    if (seq === daySeq) dayLoading.value = false
  }
}

async function loadMoreMatchDays() {
  if (dayMoreLoading.value || dayLoadedAll.value) return
  await fetchOlderMatchDays()
}

async function fetchOlderMatchDays(): Promise<boolean> {
  const seq = ++daySeq
  dayMoreLoading.value = true
  try {
    const data = await api.matchGames(props.stageKeys, 'startTime', 'desc', dayGames.value.length, DAY_PAGE_SIZE)
    if (seq !== daySeq) return false
    if (data.items.length === 0) {
      // 服务端未返回更多数据时按已加载量对齐总数，避免死循环
      dayTotal.value = dayGames.value.length
      return false
    }
    dayGames.value = [...dayGames.value, ...data.items]
    dayTotal.value = data.total
    return true
  } catch (reason) {
    if (seq === daySeq) dayError.value = reason instanceof Error ? reason.message : String(reason)
    return false
  } finally {
    if (seq === daySeq) dayMoreLoading.value = false
  }
}

function selectDay(day: string) {
  selectedDay.value = day
  void ensureDayComplete(day)
}

/* 点击最旧比赛日时自动翻页补齐该日数据（更早日期出现或全部加载完即停） */
async function ensureDayComplete(target: string) {
  while (!dayLoadedAll.value && earliestDay() === target) {
    if (dayMoreLoading.value) return
    const appended = await fetchOlderMatchDays()
    if (!appended) return
  }
}

function earliestDay(): string | null {
  const days = availableDays.value
  return days.length ? days[days.length - 1].date : null
}

watch([() => props.stageKeys.join(','), () => props.submitted], () => {
  dayGames.value = []
  dayTotal.value = 0
  selectedDay.value = ''
  eloLoaded = false
  eloRatingsByTeam.value = new Map()
  expandedMatchId.value = null
  h2hSummary.value = null
  dayMoreLoading.value = false
  if (viewMode.value === 'matchday') void loadMatchDays()
})

function dayKeyOf(game: MatchGameRecord): string {
  if (!game.startTime) return '未知日期'
  return game.startTime.slice(0, 10)
}

const availableDays = computed(() => {
  const counts = new Map<string, number>()
  for (const game of dayGames.value) {
    const key = dayKeyOf(game)
    counts.set(key, (counts.get(key) ?? 0) + 1)
  }
  return [...counts.entries()]
    .map(([date, games]) => ({ date, games }))
    .sort((a, b) => (a.date < b.date ? 1 : -1))
})

interface DayMatch {
  matchId: number
  startTime: string | null
  teamAId: number
  teamAName: string
  teamALogo: string | null
  teamBId: number
  teamBName: string
  teamBLogo: string | null
  teamAWins: number
  teamBWins: number
  lastGameKills: string
}

const dayMatches = computed<DayMatch[]>(() => {
  const byMatch = new Map<number, MatchGameRecord[]>()
  for (const game of dayGames.value) {
    if (dayKeyOf(game) !== selectedDay.value) continue
    const list = byMatch.get(game.sourceMatchId) ?? []
    list.push(game)
    byMatch.set(game.sourceMatchId, list)
  }
  return [...byMatch.entries()]
    .map(([matchId, games]) => {
      const first = games[0]
      const last = games[games.length - 1]
      return {
        matchId,
        startTime: first.startTime,
        teamAId: first.teamAId,
        teamAName: first.teamAName,
        teamALogo: first.teamALogo,
        teamBId: first.teamBId,
        teamBName: first.teamBName,
        teamBLogo: first.teamBLogo,
        teamAWins: games.filter((g) => g.winnerTeamId === first.teamAId).length,
        teamBWins: games.filter((g) => g.winnerTeamId === first.teamBId).length,
        lastGameKills: `${last.teamAKills}:${last.teamBKills}`,
      }
    })
    .sort((a, b) => ((a.startTime ?? '') < (b.startTime ?? '') ? 1 : -1))
})

function eloWinProbability(match: DayMatch): number | null {
  const ratingA = eloRatingsByTeam.value.get(match.teamAId)
  const ratingB = eloRatingsByTeam.value.get(match.teamBId)
  if (ratingA == null || ratingB == null) return null
  return 1 / (1 + 10 ** ((ratingB - ratingA) / 400))
}

async function toggleMatchH2H(match: DayMatch) {
  if (expandedMatchId.value === match.matchId) {
    expandedMatchId.value = null
    h2hSummary.value = null
    return
  }
  expandedMatchId.value = match.matchId
  h2hSummary.value = null
  const seq = ++daySeq
  h2hLoading.value = true
  try {
    const h2h = await api.teamHeadToHead(match.teamAId, props.stageKeys)
    if (seq !== daySeq) return
    const opponent = h2h.opponents.find((row) => row.opponentTeamId === match.teamBId)
    h2hSummary.value = opponent
      ? { wins: opponent.matchWins, losses: opponent.matchLosses, gameWins: opponent.gameWins, gameLosses: opponent.gameLosses }
      : { wins: 0, losses: 0, gameWins: 0, gameLosses: 0 }
  } catch {
    if (seq === daySeq) h2hSummary.value = null
  } finally {
    if (seq === daySeq) h2hLoading.value = false
  }
}
</script>

<template>
  <div class="matches-panel">
    <div class="table-toolbar">
      <div>
        <p class="eyebrow">MATCH RESULTS</p>
        <h2>对局赛果</h2>
      </div>
      <div class="toolbar-right">
        <div class="sort-row">
          <button class="pos-chip" :class="{ active: viewMode === 'list' }" @click="switchViewMode('list')">对局列表</button>
          <button class="pos-chip" :class="{ active: viewMode === 'matchday' }" @click="switchViewMode('matchday')">比赛日</button>
          <template v-if="viewMode === 'list'">
            <button class="pos-chip" :class="{ active: props.sortBy === 'startTime' }" @click="changeSort('startTime')">时间{{ sortIndicator('startTime') }}</button>
            <button class="pos-chip" :class="{ active: props.sortBy === 'matchId' }" @click="changeSort('matchId')">系列赛{{ sortIndicator('matchId') }}</button>
          </template>
        </div>
        <span class="total-games">共 {{ viewMode === 'list' ? (result?.total ?? 0) : dayTotal }} 局<span v-if="viewMode === 'matchday' && !dayLoadedAll"> · 已加载 {{ dayGames.length }} 局</span></span>
      </div>
    </div>

    <p v-if="error" class="message error">{{ error }}</p>
    <p v-if="loading" class="message success">{{ t('matches.loading') }}</p>

    <!-- 比赛日视图：按日期分组 + Elo 预测 + 可展开交锋 -->
    <template v-if="viewMode === 'matchday'">
      <p v-if="dayError" class="message error">{{ dayError }}</p>
      <p v-if="dayLoading" class="message success">{{ t('matches.loading') }}</p>
      <template v-else-if="props.submitted && availableDays.length">
        <div class="day-chips" aria-label="比赛日选择">
          <button
            v-for="day in availableDays"
            :key="day.date"
            class="pos-chip"
            :class="{ active: selectedDay === day.date }"
            @click="selectDay(day.date)"
          >{{ day.date }}（{{ day.games }}局）</button>
          <button
            v-if="!dayLoadedAll"
            class="pos-chip load-more-day"
            type="button"
            :disabled="dayMoreLoading"
            @click="loadMoreMatchDays"
          >{{ dayMoreLoading ? '加载中…' : `加载更多（已加载 ${dayGames.length}/${dayTotal} 局）` }}</button>
        </div>
        <div v-if="dayMatches.length" class="day-match-list">
          <article v-for="match in dayMatches" :key="match.matchId" class="day-match-card">
            <div class="day-match-main">
              <div class="day-match-team">
                <img v-if="match.teamALogo" :src="match.teamALogo" :alt="match.teamAName" class="team-logo" />
                <span v-else class="team-placeholder team-logo">{{ match.teamAName.slice(0, 1) }}</span>
                <strong>{{ match.teamAName }}</strong>
              </div>
              <div class="day-match-score">
                <span class="score" :class="{ 'accent-text': match.teamAWins > match.teamBWins }">{{ match.teamAWins }}</span>
                <span class="score-sep">:</span>
                <span class="score" :class="{ 'accent-text': match.teamBWins > match.teamAWins }">{{ match.teamBWins }}</span>
                <span class="score-note">末局 {{ match.lastGameKills }}</span>
              </div>
              <div class="day-match-team">
                <img v-if="match.teamBLogo" :src="match.teamBLogo" :alt="match.teamBName" class="team-logo" />
                <span v-else class="team-placeholder team-logo">{{ match.teamBName.slice(0, 1) }}</span>
                <strong>{{ match.teamBName }}</strong>
              </div>
            </div>
            <div class="day-match-meta">
              <span v-if="eloWinProbability(match) != null" class="elo-prob">
                Elo 预测 {{ match.teamAName }} <strong>{{ Math.round((eloWinProbability(match) ?? 0) * 100) }}%</strong>
              </span>
              <button class="view-link h2h-toggle" type="button" @click="toggleMatchH2H(match)">
                {{ expandedMatchId === match.matchId ? '收起交锋' : '交锋记录' }}
              </button>
              <a class="view-link" :href="detailHref({ sourceMatchId: match.matchId } as MatchGameRecord)">{{ t('matches.viewDetail') }}</a>
            </div>
            <div v-if="expandedMatchId === match.matchId" class="day-match-h2h">
              <p v-if="h2hLoading" class="h2h-text">加载交锋数据…</p>
              <p v-else-if="h2hSummary" class="h2h-text">
                所选赛段交锋：{{ match.teamAName }}
                <strong>{{ h2hSummary.wins }}</strong> 胜 <strong>{{ h2hSummary.losses }}</strong> 负
                （小局 {{ h2hSummary.gameWins }}:{{ h2hSummary.gameLosses }}）
              </p>
              <p v-else class="h2h-text">所选赛段内两队暂无历史交锋记录。</p>
            </div>
          </article>
        </div>
        <p v-else class="empty-inline">该日期暂无对局数据。</p>
      </template>
      <div v-else-if="props.submitted" class="empty-state">
        <strong>暂无对局数据</strong>
        <p>{{ t('matches.empty') }}</p>
      </div>
      <div v-else class="empty-state">
        <strong>选择赛段后点击查询</strong>
        <p>点击上方「查询统计」按钮后展示比赛日视图。</p>
      </div>
    </template>

    <template v-else-if="props.stageKeys.length">
      <template v-if="props.submitted && result && result.items.length">
        <div class="table-scroll" tabindex="0" aria-label="对局赛果表">
          <table class="match-table">
            <thead>
              <tr>
                <th>时间</th>
                <th>赛段</th>
                <th>系列赛</th>
                <th>局</th>
                <th>蓝色方</th>
                <th class="score-col">比分</th>
                <th>红色方</th>
                <th>胜方</th>
                <th>时长</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="game in result.items" :key="`${game.sourceMatchId}:${game.gameNumber}`">
                <td>{{ fmtDateTime(game.startTime) }}</td>
                <td>{{ stageNameOf(game) }}</td>
                <td>#{{ game.sourceMatchId }}</td>
                <td>{{ t('matches.game', { n: game.gameNumber }) }}</td>
                <td>
                  <div class="team-cell">
                    <img v-if="game.teamALogo" :src="game.teamALogo" :alt="game.teamAName" class="team-logo" />
                    <span class="team-placeholder team-logo" v-else>{{ game.teamAName.slice(0, 1) }}</span>
                    <strong>{{ game.teamAName }}</strong>
                    <span v-if="game.teamAFirstBlood" class="fb-tag" title="一血">First Blood</span>
                  </div>
                </td>
                <td class="score-col">{{ game.teamAKills }} : {{ game.teamBKills }}</td>
                <td>
                  <div class="team-cell">
                    <img v-if="game.teamBLogo" :src="game.teamBLogo" :alt="game.teamBName" class="team-logo" />
                    <span class="team-placeholder team-logo" v-else>{{ game.teamBName.slice(0, 1) }}</span>
                    <strong>{{ game.teamBName }}</strong>
                    <span v-if="game.teamBFirstBlood" class="fb-tag" title="一血">First Blood</span>
                  </div>
                </td>
                <td>
                  <span class="result-badge" :class="game.winnerTeamId === game.teamAId ? 'won-a' : game.winnerTeamId === game.teamBId ? 'won-b' : 'tie'">
                    {{ winnerOf(game) === game.teamAName ? game.teamAName : winnerOf(game) === game.teamBName ? game.teamBName : t('matches.win') }}
                  </span>
                </td>
                <td>{{ fmtDuration(game.gameDurationSeconds) }}</td>
                <td>
                  <a class="view-link" :href="detailHref(game)">{{ t('matches.viewDetail') }}</a>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="pagination">
          <div class="pagination-meta">
            <span>共 {{ result.total }} 局 · 第 {{ currentPage }} / {{ totalPages }} 页</span>
          </div>
          <div class="pagination-buttons">
            <button type="button" :disabled="currentPage <= 1" @click="gotoPage(currentPage - 1)">上一页</button>
            <button
              v-for="page in pageNumbers"
              :key="page"
              type="button"
              class="pagination-page"
              :class="{ active: page === currentPage }"
              :aria-current="page === currentPage ? 'page' : undefined"
              :aria-label="`第 ${page} 页`"
              @click="gotoPage(page)"
            >{{ page }}</button>
            <button type="button" :disabled="currentPage >= totalPages" @click="gotoPage(currentPage + 1)">下一页</button>
          </div>
        </div>
      </template>
      <div v-else-if="props.submitted && result" class="empty-state">
        <strong>暂无对局数据</strong>
        <p>{{ t('matches.empty') }}</p>
      </div>
      <div v-else class="empty-state">
        <strong>选择赛段后点击查询</strong>
        <p>点击上方「查询统计」按钮后展示对局列表；对局明细需要先在采集流程中回填（见「采集状态」页）。</p>
      </div>
    </template>
    <div v-else class="empty-state">
      <strong>选择赛段后查询</strong>
      <p>请先在上方选择要查询的赛段；如所选赛段尚未回填逐局明细，将显示空结果。</p>
    </div>
  </div>
</template>

<style scoped>
.sort-row { display: flex; gap: 8px; }
.sort-row .pos-chip { padding: 7px 14px; font-size: 13px; }
.total-games { color: var(--muted); font-size: 12px; font-variant-numeric: tabular-nums; }
.match-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.match-table th, .match-table td { padding: 10px 12px; border-bottom: 1px solid var(--line); text-align: left; white-space: nowrap; }
.match-table thead th { color: var(--text-3); font-size: 12px; background: var(--th-bg); }
.match-table tbody tr:hover { background: var(--hover-bg); }
.team-cell { display: flex; align-items: center; gap: 8px; }
.team-logo { width: 26px; height: 26px; border-radius: 4px; object-fit: contain; }
.score-col { text-align: center; font-variant-numeric: tabular-nums; font-weight: 650; }
.fb-tag { padding: 1px 5px; border-radius: 4px; font-size: 10px; font-weight: 700; color: #b07d0e; background: #fdf3d7; }
.result-badge { display: inline-block; max-width: 220px; overflow: hidden; text-overflow: ellipsis; padding: 2px 9px; border-radius: 999px; font-size: 12px; font-weight: 700; }
.result-badge.won-a { color: var(--accent-dark); background: var(--accent-soft); }
.result-badge.won-b { color: var(--danger); background: var(--danger-soft); }
.result-badge.tie { color: var(--muted); background: var(--tab-bg); }
.view-link { color: var(--accent); text-decoration: none; font-weight: 600; }
.view-link:hover { text-decoration: underline; }
.day-chips { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 14px; }
.load-more-day:disabled { opacity: 0.55; cursor: wait; }
.day-match-list { display: grid; gap: 10px; }
.day-match-card { border: 1px solid var(--line); border-radius: 10px; padding: 14px 16px; background: var(--panel-2); }
.day-match-main { display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: 14px; }
.day-match-team { display: flex; align-items: center; gap: 9px; min-width: 0; }
.day-match-team:last-child { justify-content: flex-end; }
.day-match-score { display: flex; align-items: baseline; gap: 6px; }
.day-match-score .score { font-size: 22px; font-weight: 800; font-variant-numeric: tabular-nums; }
.day-match-score .accent-text { color: var(--accent-dark); }
.score-sep { color: var(--text-4); }
.score-note { margin-left: 6px; color: var(--text-4); font-size: 11px; white-space: nowrap; }
.day-match-meta { display: flex; align-items: center; gap: 14px; margin-top: 8px; flex-wrap: wrap; }
.elo-prob { color: var(--text-2); font-size: 12.5px; }
.elo-prob strong { color: var(--accent-dark); }
.h2h-toggle { background: none; border: none; cursor: pointer; padding: 0; font-size: inherit; }
.day-match-h2h { margin-top: 8px; padding-top: 8px; border-top: 1px dashed var(--line); }
.h2h-text { margin: 0; color: var(--text-2); font-size: 13px; }
.h2h-text strong { color: var(--accent-dark); }
</style>
