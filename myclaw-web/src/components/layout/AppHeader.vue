<script setup lang="ts">
import { ChevronDown, Eraser, LogOut, Menu, Moon, Settings2, Sun, UserRound } from 'lucide-vue-next'
import { DropdownMenuContent, DropdownMenuItem, DropdownMenuPortal, DropdownMenuRoot, DropdownMenuSeparator, DropdownMenuTrigger } from 'reka-ui'
import type { CurrentUser } from '../../api'
import Button from '../ui/button/Button.vue'

defineProps<{ title?: string; hasMessages: boolean; isDark: boolean; busy: boolean; currentUser: CurrentUser }>()
const emit = defineEmits<{ toggleMenu: []; clear: []; openSettings: []; toggleTheme: []; logout: [] }>()
</script>
<template>
  <header class="topbar">
    <div class="crumb">
      <Button class="menu-trigger" variant="ghost" size="icon" aria-label="打开导航" @click="emit('toggleMenu')"><Menu :size="17" /></Button>
      <span class="root">Agent 工作台</span><span class="separator">/</span>
      <span class="current">{{ title || '新对话' }}</span>
    </div>
    <div class="side">
      <Button v-if="hasMessages" variant="ghost" size="icon" :disabled="busy" aria-label="清空当前会话" @click="emit('clear')"><Eraser :size="16" /></Button>
      <DropdownMenuRoot>
        <DropdownMenuTrigger as-child>
          <Button variant="ghost" class="account-trigger">
            <span class="avatar"><UserRound :size="15" /></span>
            <span class="account-name">{{ currentUser.displayName }}</span><ChevronDown :size="14" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuPortal>
          <DropdownMenuContent class="account-menu" :side-offset="8" align="end">
            <div class="identity"><strong>{{ currentUser.displayName }}</strong><span>{{ currentUser.email }}</span></div>
            <DropdownMenuSeparator class="menu-separator" />
            <DropdownMenuItem class="menu-item" @select="emit('openSettings')"><Settings2 :size="15" />连接设置</DropdownMenuItem>
            <DropdownMenuItem class="menu-item" @select="emit('toggleTheme')"><component :is="isDark ? Sun : Moon" :size="15" />{{ isDark ? '浅色主题' : '深色主题' }}</DropdownMenuItem>
            <DropdownMenuSeparator class="menu-separator" />
            <DropdownMenuItem class="menu-item danger" @select="emit('logout')"><LogOut :size="15" />退出登录</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenuPortal>
      </DropdownMenuRoot>
    </div>
  </header>
</template>
<style scoped>
.topbar{flex:0 0 auto;display:flex;align-items:center;height:var(--header-height);padding:0 30px;background:var(--bg-surface);border-bottom:1px solid var(--border)}.crumb{display:flex;align-items:center;gap:7px;min-width:0}.root{font-size:14px;color:var(--text-faint);white-space:nowrap}.separator{color:var(--text-faint)}.current{max-width:360px;font-size:13.5px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.side{display:flex;align-items:center;gap:4px;margin-left:auto}.menu-trigger{display:none}.account-trigger{height:36px;padding:0 8px 0 5px}.avatar{display:grid;place-items:center;width:26px;height:26px;border:1px solid var(--border);border-radius:8px;background:var(--bg-surface)}.account-name{max-width:120px;overflow:hidden;text-overflow:ellipsis}
@media(max-width:820px){.topbar{padding:0 12px}.menu-trigger{display:inline-flex}.root,.separator,.account-name{display:none}.current{max-width:190px}.account-trigger{width:34px;padding:0}}
</style>
<style>
.account-menu{z-index:60;width:224px;padding:6px;border:1px solid var(--border);border-radius:12px;background:var(--bg-surface);box-shadow:var(--shadow-lg);transform-origin:var(--reka-dropdown-menu-content-transform-origin);animation:menu-in .12s ease-out}.identity{display:flex;flex-direction:column;gap:2px;padding:8px 9px}.identity strong{font-size:13px}.identity span{font-size:11.5px;color:var(--text-faint);overflow:hidden;text-overflow:ellipsis}.menu-item{display:flex;align-items:center;gap:9px;padding:8px 9px;border-radius:7px;font-size:12.5px;color:var(--text-muted);outline:none;cursor:pointer}.menu-item[data-highlighted]{background:var(--bg-subtle);color:var(--text-primary)}.menu-item.danger[data-highlighted]{background:var(--danger-soft);color:var(--danger-text)}.menu-separator{height:1px;margin:5px;background:var(--border)}@keyframes menu-in{from{opacity:0;transform:translateY(-3px) scale(.98)}}
</style>
