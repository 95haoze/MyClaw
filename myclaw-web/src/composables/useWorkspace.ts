import { nextTick, onMounted, ref, watch } from 'vue'
import type { Session } from '../types'
import { useChat } from './useChat'
import { useSessions } from './useSessions'
import { useSettings } from './useSettings'
import { useTheme } from './useTheme'
import { readLocal, STORAGE_KEYS, writeLocal } from '../utils/storage'

/**
 * 工作台编排层：把设置、主题、会话、对话四个 composable 串起来，
 * 并持有跨模块共享的界面状态（错误提示、侧栏开关、滚动容器）。
 * App.vue 只消费这里返回的东西，不自己写业务逻辑。
 */
export function useWorkspace() {
  const settings = useSettings()
  const { theme, isDark, toggleTheme } = useTheme()

  const error = ref('')
  const notice = ref('')
  const storageWarning = ref('')
  const sidebarOpen = ref(false)
  const sidebarCollapsed = ref(readLocal(STORAGE_KEYS.sidebarCollapsed, false))
  watch(sidebarCollapsed, value => writeLocal(STORAGE_KEYS.sidebarCollapsed, value))
  const scroller = ref<HTMLElement>()

  function reportError(message: string): void {
    notice.value = ''
    error.value = message
  }

  function reportNotice(message: string): void {
    error.value = ''
    notice.value = message
  }

  function clearFeedback(): void {
    error.value = ''
    notice.value = ''
  }

  async function scrollToBottom(): Promise<void> {
    await nextTick()
    const element = scroller.value
    element?.scrollTo({ top: element.scrollHeight, behavior: 'smooth' })
  }

  const sessions = useSessions({
    reportError,
    clearError: clearFeedback,
    scrollToBottom,
  })

  const chat = useChat({
    endpoint: settings.endpoint,
    endpointValid: settings.endpointValid,
    session: sessions.current,
    ensureSession: sessions.ensureSession,
    touchSession: sessions.touch,
    reportError,
    reportNotice,
    clearFeedback,
    scrollToBottom,
  })

  async function newChat(): Promise<void> {
    if (chat.busy.value) return
    sessions.startNew()
    chat.draft.value = ''
    clearFeedback()
    sidebarOpen.value = false
  }

  async function selectSession(id: string): Promise<void> {
    if (chat.busy.value) return
    sidebarOpen.value = false
    await sessions.select(id)
    void scrollToBottom()
  }

  async function removeSession(id: string): Promise<void> {
    if (chat.busy.value) return
    clearFeedback()
    await sessions.remove(id)
  }

  /** 侧栏可以对任意会话重命名，不传则默认当前会话。 */
  async function renameSession(session?: Session): Promise<void> {
    if (chat.busy.value) return
    const target = session ?? sessions.current.value
    if (target) await sessions.rename(target)
  }

  async function clearCurrent(): Promise<void> {
    if (chat.busy.value) return
    clearFeedback()
    await sessions.clearCurrent()
  }

  onMounted(async () => {
    const ok = await sessions.load()
    if (!ok) {
      storageWarning.value = '无法加载服务端历史记录，请确认后端与数据库已启动。'
    }
  })

  return {
    // 设置
    settings,
    endpoint: settings.endpoint,
    modalOpen: settings.modalOpen,
    openSettings: settings.openSettings,
    // 主题
    theme,
    isDark,
    toggleTheme,
    // 会话
    sessions: sessions.sessions,
    filteredSessions: sessions.filteredSessions,
    groupedSessions: sessions.groupedSessions,
    selectedId: sessions.selectedId,
    query: sessions.query,
    sessionsLoading: sessions.loading,
    current: sessions.current,
    sessionDialog: sessions.dialog,
    confirmSessionDialog: sessions.confirmDialog,
    // 对话
    draft: chat.draft,
    busy: chat.busy,
    activity: chat.activity,
    attachments: chat.attachments,
    addFiles: chat.addFiles,
    removeFile: chat.removeFile,
    send: chat.send,
    stop: chat.stop,
    // 界面状态
    error,
    notice,
    storageWarning,
    sidebarOpen,
    sidebarCollapsed,
    scroller,
    newChat,
    selectSession,
    removeSession,
    renameSession,
    clearCurrent,
  }
}
