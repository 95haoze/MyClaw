<script setup lang="ts">
/**
 * 应用外壳：只负责把各组件拼成布局，并把编排层（useWorkspace）的状态接上去。
 * 任何业务逻辑都不应该写在这里。
 */
import ChatComposer from './components/chat/ChatComposer.vue'
import AppDialog from './components/AppDialog.vue'
import MessageList from './components/chat/MessageList.vue'
import WelcomePanel from './components/chat/WelcomePanel.vue'
import AppSidebar from './components/layout/AppSidebar.vue'
import SettingsModal from './components/settings/SettingsModal.vue'
import {useWorkspace} from './composables'
import {ref} from 'vue'
import {selectWorkspaceDirectory, type CurrentUser} from './api'
import {PanelLeftOpen, PawPrint, Plus} from 'lucide-vue-next'
import Button from './components/ui/button/Button.vue'

defineProps<{ currentUser: CurrentUser }>()
const emit = defineEmits<{ logout: [] }>()

const {
  // 设置
  endpoint,
  workingDirectory,
  workspaces,
  removeWorkspace,
  permissionMode,
  modalOpen,
  // 主题
  isDark,
  toggleTheme,
  // 会话
  sessions,
  groupedSessions,
  selectedId,
  query,
  sessionsLoading,
  current,
  sessionDialog,
  confirmSessionDialog,
  newChat,
  selectSession,
  removeSession,
  renameSession,
  clearCurrent,
  // 对话
  draft,
  busy,
  activity,
  attachments,
  addFiles,
  removeFile,
  send,
  stop,
  // 界面状态
  error,
  notice,
  storageWarning,
  sidebarOpen,
  sidebarCollapsed,
  scroller,
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
</script>

<template>
  <div class="workspace" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
    <a class="skip-link" href="#main-content">跳转到对话内容</a>
    <button
        v-if="sidebarOpen"
        class="scrim"
        type="button"
        aria-label="关闭导航"
        @click="sidebarOpen = false"
    />

    <AppSidebar
        v-model:query="query"
        :open="sidebarOpen"
        :collapsed="sidebarCollapsed"
        :groups="groupedSessions"
        :total="sessions.length"
        :selected-id="selectedId"
        :busy="busy"
        :loading="sessionsLoading"
        :current-user="currentUser"
        :is-dark="isDark"
        :has-messages="Boolean(current?.messages.length)"
        :workspaces="workspaces"
        :working-directory="workingDirectory"
        @new-chat="newChat"
        @select="selectSession"
        @rename="renameSession"
        @remove="removeSession"
        @open-settings="modalOpen = true"
        @toggle-collapse="sidebarCollapsed = !sidebarCollapsed"
        @close="sidebarOpen = false"
        @clear="clearCurrent"
        @toggle-theme="toggleTheme"
        @logout="emit('logout')"
        @add-workspace="addWorkspace"
        @select-workspace="workingDirectory = $event"
        @remove-workspace="removeWorkspace"/>

    <main id="main-content" class="main" tabindex="-1">
      <header class="workspace-header">
        <span class="header-brand" aria-hidden="true"><PawPrint :size="18"/></span>
        <div class="header-controls" aria-label="页面快捷操作">
          <Button variant="ghost" size="icon" aria-label="打开侧边栏" @click="openSidebar">
            <PanelLeftOpen :size="18"/>
          </Button>
          <Button variant="ghost" size="icon" aria-label="新建对话" :disabled="busy" @click="newChat">
            <Plus :size="18"/>
          </Button>
        </div>
        <span class="header-title">{{ current?.title || '新对话' }}</span>
      </header>
      <div ref="scroller" class="conversation">
        <WelcomePanel v-if="!current?.messages.length" @pick="draft = $event"/>
        <MessageList v-else :messages="current.messages" :busy="busy" :activity="activity" @resend="send"/>
      </div>

      <ChatComposer
          v-model="draft"
          v-model:working-directory="workingDirectory"
          v-model:permission-mode="permissionMode"
          :busy="busy"
          :current-user="currentUser"
          :attachments="attachments"
          :error="error || storageWarning"
          :notice="notice"
          :context-count="current?.messages.length || 0"
          @add-files="addFiles"
          @remove-file="removeFile"
          @send="send()"
          @stop="stop()"
          @command="handleComposerCommand"
      />
    </main>

    <AppDialog
        v-model:open="sessionDialog.open"
        :title="sessionDialog.title"
        :description="sessionDialog.description"
        :confirm-text="sessionDialog.confirmText"
        :default-value="sessionDialog.defaultValue"
        :input-label="sessionDialog.inputLabel"
        :danger="sessionDialog.danger"
        @confirm="confirmSessionDialog"
    />

    <SettingsModal
        v-if="modalOpen"
        v-model:open="modalOpen"
        v-model:endpoint="endpoint"
        :busy="busy"
    />
  </div>
</template>

<style scoped>
.workspace {
  display: flex;
  height: 100dvh;
  min-height: 520px;
  background: var(--bg-app);
}

.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.workspace-header {
  flex: 0 0 64px;
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 0 28px;
  background: var(--bg-app);
}

.header-brand {
  display: none;
  place-items: center;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  border-radius: 10px;
  background: var(--text-primary);
  color: var(--bg-surface);
}

.header-controls {
  display: none;
  align-items: center;
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--border);
  border-radius: 13px;
  background: color-mix(in srgb, var(--bg-surface) 88%, transparent);
}

.header-title {
  min-width: 0;
  max-width: 420px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13.5px;
  font-weight: 560;
  color: var(--text-primary);
}

.sidebar-collapsed .header-brand, .sidebar-collapsed .header-controls {
  display: flex;
}

.conversation {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.scrim {
  display: none;
}

@media (max-width: 820px) {
  .workspace {
    min-height: 0;
  }

  .workspace-header {
    flex-basis: 58px;
    gap: 8px;
    padding: 0 10px;
  }

  .header-brand, .header-controls {
    display: flex;
  }

  .header-title {
    max-width: calc(100vw - 170px);
  }

  .scrim {
    display: block;
    position: fixed;
    inset: 0;
    z-index: var(--z-scrim);
    background: rgba(24, 24, 27, .4);
  }
}
</style>
