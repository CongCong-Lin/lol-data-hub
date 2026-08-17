<script setup lang="ts">
import { computed } from 'vue'
import type { ChampionTrendPoint } from './api'

const props = defineProps<{ trends: ChampionTrendPoint[] }>()

const SLOT = 90
const HEIGHT = 260
const PAD = { top: 18, right: 14, bottom: 36, left: 34 }

const chartWidth = computed(() => Math.max(props.trends.length * SLOT, 360))
const plotWidth = computed(() => chartWidth.value - PAD.left - PAD.right)
const plotHeight = HEIGHT - PAD.top - PAD.bottom

const maxPickCount = computed(() =>
  Math.max(1, ...props.trends.map((point) => point.pickCount)),
)

const plotBottom = PAD.top + plotHeight

function xAt(index: number): number {
  return PAD.left + index * SLOT + SLOT / 2
}

function yCount(value: number): number {
  return PAD.top + (1 - value / maxPickCount.value) * plotHeight
}

function yRate(rate: number): number {
  return PAD.top + (1 - rate) * plotHeight
}

/** 柱顶 y：禁用数可能超出出场数比例基准，超出时截断到绘图区顶部。 */
function barTop(value: number): number {
  return Math.max(yCount(value), PAD.top)
}

function barHeight(value: number): number {
  return plotBottom - barTop(value)
}

const GRID_LEVELS = [0.25, 0.5, 0.75, 1]

const pickLine = computed(() =>
  props.trends.map((point, index) => `${xAt(index).toFixed(1)},${yCount(point.pickCount).toFixed(1)}`).join(' '),
)
const banLine = computed(() =>
  props.trends.map((point, index) => `${xAt(index).toFixed(1)},${barTop(point.banCount).toFixed(1)}`).join(' '),
)
const winRateLine = computed(() =>
  props.trends.map((point, index) => `${xAt(index).toFixed(1)},${yRate(point.winningRate).toFixed(1)}`).join(' '),
)

function stageShortName(name: string): string {
  return name.length > 8 ? `${name.slice(0, 8)}…` : name
}

function formatCount(value: number): string {
  return value >= 1000 ? `${(value / 1000).toFixed(1)}k` : String(value)
}
</script>

<template>
  <div v-if="trends.length" class="trend-chart">
    <svg :viewBox="`0 0 ${chartWidth} ${HEIGHT}`" class="trend-svg" role="img" aria-label="英雄赛段趋势图">
      <line
        v-for="level in GRID_LEVELS"
        :key="`grid-${level}`"
        :x1="PAD.left"
        :x2="chartWidth - PAD.right"
        :y1="yRate(level)"
        :y2="yRate(level)"
        class="trend-grid"
      />
      <text :x="chartWidth - PAD.right" :y="yRate(1) - 4" text-anchor="end" class="trend-axis-label">100%</text>
      <text :x="chartWidth - PAD.right" :y="yRate(0.5) - 4" text-anchor="end" class="trend-axis-label">50%</text>
      <text :x="chartWidth - PAD.right" :y="yRate(0) - 4" text-anchor="end" class="trend-axis-label">0%</text>
      <rect
        v-for="(point, index) in trends"
        :key="`bar-pick-${point.sourceStageId}`"
        :x="xAt(index) - 14"
        :y="barTop(point.pickCount)"
        :width="11"
        :height="barHeight(point.pickCount)"
        class="trend-bar trend-bar-pick"
      />
      <rect
        v-for="(point, index) in trends"
        :key="`bar-ban-${point.sourceStageId}`"
        :x="xAt(index) + 3"
        :y="barTop(point.banCount)"
        :width="11"
        :height="barHeight(point.banCount)"
        class="trend-bar trend-bar-ban"
      />
      <polyline :points="pickLine" class="trend-line trend-line-pick" />
      <polyline :points="banLine" class="trend-line trend-line-ban" />
      <polyline :points="winRateLine" class="trend-line trend-line-rate" />
      <g v-for="(point, index) in trends" :key="`label-${point.sourceStageId}`">
        <text :x="xAt(index)" :y="HEIGHT - 14" text-anchor="middle" class="trend-stage-label">
          {{ stageShortName(point.stageName) }}
        </text>
        <title>{{ point.stageName }}：出场 {{ point.pickCount }} · 禁用 {{ point.banCount }} · 胜率 {{ (point.winningRate * 100).toFixed(1) }}%</title>
      </g>
    </svg>
    <div class="trend-legend">
      <span class="legend-item legend-pick">出场 {{ formatCount(maxPickCount) }}</span>
      <span class="legend-item legend-ban">禁用</span>
      <span class="legend-item legend-rate">胜率</span>
      <span class="legend-hint">柱高按出场数归一化，胜率按 0—100% 映射</span>
    </div>
  </div>
</template>

<style scoped>
.trend-chart { overflow-x: auto; }
.trend-svg { display: block; min-width: 360px; width: 100%; background: var(--panel); }
.trend-grid { stroke: var(--line); stroke-width: 1; stroke-dasharray: 3 4; }
.trend-axis-label { font-size: 10px; fill: var(--text-4); }
.trend-bar { rx: 2; }
.trend-bar-pick { fill: var(--accent); opacity: .78; }
.trend-bar-ban { fill: var(--placeholder-bg); stroke: var(--line-strong); stroke-width: 1; }
.trend-line { fill: none; stroke-width: 2; stroke-linejoin: round; stroke-linecap: round; }
.trend-line-pick { stroke: var(--accent-dark); }
.trend-line-ban { stroke: var(--text-4); stroke-width: 1.4; }
.trend-line-rate { stroke: #c08a2e; }
.trend-stage-label { font-size: 11px; fill: var(--text-4); }
.trend-legend { display: flex; align-items: center; gap: 14px; margin-top: 8px; font-size: 11px; color: var(--muted); flex-wrap: wrap; }
.legend-item { display: inline-flex; align-items: center; gap: 5px; }
.legend-item::before { content: ''; width: 10px; height: 10px; border-radius: 2px; }
.legend-pick::before { background: var(--accent); opacity: .78; }
.legend-ban::before { background: var(--placeholder-bg); border: 1px solid var(--line-strong); }
.legend-rate::before { background: transparent; border-top: 2px solid #c08a2e; border-radius: 0; height: 0; }
.legend-hint { margin-left: auto; }
</style>
