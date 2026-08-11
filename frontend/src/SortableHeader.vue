<script setup lang="ts">
const props = defineProps<{
  label: string
  field: string
  sortBy: string
  sortDirection: 'asc' | 'desc'
}>()

defineEmits<{
  sort: [field: string]
}>()

function ariaSort(): 'ascending' | 'descending' | 'none' {
  if (props.sortBy !== props.field) return 'none'
  return props.sortDirection === 'asc' ? 'ascending' : 'descending'
}
</script>

<template>
  <th class="sortable-column" :aria-sort="ariaSort()">
    <button type="button" class="sort-header" @click="$emit('sort', field)">
      <span>{{ label }}</span>
      <span v-if="sortBy === field" class="sort-indicator" aria-hidden="true">
        {{ sortDirection === 'desc' ? '▼' : '▲' }}
      </span>
    </button>
  </th>
</template>
