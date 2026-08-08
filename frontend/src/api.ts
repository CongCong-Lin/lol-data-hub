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
  stages: (seasonId: number) => request<Stage[]>(`/api/v1/catalog/stages?seasonId=${seasonId}`),
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
}
