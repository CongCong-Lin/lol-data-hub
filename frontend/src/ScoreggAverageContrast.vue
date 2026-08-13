<script setup lang="ts">
import type { PlayerAverageContrastMetric } from './api'
import { formatPercent } from './formatters'

const props = defineProps<{
  playerName: string
  metrics: PlayerAverageContrastMetric[]
}>()

const GAP_EMPHASIS = 2

function bestValue(metric: PlayerAverageContrastMetric): number {
  return metric.higherIsBetter ? Number(metric.maxValue) : Number(metric.minValue)
}

function bestLabel(metric: PlayerAverageContrastMetric): string {
  return metric.higherIsBetter ? '赛事最高' : '赛事最佳'
}

function chartValue(metric: PlayerAverageContrastMetric, value: number): number {
  const rawValue = Number(value)
  if (metric.higherIsBetter) return rawValue
  return Number(metric.maxValue) - rawValue + Number(metric.minValue)
}

function scaleOf(metric: PlayerAverageContrastMetric): { baseline: number; range: number } {
  const best = chartValue(metric, bestValue(metric))
  const average = chartValue(metric, metric.averageValue)
  const gap = Math.max(best - average, 0)
  // 采用截断式基线，并将最佳值与平均值之间的视觉间距放大，数值仍以柱体标签为准。
  const baseline = Math.max(0, average - gap * GAP_EMPHASIS)
  return { baseline, range: Math.max(best - baseline, Number.EPSILON) }
}

function height(value: number, metric: PlayerAverageContrastMetric): string {
  const scale = scaleOf(metric)
  const ratio = (chartValue(metric, value) - scale.baseline) / scale.range
  return `${Math.max(0, Math.min(100, ratio * 100))}%`
}

function averageHeight(metric: PlayerAverageContrastMetric): string {
  return height(metric.averageValue, metric)
}

function bestHeight(metric: PlayerAverageContrastMetric): string {
  return height(bestValue(metric), metric)
}

function formatValue(value: number, metric: PlayerAverageContrastMetric): string {
  const number = Number(value)
  if (metric.percentage) return formatPercent(number)
  if (Math.abs(number) >= 1000) return `${(number / 1000).toFixed(1)}K`
  return number.toFixed(1)
}
</script>

<template>
  <section class="average-contrast" aria-labelledby="average-contrast-title">
    <header class="average-contrast-header">
      <div>
        <h2 id="average-contrast-title" class="detail-heading">职业场均对比</h2>
        <p class="detail-subheading">{{ playerName }} 与当前筛选范围内同位置合格选手比较</p>
      </div>
      <div class="average-contrast-legend" aria-label="图例">
        <span><i class="legend-swatch player" />当前选手</span>
        <span><i class="legend-swatch maximum" />赛事最高/最佳</span>
        <span><i class="legend-swatch average" />赛事平均</span>
      </div>
    </header>

    <p v-if="!metrics.length" class="detail-notice-inline">暂无可用的职业场均对比数据。</p>
    <div v-else class="average-contrast-scroll">
      <div class="average-contrast-grid">
        <article v-for="metric in metrics" :key="metric.key" class="average-contrast-item">
          <div class="contrast-bars" :aria-label="`${metric.label}：当前选手 ${formatValue(metric.value, metric)}，赛事平均 ${formatValue(metric.averageValue, metric)}，${bestLabel(metric)} ${formatValue(bestValue(metric), metric)}`">
            <div class="contrast-bar-column">
              <div class="contrast-bar-track">
                <div class="contrast-bar player-bar" :style="{ height: height(metric.value, metric) }">
                  <span class="bar-value">{{ formatValue(metric.value, metric) }}</span>
                </div>
              </div>
            </div>
            <div class="contrast-bar-column">
              <div class="contrast-bar-track">
                <div class="contrast-bar maximum-bar" :style="{ height: bestHeight(metric) }">
                  <span class="bar-value maximum-value">{{ formatValue(bestValue(metric), metric) }}</span>
                  <span class="average-bar" :style="{ height: averageHeight(metric) }">
                    <span class="bar-value average-value">{{ formatValue(metric.averageValue, metric) }}</span>
                  </span>
                </div>
              </div>
            </div>
          </div>
          <div class="contrast-label">{{ metric.label }}</div>
          <div class="contrast-rank">赛事第 <strong>{{ metric.rank }}</strong> 名</div>
        </article>
      </div>
    </div>
    <p v-if="metrics.length" class="average-contrast-note">对比柱采用差距增强刻度，数值以标签为准；死亡指标按低值优先反向显示。</p>
  </section>
</template>

<style scoped>
.average-contrast { min-width: 0; }
.average-contrast-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; padding-bottom: 10px; border-bottom: 1px solid var(--line); }
.detail-heading { margin: 0 0 6px; font-size: 16px; }
.detail-subheading { margin: 0; color: #8b949e; font-size: 12.5px; }
.average-contrast-legend { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px 12px; color: #57606a; font-size: 11px; white-space: nowrap; }
.average-contrast-legend span { display: inline-flex; align-items: center; gap: 4px; }
.legend-swatch { display: inline-block; width: 10px; height: 10px; border-radius: 2px; border: 1px solid rgba(36, 41, 47, .12); }
.legend-swatch.player { background: #2f855a; }
.legend-swatch.maximum { background: #d8dee4; }
.legend-swatch.average { background: #8b949e; }
.average-contrast-scroll { overflow-x: auto; padding: 16px 0 2px; }
.average-contrast-grid { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); min-width: 0; gap: 4px; }
.average-contrast-item { min-width: 0; text-align: center; }
.contrast-bars { height: 190px; display: grid; grid-template-columns: 1fr 1fr; align-items: end; justify-content: center; gap: 4px; padding: 0 1px; }
.contrast-bar-column { height: 100%; min-width: 0; display: flex; align-items: flex-end; justify-content: center; }
.contrast-bar-track { position: relative; height: 170px; width: 100%; max-width: 32px; display: flex; align-items: flex-end; justify-content: center; }
.contrast-bar { position: relative; width: 100%; min-height: 2px; border-radius: 4px 4px 1px 1px; transition: height .2s ease; }
.player-bar { background: #2f855a; }
.maximum-bar { background: #d8dee4; }
.average-bar { position: absolute; left: 0; right: 0; bottom: 0; min-height: 2px; background: #8b949e; border-radius: 3px 3px 1px 1px; }
.bar-value { position: absolute; bottom: calc(100% + 4px); left: 50%; transform: translateX(-50%); color: #24292f; font-size: 10px; line-height: 1; white-space: nowrap; }
.average-value { z-index: 1; color: #57606a; }
.maximum-value { color: #57606a; }
.contrast-label { margin-top: 8px; color: #24292f; font-size: 12px; font-weight: 650; white-space: nowrap; }
.contrast-rank { margin-top: 4px; color: #8b949e; font-size: 11px; white-space: nowrap; }
.contrast-rank strong { color: var(--accent); font-weight: 700; }
.average-contrast-note { margin: 10px 0 0; color: #8b949e; font-size: 11px; }
.detail-notice-inline { color: #57606a; font-size: 13px; }
@media (max-width: 700px) {
  .average-contrast-header { display: block; }
  .average-contrast-legend { justify-content: flex-start; margin-top: 8px; white-space: normal; }
}
</style>
