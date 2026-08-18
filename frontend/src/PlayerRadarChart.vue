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

/** 叠加模式下轴标签旁的指标数据小方框：每名对比选手一行（名字 + 颜色 + 格式化数值）。
 *  与 metrics 等长，索引一一对应。 */
export interface RadarAxisBox {
  /** 轴指标标题（与 metrics 的 label 一致） */
  label: string
  /** 每名对比选手一行 */
  rows: Array<{ name: string; color: string; text: string }>
}

interface OverlayWithPolygon extends RadarOverlaySeries {
  polygon: string
}

const props = defineProps<{
  metrics: PlayerRadarMetric[]
  /** 可选：多选手叠加多边形（对比模式），scores 与 metrics 一一对应 */
  overlay?: RadarOverlaySeries[]
  /** 可选：叠加模式下每个轴标签旁的数据小方框（仅对比模式使用） */
  axisBoxes?: RadarAxisBox[]
}>()

const CENTER_X = 350
const CENTER_Y = 280
const RADIUS = 128
const GRID_LEVELS = [20, 40, 60, 80, 100]
/** 对比叠加调色板：浅色系 + 20% 透明填充，多位选手叠加时互不遮盖轮廓 */
const OVERLAY_COLORS = ['#7fb0f7', '#f0a3a3', '#b39ce8', '#f0bd7e', '#7fd0c5']
/** 数据小方框几何：框沿各自轴向向外放置，相邻框互不遮挡 */
const BOX_DIST = 215
const BOX_WIDTH = 118
const BOX_ROW_HEIGHT = 15.5
const BOX_PAD_Y = 7
const BOX_SWATCH = 9

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
  const withBoxes = !!(props.axisBoxes && props.axisBoxes.length)
  if (withBoxes && cos > 0.9) {
    // 右侧水平轴：标签移到方框外侧，避免被白底方框覆盖
    x = CENTER_X + BOX_DIST + BOX_WIDTH / 2 + 8
    y = CENTER_Y
    anchor = 'start'
  } else if (withBoxes && cos < -0.9) {
    // 左侧水平轴：同上，标签置于方框左侧
    x = CENTER_X - BOX_DIST - BOX_WIDTH / 2 - 8
    y = CENTER_Y
    anchor = 'end'
  } else {
    anchor = cos > 0.3 ? 'start' : cos < -0.3 ? 'end' : 'middle'
  }
  return {
    label: metric.label,
    x,
    y,
    anchor,
    valueText: metric.available ? formatRadarMetricValue(metric.key, metric.value) : '暂无数据',
    rankText: metric.available ? `排名: ${metric.rank}` : '',
  }
}))

interface PositionedAxisBox extends RadarAxisBox {
  x: number
  y: number
  width: number
  height: number
}

/** 每个轴标签外侧的数据方框位置：沿该轴径向向外放置，保证 1~5 名选手时相邻框不重叠。 */
const positionedBoxes = computed<PositionedAxisBox[]>(() =>
  (props.axisBoxes ?? []).map((box, index) => {
    const angle = angleAt(index)
    const height = BOX_PAD_Y * 2 + box.rows.length * BOX_ROW_HEIGHT
    const cx = CENTER_X + BOX_DIST * Math.cos(angle)
    const cy = CENTER_Y + BOX_DIST * Math.sin(angle)
    return { ...box, width: BOX_WIDTH, height, x: cx - BOX_WIDTH / 2, y: cy - height / 2 }
  }),
)

function boxRowY(box: PositionedAxisBox, rowIndex: number): number {
  return box.y + BOX_PAD_Y + BOX_ROW_HEIGHT * (rowIndex + 0.5)
}
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
      <g v-if="!showCorePolygons && positionedBoxes[index]" :key="`box-${index}`">
        <rect
          :x="positionedBoxes[index].x"
          :y="positionedBoxes[index].y"
          :width="positionedBoxes[index].width"
          :height="positionedBoxes[index].height"
          rx="6"
          class="radar-axis-box"
        />
        <g v-for="(row, rowIndex) in positionedBoxes[index].rows" :key="`box-row-${index}-${rowIndex}`">
          <rect
            :x="positionedBoxes[index].x + 8"
            :y="boxRowY(positionedBoxes[index], rowIndex) - BOX_SWATCH / 2"
            :width="BOX_SWATCH"
            :height="BOX_SWATCH"
            rx="2"
            :fill="row.color"
            class="radar-axis-swatch"
          />
          <text
            :x="positionedBoxes[index].x + 22"
            :y="boxRowY(positionedBoxes[index], rowIndex) + 3.5"
            class="radar-axis-name"
          >{{ row.name }}:</text>
          <text
            :x="positionedBoxes[index].x + positionedBoxes[index].width - 8"
            :y="boxRowY(positionedBoxes[index], rowIndex) + 3.5"
            text-anchor="end"
            class="radar-axis-value"
          >{{ row.text }}</text>
        </g>
      </g>
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
.radar-axis-box { fill: #fff; stroke: #d8dee4; stroke-width: 1; }
.radar-axis-swatch { stroke: rgba(0, 0, 0, .06); }
.radar-axis-name { font-size: 11.5px; font-weight: 650; fill: #24292f; }
.radar-axis-value { font-size: 11.5px; font-weight: 600; fill: #57606a; font-variant-numeric: tabular-nums; }
</style>
