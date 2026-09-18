import { requestVoid } from './http'
export type FeedbackValue = 'up' | 'down'
export function saveFeedback(messageId: number, value: FeedbackValue): Promise<void> {
  return requestVoid(`/api/messages/${messageId}/feedback`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ value }),
  })
}
export function clearMessageFeedback(messageId: number): Promise<void> {
  return requestVoid(`/api/messages/${messageId}/feedback`, { method: 'DELETE' })
}
