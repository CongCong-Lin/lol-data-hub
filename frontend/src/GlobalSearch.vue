<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  api,
  type ChampionStatistics,
  type PlayerStatistics,
  type TeamStatistics,
} from './api'
import { useI18n } from './i18n'

const props = defineProps<{ stageKeys: string[] }>()

const { t } = useI18n()

const keyword = ref('')
const open = ref(false)
const searching = ref(false)
const error = ref('')
const champions = ref<ChampionStatistics[]>([])
const teams = ref<TeamStatistics[]>([])
const players = ref<PlayerStatistics[]>([])
let searchSeq = 0
let closeTimer: ReturnType<typeof setTimeout> | undefined

const hasStages = computed(() => props.stageKeys.length > 0)

const isEmpty = computed(
  () => !searching.value && !error.value && !champions.value.length && !teams.value.length && !players.value.length,
)

watch(keyword, (value) => {
  if (!value.trim()) {
    open.value = false
    champions.value = []
    teams.value = []
    players.value = []
    error.value = ''
    return
  }
  void search()
})

function delayClose() {
  closeTimer = setTimeout(() => {
    open.value = false
  }, 150)
}

function cancelClose() {
  if (closeTimer) clearTimeout(closeTimer)
}

async function search() {
  const keys = props.stageKeys
  if (!keys.length) {
    error.value = t('search.noStages')
    open.value = true
    return
  }
  const seq = ++searchSeq
  searching.value = true
  error.value = ''
  open.value = true
  try {
    const [championData, teamData, playerData] = await Promise.all([
      api.championStatisticsByKeys(keys, 3, '', 'bpRate', 'desc'),
      api.teamStatisticsByKeys(keys, 3, 'winningRate', 'desc'),
      api.playerStatisticsByKeys(keys, 3, '', 'kda', 'desc'),
    ])
    if (seq !== searchSeq) return
    const text = keyword.value.trim().toLowerCase()
    champions.value = championData.items
      .filter((item) => `${item.championName}${item.championTitle ?? ''}`.toLowerCase().includes(text))
      .slice(0, 3)
    teams.value = teamData.items
      .filter((item) => item.teamName.toLowerCase().includes(text))
      .slice(0, 3)
    players.value = playerData.items
      .filter((item) => `${item.playerName}${item.teamNames.join('')}`.toLowerCase().includes(text))
      .slice(0, 3)
    if (!champions.value.length && !teams.value.length && !players.value.length) {
      error.value = t('search.noResult')
    }
  } catch (reason) {
    if (seq === searchSeq) error.value = reason instanceof Error ? reason.message : String(reason)
  } finally {
    if (seq === searchSeq) searching.value = false
  }
}

function returnTo(): string {
  if (typeof window === 'undefined') return '/'
  return window.location.pathname + window.location.search
}

function stageKeysParam(): string {
  return props.stageKeys.join(',')
}

function championHref(champion: ChampionStatistics): string {
  const params = new URLSearchParams({
    stageKeys: stageKeysParam(),
    minimumPickCount: '10',
    returnTo: returnTo(),
  })
  return `/champions/${champion.championId}?${params.toString()}`
}

function teamHref(team: TeamStatistics): string {
  const params = new URLSearchParams({
    stageKeys: stageKeysParam(),
    minimumMatchCount: '5',
    returnTo: returnTo(),
  })
  return `/teams/${team.teamId}?${params.toString()}`
}

function playerHref(player: PlayerStatistics): string {
  const position = player.positions[0] ?? ''
  const params = new URLSearchParams({
    stageKeys: stageKeysParam(),
    position,
    minimumMatchCount: '5',
    returnTo: returnTo(),
  })
  if (player.sourcePlayerId == null) return '#'
  return `/players/${player.sourcePlayerId}?${params.toString()}`
}
</script>

<template>
  <div class="global-search">
    <input
      v-model="keyword"
      type="search"
      class="global-search-input"
      :placeholder="t('search.placeholder')"
      @focus="open = true"
      @blur="delayClose"
      @keydown.esc="open = false"
    />
    <div v-if="open && keyword.trim()" class="global-search-panel" @mousedown.prevent="cancelClose" @mouseenter="cancelClose" @mouseleave="delayClose">
      <p v-if="!hasStages" class="gs-hint">{{ t('search.noStages') }}</p>
      <template v-else>
        <p v-if="searching" class="gs-hint">{{ t('common.searching') }}</p>
        <p v-else-if="error" class="gs-hint">{{ error }}</p>
        <template v-else-if="!isEmpty">
          <section v-if="champions.length" class="gs-group">
            <h3 class="gs-group-title">{{ t('search.champions') }}</h3>
            <a v-for="champion in champions" :key="champion.championId" :href="championHref(champion)" class="gs-item">
              <img v-if="champion.championLogo" :src="champion.championLogo" :alt="champion.championName" class="gs-logo" />
              <span class="gs-logo gs-logo-placeholder" v-else>{{ champion.championName.slice(0, 1) }}</span>
              <span class="gs-item-text">
                <strong>{{ champion.championName }}</strong>
                <small>{{ champion.championTitle }}</small>
              </span>
              <span class="gs-meta">{{ champion.pickCount }} 次出场</span>
            </a>
          </section>
          <section v-if="teams.length" class="gs-group">
            <h3 class="gs-group-title">{{ t('search.teams') }}</h3>
            <a v-for="team in teams" :key="team.teamId" :href="teamHref(team)" class="gs-item">
              <img v-if="team.teamLogo" :src="team.teamLogo" :alt="team.teamName" class="gs-logo gs-team-logo" />
              <span class="gs-logo gs-team-logo gs-logo-placeholder" v-else>{{ team.teamName.slice(0, 1) }}</span>
              <span class="gs-item-text">
                <strong>{{ team.teamName }}</strong>
                <small>{{ team.matchCount }} 个系列赛</small>
              </span>
              <span class="gs-meta">{{ (team.winningRate * 100).toFixed(1) }}%</span>
            </a>
          </section>
          <section v-if="players.length" class="gs-group">
            <h3 class="gs-group-title">{{ t('search.players') }}</h3>
            <a v-for="player in players" :key="player.playerKey" :href="playerHref(player)" class="gs-item">
              <img v-if="player.playerAvatar" :src="player.playerAvatar" :alt="player.playerName" class="gs-logo" />
              <span class="gs-logo gs-logo-placeholder" v-else>{{ player.playerName.slice(0, 1) }}</span>
              <span class="gs-item-text">
                <strong>{{ player.playerName }}</strong>
                <small>{{ player.teamNames.join(' / ') }}</small>
              </span>
              <span class="gs-meta">KDA {{ player.kda.toFixed(2) }}</span>
            </a>
          </section>
        </template>
      </template>
    </div>
  </div>
</template>

<style scoped>
.global-search { position: relative; flex: 0 1 320px; min-width: 200px; }
.global-search-input { min-height: 34px; padding: 7px 11px; font-size: 13px; }
.global-search-panel {
  position: absolute; z-index: 60; top: calc(100% + 7px); right: 0;
  width: min(420px, calc(100vw - 40px)); max-height: 480px; overflow-y: auto;
  padding: 8px; border: 1px solid var(--line-strong); border-radius: 8px;
  background: var(--panel); box-shadow: 0 12px 28px var(--shadow);
}
.gs-hint { margin: 6px 8px; color: var(--muted); font-size: 12.5px; }
.gs-group { margin-bottom: 6px; }
.gs-group-title { margin: 6px 8px 4px; color: var(--accent); font-size: 11px; letter-spacing: .08em; font-weight: 750; }
.gs-item { display: flex; align-items: center; gap: 9px; padding: 7px 8px; border-radius: 6px; color: inherit; text-decoration: none; }
.gs-item:hover { background: var(--panel-2); }
.gs-logo { flex: 0 0 auto; width: 28px; height: 28px; border-radius: 50%; object-fit: cover; background: var(--placeholder-bg); }
.gs-team-logo { border-radius: 5px; }
.gs-logo-placeholder { display: grid; place-items: center; color: var(--accent); font-weight: 750; font-size: 12px; }
.gs-item-text { flex: 1; min-width: 0; }
.gs-item-text strong, .gs-item-text small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.gs-item-text strong { color: var(--text); font-size: 13px; }
.gs-item-text small { margin-top: 1px; color: var(--text-4); font-size: 11px; }
.gs-meta { flex: 0 0 auto; color: var(--muted); font-size: 11.5px; font-variant-numeric: tabular-nums; }
@media (max-width: 860px) {
  .global-search { flex: 1 1 100%; }
}
</style>
