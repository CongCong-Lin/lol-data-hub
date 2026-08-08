<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { api, type ChampionStatistics, type ChampionStatisticsResult, type Season, type Stage } from './api'

const seasons = ref<Season[]>([])
const stages = ref<Stage[]>([])
const seasonId = ref(237)
const selectedStageIds = ref<number[]>([])
const minimumPickCount = ref(10)
const sortBy = ref('bpRate')
const search = ref('')
const result = ref<ChampionStatisticsResult | null>(null)
const busy = ref(false)
const notice = ref('')
const error = ref('')

const filteredItems = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  if (!keyword) return result.value?.items ?? []
  return (result.value?.items ?? []).filter((item) =>
    `${item.championName}${item.championTitle ?? ''}${item.positions.join('')}`.toLowerCase().includes(keyword),
  )
})

const latestUpdatedAt = computed(() => {
  const timestamps = (result.value?.items ?? []).map((item) => item.sourceUpdatedAt).filter(Boolean) as string[]
  return timestamps.sort().at(-1) ?? null
})

async function loadSeasons() {
  seasons.value = await api.seasons()
  if (seasons.value.length && !seasons.value.some((item) => item.sourceSeasonId === seasonId.value)) {
    seasonId.value = seasons.value[0].sourceSeasonId
  }
}

async function loadStages() {
  stages.value = await api.stages(seasonId.value)
  selectedStageIds.value = selectedStageIds.value.filter((id) => stages.value.some((stage) => stage.sourceStageId === id))
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

async function syncCatalog() {
  await run('赛事目录同步完成', async () => {
    await api.syncCatalog(seasonId.value)
    await loadSeasons()
    await loadStages()
  })
}

async function collect() {
  if (!selectedStageIds.value.length) {
    error.value = '请至少选择一个赛段'
    return
  }
  await run('英雄数据采集完成', async () => {
    await api.collectHeroes(seasonId.value, selectedStageIds.value)
    await query()
  })
}

async function query() {
  if (!selectedStageIds.value.length) {
    error.value = '请至少选择一个赛段'
    return
  }
  await run('查询完成', async () => {
    result.value = await api.championStatistics(
      seasonId.value,
      selectedStageIds.value,
      minimumPickCount.value,
      sortBy.value,
    )
  })
}

function toggleStage(stageId: number) {
  selectedStageIds.value = selectedStageIds.value.includes(stageId)
    ? selectedStageIds.value.filter((id) => id !== stageId)
    : [...selectedStageIds.value, stageId]
}

function percent(value: number) {
  return `${(value * 100).toFixed(2)}%`
}

watch(seasonId, () => void loadStages())

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
        <h1>赛事数据，不止看一个赛段</h1>
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
      <div class="actions">
        <button class="ghost" :disabled="busy" @click="syncCatalog">同步目录</button>
        <button class="ghost" :disabled="busy" @click="collect">人工采集</button>
        <button class="primary" :disabled="busy" @click="query">{{ busy ? '处理中…' : '查询统计' }}</button>
      </div>

      <div class="stage-block">
        <div class="stage-heading">
          <span>选择一个或多个赛段</span>
          <small>跨赛段结果按计数重新计算，不平均官网百分比</small>
        </div>
        <div v-if="stages.length" class="stage-list">
          <button
            v-for="stage in stages"
            :key="stage.sourceStageId"
            class="stage-chip"
            :class="{ selected: selectedStageIds.includes(stage.sourceStageId) }"
            @click="toggleStage(stage.sourceStageId)"
          >
            <span>{{ stage.name }}</span>
            <small>#{{ stage.sourceStageId }}</small>
          </button>
        </div>
        <p v-else class="empty-inline">本地还没有该赛季目录，请先点击“同步目录”。</p>
      </div>
    </section>

    <p v-if="error" class="message error">{{ error }}</p>
    <p v-else-if="notice" class="message success">{{ notice }}</p>

    <section class="panel table-panel">
      <div class="table-toolbar">
        <div>
          <p class="eyebrow">CHAMPION STATISTICS</p>
          <h2>英雄统计</h2>
        </div>
        <div class="search-wrap">
          <input v-model="search" type="search" placeholder="搜索英雄、称号或分路" />
          <span>{{ filteredItems.length }} 项</span>
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
        <strong>等待第一批数据</strong>
        <p>同步赛事目录，选择赛段并人工采集后，即可在这里查询跨赛段英雄统计。</p>
      </div>
    </section>
  </main>
</template>

