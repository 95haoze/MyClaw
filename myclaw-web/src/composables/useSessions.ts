import { computed, ref } from 'vue'
import { clearSession as clearRemoteSession, deleteSession as deleteRemoteSession, getSession, listSessions, renameSession as renameRemoteSession } from '../api'
import type { Session } from '../types'
import { describeChatError } from '../utils/errors'

export interface UseSessionsOptions {
  reportError: (message: string) => void
  clearError: () => void
  scrollToBottom: () => void | Promise<void>
}
type DialogAction = 'rename' | 'remove' | 'clear'
export function useSessions(options: UseSessionsOptions) {
  const sessions = ref<Session[]>([])
  const selectedId = ref('')
  const query = ref('')
  const loading = ref(false)
  const dialog = ref({ open: false, action: 'rename' as DialogAction, targetId: '', title: '', description: '', defaultValue: '', inputLabel: '', confirmText: '确认', danger: false })
  const current = computed(() => sessions.value.find(session => session.id === selectedId.value))
  const filteredSessions = computed(() => {
    const keyword = query.value.trim().toLowerCase()
    return keyword ? sessions.value.filter(session => session.title.toLowerCase().includes(keyword)) : sessions.value
  })
  const groupedSessions = computed<{ label: string; items: Session[] }[]>(() => {
    const list = filteredSessions.value
    if (!list.length) return []
    if (query.value.trim()) return [{ label: '搜索结果', items: list }]
    const todayStart = new Date(); todayStart.setHours(0, 0, 0, 0)
    const today: Session[] = []; const earlier: Session[] = []
    for (const session of list) {
      const time = session.updatedAt ? new Date(session.updatedAt).getTime() : Date.now()
      ;(Number.isNaN(time) || time >= todayStart.getTime() ? today : earlier).push(session)
    }
    return [{ label: '今天', items: today }, { label: '更早', items: earlier }].filter(group => group.items.length)
  })
  async function load(): Promise<boolean> {
    loading.value = true
    try {
      const remote = await listSessions()
      sessions.value = remote.map(session => ({ id: session.id, title: session.title, messages: [], updatedAt: session.updatedAt }))
      if (sessions.value.length) await select(sessions.value[0]!.id)
      return true
    } catch { return false }
    finally { loading.value = false }
  }
  async function select(id: string): Promise<void> {
    selectedId.value = id; options.clearError()
    try {
      const detail = await getSession(id)
      const target = sessions.value.find(session => session.id === id)
      if (target) target.messages = detail.messages.filter(message => message.status !== 'PENDING')
    } catch (cause) { options.reportError(describeChatError(cause)) }
  }
  function startNew() { selectedId.value = '' }
  function ensureSession(title: string): Session {
    if (current.value) return current.value
    const session: Session = { id: crypto.randomUUID(), title: title.slice(0, 26), messages: [] }
    sessions.value.unshift(session); selectedId.value = session.id
    // Return Vue's proxy so streaming mutations trigger a render for new sessions.
    return sessions.value[0]
  }
  function touch(session: Session) {
    const target = sessions.value.find(item => item.id === session.id)
    if (!target) return
    target.updatedAt = new Date().toISOString()
    const index = sessions.value.indexOf(target)
    if (index > 0) { sessions.value.splice(index, 1); sessions.value.unshift(target) }
  }
  function rename(session: Session) {
    dialog.value = { open: true, action: 'rename', targetId: session.id, title: '重命名会话', description: '输入一个更容易识别的会话标题。', defaultValue: session.title, inputLabel: '会话标题', confirmText: '保存', danger: false }
  }
  function remove(id: string) {
    dialog.value = { open: true, action: 'remove', targetId: id, title: '删除会话', description: '该会话及其全部消息将被永久删除，此操作无法撤销。', defaultValue: '', inputLabel: '', confirmText: '删除', danger: true }
  }
  function clearCurrent() {
    if (!current.value) return
    dialog.value = { open: true, action: 'clear', targetId: current.value.id, title: '清空会话', description: '当前会话中的全部消息将被删除，但会话本身会保留。', defaultValue: '', inputLabel: '', confirmText: '清空', danger: true }
  }
  async function confirmDialog(value: string) {
    const item = dialog.value
    dialog.value.open = false
    try {
      if (item.action === 'rename') {
        const target = sessions.value.find(session => session.id === item.targetId)
        if (!target || !value.trim() || value.trim() === target.title) return
        await renameRemoteSession(target.id, value.trim()); target.title = value.trim()
      } else if (item.action === 'remove') {
        await deleteRemoteSession(item.targetId)
        sessions.value = sessions.value.filter(session => session.id !== item.targetId)
        if (selectedId.value === item.targetId) startNew()
      } else {
        await clearRemoteSession(item.targetId)
        const target = sessions.value.find(session => session.id === item.targetId)
        if (target) target.messages = []
      }
    } catch (cause) { options.reportError(describeChatError(cause)) }
  }
  return { sessions, selectedId, query, loading, current, filteredSessions, groupedSessions, dialog, confirmDialog,
    load, select, startNew, ensureSession, touch, rename, remove, clearCurrent, scrollToBottom: options.scrollToBottom }
}
