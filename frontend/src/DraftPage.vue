<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  api,
  type ChampionCounterResult,
  type ChampionStatistics,
  type Stage,
  type TeamCombinationStatisticsResult,
  type TeamCombinationType,
} from './api'

/**
 * BP 模拟器：Ban/Pick 沙盘。
 * 推荐基于当前赛段的英雄胜率×样本（版本 Tier），并结合已选敌方英雄的克制数据与
 * 己方已选英雄的组合胜率给出参考；所有衍生查询按英雄/组合缓存，调用次数有界。
 */

const POSITIONS = ['TOP', 'JUN', 'MID', 'BOT', 'SUP'] as const
type Position = (typeof POSITIONS)[number]
const POSITION_LABELS: Record<Position, string> = {
  TOP: '上单', JUN: '打野', MID: '中路', BOT: '下路', SUP: '辅助',
}

/** 组合类型 → 两个位置（与后端 TeamCombinationType 的位置对一致）。 */
const COMBINATION_POSITION_PAIRS: Record<TeamCombinationType, [Position, Position]> = {
  MID_JUNGLE: ['JUN', 'MID'],
  BOT_SUPPORT: ['BOT', 'SUP'],
  TOP_JUNGLE: ['TOP', 'JUN'],
  TOP_MID: ['TOP', 'MID'],
  MID_BOT: ['MID', 'BOT'],
  TOP_SUPPORT: ['TOP', 'SUP'],
  JUNGLE_SUPPORT: ['JUN', 'SUP'],
  JUNGLE_BOT: ['JUN', 'BOT'],
  MID_SUPPORT: ['MID', 'SUP'],
  TOP_BOT: ['TOP', 'BOT'],
}

type Side = 'blue' | 'red'

interface BanSlot {
  kind: 'ban'
  side: Side
  championId: number | null
}

interface PickSlot {
  kind: 'pick'
  side: Side
  position: Position | null
  championId: number | null
}

type Slot = BanSlot | PickSlot

/** 真实 BP 顺序：三轮 Ban 交替；Pick 按 B R R B B R R B R B。 */
const SLOTS: Slot[] = [
  ...(['blue', 'red', 'blue', 'red', 'blue', 'red'] as Side[]).map<Slot>(
    (side) => ({ kind: 'ban', side, championId: null }),
  ),
  ...(
    [
      ['blue'], ['red', 'red'], ['blue', 'blue'], ['red', 'red'], ['blue'], ['red'], ['blue'],
    ] as Side[][]
  )
    .flat()
    .map<Slot>((side) => ({ kind: 'pick', side, position: null, championId: null })),
]

const stageKey = ref('')
const stages = ref<Stage[]>([])
const champions = ref<ChampionStatistics[]>([])
const loading = ref(false)
const error = ref('')
const searchKeyword = ref('')
const slots = ref<Slot[]>(SLOTS.map((slot) => ({ ...slot })))
const activeIndex = ref(0)
let loadSeq = 0

const championById = computed(() => {
  const map = new Map<number, ChampionStatistics>()
  for (const champion of champions.value) map.set(champion.championId, champion)
  return map
})

const usedChampionIds = computed(
  () => new Set(slots.value.map((slot) => slot.championId).filter((id): id is number => id != null)),
)

function championNameOf(championId: number): string {
  const champion = championById.value.get(championId)
  return champion?.championName || `#${championId}`
}

function championLogoOf(championId: number): string | null {
  return championById.value.get(championId)?.championLogo ?? null
}

/** 当前激活槽位之后的第一个空槽；Ban 完成后进入 Pick。 */
const activeSlot = computed<Slot | null>(() => {
  const slot = slots.value[activeIndex.value]
  return slot && slot.championId == null ? slot : null
})

const picks = computed(() => slots.value.filter((slot): slot is PickSlot => slot.kind === 'pick'))
const bans = computed(() => slots.value.filter((slot): slot is BanSlot => slot.kind === 'ban'))
const bluePicks = computed(() => picks.value.filter((slot) => slot.side === 'blue'))
const redPicks = computed(() => picks.value.filter((slot) => slot.side === 'red'))

function slotLabel(slot: Slot): string {
  const sideText = slot.side === 'blue' ? '蓝方' : '红方'
  if (slot.kind === 'ban') return `${sideText} Ban`
  const positionText = slot.position ? POSITION_LABELS[slot.position] : '自由'
  return `${sideText} Pick · ${positionText}`
}

function setActive(index: number) {
  const slot = slots.value[index]
  if (!slot || slot.championId != null) return
  activeIndex.value = index
}

function assignChampion(championId: number) {
  const slot = slots.value[activeIndex.value]
  if (!slot || slot.championId != null) return
  if (usedChampionIds.value.has(championId)) return
  slot.championId = championId
  const next = slots.value.findIndex((candidate) => candidate.championId == null)
  activeIndex.value = next >= 0 ? next : slots.value.length - 1
}

function clearSlot(index: number) {
  const slot = slots.value[index]
  if (!slot || slot.championId == null) return
  slot.championId = null
  if (slot.kind === 'pick') slot.position = null
  activeIndex.value = index
}

function resetDraft() {
  slots.value = SLOTS.map((slot) => ({ ...slot }))
  activeIndex.value = 0
  counterCache.clear()
  combinationCache.clear()
}

function assignPosition(slot: PickSlot, position: Position | null) {
  if (slot.championId != null) return
  const occupied = picks.value.some(
    (candidate) => candidate.side === slot.side && candidate.position === position,
  )
  slot.position = occupied ? null : position
}

/** 版本强度分：胜率 × log(1+出场)，用于排序推荐。 */
function tierScore(champion: ChampionStatistics): number {
  return Number(champion.winningRate) * Math.log1p(champion.pickCount)
}

const candidates = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  const slot = activeSlot.value
  let pool = champions.value.filter((champion) => !usedChampionIds.value.has(champion.championId))
  if (slot && slot.kind === 'pick' && slot.position) {
    pool = pool.filter((champion) => champion.positions.includes(slot.position ?? ''))
  }
  if (keyword) {
    pool = pool.filter((champion) =>
      `${champion.championName}${champion.championTitle ?? ''}`.toLowerCase().includes(keyword),
    )
  }
  return [...pool].sort((a, b) => tierScore(b) - tierScore(a)).slice(0, 24)
})

/* ---- 克制提示：对每个敌方已选英雄拉取一次对位数据（有界缓存） ---- */

const counterCache = new Map<number, ChampionCounterResult | null>()
const counterHints = ref<{ enemy: string; counters: string[] }[]>([])

async function refreshCounterHints() {
  const enemyIds = [...new Set(
    redPicks.value.map((slot) => slot.championId).filter((id): id is number => id != null),
  )]
  const hints: { enemy: string; counters: string[] }[] = []
  for (const enemyId of enemyIds.slice(0, 5)) {
    if (!counterCache.has(enemyId)) {
      counterCache.set(enemyId, null)
      try {
        const enemy = championById.value.get(enemyId)
        const position = enemy?.positions[0]
        if (!position) continue
        counterCache.set(enemyId, await api.championCounters(enemyId, [stageKey.value], position, 1))
      } catch {
        counterCache.set(enemyId, null)
      }
    }
    const data = counterCache.get(enemyId) ?? null
    if (data && data.opponents.length) {
      hints.push({
        enemy: championNameOf(enemyId),
        counters: data.opponents
          .slice(0, 3)
          .map((opponent) => `${opponent.championChineseName || opponent.championName} ${(opponent.winRate * 100).toFixed(0)}%`),
      })
    }
  }
  counterHints.value = hints
}

/* ---- 组合参考：己方相邻已选英雄的两位置组合胜率（按组合类型缓存） ---- */

const combinationCache = new Map<TeamCombinationType, TeamCombinationStatisticsResult | null>()
const combinationHints = ref<string[]>([])

async function refreshCombinationHints() {
  const picked = bluePicks.value
    .filter((slot): slot is PickSlot & { championId: number; position: Position } =>
      slot.championId != null && slot.position != null)
    .map((slot) => ({ championId: slot.championId, position: slot.position as Position }))
  const hints: string[] = []
  for (let i = 0; i < picked.length; i += 1) {
    for (let j = i + 1; j < picked.length; j += 1) {
      const first = picked[i]
      const second = picked[j]
      const entry = Object.entries(COMBINATION_POSITION_PAIRS).find(
        ([, pair]) =>
          (pair[0] === first.position && pair[1] === second.position)
          || (pair[1] === first.position && pair[0] === second.position),
      )
      if (!entry) continue
      const [type] = entry as [TeamCombinationType, [Position, Position]]
      if (!combinationCache.has(type)) {
        combinationCache.set(type, null)
        try {
          combinationCache.set(
            type,
            await api.teamCombinationStatisticsByKeys([stageKey.value], type, 1, 'winningRate', 'desc'),
          )
        } catch {
          combinationCache.set(type, null)
        }
      }
      const data = combinationCache.get(type) ?? null
      if (!data) continue
      const ids = [first.championId, second.championId]
      const row = data.items.find(
        (item) => ids.includes(item.firstChampionId) && ids.includes(item.secondChampionId),
      )
      if (row) {
        hints.push(
          `${championNameOf(first.championId)} + ${championNameOf(second.championId)}：${(row.winningRate * 100).toFixed(0)}% 胜率（${row.pickCount} 局）`,
        )
      }
    }
  }
  combinationHints.value = hints
}

watch(
  () => slots.value.map((slot) => slot.championId).join(','),
  () => {
    void refreshCounterHints()
    void refreshCombinationHints()
  },
)

/** 阵容对比：两侧平均胜率与 KDA。 */
const sideSummary = computed(() => {
  function summaryOf(sidePicks: PickSlot[]) {
    const picked = sidePicks
      .map((slot) => slot.championId)
      .filter((id): id is number => id != null)
      .map((id) => championById.value.get(id))
      .filter((champion): champion is ChampionStatistics => champion != null)
    if (!picked.length) return null
    return {
      count: picked.length,
      winRate: picked.reduce((sum, c) => sum + Number(c.winningRate), 0) / picked.length,
      kda: picked.reduce((sum, c) => sum + Number(c.kda), 0) / picked.length,
    }
  }
  return { blue: summaryOf(bluePicks.value), red: summaryOf(redPicks.value) }
})

onMounted(async () => {
  const seq = ++loadSeq
  loading.value = true
  try {
    const [availability] = await Promise.all([api.availability('HERO', true)])
    if (seq !== loadSeq) return
    stages.value = availability
    const first = availability[0]
    if (first) stageKey.value = `${first.sourceSeasonId}:${first.sourceStageId}`
  } catch (reason) {
    if (seq === loadSeq) error.value = reason instanceof Error ? reason.message : String(reason)
  } finally {
    if (seq === loadSeq) loading.value = false
  }
})

watch(stageKey, () => {
  if (!stageKey.value) return
  counterCache.clear()
  combinationCache.clear()
  void loadChampions()
}, { immediate: true })

async function loadChampions() {
  const seq = ++loadSeq
  loading.value = true
  error.value = ''
  try {
    const data = await api.championStatisticsByKeys([stageKey.value], 1, '', 'winningRate', 'desc')
    if (seq === loadSeq) champions.value = data.items
  } catch (reason) {
    if (seq === loadSeq) error.value = reason instanceof Error ? reason.message : String(reason)
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

function fmtPercent(value: number | null | undefined): string {
  return value == null ? '-' : `${(value * 100).toFixed(1)}%`
}
</script>

<template>
  <main class="shell draft-page">
    <header class="hero">
      <div>
        <h1>BP 模拟器</h1>
        <p class="hero-copy">按真实 Ban/Pick 顺序推演阵容。推荐基于所选赛段的版本强度（胜率×样本），并汇总敌方英雄的克制数据与己方英雄的组合胜率。</p>
      </div>
    </header>

    <section class="panel controls draft-controls">
      <div class="field">
        <label for="draft-stage">数据范围（单赛段）</label>
        <select id="draft-stage" v-model="stageKey">
          <option v-for="stage in stages" :key="`${stage.sourceSeasonId}:${stage.sourceStageId}`"
            :value="`${stage.sourceSeasonId}:${stage.sourceStageId}`">
            {{ stage.seasonName ?? `赛事#${stage.sourceSeasonId}` }} · {{ stage.name }}
          </option>
        </select>
      </div>
      <div class="actions">
        <button class="secondary" type="button" @click="resetDraft">重置 BP</button>
      </div>
    </section>

    <p v-if="loading" class="message success">加载中…</p>
    <p v-if="error" class="message error">{{ error }}</p>

    <div class="draft-columns">
      <section class="panel draft-board">
        <h2 class="board-title">Ban / Pick 沙盘</h2>
        <div class="draft-slot-list">
          <div
            v-for="(slot, index) in slots"
            :key="index"
            class="draft-slot"
            :class="{
              blue: slot.side === 'blue',
              red: slot.side === 'red',
              active: index === activeIndex && slot.championId == null,
              filled: slot.championId != null,
            }"
            @click="slot.championId == null ? setActive(index) : clearSlot(index)"
          >
            <span class="slot-kind">{{ slotLabel(slot) }}</span>
            <template v-if="slot.championId != null">
              <img v-if="championLogoOf(slot.championId)" :src="championLogoOf(slot.championId)!" :alt="championNameOf(slot.championId)" class="slot-logo" />
              <strong class="slot-name">{{ championNameOf(slot.championId) }}</strong>
            </template>
            <span v-else class="slot-empty">点击后在右侧选择英雄</span>
            <div
              v-if="slot.kind === 'pick' && slot.championId == null"
              class="slot-positions"
              @click.stop
            >
              <button
                v-for="position in POSITIONS"
                :key="position"
                class="pos-chip"
                :class="{ active: slot.position === position }"
                @click="assignPosition(slot, position)"
              >{{ POSITION_LABELS[position] }}</button>
            </div>
          </div>
        </div>
        <div v-if="sideSummary.blue || sideSummary.red" class="side-summary">
          <div>
            <strong>蓝方阵容</strong>
            <span v-if="sideSummary.blue">
              {{ sideSummary.blue.count }} 英雄 · 平均胜率 {{ fmtPercent(sideSummary.blue.winRate) }} · 平均 KDA {{ sideSummary.blue.kda.toFixed(2) }}
            </span>
          </div>
          <div>
            <strong>红方阵容</strong>
            <span v-if="sideSummary.red">
              {{ sideSummary.red.count }} 英雄 · 平均胜率 {{ fmtPercent(sideSummary.red.winRate) }} · 平均 KDA {{ sideSummary.red.kda.toFixed(2) }}
            </span>
          </div>
        </div>
      </section>

      <section class="panel draft-picker">
        <h2 class="board-title">
          {{ activeSlot ? (activeSlot.kind === 'ban' ? '选择禁用英雄' : '选择英雄') : 'BP 已完成' }}
        </h2>
        <input v-model="searchKeyword" type="search" placeholder="搜索英雄（中文名/英文名）" class="draft-search" />
        <div class="draft-candidates">
          <button
            v-for="champion in candidates"
            :key="champion.championId"
            class="candidate-chip"
            :disabled="!activeSlot"
            @click="assignChampion(champion.championId)"
          >
            <img v-if="champion.championLogo" :src="champion.championLogo" :alt="champion.championName" class="candidate-logo" />
            <span class="candidate-name">{{ champion.championName }}</span>
            <span class="candidate-meta">{{ fmtPercent(champion.winningRate) }} · {{ champion.pickCount }}局</span>
          </button>
        </div>
        <p v-if="!candidates.length" class="empty-inline">没有符合条件的候选英雄。</p>

        <div v-if="counterHints.length" class="hint-block">
          <h3>敌方英雄克制提示</h3>
          <p v-for="hint in counterHints" :key="hint.enemy" class="hint-line">
            对阵 <strong>{{ hint.enemy }}</strong> 胜率最高：{{ hint.counters.join('、') }}
          </p>
        </div>
        <div v-if="combinationHints.length" class="hint-block">
          <h3>己方组合参考</h3>
          <p v-for="hint in combinationHints" :key="hint" class="hint-line">{{ hint }}</p>
        </div>
      </section>
    </div>
  </main>
</template>

<style scoped>
.draft-page { padding-bottom: 40px; }
.draft-controls { margin-bottom: 16px; }
.draft-columns { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr); gap: 16px; align-items: start; }
@media (max-width: 1000px) { .draft-columns { grid-template-columns: 1fr; } }
.board-title { margin: 0 0 12px; font-size: 16px; }
.draft-slot-list { display: grid; gap: 8px; }
.draft-slot {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
  border: 1px solid var(--line); border-left-width: 4px; border-radius: 8px;
  padding: 9px 12px; cursor: pointer; background: var(--panel-2); min-height: 44px;
}
.draft-slot.blue { border-left-color: #2f6fed; }
.draft-slot.red { border-left-color: #c94040; }
.draft-slot.active { border-color: var(--accent); box-shadow: 0 0 0 2px var(--accent-soft); }
.draft-slot.filled { opacity: .92; }
.slot-kind { font-size: 12px; color: var(--text-3); font-weight: 700; min-width: 96px; }
.slot-logo { width: 30px; height: 30px; border-radius: 50%; object-fit: cover; }
.slot-name { font-size: 14px; }
.slot-empty { font-size: 12.5px; color: var(--text-4); }
.slot-positions { display: flex; gap: 4px; margin-left: auto; flex-wrap: wrap; }
.slot-positions .pos-chip { padding: 3px 7px; font-size: 11px; }
.side-summary { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: 14px; }
.side-summary > div { display: grid; gap: 3px; border: 1px solid var(--line); border-radius: 8px; padding: 10px 12px; }
.side-summary strong { font-size: 13px; }
.side-summary span { color: var(--text-2); font-size: 12.5px; }
.draft-search { width: 100%; box-sizing: border-box; padding: 9px 12px; border: 1px solid var(--line); border-radius: 7px; margin-bottom: 10px; }
.draft-candidates { display: grid; grid-template-columns: repeat(auto-fill, minmax(168px, 1fr)); gap: 7px; max-height: 420px; overflow: auto; }
.candidate-chip {
  display: flex; align-items: center; gap: 8px; padding: 7px 9px;
  border: 1px solid var(--line); border-radius: 8px; background: var(--panel-2);
  cursor: pointer; text-align: left;
}
.candidate-chip:hover:not(:disabled) { border-color: var(--accent-line); }
.candidate-chip:disabled { opacity: .5; cursor: not-allowed; }
.candidate-logo { width: 26px; height: 26px; border-radius: 50%; object-fit: cover; }
.candidate-name { font-size: 13px; font-weight: 650; }
.candidate-meta { margin-left: auto; font-size: 11.5px; color: var(--text-3); white-space: nowrap; }
.hint-block { margin-top: 16px; border-top: 1px dashed var(--line); padding-top: 10px; }
.hint-block h3 { margin: 0 0 6px; font-size: 13.5px; }
.hint-line { margin: 0 0 5px; font-size: 12.5px; color: var(--text-2); }
.hint-line strong { color: var(--accent-dark); }
.empty-inline { color: var(--text-4); font-size: 13px; }
</style>
