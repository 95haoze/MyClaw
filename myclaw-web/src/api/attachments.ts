import {apiFetch, readApiError, readData} from './http'
import type {AttachmentView} from './types'

export async function uploadAttachment(sessionId: string, file: File): Promise<AttachmentView> {
    const body = new FormData()
    body.append('sessionId', sessionId);
    body.append('file', file)
    const response = await apiFetch('/api/attachments', {method: 'POST', body})
    if (!response.ok) throw await readApiError(response)
    return readData<AttachmentView>(response)
}

export async function deleteAttachment(id: string): Promise<void> {
    const response = await apiFetch(`/api/attachments/${encodeURIComponent(id)}`, {method: 'DELETE'})
    if (!response.ok) throw await readApiError(response)
}
