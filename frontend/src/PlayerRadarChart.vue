<script setup lang="ts">
import { computed } from 'vue'
import type { PlayerRadarMetric } from './api'
import { formatRadarMetricValue } from './formatters'

export interface RadarOverlaySeries {
  name: string
  /** 与 metrics 等长的 0-100 分值序列 */
  scores: number[]
  /** 可选颜色；缺省时按序取内置调色板 */
  color?: string
}

interface OverlayWithPolygon extends RadarOverlaySeries {
  polygon: string
}

const props = defineProps<{
  metrics: PlayerRadarMetric[]
  /** 可选：多选手叠加多边形（对比模式），scores 与 metrics 一一对应 */
  overlay?: RadarOverlaySeries[]
}>()

const CENTER_X = 350
const CENTER_Y = 280
const RADIUS = 128
const GRID_LEVELS = [20, 40, 60, 80, 100]
/** 对比叠加调色板：浅色系 + 20% 透明填充，多位选手叠加时互不遮盖轮廓 */
const OVERLAY_COLORS = ['#7fb0f7', '#f0a3a3', '#b39ce8', '#f0bd7e', '#7fd0c5']
function angleAt(index: number): number {
  return (Math.PI * 2 * index) / Math.max(props.metrics.length, 1) - Math.PI / 2
}

function pointAt(index: number, score: number): { x: number; y: number } {
  const clamped = Math.min(Math.max(score, 0), 100)
  const radius = (RADIUS * clamped) / 100
  const angle = angleAt(index)
  return { x: CENTER_X + radius * Math.cos(angle), y: CENTER_Y + radius * Math.sin(angle) }
}

function polygonOf(scores: number[]): string {
  return scores.map((score, index) => {
    const point = pointAt(index, score)
    return `${point.x.toFixed(1)},${point.y.toFixed(1)}`
  }).join(' ')
}

const gridPolygons = computed(() => GRID_LEVELS.map((level) => polygonOf(props.metrics.map(() => level))))
const playerPolygon = computed(() => polygonOf(props.metrics.map((metric) => Number(metric.playerScore))))
const averagePolygon = computed(() => polygonOf(props.metrics.map((metric) => Number(metric.averageScore))))
const playerPoints = computed(() => props.metrics.map((metric, index) => pointAt(index, Number(metric.playerScore))))

const overlaySeries = computed<OverlayWithPolygon[]>(() =>
  (props.overlay ?? []).map((series, index) => ({
    ...series,
    color: series.color || OVERLAY_COLORS[index % OVERLAY_COLORS.length],
    polygon: polygonOf(series.scores),
  })),
)

const showCorePolygons = computed(() => !(props.overlay && props.overlay.length))

interface AxisLabel {
  label: string
  x: number
  y: number
  anchor: 'start' | 'middle' | 'end'
  valueText: string
  rankText: string
}

const axisLabels = computed<AxisLabel[]>(() => props.metrics.map((metric, index) => {
  const angle = angleAt(index)
  const labelRadius = RADIUS + 35
  const cos = Math.cos(angle)
  const sin = Math.sin(angle)
  let x = CENTER_X + labelRadius * cos
  let y = CENTER_Y + labelRadius * sin
  let anchor: AxisLabel['anchor'] = 'middle'
  anchor = cos > 0.3 ? 'start' : cos < -0.3 ? 'end' : 'middle'
  return {
    label: metric.label,
    x,
    y,
    anchor,
    valueText: metric.available ? formatRadarMetricValue(metric.key, metric.value) : '暂无数据',
    rankText: metric.available ? `排名: ${metric.rank}` : '',
  }
}))

</script>

<template>
  <svg
    v-if="metrics.length"
    class="player-radar-chart"
    viewBox="0 0 700 560"
    role="img"
    aria-label="选手八维能力雷达图"
  >
    <polygon v-for="(grid, index) in gridPolygons" :key="`grid-${index}`" :points="grid" class="radar-grid" />
    <line
      v-for="(_, index) in metrics"
      :key="`axis-${index}`"
      :x1="CENTER_X"
      :y1="CENTER_Y"
      :x2="pointAt(index, 100).x"
      :y2="pointAt(index, 100).y"
      class="radar-axis"
    />
    <template v-if="showCorePolygons">
      <polygon :points="averagePolygon" class="radar-average" />
      <polygon :points="playerPolygon" class="radar-player" />
      <circle
        v-for="(point, index) in playerPoints"
        :key="`point-${index}`"
        :cx="point.x"
        :cy="point.y"
        r="3.2"
        class="radar-point"
      />
    </template>
    <template v-else>
      <polygon
        v-for="(series, index) in overlaySeries"
        :key="`overlay-${index}`"
        :points="series.polygon"
        :fill="`${series.color}33`"
        :stroke="series.color"
        stroke-width="2"
      />
    </template>
    <template v-for="(axis, index) in axisLabels" :key="`label-${index}`">
      <text :x="axis.x" :y="axis.y" :text-anchor="axis.anchor" class="radar-label">{{ axis.label }}</text>
      <text v-if="showCorePolygons" :x="axis.x" :y="axis.y + 15" :text-anchor="axis.anchor" class="radar-label-value">{{ axis.valueText }}</text>
      <text v-if="showCorePolygons && axis.rankText" :x="axis.x" :y="axis.y + 29" :text-anchor="axis.anchor" class="radar-label-rank">{{ axis.rankText }}</text>
    </template>
  </svg>
</template>

<style scoped>
.player-radar-chart { display: block; width: 100%; max-width: 700px; margin: 0 auto; background: #fff; }
.radar-grid { fill: none; stroke: #d8dee4; stroke-width: 1; }
.radar-axis { stroke: #e1e7ec; stroke-width: 1; }
.radar-average { fill: rgba(87, 96, 106, .20); stroke: #8b949e; stroke-width: 1.5; stroke-dasharray: 5 4; }
.radar-player { fill: rgba(47, 133, 90, .20); stroke: var(--accent); stroke-width: 2.4; }
.radar-point { fill: var(--accent); }
.radar-label { font-size: 13px; font-weight: 700; fill: #24292f; }
.radar-label-value { font-size: 12px; font-weight: 600; fill: #57606a; }
.radar-label-rank { font-size: 11px; font-weight: 700; fill: var(--accent); }
</style>
