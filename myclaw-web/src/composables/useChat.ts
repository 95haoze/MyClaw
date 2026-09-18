import { ref, type ComputedRef, type Ref } from 'vue'
import { cancelChat, deleteAttachment, streamChat, truncateSessionMessages, uploadAttachment } from '../api'
import type { Session } from '../types'
import type { ExecutionActivity, ToolExecution } from '../api'
import { describeChatError } from '../utils/errors'

/** 单次请求的最长等待时间，超时后自动停止并保留已收到的内容。 */
const REQUEST_TIMEOUT = 600_000

export interface UseChatOptions {
  endpoint: Ref<string>
  endpointValid: ComputedRef<boolean>
  session: ComputedRef<Session | undefined>
  ensureSession: (title: string) => Session
  touchSession: (session: Session) => void
  reportError: (message: string) => void
  reportNotice: (message: string) => void
  clearFeedback: () => void
  scrollToBottom: () => void | Promise<void>
}


/** 输入框与发送流程：草稿、忙碌态、流式追加、停止与超时。 */
export function useChat(options: UseChatOptions) {
  const draft = ref('')
  const busy = ref(false)
  const activity = ref<ExecutionActivity | null>(null)
  const attachments = ref<File[]>([])

  let controller: AbortController | undefined
  let activeRequestId = ''
  let stopping = false
  let timedOut = false

  /** 停止生成。fromTimeout 用来区分用户主动点击和超时自动停止。 */
  function stop(fromTimeout = false): void {
    if (!busy.value || stopping) return

    stopping = true
    timedOut = fromTimeout
    const requestId = activeRequestId

    controller?.abort()
    if (requestId) {
      void cancelChat(options.endpoint.value, requestId)
        .catch(() => undefined)
        .finally(() => {
          stopping = false
        })
    } else {
      stopping = false
    }
  }

  async function send(text: string = draft.value, branchIndex?: number): Promise<void> {
    const content = text.trim()
    if (busy.value || !content) return

    if (!options.endpointValid.value) {
      options.reportError('接口路径必须以 /api/ 开头，例如 /api/chat。')
      return
    }

    const session = options.ensureSession(content)
    if (branchIndex !== undefined) {
      const branchMessage = session.messages[branchIndex]
      if (!branchMessage?.id) { options.reportError('消息尚未完成保存，请稍后再试'); return }
      try { await truncateSessionMessages(session.id, branchMessage.id) }
      catch (cause) { options.reportError(describeChatError(cause)); return }
      session.messages.splice(branchIndex)
      options.touchSession(session)
    }
    const uploaded: Awaited<ReturnType<typeof uploadAttachment>>[] = []
    try {
      for (const file of attachments.value) uploaded.push(await uploadAttachment(session.id, file))
    } catch (cause) {
      await Promise.allSettled(uploaded.map(item => deleteAttachment(item.id)))
      options.reportError(describeChatError(cause))
      return
    }
    session.messages.push({ role: 'user', content, attachments: uploaded })
    const userIndex = session.messages.length - 1
    options.touchSession(session)

    draft.value = ''
    attachments.value = []
    options.clearFeedback()
    busy.value = true
    activity.value = { label: '正在连接模型', iteration: 1, tools: [] }
    stopping = false
    timedOut = false
    void options.scrollToBottom()


    controller = new AbortController()
    const requestId = crypto.randomUUID()
    activeRequestId = requestId

    // 先插入一条空回答占位，流式增量直接往这条消息上累加。
    session.messages.push({ role: 'assistant', content: '' })
    const assistantIndex = session.messages.length - 1
    const requestMessages = session.messages.slice(0, -1)
    const timeout = window.setTimeout(() => stop(true), REQUEST_TIMEOUT)
    let requestStarted = false

    try {
      const result = await streamChat({
        endpoint: options.endpoint.value,
        requestId,
        sessionId: session.id,
        messages: requestMessages,
        attachmentIds: uploaded.map(item => item.id),
        signal: controller.signal,
        onStart: userMessageId => {
          requestStarted = true
          const user = session.messages[userIndex]
          if (user) session.messages[userIndex] = { ...user, id: userMessageId }
        },
        onStatus: (label, iteration) => {
          activity.value = { label, iteration, tools: activity.value?.tools ?? [] }
        },
        onTool: (tool: ToolExecution) => {
          const tools = [...(activity.value?.tools ?? [])]
          const index = tools.findIndex(item => item.id === tool.id)
          if (index >= 0) tools[index] = tool
          else tools.push(tool)
          const label = tool.status === 'running' ? `正在调用工具：${tool.name}` : `工具执行${tool.status === 'failed' ? '失败' : '完成'}：${tool.name}`
          activity.value = { label, iteration: activity.value?.iteration ?? 1, tools }
        },
        onDelta: delta => {
          const target = session.messages[assistantIndex]
          if (!target) return
          session.messages[assistantIndex] = { ...target, content: target.content + delta }
          activity.value = { ...(activity.value ?? { iteration: 1, tools: [] }), label: '正在生成回答' }
          void options.scrollToBottom()
        },
      })

      const target = session.messages[assistantIndex]
      if (target) {
        session.messages[assistantIndex] = {
          ...target,
          id: result.messageId ?? undefined,
          content: result.content,
          tools: result.tools,
          durationMillis: result.durationMillis,
        }
      }
    } catch (cause) {
      const target = session.messages[assistantIndex]
      const aborted = cause instanceof Error && cause.name === 'AbortError'

      if (aborted) {
        options.reportNotice(timedOut ? '模型响应超时，已停止生成并保留部分回答。' : '已停止生成，已保留部分回答。')
      } else {
        options.reportError(describeChatError(cause))
      }
      // 建连前失败说明服务端尚未确认接收：回滚乐观消息并恢复输入，避免重试后出现重复用户消息。
      if (!requestStarted) {
        session.messages.splice(userIndex, 2)
        draft.value = content
      } else if (target && !target.content) {
        // 服务端已经保存用户消息，但没有产生任何回答，只移除空的助手占位。
        session.messages.splice(assistantIndex, 1)
      }
    } finally {
      window.clearTimeout(timeout)
      busy.value = false
      activity.value = null
      stopping = false
      controller = undefined
      activeRequestId = ''
      void options.scrollToBottom()
    }
  }

  function addFiles(files: FileList | File[]) {
    for (const file of Array.from(files)) {
      if (file.size > 10 * 1024 * 1024) { options.reportError(`${file.name} 超过 10 MB`); continue }
      if (attachments.value.length >= 5) { options.reportError('每次最多添加 5 个附件'); break }
      const duplicate = attachments.value.some(item => item.name === file.name && item.size === file.size && item.lastModified === file.lastModified)
      if (!duplicate) attachments.value.push(file)
    }
  }
  function removeFile(index: number) { attachments.value.splice(index, 1) }
  return { draft, busy, activity, attachments, addFiles, removeFile, send, stop }
}
