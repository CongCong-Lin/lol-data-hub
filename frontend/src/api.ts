export interface ApiResponse<T> {
  success: boolean
  data: T
  message: string | null
}

export interface Season {
  sourceSeasonId: number
  name: string
  startTime: string | null
  endTime: string | null
  open: boolean
}

export type StatisticType = 'HERO' | 'TEAM' | 'PLAYER' | 'COMBO'

export type TeamCombinationType = 'MID_JUNGLE' | 'BOT_SUPPORT'

export interface Stage {
  sourceSeasonId: number
  sourceStageId: number
  seasonName?: string
  name: string
  startTime?: string | null
  endTime?: string | null
  collected: boolean
  sampleBaseCount: number | null
  collectedAt: string | null
}

export interface ChampionStatistics {
  championId: number
  championName: string
  championTitle: string | null
  championLogo: string | null
  positions: string[]
  sampleBaseCount: number
  pickCount: number
  banCount: number
  bpCount: number
  winningCount: number
  totalKills: number
  totalDeaths: number
  totalAssists: number
  pickRate: number
  banRate: number
  bpRate: number
  winningRate: number
  kda: number
  killPerGame: number
  assistPerGame: number
  deathPerGame: number
  mostUsedPlayers: string[]
  sampleQualified: boolean
  sourceUpdatedAt: string | null
}

export interface ChampionStatisticsResult {
  dataVersion: number
  minimumPickCount: number
  total: number
  items: ChampionStatistics[]
}

export interface TeamStatistics {
  teamId: number
  teamName: string
  teamLogo: string | null
  matchCount: number
  gameCount: number
  matchWinCount: number
  winningRate: number
  totalKills: number
  killPerGame: number
  totalDeaths: number
  deathPerGame: number
  wardPlacedPerGame: number
  wardKilledPerGame: number
  goldPerGame: number
  baronKillPerGame: number
  drakeKillPerGame: number
  sampleQualified: boolean
}

export interface TeamStatisticsResult {
  dataVersion: number
  minimumMatchCount: number
  total: number
  items: TeamStatistics[]
}

export interface PlayerStatistics {
  playerKey: string
  sourcePlayerId: number | null
  playerName: string
  playerAvatar: string | null
  teamNames: string[]
  positions: string[]
  matchCount: number
  gameCount: number
  mvpCount: number
  mvpVotes: number
  totalKills: number
  totalAssists: number
  totalDeaths: number
  kda: number
  killPerGame: number
  assistPerGame: number
  deathPerGame: number
  goldPerGame: number
  creepScorePerGame: number
  wardPlacedPerGame: number
  wardKilledPerGame: number
  killParticipantPercent: number
  goldGapPerGame: number
  damagePerGame: number | null
  damagePercent: number
  goldPercent: number
  sampleQualified: boolean
}

export interface PlayerStatisticsResult {
  dataVersion: number
  minimumMatchCount: number
  total: number
  items: PlayerStatistics[]
}

export interface PlayerDetailProfile {
  sourcePlayerId: number | null
  playerName: string
  playerAvatar: string | null
  teamNames: string[]
  positions: string[]
  matchCount: number
  gameCount: number
}

export interface RankedPlayerMetric {
  key: string
  label: string
  value: number
  formattedValue: string
  rank: number
  cohortSize: number
  higherIsBetter: boolean
}

export interface PlayerRadarMetric {
  key: string
  label: string
  value: number | null
  averageValue: number
  playerScore: number
  averageScore: number
  rank: number
  cohortSize: number
  available: boolean
}

export interface PlayerAverageContrastMetric {
  key: string
  label: string
  value: number
  averageValue: number
  minValue: number
  maxValue: number
  rank: number
  cohortSize: number
  higherIsBetter: boolean
  percentage: boolean
}

export interface PlayerHeroUsage {
  sourceChampionId: number
  championName: string
  championChineseName: string
  championTitle: string | null
  championLogo: string | null
  pickCount: number
  pickRate: number
  winningCount: number
  winningRate: number
  totalKills: number
  totalDeaths: number
  totalAssists: number
  kda: number
  killPerGame: number
  deathPerGame: number
  assistPerGame: number
}

export interface PlayerDetailStatisticsResult {
  dataVersion: number
  minimumMatchCount: number
  position: string
  cohortSize: number
  player: PlayerDetailProfile
  coreMetrics: RankedPlayerMetric[]
  radarMetrics: PlayerRadarMetric[]
  heroUsageAvailable: boolean
  missingHeroStageKeys: string[]
  heroUsageTotalGames: number
  heroes: PlayerHeroUsage[]
  latestCollectedAt: string | null
  averageContrastMetrics: PlayerAverageContrastMetric[]
}

export interface TeamCombinationStatistics {
  teamId: number
  teamName: string
  teamLogo: string | null
  combinationType: TeamCombinationType
  firstPosition: string
  firstChampionId: number
  firstChampionName: string
  firstChampionTitle: string | null
  firstChampionLogo: string | null
  secondPosition: string
  secondChampionId: number
  secondChampionName: string
  secondChampionTitle: string | null
  secondChampionLogo: string | null
  pickCount: number
  validGameCount: number
  pickRate: number
  winningCount: number
  winningRate: number
  sampleQualified: boolean
}

export interface TeamCombinationStatisticsResult {
  dataVersion: number
  combinationType: TeamCombinationType
  minimumPickCount: number
  total: number
  items: TeamCombinationStatistics[]
}

const REQUEST_TIMEOUT_MS = 12_000

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const controller = new AbortController()
  const timeoutId = globalThis.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)

  try {
    const response = await fetch(path, {
      ...init,
      signal: controller.signal,
      headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
    })
    const text = await response.text()
    let body: ApiResponse<T> | null = null
    if (text) {
      try {
        body = JSON.parse(text) as ApiResponse<T>
      } catch {
        if (response.ok) {
          throw new Error('服务返回了无法识别的响应')
        }
      }
    }

    if (!response.ok) {
      throw new Error(body?.message || `请求失败：HTTP ${response.status}`)
    }
    if (!body || body.success !== true) {
      throw new Error(body?.message || '服务返回了无效的业务响应')
    }
    return body.data
  } catch (reason) {
    if (reason instanceof Error && reason.name === 'AbortError') {
      throw new Error('请求超时，请稍后重试')
    }
    if (reason instanceof TypeError) {
      throw new Error('无法连接服务，请检查网络或稍后重试')
    }
    throw reason
  } finally {
    globalThis.clearTimeout(timeoutId)
  }
}

export const api = {
  seasons: () => request<Season[]>('/api/v1/catalog/seasons'),
  stages: (seasonId: number, statisticType: StatisticType = 'HERO') =>
    request<Stage[]>(`/api/v1/catalog/stages?seasonId=${seasonId}&statisticType=${statisticType}`),
  availability: (statisticType: StatisticType, collectedOnly: boolean = false) =>
    request<Stage[]>(`/api/v1/catalog/stages/availability?statisticType=${statisticType}&collectedOnly=${collectedOnly}`),
  championStatistics: (
    seasonId: number,
    stageIds: number[],
    minimumPickCount: number,
    position: string,
    sortBy: string,
    sortDirection: string,
  ) => {
    const params = new URLSearchParams({
      seasonId: String(seasonId),
      stageIds: stageIds.join(','),
      minimumPickCount: String(minimumPickCount),
      sortBy,
      sortDirection,
    })
    if (position) params.set('position', position)
    return request<ChampionStatisticsResult>(`/api/v1/statistics/champions?${params}`)
  },
  teamStatistics: (
    seasonId: number,
    stageIds: number[],
    minimumMatchCount: number,
    sortBy: string,
    sortDirection: string,
  ) => {
    const params = new URLSearchParams({
      seasonId: String(seasonId),
      stageIds: stageIds.join(','),
      minimumMatchCount: String(minimumMatchCount),
      sortBy,
      sortDirection,
    })
    return request<TeamStatisticsResult>(`/api/v1/statistics/teams?${params}`)
  },
  playerStatistics: (
    seasonId: number,
    stageIds: number[],
    minimumMatchCount: number,
    position: string,
    sortBy: string,
    sortDirection: string,
  ) => {
    const params: Record<string, string> = {
      seasonId: String(seasonId),
      stageIds: stageIds.join(','),
      minimumMatchCount: String(minimumMatchCount),
      sortBy,
      sortDirection,
    }
    if (position) params.position = position
    return request<PlayerStatisticsResult>(`/api/v1/statistics/players?${new URLSearchParams(params)}`)
  },
  championStatisticsByKeys: (
    stageKeys: string[],
    minimumPickCount: number,
    position: string,
    sortBy: string,
    sortDirection: string,
  ) => {
    const params = new URLSearchParams({
      stageKeys: stageKeys.join(','),
      minimumPickCount: String(minimumPickCount),
      sortBy,
      sortDirection,
    })
    if (position) params.set('position', position)
    return request<ChampionStatisticsResult>(`/api/v1/statistics/champions?${params}`)
  },
  teamStatisticsByKeys: (
    stageKeys: string[],
    minimumMatchCount: number,
    sortBy: string,
    sortDirection: string,
  ) => {
    const params = new URLSearchParams({
      stageKeys: stageKeys.join(','),
      minimumMatchCount: String(minimumMatchCount),
      sortBy,
      sortDirection,
    })
    return request<TeamStatisticsResult>(`/api/v1/statistics/teams?${params}`)
  },
  playerStatisticsByKeys: (
    stageKeys: string[],
    minimumMatchCount: number,
    position: string,
    sortBy: string,
    sortDirection: string,
  ) => {
    const params: Record<string, string> = {
      stageKeys: stageKeys.join(','),
      minimumMatchCount: String(minimumMatchCount),
      sortBy,
      sortDirection,
    }
    if (position) params.position = position
    return request<PlayerStatisticsResult>(`/api/v1/statistics/players?${new URLSearchParams(params)}`)
  },
  teamCombinationStatisticsByKeys: (
    stageKeys: string[],
    combinationType: TeamCombinationType,
    minimumPickCount: number,
    sortBy: string,
    sortDirection: string,
  ) => {
    const params = new URLSearchParams({
      stageKeys: stageKeys.join(','),
      combinationType,
      minimumPickCount: String(minimumPickCount),
      sortBy,
      sortDirection,
    })
    return request<TeamCombinationStatisticsResult>(`/api/v1/statistics/team-combinations?${params}`)
  },
  playerDetail: (
    sourcePlayerId: number,
    stageKeys: string[],
    position: string,
    minimumMatchCount: number,
  ) => {
    const params = new URLSearchParams({
      stageKeys: stageKeys.join(','),
      position,
      minimumMatchCount: String(minimumMatchCount),
    })
    return request<PlayerDetailStatisticsResult>(
      `/api/v1/statistics/players/${sourcePlayerId}/detail?${params}`,
    )
  },
}
