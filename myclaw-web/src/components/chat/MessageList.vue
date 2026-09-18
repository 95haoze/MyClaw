<script setup lang="ts">import type {ExecutionActivity, Message} from '../../api';
import MessageItem from './MessageItem.vue';
import ToolCallPanel from './ToolCallPanel.vue';

const props = defineProps<{ messages: Message[]; busy: boolean; activity?: ExecutionActivity | null }>();
const emit = defineEmits<{ resend: [text: string, branchIndex: number] }>();

function regenerate(index: number) {
  for (let i = index - 1; i >= 0; i--) {
    const m = props.messages[i];
    if (m?.role === 'user') {
      emit('resend', m.content, i);
      return
    }
  }
}
</script>
<template>
  <section class="thread" aria-label="对话内容">
    <MessageItem v-for="(message,index) in messages" :key="index" :message="message" @edit="emit('resend',$event,index)"
                 @regenerate="regenerate(index)"/>
    <div v-if="busy && activity" class="thinking" role="status" aria-live="polite">
      <div class="activity-title"><span class="pulse"/>{{ activity.label }}</div>
      <small v-if="activity.iteration > 1">第 {{ activity.iteration }} 轮处理</small>
      <ToolCallPanel v-if="activity.tools.length" :tools="activity.tools"/>
    </div>
  </section>
</template>
<style scoped>.thread {
  max-width: var(--content-width);
  margin: 0 auto;
  padding: 34px 24px 4px;
  display: flex;
  flex-direction: column;
  gap: 28px
}

.thinking {
  align-self: stretch;
  border-left: 2px solid var(--border-strong);
  display: flex;
  align-items: flex-start;
  flex-direction: column;
  gap: 8px;
  padding-left: 38px;
  font-size: 13px;
  color: var(--text-muted)
}

.activity-title { display:flex; align-items:center; gap:8px; }
.thinking small { padding-left:16px; color:var(--text-faint); }
.pulse { width:8px; height:8px; border-radius:50%; background:var(--accent); animation:pulse 1.4s ease-in-out infinite; }
@keyframes pulse { 50% { opacity:.35; transform:scale(.8) } }

.dots {
  display: inline-flex;
  gap: 3px
}

.dots i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--accent);
  animation: bounce 1.2s ease-in-out infinite
}

.dots i:nth-child(2) {
  animation-delay: .15s
}

.dots i:nth-child(3) {
  animation-delay: .3s
}

@keyframes bounce {
  30% {
    opacity: 1;
    transform: translateY(-3px)
  }
  0%, 60%, 100% {
    opacity: .3;
    transform: none
  }
}

@media (max-width: 820px) {
  .thread {
    padding: 20px 16px 4px;
    gap: 22px
  }

  .thinking {
  align-self: stretch;
  border-left: 2px solid var(--border-strong);
    padding-left: 0
  }
}</style>