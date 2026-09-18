import { requestJson, requestVoid } from './http'
import type { SessionDetail, SessionSummary } from './types'

const BASE = '/api/sessions'

const jsonHeaders = { 'Content-Type': 'application/json' } as const

/** 会话列表，按服务端返回的更新时间倒序。 */
export function listSessions(): Promise<SessionSummary[]> {
  return requestJson<SessionSummary[]>(BASE)
}

/** 会话详情，含全部消息。 */
export function getSession(id: string): Promise<SessionDetail> {
  return requestJson<SessionDetail>(`${BASE}/${encodeURIComponent(id)}`)
}

export function createSession(id: string, title: string): Promise<SessionSummary> {
  return requestJson<SessionSummary>(BASE, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({ id, title }),
  })
}

export function renameSession(id: string, title: string): Promise<void> {
  return requestVoid(`${BASE}/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    headers: jsonHeaders,
    body: JSON.stringify({ title }),
  })
}

/** 清空会话里的全部消息，但保留会话本身。 */
export function clearSession(id: string): Promise<void> {
  return requestVoid(`${BASE}/${encodeURIComponent(id)}/messages`, { method: 'DELETE' })
}

export function deleteSession(id: string): Promise<void> {
  return requestVoid(`${BASE}/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

export function truncateSessionMessages(id: string, fromMessageId: number): Promise<void> {
  return requestVoid(`${BASE}/${encodeURIComponent(id)}/messages/${fromMessageId}`, { method: 'DELETE' })
}
