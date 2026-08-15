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
          <button class="pos-chip" :class="{ active: props.sortBy === 'startTime' }" @click="changeSort('startTime')">时间{{ sortIndicator('startTime') }}</button>
          <button class="pos-chip" :class="{ active: props.sortBy === 'matchId' }" @click="changeSort('matchId')">系列赛{{ sortIndicator('matchId') }}</button>
        </div>
        <span class="total-games">共 {{ result?.total ?? 0 }} 局</span>
      </div>
    </div>

    <p v-if="error" class="message error">{{ error }}</p>
    <p v-if="loading" class="message success">{{ t('matches.loading') }}</p>

    <template v-if="props.stageKeys.length">
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
</style>
