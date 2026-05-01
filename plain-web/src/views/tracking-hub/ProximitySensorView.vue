<template>
  <div class="sensor-root">
    <div class="sensor-header">
      <button class="back-btn" @click="$router.back()"><i-lucide:arrow-left /></button>
      <h2 class="sensor-title">Proximity Sensor</h2>
      <div class="live-badge" :class="{ active: polling }"><span class="dot" />{{ polling ? 'Live' : '—' }}</div>
    </div>
    <div v-if="!data.supported && checked" class="warn-box"><i-lucide:alert-triangle /> This device does not have a proximity sensor.</div>
    <div v-else>
      <div class="info-banner">
        <i-lucide:info class="info-icon" />
        <span>Detects whether an object is near or far from the device's front sensor (typically used to turn off screen during calls).</span>
      </div>
      <div class="prox-indicator" :class="data.near ? 'near' : 'far'">
        <div class="prox-icon">{{ data.near ? '🔴' : '🟢' }}</div>
        <div class="prox-state">{{ data.near ? 'NEAR' : 'FAR' }}</div>
        <div class="prox-desc">{{ data.near ? 'Object detected close to sensor' : 'No object nearby' }}</div>
        <div class="prox-dist" v-if="data.distanceCm >= 0">
          Distance: <b>{{ data.distanceCm }}</b> cm &nbsp;|&nbsp; Max: <b>{{ data.maxRangeCm }}</b> cm
        </div>
      </div>
      <div class="use-cases">
        <div class="uc-title">Common Uses</div>
        <div class="uc-item">📵 Screen off during phone calls</div>
        <div class="uc-item">🙌 Gesture detection</div>
        <div class="uc-item">💡 Auto-brightness based on distance</div>
        <div class="uc-item">🔒 Pocket detection / accidental touch prevention</div>
      </div>
      <div v-if="error" class="error-box"><i-lucide:alert-triangle />{{ error }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import { proximityDataGQL } from '@/lib/api/query'

const polling = ref(false)
const checked = ref(false)
const error = ref('')
const data = ref({ near: false, distanceCm: -1, maxRangeCm: 1, supported: true })
let timer = 0

async function poll() {
  try {
    const r = await gqlFetch<{ proximityData: typeof data.value }>(proximityDataGQL)
    if (r.errors?.length) { error.value = r.errors[0].message; return }
    data.value = r.data.proximityData
    checked.value = true
    polling.value = true
  } catch { error.value = 'Could not reach device.'; polling.value = false }
}

onMounted(() => { poll(); timer = window.setInterval(poll, 300) })
onUnmounted(() => clearInterval(timer))
</script>

<style scoped lang="scss">
.sensor-root { padding: 18px 20px 32px; display: flex; flex-direction: column; gap: 14px; max-width: 700px; margin: 0 auto; }
.sensor-header { display: flex; align-items: center; gap: 12px; }
.back-btn { background: none; border: none; cursor: pointer; color: var(--md-sys-color-on-surface); display: flex; align-items: center; padding: 6px; border-radius: 50%; transition: background 0.18s; &:hover { background: var(--md-sys-color-surface-container); } svg { width: 22px; height: 22px; } }
.sensor-title { font-size: 1.4rem; font-weight: 700; margin: 0; flex: 1; }
.live-badge { display: flex; align-items: center; gap: 6px; padding: 4px 12px; border-radius: 999px; background: var(--md-sys-color-surface-container); font-size: 0.78rem; font-weight: 600; color: var(--md-sys-color-on-surface-variant); .dot { width: 8px; height: 8px; border-radius: 50%; background: #9ca3af; } &.active { background: rgba(34,197,94,0.14); color: #16a34a; .dot { background: #22c55e; animation: pulse 1.4s infinite; } } }
@keyframes pulse { 0%,100%{opacity:1}50%{opacity:.4} }
.info-banner { display: flex; align-items: flex-start; gap: 10px; padding: 14px 16px; border-radius: 14px; border: 1px solid rgba(99,102,241,0.3); background: rgba(99,102,241,0.06); color: var(--md-sys-color-on-surface-variant); font-size: 0.88rem; line-height: 1.5; }
.info-icon { width: 18px; height: 18px; color: #6366f1; flex-shrink: 0; margin-top: 2px; }
.prox-indicator { border-radius: 24px; padding: 32px 20px; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 8px; transition: all 0.3s; &.near { background: rgba(239,68,68,0.12); border: 2px solid rgba(239,68,68,0.4); } &.far { background: rgba(34,197,94,0.10); border: 2px solid rgba(34,197,94,0.3); } }
.prox-icon { font-size: 3rem; }
.prox-state { font-size: 2.2rem; font-weight: 800; .near & { color: #ef4444; } .far & { color: #22c55e; } }
.prox-desc { color: var(--md-sys-color-on-surface-variant); }
.prox-dist { font-size: 0.85rem; color: var(--md-sys-color-on-surface-variant); margin-top: 4px; }
.use-cases { background: var(--md-sys-color-surface-container); border-radius: 16px; padding: 16px; }
.uc-title { font-size: 0.85rem; font-weight: 600; color: var(--md-sys-color-on-surface-variant); margin-bottom: 10px; }
.uc-item { padding: 6px 0; font-size: 0.88rem; border-bottom: 1px solid var(--md-sys-color-outline-variant); &:last-child { border-bottom: none; } }
.warn-box, .error-box { display: flex; align-items: center; gap: 10px; padding: 14px; border-radius: 14px; font-size: 0.9rem; svg { width: 18px; height: 18px; } }
.warn-box { background: rgba(245,158,11,0.09); color: #d97706; }
.error-box { background: rgba(239,68,68,0.08); color: #ef4444; }
</style>
