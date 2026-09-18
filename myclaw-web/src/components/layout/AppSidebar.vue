<script setup lang="ts">
import { Eraser, LogOut, Moon, PanelLeftClose, PawPrint, Plus, Search, Settings2, Sun, UserRound, X } from 'lucide-vue-next'
import type { Session } from '../../types'
import type { CurrentUser } from '../../api'
import SessionItem from '../sidebar/SessionItem.vue'

defineProps<{
  open: boolean
  collapsed: boolean
  /** 已按时间分好组的会话，空组不会传进来。 */
  groups: { label: string; items: Session[] }[]
  total: number
  selectedId: string
  busy: boolean
  loading: boolean
  currentUser: CurrentUser
  isDark: boolean
  hasMessages: boolean
}>()

/** 搜索词由父级持有，这里双向绑定。 */
const query = defineModel<string>('query', { required: true })

const emit = defineEmits<{
  newChat: []
  select: [id: string]
  rename: [session: Session]
  remove: [id: string]
  openSettings: []
  toggleCollapse: []
  close: []
  clear: []
  toggleTheme: []
  logout: []
}>()
</script>

<template>
  <aside class="sidebar" :class="{ open, collapsed }">
    <div class="brand">
      <span class="brand-mark"><PawPrint :size="15" /></span>
      <span class="brand-name">MyClaw</span>
      <span class="brand-tag">BETA</span>
      <button class="mobile-close" type="button" aria-label="关闭侧边栏" @click="emit('close')"><X :size="18" /></button>
      <button class="collapse-button" type="button" aria-label="收起侧边栏" title="收起侧边栏" @click="emit('toggleCollapse')"><PanelLeftClose :size="17" /></button>
    </div>

    <div class="side-pad">
      <button class="new-chat" type="button" :disabled="busy" @click="emit('newChat')">
        <Plus :size="15" />
        开启新对话
      </button>

      <label class="search">
        <Search :size="15" />
        <input v-model="query" type="search" placeholder="搜索历史对话" aria-label="搜索历史对话" />
      </label>
    </div>

    <div class="side-scroll">
      <p v-if="loading" class="placeholder">正在加载历史记录…</p>

      <template v-else>
        <div v-for="group in groups" :key="group.label" class="group">
          <div class="group-label">{{ group.label }}</div>
          <SessionItem
            v-for="session in group.items"
            :key="session.id"
            :session="session"
            :active="session.id === selectedId"
            :busy="busy"
            @select="emit('select', $event)"
            @rename="emit('rename', $event)"
            @remove="emit('remove', $event)"
          />
        </div>

        <p v-if="!groups.length" class="placeholder">
          {{ query ? '没有找到相关对话' : '新的灵感，从第一句开始。' }}
        </p>
      </template>
    </div>

    <div class="side-foot">
      <div class="foot-actions">
        <button class="foot-link" type="button" @click="emit('openSettings')"><Settings2 :size="16" /><span>连接设置</span></button>
        <button v-if="hasMessages" class="foot-link" type="button" :disabled="busy" @click="emit('clear')"><Eraser :size="16" /><span>清空当前对话</span></button>
        <button class="foot-link" type="button" @click="emit('toggleTheme')"><component :is="isDark ? Sun : Moon" :size="16" /><span>{{ isDark ? '浅色主题' : '深色主题' }}</span></button>
      </div>
      <div class="account-row">
        <span class="avatar"><UserRound :size="16" /></span>
        <div class="workspace-meta"><div class="workspace-name">{{ currentUser.displayName }}</div><div class="workspace-sub">{{ currentUser.email }}</div></div>
        <button class="logout-button" type="button" aria-label="退出登录" title="退出登录" @click="emit('logout')"><LogOut :size="16" /></button>
      </div>
    </div>  </aside>
</template>

<style scoped>
.sidebar {
  width: var(--sidebar-width);
  flex: 0 0 var(--sidebar-width);
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--bg-sidebar);
  border-right: 1px solid var(--border);
  overflow: hidden;
}

/* ── 品牌 ── */
.brand {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 9px;
  height: 88px;
  padding: 0 25px;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 39px;
  height: 39px;
  flex: 0 0 auto;
  border-radius: 14px;
  background: #292a27;
  color: var(--text-on-accent);
}

.brand-name {
  font-size: 21px;
  font-weight: 640;
  letter-spacing: -0.01em;
  color: var(--text-primary);
}

.brand-tag {
  padding: 1px 5px;
  border: 1px solid var(--border);
  border-radius: 4px;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.03em;
  color: var(--text-faint);
}

/* ── 新建与搜索 ── */
.side-pad {
  flex: 0 0 auto;
  padding: 0 17px;
}

.new-chat {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  height: 52px;
  padding: 0 17px;
  border-radius: 14px;
  background: #292a27;
  color: var(--text-on-accent);
  font-size: 14.5px;
  font-weight: 560;
}

.new-chat:hover:not(:disabled) {
  background: var(--accent-hover);
}

.search {
  position: relative;
  display: block;
  margin-top: 16px;
}

.search svg {
  position: absolute;
  left: 11px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--text-faint);
  pointer-events: none;
}

.search input {
  width: 100%;
  height: 49px;
  padding: 0 12px 0 34px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--bg-surface);
  font-size: 14.5px;
  color: var(--text-primary);
}

.search input::placeholder {
  color: var(--text-faint);
}

/* ── 会话列表 ── */
.side-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 30px 17px 9px;
}

.group + .group {
  margin-top: 16px;
}

.group-label {
  padding: 0 8px 6px;
  font-size: 11px;
  font-weight: 560;
  color: var(--text-faint);
}

.placeholder {
  padding: 6px 8px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--text-faint);
}

/* ── 底部 ── */
.side-foot {
  flex: 0 0 auto;
  padding: 18px 17px 22px;
  border-top: 1px solid var(--border);
}

.foot-link {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--text-secondary);
}

.foot-link:hover {
  background: var(--bg-subtle);
}

.foot-actions { display:grid; gap:2px; }
.account-row { display:flex; align-items:center; gap:9px; margin-top:12px; padding-top:14px; border-top:1px solid var(--border); }
.avatar { display:grid; place-items:center; width:36px; height:36px; flex:0 0 auto; border:1px solid var(--border); border-radius:10px; background:var(--bg-subtle); color:var(--text-secondary); }
.workspace-meta { min-width:0; flex:1; }
.workspace-name { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:13px; font-weight:560; line-height:1.35; color:var(--text-primary); }
.workspace-sub { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:11px; line-height:1.35; color:var(--text-faint); }
.logout-button { display:grid; place-items:center; width:34px; height:34px; flex:0 0 auto; border-radius:8px; color:var(--text-muted); }
.logout-button:hover { background:var(--danger-soft); color:var(--danger-text); }
.mobile-close {
  display: none;
  place-items: center;
  width: 40px;
  height: 40px;
  margin-left: auto;
  border-radius: 9px;
  color: var(--text-muted);
}
.mobile-close:hover { background: var(--bg-subtle); color: var(--text-primary); }
.collapse-button {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  margin-left: auto;
  flex: 0 0 auto;
  border-radius: 8px;
  color: var(--text-muted);
}
.collapse-button:hover { background: var(--bg-subtle); color: var(--text-primary); }

@media (min-width: 821px) {
  .sidebar { transition: width var(--transition-base), flex-basis var(--transition-base), border-color var(--transition-base); }
  .sidebar.collapsed { width:0; flex-basis:0; border-right-color:transparent; }
  .sidebar.collapsed > * { visibility:hidden; }
}
@media (max-width: 820px) {
  .sidebar {
    width: min(var(--sidebar-width), calc(100vw - 44px));
    flex-basis: min(var(--sidebar-width), calc(100vw - 44px));
    position: fixed;
    inset: 0 auto 0 0;
    z-index: var(--z-sidebar);
    transform: translateX(-100%);
    transition: transform var(--transition-base);
  }
  .sidebar.open { transform: translateX(0); }
  .collapse-button { display: none; }
  .mobile-close { display: grid; }
  .new-chat { min-height: 44px; }
  .foot-link { min-height: 44px; }
  .logout-button { width:44px; height:44px; }
}
</style>
