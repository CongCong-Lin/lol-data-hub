<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  api,
  type MatchGameRecord,
  type MatchGamesResult,
  type Season,
  type Stage,
} from './api'
import { useI18n } from './i18n'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const MAX_STAGE_SELECTION = 50
const PAGE_SIZE = 50

function makeKey(seasonId: number, stageId: number): string {
  return `${seasonId}:${stageId}`
}

const seasons = ref<Season[]>([])
const allStages = ref<Stage[]>([])
const browsedSeasonId = ref(0)
const selectedStageKeys = ref<Set<string>>(new Set())
const loading = ref(false)
const error = ref('')
const result = ref<MatchGamesResult | null>(null)
let querySeq = 0

const sortBy = ref<'startTime' | 'matchId'>('startTime')
const sortDirection = ref<'asc' | 'desc'>('desc')
const offset = ref(0)

const sortedSeasons = computed(() =>
  [...seasons.value].sort((left, right) => left.sourceSeasonId - right.sourceSeasonId),
)

const browsedStages = computed(() =>
  allStages.value.filter((s) => s.sourceSeasonId === browsedSeasonId.value),
)

const selectedStageDetails = computed(() =>
  allStages.value.filter((s) => selectedStageKeys.value.has(makeKey(s.sourceSeasonId, s.sourceStageId))),
)

function parseStageKeys(value: string | null): string[] {
  return (value ?? '')
    .split(',')
    .map((key) => key.trim())
    .filter((key) => /^\d+:\d+$/.test(key))
    .slice(0, MAX_STAGE_SELECTION)
}

function syncUrl() {
  const params = new URLSearchParams()
  if (selectedStageKeys.value.size) params.set('stageKeys', [...selectedStageKeys.value].join(','))
  if (sortBy.value !== 'startTime') params.set('sortBy', sortBy.value)
  if (sortDirection.value !== 'desc') params.set('sortDirection', sortDirection.value)
  if (offset.value > 0) params.set('offset', String(offset.value))
  const query = params.toString()
  void router.replace({ query: query ? Object.fromEntries(new URLSearchParams(query)) : {} })
}

async function load() {
  const keys = [...selectedStageKeys.value]
  if (!keys.length) {
    result.value = null
    return
  }
  const seq = ++querySeq
  loading.value = true
  error.value = ''
  try {
    const data = await api.matchGames(keys, sortBy.value, sortDirection.value, offset.value, PAGE_SIZE)
    if (seq === querySeq) result.value = data
  } catch (reason) {
    if (seq === querySeq) error.value = reason instanceof Error ? reason.message : String(reason)
  } finally {
    if (seq === querySeq) loading.value = false
  }
}

watch([sortBy, sortDirection, offset], () => {
  void load()
  syncUrl()
})

async function loadSeasons() {
  seasons.value = await api.seasons()
  if (seasons.value.length && !seasons.value.some((item) => item.sourceSeasonId === browsedSeasonId.value)) {
    browsedSeasonId.value = seasons.value[0].sourceSeasonId
  }
}

async function loadStages() {
  try {
    allStages.value = await api.availability('HERO', false)
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : `加载赛段失败：${String(reason)}`
  }
}

onMounted(async () => {
  const params = new URLSearchParams(window.location.search)
  const restoredKeys = parseStageKeys(params.get('stageKeys'))
  const restoredSort = params.get('sortBy')
  if (restoredSort === 'startTime' || restoredSort === 'matchId') sortBy.value = restoredSort
  const restoredDirection = params.get('sortDirection')
  if (restoredDirection === 'asc' || restoredDirection === 'desc') sortDirection.value = restoredDirection
  const restoredOffset = Number(params.get('offset'))
  if (Number.isInteger(restoredOffset) && restoredOffset >= 0) offset.value = restoredOffset
  try {
    await loadSeasons()
    await loadStages()
    selectedStageKeys.value = new Set(restoredKeys)
    if (selectedStageKeys.value.size) await load()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : String(reason)
  }
})

function toggleStage(compositeKey: string) {
  const newSet = new Set(selectedStageKeys.value)
  if (newSet.has(compositeKey)) newSet.delete(compositeKey)
  else {
    if (newSet.size >= MAX_STAGE_SELECTION) {
      error.value = `最多选择 ${MAX_STAGE_SELECTION} 个赛段`
      return
    }
    newSet.add(compositeKey)
  }
  selectedStageKeys.value = newSet
  offset.value = 0
  void load()
  syncUrl()
}

function removeStage(compositeKey: string) {
  const newSet = new Set(selectedStageKeys.value)
  newSet.delete(compositeKey)
  selectedStageKeys.value = newSet
  offset.value = 0
  void load()
  syncUrl()
}

function clearSelectedStages() {
  if (!selectedStageKeys.value.size) return
  selectedStageKeys.value = new Set()
  offset.value = 0
  result.value = null
  syncUrl()
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
  const params = new URLSearchParams({ stageKeys: [...selectedStageKeys.value].join(',') })
  return `/matches/${game.sourceMatchId}?${params.toString()}`
}

const totalPages = computed(() => Math.max(1, Math.ceil((result.value?.total ?? 0) / PAGE_SIZE)))
const currentPage = computed(() => Math.floor(offset.value / PAGE_SIZE) + 1)

function gotoPage(page: number) {
  if (page < 1 || page > totalPages.value) return
  offset.value = (page - 1) * PAGE_SIZE
}

function changeSort(field: 'startTime' | 'matchId') {
  if (sortBy.value === field) {
    sortDirection.value = sortDirection.value === 'desc' ? 'asc' : 'desc'
  } else {
    sortBy.value = field
    sortDirection.value = 'desc'
  }
  offset.value = 0
}

function sortIndicator(field: 'startTime' | 'matchId'): string {
  if (sortBy.value !== field) return ''
  return sortDirection.value === 'desc' ? ' ↓' : ' ↑'
}
</script>

<template>
  <div class="matches-page shell">
    <header class="hero">
      <div>
        <p class="eyebrow">MATCH RESULTS</p>
        <h1>对局赛果</h1>
      </div>
      <RouterLink class="back-link" to="/">{{ t('matches.back') }}</RouterLink>
      <p class="hero-copy">基于已回填的逐局明细数据展示每场系列赛各小局的对阵结果、击杀比分与时长；行首标识该局胜方。</p>
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
        <label>排序</label>
        <div class="sort-row">
          <button class="pos-chip" :class="{ active: sortBy === 'startTime' }" @click="changeSort('startTime')">时间{{ sortIndicator('startTime') }}</button>
          <button class="pos-chip" :class="{ active: sortBy === 'matchId' }" @click="changeSort('matchId')">系列赛{{ sortIndicator('matchId') }}</button>
        </div>
      </div>
      <div class="actions">
        <span class="empty-inline">共 {{ result?.total ?? 0 }} 局</span>
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
            :class="{ selected: selectedStageKeys.has(makeKey(stage.sourceSeasonId, stage.sourceStageId)) }"
            @click="toggleStage(makeKey(stage.sourceSeasonId, stage.sourceStageId))"
          >
            <span>{{ stage.name }}</span>
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
            请选择要查询的赛段；如所选赛段尚未回填逐局明细，将显示空结果。
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

    <p v-if="error" class="message error">{{ error }}</p>
    <p v-if="loading" class="message success">加载中…</p>

    <section v-if="selectedStageKeys.size" class="panel table-panel">
      <template v-if="result && result.items.length">
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
                    <span v-if="game.teamAFirstBlood" class="fb-tag" title="一血">FB</span>
                  </div>
                </td>
                <td class="score-col">{{ game.teamAKills }} : {{ game.teamBKills }}</td>
                <td>
                  <div class="team-cell">
                    <img v-if="game.teamBLogo" :src="game.teamBLogo" :alt="game.teamBName" class="team-logo" />
                    <span class="team-placeholder team-logo" v-else>{{ game.teamBName.slice(0, 1) }}</span>
                    <strong>{{ game.teamBName }}</strong>
                    <span v-if="game.teamBFirstBlood" class="fb-tag" title="一血">FB</span>
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
            <button type="button" :disabled="currentPage >= totalPages" @click="gotoPage(currentPage + 1)">下一页</button>
          </div>
        </div>
      </template>
      <div v-else-if="result" class="empty-state">
        <strong>暂无对局数据</strong>
        <p>{{ t('matches.empty') }}</p>
      </div>
      <div v-else class="empty-state">
        <strong>选择赛段后查询</strong>
        <p>对局明细需要先在采集流程中回填（见「采集状态」页）。</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.back-link { color: var(--accent); text-decoration: none; font-weight: 600; justify-self: end; }
.back-link:hover { text-decoration: underline; }
.sort-row { display: flex; gap: 5px; }
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
</style>
