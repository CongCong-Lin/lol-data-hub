<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  api,
  type ChampionStatistics,
  type ChampionStatisticsResult,
  type PlayerStatistics,
  type PlayerStatisticsResult,
  type Season,
  type Stage,
  type StatisticType,
  type TeamStatistics,
  type TeamStatisticsResult,
} from './api'

type ActiveView = 'champion' | 'team' | 'player'

const CHAMPION_POSITION_OPTIONS = [
  { value: '', label: '全部' },
  { value: 'TOP', label: '上单' },
  { value: 'JUN', label: '打野' },
  { value: 'MID', label: '中路' },
  { value: 'BOT', label: '下路' },
  { value: 'SUP', label: '辅助' },
]

const PLAYER_POSITION_OPTIONS = [
  { value: '', label: '全部' },
  { value: 'TOP', label: '上单' },
  { value: 'JUG', label: '打野' },
  { value: 'MID', label: '中路' },
  { value: 'AD', label: '下路' },
  { value: 'SUP', label: '辅助' },
]

const CHAMPION_SORT_OPTIONS = [
  { value: 'bpRate', label: 'BP 率' },
  { value: 'winningRate', label: '胜率' },
  { value: 'pickCount', label: '出场次数' },
  { value: 'pickRate', label: '出场率' },
  { value: 'banRate', label: '禁用率' },
]

const TEAM_SORT_OPTIONS = [
  { value: 'winningRate', label: '胜率' },
  { value: 'totalKills', label: '总击杀' },
  { value: 'killPerGame', label: '场均击杀' },
  { value: 'matchCount', label: '比赛场数' },
  { value: 'baronKillPerGame', label: '场均大龙' },
]

const PLAYER_SORT_OPTIONS = [
  { value: 'kda', label: 'KDA' },
  { value: 'totalKills', label: '总击杀' },
  { value: 'mvpCount', label: 'MVP' },
  { value: 'killPerGame', label: '场均击杀' },
  { value: 'goldPerGame', label: '场均经济' },
  { value: 'damagePercent', label: '伤害占比' },
  { value: 'matchCount', label: '比赛场数' },
]

const VIEW_STAT_TYPE: Record<ActiveView, StatisticType> = {
  champion: 'HERO',
  team: 'TEAM',
  player: 'PLAYER',
}

const activeView = ref<ActiveView>('champion')
const seasons = ref<Season[]>([])
const stages = ref<Stage[]>([])
const seasonId = ref(237)
const selectedStageIds = ref<number[]>([])
const minimumPickCount = ref(10)
const minimumMatchCount = ref(5)
const sortBy = ref('bpRate')
const teamSortBy = ref('winningRate')
const playerSortBy = ref('kda')
const sortDirection = ref('desc')
const positionFilter = ref('')
const playerPositionFilter = ref('')
const search = ref('')
const teamSearch = ref('')
const playerSearch = ref('')
const result = ref<ChampionStatisticsResult | null>(null)
const teamResult = ref<TeamStatisticsResult | null>(null)
const playerResult = ref<PlayerStatisticsResult | null>(null)
const busy = ref(false)
const stagesLoading = ref(false)
const notice = ref('')
const error = ref('')
let loadStagesSeq = 0
let querySeq = 0

const collectedStages = computed(() => stages.value.filter((s) => s.collected))
const hasCollectedStages = computed(() => collectedStages.value.length > 0)

const selectedStages = computed(() =>
  stages.value.filter((s) => selectedStageIds.value.includes(s.sourceStageId)),
)

const totalSampleBase = computed(() =>
  selectedStages.value.reduce((sum, s) => sum + (s.sampleBaseCount ?? 0), 0),
)

const filteredChampionItems = computed(() => {
  let items = result.value?.items ?? []
  const pos = positionFilter.value
  if (pos) {
    items = items.filter((item) => item.positions.includes(pos))
  }
  const keyword = search.value.trim().toLowerCase()
  if (keyword) {
    items = items.filter((item) =>
      `${item.championName}${item.championTitle ?? ''}${item.positions.join('')}`.toLowerCase().includes(keyword),
    )
  }
  return items
})

const filteredTeamItems = computed(() => {
  let items = teamResult.value?.items ?? []
  const keyword = teamSearch.value.trim().toLowerCase()
  if (keyword) {
    items = items.filter((item) => item.teamName.toLowerCase().includes(keyword))
  }
  return items
})

const filteredPlayerItems = computed(() => {
  let items = playerResult.value?.items ?? []
  if (playerPositionFilter.value) {
    items = items.filter((item) => item.positions.includes(playerPositionFilter.value))
  }
  const keyword = playerSearch.value.trim().toLowerCase()
  if (keyword) {
    items = items.filter((item) =>
      `${item.playerName}${item.teamNames.join('')}`.toLowerCase().includes(keyword),
    )
  }
  return items
})

const latestCollectedAt = computed(() => {
  const timestamps = selectedStages.value
    .map((s) => s.collectedAt)
    .filter(Boolean) as string[]
  return timestamps.sort().at(-1) ?? null
})

const latestUpdatedAt = computed(() => {
  if (activeView.value === 'team' || activeView.value === 'player') return latestCollectedAt.value
  const timestamps = (result.value?.items ?? [])
    .map((item) => item.sourceUpdatedAt)
    .filter(Boolean) as string[]
  return timestamps.sort().at(-1) ?? latestCollectedAt.value
})

const currentDataVersion = computed(() => {
  if (activeView.value === 'champion') return result.value?.dataVersion
  if (activeView.value === 'team') return teamResult.value?.dataVersion
  return playerResult.value?.dataVersion
})

const canQuery = computed(() => {
  if (busy.value || stagesLoading.value || selectedStageIds.value.length === 0) return false
  return hasCollectedStages.value
})

async function loadSeasons() {
  seasons.value = await api.seasons()
  if (seasons.value.length && !seasons.value.some((item) => item.sourceSeasonId === seasonId.value)) {
    seasonId.value = seasons.value[0].sourceSeasonId
  }
}

async function loadStages() {
  const seq = ++loadStagesSeq
  querySeq++
  busy.value = false
  const sid = seasonId.value
  const type = VIEW_STAT_TYPE[activeView.value]
  stages.value = []
  selectedStageIds.value = []
  result.value = null
  teamResult.value = null
  playerResult.value = null
  notice.value = ''
  error.value = ''
  stagesLoading.value = true
  try {
    const data = await api.stages(sid, type)
    if (seq !== loadStagesSeq) return
    stages.value = data
    selectedStageIds.value = data.filter((s) => s.collected).map((s) => s.sourceStageId)
  } catch (reason) {
    if (seq !== loadStagesSeq) return
    error.value = reason instanceof Error ? reason.message : `加载赛段失败：${String(reason)}`
  } finally {
    if (seq === loadStagesSeq) stagesLoading.value = false
  }
}

async function query() {
  if (!canQuery.value) return
  const seq = ++querySeq
  const view = activeView.value
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    if (view === 'champion') {
      const data = await api.championStatistics(
        seasonId.value,
        selectedStageIds.value,
        minimumPickCount.value,
        sortBy.value,
        sortDirection.value,
      )
      if (seq === querySeq && activeView.value === view) result.value = data
    } else if (view === 'team') {
      const data = await api.teamStatistics(
        seasonId.value,
        selectedStageIds.value,
        minimumMatchCount.value,
        teamSortBy.value,
        sortDirection.value,
      )
      if (seq === querySeq && activeView.value === view) teamResult.value = data
    } else {
      const data = await api.playerStatistics(
        seasonId.value,
        selectedStageIds.value,
        minimumMatchCount.value,
        '',
        playerSortBy.value,
        sortDirection.value,
      )
      if (seq === querySeq && activeView.value === view) playerResult.value = data
    }
    if (seq === querySeq) notice.value = '查询完成'
  } catch (reason) {
    if (seq === querySeq) error.value = reason instanceof Error ? reason.message : String(reason)
  } finally {
    if (seq === querySeq) busy.value = false
  }
}

function switchView(view: ActiveView) {
  if (activeView.value === view) return
  activeView.value = view
  result.value = null
  teamResult.value = null
  playerResult.value = null
  notice.value = ''
  error.value = ''
  void loadStages()
}

function toggleStage(stageId: number, collected: boolean) {
  if (!collected) return
  selectedStageIds.value = selectedStageIds.value.includes(stageId)
    ? selectedStageIds.value.filter((id) => id !== stageId)
    : [...selectedStageIds.value, stageId]
}

function percent(value: number) {
  return `${(value * 100).toFixed(2)}%`
}

function fmtDecimal(value: number, digits = 2) {
  return value.toFixed(digits)
}

function fmtGold(value: number) {
  if (value < 0) return '-' + ((-value) / 1000).toFixed(1) + 'k'
  return (value / 1000).toFixed(1) + 'k'
}

function fmtPositions(positions: string[]) {
  return positions.join(' / ') || '—'
}

function fmtTeamNames(teamNames: string[]) {
  return teamNames.join(' / ') || '—'
}

watch(seasonId, () => { void loadStages() })

onMounted(async () => {
  try {
    await loadSeasons()
    await loadStages()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : String(reason)
  }
})
</script>

<template>
  <main class="shell">
    <header class="hero">
      <div>
        <p class="eyebrow">LOL DATA HUB</p>
        <h1>赛事数据，<span class="title-tail">不止看一个赛段</span></h1>
        <p class="hero-copy">基于本地持久化数据重新计算跨赛段指标，并用明确的样本门槛隔离低样本噪声。</p>
      </div>
      <div class="status-card">
        <span>数据版本</span>
        <strong>{{ currentDataVersion ?? '—' }}</strong>
        <small>{{ latestUpdatedAt ? `更新于 ${new Date(latestUpdatedAt).toLocaleString()}` : '尚未查询数据' }}</small>
      </div>
    </header>

    <nav class="view-tabs">
      <button
        class="tab-btn"
        :class="{ active: activeView === 'champion' }"
        @click="switchView('champion')"
      >
        英雄统计
      </button>
      <button
        class="tab-btn"
        :class="{ active: activeView === 'team' }"
        @click="switchView('team')"
      >
        战队统计
      </button>
      <button
        class="tab-btn"
        :class="{ active: activeView === 'player' }"
        @click="switchView('player')"
      >
        选手统计
      </button>
    </nav>

    <section class="panel controls">
      <div class="field">
        <label for="season">赛季</label>
        <select id="season" v-model.number="seasonId">
          <option v-if="!seasons.length" :value="seasonId">赛季 #{{ seasonId }}</option>
          <option v-for="season in seasons" :key="season.sourceSeasonId" :value="season.sourceSeasonId">
            {{ season.name }} · #{{ season.sourceSeasonId }}
          </option>
        </select>
      </div>
      <div v-if="activeView === 'champion'" class="field compact">
        <label for="minimum">最低出场次数</label>
        <input id="minimum" v-model.number="minimumPickCount" type="number" min="0" />
      </div>
      <div v-else class="field compact">
        <label for="minimumMatch">最低比赛场数</label>
        <input id="minimumMatch" v-model.number="minimumMatchCount" type="number" min="0" />
      </div>
      <div v-if="activeView === 'champion'" class="field compact">
        <label for="sort">排序指标</label>
        <select id="sort" v-model="sortBy">
          <option v-for="opt in CHAMPION_SORT_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </select>
      </div>
      <div v-else-if="activeView === 'team'" class="field compact">
        <label for="teamSort">排序指标</label>
        <select id="teamSort" v-model="teamSortBy">
          <option v-for="opt in TEAM_SORT_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </select>
      </div>
      <div v-else class="field compact">
        <label for="playerSort">排序指标</label>
        <select id="playerSort" v-model="playerSortBy">
          <option v-for="opt in PLAYER_SORT_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </select>
      </div>
      <div class="field compact">
        <label for="direction">排序方向</label>
        <select id="direction" v-model="sortDirection">
          <option value="desc">降序</option>
          <option value="asc">升序</option>
        </select>
      </div>
      <div class="actions">
        <button class="primary" :disabled="!canQuery" @click="query">{{ busy ? '处理中…' : '查询统计' }}</button>
      </div>

      <div class="stage-block">
        <div class="stage-heading">
          <span>选择一个或多个赛段</span>
          <small v-if="activeView === 'champion'">跨赛段：胜率等由可加总计数重算，不平均官网百分比</small>
          <small v-else-if="activeView === 'team'">跨赛段：胜率/击杀由可加总计数重算；眼位/经济/龙按比赛场数加权，非简单平均</small>
          <small v-else>跨赛段：KDA/击杀/助攻/死亡由计数重算；经济/补刀/视野及参团率/伤害占比等按比赛场数加权，比例为近似整合口径</small>
        </div>
        <div v-if="stagesLoading" class="empty-inline">正在加载赛段…</div>
        <div v-else-if="stages.length" class="stage-list">
          <button
            v-for="stage in stages"
            :key="stage.sourceStageId"
            class="stage-chip"
            :class="{ selected: selectedStageIds.includes(stage.sourceStageId), disabled: !stage.collected }"
            :disabled="!stage.collected"
            @click="toggleStage(stage.sourceStageId, stage.collected)"
          >
            <span>{{ stage.name }}</span>
            <small v-if="!stage.collected" class="uncollected-tag">未采集</small>
            <small v-else-if="stage.sampleBaseCount != null">{{ stage.sampleBaseCount }} 场</small>
          </button>
        </div>
        <p v-else class="empty-inline">
          {{ activeView === 'team' ? '该赛季暂无已采集战队数据。' : activeView === 'player' ? '该赛季暂无已采集选手数据。' : '该赛季暂无赛段数据。' }}
        </p>
      </div>
    </section>

    <section v-if="selectedStageIds.length > 0" class="query-summary">
      <span>已选 <strong>{{ selectedStageIds.length }}</strong> 个赛段</span>
      <span v-if="activeView === 'champion'">样本基数合计 <strong>{{ totalSampleBase }}</strong></span>
      <span>数据版本 <strong>{{ currentDataVersion ?? '—' }}</strong></span>
      <span v-if="latestUpdatedAt">最近更新 <strong>{{ new Date(latestUpdatedAt).toLocaleString() }}</strong></span>
    </section>

    <p v-if="error" class="message error">{{ error }}</p>
    <p v-else-if="notice" class="message success">{{ notice }}</p>

    <!-- 英雄统计面板 -->
    <section v-if="activeView === 'champion'" class="panel table-panel">
      <div class="table-toolbar">
        <div>
          <p class="eyebrow">CHAMPION STATISTICS</p>
          <h2>英雄统计</h2>
        </div>
        <div class="toolbar-right">
          <div class="position-filter">
            <button
              v-for="opt in CHAMPION_POSITION_OPTIONS"
              :key="opt.value"
              class="pos-chip"
              :class="{ active: positionFilter === opt.value }"
              @click="positionFilter = opt.value"
            >
              {{ opt.label }}
            </button>
          </div>
          <div class="search-wrap">
            <input v-model="search" type="search" placeholder="搜索英雄、称号" />
            <span>{{ filteredChampionItems.length }} 项</span>
          </div>
        </div>
      </div>

      <div v-if="filteredChampionItems.length" class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>英雄</th>
              <th>分路</th>
              <th>出场</th>
              <th>禁用</th>
              <th>BP 率</th>
              <th>胜场</th>
              <th>胜率</th>
              <th>KDA</th>
              <th>样本基数</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredChampionItems" :key="item.championId">
              <td>
                <div class="champion-cell">
                  <img v-if="item.championLogo" :src="item.championLogo" :alt="item.championName" />
                  <span class="champion-placeholder" v-else>{{ item.championName.slice(0, 1) }}</span>
                  <div><strong>{{ item.championName }}</strong><small>{{ item.championTitle }}</small></div>
                </div>
              </td>
              <td>{{ item.positions.join(' / ') || '—' }}</td>
              <td>{{ item.pickCount }}</td>
              <td>{{ item.banCount }}</td>
              <td class="accent">{{ percent(item.bpRate) }}</td>
              <td>{{ item.winningCount }}</td>
              <td class="accent">{{ percent(item.winningRate) }}</td>
              <td>{{ fmtDecimal(item.kda) }}</td>
              <td>{{ item.sampleBaseCount }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state">
        <strong v-if="!hasCollectedStages">该赛季暂无已采集赛段</strong>
        <strong v-else-if="!result">选择赛段后点击查询</strong>
        <strong v-else>无匹配结果</strong>
        <p v-if="!hasCollectedStages">请切换到其他赛季查看已采集的数据。</p>
      </div>
    </section>

    <!-- 战队统计面板 -->
    <section v-if="activeView === 'team'" class="panel table-panel">
      <div class="table-toolbar">
        <div>
          <p class="eyebrow">TEAM STATISTICS</p>
          <h2>战队统计</h2>
        </div>
        <div class="toolbar-right">
          <div class="search-wrap">
            <input v-model="teamSearch" type="search" placeholder="搜索战队" />
            <span>{{ filteredTeamItems.length }} 项</span>
          </div>
        </div>
      </div>

      <div v-if="filteredTeamItems.length" class="table-scroll">
        <table class="team-table">
          <thead>
            <tr>
              <th>战队</th>
              <th>比赛</th>
              <th>胜场</th>
              <th>胜率</th>
              <th>总击杀</th>
              <th>场均击杀</th>
              <th>场均死亡</th>
              <th>场均插眼</th>
              <th>场均排眼</th>
              <th>场均经济</th>
              <th>场均大龙</th>
              <th>场均小龙</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredTeamItems" :key="item.teamId">
              <td>
                <div class="team-cell">
                  <img v-if="item.teamLogo" :src="item.teamLogo" :alt="item.teamName" class="team-logo" />
                  <span class="team-placeholder" v-else>{{ item.teamName.slice(0, 1) }}</span>
                  <strong>{{ item.teamName }}</strong>
                </div>
              </td>
              <td>{{ item.matchCount }}</td>
              <td>{{ item.matchWinCount }}</td>
              <td class="accent">{{ percent(item.winningRate) }}</td>
              <td>{{ item.totalKills }}</td>
              <td>{{ fmtDecimal(item.killPerGame) }}</td>
              <td>{{ fmtDecimal(item.deathPerGame) }}</td>
              <td>{{ fmtDecimal(item.wardPlacedPerGame) }}</td>
              <td>{{ fmtDecimal(item.wardKilledPerGame) }}</td>
              <td>{{ fmtGold(item.goldPerGame) }}</td>
              <td>{{ fmtDecimal(item.baronKillPerGame) }}</td>
              <td>{{ fmtDecimal(item.drakeKillPerGame) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state">
        <strong v-if="!hasCollectedStages">该赛季暂无已采集战队数据</strong>
        <strong v-else-if="!teamResult">选择赛段后点击查询</strong>
        <strong v-else>无匹配结果</strong>
        <p v-if="!hasCollectedStages">请切换到其他赛季或采集战队数据后再查询。</p>
      </div>
    </section>

    <!-- 选手统计面板 -->
    <section v-if="activeView === 'player'" class="panel table-panel">
      <div class="table-toolbar">
        <div>
          <p class="eyebrow">PLAYER STATISTICS</p>
          <h2>选手统计</h2>
        </div>
        <div class="toolbar-right">
          <div class="position-filter">
            <button
              v-for="opt in PLAYER_POSITION_OPTIONS"
              :key="opt.value"
              class="pos-chip"
              :class="{ active: playerPositionFilter === opt.value }"
              @click="playerPositionFilter = opt.value"
            >
              {{ opt.label }}
            </button>
          </div>
          <div class="search-wrap">
            <input v-model="playerSearch" type="search" placeholder="搜索选手、战队" />
            <span>{{ filteredPlayerItems.length }} 项</span>
          </div>
        </div>
      </div>

      <div v-if="filteredPlayerItems.length" class="table-scroll">
        <table class="player-table">
          <thead>
            <tr>
              <th>选手</th>
              <th>位置</th>
              <th>比赛</th>
              <th>MVP</th>
              <th>KDA</th>
              <th>总击杀</th>
              <th>总助攻</th>
              <th>总死亡</th>
              <th>场均经济</th>
              <th>场均补刀</th>
              <th>参团率</th>
              <th>场均经济差</th>
              <th>伤害占比</th>
              <th>经济占比</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredPlayerItems" :key="item.playerKey">
              <td>
                <div class="player-cell">
                  <img v-if="item.playerAvatar" :src="item.playerAvatar" :alt="item.playerName" class="player-avatar" />
                  <span class="player-placeholder" v-else>{{ item.playerName.slice(0, 1) }}</span>
                  <div>
                    <strong>{{ item.playerName }}</strong>
                    <small>{{ fmtTeamNames(item.teamNames) }}</small>
                  </div>
                </div>
              </td>
              <td>{{ fmtPositions(item.positions) }}</td>
              <td>{{ item.matchCount }}</td>
              <td>{{ item.mvpCount }}</td>
              <td class="accent">{{ fmtDecimal(item.kda) }}</td>
              <td>{{ item.totalKills }}</td>
              <td>{{ item.totalAssists }}</td>
              <td>{{ item.totalDeaths }}</td>
              <td>{{ fmtGold(item.goldPerGame) }}</td>
              <td>{{ fmtDecimal(item.creepScorePerGame) }}</td>
              <td class="accent">{{ percent(item.killParticipantPercent) }}</td>
              <td>{{ fmtGold(item.goldGapPerGame) }}</td>
              <td class="accent">{{ percent(item.damagePercent) }}</td>
              <td>{{ percent(item.goldPercent) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state">
        <strong v-if="!hasCollectedStages">该赛季暂无已采集选手数据</strong>
        <strong v-else-if="!playerResult">选择赛段后点击查询</strong>
        <strong v-else>无匹配结果</strong>
        <p v-if="!hasCollectedStages">请切换到其他赛季或采集选手数据后再查询。</p>
      </div>
    </section>
  </main>
</template>
