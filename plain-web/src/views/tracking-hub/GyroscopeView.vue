<template>
  <div class="sensor-root">
    <div class="sensor-header">
      <button class="back-btn" @click="$router.back()">
        <i-lucide:arrow-left />
      </button>
      <h2 class="sensor-title">Gyroscope</h2>
      <div class="live-badge" :class="{ active: polling }">
        <span class="dot" />{{ polling ? 'Live' : '—' }}
      </div>
    </div>
    <div v-if="!supported && checked" class="warn-box">
      <i-lucide:alert-triangle /> This device does not have a gyroscope sensor.
    </div>
    <div v-else>
      <div class="info-banner">
        <i-lucide:info class="info-icon" />
        <span>Measures the device's rotation rate in radians/second on 3 axes. Tells how fast the phone is spinning.</span>
      </div>
      <div class="axis-grid">
        <div class="axis-card" v-for="ax in axes" :key="ax.label">
          <div class="axis-label">{{ ax.label }}</div>
          <div class="axis-name">{{ ax.name }}</div>
          <div class="axis-value" :style="{ color: ax.color }">{{ ax.value.toFixed(3) }}</div>
          <div class="axis-unit">rad/s</div>
          <div class="axis-bar-track">
            <div class="axis-bar" :style="{ width: ax.pct + '%', background: ax.color }" />
          </div>
        </div>
      </div>
      <div class="mag-card">
        <div class="mag-label">Rotation Magnitude</div>
        <div class="mag-value">{{ data.magnitude.toFixed(3) }} <span class="unit">rad/s</span></div>
        <div class="mag-desc">{{ magnitudeLabel }}</div>
      </div>
      <div v-if="error" class="error-box"><i-lucide:alert-triangle />{{ error }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import { gyroscopeDataGQL, gyroscopeSupportedGQL } from '@/lib/api/query'

const polling = ref(false)
const supported = ref(true)
const checked = ref(false)
const error = ref('')
let timer = 0

const data = ref({ x: 0, y: 0, z: 0, magnitude: 0 })

const axes = computed(() => [
  { label: 'X', name: 'Roll',  value: data.value.x, color: '#ef4444', pct: Math.min(100, (Math.abs(data.value.x) / 10) * 100) },
  { label: 'Y', name: 'Pitch', value: data.value.y, color: '#22c55e', pct: Math.min(100, (Math.abs(data.value.y) / 10) * 100) },
  { label: 'Z', name: 'Yaw',   value: data.value.z, color: '#3b82f6', pct: Math.min(100, (Math.abs(data.value.z) / 10) * 100) },
])

const magnitudeLabel = computed(() => {
  const m = data.value.magnitude
  if (m < 0.05) return 'Stationary'
  if (m < 0.5) return 'Slight Rotation'
  if (m < 2) return 'Moderate Rotation'
  if (m < 5) return 'Fast Rotation'
  return 'Very Fast Rotation'
})

async function poll() {
  try {
    if (!checked.value) {
      const s = await gqlFetch<{ gyroscopeSupported: boolean }>(gyroscopeSupportedGQL)
      supported.value = s.data.gyroscopeSupported
      checked.value = true
      if (!supported.value) return
    }
    const r = await gqlFetch<{ gyroscopeData: typeof data.value }>(gyroscopeDataGQL)
    if (r.errors?.length) { error.value = r.errors[0].message; return }
    data.value = r.data.gyroscopeData
    polling.value = true
  } catch {
    error.value = 'Could not reach device.'
    polling.value = false
  }
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
.axis-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.axis-card { background: var(--md-sys-color-surface-container); border-radius: 16px; padding: 16px 12px; display: flex; flex-direction: column; align-items: center; gap: 4px; }
.axis-label { font-size: 1.2rem; font-weight: 800; }
.axis-name { font-size: 0.75rem; color: var(--md-sys-color-on-surface-variant); }
.axis-value { font-size: 1.5rem; font-weight: 700; font-variant-numeric: tabular-nums; }
.axis-unit { font-size: 0.7rem; color: var(--md-sys-color-on-surface-variant); }
.axis-bar-track { width: 100%; height: 4px; border-radius: 999px; background: var(--md-sys-color-surface-container-high); margin-top: 6px; overflow: hidden; }
.axis-bar { height: 100%; border-radius: 999px; transition: width 0.2s ease; }
.mag-card { background: var(--md-sys-color-surface-container); border-radius: 16px; padding: 18px; text-align: center; }
.mag-label { font-size: 0.85rem; color: var(--md-sys-color-on-surface-variant); margin-bottom: 6px; }
.mag-value { font-size: 2rem; font-weight: 800; .unit { font-size: 0.9rem; font-weight: 400; color: var(--md-sys-color-on-surface-variant); } }
.mag-desc { font-size: 0.9rem; color: var(--md-sys-color-on-surface-variant); margin-top: 4px; }
.warn-box, .error-box { display: flex; align-items: center; gap: 10px; padding: 14px; border-radius: 14px; font-size: 0.9rem; svg { width: 18px; height: 18px; } }
.warn-box { background: rgba(245,158,11,0.09); color: #d97706; }
.error-box { background: rgba(239,68,68,0.08); color: #ef4444; }
</style>
