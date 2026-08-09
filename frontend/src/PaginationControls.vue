<script setup lang="ts">
import { computed } from 'vue'

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100]

const props = defineProps<{
  totalItems: number
  currentPage: number
  pageSize: number
}>()

const emit = defineEmits<{
  'update:currentPage': [value: number]
  'update:pageSize': [value: number]
}>()

const totalPages = computed(() => Math.max(1, Math.ceil(props.totalItems / props.pageSize)))
const boundedCurrentPage = computed(() => Math.min(Math.max(props.currentPage, 1), totalPages.value))
const startItem = computed(() => props.totalItems === 0 ? 0 : (boundedCurrentPage.value - 1) * props.pageSize + 1)
const endItem = computed(() => Math.min(boundedCurrentPage.value * props.pageSize, props.totalItems))

const pageNumbers = computed(() => {
  const visibleCount = Math.min(5, totalPages.value)
  let start = Math.max(1, boundedCurrentPage.value - 2)
  let end = Math.min(totalPages.value, start + visibleCount - 1)
  start = Math.max(1, end - visibleCount + 1)
  return Array.from({ length: end - start + 1 }, (_, index) => start + index)
})

function goToPage(page: number) {
  emit('update:currentPage', Math.min(Math.max(page, 1), totalPages.value))
}

function updatePageSize(event: Event) {
  const value = Number((event.target as HTMLSelectElement).value)
  emit('update:pageSize', value)
  emit('update:currentPage', 1)
}
</script>

<template>
  <nav class="pagination" aria-label="统计结果分页">
    <div class="pagination-meta">
      <span class="pagination-row-count">第 {{ startItem }}–{{ endItem }} 项，共 {{ totalItems }} 项</span>
      <label>
        每页
        <select class="page-size-select" :value="pageSize" @change="updatePageSize">
          <option v-for="option in PAGE_SIZE_OPTIONS" :key="option" :value="option">{{ option }}</option>
        </select>
        项
      </label>
    </div>
    <div class="pagination-buttons">
      <button type="button" :disabled="boundedCurrentPage === 1" @click="goToPage(1)">首页</button>
      <button
        type="button"
        class="pagination-previous"
        :disabled="boundedCurrentPage === 1"
        @click="goToPage(boundedCurrentPage - 1)"
      >上一页</button>
      <button
        v-for="page in pageNumbers"
        :key="page"
        type="button"
        class="pagination-page"
        :class="{ active: page === boundedCurrentPage }"
        :aria-current="page === boundedCurrentPage ? 'page' : undefined"
        :aria-label="`第 ${page} 页`"
        @click="goToPage(page)"
      >{{ page }}</button>
      <button
        type="button"
        class="pagination-next"
        :disabled="boundedCurrentPage === totalPages"
        @click="goToPage(boundedCurrentPage + 1)"
      >下一页</button>
      <button
        type="button"
        :disabled="boundedCurrentPage === totalPages"
        @click="goToPage(totalPages)"
      >末页</button>
    </div>
  </nav>
</template>
