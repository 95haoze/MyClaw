<script setup lang="ts">
import type {Component} from 'vue'

withDefaults(
    defineProps<{
      icon: Component
      /** 同时作为 aria-label 和原生 tooltip，无障碍与鼠标悬停共用一份文案。 */
      label: string
      size?: number
      disabled?: boolean
    }>(),
    {size: 16, disabled: false},
)
</script>

<template>
  <button type="button" class="icon-button" :disabled="disabled" :aria-label="label" :title="label">
    <component :is="icon" :size="size"/>
  </button>
</template>

<style scoped>
.icon-button {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  color: var(--text-muted);
}

.icon-button:hover:not(:disabled) {
  background: var(--bg-subtle);
  color: var(--text-primary);
}
</style>
