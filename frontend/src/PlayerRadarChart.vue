<script setup lang="ts">
import { computed } from 'vue'
import type { PlayerRadarMetric } from './api'

const props = defineProps<{ metrics: PlayerRadarMetric[] }>()

const CENTER_X = 180
const CENTER_Y = 152
const RADIUS = 100
const GRID_LEVELS = [25, 50, 75, 100]

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
  return scores
    .map((score, index) => {
      const point = pointAt(index, score)
      return `${point.x.toFixed(1)},${point.y.toFixed(1)}`
    })
    .join(' ')
}

const gridPolygons = computed(() => GRID_LEVELS.map((level) => polygonOf(props.metrics.map(() => level))))
const playerPolygon = computed(() => polygonOf(props.metrics.map((metric) => Number(metric.playerScore))))
const averagePolygon = computed(() => polygonOf(props.metrics.map((metric) => Number(metric.averageScore))))
const playerPoints = computed(() =>
  props.metrics.map((metric, index) => pointAt(index, Number(metric.playerScore))),
)

interface AxisLabel {
  label: string
  x: number
  y: number
  anchor: 'start' | 'middle' | 'end'
  valueText: string
}

const axisLabels = computed<AxisLabel[]>(() =>
  props.metrics.map((metric, index) => {
    const angle = angleAt(index)
    const labelRadius = RADIUS + 22
    const x = CENTER_X + labelRadius * Math.cos(angle)
    const y = CENTER_Y + labelRadius * Math.sin(angle)
    const cos = Math.cos(angle)
    const anchor: 'start' | 'middle' | 'end' = cos > 0.3 ? 'start' : cos < -0.3 ? 'end' : 'middle'
    return {
      label: metric.label,
      x,
      y,
      anchor,
      valueText: `${formatValue(metric.value)}（均值 ${formatValue(metric.averageValue)}）`,
    }
  }),
)

function formatValue(value: number): string {
  return Number(value).toFixed(2)
}
</script>

<template>
  <svg
    v-if="metrics.length"
    class="player-radar-chart"
    viewBox="0 0 360 316"
    role="img"
    aria-label="选手六维能力雷达图"
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
    <polygon :points="averagePolygon" class="radar-average" />
    <polygon :points="playerPolygon" class="radar-player" />
    <circle
      v-for="(point, index) in playerPoints"
      :key="`point-${index}`"
      :cx="point.x"
      :cy="point.y"
      r="3"
      class="radar-point"
    />
    <template v-for="(axis, index) in axisLabels" :key="`label-${index}`">
      <text :x="axis.x" :y="axis.y" :text-anchor="axis.anchor" class="radar-label">{{ axis.label }}</text>
      <text :x="axis.x" :y="axis.y + 13" :text-anchor="axis.anchor" class="radar-label-value">{{ axis.valueText }}</text>
    </template>
  </svg>
</template>

<style scoped>
.player-radar-chart { display: block; width: 100%; max-width: 420px; margin: 0 auto; background: #fff; }
.radar-grid { fill: none; stroke: var(--line); stroke-width: 1; }
.radar-axis { stroke: var(--line); stroke-width: 1; }
.radar-average { fill: none; stroke: #8b949e; stroke-width: 1.5; stroke-dasharray: 5 4; }
.radar-player { fill: rgba(47, 133, 90, 0.16); stroke: var(--accent); stroke-width: 2; }
.radar-point { fill: var(--accent); }
.radar-label { font-size: 12px; font-weight: 650; fill: #24292f; }
.radar-label-value { font-size: 10.5px; fill: #8b949e; }
</style>
