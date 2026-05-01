<template>
  <div class="sensor-root">
    <div class="sensor-header">
      <button class="back-btn" @click="$router.back()"><i-lucide:arrow-left /></button>
      <h2 class="sensor-title">Pedometer</h2>
      <div class="live-badge" :class="{ active: polling }"><span class="dot" />{{ polling ? 'Live' : '—' }}</div>
    </div>
    <div v-if="!supported && checked" class="warn-box"><i-lucide:alert-triangle /> This device does not have a step counter sensor.</div>
    <div v-else>
      <div class="info-banner">
        <i-lucide:info class="info-icon" />
        <span>Counts steps since the last device reboot using the hardware step counter sensor. Estimates distance and calories burned.</span>
      </div>
      <div class="step-ring">
        <svg viewBox="0 0 200 200" class="ring-svg">
          <circle cx="100" cy="100" r="85" fill="none" stroke="var(--md-sys-color-surface-container-high)" stroke-width="14"/>
          <circle cx="100" cy="100" r="85" fill="none" stroke="#22c55e" stroke-width="14"
            stroke-linecap="round"
            :stroke-dasharray="534"
            :stroke-dashoffset="ringOffset"
            transform="rotate(-90 100 100)"
            style="transition: stroke-dashoffset 0.6s ease"
          />
        </svg>
        <div class="ring-center">
          <div class="ring-steps">{{ stepCount < 0 ? '—' : stepCount.toLocaleString() }}</div>
          <div class="ring-label">steps</div>
        </div>
      </div>
      <div class="stat-row">
        <div class="mini-stat"><div class="ms-value">{{ distanceM }}</div><div class="ms-label">meters walked</div></div>
        <div class="mini-stat"><div class="ms-value">{{ kcal }}</div><div class="ms-label">kcal burned</div></div>
        <div class="mini-stat"><div class="ms-value">{{ stepCount < 0 ? '—' : Math.round(stepCount * 0.762 / 1000 * 10) / 10 }}</div><div class="ms-label">km walked</div></div>
      </div>
      <div class="wait-note" v-if="stepCount < 0">
        <i-lucide:loader-circle class="spin" /> Waiting for first step event. Walk with the phone...
      </div>
      <div v-if="error" class="error-box"><i-lucide:alert-triangle />{{ error }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import { stepCountGQL, pedometerSupportedGQL } from '@/lib/api/query'

const polling = ref(false)
const supported = ref(true)
const checked = ref(false)
const error = ref('')
const stepCount = ref(-1)
let timer = 0

const GOAL = 10000
const ringOffset = computed(() => {
  if (stepCount.value < 0) return 534
  return 534 * (1 - Math.min(1, stepCount.value / GOAL))
})
const distanceM = computed(() => stepCount.value < 0 ? '—' : Math.round(stepCount.value * 0.762).toLocaleString())
const kcal = computed(() => stepCount.value < 0 ? '—' : (stepCount.value * 0.04).toFixed(1))

async function poll() {
  try {
    if (!checked.value) {
      const s = await gqlFetch<{ pedometerSupported: boolean }>(pedometerSupportedGQL)
      supported.value = s.data.pedometerSupported
      checked.value = true
      if (!supported.value) return
    }
    const r = await gqlFetch<{ stepCount: number }>(stepCountGQL)
    if (r.errors?.length) { error.value = r.errors[0].message; return }
    stepCount.value = r.data.stepCount
    polling.value = true
  } catch { error.value = 'Could not reach device.'; polling.value = false }
}

onMounted(() => { poll(); timer = window.setInterval(poll, 1000) })
onUnmounted(() => clearInterval(timer))
</script>

<style scoped lang="scss">
.sensor-root { padding: 18px 20px 32px; display: flex; flex-direction: column; gap: 14px; max-width: 700px; margin: 0 auto; }
.sensor-header { display: flex; align-items: center; gap: 12px; }
.back-btn { background: none; border: none; cursor: pointer; color: var(--md-sys-color-on-surface); display: flex; align-items: center; padding: 6px; border-radius: 50%; transition: background 0.18s; &:hover { background: var(--md-sys-color-surface-container); } svg { width: 22px; height: 22px; } }
.sensor-title { font-size: 1.4rem; font-weight: 700; margin: 0; flex: 1; }
.live-badge { display: flex; align-items: center; gap: 6px; padding: 4px 12px; border-radius: 999px; background: var(--md-sys-color-surface-container); font-size: 0.78rem; font-weight: 600; color: var(--md-sys-color-on-surface-variant); .dot { width: 8px; height: 8px; border-radius: 50%; background: #9ca3af; } &.active { background: rgba(34,197,94,0.14); color: #16a34a; .dot { background: #22c55e; animation: pulse 1.4s infinite; } } }
@keyframes pulse { 0%,100%{opacity:1}50%{opacity:.4} }
.info-banner { display: flex; align-items: flex-start; gap: 10px; padding: 14px 16px; border-radius: 14px; border: 1px solid rgba(34,197,94,0.3); background: rgba(34,197,94,0.06); color: var(--md-sys-color-on-surface-variant); font-size: 0.88rem; line-height: 1.5; }
.info-icon { width: 18px; height: 18px; color: #22c55e; flex-shrink: 0; margin-top: 2px; }
.step-ring { position: relative; width: 220px; height: 220px; margin: 0 auto; }
.ring-svg { width: 100%; height: 100%; }
.ring-center { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; }
.ring-steps { font-size: 2.4rem; font-weight: 800; font-variant-numeric: tabular-nums; }
.ring-label { font-size: 0.88rem; color: var(--md-sys-color-on-surface-variant); }
.stat-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.mini-stat { background: var(--md-sys-color-surface-container); border-radius: 14px; padding: 14px 10px; text-align: center; }
.ms-value { font-size: 1.4rem; font-weight: 700; font-variant-numeric: tabular-nums; }
.ms-label { font-size: 0.72rem; color: var(--md-sys-color-on-surface-variant); margin-top: 2px; }
.wait-note { display: flex; align-items: center; gap: 8px; color: var(--md-sys-color-on-surface-variant); font-size: 0.88rem; padding: 10px 14px; background: var(--md-sys-color-surface-container); border-radius: 12px; }
.spin { animation: spin 1.2s linear infinite; width: 16px; height: 16px; }
@keyframes spin { to { transform: rotate(360deg); } }
.warn-box, .error-box { display: flex; align-items: center; gap: 10px; padding: 14px; border-radius: 14px; font-size: 0.9rem; svg { width: 18px; height: 18px; } }
.warn-box { background: rgba(245,158,11,0.09); color: #d97706; }
.error-box { background: rgba(239,68,68,0.08); color: #ef4444; }
</style>
