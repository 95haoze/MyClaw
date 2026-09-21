<script setup lang="ts">
import {computed} from 'vue'
import {cva} from 'class-variance-authority'
import {cn} from '../../../lib/utils'

const styles = cva('inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-lg text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--border-strong)] disabled:pointer-events-none disabled:opacity-45', {
  variants: {
    variant: {
      default: 'bg-[var(--text-primary)] text-[var(--bg-surface)] hover:opacity-90',
      outline: 'border border-[var(--border)] bg-[var(--bg-surface)] hover:bg-[var(--bg-subtle)]',
      ghost: 'text-[var(--text-muted)] hover:bg-[var(--bg-subtle)] hover:text-[var(--text-primary)]',
      danger: 'text-[var(--danger-text)] hover:bg-[var(--danger-soft)]',
    },
    size: {default: 'h-9 px-4', sm: 'h-8 px-3 text-xs', icon: 'size-8 p-0'},
  },
  defaultVariants: {variant: 'default', size: 'default'},
})
type Props = {
  variant?: 'default' | 'outline' | 'ghost' | 'danger';
  size?: 'default' | 'sm' | 'icon';
  class?: string;
  type?: 'button' | 'submit' | 'reset'
}
const props = withDefaults(defineProps<Props>(), {type: 'button'})
const classes = computed(() => cn(styles({variant: props.variant, size: props.size}), props.class))
</script>
<template>
  <button :type="type" :class="classes">
    <slot/>
  </button>
</template>
