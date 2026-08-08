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

export type StatisticType = 'HERO' | 'TEAM' | 'PLAYER'

export interface Stage {
  sourceSeasonId: number
  sourceStageId: number
  name: string
  startTime: string | null
  endTime: string | null
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

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  })
  const body = (await response.json()) as ApiResponse<T>
  if (!response.ok || !body.success) {
    throw new Error(body.message || `请求失败：HTTP ${response.status}`)
  }
  return body.data
}

export const api = {
  seasons: () => request<Season[]>('/api/v1/catalog/seasons'),
  stages: (seasonId: number, statisticType: StatisticType = 'HERO') =>
    request<Stage[]>(`/api/v1/catalog/stages?seasonId=${seasonId}&statisticType=${statisticType}`),
  championStatistics: (
    seasonId: number,
    stageIds: number[],
    minimumPickCount: number,
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
}
