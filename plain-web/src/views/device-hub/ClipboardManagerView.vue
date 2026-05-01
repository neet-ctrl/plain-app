<template>
  <div class="page-root">
    <div class="page-header">
      <button class="back-btn" @click="$router.back()"><i-lucide:arrow-left /></button>
      <h2 class="page-title">Clipboard Manager</h2>
    </div>
    <div class="info-banner">
      <i-lucide:info class="info-icon" />
      <span>Read, write, and clear the device clipboard remotely from this panel.</span>
    </div>
    <div class="current-card">
      <div class="cc-header">
        <span class="cc-label">Current Clipboard Content</span>
        <button class="icon-btn" @click="readClipboard" title="Refresh"><i-lucide:refresh-cw /></button>
        <button class="icon-btn danger" @click="clearClip" title="Clear"><i-lucide:trash-2 /></button>
      </div>
      <div class="clip-content" v-if="clipText">{{ clipText }}</div>
      <div class="clip-empty" v-else>
        <i-lucide:clipboard /> Clipboard is empty or could not be read
      </div>
      <div class="char-count" v-if="clipText">{{ clipText.length }} characters</div>
    </div>
    <div class="set-card">
      <div class="sc-label">Set New Clipboard Content</div>
      <textarea v-model="newText" rows="4" placeholder="Type or paste text to push to device clipboard..." class="clip-textarea" />
      <button class="set-btn" @click="setClip" :disabled="!newText.trim()">
        <i-lucide:clipboard-copy /> Push to Device Clipboard
      </button>
    </div>
    <div v-if="success" class="success-box"><i-lucide:check-circle /> {{ success }}</div>
    <div v-if="error" class="error-box"><i-lucide:alert-triangle /> {{ error }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import { clipboardTextGQL } from '@/lib/api/query'
import { setClipboardTextGQL, clearClipboardGQL } from '@/lib/api/mutation'

const clipText = ref('')
const newText = ref('')
const error = ref('')
const success = ref('')

async function readClipboard() {
  error.value = ''; success.value = ''
  try {
    const r = await gqlFetch<{ clipboardText: string }>(clipboardTextGQL)
    if (r.errors?.length) { error.value = r.errors[0].message; return }
    clipText.value = r.data.clipboardText
  } catch { error.value = 'Could not reach device.' }
}

async function setClip() {
  if (!newText.value.trim()) return
  error.value = ''; success.value = ''
  try {
    const r = await gqlFetch<{ setClipboardText: boolean }>(setClipboardTextGQL, { text: newText.value })
    if (r.errors?.length) { error.value = r.errors[0].message; return }
    success.value = 'Clipboard updated on device!'
    clipText.value = newText.value
    newText.value = ''
    setTimeout(() => success.value = '', 3000)
  } catch { error.value = 'Could not reach device.' }
}

async function clearClip() {
  error.value = ''; success.value = ''
  try {
    await gqlFetch(clearClipboardGQL)
    clipText.value = ''
    success.value = 'Clipboard cleared!'
    setTimeout(() => success.value = '', 3000)
  } catch { error.value = 'Could not reach device.' }
}

onMounted(readClipboard)
</script>

<style scoped lang="scss">
.page-root { padding: 18px 20px 32px; display: flex; flex-direction: column; gap: 16px; max-width: 700px; margin: 0 auto; }
.page-header { display: flex; align-items: center; gap: 12px; }
.back-btn { background: none; border: none; cursor: pointer; color: var(--md-sys-color-on-surface); display: flex; align-items: center; padding: 6px; border-radius: 50%; transition: background 0.18s; &:hover { background: var(--md-sys-color-surface-container); } svg { width: 22px; height: 22px; } }
.page-title { font-size: 1.4rem; font-weight: 700; margin: 0; flex: 1; }
.info-banner { display: flex; align-items: flex-start; gap: 10px; padding: 14px 16px; border-radius: 14px; border: 1px solid rgba(99,102,241,0.3); background: rgba(99,102,241,0.06); color: var(--md-sys-color-on-surface-variant); font-size: 0.88rem; line-height: 1.5; }
.info-icon { width: 18px; height: 18px; color: #6366f1; flex-shrink: 0; margin-top: 2px; }
.current-card { background: var(--md-sys-color-surface-container); border-radius: 18px; padding: 18px; }
.cc-header { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.cc-label { flex: 1; font-weight: 600; font-size: 0.9rem; }
.icon-btn { background: none; border: none; cursor: pointer; color: var(--md-sys-color-on-surface-variant); display: flex; align-items: center; padding: 6px; border-radius: 50%; transition: background 0.15s; &:hover { background: var(--md-sys-color-surface-container-high); } &.danger:hover { background: rgba(239,68,68,0.1); color: #ef4444; } svg { width: 18px; height: 18px; } }
.clip-content { font-size: 0.9rem; line-height: 1.6; white-space: pre-wrap; word-break: break-all; max-height: 180px; overflow-y: auto; padding: 10px; background: var(--md-sys-color-surface-container-high); border-radius: 10px; }
.clip-empty { display: flex; align-items: center; gap: 8px; color: var(--md-sys-color-on-surface-variant); padding: 16px 10px; svg { width: 18px; height: 18px; } }
.char-count { font-size: 0.75rem; color: var(--md-sys-color-on-surface-variant); text-align: right; margin-top: 6px; }
.set-card { background: var(--md-sys-color-surface-container); border-radius: 18px; padding: 18px; display: flex; flex-direction: column; gap: 12px; }
.sc-label { font-weight: 600; font-size: 0.9rem; }
.clip-textarea { border: 1px solid var(--md-sys-color-outline-variant); border-radius: 10px; padding: 10px 12px; font-size: 0.9rem; background: var(--md-sys-color-surface); color: var(--md-sys-color-on-surface); resize: vertical; font-family: inherit; &:focus { outline: 2px solid #6366f1; outline-offset: -1px; } }
.set-btn { display: flex; align-items: center; gap: 8px; padding: 10px 18px; background: #6366f1; color: #fff; border: none; border-radius: 10px; cursor: pointer; font-size: 0.9rem; font-weight: 600; transition: background 0.2s; align-self: flex-start; &:hover:not(:disabled) { background: #4f46e5; } &:disabled { opacity: 0.5; cursor: not-allowed; } svg { width: 18px; height: 18px; } }
.error-box { display: flex; align-items: center; gap: 10px; padding: 14px; border-radius: 14px; font-size: 0.9rem; background: rgba(239,68,68,0.08); color: #ef4444; svg { width: 18px; height: 18px; } }
.success-box { display: flex; align-items: center; gap: 10px; padding: 14px; border-radius: 14px; font-size: 0.9rem; background: rgba(34,197,94,0.08); color: #16a34a; svg { width: 18px; height: 18px; } }
</style>
