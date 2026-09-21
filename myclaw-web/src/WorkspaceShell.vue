<script setup lang="ts">
import {ref} from 'vue'
import {ChevronDown, Download, Ellipsis, FolderOpen, PanelLeftOpen, PawPrint, Plus} from 'lucide-vue-next'
import AppDialog from './components/AppDialog.vue'
import ChatComposer from './components/chat/ChatComposer.vue'
import MessageList from './components/chat/MessageList.vue'
import TraceView from './components/chat/TraceView.vue'
import WelcomePanel from './components/chat/WelcomePanel.vue'
import AppSidebar from './components/layout/AppSidebar.vue'
import WorkspaceExplorer from './components/layout/WorkspaceExplorer.vue'
import SettingsModal from './components/settings/SettingsModal.vue'
import Button from './components/ui/button/Button.vue'
import {openWorkspaceInApplication, selectWorkspaceDirectory, type CurrentUser, type WorkspaceApplication} from './api'
import {useWorkspace} from './composables'

defineProps<{ currentUser: CurrentUser }>()
const emit = defineEmits<{ logout: [] }>()
const activeTab = ref<'chat' | 'trace'>('chat')
const explorerOpen = ref(false)
const openMenu = ref(false)
const moreMenu = ref(false)

const {
  endpoint, workingDirectory, workspaces, removeWorkspace, permissionMode, modalOpen,
  isDark, toggleTheme, sessions, groupedSessions, selectedId, query, sessionsLoading,
  current, sessionDialog, confirmSessionDialog, newChat, selectSession, removeSession,
  renameSession, clearCurrent, draft, busy, activity, attachments, addFiles, removeFile,
  send, stop, error, notice, storageWarning, sidebarOpen, sidebarCollapsed, scroller,
} = useWorkspace()

async function addWorkspace(): Promise<void> {
  try {
    const selected = await selectWorkspaceDirectory()
    if (selected) workingDirectory.value = selected
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '无法打开系统目录选择器'
  }
}
function handleComposerCommand(name: string): void {
  if (name === 'new') void newChat()
  else if (name === 'clear') void clearCurrent()
}
function openSidebar(): void {
  if (window.matchMedia('(max-width: 820px)').matches) sidebarOpen.value = true
  else sidebarCollapsed.value = false
}
async function openWith(application: WorkspaceApplication): Promise<void> {
  openMenu.value = false
  try { await openWorkspaceInApplication(workingDirectory.value, application) }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '无法打开工作区' }
}
function downloadSessionLog(): void {
  moreMenu.value = false
  if (!current.value) {
    error.value = '当前没有可下载的会话'
    return
  }
  const payload = {
    exportedAt: new Date().toISOString(),
    session: {id: current.value.id, title: current.value.title},
    workingDirectory: workingDirectory.value,
    messages: current.value.messages,
    activity: activity.value,
  }
  const blob = new Blob([JSON.stringify(payload, null, 2)], {type: 'application/json;charset=utf-8'})
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `${current.value.title.replace(/[\\/:*?"<>|]/g, '_') || 'session'}-${new Date().toISOString().slice(0, 10)}.json`
  link.click()
  URL.revokeObjectURL(link.href)
}
</script>

<template>
  <div class="workspace" :class="{'sidebar-collapsed': sidebarCollapsed}">
    <a class="skip-link" href="#main-content">跳转到对话内容</a>
    <button v-if="sidebarOpen" class="scrim" type="button" aria-label="关闭导航" @click="sidebarOpen=false"/>
    <AppSidebar
      v-model:query="query" :open="sidebarOpen" :collapsed="sidebarCollapsed" :groups="groupedSessions"
      :total="sessions.length" :selected-id="selectedId" :busy="busy" :loading="sessionsLoading"
      :current-user="currentUser" :is-dark="isDark" :has-messages="Boolean(current?.messages.length)"
      :workspaces="workspaces" :working-directory="workingDirectory" @new-chat="newChat" @select="selectSession"
      @rename="renameSession" @remove="removeSession" @open-settings="modalOpen=true"
      @toggle-collapse="sidebarCollapsed=!sidebarCollapsed" @close="sidebarOpen=false" @clear="clearCurrent"
      @toggle-theme="toggleTheme" @logout="emit('logout')" @add-workspace="addWorkspace"
      @select-workspace="workingDirectory=$event" @remove-workspace="removeWorkspace"/>

    <div class="workbench">
      <main id="main-content" class="main" tabindex="-1">
        <header class="workspace-header">
          <span class="header-brand" aria-hidden="true"><PawPrint :size="18"/></span>
          <div class="header-controls">
            <Button variant="ghost" size="icon" aria-label="打开侧边栏" @click="openSidebar"><PanelLeftOpen :size="18"/></Button>
            <Button variant="ghost" size="icon" aria-label="新建对话" :disabled="busy" @click="newChat"><Plus :size="18"/></Button>
          </div>
          <span class="header-title">{{ current?.title || '新对话' }}</span>
          <div class="header-actions">
            <div class="split-action">
              <Button variant="ghost" size="icon" :aria-pressed="explorerOpen" title="工作区文件" @click="explorerOpen=!explorerOpen"><FolderOpen :size="17"/></Button>
              <button class="split-toggle" type="button" aria-label="选择打开方式" @click="openMenu=!openMenu;moreMenu=false"><ChevronDown :size="13"/></button>
              <div v-if="openMenu" class="header-menu open-with-menu">
                <button type="button" @click="openWith('explorer')"><span>📁</span>文件资源管理器</button>
                <button type="button" @click="openWith('cursor')"><span>◼</span>Cursor</button>
                <button type="button" @click="openWith('idea')"><span>IJ</span>IntelliJ IDEA</button>
                <button type="button" @click="openWith('pycharm')"><span>PC</span>PyCharm</button>
              </div>
            </div>
            <div class="menu-anchor">
              <Button variant="ghost" size="icon" aria-label="更多操作" @click="moreMenu=!moreMenu;openMenu=false"><Ellipsis :size="18"/></Button>
              <div v-if="moreMenu" class="header-menu more-menu"><button type="button" @click="downloadSessionLog"><Download :size="16"/>下载 Session 日志</button></div>
            </div>
          </div>
        </header>
        <nav class="view-tabs" aria-label="内容视图">
          <button type="button" :class="{active:activeTab==='chat'}" @click="activeTab='chat'">对话</button>
          <button type="button" :class="{active:activeTab==='trace'}" @click="activeTab='trace'">轨迹</button>
        </nav>
        <div ref="scroller" class="conversation">
          <template v-if="activeTab==='chat'">
            <WelcomePanel v-if="!current?.messages.length" @pick="draft=$event"/>
            <MessageList v-else :messages="current.messages" :busy="busy" :activity="activity" @resend="send"/>
          </template>
          <TraceView v-else :messages="current?.messages || []" :busy="busy" :activity="activity"/>
        </div>
        <ChatComposer v-model="draft" v-model:working-directory="workingDirectory" v-model:permission-mode="permissionMode"
          :busy="busy" :current-user="currentUser" :attachments="attachments" :error="error || storageWarning"
          :notice="notice" :context-count="current?.messages.length || 0" @add-files="addFiles" @remove-file="removeFile"
          @send="send()" @stop="stop()" @command="handleComposerCommand"/>
      </main>
      <WorkspaceExplorer v-if="explorerOpen" :working-directory="workingDirectory" @close="explorerOpen=false"/>
    </div>

    <AppDialog v-model:open="sessionDialog.open" :title="sessionDialog.title" :description="sessionDialog.description"
      :confirm-text="sessionDialog.confirmText" :default-value="sessionDialog.defaultValue"
      :input-label="sessionDialog.inputLabel" :danger="sessionDialog.danger" @confirm="confirmSessionDialog"/>
    <SettingsModal v-if="modalOpen" v-model:open="modalOpen" v-model:endpoint="endpoint" :busy="busy"/>
  </div>
</template>

<style scoped>
.workspace{display:flex;height:100dvh;min-height:520px;background:var(--bg-app)}
.workbench{flex:1;min-width:0;display:flex}.main{flex:1;min-width:0;display:flex;flex-direction:column}
.workspace-header{flex:0 0 54px;display:flex;align-items:center;gap:12px;min-width:0;padding:0 20px;background:var(--bg-app)}
.header-brand{display:none;place-items:center;width:34px;height:34px;flex:0 0 auto;border-radius:10px;background:var(--text-primary);color:var(--bg-surface)}
.header-controls{display:none;align-items:center;gap:2px;padding:3px;border:1px solid var(--border);border-radius:13px;background:var(--bg-surface)}
.header-title{min-width:0;max-width:520px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:13.5px;font-weight:560;color:var(--text-primary)}
.header-actions{margin-left:auto;display:flex}.header-actions button{display:flex;gap:2px}.sidebar-collapsed .header-brand,.sidebar-collapsed .header-controls{display:flex}
.view-tabs{height:38px;display:flex;gap:24px;padding:0 22px;border-bottom:1px solid var(--border)}.view-tabs button{position:relative;border:0;background:transparent;color:var(--text-muted);font-size:13px;cursor:pointer}.view-tabs button.active{color:var(--text-primary);font-weight:600}.view-tabs button.active:after{content:'';position:absolute;left:0;right:0;bottom:-1px;height:2px;background:var(--accent);border-radius:2px}
.conversation{flex:1;min-height:0;overflow-y:auto;overscroll-behavior:contain}.scrim{display:none}
@media(max-width:820px){.workspace{min-height:0}.workspace-header{flex-basis:52px;padding:0 10px}.header-brand,.header-controls{display:flex}.header-title{max-width:calc(100vw - 210px)}.view-tabs{padding-left:14px}.scrim{display:block;position:fixed;inset:0;z-index:var(--z-scrim);background:rgba(24,24,27,.4)}}

.header-actions{position:relative;margin-left:auto;display:flex;align-items:center;gap:6px}.split-action,.menu-anchor{position:relative;display:flex}.split-action{border:1px solid var(--border);border-radius:11px;background:var(--bg-surface)}.split-action :deep(button){border-radius:10px 0 0 10px}.split-toggle{display:grid;place-items:center;width:27px;border:0;border-left:1px solid var(--border);border-radius:0 10px 10px 0;background:transparent;color:var(--text-muted);cursor:pointer}.split-toggle:hover{background:var(--bg-subtle);color:var(--text-primary)}.header-menu{position:absolute;z-index:40;top:calc(100% + 8px);right:0;min-width:250px;padding:5px;border:1px solid var(--border);border-radius:14px;background:var(--bg-surface);box-shadow:0 12px 36px rgba(0,0,0,.13)}.header-menu button{width:100%;height:39px;display:flex;align-items:center;gap:10px;padding:0 11px;border:0;border-radius:9px;background:transparent;color:var(--text-primary);font-size:13px;text-align:left;cursor:pointer}.header-menu button:hover{background:var(--bg-subtle)}.header-menu button span{width:21px;text-align:center;font:600 11px var(--font-mono)}.more-menu{min-width:230px}.open-with-menu{right:0}
</style>
