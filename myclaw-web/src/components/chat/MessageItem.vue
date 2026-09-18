<script setup lang="ts">
import {Copy, FileText, PawPrint, Pencil, RefreshCw, ThumbsDown, ThumbsUp} from 'lucide-vue-next'
import {computed, ref, watch} from 'vue'
import type {Message} from '../../api'
import {clearMessageFeedback, saveFeedback} from '../../api'
import {formatDuration} from '../../utils/format'
import {renderMarkdown} from '../../utils/markdown'
import ToolCallPanel from './ToolCallPanel.vue'
import AppDialog from '../AppDialog.vue'

const props = defineProps<{ message: Message }>()
const emit = defineEmits<{ edit: [text: string]; regenerate: [] }>()
const feedback = ref<'up' | 'down' | ''>(props.message.feedback ?? '')
const savingFeedback = ref(false)
const editOpen = ref(false)
const assistant = computed(() => props.message.role === 'assistant')
const duration = computed(() => props.message.durationMillis ? formatDuration(props.message.durationMillis) : '')
const rendered = computed(() => renderMarkdown(props.message.content))
watch(() => props.message.feedback, value => {
  feedback.value = value ?? ''
})

async function onMarkdownClick(event: MouseEvent) {
  const button = (event.target as HTMLElement).closest<HTMLButtonElement>('[data-copy-code]')
  if (!button) return
  const code = button.closest('.code-block')?.querySelector('code')?.textContent ?? ''
  await navigator.clipboard.writeText(code)
  button.textContent = '已复制'
  button.classList.add('copied')
  window.setTimeout(() => { button.textContent = '复制'; button.classList.remove('copied') }, 1600)
}

async function copy() {
  await navigator.clipboard.writeText(props.message.content)
}

function edit() { editOpen.value = true }
function confirmEdit(text: string) { editOpen.value = false; if (text) emit('edit', text) }

async function toggleFeedback(value: 'up' | 'down') {
  if (!props.message.id || savingFeedback.value) return
  const previous = feedback.value
  const next = previous === value ? '' : value
  feedback.value = next
  savingFeedback.value = true
  try {
    if (next) await saveFeedback(props.message.id, next)
    else await clearMessageFeedback(props.message.id)
    props.message.feedback = next || undefined
  } catch {
    feedback.value = previous
  } finally {
    savingFeedback.value = false
  }
}
</script>
<template>
  <article class="message" :class="message.role">
    <template v-if="!assistant">
      <div class="user-content">
        <div class="bubble">{{ message.content }}</div>
        <div v-if="message.attachments?.length" class="files">
          <a v-for="a in message.attachments" :key="a.id" :href="a.previewUrl" target="_blank" rel="noopener">
            <img v-if="a.contentType.startsWith('image/')" :src="a.previewUrl" :alt="a.name">
            <span v-else class="file-chip"><FileText :size="14"/>{{ a.name }}</span>
          </a>
        </div>
        <div class="actions">
          <button type="button" title="复制" aria-label="复制消息" @click="copy">
            <Copy :size="13"/>
          </button>
          <button type="button" title="编辑后重发" aria-label="编辑消息后重新发送" @click="edit">
            <Pencil :size="13"/>
          </button>
        </div>
      </div>
    </template>
    <template v-else>
      <span class="mark"><PawPrint :size="14"/></span>
      <div class="body">
        <div class="name">MyClaw<span v-if="duration" class="duration">{{ duration }}</span></div>
        <div class="markdown-body" v-html="rendered" @click="onMarkdownClick"/>
        <ToolCallPanel v-if="message.tools?.length" :tools="message.tools"/>
        <div class="actions">
          <button type="button" title="复制" aria-label="复制消息" @click="copy">
            <Copy :size="13"/>
          </button>
          <button type="button" title="重新生成" aria-label="重新生成回答" @click="emit('regenerate')">
            <RefreshCw :size="13"/>
          </button>
          <button type="button" aria-label="标记回答有帮助" :aria-pressed="feedback === 'up'" :class="{ on: feedback === 'up' }" :disabled="!message.id || savingFeedback" title="有帮助"
                  @click="toggleFeedback('up')">
            <ThumbsUp :size="13"/>
          </button>
          <button type="button" aria-label="标记回答没有帮助" :aria-pressed="feedback === 'down'" :class="{ on: feedback === 'down' }" :disabled="!message.id || savingFeedback" title="没有帮助"
                  @click="toggleFeedback('down')">
            <ThumbsDown :size="13"/>
          </button>
        </div>
      </div>
    </template>
  </article>
  <AppDialog v-model:open="editOpen" title="编辑后重发" description="修改这条消息并作为新的请求发送。" input-label="消息内容" confirm-text="发送" :default-value="message.content" @confirm="confirmEdit" />
</template>
<style scoped>
.message.user {
  display: flex;
  justify-content: flex-end
}

.user-content {
  max-width: min(80%, 640px)
}

.bubble {
  padding: 10px 14px;
  border-radius: var(--radius-lg);
  background: var(--bg-subtle);
  font-size: 14.5px;
  line-height: 1.6;
  white-space: pre-wrap
}

.message.assistant {
  display: flex;
  gap: 12px
}

.mark {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  flex: none;
  border-radius: var(--radius-sm);
  background: var(--text-primary);
  color: var(--bg-surface)
}

.body {
  flex: 1;
  min-width: 0
}

.name {
  display: flex;
  margin-bottom: 10px;
  font-size: 13.5px;
  font-weight: 600
}

.duration {
  margin-left: auto;
  font-size: 11px;
  font-weight: 400;
  color: var(--text-faint)
}

.actions {
  display: flex;
  gap: 3px;
  margin-top: 7px;
  opacity: 0
}

.message:hover .actions {
  opacity: 1
}

.actions button {
  display: grid;
  place-items: center;
  width: 27px;
  height: 27px;
  border-radius: 7px;
  color: var(--text-faint)
}

.actions button:hover, .actions button.on {
  background: var(--bg-subtle);
  color: var(--text-primary)
}

.actions button:disabled {
  cursor: not-allowed;
  opacity: .35
}

.files {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  justify-content: flex-end;
  margin-top: 7px
}

.files a {
  display: block;
  color: var(--text-secondary);
  text-decoration: none
}

.files img {
  display: block;
  width: 160px;
  height: 112px;
  object-fit: cover;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--bg-subtle)
}

.file-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  max-width: 240px;
  padding: 7px 9px;
  border: 1px solid var(--border);
  border-radius: 9px;
  background: var(--bg-surface);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap
}

@media (hover: none), (max-width: 820px) {
  .actions { opacity: 1; }
  .actions button { width: 40px; height: 40px; }
  .user-content { max-width: 92%; }
  .files img { width: 132px; height: 96px; }
}</style>
