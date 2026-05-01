<template>
  <div class="sensor-root">
    <div class="sensor-header">
      <button class="back-btn" @click="$router.back()"><i-lucide:arrow-left /></button>
      <h2 class="sensor-title">Heart Rate Monitor</h2>
      <div class="live-badge" :class="{ active: polling }"><span class="dot" />{{ polling ? 'Live' : '—' }}</div>
    </div>
    <div v-if="!supported && checked" class="warn-box"><i-lucide:alert-triangle /> This device does not have a heart rate sensor. (Available on select Samsung and Wear OS devices.)</div>
    <div v-else>
      <div class="info-banner">
        <i-lucide:info class="info-icon" />
        <span>Reads heart rate in BPM via the optical heart rate sensor. Available on Samsung Galaxy S series and Wear OS devices.</span>
      </div>
      <div class="hr-card">
        <div class="heart-beat" :class="{ beating: polling && bpm > 0 }">❤️</div>
        <div class="bpm-value" :style="{ color: bpmColor }">{{ bpm > 0 ? bpm.toFixed(0) : '—' }}</div>
        <div class="bpm-unit">BPM</div>
        <div class="bpm-zone" :style="{ color: bpmColor }">{{ zoneLabel }}</div>
      </div>
      <div class="zone-list">
        <div class="zone-row" v-for="z in zones" :key="z.name" :class="{ active: bpm >= z.min && bpm < z.max }">
          <div class="zone-dot" :style="{ background: z.color }"></div>
          <span class="zone-name">{{ z.name }}</span>
          <span class="zone-range">{{ z.min }}–{{ z.max === Infinity ? '∞' : z.max }} BPM</span>
        </div>
      </div>
      <div v-if="error" class="error-box"><i-lucide:alert-triangle />{{ error }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import { heartRateGQL, heartRateSupportedGQL } from '@/lib/api/query'

const polling = ref(false)
const supported = ref(true)
const checked = ref(false)
const error = ref('')
const bpm = ref(0)
let timer = 0

const zones = [
  { name: 'Rest', min: 0, max: 60, color: '#3b82f6' },
  { name: 'Fat Burn', min: 60, max: 100, color: '#22c55e' },
  { name: 'Cardio', min: 100, max: 140, color: '#f59e0b' },
  { name: 'Peak', min: 140, max: 170, color: '#f97316' },
  { name: 'Maximum', min: 170, max: Infinity, color: '#ef4444' },
]

const currentZone = computed(() => zones.find(z => bpm.value >= z.min && bpm.value < z.max))
const bpmColor = computed(() => currentZone.value?.color ?? '#6366f1')
const zoneLabel = computed(() => currentZone.value?.name ?? 'No Reading')

async function poll() {
  try {
    if (!checked.value) {
      const s = await gqlFetch<{ heartRateSupported: boolean }>(heartRateSupportedGQL)
      supported.value = s.data.heartRateSupported
      checked.value = true
      if (!supported.value) return
    }
    const r = await gqlFetch<{ heartRate: number }>(heartRateGQL)
    if (r.errors?.length) { error.value = r.errors[0].message; return }
    const val = r.data.heartRate
    if (val < 0) { supported.value = false; return }
    bpm.value = val
    polling.value = true
  } catch { error.value = 'Could not reach device.'; polling.value = false }
}

onMounted(() => { poll(); timer = window.setInterval(poll, 2000) })
onUnmounted(() => clearInterval(timer))
</script>

<style scoped lang="scss">
.sensor-root { padding: 18px 20px 32px; display: flex; flex-direction: column; gap: 14px; max-width: 700px; margin: 0 auto; }
.sensor-header { display: flex; align-items: center; gap: 12px; }
.back-btn { background: none; border: none; cursor: pointer; color: var(--md-sys-color-on-surface); display: flex; align-items: center; padding: 6px; border-radius: 50%; transition: background 0.18s; &:hover { background: var(--md-sys-color-surface-container); } svg { width: 22px; height: 22px; } }
.sensor-title { font-size: 1.4rem; font-weight: 700; margin: 0; flex: 1; }
.live-badge { display: flex; align-items: center; gap: 6px; padding: 4px 12px; border-radius: 999px; background: var(--md-sys-color-surface-container); font-size: 0.78rem; font-weight: 600; color: var(--md-sys-color-on-surface-variant); .dot { width: 8px; height: 8px; border-radius: 50%; background: #9ca3af; } &.active { background: rgba(34,197,94,0.14); color: #16a34a; .dot { background: #22c55e; animation: pulse 1.4s infinite; } } }
@keyframes pulse { 0%,100%{opacity:1}50%{opacity:.4} }
.info-banner { display: flex; align-items: flex-start; gap: 10px; padding: 14px 16px; border-radius: 14px; border: 1px solid rgba(239,68,68,0.3); background: rgba(239,68,68,0.06); color: var(--md-sys-color-on-surface-variant); font-size: 0.88rem; line-height: 1.5; }
.info-icon { width: 18px; height: 18px; color: #ef4444; flex-shrink: 0; margin-top: 2px; }
.hr-card { background: var(--md-sys-color-surface-container); border-radius: 24px; padding: 32px 20px; text-align: center; }
.heart-beat { font-size: 3rem; transition: transform 0.1s; display: inline-block; &.beating { animation: heartbeat 0.8s infinite; } }
@keyframes heartbeat { 0%,100% { transform: scale(1); } 15% { transform: scale(1.3); } 30% { transform: scale(1); } 45% { transform: scale(1.2); } }
.bpm-value { font-size: 4rem; font-weight: 800; transition: color 0.4s; }
.bpm-unit { font-size: 1.2rem; color: var(--md-sys-color-on-surface-variant); }
.bpm-zone { font-size: 1.4rem; font-weight: 700; margin-top: 8px; }
.zone-list { background: var(--md-sys-color-surface-container); border-radius: 16px; padding: 14px; display: flex; flex-direction: column; gap: 6px; }
.zone-row { display: flex; align-items: center; gap: 10px; padding: 8px 10px; border-radius: 10px; transition: background 0.2s; &.active { background: rgba(255,255,255,0.07); font-weight: 600; .zone-dot { transform: scale(1.4); } } }
.zone-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; transition: transform 0.2s; }
.zone-name { flex: 1; }
.zone-range { font-size: 0.82rem; color: var(--md-sys-color-on-surface-variant); font-variant-numeric: tabular-nums; }
.warn-box, .error-box { display: flex; align-items: center; gap: 10px; padding: 14px; border-radius: 14px; font-size: 0.9rem; svg { width: 18px; height: 18px; } }
.warn-box { background: rgba(245,158,11,0.09); color: #d97706; }
.error-box { background: rgba(239,68,68,0.08); color: #ef4444; }
</style>
