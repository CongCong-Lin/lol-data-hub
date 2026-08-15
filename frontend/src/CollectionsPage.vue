<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, type CollectionStatusRow } from './api'
import { useI18n } from './i18n'

const { t } = useI18n()

const LIMIT = 50

const loading = ref(false)
const error = ref('')
const rows = ref<CollectionStatusRow[]>([])

const TYPE_LABELS: Record<string, string> = {
  MATCH_GAME: '对局明细',
  HERO: '英雄数据',
  TEAM: '战队数据',
  PLAYER: '选手数据',
}

function typeLabel(type: string): string {
  return TYPE_LABELS[type] ?? type
}

function statusLabel(status: string): string {
  return t(`collections.status.${status}`) ?? status
}

function statusClass(status: string): string {
  if (status === 'SUCCESS') return 'status-success'
  if (status === 'FAILED') return 'status-failed'
  return 'status-running'
}

function fmtDateTime(value: string | null): string {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ').slice(0, 19)
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

async function load() {
  const seq = ++loadSeq
  loading.value = true
  error.value = ''
  try {
    const data = await api.collectionStatus(LIMIT)
    if (seq === loadSeq) rows.value = data
  } catch (reason) {
    if (seq === loadSeq) error.value = reason instanceof Error ? reason.message : t('collections.loadFailed')
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}
let loadSeq = 0

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="collections-panel">
    <div class="table-toolbar">
      <div>
        <p class="eyebrow">COLLECTION STATUS</p>
        <h2>{{ t('collections.title') }}</h2>
      </div>
      <p class="toolbar-note">{{ t('collections.note', { n: LIMIT }) }}；对局明细（MATCH_GAME）为逐局战绩回填，其余类型为常规统计采集。</p>
    </div>

    <p v-if="error" class="message error">{{ error }}</p>
    <p v-if="loading" class="message success">{{ t('common.loading') }}</p>

    <div v-if="rows.length" class="table-scroll" tabindex="0" aria-label="采集运行记录表">
        <table class="collection-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>{{ t('collections.type') }}</th>
              <th>{{ t('collections.season') }}</th>
              <th>{{ t('collections.stages') }}</th>
              <th>{{ t('collections.status') }}</th>
              <th>{{ t('collections.startedAt') }}</th>
              <th>{{ t('collections.finishedAt') }}</th>
              <th>{{ t('collections.changedRecords') }}</th>
              <th>{{ t('collections.error') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
              <td>#{{ row.id }}</td>
              <td>
                <span class="type-badge">{{ typeLabel(row.collectionType) }}</span>
              </td>
              <td>{{ row.sourceSeasonId ?? '—' }}</td>
              <td class="stages-cell">{{ row.requestedStageIds || '—' }}</td>
              <td>
                <span class="status-badge" :class="statusClass(row.status)">{{ statusLabel(row.status) }}</span>
              </td>
              <td>{{ fmtDateTime(row.startedAt) }}</td>
              <td>{{ fmtDateTime(row.finishedAt) }}</td>
              <td>{{ row.changedRecords }}</td>
              <td class="error-cell" :title="row.errorMessage ?? ''">{{ row.errorMessage || '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="!loading" class="empty-state">
        <strong>暂无采集记录</strong>
        <p>采集任务运行后这里会显示最近 {{ LIMIT }} 条执行记录。</p>
      </div>
  </div>
</template>

<style scoped>
.toolbar-note { max-width: 420px; color: var(--muted); font-size: 12px; line-height: 1.6; margin: 0; }
.collection-table { width: 100%; border-collapse: collapse; font-size: 12.5px; min-width: 980px; }
.collection-table th, .collection-table td { padding: 9px 12px; border-bottom: 1px solid var(--line); text-align: left; white-space: nowrap; }
.collection-table thead th { color: var(--text-3); font-size: 12px; background: var(--th-bg); }
.collection-table tbody tr:hover { background: var(--hover-bg); }
.type-badge {
  display: inline-block; padding: 2px 8px; border: 1px solid var(--accent-line); border-radius: 999px;
  color: var(--accent-dark); background: var(--accent-soft); font-size: 11px; font-weight: 650;
}
.status-badge { display: inline-block; min-width: 46px; padding: 2px 9px; border-radius: 999px; text-align: center; font-size: 11px; font-weight: 700; }
.status-success { color: var(--accent-dark); background: var(--accent-soft); }
.status-failed { color: var(--danger); background: var(--danger-soft); }
.status-running { color: #b07d0e; background: #fdf3d7; }
.stages-cell { max-width: 260px; overflow: hidden; text-overflow: ellipsis; }
.error-cell { max-width: 320px; overflow: hidden; text-overflow: ellipsis; color: var(--danger); }
</style>
