<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import {ChevronLeft, Folder, FolderOpen, X} from 'lucide-vue-next';
import {DialogContent, DialogOverlay, DialogPortal, DialogRoot, DialogTitle} from 'reka-ui';
import {browseWorkspaces, type WorkspaceDirectoryView} from '../../api';
import Button from '../ui/button/Button.vue'

const p = defineProps<{ open: boolean; modelValue: string; busy: boolean }>(),
    e = defineEmits<{ 'update:open': [boolean]; 'update:modelValue': [string] }>();
const v = ref<WorkspaceDirectoryView | null>(null), loading = ref(false), error = ref('');
const label = computed(() => v.value?.current === '__roots__' ? '允许的位置' : v.value?.current || '');
watch(() => p.open, x => {
  if (x) void load(p.modelValue || '.')
});

async function load(path: string) {
  loading.value = true;
  error.value = '';
  try {
    v.value = await browseWorkspaces(path)
  } catch (x) {
    error.value = x instanceof Error ? x.message : '目录读取失败'
  } finally {
    loading.value = false
  }
}

function choose() {
  if (v.value) {
    e('update:modelValue', v.value.current);
    e('update:open', false)
  }
}
</script>
<template>
  <DialogRoot :open="open" @update:open="e('update:open',$event)">
    <DialogPortal>
      <DialogOverlay class="ov"/>
      <DialogContent class="dlg">
        <header>
          <DialogTitle>选择工作目录</DialogTitle>
          <button @click="e('update:open',false)">
            <X :size="16"/>
          </button>
        </header>
        <div class="path">
          <FolderOpen :size="16"/>
          <span>{{ label }}</span>
          <button v-if="v?.canBrowseRoots && v.current !== '__roots__'" type="button" @click="load('__roots__')">
            此电脑
          </button>
        </div>
        <div class="list">
          <button v-if="v?.parent" @click="load(v.parent)">
            <ChevronLeft :size="17"/>
            返回上一级
          </button>
          <button v-for="d in v?.directories" :key="d.path" @click="load(d.path)">
            <Folder :size="17"/>
            {{ d.name }}
          </button>
          <p v-if="loading">正在读取目录…</p>
          <p v-else-if="error" class="error">{{ error }}</p>
          <p v-else-if="v&&!v.directories.length">此目录没有子目录</p></div>
        <footer><span>当前：{{ label }}</span>
          <Button :disabled="loading||!v||busy||v.current==='__roots__'" @click="choose">选择此目录</Button>
        </footer>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>
<style scoped>.ov {
  position: fixed;
  inset: 0;
  z-index: 50;
  background: #18181b6b
}

.dlg {
  position: fixed;
  z-index: 51;
  top: 50%;
  left: 50%;
  width: 520px;
  max-width: calc(100vw - 32px);
  transform: translate(-50%, -50%);
  padding: 20px;
  border: 1px solid var(--border);
  border-radius: 16px;
  background: var(--bg-surface);
  box-shadow: var(--shadow-lg)
}

header, footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px
}

.path {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 18px;
  padding: 10px;
  background: var(--bg-subtle);
  border-radius: 9px;
  font-size: 12px
}

.path span {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap
}

.path button {
  padding: 4px 7px;
  border-radius: 6px;
  color: var(--text-muted)
}

.path button:hover {
  background: var(--bg-hover);
  color: var(--text-primary)
}

.list {
  height: 300px;
  margin: 10px 0 14px;
  overflow: auto;
  border: 1px solid var(--border);
  border-radius: 10px
}

.list button {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border);
  color: var(--text-primary)
}

.list button:hover {
  background: var(--bg-hover)
}

.list p {
  padding: 30px;
  text-align: center;
  color: var(--text-muted);
  font-size: 12px
}

.list .error {
  color: var(--danger-text)
}

footer span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--text-muted)
}</style>