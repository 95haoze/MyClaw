<script setup lang="ts">
import { DialogContent, DialogDescription, DialogOverlay, DialogPortal, DialogRoot, DialogTitle } from 'reka-ui'
import { ref, watch } from 'vue'
import Button from './ui/button/Button.vue'
import Input from './ui/input/Input.vue'

const props = withDefaults(defineProps<{
  open: boolean
  title: string
  description: string
  confirmText?: string
  defaultValue?: string
  inputLabel?: string
  danger?: boolean
}>(), { confirmText: '确认', defaultValue: '', inputLabel: '', danger: false })
const emit = defineEmits<{ 'update:open': [value: boolean]; confirm: [value: string] }>()
const value = ref(props.defaultValue)
watch(() => props.open, open => { if (open) value.value = props.defaultValue })
function submit() {
  const result = value.value.trim()
  if (props.inputLabel && !result) return
  emit('confirm', result)
}
</script>
<template>
  <DialogRoot :open="open" @update:open="emit('update:open',$event)">
    <DialogPortal>
      <DialogOverlay class="dialog-overlay" />
      <DialogContent class="dialog-content compact" @escape-key-down="emit('update:open',false)">
        <DialogTitle class="dialog-title">{{ title }}</DialogTitle>
        <DialogDescription class="dialog-description">{{ description }}</DialogDescription>
        <label v-if="inputLabel" class="prompt-field"><span>{{ inputLabel }}</span><Input v-model="value" autofocus @keyup.enter="submit" /></label>
        <div class="dialog-actions">
          <Button variant="outline" @click="emit('update:open',false)">取消</Button>
          <Button :variant="danger ? 'danger' : 'default'" @click="submit">{{ confirmText }}</Button>
        </div>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>
<style>
.dialog-content.compact{width:400px}.prompt-field{display:block;margin-top:18px}.prompt-field span{display:block;margin-bottom:7px;font-size:12.5px;font-weight:560}.dialog-actions{display:flex;justify-content:flex-end;gap:8px;margin-top:22px}
</style>
