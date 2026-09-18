import { apiFetch, ChatApiError, isObject, readApiError } from './http'
import { consumeSse } from './sse'
import type { ChatResponse, Message, ToolExecution } from './types'

/** 把未知的 SSE 负载收窄成 ChatResponse。 */
function isChatResponse(value: unknown): value is ChatResponse {
  return (
    isObject(value) &&
    typeof value.content === 'string' &&
    typeof value.iterations === 'number' &&
    typeof value.toolCalls === 'number' &&
    typeof value.totalTokens === 'number' &&
    typeof value.durationMillis === 'number' &&
    Array.isArray(value.tools)
  )
}

export interface StreamChatOptions {
  endpoint: string
  requestId: string
  sessionId: string
  messages: Message[]
  attachmentIds?: string[]
  signal: AbortSignal
  /** 每收到一段增量文本就回调一次，用于打字机效果。 */
  onDelta: (content: string) => void
  onStart?: (userMessageId: number) => void
  onStatus?: (label: string, iteration: number) => void
  onTool?: (tool: ToolExecution) => void
}

/**
 * 发起一次流式对话。返回 complete 事件里的最终结果。
 * 中途报错或提前断流都会抛出 ChatApiError，被 abort 则抛出 AbortError。
 */
export async function streamChat(options: StreamChatOptions): Promise<ChatResponse> {
  const { endpoint, requestId, sessionId, messages, attachmentIds, signal, onDelta, onStart, onStatus, onTool } = options

  const response = await apiFetch(`${endpoint.replace(/\/$/, '')}/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify({ requestId, sessionId, messages, attachmentIds }),
    signal,
  })

  if (!response.ok) throw await readApiError(response)

  let result: ChatResponse | undefined

  await consumeSse(response, signal, ({ event, data }) => {
    if (event === 'start' && isObject(data) && typeof data.userMessageId === 'number') {
      onStart?.(data.userMessageId)
      return
    }

    if (event === 'status' && isObject(data) && typeof data.label === 'string') {
      onStatus?.(data.label, typeof data.iteration === 'number' ? data.iteration : 1)
      return
    }

    if ((event === 'tool_start' || event === 'tool_complete') && isObject(data) &&
        typeof data.id === 'string' && typeof data.name === 'string') {
      onTool?.({
        id: data.id,
        name: data.name,
        arguments: typeof data.arguments === 'string' ? data.arguments : '{}',
        status: data.status === 'running' || data.status === 'completed' || data.status === 'failed' ? data.status : 'unknown',
        durationMillis: typeof data.durationMillis === 'number' ? data.durationMillis : 0,
        result: typeof data.result === 'string' ? data.result : '',
      })
      return
    }
    if (event === 'delta' && isObject(data) && typeof data.content === 'string') {
      onDelta(data.content)
      return
    }

    if (event === 'complete' && isChatResponse(data)) {
      result = data
      return
    }

    if (event === 'error') {
      const code = isObject(data) && typeof data.code === 'string' ? data.code : 'STREAM_ERROR'
      const message =
        isObject(data) && typeof data.message === 'string' ? data.message : 'Streaming request failed.'
      const retryable = isObject(data) && typeof data.retryable === 'boolean' ? data.retryable : true
      throw new ChatApiError(code, message, retryable)
    }
  })

  if (!result) {
    throw new ChatApiError('INCOMPLETE_STREAM', 'The response stream ended unexpectedly.', true)
  }
  return result
}

/** 通知服务端取消一个正在执行的请求。404 视为已经结束，不算失败。 */
export async function cancelChat(endpoint: string, requestId: string): Promise<void> {
  const apiBase = endpoint.replace(/\/chat\/?$/, '')
  const response = await apiFetch(`${apiBase}/requests/${encodeURIComponent(requestId)}`, {
    method: 'DELETE',
    keepalive: true,
  })
  if (!response.ok && response.status !== 404) throw await readApiError(response)
}
