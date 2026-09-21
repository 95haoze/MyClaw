import {computed, ref, watch} from 'vue'
import {readLocal, STORAGE_KEYS, writeLocal} from '../utils/storage'

export const DEFAULT_ENDPOINT = '/api/chat'
const ENDPOINT_PATTERN = /^\/api\/[\w/.-]+$/

/** 后端连接设置。聊天始终调用真实服务端，不提供本地演示分支。 */
export function useSettings() {
    const endpoint = ref(readLocal(STORAGE_KEYS.endpoint, DEFAULT_ENDPOINT))
    const workingDirectory = ref(readLocal(STORAGE_KEYS.workingDirectory, '.'))
    const workspaces = ref<string[]>(readLocal(STORAGE_KEYS.workspaces, [workingDirectory.value]))
    const permissionMode = ref<'read-only' | 'workspace-write' | 'full-access'>(readLocal(STORAGE_KEYS.permissionMode, 'workspace-write'))
    const modalOpen = ref(false)

    watch(endpoint, value => writeLocal(STORAGE_KEYS.endpoint, value))
    watch(workingDirectory, value => {
        const normalized = value.trim() || '.'
        writeLocal(STORAGE_KEYS.workingDirectory, normalized)
        if (!workspaces.value.includes(normalized)) workspaces.value = [...workspaces.value, normalized]
    })
    watch(workspaces, value => writeLocal(STORAGE_KEYS.workspaces, value), {deep: true})
    watch(permissionMode, value => writeLocal(STORAGE_KEYS.permissionMode, value))
    const endpointValid = computed(() => ENDPOINT_PATTERN.test(endpoint.value))

    function removeWorkspace(path: string) {
        const remaining = workspaces.value.filter(item => item !== path)
        workspaces.value = remaining.length ? remaining : ['.']
        if (workingDirectory.value === path) workingDirectory.value = workspaces.value[0] ?? '.'
    }

    function openSettings() {
        modalOpen.value = true
    }

    function closeSettings() {
        modalOpen.value = false
    }

    return {
        endpoint,
        workingDirectory,
        workspaces,
        permissionMode,
        modalOpen,
        endpointValid,
        removeWorkspace,
        openSettings,
        closeSettings
    }
}