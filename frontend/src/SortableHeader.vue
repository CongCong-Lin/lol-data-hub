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
  <th class="sortable-column" :data-sort-field="field" :aria-sort="ariaSort()">
    <button type="button" class="sort-header" @click="$emit('sort', field)">
      <span>{{ label }}</span>
      <span class="sort-indicator" :class="{ 'is-active': sortBy === field }" aria-hidden="true">
        {{ sortBy === field && sortDirection === 'asc' ? '▲' : '▼' }}
      </span>
    </button>
  </th>
</template>
