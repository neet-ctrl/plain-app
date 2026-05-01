<template>
  <div class="sensor-root">
    <div class="sensor-header">
      <button class="back-btn" @click="$router.back()"><i-lucide:arrow-left /></button>
      <h2 class="sensor-title">Barometer</h2>
      <div class="live-badge" :class="{ active: polling }">
        <span class="dot" />{{ polling ? 'Live' : '—' }}
      </div>
    </div>
    <div v-if="!supported && checked" class="warn-box">
      <i-lucide:alert-triangle /> This device does not have a barometric pressure sensor.
    </div>
    <div v-else>
      <div class="info-banner">
        <i-lucide:info class="info-icon" />
        <span>Reads atmospheric pressure in hPa and calculates estimated altitude above sea level.</span>
      </div>
      <div class="stat-grid">
        <div class="stat-card">
          <div class="stat-icon" style="color:#6366f1">🌡</div>
          <div class="stat-value">{{ data.pressureHpa.toFixed(1) }}</div>
          <div class="stat-unit">hPa</div>
          <div class="stat-label">Atmospheric Pressure</div>
        </div>
        <div class="stat-card">
          <div class="stat-icon" style="color:#22c55e">⛰️</div>
          <div class="stat-value">{{ data.altitudeMeters.toFixed(0) }}</div>
          <div class="stat-unit">m</div>
          <div class="stat-label">Estimated Altitude</div>
        </div>
      </div>
      <div class="ref-card">
        <div class="ref-title">Reference Values</div>
        <div class="ref-row"><span>Sea Level</span><span>1013.25 hPa</span></div>
        <div class="ref-row"><span>Current</span><span :style="{color: pressureColor}">{{ data.pressureHpa.toFixed(2) }} hPa ({{ delta }})</span></div>
        <div class="ref-row"><span>Condition</span><span :style="{color: conditionColor}">{{ weatherCondition }}</span></div>
      </div>
      <div v-if="error" class="error-box"><i-lucide:alert-triangle />{{ error }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import { barometerDataGQL, barometerSupportedGQL } from '@/lib/api/query'

const polling = ref(false)
const supported = ref(true)
const checked = ref(false)
const error = ref('')
let timer = 0
const data = ref({ pressureHpa: 0, altitudeMeters: 0 })

const SEA_LEVEL = 1013.25
const delta = computed(() => {
  const d = data.value.pressureHpa - SEA_LEVEL
  return (d >= 0 ? '+' : '') + d.toFixed(1) + ' hPa'
})
const pressureColor = computed(() => {
  const p = data.value.pressureHpa
  if (p < 980) return '#ef4444'
  if (p > 1020) return '#22c55e'
  return '#6366f1'
})
const conditionColor = computed(() => pressureColor.value)
const weatherCondition = computed(() => {
  const p = data.value.pressureHpa
  if (p < 960) return '⛈ Storm'
  if (p < 980) return '🌧 Rain Likely'
  if (p < 1000) return '⛅ Cloudy'
  if (p < 1020) return '🌤 Fair'
  return '☀️ Clear & Dry'
})

async function poll() {
  try {
    if (!checked.value) {
      const s = await gqlFetch<{ barometerSupported: boolean }>(barometerSupportedGQL)
      supported.value = s.data.barometerSupported
      checked.value = true
      if (!supported.value) return
    }
    const r = await gqlFetch<{ barometerData: typeof data.value }>(barometerDataGQL)
    if (r.errors?.length) { error.value = r.errors[0].message; return }
    data.value = r.data.barometerData
    polling.value = true
  } catch {
    error.value = 'Could not reach device.'
    polling.value = false
  }
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
.info-banner { display: flex; align-items: flex-start; gap: 10px; padding: 14px 16px; border-radius: 14px; border: 1px solid rgba(99,102,241,0.3); background: rgba(99,102,241,0.06); color: var(--md-sys-color-on-surface-variant); font-size: 0.88rem; line-height: 1.5; }
.info-icon { width: 18px; height: 18px; color: #6366f1; flex-shrink: 0; margin-top: 2px; }
.stat-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.stat-card { background: var(--md-sys-color-surface-container); border-radius: 20px; padding: 22px 16px; display: flex; flex-direction: column; align-items: center; gap: 4px; }
.stat-icon { font-size: 2rem; }
.stat-value { font-size: 2.4rem; font-weight: 800; }
.stat-unit { font-size: 0.85rem; color: var(--md-sys-color-on-surface-variant); }
.stat-label { font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); text-align: center; margin-top: 4px; }
.ref-card { background: var(--md-sys-color-surface-container); border-radius: 16px; padding: 16px; }
.ref-title { font-size: 0.85rem; font-weight: 600; color: var(--md-sys-color-on-surface-variant); margin-bottom: 10px; }
.ref-row { display: flex; justify-content: space-between; padding: 7px 0; border-bottom: 1px solid var(--md-sys-color-outline-variant); font-size: 0.88rem; &:last-child { border-bottom: none; } }
.warn-box, .error-box { display: flex; align-items: center; gap: 10px; padding: 14px; border-radius: 14px; font-size: 0.9rem; svg { width: 18px; height: 18px; } }
.warn-box { background: rgba(245,158,11,0.09); color: #d97706; }
.error-box { background: rgba(239,68,68,0.08); color: #ef4444; }
</style>
