import { computed, ref, watch } from 'vue'
import { readLocal, STORAGE_KEYS, writeLocal } from '../utils/storage'

export const DEFAULT_ENDPOINT = '/api/chat'
const ENDPOINT_PATTERN = /^\/api\/[\w/.-]+$/

/** 后端连接设置。聊天始终调用真实服务端，不提供本地演示分支。 */
export function useSettings() {
  const endpoint = ref(readLocal(STORAGE_KEYS.endpoint, DEFAULT_ENDPOINT))
  const modalOpen = ref(false)

  watch(endpoint, value => writeLocal(STORAGE_KEYS.endpoint, value))
  const endpointValid = computed(() => ENDPOINT_PATTERN.test(endpoint.value))

  function openSettings() { modalOpen.value = true }
  function closeSettings() { modalOpen.value = false }

  return { endpoint, modalOpen, endpointValid, openSettings, closeSettings }
}