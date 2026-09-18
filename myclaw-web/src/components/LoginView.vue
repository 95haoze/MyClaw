<script setup lang="ts">
import { Bot, LoaderCircle } from 'lucide-vue-next'
import { ref } from 'vue'
import { type CurrentUser, login, register } from '../api'
import { describeChatError } from '../utils/errors'
import Button from './ui/button/Button.vue'
import Input from './ui/input/Input.vue'

const emit = defineEmits<{ authenticated: [user: CurrentUser] }>()
const email = ref(''); const password = ref(''); const name = ref('')
const creating = ref(false); const error = ref(''); const busy = ref(false)
async function submit() {
  busy.value = true; error.value = ''
  try {
    if (creating.value) await register(email.value, password.value, name.value)
    emit('authenticated', await login(email.value, password.value))
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '操作失败' }
  finally { busy.value = false }
}
</script>
<template>
  <main class="auth">
    <section class="auth-card">
      <div class="brand"><span><Bot :size="20" /></span><b>MyClaw</b></div>
      <div class="heading"><h1>{{ creating ? '创建工作空间' : '欢迎回来' }}</h1><p>{{ creating ? '注册后开始使用你的 Agent 工作台' : '登录以继续你的对话' }}</p></div>
      <form @submit.prevent="submit">
        <label v-if="creating"><span>显示名称</span><Input v-model="name" placeholder="你的名字" required /></label>
        <label><span>邮箱</span><Input v-model="email" type="email" placeholder="name@example.com" required /></label>
        <label><span>密码</span><Input v-model="password" type="password" placeholder="至少 8 位" :minlength="8" required /></label>
        <p v-if="error" class="error">{{ error }}</p>
        <Button type="submit" class="submit" :disabled="busy"><LoaderCircle v-if="busy" class="spin" :size="15" />{{ busy ? '请稍候' : creating ? '注册并登录' : '登录' }}</Button>
      </form>
      <Button variant="ghost" class="switch" @click="creating = !creating">{{ creating ? '已有账户？登录' : '没有账户？注册' }}</Button>
    </section>
  </main>
</template>
<style scoped>
.auth{display:grid;place-items:center;min-height:100dvh;padding:24px;background:var(--bg-app)}.auth-card{width:380px;max-width:100%;padding:30px;border:1px solid var(--border);border-radius:16px;background:var(--bg-surface)}.brand{display:flex;align-items:center;gap:9px;font-size:15px}.brand span{display:grid;place-items:center;width:34px;height:34px;border-radius:10px;background:var(--text-primary);color:var(--bg-surface)}.heading{margin:30px 0 22px}.heading h1{font-size:22px;letter-spacing:-.02em}.heading p{margin-top:5px;font-size:13px;color:var(--text-muted)}form{display:grid;gap:14px}label span{display:block;margin-bottom:6px;font-size:12.5px;font-weight:550}.submit,.switch{width:100%}.submit{margin-top:3px}.switch{margin-top:12px}.error{padding:8px 10px;border-radius:8px;background:var(--danger-soft);font-size:12px;color:var(--danger-text)}.spin{animation:spin .8s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
</style>
