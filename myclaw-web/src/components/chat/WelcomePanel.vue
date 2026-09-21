<script setup lang="ts">
import {FileText, Sparkles, SquarePen, Terminal} from 'lucide-vue-next'
import type {Component} from 'vue'

const emit = defineEmits<{ pick: [text: string] }>()

/**
 * 空状态的引导卡。点击只把内容填进输入框、不直接发送，
 * 用户还能再改一改措辞。
 */
const suggestions: { icon: Component; title: string; text: string }[] = [
  {
    icon: SquarePen,
    title: '写一份技术方案',
    text: '为我写一个 agent 实现的完整方案',
  },
  {
    icon: FileText,
    title: '读文档提炼要点',
    text: '帮我读一下这份文档，提炼关键结论',
  },
  {
    icon: Terminal,
    title: '分析代码',
    text: '分析一下这段代码的性能瓶颈',
  },
]
</script>

<template>
  <section class="empty">
    <h2>今天想做点什么？</h2>
    <p>描述你的想法，剩下的交给 MyClaw</p>

    <div class="suggestions">
      <button
          v-for="item in suggestions"
          :key="item.title"
          type="button"
          class="suggestion"
          @click="emit('pick', item.text)"
      >
        <component :is="item.icon" :size="18" class="icon"/>
        <b>{{ item.title }}</b>
        <small>{{ item.text }}</small>
      </button>
    </div>
  </section>
</template>

<style scoped>
.empty {
  width: min(990px, calc(100% - 44px));
  margin: 0 auto;
  padding-top: clamp(106px, 14vh, 162px);
  text-align: center;
}

.hero-icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  margin: 0 auto 22px;
  border-radius: 16px;
  background: #eeece8;
  color: #65645e;
}

h2 {
  margin: 0 0 11px;
  font-size: 30px;
  line-height: 1.2;
  font-weight: 720;
  letter-spacing: -0.035em;
  color: var(--text-primary);
}

p {
  margin: 0 0 41px;
  font-size: 15px;
  color: var(--text-muted);
}

.suggestions {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.suggestion {
  min-height: 207px;
  padding: 22px;
  text-align: left;
  border: 1px solid var(--border);
  border-radius: 16px;
  background: var(--bg-surface);
  transition: border-color var(--transition-base), transform var(--transition-base);
}

.suggestion:hover {
  border-color: var(--border-strong);
  transform: translateY(-2px);
}

.icon {
  display: grid;
  width: 43px;
  height: 43px;
  margin-bottom: 34px;
  padding: 13px;
  border-radius: 14px;
  background: #f0efec;
  color: #5d5c56;
}

b {
  display: block;
  margin-bottom: 10px;
  font-size: 15px;
  font-weight: 650;
  color: var(--text-primary);
}

small {
  display: block;
  font-size: 14px;
  line-height: 1.75;
  color: var(--text-muted);
}

@media (max-width: 960px) {
  .empty {
    padding-top: 50px;
  }

  .suggestions {
    grid-template-columns: 1fr;
  }

  .suggestion {
    min-height: 150px;
  }

  .icon {
    margin-bottom: 20px;
  }
}
</style>
