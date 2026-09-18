<script setup lang="ts">
import { ChevronRight, Wrench } from 'lucide-vue-next'
import type { ToolExecution } from '../../api'
import { formatDuration } from '../../utils/format'

defineProps<{ tools: ToolExecution[] }>()

const STATUS_LABEL: Record<ToolExecution['status'], string> = {
  running: '执行中',
  completed: '成功',
  failed: '失败',
  unknown: '未知',
}
</script>

<template>
  <div class="tool-calls">
    <details
      v-for="tool in tools"
      :key="tool.id"
      class="tool-call"
      :class="tool.status"
      :open="tool.status === 'failed'"
    >
      <summary :aria-label="`${tool.name}，状态：${STATUS_LABEL[tool.status]}，耗时：${formatDuration(tool.durationMillis)}`">
        <ChevronRight :size="13" class="chevron" />
        <Wrench :size="13" class="icon" />
        <strong>{{ tool.name }}</strong>
        <span class="status">{{ STATUS_LABEL[tool.status] }}</span>
        <small>{{ formatDuration(tool.durationMillis) }}</small>
      </summary>

      <div class="detail">
        <label>参数</label>
        <pre>{{ tool.arguments }}</pre>
        <label>结果</label>
        <pre>{{ tool.result }}</pre>
      </div>
    </details>
  </div>
</template>

<style scoped>
.tool-calls {
  display: grid;
  gap: 8px;
  margin-top: 16px;
}

.tool-call {
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--bg-surface);
  overflow: hidden;
}

.tool-call.failed {
  border-color: var(--danger-text);
}

summary {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 12px;
  cursor: pointer;
  list-style: none;
  font-size: 12.5px;
}

summary::-webkit-details-marker {
  display: none;
}

.chevron {
  color: var(--text-faint);
  transition: transform var(--transition-fast);
}

.tool-call[open] .chevron {
  transform: rotate(90deg);
}

.icon {
  color: var(--text-muted);
}

summary strong {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 580;
  color: var(--text-primary);
}

.status {
  padding: 2px 7px;
  border-radius: var(--radius-pill);
  font-size: 10.5px;
  background: var(--bg-subtle);
  color: var(--text-muted);
}

.tool-call.completed .status {
  background: var(--success-soft);
  color: var(--success-text);
}

.tool-call.failed .status {
  background: var(--danger-soft);
  color: var(--danger-text);
}

summary small {
  margin-left: auto;
  font-size: 11px;
  color: var(--text-faint);
}

.detail {
  padding: 0 12px 12px;
}

.detail label {
  display: block;
  margin: 8px 0 5px;
  font-size: 11px;
  color: var(--text-faint);
}

.detail pre {
  margin: 0;
  padding: 12px 14px;
  max-height: 220px;
  overflow: auto;
  border-radius: var(--radius-sm);
  background: var(--code-bg);
  color: var(--code-text);
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

@media (max-width: 820px) {
  summary { min-height: 44px; padding: 10px; }
  .detail { padding: 0 10px 10px; }
}</style>
