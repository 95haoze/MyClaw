import { requestJson, requestVoid } from './http'
export interface CurrentUser { id: number; email: string; displayName: string }
const headers = { 'Content-Type': 'application/json' }
export const currentUser = () => requestJson<CurrentUser>('/api/auth/me')
export const login = (email: string, password: string) =>
  requestJson<CurrentUser>('/api/auth/login', { method: 'POST', headers, body: JSON.stringify({ email, password }) })
export async function register(email: string, password: string, displayName: string): Promise<void> {
  await requestJson<CurrentUser>('/api/auth/register', {
    method: 'POST', headers, body: JSON.stringify({ email, password, displayName }),
  })
}
export const logout = () => requestVoid('/api/auth/logout', { method: 'POST' })
