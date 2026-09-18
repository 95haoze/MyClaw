export type MessageRole = 'user' | 'assistant'
export type ToolStatus = 'running' | 'completed' | 'failed' | 'unknown'
export interface ExecutionActivity { label: string; iteration: number; tools: ToolExecution[] }
export interface ToolExecution { id: string; name: string; arguments: string; status: ToolStatus; durationMillis: number; result: string }
export interface AttachmentView { id: string; name: string; contentType: string; sizeBytes: number; previewUrl: string; extractedText?: string }
export interface Message {
  id?: number
  role: MessageRole
  content: string
  tools?: ToolExecution[]
  durationMillis?: number
  attachments?: AttachmentView[]
  feedback?: 'up' | 'down'
}
export interface ChatResponse {
  content: string
  iterations: number
  toolCalls: number
  totalTokens: number
  durationMillis: number
  tools: ToolExecution[]
  messageId: number | null
}
export interface ApiErrorBody { code: string; message: string; retryable: boolean }
export interface SessionSummary { id: string; title: string; createdAt: string; updatedAt: string; messageCount: number }
export interface StoredMessage extends Message {
  id: number
  status: string
  createdAt: string
  iterations?: number
  toolCalls?: number
  totalTokens?: number
}
export interface SessionDetail extends Omit<SessionSummary, 'messageCount'> { messages: StoredMessage[] }
