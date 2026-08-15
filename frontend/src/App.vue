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
  type TeamCombinationStatisticsResult,
  type TeamCombinationType,
  type TeamStatistics,
  type TeamStatisticsResult,
} from './api'
import PaginationControls from './PaginationControls.vue'
import ColumnVisibilityMenu, { type ColumnOption } from './ColumnVisibilityMenu.vue'
import SortableHeader from './SortableHeader.vue'
import SiteNav from './SiteNav.vue'
import { formatPercent } from './formatters'

type ActiveView = 'champion' | 'team' | 'player' | 'combo'

const CHAMPION_COLUMNS: ColumnOption[] = [
  { key: 'champion', label: '英雄' }, { key: 'positions', label: '分路' },
  { key: 'pickCount', label: '出场' }, { key: 'pickRate', label: '出场率' },
  { key: 'banCount', label: '禁用' }, { key: 'banRate', label: '禁用率' },
  { key: 'bpRate', label: 'BP 率' }, { key: 'winningCount', label: '胜场' },
  { key: 'winningRate', label: '胜率' }, { key: 'totalKills', label: '总击杀' },
  { key: 'killPerGame', label: '场均击杀' }, { key: 'totalAssists', label: '总助攻' },
  { key: 'assistPerGame', label: '场均助攻' }, { key: 'totalDeaths', label: '总死亡' },
  { key: 'deathPerGame', label: '场均死亡' }, { key: 'kda', label: 'KDA' },
  { key: 'mostUsedPlayers', label: '常用选手' },
]

const TEAM_COLUMNS: ColumnOption[] = [
  { key: 'team', label: '战队' }, { key: 'matchCount', label: '系列赛' },
  { key: 'gameCount', label: '对局' }, { key: 'matchWinCount', label: '胜场' },
  { key: 'winningRate', label: '胜率' }, { key: 'kda', label: 'KDA' }, { key: 'totalKills', label: '总击杀' },
  { key: 'killPerGame', label: '场均击杀' }, { key: 'deathPerGame', label: '场均死亡' },
  { key: 'damagePerGame', label: '场均输出' },
  { key: 'damagePerMinute', label: '每分钟输出' }, { key: 'averageGameDurationSeconds', label: '场均时长' },
  { key: 'goldPerMinute', label: '每分钟经济' }, { key: 'creepScorePerMinute', label: '每分钟补刀' },
  { key: 'wardPlacedPerMinute', label: '每分钟插眼' }, { key: 'wardKilledPerMinute', label: '每分钟拆眼' },
  { key: 'drakeControlRate', label: '小龙控制率' }, { key: 'baronControlRate', label: '大龙控制率' },
  { key: 'firstBloodRate', label: '一血率' }, { key: 'turretKillPerGame', label: '场均推塔' },
  { key: 'turretLostPerGame', label: '场均被推塔' },
  { key: 'wardPlacedPerGame', label: '场均插眼' }, { key: 'wardKilledPerGame', label: '场均排眼' },
  { key: 'goldPerGame', label: '场均经济' }, { key: 'baronKillPerGame', label: '场均大龙' },
  { key: 'drakeKillPerGame', label: '场均小龙' },
]

const PLAYER_COLUMNS: ColumnOption[] = [
  { key: 'player', label: '选手' }, { key: 'positions', label: '位置' },
  { key: 'matchCount', label: '系列赛' }, { key: 'gameCount', label: '对局' },
  { key: 'mvpCount', label: 'MVP' }, { key: 'mvpVotes', label: 'MVP 票数' },
  { key: 'kda', label: 'KDA' }, { key: 'totalKills', label: '总击杀' },
  { key: 'killPerGame', label: '场均击杀' }, { key: 'totalAssists', label: '总助攻' },
  { key: 'assistPerGame', label: '场均助攻' }, { key: 'totalDeaths', label: '总死亡' },
  { key: 'deathPerGame', label: '场均死亡' }, { key: 'goldPerGame', label: '场均经济' },
  { key: 'creepScorePerGame', label: '场均补刀' }, { key: 'killParticipantPercent', label: '参团率' },
  { key: 'goldGapPerGame', label: '场均经济差' }, { key: 'damagePerGame', label: '场均伤害' },
  { key: 'damagePercent', label: '伤害占比' },
  { key: 'goldPercent', label: '经济占比' },
]

const COMBINATION_COLUMNS: ColumnOption[] = [
  { key: 'team', label: '战队' }, { key: 'firstChampion', label: '英雄一' },
  { key: 'secondChampion', label: '英雄二' }, { key: 'pickCount', label: '选取次数' },
  { key: 'validGameCount', label: '有效小局' }, { key: 'pickRate', label: '选取率' },
  { key: 'winningCount', label: '获胜次数' }, { key: 'winningRate', label: '组合胜率' },
]

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

const VIEW_STAT_TYPE: Record<ActiveView, StatisticType> = {
  champion: 'HERO',
  team: 'TEAM',
  player: 'PLAYER',
  combo: 'COMBO',
}

const MAX_STAGE_SELECTION = 50

function makeKey(seasonId: number, stageId: number): string {
  return `${seasonId}:${stageId}`
}

const activeView = ref<ActiveView>('champion')
const seasons = ref<Season[]>([])
const allAvailability = ref<Stage[]>([])
const browsedSeasonId = ref(0)
const selectedStageKeys = ref<Set<string>>(new Set())
const minimumPickCount = ref(10)
const minimumMatchCount = ref(5)
const sortBy = ref('bpRate')
const teamSortBy = ref('winningRate')
const playerSortBy = ref('kda')
const combinationSortBy = ref('pickCount')
const championSortDirection = ref<'asc' | 'desc'>('desc')
const teamSortDirection = ref<'asc' | 'desc'>('desc')
const playerSortDirection = ref<'asc' | 'desc'>('desc')
const combinationSortDirection = ref<'asc' | 'desc'>('desc')
const combinationType = ref<TeamCombinationType>('MID_JUNGLE')
const minimumCombinationPickCount = ref(3)
const positionFilter = ref('')
const playerPositionFilter = ref('')
const search = ref('')
const teamSearch = ref('')
const playerSearch = ref('')
const combinationSearch = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const championVisibleColumns = ref(CHAMPION_COLUMNS.map((column) => column.key))
const teamVisibleColumns = ref(TEAM_COLUMNS.map((column) => column.key))
const playerVisibleColumns = ref(PLAYER_COLUMNS.map((column) => column.key))
const combinationVisibleColumns = ref(COMBINATION_COLUMNS.map((column) => column.key))
const result = ref<ChampionStatisticsResult | null>(null)
const teamResult = ref<TeamStatisticsResult | null>(null)
const playerResult = ref<PlayerStatisticsResult | null>(null)
const submittedPlayerQuery = ref<{ stageKeys: string[]; position: string; minimumMatchCount: number } | null>(null)
const combinationResult = ref<TeamCombinationStatisticsResult | null>(null)
const busy = ref(false)
const sorting = ref(false)
const exporting = ref(false)
const availabilityLoading = ref(false)
const notice = ref('')
const error = ref('')
let loadAvailabilitySeq = 0
let querySeq = 0

const PLAYER_SORT_FIELDS = new Set([
  'playerName',
  'positions',
  ...PLAYER_COLUMNS.map((column) => column.key).filter((key) => key !== 'player' && key !== 'positions'),
])
const PLAYER_POSITION_VALUES = new Set(PLAYER_POSITION_OPTIONS.map((option) => option.value))
const PAGE_SIZE_VALUES = new Set([10, 20, 50, 100])

const CHAMPION_SORT_FIELDS = new Set([
  'championName',
  'positions',
  ...CHAMPION_COLUMNS.map((column) => column.key).filter((key) => key !== 'champion' && key !== 'positions'),
])
const TEAM_SORT_FIELDS = new Set([
  'teamName',
  ...TEAM_COLUMNS.map((column) => column.key).filter((key) => key !== 'team'),
])
const COMBINATION_SORT_FIELDS = new Set([
  'teamName',
  'firstChampionName',
  'secondChampionName',
  ...COMBINATION_COLUMNS.map((column) => column.key).filter(
    (key) => key !== 'team' && key !== 'firstChampion' && key !== 'secondChampion',
  ),
])
const CHAMPION_POSITION_VALUES = new Set(CHAMPION_POSITION_OPTIONS.map((option) => option.value))
const COMBINATION_TYPE_VALUES = new Set<TeamCombinationType>([
  'MID_JUNGLE', 'BOT_SUPPORT', 'TOP_JUNGLE', 'TOP_MID', 'MID_BOT',
])

function parsePositiveInteger(value: string | null): number | null {
  if (!value || !/^\d+$/.test(value)) return null
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
}

function restoreRestorableColumns(params: URLSearchParams, name: string, definitions: ColumnOption[]) {
  const restored = (params.get(name) ?? '')
    .split(',')
    .filter((key) => definitions.some((column) => column.key === key))
  if (restored.length > 0) return [...new Set(restored)]
  return definitions.map((column) => column.key)
}

/** 从 URL 恢复各 tab 的查询状态；返回是否有已选赛段。 */
function restoreQueryFromLocation(): boolean {
  if (typeof window === 'undefined') return false
  const params = new URLSearchParams(window.location.search)
  const view = params.get('view')
  if (view === 'champion' || view === 'team' || view === 'player' || view === 'combo') {
    activeView.value = view
  } else if (view !== null) {
    return false
  }

  const seasonId = parsePositiveInteger(params.get('season'))
  if (seasonId != null) browsedSeasonId.value = seasonId

  const restoredStageKeys = (params.get('stageKeys') ?? '')
    .split(',')
    .map((key) => key.trim())
    .filter((key) => /^\d+:\d+$/.test(key))
    .slice(0, MAX_STAGE_SELECTION)
  selectedStageKeys.value = new Set(restoredStageKeys)

  const minimumRaw = params.get('minimumMatchCount')
  if (minimumRaw !== null) {
    const minimum = Number(minimumRaw)
    if (Number.isInteger(minimum) && minimum >= 0 && minimum <= 10_000) minimumMatchCount.value = minimum
  }
  const minimumPickRaw = params.get('minimumPickCount')
  if (minimumPickRaw !== null) {
    const minimumPick = Number(minimumPickRaw)
    if (Number.isInteger(minimumPick) && minimumPick >= 0 && minimumPick <= 10_000) minimumPickCount.value = minimumPick
  }
  const minimumComboRaw = params.get('minimumCombinationPickCount')
  if (minimumComboRaw !== null) {
    const minimumCombo = Number(minimumComboRaw)
    if (Number.isInteger(minimumCombo) && minimumCombo >= 0 && minimumCombo <= 10_000) {
      minimumCombinationPickCount.value = minimumCombo
    }
  }

  const position = params.get('position') ?? ''
  if (CHAMPION_POSITION_VALUES.has(position)) positionFilter.value = position
  const playerPosition = params.get('playerPosition') ?? ''
  if (PLAYER_POSITION_VALUES.has(playerPosition)) playerPositionFilter.value = playerPosition

  const comboType = params.get('combinationType') ?? ''
  if (COMBINATION_TYPE_VALUES.has(comboType as TeamCombinationType)) {
    combinationType.value = comboType as TeamCombinationType
  }

  const restoredChampionSort = params.get('championSortBy') ?? ''
  if (CHAMPION_SORT_FIELDS.has(restoredChampionSort)) sortBy.value = restoredChampionSort
  const restoredChampionDirection = params.get('championSortDirection')
  if (restoredChampionDirection === 'asc' || restoredChampionDirection === 'desc') {
    championSortDirection.value = restoredChampionDirection
  }
  const restoredTeamSort = params.get('teamSortBy') ?? ''
  if (TEAM_SORT_FIELDS.has(restoredTeamSort)) teamSortBy.value = restoredTeamSort
  const restoredTeamDirection = params.get('teamSortDirection')
  if (restoredTeamDirection === 'asc' || restoredTeamDirection === 'desc') {
    teamSortDirection.value = restoredTeamDirection
  }
  const restoredPlayerSort = params.get('playerSortBy') ?? ''
  if (PLAYER_SORT_FIELDS.has(restoredPlayerSort)) playerSortBy.value = restoredPlayerSort
  const restoredPlayerDirection = params.get('playerSortDirection')
  if (restoredPlayerDirection === 'asc' || restoredPlayerDirection === 'desc') {
    playerSortDirection.value = restoredPlayerDirection
  }
  const restoredCombinationSort = params.get('combinationSortBy') ?? ''
  if (COMBINATION_SORT_FIELDS.has(restoredCombinationSort)) combinationSortBy.value = restoredCombinationSort
  const restoredCombinationDirection = params.get('combinationSortDirection')
  if (restoredCombinationDirection === 'asc' || restoredCombinationDirection === 'desc') {
    combinationSortDirection.value = restoredCombinationDirection
  }

  search.value = params.get('championSearch') ?? ''
  teamSearch.value = params.get('teamSearch') ?? ''
  playerSearch.value = params.get('playerSearch') ?? ''
  combinationSearch.value = params.get('combinationSearch') ?? ''

  championVisibleColumns.value = restoreRestorableColumns(params, 'championColumns', CHAMPION_COLUMNS)
  teamVisibleColumns.value = restoreRestorableColumns(params, 'teamColumns', TEAM_COLUMNS)
  playerVisibleColumns.value = restoreRestorableColumns(params, 'playerColumns', PLAYER_COLUMNS)
  combinationVisibleColumns.value = restoreRestorableColumns(params, 'combinationColumns', COMBINATION_COLUMNS)

  const page = parsePositiveInteger(params.get('page'))
  if (page != null) currentPage.value = page
  const restoredPageSize = Number(params.get('pageSize'))
  if (PAGE_SIZE_VALUES.has(restoredPageSize)) pageSize.value = restoredPageSize

  return restoredStageKeys.length > 0
}

/** 序列化当前查询状态为 URL query；供 URL 同步与详情页 returnTo 使用。 */
function buildQueryString(): string {
  const params = new URLSearchParams()
  params.set('view', activeView.value)
  params.set('season', String(browsedSeasonId.value))
  if (selectedStageKeys.value.size) params.set('stageKeys', [...selectedStageKeys.value].join(','))
  params.set('page', String(currentPage.value))
  params.set('pageSize', String(pageSize.value))
  if (activeView.value === 'champion') {
    params.set('minimumPickCount', String(minimumPickCount.value))
    if (positionFilter.value) params.set('position', positionFilter.value)
    params.set('championSortBy', sortBy.value)
    params.set('championSortDirection', championSortDirection.value)
    params.set('championColumns', championVisibleColumns.value.join(','))
    if (search.value) params.set('championSearch', search.value)
  } else if (activeView.value === 'team') {
    params.set('minimumMatchCount', String(minimumMatchCount.value))
    params.set('teamSortBy', teamSortBy.value)
    params.set('teamSortDirection', teamSortDirection.value)
    params.set('teamColumns', teamVisibleColumns.value.join(','))
    if (teamSearch.value) params.set('teamSearch', teamSearch.value)
  } else if (activeView.value === 'player') {
    params.set('minimumMatchCount', String(minimumMatchCount.value))
    if (playerPositionFilter.value) params.set('playerPosition', playerPositionFilter.value)
    params.set('playerSortBy', playerSortBy.value)
    params.set('playerSortDirection', playerSortDirection.value)
    params.set('playerColumns', playerVisibleColumns.value.join(','))
    if (playerSearch.value) params.set('playerSearch', playerSearch.value)
  } else {
    params.set('combinationType', combinationType.value)
    params.set('minimumCombinationPickCount', String(minimumCombinationPickCount.value))
    params.set('combinationSortBy', combinationSortBy.value)
    params.set('combinationSortDirection', combinationSortDirection.value)
    params.set('combinationColumns', combinationVisibleColumns.value.join(','))
    if (combinationSearch.value) params.set('combinationSearch', combinationSearch.value)
  }
  return params.toString()
}

/** 将查询状态写入地址栏（replaceState，不产生历史记录），刷新与分享链接可恢复。 */
function syncQueryToUrl() {
  if (typeof window === 'undefined') return
  if (window.location.pathname !== '/') return
  window.history.replaceState(null, '', `?${buildQueryString()}`)
}

function isColumnVisible(visibleColumns: string[], key: string): boolean {
  return visibleColumns.includes(key)
}

const TABLE_COLUMN_WIDTHS: Record<string, number> = {
  champion: 169,
  player: 169,
  damagePerGame: 106,
  team: 150,
  positions: 124,
  mostUsedPlayers: 154,
  firstChampion: 180,
  secondChampion: 180,
}

function tableWidth(visibleColumns: string[]): string {
  const pixels = visibleColumns.reduce((sum, key) => sum + (TABLE_COLUMN_WIDTHS[key] ?? 88), 0)
  return `${pixels}px`
}

/* ---- computed ---- */

const sortedSeasons = computed(() =>
  [...seasons.value].sort((left, right) => left.sourceSeasonId - right.sourceSeasonId),
)

const browsedSeasonName = computed(() => {
  const season = seasons.value.find((s) => s.sourceSeasonId === browsedSeasonId.value)
  return season?.name ?? `赛事 #${browsedSeasonId.value}`
})

const browsedStages = computed(() =>
  allAvailability.value.filter((s) => s.sourceSeasonId === browsedSeasonId.value),
)

const selectedStageDetails = computed(() => {
  const keys = selectedStageKeys.value
  return allAvailability.value.filter((s) => keys.has(makeKey(s.sourceSeasonId, s.sourceStageId)))
})

const selectedSeasonCount = computed(() => {
  const ids = new Set(selectedStageDetails.value.map((s) => s.sourceSeasonId))
  return ids.size
})

const totalSampleBase = computed(() =>
  selectedStageDetails.value.reduce((sum, s) => sum + (s.sampleBaseCount ?? 0), 0),
)

const filteredChampionItems = computed(() => {
  let items = result.value?.items ?? []
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
  // 位置已作为查询条件由后端过滤（playerStatisticsByKeys 携带 position），
  // 前端不再重复过滤，避免切换位置时旧结果被同步清空导致表格闪现空状态。
  let items = playerResult.value?.items ?? []
  const keyword = playerSearch.value.trim().toLowerCase()
  if (keyword) {
    items = items.filter((item) =>
      `${item.playerName}${item.teamNames.join('')}`.toLowerCase().includes(keyword),
    )
  }
  return items
})

const filteredCombinationItems = computed(() => {
  let items = combinationResult.value?.items ?? []
  const keyword = combinationSearch.value.trim().toLowerCase()
  if (keyword) {
    items = items.filter((item) =>
      `${item.teamName}${item.firstChampionName}${item.secondChampionName}`.toLowerCase().includes(keyword),
    )
  }
  return items
})

function paginate<T>(items: T[]): T[] {
  const start = (currentPage.value - 1) * pageSize.value
  return items.slice(start, start + pageSize.value)
}

const paginatedChampionItems = computed(() => paginate(filteredChampionItems.value))
const paginatedTeamItems = computed(() => paginate(filteredTeamItems.value))
const paginatedPlayerItems = computed(() => paginate(filteredPlayerItems.value))
const paginatedCombinationItems = computed(() => paginate(filteredCombinationItems.value))
const championTableWidth = computed(() => tableWidth(championVisibleColumns.value))
const teamTableWidth = computed(() => tableWidth(teamVisibleColumns.value))
const playerTableWidth = computed(() => tableWidth(playerVisibleColumns.value))
const combinationTableWidth = computed(() => tableWidth(combinationVisibleColumns.value))

const latestCollectedAt = computed(() => {
  const timestamps = selectedStageDetails.value
    .map((s) => s.collectedAt)
    .filter(Boolean) as string[]
  return timestamps.sort().at(-1) ?? null
})

const latestUpdatedAt = computed(() => {
  if (activeView.value === 'team' || activeView.value === 'player' || activeView.value === 'combo') return latestCollectedAt.value
  const timestamps = (result.value?.items ?? [])
    .map((item) => item.sourceUpdatedAt)
    .filter(Boolean) as string[]
  return timestamps.sort().at(-1) ?? latestCollectedAt.value
})

const currentDataVersion = computed(() => {
  if (activeView.value === 'champion') return result.value?.dataVersion
  if (activeView.value === 'team') return teamResult.value?.dataVersion
  if (activeView.value === 'player') return playerResult.value?.dataVersion
  return combinationResult.value?.dataVersion
})

function isValidMinimum(value: number): boolean {
  return typeof value === 'number' && Number.isInteger(value) && value >= 0 && value <= 10_000
}

const minimumPickCountValid = computed(() => isValidMinimum(minimumPickCount.value))
const minimumMatchCountValid = computed(() => isValidMinimum(minimumMatchCount.value))
const minimumCombinationPickCountValid = computed(() => isValidMinimum(minimumCombinationPickCount.value))
const activeMinimumValid = computed(() =>
  activeView.value === 'champion' ? minimumPickCountValid.value
    : activeView.value === 'combo' ? minimumCombinationPickCountValid.value : minimumMatchCountValid.value,
)

const canQuery = computed(() => {
  if (busy.value || availabilityLoading.value) return false
  return selectedStageKeys.value.size > 0 && activeMinimumValid.value
})

type ExportItem = ChampionStatisticsResult['items'][number]
  | TeamStatisticsResult['items'][number]
  | PlayerStatisticsResult['items'][number]
  | TeamCombinationStatisticsResult['items'][number]

const PERCENTAGE_EXPORT_FIELDS = new Set([
  'pickRate', 'banRate', 'bpRate', 'winningRate',
  'killParticipantPercent', 'damagePercent', 'goldPercent',
])

function exportValue(view: ActiveView, key: string, item: ExportItem): string | number {
  if (view === 'champion') {
    const champion = item as ChampionStatisticsResult['items'][number]
    if (key === 'champion') return champion.championName
    if (key === 'positions') return champion.positions.join(' / ')
    if (key === 'mostUsedPlayers') return champion.mostUsedPlayers.join('、')
    return champion[key as keyof typeof champion] as string | number
  }
  if (view === 'team') {
    const team = item as TeamStatisticsResult['items'][number]
    if (key === 'team') return team.teamName
    return team[key as keyof typeof team] as string | number
  }
  if (view === 'player') {
    const player = item as PlayerStatisticsResult['items'][number]
    if (key === 'player') return player.playerName
    if (key === 'positions') return player.positions.join(' / ')
    return player[key as keyof typeof player] as string | number
  }
  const combination = item as TeamCombinationStatisticsResult['items'][number]
  if (key === 'team') return combination.teamName
  if (key === 'firstChampion') return combination.firstChampionName
  if (key === 'secondChampion') return combination.secondChampionName
  return combination[key as keyof typeof combination] as string | number
}

async function exportExcel() {
  if (exporting.value) return
  const view = activeView.value
  const source = view === 'champion' ? filteredChampionItems.value
    : view === 'team' ? filteredTeamItems.value
      : view === 'player' ? filteredPlayerItems.value : filteredCombinationItems.value
  const definitions = view === 'champion' ? CHAMPION_COLUMNS
    : view === 'team' ? TEAM_COLUMNS : view === 'player' ? PLAYER_COLUMNS : COMBINATION_COLUMNS
  const visible = view === 'champion' ? championVisibleColumns.value
    : view === 'team' ? teamVisibleColumns.value
      : view === 'player' ? playerVisibleColumns.value : combinationVisibleColumns.value
  const columns = definitions.filter((column) => visible.includes(column.key))
  if (!source.length || !columns.length) return

  exporting.value = true
  error.value = ''
  try {
    const { Workbook } = await import('exceljs')
    const workbook = new Workbook()
    const sheetName = view === 'champion' ? '英雄统计'
      : view === 'team' ? '战队统计' : view === 'player' ? '选手统计' : '英雄组合统计'
    const worksheet = workbook.addWorksheet(sheetName)
    worksheet.columns = columns.map((column) => ({
      header: column.label,
      key: column.key,
      width: Math.max(12, Math.min(28, column.label.length * 2 + 8)),
    }))
    worksheet.addRows(source.map((item) => Object.fromEntries(
      columns.map((column) => [column.key, exportValue(view, column.key, item)]),
    )))
    worksheet.getRow(1).font = { bold: true }
    worksheet.getRow(1).fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFF1F3F5' } }
    worksheet.views = [{ state: 'frozen', ySplit: 1, xSplit: view === 'team' ? 0 : 1 }]
    worksheet.autoFilter = {
      from: { row: 1, column: 1 },
      to: { row: 1, column: columns.length },
    }
    for (const column of columns) {
      if (PERCENTAGE_EXPORT_FIELDS.has(column.key)) worksheet.getColumn(column.key).numFmt = '0.00%'
    }

    const buffer = await workbook.xlsx.writeBuffer()
    const blob = new Blob([buffer as BlobPart], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${sheetName}-${new Date().toISOString().slice(0, 10)}.xlsx`
    document.body.appendChild(link)
    link.click()
    link.remove()
    globalThis.setTimeout(() => URL.revokeObjectURL(url), 1000)
    notice.value = `已导出 ${source.length} 条${sheetName}数据`
  } catch (reason) {
    error.value = reason instanceof Error ? `导出失败：${reason.message}` : `导出失败：${String(reason)}`
  } finally {
    exporting.value = false
  }
}

function clearStatisticsResults() {
  result.value = null
  teamResult.value = null
  playerResult.value = null
  combinationResult.value = null
  submittedPlayerQuery.value = null
}

function clearActiveResult(view: ActiveView) {
  if (view === 'champion') result.value = null
  else if (view === 'team') teamResult.value = null
  else if (view === 'player') playerResult.value = null
  else combinationResult.value = null
}

function invalidateQueryResults() {
  querySeq++
  busy.value = false
  sorting.value = false
  currentPage.value = 1
  clearStatisticsResults()
  notice.value = ''
  error.value = ''
}

watch(
  [minimumPickCount, minimumMatchCount, minimumCombinationPickCount],
  invalidateQueryResults,
  { flush: 'sync' },
)

/* 位置/组合类型筛选：点击后立即重新查询，无需再次点击查询按钮 */
watch([positionFilter, playerPositionFilter, combinationType], () => {
  sorting.value = true
  void query(true).finally(() => {
    sorting.value = false
  })
}, { flush: 'sync' })

watch([search, teamSearch, playerSearch, combinationSearch], () => {
  currentPage.value = 1
}, { flush: 'sync' })

watch(pageSize, () => {
  currentPage.value = 1
}, { flush: 'sync' })

/* 查询状态写入地址栏：刷新与分享链接可恢复当前 tab 的全部条件 */
watch(
  [activeView, browsedSeasonId, selectedStageKeys, minimumPickCount, minimumMatchCount, minimumCombinationPickCount,
   positionFilter, playerPositionFilter, combinationType,
   sortBy, championSortDirection, teamSortBy, teamSortDirection,
   playerSortBy, playerSortDirection, combinationSortBy, combinationSortDirection,
   search, teamSearch, playerSearch, combinationSearch,
   currentPage, pageSize,
   championVisibleColumns, teamVisibleColumns, playerVisibleColumns, combinationVisibleColumns],
  syncQueryToUrl,
  { flush: 'sync' },
)

/* ---- methods ---- */

async function loadSeasons() {
  seasons.value = await api.seasons()
  if (seasons.value.length && !seasons.value.some((item) => item.sourceSeasonId === browsedSeasonId.value)) {
    browsedSeasonId.value = seasons.value[0].sourceSeasonId
  }
}

async function loadAvailability() {
  const seq = ++loadAvailabilitySeq
  const previouslySelected = new Set(selectedStageKeys.value)
  querySeq++
  busy.value = false
  const type = VIEW_STAT_TYPE[activeView.value]
  clearStatisticsResults()
  allAvailability.value = []
  selectedStageKeys.value = new Set()
  notice.value = ''
  error.value = ''
  availabilityLoading.value = true
  try {
    const data = await api.availability(type, false)
    if (seq !== loadAvailabilitySeq) return
    allAvailability.value = data

    /* 保留仍然 collected 的已选复合键 */
    const collectedKeys = new Set(
      data.filter((s) => s.collected).map((s) => makeKey(s.sourceSeasonId, s.sourceStageId)),
    )
    const preserved = new Set([...previouslySelected].filter((k) => collectedKeys.has(k)))

    selectedStageKeys.value = preserved

    /* 确保 browsedSeasonId 指向有数据的赛事 */
    if (!data.some((s) => s.sourceSeasonId === browsedSeasonId.value)) {
      const first = data.find((s) => s.collected) ?? data[0]
      if (first) browsedSeasonId.value = first.sourceSeasonId
    }
  } catch (reason) {
    if (seq !== loadAvailabilitySeq) return
    error.value = reason instanceof Error ? reason.message : `加载赛段失败：${String(reason)}`
  } finally {
    if (seq === loadAvailabilitySeq) availabilityLoading.value = false
  }
}

async function query(preserveCurrentResult = false) {
  if (!activeMinimumValid.value) {
    error.value = '最低样本数必须是 0 到 10000 之间的整数'
    return
  }
  if (!canQuery.value) return
  const seq = ++querySeq
  const view = activeView.value
  const keys = [...selectedStageKeys.value]
  const selectedChampionPosition = positionFilter.value
  const selectedPlayerPosition = playerPositionFilter.value
  const selectedMinimumPickCount = minimumPickCount.value
  const selectedMinimumMatchCount = minimumMatchCount.value
  currentPage.value = 1
  if (!preserveCurrentResult) clearActiveResult(view)
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    if (view === 'champion') {
      const data = await api.championStatisticsByKeys(
        keys,
        selectedMinimumPickCount,
        selectedChampionPosition,
        sortBy.value,
        championSortDirection.value,
      )
      if (seq === querySeq && activeView.value === view) result.value = data
    } else if (view === 'team') {
      const data = await api.teamStatisticsByKeys(keys, selectedMinimumMatchCount, teamSortBy.value, teamSortDirection.value)
      if (seq === querySeq && activeView.value === view) teamResult.value = data
    } else if (view === 'player') {
      submittedPlayerQuery.value = {
        stageKeys: [...keys],
        position: selectedPlayerPosition,
        minimumMatchCount: selectedMinimumMatchCount,
      }
      const data = await api.playerStatisticsByKeys(
        keys,
        selectedMinimumMatchCount,
        selectedPlayerPosition,
        playerSortBy.value,
        playerSortDirection.value,
      )
      if (seq === querySeq && activeView.value === view) playerResult.value = data
    } else {
      const data = await api.teamCombinationStatisticsByKeys(
        keys,
        combinationType.value,
        minimumCombinationPickCount.value,
        combinationSortBy.value,
        combinationSortDirection.value,
      )
      if (seq === querySeq && activeView.value === view) combinationResult.value = data
    }
    if (seq === querySeq) notice.value = '查询完成'
  } catch (reason) {
    if (seq === querySeq) error.value = reason instanceof Error ? reason.message : String(reason)
  } finally {
    if (seq === querySeq) busy.value = false
  }
}

function playerDetailHref(item: PlayerStatistics): string | undefined {
  const snapshot = submittedPlayerQuery.value
  if (!snapshot || item.sourcePlayerId == null) return undefined
  const position = snapshot.position || item.positions[0] || ''
  if (!position) return undefined
  const params = new URLSearchParams({
    stageKeys: snapshot.stageKeys.join(','),
    position,
    minimumMatchCount: String(snapshot.minimumMatchCount),
    returnTo: `/?${buildQueryString()}`,
  })
  return `/players/${item.sourcePlayerId}?${params.toString()}`
}

function championDetailHref(item: ChampionStatistics): string | undefined {
  const keys = [...selectedStageKeys.value]
  if (!keys.length) return undefined
  const params = new URLSearchParams({
    stageKeys: keys.join(','),
    minimumPickCount: String(minimumPickCount.value),
    returnTo: `/?${buildQueryString()}`,
  })
  if (positionFilter.value) params.set('position', positionFilter.value)
  return `/champions/${item.championId}?${params.toString()}`
}

function teamDetailHref(item: TeamStatistics): string | undefined {
  const keys = [...selectedStageKeys.value]
  if (!keys.length) return undefined
  const params = new URLSearchParams({
    stageKeys: keys.join(','),
    minimumMatchCount: String(minimumMatchCount.value),
    returnTo: `/?${buildQueryString()}`,
  })
  return `/teams/${item.teamId}?${params.toString()}`
}

function changeSort(view: ActiveView, field: string) {
  if (view !== activeView.value || busy.value) return
  if (view === 'champion') {
    if (sortBy.value === field) championSortDirection.value = championSortDirection.value === 'desc' ? 'asc' : 'desc'
    else {
      sortBy.value = field
      championSortDirection.value = 'desc'
    }
  } else if (view === 'team') {
    if (teamSortBy.value === field) teamSortDirection.value = teamSortDirection.value === 'desc' ? 'asc' : 'desc'
    else {
      teamSortBy.value = field
      teamSortDirection.value = 'desc'
    }
  } else if (view === 'player') {
    if (playerSortBy.value === field) playerSortDirection.value = playerSortDirection.value === 'desc' ? 'asc' : 'desc'
    else {
      playerSortBy.value = field
      playerSortDirection.value = 'desc'
    }
  } else {
    if (combinationSortBy.value === field) {
      combinationSortDirection.value = combinationSortDirection.value === 'desc' ? 'asc' : 'desc'
    } else {
      combinationSortBy.value = field
      combinationSortDirection.value = 'desc'
    }
  }
  sorting.value = true
  void query(true).finally(() => {
    sorting.value = false
  })
}

function switchView(view: ActiveView) {
  if (activeView.value === view) return
  activeView.value = view
  currentPage.value = 1
  clearStatisticsResults()
  notice.value = ''
  error.value = ''
  void loadAvailability()
}

function toggleStage(compositeKey: string, collected: boolean) {
  if (!collected) return
  const newSet = new Set(selectedStageKeys.value)
  if (newSet.has(compositeKey)) {
    newSet.delete(compositeKey)
  } else {
    if (newSet.size >= MAX_STAGE_SELECTION) {
      error.value = `最多选择 ${MAX_STAGE_SELECTION} 个赛段，请先移除部分赛段后再添加`
      return
    }
    newSet.add(compositeKey)
  }
  selectedStageKeys.value = newSet
  invalidateQueryResults()
}

function removeStage(compositeKey: string) {
  const newSet = new Set(selectedStageKeys.value)
  newSet.delete(compositeKey)
  selectedStageKeys.value = newSet
  invalidateQueryResults()
}

function clearSelectedStages() {
  if (selectedStageKeys.value.size === 0) return
  selectedStageKeys.value = new Set()
  invalidateQueryResults()
}

function percent(value: number) {
  return formatPercent(value)
}

function fmtDecimal(value: number | null | undefined, digits = 2) {
  return value == null ? '-' : value.toFixed(digits)
}

function fmtGold(value: number) {
  if (value < 0) return '-' + ((-value) / 1000).toFixed(1) + 'k'
  return (value / 1000).toFixed(1) + 'k'
}

function fmtDuration(value: number | null | undefined) {
  if (value == null) return '-'
  const seconds = Math.round(value)
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

function fmtPositions(positions: string[]) {
  return positions.join(' / ') || '—'
}

function fmtTeamNames(teamNames: string[]) {
  return teamNames.join(' / ') || '—'
}

onMounted(async () => {
  const restoredQuery = restoreQueryFromLocation()
  try {
    await loadSeasons()
    if (seasons.value.length > 0 && browsedSeasonId.value === 0) {
      browsedSeasonId.value = seasons.value[0].sourceSeasonId
    }
    await loadAvailability()
    if (restoredQuery && selectedStageKeys.value.size > 0) await query()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : String(reason)
  }
})
</script>

<template>
  <SiteNav :stage-keys="[...selectedStageKeys]" />
  <main class="shell">
    <header class="hero">
      <div>
        <p class="eyebrow">LOL DATA HUB</p>
        <h1>赛事数据</h1>
      </div>
      <div class="status-card">
        <span>数据版本</span>
        <strong>{{ currentDataVersion ?? '—' }}</strong>
        <small>{{ latestUpdatedAt ? `更新于 ${new Date(latestUpdatedAt).toLocaleString()}` : '尚未查询数据' }}</small>
      </div>
      <p class="hero-copy">基于本地持久化数据重新计算跨赛事指标，并用明确的样本门槛隔离低样本噪声。支持跨赛事赛段选择，如 LPL + MSI（需已有采集数据）。</p>
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
      <button
        class="tab-btn"
        :class="{ active: activeView === 'combo' }"
        @click="switchView('combo')"
      >
        英雄组合
      </button>
    </nav>

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
      <div v-if="activeView === 'champion'" class="field compact">
        <label for="minimum">最低出场次数</label>
        <input id="minimum" v-model.number="minimumPickCount" type="number" min="0" max="10000" step="1" />
        <small v-if="!minimumPickCountValid" class="field-error">请输入 0 到 10000 之间的整数</small>
      </div>
      <div v-else-if="activeView === 'combo'" class="field compact">
        <label for="minimumCombination">最低组合选取次数</label>
        <input id="minimumCombination" v-model.number="minimumCombinationPickCount" type="number" min="0" max="10000" step="1" />
        <small v-if="!minimumCombinationPickCountValid" class="field-error">请输入 0 到 10000 之间的整数</small>
      </div>
      <div v-else class="field compact">
        <label for="minimumMatch">最低比赛场数</label>
        <input id="minimumMatch" v-model.number="minimumMatchCount" type="number" min="0" max="10000" step="1" />
        <small v-if="!minimumMatchCountValid" class="field-error">请输入 0 到 10000 之间的整数</small>
      </div>
      <div class="actions">
        <button class="primary" :disabled="!canQuery" @click="query()">{{ busy ? '处理中…' : '查询统计' }}</button>
      </div>

      <!-- 赛段浏览器 -->
      <div class="stage-block">
        <div class="stage-heading">
          <span>选择赛段（支持跨赛事选择，如 LPL + MSI）</span>
          <small>当前浏览：{{ browsedSeasonName }}</small>
        </div>
        <div v-if="availabilityLoading" class="empty-inline">正在加载赛段…</div>
        <div v-else-if="browsedStages.length" class="stage-list">
          <button
            v-for="stage in browsedStages"
            :key="makeKey(stage.sourceSeasonId, stage.sourceStageId)"
            class="stage-chip"
            :class="{ selected: selectedStageKeys.has(makeKey(stage.sourceSeasonId, stage.sourceStageId)), disabled: !stage.collected }"
            :disabled="!stage.collected"
            @click="toggleStage(makeKey(stage.sourceSeasonId, stage.sourceStageId), stage.collected)"
          >
            <span>{{ stage.name }}</span>
            <small v-if="!stage.collected" class="uncollected-tag">未采集</small>
            <small v-else-if="stage.sampleBaseCount != null">{{ stage.sampleBaseCount }} 场</small>
          </button>
        </div>
        <p v-else class="empty-inline">
          {{ activeView === 'team' ? '该赛季暂无已采集战队数据。' : activeView === 'player' ? '该赛季暂无已采集选手数据。' : activeView === 'combo' ? '该赛季暂无已采集单局阵容数据。' : '该赛季暂无赛段数据。' }}
        </p>

        <!-- 跨赛事选择篮 -->
        <div class="basket-section">
          <div class="basket-heading">
            <span>已选跨赛事赛段</span>
            <div v-if="selectedStageKeys.size > 0" class="basket-heading-actions">
              <small>
                {{ selectedSeasonCount }} 个赛事 · {{ selectedStageKeys.size }} 个赛段
                <template v-if="activeView === 'champion'"> · 样本合计 {{ totalSampleBase }}</template>
              </small>
              <button
                type="button"
                class="basket-clear"
                aria-label="取消所有已选赛段"
                @click="clearSelectedStages"
              >清空全部</button>
            </div>
          </div>
          <div v-if="selectedStageKeys.size === 0" class="empty-inline">
            请在上方赛段列表中勾选要查询的赛段，支持跨赛事选择（如 LPL 赛段 + MSI 赛段，需已有采集数据）。
          </div>
          <div v-else class="basket-list">
            <div
              v-for="stage in selectedStageDetails"
              :key="makeKey(stage.sourceSeasonId, stage.sourceStageId)"
              class="basket-item"
            >
              <span class="basket-season">{{ stage.seasonName ?? `赛事#${stage.sourceSeasonId}` }}</span>
              <span class="basket-stage">{{ stage.name }}</span>
              <small v-if="stage.sampleBaseCount != null" class="basket-sample">{{ stage.sampleBaseCount }} 场</small>
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

    <section v-if="selectedStageKeys.size > 0" class="query-summary">
      <span>已选 <strong>{{ selectedSeasonCount }}</strong> 个赛事 · <strong>{{ selectedStageKeys.size }}</strong> 个赛段</span>
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
          <div class="toolbar-options-row">
            <div class="position-filter">
              <button
                v-for="opt in CHAMPION_POSITION_OPTIONS"
                :key="opt.value"
                class="pos-chip"
                :class="{ active: positionFilter === opt.value }"
                :aria-pressed="positionFilter === opt.value"
                @click="positionFilter = opt.value"
              >
                {{ opt.label }}
              </button>
            </div>
            <ColumnVisibilityMenu v-model="championVisibleColumns" :columns="CHAMPION_COLUMNS" />
            <button class="export-button" type="button" :disabled="exporting || !filteredChampionItems.length" @click="exportExcel">
              {{ exporting ? '导出中…' : '导出 Excel' }}
            </button>
          </div>
          <div class="search-wrap">
            <input v-model="search" type="search" placeholder="搜索英雄、称号" />
            <span>{{ filteredChampionItems.length }} 项</span>
          </div>
          </div>
        </div>
        <p v-if="positionFilter" class="position-note">
          出场、胜负与 KDA 按实际分路独立统计；英雄被禁用时没有实际分路，禁用指标按所选赛段整体计算，BP 率为该分路出场率与整体禁用率之和。
        </p>

      <div v-if="filteredChampionItems.length" class="table-scroll" :class="{ 'is-updating': sorting }" :aria-busy="sorting" tabindex="0" aria-label="英雄统计表，可横向和纵向滚动">
        <table class="champion-table" :style="{ width: championTableWidth }">
          <thead>
            <tr>
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'champion')" class="champion-name-column" label="英雄" field="championName" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'positions')" class="champion-position-column" label="分路" field="positions" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'pickCount')" label="出场" field="pickCount" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'pickRate')" label="出场率" field="pickRate" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'banCount')" label="禁用" field="banCount" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'banRate')" label="禁用率" field="banRate" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'bpRate')" label="BP 率" field="bpRate" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'winningCount')" label="胜场" field="winningCount" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'winningRate')" label="胜率" field="winningRate" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'totalKills')" label="总击杀" field="totalKills" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'killPerGame')" label="场均击杀" field="killPerGame" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'totalAssists')" label="总助攻" field="totalAssists" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'assistPerGame')" label="场均助攻" field="assistPerGame" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'totalDeaths')" label="总死亡" field="totalDeaths" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'deathPerGame')" label="场均死亡" field="deathPerGame" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'kda')" label="KDA" field="kda" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
              <SortableHeader v-if="isColumnVisible(championVisibleColumns, 'mostUsedPlayers')" label="常用选手" field="mostUsedPlayers" :sort-by="sortBy" :sort-direction="championSortDirection" @sort="changeSort('champion', $event)" />
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in paginatedChampionItems" :key="item.championId">
              <td v-if="isColumnVisible(championVisibleColumns, 'champion')" class="champion-name-column">
                <a
                  v-if="championDetailHref(item)"
                  :href="championDetailHref(item)"
                  class="champion-detail-link"
                  :title="`查看 ${item.championName} 的英雄详情`"
                >
                  <div class="champion-cell">
                    <img v-if="item.championLogo" :src="item.championLogo" :alt="item.championName" />
                    <span class="champion-placeholder" v-else>{{ item.championName.slice(0, 1) }}</span>
                    <div><strong>{{ item.championName }}</strong><small>{{ item.championTitle }}</small></div>
                  </div>
                </a>
                <div v-else class="champion-cell">
                  <img v-if="item.championLogo" :src="item.championLogo" :alt="item.championName" />
                  <span class="champion-placeholder" v-else>{{ item.championName.slice(0, 1) }}</span>
                  <div><strong>{{ item.championName }}</strong><small>{{ item.championTitle }}</small></div>
                </div>
              </td>
              <td v-if="isColumnVisible(championVisibleColumns, 'positions')" class="champion-position-column">{{ item.positions.join(' / ') || '—' }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'pickCount')">{{ item.pickCount }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'pickRate')">{{ percent(item.pickRate) }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'banCount')">{{ item.banCount }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'banRate')">{{ percent(item.banRate) }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'bpRate')" class="accent">{{ percent(item.bpRate) }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'winningCount')">{{ item.winningCount }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'winningRate')" class="accent">{{ percent(item.winningRate) }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'totalKills')">{{ item.totalKills }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'killPerGame')">{{ fmtDecimal(item.killPerGame) }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'totalAssists')">{{ item.totalAssists }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'assistPerGame')">{{ fmtDecimal(item.assistPerGame) }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'totalDeaths')">{{ item.totalDeaths }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'deathPerGame')">{{ fmtDecimal(item.deathPerGame) }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'kda')">{{ fmtDecimal(item.kda) }}</td>
              <td v-if="isColumnVisible(championVisibleColumns, 'mostUsedPlayers')">{{ item.mostUsedPlayers.join('、') || '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state">
        <strong v-if="selectedStageKeys.size === 0">选择赛段后点击查询</strong>
        <strong v-else-if="!result">选择赛段后点击查询</strong>
        <strong v-else>无匹配结果</strong>
      </div>
      <PaginationControls
        v-if="filteredChampionItems.length"
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total-items="filteredChampionItems.length"
      />
    </section>

    <!-- 战队统计面板 -->
    <section v-if="activeView === 'team'" class="panel table-panel">
      <div class="table-toolbar">
        <div>
          <p class="eyebrow">TEAM STATISTICS</p>
          <h2>战队统计</h2>
        </div>
        <div class="toolbar-right">
          <div class="toolbar-options-row">
            <ColumnVisibilityMenu v-model="teamVisibleColumns" :columns="TEAM_COLUMNS" />
            <button class="export-button" type="button" :disabled="exporting || !filteredTeamItems.length" @click="exportExcel">
              {{ exporting ? '导出中…' : '导出 Excel' }}
            </button>
          </div>
          <div class="search-wrap">
            <input v-model="teamSearch" type="search" placeholder="搜索战队" />
            <span>{{ filteredTeamItems.length }} 项</span>
          </div>
        </div>
      </div>

      <div v-if="filteredTeamItems.length" class="table-scroll" :class="{ 'is-updating': sorting }" :aria-busy="sorting" tabindex="0" aria-label="战队统计表，可横向和纵向滚动">
        <table class="team-table" :style="{ width: teamTableWidth }">
          <thead>
            <tr>
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'team')" label="战队" field="teamName" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'matchCount')" label="系列赛" field="matchCount" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'gameCount')" label="对局" field="gameCount" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'matchWinCount')" label="胜场" field="matchWinCount" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'winningRate')" label="胜率" field="winningRate" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'kda')" label="KDA" field="kda" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'totalKills')" label="总击杀" field="totalKills" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'killPerGame')" label="场均击杀" field="killPerGame" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'deathPerGame')" label="场均死亡" field="deathPerGame" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'damagePerGame')" label="场均输出" field="damagePerGame" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'damagePerMinute')" label="每分钟输出" field="damagePerMinute" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'averageGameDurationSeconds')" label="场均时长" field="averageGameDurationSeconds" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'goldPerMinute')" label="每分钟经济" field="goldPerMinute" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'creepScorePerMinute')" label="每分钟补刀" field="creepScorePerMinute" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'wardPlacedPerMinute')" label="每分钟插眼" field="wardPlacedPerMinute" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'wardKilledPerMinute')" label="每分钟拆眼" field="wardKilledPerMinute" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'drakeControlRate')" label="小龙控制率" field="drakeControlRate" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'baronControlRate')" label="大龙控制率" field="baronControlRate" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'firstBloodRate')" label="一血率" field="firstBloodRate" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'turretKillPerGame')" label="场均推塔" field="turretKillPerGame" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'turretLostPerGame')" label="场均被推塔" field="turretLostPerGame" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'wardPlacedPerGame')" label="场均插眼" field="wardPlacedPerGame" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'wardKilledPerGame')" label="场均排眼" field="wardKilledPerGame" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'goldPerGame')" label="场均经济" field="goldPerGame" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'baronKillPerGame')" label="场均大龙" field="baronKillPerGame" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
              <SortableHeader v-if="isColumnVisible(teamVisibleColumns, 'drakeKillPerGame')" label="场均小龙" field="drakeKillPerGame" :sort-by="teamSortBy" :sort-direction="teamSortDirection" @sort="changeSort('team', $event)" />
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in paginatedTeamItems" :key="item.teamId">
              <td v-if="isColumnVisible(teamVisibleColumns, 'team')">
                <a
                  v-if="teamDetailHref(item)"
                  :href="teamDetailHref(item)"
                  class="team-detail-link"
                  :title="`查看 ${item.teamName} 的战队详情`"
                >
                  <div class="team-cell">
                    <img v-if="item.teamLogo" :src="item.teamLogo" :alt="item.teamName" class="team-logo" />
                    <span class="team-placeholder" v-else>{{ item.teamName.slice(0, 1) }}</span>
                    <strong>{{ item.teamName }}</strong>
                  </div>
                </a>
                <div v-else class="team-cell">
                  <img v-if="item.teamLogo" :src="item.teamLogo" :alt="item.teamName" class="team-logo" />
                  <span class="team-placeholder" v-else>{{ item.teamName.slice(0, 1) }}</span>
                  <strong>{{ item.teamName }}</strong>
                </div>
              </td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'matchCount')">{{ item.matchCount }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'gameCount')">{{ item.gameCount }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'matchWinCount')">{{ item.matchWinCount }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'winningRate')" class="accent">{{ percent(item.winningRate) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'kda')" class="accent">{{ fmtDecimal(item.kda) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'totalKills')">{{ item.totalKills }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'killPerGame')">{{ fmtDecimal(item.killPerGame) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'deathPerGame')">{{ fmtDecimal(item.deathPerGame) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'damagePerGame')">{{ fmtDecimal(item.damagePerGame) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'damagePerMinute')">{{ fmtDecimal(item.damagePerMinute) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'averageGameDurationSeconds')">{{ fmtDuration(item.averageGameDurationSeconds) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'goldPerMinute')">{{ fmtDecimal(item.goldPerMinute) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'creepScorePerMinute')">{{ fmtDecimal(item.creepScorePerMinute) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'wardPlacedPerMinute')">{{ fmtDecimal(item.wardPlacedPerMinute) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'wardKilledPerMinute')">{{ fmtDecimal(item.wardKilledPerMinute) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'drakeControlRate')">{{ item.drakeControlRate == null ? '-' : percent(item.drakeControlRate) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'baronControlRate')">{{ item.baronControlRate == null ? '-' : percent(item.baronControlRate) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'firstBloodRate')">{{ item.firstBloodRate == null ? '-' : percent(item.firstBloodRate) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'turretKillPerGame')">{{ fmtDecimal(item.turretKillPerGame) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'turretLostPerGame')">{{ fmtDecimal(item.turretLostPerGame) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'wardPlacedPerGame')">{{ fmtDecimal(item.wardPlacedPerGame) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'wardKilledPerGame')">{{ fmtDecimal(item.wardKilledPerGame) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'goldPerGame')">{{ fmtGold(item.goldPerGame) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'baronKillPerGame')">{{ fmtDecimal(item.baronKillPerGame) }}</td>
              <td v-if="isColumnVisible(teamVisibleColumns, 'drakeKillPerGame')">{{ fmtDecimal(item.drakeKillPerGame) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state">
        <strong v-if="selectedStageKeys.size === 0">选择赛段后点击查询</strong>
        <strong v-else-if="!teamResult">选择赛段后点击查询</strong>
        <strong v-else>无匹配结果</strong>
      </div>
      <PaginationControls
        v-if="filteredTeamItems.length"
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total-items="filteredTeamItems.length"
      />
    </section>

    <!-- 选手统计面板 -->
    <section v-if="activeView === 'player'" class="panel table-panel">
      <div class="table-toolbar">
        <div>
          <p class="eyebrow">PLAYER STATISTICS</p>
          <h2>选手统计</h2>
        </div>
        <div class="toolbar-right">
          <div class="toolbar-options-row">
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
            <ColumnVisibilityMenu v-model="playerVisibleColumns" :columns="PLAYER_COLUMNS" />
            <button class="export-button" type="button" :disabled="exporting || !filteredPlayerItems.length" @click="exportExcel">
              {{ exporting ? '导出中…' : '导出 Excel' }}
            </button>
          </div>
          <div class="search-wrap">
            <input v-model="playerSearch" type="search" placeholder="搜索选手、战队" />
            <span>{{ filteredPlayerItems.length }} 项</span>
          </div>
        </div>
      </div>

      <div v-if="filteredPlayerItems.length" class="table-scroll" :class="{ 'is-updating': sorting }" :aria-busy="sorting" tabindex="0" aria-label="选手统计表，可横向和纵向滚动">
        <table class="player-table" :style="{ width: playerTableWidth }">
          <thead>
            <tr>
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'player')" class="player-name-column" label="选手" field="playerName" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'positions')" class="player-position-column" label="位置" field="positions" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'matchCount')" label="系列赛" field="matchCount" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'gameCount')" label="对局" field="gameCount" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'mvpCount')" label="MVP" field="mvpCount" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'mvpVotes')" label="MVP 票数" field="mvpVotes" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'kda')" label="KDA" field="kda" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'totalKills')" label="总击杀" field="totalKills" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'killPerGame')" label="场均击杀" field="killPerGame" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'totalAssists')" label="总助攻" field="totalAssists" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'assistPerGame')" label="场均助攻" field="assistPerGame" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'totalDeaths')" label="总死亡" field="totalDeaths" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'deathPerGame')" label="场均死亡" field="deathPerGame" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'goldPerGame')" label="场均经济" field="goldPerGame" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'creepScorePerGame')" label="场均补刀" field="creepScorePerGame" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'killParticipantPercent')" label="参团率" field="killParticipantPercent" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'goldGapPerGame')" label="场均经济差" field="goldGapPerGame" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'damagePerGame')" label="场均伤害" field="damagePerGame" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'damagePercent')" label="伤害占比" field="damagePercent" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
              <SortableHeader v-if="isColumnVisible(playerVisibleColumns, 'goldPercent')" label="经济占比" field="goldPercent" :sort-by="playerSortBy" :sort-direction="playerSortDirection" @sort="changeSort('player', $event)" />
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in paginatedPlayerItems" :key="item.playerKey">
              <td v-if="isColumnVisible(playerVisibleColumns, 'player')" class="player-name-column">
                <div class="player-cell">
                  <a
                    v-if="playerDetailHref(item)"
                    :href="playerDetailHref(item)"
                    class="player-avatar-link"
                    :title="`查看 ${item.playerName} 的选手详情`"
                  >
                    <img v-if="item.playerAvatar" :src="item.playerAvatar" :alt="item.playerName" class="player-avatar" />
                    <span class="player-placeholder" v-else>{{ item.playerName.slice(0, 1) }}</span>
                  </a>
                  <template v-else>
                    <img v-if="item.playerAvatar" :src="item.playerAvatar" :alt="item.playerName" class="player-avatar" />
                    <span class="player-placeholder" v-else>{{ item.playerName.slice(0, 1) }}</span>
                  </template>
                  <div>
                    <strong>{{ item.playerName }}</strong>
                    <small>{{ fmtTeamNames(item.teamNames) }}</small>
                  </div>
                </div>
              </td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'positions')" class="player-position-column">{{ fmtPositions(item.positions) }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'matchCount')">{{ item.matchCount }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'gameCount')">{{ item.gameCount }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'mvpCount')">{{ item.mvpCount }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'mvpVotes')">{{ item.mvpVotes }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'kda')" class="accent">{{ fmtDecimal(item.kda) }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'totalKills')">{{ item.totalKills }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'killPerGame')">{{ fmtDecimal(item.killPerGame) }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'totalAssists')">{{ item.totalAssists }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'assistPerGame')">{{ fmtDecimal(item.assistPerGame) }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'totalDeaths')">{{ item.totalDeaths }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'deathPerGame')">{{ fmtDecimal(item.deathPerGame) }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'goldPerGame')">{{ fmtGold(item.goldPerGame) }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'creepScorePerGame')">{{ fmtDecimal(item.creepScorePerGame) }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'killParticipantPercent')" class="accent">{{ percent(item.killParticipantPercent) }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'goldGapPerGame')">{{ fmtGold(item.goldGapPerGame) }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'damagePerGame')">{{ fmtDecimal(item.damagePerGame) }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'damagePercent')" class="accent">{{ percent(item.damagePercent) }}</td>
              <td v-if="isColumnVisible(playerVisibleColumns, 'goldPercent')">{{ percent(item.goldPercent) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state">
        <strong v-if="selectedStageKeys.size === 0">选择赛段后点击查询</strong>
        <strong v-else-if="!playerResult">选择赛段后点击查询</strong>
        <strong v-else>无匹配结果</strong>
      </div>
      <PaginationControls
        v-if="filteredPlayerItems.length"
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total-items="filteredPlayerItems.length"
      />
    </section>

    <!-- 战队英雄组合面板 -->
    <section v-if="activeView === 'combo'" class="panel table-panel">
      <div class="table-toolbar">
        <div>
          <p class="eyebrow">TEAM COMBINATIONS</p>
          <h2>战队英雄组合</h2>
        </div>
        <div class="toolbar-right">
          <div class="toolbar-options-row">
            <div class="position-filter" aria-label="组合类型">
              <button
                class="pos-chip"
                :class="{ active: combinationType === 'MID_JUNGLE' }"
                :aria-pressed="combinationType === 'MID_JUNGLE'"
                @click="combinationType = 'MID_JUNGLE'"
              >中野组合</button>
              <button
                class="pos-chip"
                :class="{ active: combinationType === 'BOT_SUPPORT' }"
                :aria-pressed="combinationType === 'BOT_SUPPORT'"
                @click="combinationType = 'BOT_SUPPORT'"
              >AD 辅助组合</button>
              <button
                class="pos-chip"
                :class="{ active: combinationType === 'TOP_JUNGLE' }"
                :aria-pressed="combinationType === 'TOP_JUNGLE'"
                @click="combinationType = 'TOP_JUNGLE'"
              >上野组合</button>
              <button
                class="pos-chip"
                :class="{ active: combinationType === 'TOP_MID' }"
                :aria-pressed="combinationType === 'TOP_MID'"
                @click="combinationType = 'TOP_MID'"
              >上中组合</button>
              <button
                class="pos-chip"
                :class="{ active: combinationType === 'MID_BOT' }"
                :aria-pressed="combinationType === 'MID_BOT'"
                @click="combinationType = 'MID_BOT'"
              >中下组合</button>
            </div>
            <ColumnVisibilityMenu v-model="combinationVisibleColumns" :columns="COMBINATION_COLUMNS" />
            <button class="export-button" type="button" :disabled="exporting || !filteredCombinationItems.length" @click="exportExcel">
              {{ exporting ? '导出中…' : '导出 Excel' }}
            </button>
          </div>
          <div class="search-wrap">
            <input v-model="combinationSearch" type="search" placeholder="搜索战队或英雄" />
            <span>{{ filteredCombinationItems.length }} 项</span>
          </div>
        </div>
      </div>

      <p class="position-note">
        每条记录只统计同一战队、同一系列赛且同一小局内的实际英雄组合；选取率以该战队在所选范围内阵容完整的有效小局数为分母。
      </p>

      <div v-if="filteredCombinationItems.length" class="table-scroll" :class="{ 'is-updating': sorting }" :aria-busy="sorting" tabindex="0" aria-label="战队英雄组合统计表，可横向和纵向滚动">
        <table class="combo-table" :style="{ width: combinationTableWidth }">
          <thead>
            <tr>
              <SortableHeader v-if="isColumnVisible(combinationVisibleColumns, 'team')" label="战队" field="teamName" :sort-by="combinationSortBy" :sort-direction="combinationSortDirection" @sort="changeSort('combo', $event)" />
              <SortableHeader v-if="isColumnVisible(combinationVisibleColumns, 'firstChampion')" :label="combinationType === 'MID_JUNGLE' ? '打野英雄' : 'AD 英雄'" field="firstChampionName" :sort-by="combinationSortBy" :sort-direction="combinationSortDirection" @sort="changeSort('combo', $event)" />
              <SortableHeader v-if="isColumnVisible(combinationVisibleColumns, 'secondChampion')" :label="combinationType === 'MID_JUNGLE' ? '中单英雄' : '辅助英雄'" field="secondChampionName" :sort-by="combinationSortBy" :sort-direction="combinationSortDirection" @sort="changeSort('combo', $event)" />
              <SortableHeader v-if="isColumnVisible(combinationVisibleColumns, 'pickCount')" label="选取次数" field="pickCount" :sort-by="combinationSortBy" :sort-direction="combinationSortDirection" @sort="changeSort('combo', $event)" />
              <SortableHeader v-if="isColumnVisible(combinationVisibleColumns, 'validGameCount')" label="有效小局" field="validGameCount" :sort-by="combinationSortBy" :sort-direction="combinationSortDirection" @sort="changeSort('combo', $event)" />
              <SortableHeader v-if="isColumnVisible(combinationVisibleColumns, 'pickRate')" label="选取率" field="pickRate" :sort-by="combinationSortBy" :sort-direction="combinationSortDirection" @sort="changeSort('combo', $event)" />
              <SortableHeader v-if="isColumnVisible(combinationVisibleColumns, 'winningCount')" label="获胜次数" field="winningCount" :sort-by="combinationSortBy" :sort-direction="combinationSortDirection" @sort="changeSort('combo', $event)" />
              <SortableHeader v-if="isColumnVisible(combinationVisibleColumns, 'winningRate')" label="组合胜率" field="winningRate" :sort-by="combinationSortBy" :sort-direction="combinationSortDirection" @sort="changeSort('combo', $event)" />
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in paginatedCombinationItems" :key="`${item.teamId}:${item.combinationType}:${item.firstChampionId}:${item.secondChampionId}`">
              <td v-if="isColumnVisible(combinationVisibleColumns, 'team')">
                <div class="team-cell">
                  <img v-if="item.teamLogo" :src="item.teamLogo" :alt="item.teamName" class="team-logo" />
                  <span v-else class="team-placeholder">{{ item.teamName.slice(0, 1) }}</span>
                  <strong>{{ item.teamName }}</strong>
                </div>
              </td>
              <td v-if="isColumnVisible(combinationVisibleColumns, 'firstChampion')">
                <div class="combination-champion-cell">
                  <img v-if="item.firstChampionLogo" :src="item.firstChampionLogo" :alt="item.firstChampionName" />
                  <span><strong>{{ item.firstChampionName }}</strong><small>{{ item.firstChampionTitle }}</small></span>
                </div>
              </td>
              <td v-if="isColumnVisible(combinationVisibleColumns, 'secondChampion')">
                <div class="combination-champion-cell">
                  <img v-if="item.secondChampionLogo" :src="item.secondChampionLogo" :alt="item.secondChampionName" />
                  <span><strong>{{ item.secondChampionName }}</strong><small>{{ item.secondChampionTitle }}</small></span>
                </div>
              </td>
              <td v-if="isColumnVisible(combinationVisibleColumns, 'pickCount')">{{ item.pickCount }}</td>
              <td v-if="isColumnVisible(combinationVisibleColumns, 'validGameCount')">{{ item.validGameCount }}</td>
              <td v-if="isColumnVisible(combinationVisibleColumns, 'pickRate')" class="accent">{{ percent(item.pickRate) }}</td>
              <td v-if="isColumnVisible(combinationVisibleColumns, 'winningCount')">{{ item.winningCount }}</td>
              <td v-if="isColumnVisible(combinationVisibleColumns, 'winningRate')" class="accent">{{ percent(item.winningRate) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state">
        <strong v-if="selectedStageKeys.size === 0">选择赛段后点击查询</strong>
        <strong v-else-if="!combinationResult">所选赛段尚未生成单局阵容，请先重新采集英雄数据</strong>
        <strong v-else>没有达到最低选取次数的组合</strong>
      </div>
      <PaginationControls
        v-if="filteredCombinationItems.length"
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total-items="filteredCombinationItems.length"
      />
    </section>
  </main>
</template>
