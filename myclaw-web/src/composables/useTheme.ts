import {computed, ref, watchEffect} from 'vue'
import type {ThemeMode} from '../types'
import {readLocal, STORAGE_KEYS, writeLocal} from '../utils/storage'

/**
 * 模块级单例：主题是全站唯一状态，多个组件调用 useTheme 拿到的是同一份。
 * 实际写入 <html data-theme> 的时机交给 watchEffect，避免各处手动同步。
 */
const theme = ref<ThemeMode>('light')
let initialized = false

function initialize() {
    if (initialized) return
    initialized = true

    const stored = readLocal<ThemeMode | null>(STORAGE_KEYS.theme, null)
    const prefersDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
    theme.value = stored ?? (prefersDark ? 'dark' : 'light')

    watchEffect(() => {
        document.documentElement.dataset.theme = theme.value
    })
}

export function useTheme() {
    initialize()

    const isDark = computed(() => theme.value === 'dark')

    function toggleTheme() {
        theme.value = isDark.value ? 'light' : 'dark'
        writeLocal(STORAGE_KEYS.theme, theme.value)
    }

    return {theme, isDark, toggleTheme}
}
