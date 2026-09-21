<script setup lang="ts">
import {CircleHelp, X} from 'lucide-vue-next'
import {
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogOverlay,
  DialogPortal,
  DialogRoot,
  DialogTitle
} from 'reka-ui'
import Button from '../ui/button/Button.vue'
import Input from '../ui/input/Input.vue'

const props = defineProps<{ busy: boolean }>()
const open = defineModel<boolean>('open', {required: true})
const endpoint = defineModel<string>('endpoint', {required: true})
</script>
<template>
  <DialogRoot v-model:open="open">
    <DialogPortal>
      <DialogOverlay class="dialog-overlay"/>
      <DialogContent class="dialog-content">
        <header>
          <div>
            <DialogTitle class="dialog-title">连接设置</DialogTitle>
            <DialogDescription class="dialog-description">配置 Agent 服务地址，聊天请求会调用真实后端。
            </DialogDescription>
          </div>
          <DialogClose as-child>
            <Button variant="ghost" size="icon" aria-label="关闭设置">
              <X :size="16"/>
            </Button>
          </DialogClose>
        </header>
        <label class="field"><span>聊天接口路径</span><Input v-model="endpoint" :disabled="props.busy"
                                                             placeholder="/api/chat"/><small>必须以 /api/ 开头，开发环境默认转发至
          localhost:19090。</small></label>
        <div class="note">
          <CircleHelp :size="15"/>
          <p>后端通过 SSE 推送增量内容，API Key 只保存在服务端。</p></div>
        <DialogClose as-child>
          <Button class="save">完成</Button>
        </DialogClose>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>
<style>
.dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: 50;
  background: rgba(24, 24, 27, .42);
  animation: fade-in .15s ease
}

.dialog-content {
  position: fixed;
  z-index: 51;
  top: 50%;
  left: 50%;
  width: 440px;
  max-width: calc(100vw - 32px);
  transform: translate(-50%, -50%);
  padding: 22px;
  border: 1px solid var(--border);
  border-radius: 16px;
  background: var(--bg-surface);
  box-shadow: var(--shadow-lg);
  animation: dialog-in .16s ease
}

.dialog-content header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px
}

.dialog-title {
  font-size: 16px;
  font-weight: 650
}

.dialog-description {
  margin-top: 6px;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--text-muted)
}

.dialog-content .field {
  display: block;
  margin-top: 22px
}

.dialog-content .field > span {
  display: block;
  margin-bottom: 7px;
  font-size: 12.5px;
  font-weight: 560
}

.dialog-content .field small {
  display: block;
  margin-top: 7px;
  font-size: 11.5px;
  color: var(--text-faint)
}

.dialog-content .note {
  display: flex;
  gap: 8px;
  margin-top: 18px;
  padding: 10px 11px;
  border-radius: 9px;
  background: var(--bg-subtle);
  font-size: 11.5px;
  color: var(--text-muted)
}

.dialog-content .note svg {
  margin-top: 2px
}

.dialog-content .save {
  width: 100%;
  margin-top: 20px
}

@keyframes fade-in {
  from {
    opacity: 0
  }
}

@keyframes dialog-in {
  from {
    opacity: 0;
    transform: translate(-50%, -48%) scale(.98)
  }
}
</style>
