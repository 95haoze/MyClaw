<script setup lang="ts">
import { ArrowUp, Paperclip, PawPrint, Square, X } from 'lucide-vue-next'
import { computed, onBeforeUnmount, watch } from 'vue'
import Button from '../ui/button/Button.vue'
const props = defineProps<{ busy: boolean; error: string; notice: string; contextCount: number; attachments: File[] }>()
const emit = defineEmits<{ send: []; stop: []; addFiles: [files: FileList]; removeFile: [index: number] }>()
const draft = defineModel<string>({ required: true })
const MAX_LENGTH = 20_000
const canSend = computed(() => !props.busy && draft.value.trim().length > 0)
const previewUrls = new Map<File, string>()
watch(() => props.attachments, files => {
  for (const [file, url] of previewUrls) {
    if (!files.includes(file)) { URL.revokeObjectURL(url); previewUrls.delete(file) }
  }
  for (const file of files) {
    if (file.type.startsWith('image/') && !previewUrls.has(file)) previewUrls.set(file, URL.createObjectURL(file))
  }
}, { immediate: true })
onBeforeUnmount(() => previewUrls.forEach(url => URL.revokeObjectURL(url)))
function previewUrl(file: File): string { return previewUrls.get(file) ?? '' }
function fileSize(bytes: number): string { return bytes < 1024 * 1024 ? `${Math.ceil(bytes / 1024)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB` }
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault(); if (canSend.value) emit('send')
  }
}
function pick(event: Event) {
  const input = event.target as HTMLInputElement
  if (input.files) emit('addFiles', input.files)
  input.value = ''
}
</script>
<template>
  <div class="composer-area">
    <div class="composer-inner">
      <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
      <p v-else-if="notice" class="feedback notice" role="status">{{ notice }}</p>
      <form class="composer" @submit.prevent="canSend && emit('send')">
        <div v-if="attachments.length" class="attachments">
          <span v-for="(file,index) in attachments" :key="file.name+file.size+file.lastModified" class="attachment-item"><img v-if="file.type.startsWith('image/')" :src="previewUrl(file)" :alt="file.name" /><Paperclip v-else :size="14" /><span class="attachment-name">{{ file.name }}</span><small>{{ fileSize(file.size) }}</small><button type="button" :aria-label="`移除 ${file.name}`" @click="emit('removeFile',index)"><X :size="12" /></button></span>
        </div>
        <textarea v-model="draft" :disabled="busy" :maxlength="MAX_LENGTH" rows="1" aria-label="消息内容" placeholder="描述你的想法，剩下的交给 MyClaw…" @keydown="onKeydown" />
        <div class="composer-foot">
          <label class="attach" title="添加附件"><Paperclip :size="15" /><input type="file" multiple accept=".txt,.md,.json,.csv,.pdf,image/png,image/jpeg,image/webp,image/gif" @change="pick"></label>
          <span class="tool"><PawPrint :size="13" />MyClaw Agent</span>
          <span v-if="contextCount" class="tool muted">{{ contextCount }} 条上下文消息</span>
          <Button v-if="busy" variant="outline" size="icon" aria-label="停止生成" @click="emit('stop')"><Square :size="12" fill="currentColor" /></Button>
          <Button v-else type="submit" size="icon" :disabled="!canSend" aria-label="发送消息"><ArrowUp :size="15" /></Button>
        </div>
      </form>
    </div>
  </div>
</template>
<style scoped>
.composer-area{flex:0 0 auto;padding:14px 22px 20px}.composer-inner{max-width:var(--content-width);margin:0 auto}.feedback{margin-bottom:8px;padding:8px 12px;border-radius:10px;font-size:12.5px}.feedback.error{background:var(--danger-soft);color:var(--danger-text)}.feedback.notice{color:var(--text-muted)}.composer{padding:14px 14px 10px;border:1px solid var(--border);border-radius:16px;background:var(--bg-surface);transition:border-color .15s,box-shadow .15s}.composer:focus-within{border-color:var(--border-strong);box-shadow:0 0 0 3px var(--bg-subtle)}textarea{display:block;width:100%;min-height:70px;max-height:200px;padding:4px 4px 10px;border:0;outline:0;background:none;resize:none;font-size:14px;line-height:1.6}textarea::placeholder{color:var(--text-faint)}.composer-foot{display:flex;align-items:center;gap:6px}.composer-foot>button:last-child{margin-left:auto}.attach{display:grid;place-items:center;width:32px;height:32px;border-radius:8px;color:var(--text-muted);cursor:pointer}.attach:hover{background:var(--bg-subtle)}.attach input{display:none}.tool{display:flex;align-items:center;gap:6px;padding:0 6px;font-size:12px;color:var(--text-muted)}.tool.muted{color:var(--text-faint)}.attachments{display:flex;flex-wrap:wrap;gap:7px;margin-bottom:10px}.attachments>span{display:flex;align-items:center;gap:5px;max-width:260px;padding:6px 8px;border:1px solid var(--border);border-radius:8px;background:var(--bg-subtle);font-size:12px}.attachments>span>span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.attachments small{color:var(--text-faint);white-space:nowrap}.attachments button{display:grid;place-items:center;color:var(--text-muted)}@media(max-width:820px){.composer-area{padding:10px 14px 14px}.tool.muted{display:none}.attach,.composer-foot>button{width:44px;height:44px}.composer{padding:10px}.attachments>span{max-width:100%}}
.attachment-item img{width:34px;height:34px;flex:0 0 auto;border:1px solid var(--border);border-radius:6px;object-fit:cover;background:var(--bg-surface)}.attachment-name{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.attachment-item button{flex:0 0 auto;padding:3px;border-radius:5px}.attachment-item button:hover{background:var(--bg-hover);color:var(--text-primary)}</style>
