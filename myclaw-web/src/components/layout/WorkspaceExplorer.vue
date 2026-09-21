<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {ChevronLeft, File, FileCode2, Folder, RefreshCw, X} from 'lucide-vue-next'
import {browseWorkspaces, type WorkspaceDirectoryView} from '../../api/workspaces'

const props = defineProps<{ workingDirectory: string }>()
const emit = defineEmits<{ close: []; navigate: [path: string] }>()
const view = ref<WorkspaceDirectoryView | null>(null)
const loading = ref(false)
const error = ref('')
const path = computed(() => view.value?.current === '__roots__' ? '计算机' : (view.value?.current || props.workingDirectory))

function isCode(name: string): boolean {
  return /\.(java|kt|ts|tsx|js|jsx|vue|json|ya?ml|xml|md|py|go|rs|cs|css|html|sql)$/i.test(name)
}
function sizeLabel(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
async function load(target = props.workingDirectory): Promise<void> {
  loading.value = true
  error.value = ''
  try { view.value = await browseWorkspaces(target) }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '无法读取工作区目录' }
  finally { loading.value = false }
}
function openDirectory(target: string): void {
  void load(target)
}
watch(() => props.workingDirectory, value => void load(value), {immediate: true})
</script>

<template>
  <aside class="explorer" aria-label="工作区文件">
    <header class="explorer-header">
      <div class="explorer-title"><Folder :size="16"/><strong>文件</strong></div>
      <div class="explorer-actions">
        <button type="button" title="刷新" @click="load(view?.current || workingDirectory)"><RefreshCw :size="15"/></button>
        <button type="button" title="关闭文件面板" @click="emit('close')"><X :size="16"/></button>
      </div>
    </header>
    <div class="path" :title="path">{{ path }}</div>
    <div class="entries" :aria-busy="loading">
      <p v-if="error" class="state error">{{ error }}</p>
      <p v-else-if="loading" class="state">正在读取目录…</p>
      <template v-else-if="view">
        <button v-if="view.parent" class="entry" type="button" @click="openDirectory(view.parent)">
          <ChevronLeft :size="16"/><span>返回上级</span>
        </button>
        <button v-for="directory in view.directories" :key="directory.path" class="entry" type="button" @click="openDirectory(directory.path)">
          <Folder :size="16" class="folder"/><span>{{ directory.name }}</span>
        </button>
        <div v-for="file in view.files" :key="file.path" class="entry file-entry" :title="file.path">
          <FileCode2 v-if="isCode(file.name)" :size="16" class="code"/><File v-else :size="16"/>
          <span>{{ file.name }}</span><small>{{ sizeLabel(file.sizeBytes) }}</small>
        </div>
        <p v-if="!view.directories.length && !view.files.length" class="state">此目录为空</p>
      </template>
    </div>
  </aside>
</template>

<style scoped>
.explorer{width:360px;min-width:280px;display:flex;flex-direction:column;border-left:1px solid var(--border);background:var(--bg-app)}
.explorer-header{height:52px;display:flex;align-items:center;justify-content:space-between;padding:0 14px;border-bottom:1px solid var(--border)}
.explorer-title,.explorer-actions{display:flex;align-items:center;gap:8px}.explorer-title strong{font-size:13px}
.explorer-actions button{display:grid;place-items:center;width:30px;height:30px;border:0;border-radius:8px;background:transparent;color:var(--text-muted);cursor:pointer}.explorer-actions button:hover{background:var(--bg-subtle);color:var(--text-primary)}
.path{padding:10px 14px;border-bottom:1px solid var(--border);font:11px/1.4 var(--font-mono);color:var(--text-muted);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.entries{flex:1;overflow:auto;padding:8px}.entry{width:100%;height:34px;display:flex;align-items:center;gap:9px;padding:0 8px;border:0;border-radius:7px;background:transparent;color:var(--text-secondary);font-size:12.5px;text-align:left}.entry:not(.file-entry){cursor:pointer}.entry:not(.file-entry):hover{background:var(--bg-subtle);color:var(--text-primary)}.entry span{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.entry small{margin-left:auto;color:var(--text-faint);font-size:10px}.folder{color:#d49128}.code{color:var(--accent)}.state{padding:24px 12px;text-align:center;color:var(--text-faint);font-size:12px}.state.error{color:var(--danger-text)}
@media(max-width:1100px){.explorer{position:fixed;z-index:30;right:0;top:0;bottom:0;width:min(380px,90vw);box-shadow:-14px 0 36px rgba(0,0,0,.12)}}
</style>
