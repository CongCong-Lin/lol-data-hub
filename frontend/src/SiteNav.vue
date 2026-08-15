<script setup lang="ts">
import { ref } from 'vue'
import GlobalSearch from './GlobalSearch.vue'
import { setLocale, useI18n, type Locale } from './i18n'
import { applyTheme, type Theme } from './theme'

const props = defineProps<{
  stageKeys: string[]
  currentView: string
}>()

const emit = defineEmits<{ navigate: [view: string] }>()

const { t, locale } = useI18n()

const theme = ref<Theme>('light')
if (typeof document !== 'undefined') {
  theme.value = document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light'
}

function toggleTheme() {
  const next: Theme = theme.value === 'dark' ? 'light' : 'dark'
  theme.value = next
  applyTheme(next)
}

function toggleLocale() {
  const next: Locale = locale.value === 'zh-CN' ? 'en-US' : 'zh-CN'
  setLocale(next)
}

const STATISTIC_VIEWS = new Set(['champion', 'team', 'player', 'combo'])

/** 当前视图与导航项匹配时高亮；「统计查询」覆盖四个统计视图。 */
function isActive(view: string): boolean {
  if (view === 'statistics') return STATISTIC_VIEWS.has(props.currentView)
  return props.currentView === view
}
</script>

<template>
  <header class="site-nav">
    <div class="site-nav-inner">
      <a href="/" class="site-brand">LoL&nbsp;Data&nbsp;Hub</a>
      <nav class="site-links" aria-label="主导航">
        <a href="/?view=champion" class="site-link" :class="{ active: isActive('statistics') }" @click.prevent="emit('navigate', 'champion')">{{ t('nav.statistics') }}</a>
        <a href="/?view=matches" class="site-link" :class="{ active: isActive('matches') }" @click.prevent="emit('navigate', 'matches')">{{ t('nav.matches') }}</a>
        <a href="/?view=compare" class="site-link" :class="{ active: isActive('compare') }" @click.prevent="emit('navigate', 'compare')">{{ t('nav.compare') }}</a>
        <a href="/?view=collections" class="site-link" :class="{ active: isActive('collections') }" @click.prevent="emit('navigate', 'collections')">{{ t('nav.collections') }}</a>
      </nav>
      <div class="site-actions">
        <GlobalSearch :stage-keys="props.stageKeys" />
        <button
          type="button"
          class="nav-icon-btn"
          :title="t('theme.toggle')"
          :aria-label="t('theme.toggle')"
          @click="toggleTheme"
        >{{ theme === 'dark' ? '☀️' : '🌙' }}</button>
        <button
          type="button"
          class="nav-icon-btn lang-btn"
          :title="t('lang.toggle')"
          @click="toggleLocale"
        >{{ locale === 'zh-CN' ? 'EN' : '中' }}</button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.site-nav { border-bottom: 1px solid var(--line); background: var(--panel); }
.site-nav-inner {
  width: min(1440px, calc(100% - 48px)); margin: 0 auto;
  display: flex; align-items: center; gap: 22px; padding: 12px 0;
}
.site-brand { color: var(--accent); font-weight: 800; font-size: 15px; letter-spacing: .02em; text-decoration: none; white-space: nowrap; }
.site-links { display: flex; align-items: center; gap: 4px; flex-wrap: wrap; }
.site-link {
  padding: 6px 12px; border-radius: 6px; color: var(--muted);
  font-size: 13.5px; font-weight: 600; text-decoration: none; white-space: nowrap;
}
.site-link:hover { color: var(--text-2); background: var(--panel-2); }
.site-link.active { color: var(--accent); background: var(--accent-soft); }
.site-actions { margin-left: auto; display: flex; align-items: center; gap: 8px; }
.nav-icon-btn {
  flex: 0 0 auto; min-width: 34px; height: 34px; padding: 0 8px;
  border: 1px solid var(--line-strong); border-radius: 6px;
  color: var(--text-3); background: var(--panel); font-size: 14px; line-height: 1;
}
.nav-icon-btn:hover { border-color: var(--accent-line); color: var(--accent); }
.lang-btn { font-size: 12px; font-weight: 700; }
@media (max-width: 860px) {
  .site-nav-inner { flex-wrap: wrap; gap: 10px; }
  .site-actions { margin-left: 0; width: 100%; }
}
</style>
