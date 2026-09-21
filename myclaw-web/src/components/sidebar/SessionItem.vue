<script setup lang="ts">
import {MessageSquare, Pencil, Trash2} from 'lucide-vue-next'
import {computed} from 'vue'
import type {Session} from '../../types'
import {formatRelativeTime} from '../../utils/format'

const props = defineProps<{
  session: Session
  active: boolean
  busy: boolean
}>()

const emit = defineEmits<{
  select: [id: string]
  rename: [session: Session]
  remove: [id: string]
}>()

/**
 * 侧栏已按「今天 / 更早」分组，今天内的条目不必再显示日期，
 * 只有更早的会话才补一个相对时间。
 */
const timeLabel = computed(() => {
  if (!props.session.updatedAt) return ''
  const stamp = new Date(props.session.updatedAt).getTime()
  if (Number.isNaN(stamp)) return ''

  const startOfToday = new Date()
  startOfToday.setHours(0, 0, 0, 0)
  if (stamp >= startOfToday.getTime()) return ''

  return formatRelativeTime(props.session.updatedAt)
})
</script>

<template>
  <div class="session-item" :class="{ active }">
    <button
        type="button"
        class="select"
        :disabled="busy"
        :aria-current="active ? 'page' : undefined"
        @click="emit('select', session.id)"
    >
      <MessageSquare :size="15" class="icon"/>
      <span class="title">{{ session.title }}</span>
      <span v-if="timeLabel" class="time">{{ timeLabel }}</span>
    </button>

    <div class="actions">
      <button
          type="button"
          class="action"
          :disabled="busy"
          :aria-label="`重命名对话：${session.title}`"
          @click.stop="emit('rename', session)"
      >
        <Pencil :size="13"/>
      </button>
      <button
          type="button"
          class="action danger"
          :disabled="busy"
          :aria-label="`删除对话：${session.title}`"
          @click.stop="emit('remove', session.id)"
      >
        <Trash2 :size="13"/>
      </button>
    </div>
  </div>
</template>

<style scoped>
.session-item {
  display: flex;
  align-items: center;
  border-radius: var(--radius-sm);
  transition: background var(--transition-fast);
}

.session-item:hover,
.session-item.active {
  background: var(--bg-subtle);
}

.select {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 8px 10px;
  text-align: left;
}

.icon {
  color: var(--text-faint);
  transition: color var(--transition-fast);
}

.session-item.active .icon {
  color: var(--accent);
}

.title {
  flex: 1;
  min-width: 0;
  font-size: 13.5px;
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-item.active .title {
  color: var(--text-primary);
  font-weight: 560;
}

.time {
  flex: 0 0 auto;
  font-size: 11px;
  color: var(--text-faint);
}

.actions {
  display: flex;
  gap: 2px;
  padding-right: 6px;
  opacity: 0;
  transition: opacity var(--transition-fast);
}

.session-item:hover .actions,
.session-item.active .actions,
.actions:focus-within {
  opacity: 1;
}

.action {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: var(--radius-xs);
  color: var(--text-muted);
}

.action:hover:not(:disabled) {
  background: var(--bg-surface);
  color: var(--text-primary);
}

.action.danger:hover:not(:disabled) {
  background: var(--danger-soft);
  color: var(--danger-text);
}

@media (hover: none), (max-width: 820px) {
  .select {
    min-height: 44px;
  }

  .actions {
    opacity: 1;
  }

  .action {
    width: 40px;
    height: 40px;
  }

  .time {
    display: none;
  }
}</style>
