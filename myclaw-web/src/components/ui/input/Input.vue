<script setup lang="ts">
import { cn } from '../../../lib/utils'
defineProps<{ class?: string; type?: string; placeholder?: string; disabled?: boolean; required?: boolean; minlength?: number }>()
const model = defineModel<string>({ default: '' })
</script>
<template><input v-model="model" :type="type" :placeholder="placeholder" :disabled="disabled" :required="required" :minlength="minlength" :class="cn('flex h-10 w-full rounded-lg border border-[var(--border)] bg-[var(--bg-surface)] px-3 text-sm outline-none transition-colors placeholder:text-[var(--text-faint)] focus:border-[var(--border-strong)] focus:ring-2 focus:ring-[var(--bg-subtle)] disabled:cursor-not-allowed disabled:opacity-50', $props.class)"></template>
