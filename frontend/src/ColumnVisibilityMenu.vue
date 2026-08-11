<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

export interface ColumnOption {
  key: string
  label: string
}

const props = defineProps<{
  columns: ColumnOption[]
  modelValue: string[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string[]]
}>()

const root = ref<HTMLElement | null>(null)
const open = ref(false)

function isSelected(key: string): boolean {
  return props.modelValue.includes(key)
}

function toggleColumn(key: string) {
  const next = new Set(props.modelValue)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  emit('update:modelValue', props.columns.filter((column) => next.has(column.key)).map((column) => column.key))
}

function selectAll() {
  emit('update:modelValue', props.columns.map((column) => column.key))
}

function clearAll() {
  emit('update:modelValue', [])
}

function handlePointerDown(event: PointerEvent) {
  if (open.value && root.value && !root.value.contains(event.target as Node)) open.value = false
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') open.value = false
}

onMounted(() => {
  document.addEventListener('pointerdown', handlePointerDown)
  document.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handlePointerDown)
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <div ref="root" class="column-menu">
    <button
      type="button"
      class="column-menu-trigger"
      aria-haspopup="true"
      :aria-expanded="open"
      @click="open = !open"
    >
      列显示
      <span>{{ modelValue.length }}/{{ columns.length }}</span>
    </button>
    <div v-if="open" class="column-menu-dropdown" role="group" aria-label="选择显示列">
      <div class="column-menu-actions">
        <button type="button" @click="selectAll">全选</button>
        <button type="button" @click="clearAll">清空</button>
      </div>
      <label v-for="column in columns" :key="column.key" class="column-menu-option">
        <input
          type="checkbox"
          :checked="isSelected(column.key)"
          @change="toggleColumn(column.key)"
        />
        <span>{{ column.label }}</span>
      </label>
    </div>
  </div>
</template>
