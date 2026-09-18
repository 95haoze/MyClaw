import type {ApiErrorBody} from './types'

export class ChatApiError extends Error {
    readonly code: string
    readonly retryable: boolean
    readonly status?: number

    constructor(code: string, message: string, retryable: boolean, status?: number) {
        super(message);
        this.name = 'ChatApiError';
        this.code = code;
        this.retryable = retryable;
        this.status = status
    }
}

export interface ApiEnvelope<T> {
    code: string;
    message: string;
    data: T;
    retryable: boolean
}

export function isObject(value: unknown): value is Record<string, unknown> {
    return value !== null && typeof value === 'object'
}

export async function readApiError(response: Response): Promise<ChatApiError> {
    try {
        const data: unknown = await response.json()
        if (isObject(data) && typeof data.code === 'string' && typeof data.message === 'string') {
            const body = data as unknown as ApiErrorBody
            return new ChatApiError(body.code, body.message,
                typeof body.retryable === 'boolean' ? body.retryable : response.status >= 500, response.status)
        }
    } catch { /* non-JSON gateway response */
    }
    return new ChatApiError(response.status === 429 ? 'MODEL_RATE_LIMIT' : 'HTTP_ERROR',
        `Request failed (HTTP ${response.status}).`, response.status === 429 || response.status >= 500, response.status)
}

export async function readData<T>(response: Response): Promise<T> {
    const envelope = await response.json() as ApiEnvelope<T>
    if (!envelope || envelope.code !== 'OK' || !('data' in envelope))
        throw new ChatApiError(envelope?.code || 'INVALID_RESPONSE', envelope?.message || 'Invalid API response.', false, response.status)
    return envelope.data
}

let csrfHeader = 'X-XSRF-TOKEN'
let csrfToken = ''

export async function ensureCsrf(): Promise<void> {
    if (csrfToken) return
    const response = await fetch('/api/auth/csrf', {credentials: 'same-origin'})
    if (!response.ok) throw await readApiError(response)
    const data = await readData<{ headerName: string; token: string }>(response)
    csrfHeader = data.headerName;
    csrfToken = data.token
}

export async function apiFetch(input: string, init: RequestInit = {}): Promise<Response> {
    const method = (init.method || 'GET').toUpperCase()
    const headers = new Headers(init.headers)
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
        await ensureCsrf();
        headers.set(csrfHeader, csrfToken)
    }
    return fetch(input, {...init, headers, credentials: 'same-origin'})
}

export async function requestJson<T>(input: string, init?: RequestInit): Promise<T> {
    const response = await apiFetch(input, init)
    if (!response.ok) throw await readApiError(response)
    return readData<T>(response)
}

export async function requestVoid(input: string, init?: RequestInit): Promise<void> {
    const response = await apiFetch(input, init)
    if (!response.ok) throw await readApiError(response)
}
