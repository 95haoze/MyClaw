import type { Message } from '../api'

/**
 * 前端会话视图模型。
 * 本地新建的会话在第一次发送时才落库，所以服务端字段都是可选的。
 */
export interface Session {
  id: string
  title: string
  messages: Message[]
  /** 服务端返回的更新时间，本地新建的会话为空。 */
  updatedAt?: string
}

export type ThemeMode = 'light' | 'dark'
