<template>
  <div class="page-root">
    <div class="page-header">
      <button class="back-btn" @click="$router.back()"><i-lucide:arrow-left /></button>
      <h2 class="page-title">Lock / Power Controls</h2>
    </div>
    <div class="warn-banner">
      <i-lucide:shield-alert />
      <div>
        <div class="wb-title">Device Admin Required</div>
        <div class="wb-text">Lock Screen requires Device Admin permission. Reboot requires root or shell access. Use with caution.</div>
      </div>
    </div>
    <div class="action-grid">
      <div class="action-card" v-for="a in actions" :key="a.id">
        <div class="ac-icon">{{ a.icon }}</div>
        <div class="ac-name">{{ a.name }}</div>
        <div class="ac-desc">{{ a.desc }}</div>
        <button class="ac-btn" :class="a.color" @click="runAction(a)">{{ a.btnLabel }}</button>
      </div>
    </div>
    <div v-if="result" class="result-box" :class="resultType"><i-lucide:check-circle v-if="resultType==='ok'" /><i-lucide:alert-triangle v-else />{{ result }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import { lockScreenGQL, rebootDeviceGQL } from '@/lib/api/mutation'

const result = ref('')
const resultType = ref<'ok' | 'err'>('ok')

const actions = [
  { id: 'lock', icon: '🔒', name: 'Lock Screen', desc: 'Immediately lock the device screen. Requires Device Admin.', color: 'blue', btnLabel: 'Lock Now', gql: lockScreenGQL },
  { id: 'reboot', icon: '🔄', name: 'Reboot Device', desc: 'Reboot the device. Requires root or shell (adb) access.', color: 'orange', btnLabel: 'Reboot', gql: rebootDeviceGQL },
]

async function runAction(a: typeof actions[0]) {
  if (!confirm(`Execute "${a.name}"? This action affects the device immediately.`)) return
  result.value = ''
  try {
    const r = await gqlFetch<Record<string, boolean>>(a.gql)
    if (r.errors?.length) { result.value = r.errors[0].message; resultType.value = 'err'; return }
    result.value = `"${a.name}" executed successfully.`
    resultType.value = 'ok'
  } catch { result.value = 'Could not reach device.'; resultType.value = 'err' }
  setTimeout(() => result.value = '', 4000)
}
</script>

<style scoped lang="scss">
.page-root { padding: 18px 20px 32px; display: flex; flex-direction: column; gap: 16px; max-width: 700px; margin: 0 auto; }
.page-header { display: flex; align-items: center; gap: 12px; }
.back-btn { background: none; border: none; cursor: pointer; color: var(--md-sys-color-on-surface); display: flex; align-items: center; padding: 6px; border-radius: 50%; transition: background 0.18s; &:hover { background: var(--md-sys-color-surface-container); } svg { width: 22px; height: 22px; } }
.page-title { font-size: 1.4rem; font-weight: 700; margin: 0; flex: 1; }
.warn-banner { display: flex; gap: 12px; padding: 14px 16px; border-radius: 14px; background: rgba(245,158,11,0.1); border: 1px solid rgba(245,158,11,0.4); color: #d97706; svg { width: 22px; height: 22px; flex-shrink: 0; margin-top: 2px; } }
.wb-title { font-weight: 700; font-size: 0.9rem; margin-bottom: 4px; }
.wb-text { font-size: 0.83rem; line-height: 1.5; }
.action-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.action-card { background: var(--md-sys-color-surface-container); border-radius: 20px; padding: 22px 16px; display: flex; flex-direction: column; align-items: center; gap: 8px; text-align: center; }
.ac-icon { font-size: 2.5rem; }
.ac-name { font-size: 1rem; font-weight: 700; }
.ac-desc { font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); line-height: 1.4; }
.ac-btn { margin-top: 8px; padding: 10px 20px; border: none; border-radius: 10px; cursor: pointer; font-weight: 600; font-size: 0.88rem; transition: background 0.2s; &.blue { background: #3b82f6; color: #fff; &:hover { background: #2563eb; } } &.orange { background: #f97316; color: #fff; &:hover { background: #ea580c; } } }
.result-box { display: flex; align-items: center; gap: 10px; padding: 14px; border-radius: 14px; font-size: 0.9rem; svg { width: 18px; height: 18px; } &.ok { background: rgba(34,197,94,0.08); color: #16a34a; } &.err { background: rgba(239,68,68,0.08); color: #ef4444; } }
</style>
