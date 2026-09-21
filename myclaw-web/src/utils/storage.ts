/**
 * localStorage 的安全包装。
 * 隐私模式、无痕窗口或配额写满时浏览器会直接抛错，这里统一静默降级，
 * 保证存储不可用不会连带把界面搞崩。
 */

export function readLocal<T>(key: string, fallback: T): T {
    try {
        const raw = window.localStorage.getItem(key)
        return raw === null ? fallback : (JSON.parse(raw) as T)
    } catch {
        return fallback
    }
}

export function writeLocal(key: string, value: unknown): void {
    try {
        window.localStorage.setItem(key, JSON.stringify(value))
    } catch {
        // 忽略：存储不可用时放弃持久化即可。
    }
}

/** 存储键集中管理，避免散落在各处的魔法字符串。 */
export const STORAGE_KEYS = {
    theme: 'myclaw.theme',
    endpoint: 'myclaw.endpoint',
    workingDirectory: 'myclaw.working-directory',
    workspaces: 'myclaw.workspaces',
    permissionMode: 'myclaw.permission-mode',
    sidebarCollapsed: 'myclaw.sidebar-collapsed',
} as const
