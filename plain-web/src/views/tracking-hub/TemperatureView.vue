<template>
  <div class="sensor-root">
    <div class="sensor-header">
      <button class="back-btn" @click="$router.back()"><i-lucide:arrow-left /></button>
      <h2 class="sensor-title">Ambient Temperature</h2>
      <div class="live-badge" :class="{ active: polling }"><span class="dot" />{{ polling ? 'Live' : '—' }}</div>
    </div>
    <div v-if="!supported && checked" class="warn-box"><i-lucide:alert-triangle /> This device does not have an ambient temperature sensor. (Only a few Samsung and specialized devices support this.)</div>
    <div v-else>
      <div class="info-banner">
        <i-lucide:info class="info-icon" />
        <span>Reads the ambient air temperature around the device in °C. Most phones do NOT have this sensor.</span>
      </div>
      <div class="temp-card">
        <div class="thermometer-icon">🌡️</div>
        <div class="temp-value" :style="{ color: tempColor }">{{ tempC }}</div>
        <div class="temp-unit">°C</div>
        <div class="temp-f">{{ tempF }} °F</div>
        <div class="temp-label" :style="{ color: tempColor }">{{ tempLabel }}</div>
      </div>
      <div class="scale-bar">
        <div class="scale-fill" :style="{ width: scalePct + '%', background: tempColor }"></div>
        <div class="scale-marks">
          <span>0°C</span><span>10°C</span><span>20°C</span><span>30°C</span><span>40°C</span>
        </div>
      </div>
      <div v-if="error" class="error-box"><i-lucide:alert-triangle />{{ error }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import { ambientTemperatureGQL, temperatureSupportedGQL } from '@/lib/api/query'

const polling = ref(false)
const supported = ref(true)
const checked = ref(false)
const error = ref('')
const temp = ref(0)
let timer = 0

const tempC = computed(() => temp.value === -274 ? '—' : temp.value.toFixed(1))
const tempF = computed(() => temp.value === -274 ? '—' : (temp.value * 9/5 + 32).toFixed(1))
const scalePct = computed(() => Math.min(100, Math.max(0, (temp.value / 45) * 100)))
const tempColor = computed(() => {
  const t = temp.value
  if (t < 10) return '#3b82f6'
  if (t < 20) return '#22c55e'
  if (t < 28) return '#f59e0b'
  if (t < 35) return '#f97316'
  return '#ef4444'
})
const tempLabel = computed(() => {
  const t = temp.value
  if (t < 0) return 'Freezing'
  if (t < 10) return 'Cold'
  if (t < 18) return 'Cool'
  if (t < 24) return 'Comfortable'
  if (t < 28) return 'Warm'
  if (t < 33) return 'Hot'
  return 'Very Hot'
})

async function poll() {
  try {
    if (!checked.value) {
      const s = await gqlFetch<{ temperatureSupported: boolean }>(temperatureSupportedGQL)
      supported.value = s.data.temperatureSupported
      checked.value = true
      if (!supported.value) return
    }
    const r = await gqlFetch<{ ambientTemperature: number }>(ambientTemperatureGQL)
    if (r.errors?.length) { error.value = r.errors[0].message; return }
    const val = r.data.ambientTemperature
    if (val <= -273) { supported.value = false; return }
    temp.value = val
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
.temp-card { background: var(--md-sys-color-surface-container); border-radius: 24px; padding: 32px 20px; text-align: center; }
.thermometer-icon { font-size: 2.5rem; }
.temp-value { font-size: 4rem; font-weight: 800; transition: color 0.4s; }
.temp-unit { font-size: 1.2rem; color: var(--md-sys-color-on-surface-variant); }
.temp-f { color: var(--md-sys-color-on-surface-variant); font-size: 1rem; margin-top: 4px; }
.temp-label { font-size: 1.4rem; font-weight: 700; margin-top: 8px; }
.scale-bar { background: var(--md-sys-color-surface-container); border-radius: 12px; padding: 14px; }
.scale-fill { height: 8px; border-radius: 999px; transition: width 0.5s ease, background 0.4s; }
.scale-marks { display: flex; justify-content: space-between; margin-top: 6px; font-size: 0.72rem; color: var(--md-sys-color-on-surface-variant); }
.warn-box, .error-box { display: flex; align-items: center; gap: 10px; padding: 14px; border-radius: 14px; font-size: 0.9rem; svg { width: 18px; height: 18px; } }
.warn-box { background: rgba(245,158,11,0.09); color: #d97706; }
.error-box { background: rgba(239,68,68,0.08); color: #ef4444; }
</style>
