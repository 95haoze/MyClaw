/** API 层统一出口：外部只从这里 import，不直接引用子模块。 */

export { ChatApiError, isObject, readApiError, requestJson, requestVoid } from './http'
export { streamChat, cancelChat, type StreamChatOptions } from './chat'
export { consumeSse, type SseEvent } from './sse'
export {
  listSessions,
  getSession,
  createSession,
  renameSession,
  clearSession,
  truncateSessionMessages,
  deleteSession,
} from './sessions'
export type {
  ApiErrorBody,
  AttachmentView,
  ChatResponse,
  ExecutionActivity,
  Message,
  MessageRole,
  SessionDetail,
  SessionSummary,
  StoredMessage,
  ToolExecution,
  ToolStatus,
} from './types'

export * from './auth'

export * from './attachments'

export * from './feedback'

export * from './workspaces'
