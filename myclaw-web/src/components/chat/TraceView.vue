<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {Bot, Clock3, UserRound, Wrench, X} from 'lucide-vue-next'
import type {ExecutionActivity, Message, ToolExecution} from '../../api'
import {formatDuration} from '../../utils/format'

const props = defineProps<{ messages: Message[]; busy: boolean; activity: ExecutionActivity | null }>()
type Row = { id:string; kind:'user'|'assistant'|'tool'; label:string; content:string; duration?:number; tool?:ToolExecution }
const selectedId = ref<string | null>(null)
const detailTab = ref<'overview'|'raw'>('overview')
const rows = computed<Row[]>(() => {
  const result: Row[] = []
  props.messages.forEach((message,index) => {
    if(message.role==='user') result.push({id:`m-${message.id??index}`,kind:'user',label:'用户',content:message.content})
    else {
      message.tools?.forEach(tool=>result.push({id:`t-${message.id??index}-${tool.id}`,kind:'tool',label:'工具',content:tool.name,duration:tool.durationMillis,tool}))
      if(message.content) result.push({id:`m-${message.id??index}`,kind:'assistant',label:'助手',content:message.content,duration:message.durationMillis})
    }
  })
  if(props.busy) props.activity?.tools.forEach(tool=>result.push({id:`live-${tool.id}`,kind:'tool',label:'工具',content:tool.name,duration:tool.durationMillis,tool}))
  return result
})
const selected = computed(()=>rows.value.find(row=>row.id===selectedId.value)||null)
const tools = computed(()=>rows.value.filter(row=>row.kind==='tool'))
const duration = computed(()=>props.messages.reduce((sum,message)=>sum+(message.durationMillis||0),0))
function compact(value:string):string{return value.replace(/\s+/g,' ').trim()}
function select(row:Row):void{selectedId.value=row.id;detailTab.value='overview'}
watch(rows,value=>{if(selectedId.value&&!value.some(row=>row.id===selectedId.value))selectedId.value=null})
</script>

<template>
  <section class="trace" :class="{inspecting:selected}">
    <div class="trace-main">
      <header class="trace-summary">
        <span><Clock3 :size="14"/> 时长 {{ formatDuration(duration) }}</span>
        <span>轮次 {{ messages.filter(item=>item.role==='assistant').length }}</span>
        <span>调用 {{ tools.length }}</span>
      </header>
      <div class="bars" aria-hidden="true"><i v-for="row in rows" :key="row.id" :class="[row.kind,{selected:row.id===selectedId}]"/></div>
      <div v-if="rows.length" class="timeline">
        <button v-for="row in rows" :key="row.id" type="button" class="row" :class="[row.kind,{selected:row.id===selectedId}]" @click="select(row)">
          <span class="dot"/>
          <span class="badge"><UserRound v-if="row.kind==='user'" :size="12"/><Bot v-else-if="row.kind==='assistant'" :size="12"/><Wrench v-else :size="12"/>{{ row.label }}</span>
          <strong>{{ row.content }}</strong>
          <span v-if="row.tool" class="args">{{ compact(row.tool.arguments) }}</span>
          <span v-if="row.tool" class="arrow">→</span>
          <span v-if="row.tool" class="result">{{ compact(row.tool.result) }}</span>
          <span v-if="row.tool" class="status" :class="row.tool.status">{{ row.tool.status==='completed'?'成功':row.tool.status==='failed'?'失败':'执行中' }}</span>
          <small v-if="row.duration">{{ formatDuration(row.duration) }}</small>
        </button>
      </div>
      <div v-else class="empty">当前对话还没有可展示的执行轨迹</div>
    </div>

    <aside v-if="selected" class="inspector">
      <header><span class="badge"><Wrench v-if="selected.kind==='tool'" :size="12"/><Bot v-else :size="12"/>{{ selected.label }}</span><strong>{{ selected.content }}</strong><button type="button" aria-label="关闭详情" @click="selectedId=null"><X :size="16"/></button></header>
      <nav><button type="button" :class="{active:detailTab==='overview'}" @click="detailTab='overview'">概述</button><button type="button" :class="{active:detailTab==='raw'}" @click="detailTab='raw'">原始内容</button></nav>
      <div v-if="detailTab==='overview'" class="inspector-body">
        <dl><dt>类型</dt><dd>{{ selected.label }}</dd><dt>状态</dt><dd>{{ selected.tool?.status==='failed'?'失败':selected.tool?.status==='running'?'执行中':'已完成' }}</dd><dt>耗时</dt><dd>{{ selected.duration ? formatDuration(selected.duration) : '未记录' }}</dd></dl>
        <template v-if="selected.tool"><h4>调用参数</h4><pre>{{ selected.tool.arguments || '无' }}</pre><h4>执行结果</h4><pre>{{ selected.tool.result || '暂无结果' }}</pre></template>
        <template v-else><h4>内容</h4><div class="text-content">{{ selected.content }}</div></template>
      </div>
      <div v-else class="inspector-body"><pre>{{ selected.tool ? JSON.stringify(selected.tool,null,2) : selected.content }}</pre></div>
    </aside>
  </section>
</template>

<style scoped>
.trace{height:100%;display:flex}.trace-main{min-width:0;flex:1;padding:0 28px 180px;overflow:auto}.trace-summary{position:sticky;top:0;z-index:2;height:38px;display:flex;align-items:center;gap:20px;background:var(--bg-app);border-bottom:1px solid var(--border);color:var(--text-muted);font-size:12px}.trace-summary span{display:flex;align-items:center;gap:5px}.bars{height:30px;display:flex;align-items:center;gap:5px;overflow:hidden;border-bottom:1px solid var(--border)}.bars i{display:block;width:30px;height:7px;border-radius:2px;background:#8e70b4}.bars i.tool{height:6px;background:#d88427}.bars i.user{background:#73829a}.bars i.selected{outline:2px solid var(--accent);outline-offset:1px}.timeline{padding:6px 0}.row{position:relative;width:100%;min-height:39px;display:flex;align-items:center;gap:9px;padding:5px 8px 5px 34px;border:0;border-bottom:1px solid var(--border);background:transparent;color:var(--text-secondary);text-align:left;cursor:pointer;font-size:12px}.row:hover,.row.selected{background:var(--bg-subtle)}.row.selected:before{content:'';position:absolute;left:0;top:0;bottom:0;width:3px;background:var(--accent)}.dot{position:absolute;left:10px;width:6px;height:6px;border-radius:50%;background:var(--text-faint)}.badge{display:inline-flex;align-items:center;gap:4px;padding:2px 6px;border-radius:4px;background:#f2edf8;color:#76569b;white-space:nowrap}.tool .badge,.inspector .badge{background:#fff3df;color:#a96416}.user .badge{background:var(--bg-subtle);color:var(--text-muted)}.row strong{max-width:210px;overflow:hidden;text-overflow:ellipsis;font:500 12px/1.4 var(--font-mono);white-space:nowrap}.args,.result{min-width:0;flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:var(--text-muted);font:11px var(--font-mono)}.arrow{color:var(--text-faint)}.status{margin-left:auto;color:var(--text-muted)}.status.completed{color:var(--success-text)}.status.failed{color:var(--danger-text)}.row small{color:var(--text-faint);white-space:nowrap}.empty{padding:80px 0;text-align:center;color:var(--text-faint)}
.inspector{width:350px;flex:0 0 350px;border-left:1px solid var(--border);background:var(--bg-app);overflow:auto}.inspector>header{height:54px;display:flex;align-items:center;gap:8px;padding:0 14px;border-bottom:1px solid var(--border)}.inspector>header strong{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font:600 12px var(--font-mono)}.inspector>header button{margin-left:auto;display:grid;place-items:center;width:28px;height:28px;border:0;border-radius:7px;background:transparent;color:var(--text-muted);cursor:pointer}.inspector nav{height:38px;display:flex;gap:20px;padding:0 16px;border-bottom:1px solid var(--border)}.inspector nav button{position:relative;border:0;background:transparent;color:var(--text-muted);font-size:12px;cursor:pointer}.inspector nav button.active{color:var(--accent);font-weight:600}.inspector nav button.active:after{content:'';position:absolute;left:0;right:0;bottom:-1px;height:2px;background:var(--accent)}.inspector-body{padding:18px 16px}.inspector dl{display:grid;grid-template-columns:70px 1fr;gap:9px;margin:0;font-size:12px}.inspector dt{color:var(--text-faint)}.inspector dd{margin:0;color:var(--text-secondary)}.inspector h4{margin:22px 0 8px;font-size:12px}.inspector pre,.text-content{margin:0;padding:10px;border-radius:7px;background:var(--code-bg);color:var(--code-text);font:11px/1.6 var(--font-mono);white-space:pre-wrap;overflow-wrap:anywhere;max-height:280px;overflow:auto}.text-content{font-family:inherit;color:var(--text-secondary)}
@media(max-width:1050px){.inspector{position:absolute;right:0;top:92px;bottom:0;z-index:4;width:min(380px,88vw);box-shadow:-12px 0 30px rgba(0,0,0,.1)}}
</style>
