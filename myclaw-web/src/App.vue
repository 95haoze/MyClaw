<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {currentUser, logout, type CurrentUser} from './api'
import LoginView from './components/LoginView.vue'
import WorkspaceShell from './WorkspaceShell.vue'

const user = ref<CurrentUser | null>(null)
const ready = ref(false)
onMounted(async () => {
  try {
    user.value = await currentUser()
  } catch {
    user.value = null
  } finally {
    ready.value = true
  }
})

function authenticated(value: CurrentUser) {
  user.value = value
}

async function signOut() {
  await logout();
  user.value = null
}
</script>

<template>
  <div v-if="!ready" class="loading">正在加载…</div>
  <LoginView v-else-if="!user" @authenticated="authenticated"/>
  <WorkspaceShell v-else :current-user="user" @logout="signOut"/>
</template>

<style scoped>
.loading {
  display: grid;
  place-items: center;
  height: 100dvh;
  color: var(--text-muted)
}
</style>
