<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { api, type ChampionStatistics, type ChampionStatisticsResult, type Season, type Stage } from './api'

const POSITION_OPTIONS = [
  { value: '', label: '全部' },
  { value: 'TOP', label: '上单' },
  { value: 'JUN', label: '打野' },
  { value: 'MID', label: '中路' },
  { value: 'BOT', label: '下路' },
  { value: 'SUP', label: '辅助' },
]

const seasons = ref<Season[]>([])
const stages = ref<Stage[]>([])
const seasonId = ref(237)
const selectedStageIds = ref<number[]>([])
const minimumPickCount = ref(10)
const sortBy = ref('bpRate')
const sortDirection = ref('desc')
const positionFilter = ref('')
const search = ref('')
const result = ref<ChampionStatisticsResult | null>(null)
const busy = ref(false)
const stagesLoading = ref(false)
const notice = ref('')
const error = ref('')
let loadStagesSeq = 0

const collectedStages = computed(() => stages.value.filter((s) => s.collected))
const hasCollectedStages = computed(() => collectedStages.value.length > 0)

const selectedStages = computed(() =>
  stages.value.filter((s) => selectedStageIds.value.includes(s.sourceStageId)),
)

const totalSampleBase = computed(() =>
  selectedStages.value.reduce((sum, s) => sum + (s.sampleBaseCount ?? 0), 0),
)

const filteredItems = computed(() => {
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

const latestUpdatedAt = computed(() => {
  const timestamps = (result.value?.items ?? []).map((item) => item.sourceUpdatedAt).filter(Boolean) as string[]
  return timestamps.sort().at(-1) ?? null
})

const canQuery = computed(
  () => !busy.value && !stagesLoading.value && hasCollectedStages.value && selectedStageIds.value.length > 0,
)

async function loadSeasons() {
  seasons.value = await api.seasons()
  if (seasons.value.length && !seasons.value.some((item) => item.sourceSeasonId === seasonId.value)) {
    seasonId.value = seasons.value[0].sourceSeasonId
  }
}

async function loadStages() {
  const seq = ++loadStagesSeq
  const sid = seasonId.value
  stages.value = []
  selectedStageIds.value = []
  result.value = null
  notice.value = ''
  error.value = ''
  stagesLoading.value = true
  try {
    const data = await api.stages(sid)
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

async function run(label: string, action: () => Promise<unknown>) {
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await action()
    notice.value = label
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : String(reason)
  } finally {
    busy.value = false
  }
}

async function query() {
  if (!canQuery.value) return
  await run('查询完成', async () => {
    result.value = await api.championStatistics(
      seasonId.value,
      selectedStageIds.value,
      minimumPickCount.value,
      sortBy.value,
      sortDirection.value,
    )
  })
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
        <strong>{{ result?.dataVersion ?? '—' }}</strong>
        <small>{{ latestUpdatedAt ? `官网更新于 ${new Date(latestUpdatedAt).toLocaleString()}` : '尚未查询数据' }}</small>
      </div>
    </header>

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
      <div class="field compact">
        <label for="minimum">最低出场次数</label>
        <input id="minimum" v-model.number="minimumPickCount" type="number" min="0" />
      </div>
      <div class="field compact">
        <label for="sort">排序指标</label>
        <select id="sort" v-model="sortBy">
          <option value="bpRate">BP 率</option>
          <option value="winningRate">胜率</option>
          <option value="pickCount">出场次数</option>
          <option value="pickRate">出场率</option>
          <option value="banRate">禁用率</option>
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
          <small>跨赛段结果按计数重新计算，不平均官网百分比</small>
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
        <p v-else class="empty-inline">该赛季暂无赛段数据。</p>
      </div>
    </section>

    <section v-if="selectedStageIds.length > 0" class="query-summary">
      <span>已选 <strong>{{ selectedStageIds.length }}</strong> 个赛段</span>
      <span>样本基数合计 <strong>{{ totalSampleBase }}</strong></span>
      <span>数据版本 <strong>{{ result?.dataVersion ?? '—' }}</strong></span>
      <span v-if="latestUpdatedAt">最近更新 <strong>{{ new Date(latestUpdatedAt).toLocaleString() }}</strong></span>
    </section>

    <p v-if="error" class="message error">{{ error }}</p>
    <p v-else-if="notice" class="message success">{{ notice }}</p>

    <section class="panel table-panel">
      <div class="table-toolbar">
        <div>
          <p class="eyebrow">CHAMPION STATISTICS</p>
          <h2>英雄统计</h2>
        </div>
        <div class="toolbar-right">
          <div class="position-filter">
            <button
              v-for="opt in POSITION_OPTIONS"
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
            <span>{{ filteredItems.length }} 项</span>
          </div>
        </div>
      </div>

      <div v-if="filteredItems.length" class="table-scroll">
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
            <tr v-for="item in filteredItems" :key="item.championId">
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
              <td>{{ item.kda.toFixed(2) }}</td>
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
  </main>
</template>
