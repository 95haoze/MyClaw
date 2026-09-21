<script setup lang="ts">
import {ChevronRight, Wrench} from 'lucide-vue-next'
import {computed} from 'vue'
import type {ToolExecution} from '../../api'
import {formatDuration} from '../../utils/format'

const props = defineProps<{ tools: ToolExecution[] }>()

const STATUS_LABEL: Record<ToolExecution['status'], string> = {
  running: '执行中',
  completed: '成功',
  failed: '失败',
  unknown: '未知',
}

const failedCount = computed(() => props.tools.filter(tool => tool.status === 'failed').length)
const latest = computed(() => props.tools[props.tools.length - 1])
</script>

<template>
  <details class="tool-calls">
    <summary class="tool-calls-summary"
             :aria-label="`查看工具调用详情，共 ${tools.length} 次，最近一次：${latest ? STATUS_LABEL[latest.status] : ''}`">
      <Wrench :size="13" class="icon"/>
      <span>工具调用</span>
      <span class="count">{{ tools.length }}</span>
      <span v-if="latest" class="latest-status" :class="latest.status">
        <span class="dot"/>
        <span>{{ STATUS_LABEL[latest.status] }}</span>
        <span v-if="latest.durationMillis" class="latest-duration">{{ formatDuration(latest.durationMillis) }}</span>
      </span>
      <span v-if="failedCount" class="failed-badge"><span class="dot"/>{{ failedCount }} 次失败</span>
      <ChevronRight :size="13" class="chevron"/>
    </summary>

    <div class="tool-list">
      <details
          v-for="tool in tools"
          :key="tool.id"
          class="tool-call"
          :class="tool.status"
          :open="tool.status === 'failed'"
      >
        <summary
            :aria-label="`${tool.name}，状态：${STATUS_LABEL[tool.status]}，耗时：${formatDuration(tool.durationMillis)}`">
          <ChevronRight :size="13" class="chevron"/>
          <Wrench :size="13" class="icon"/>
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
  </details>
</template>

<style scoped>
.tool-calls {
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--bg-surface);
  overflow: hidden;
  margin-top: 16px;
}

.tool-calls-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 12px;
  cursor: pointer;
  list-style: none;
  font-size: 12.5px;
  color: var(--text-secondary);
  user-select: none;
}

.tool-calls-summary::-webkit-details-marker {
  display: none;
}

.tool-calls-summary .icon {
  color: var(--text-muted);
}

.count {
  min-width: 18px;
  padding: 1px 7px;
  border-radius: var(--radius-pill);
  background: var(--bg-subtle);
  color: var(--text-muted);
  font-size: 11px;
  text-align: center;
}

.latest-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: var(--text-muted);
}

.latest-status .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-faint);
}

.latest-status.running .dot {
  background: var(--accent);
  animation: pulse 1.4s ease-in-out infinite;
}

.latest-status.completed .dot {
  background: var(--success-text);
}

.latest-status.failed .dot {
  background: var(--danger-text);
}

.latest-duration {
  color: var(--text-faint);
}

.failed-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 7px;
  border-radius: var(--radius-pill);
  background: var(--danger-soft);
  color: var(--danger-text);
  font-size: 11px;
  font-weight: 600;
}

.failed-badge .dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

@keyframes pulse {
  50% {
    opacity: .35;
    transform: scale(.8);
  }
}

.tool-calls-summary .chevron {
  margin-left: auto;
  color: var(--text-faint);
  transition: transform var(--transition-fast);
}

.tool-calls[open] > .tool-calls-summary .chevron {
  transform: rotate(90deg);
}

.tool-list {
  display: grid;
  gap: 8px;
  padding: 0 12px 12px;
}

.tool-call {
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--bg-subtle);
  overflow: hidden;
}

.tool-call.failed {
  border-color: var(--danger-text);
}

.tool-call > summary {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 12px;
  cursor: pointer;
  list-style: none;
  font-size: 12.5px;
}

.tool-call > summary::-webkit-details-marker {
  display: none;
}

.tool-call .chevron {
  color: var(--text-faint);
  transition: transform var(--transition-fast);
}

.tool-call[open] .chevron {
  transform: rotate(90deg);
}

.tool-call .icon {
  color: var(--text-muted);
}

.tool-call > summary strong {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 580;
  color: var(--text-primary);
}

.status {
  padding: 2px 7px;
  border-radius: var(--radius-pill);
  font-size: 10.5px;
  background: var(--bg-surface);
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

.tool-call > summary small {
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
  .tool-calls-summary,
  .tool-call > summary {
    min-height: 44px;
    padding: 10px;
  }

  .tool-list {
    padding: 0 10px 10px;
  }

  .detail {
    padding: 0 10px 10px;
  }
}
</style>
