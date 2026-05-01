<template>
  <div class="sensor-root">
    <div class="sensor-header">
      <button class="back-btn" @click="$router.back()">
        <i-lucide:arrow-left />
      </button>
      <h2 class="sensor-title">Vibrometer</h2>
      <div class="live-badge" :class="{ active: polling }">
        <span class="dot" />{{ polling ? 'Live' : 'Stopped' }}
      </div>
    </div>

    <div class="info-banner">
      <i-lucide:info class="info-icon" />
      <span>This tool helps to measure the vibration of device (reading from phone motion sensors).</span>
    </div>

    <div class="vib-value" :style="{ color: statusColor }">{{ vibration.toFixed(1) }}</div>
    <div class="vib-unit">m/s²</div>
    <div class="vib-status" :style="{ color: statusColor }">{{ vibStatus }}</div>

    <div class="chart-wrap">
      <svg class="chart-svg" viewBox="0 0 360 220" preserveAspectRatio="none">
        <line x1="0" x2="360" y1="55" y2="55" stroke="rgba(0,0,0,0.07)" stroke-width="1" />
        <line x1="0" x2="360" y1="110" y2="110" stroke="rgba(0,0,0,0.07)" stroke-width="1" />
        <line x1="0" x2="360" y1="165" y2="165" stroke="rgba(0,0,0,0.07)" stroke-width="1" />
        <polyline :points="polylinePoints" fill="none" stroke="#ef4444" stroke-width="2" stroke-linejoin="round" />
      </svg>
    </div>

    <div v-if="error" class="error-box">
      <i-lucide:alert-triangle />{{ error }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import { accelerometerDataGQL } from '@/lib/api/query'

const polling = ref(false)
const error = ref('')
const vibration = ref(0)
const history = ref<number[]>(Array(80).fill(0.1))
let timer = 0

const vibStatus = computed(() => {
  const v = vibration.value
  if (v < 1) return 'Calm'
  if (v < 5) return 'Slight'
  if (v < 12) return 'Moderate'
  if (v < 20) return 'Strong'
  return 'Intense'
})
const statusColor = computed(() => {
  const v = vibration.value
  if (v < 1) return '#0d9488'
  if (v < 5) return '#16a34a'
  if (v < 12) return '#f59e0b'
  return '#ef4444'
})
const polylinePoints = computed(() => {
  const pts = history.value
  const MAX_VAL = 30
  return pts.map((v, i) => {
    const x = (i / (pts.length - 1)) * 360
    const y = 220 - (Math.min(v, MAX_VAL) / MAX_VAL) * 200 - 10
    return `${x},${y}`
  }).join(' ')
})

async function poll() {
  try {
    const r = await gqlFetch<{ accelerometerData: any }>(accelerometerDataGQL)
    if (!r.errors && r.data?.accelerometerData) {
      const mag = r.data.accelerometerData.vibrationMagnitude ?? 0
      vibration.value = mag
      history.value.push(mag)
      if (history.value.length > 80) history.value.shift()
      polling.value = true
    }
  } catch {
    error.value = 'Could not reach device.'
    polling.value = false
  }
}

onMounted(() => {
  poll()
  timer = window.setInterval(poll, 300)
})
onUnmounted(() => clearInterval(timer))
</script>

<style scoped lang="scss">
.sensor-root {
  padding: 18px 20px 32px;
  display: flex; flex-direction: column; gap: 12px;
  max-width: 700px; margin: 0 auto;
}
.sensor-header { display: flex; align-items: center; gap: 12px; }
.back-btn {
  background: none; border: none; cursor: pointer;
  color: var(--md-sys-color-on-surface);
  display: flex; align-items: center; padding: 6px; border-radius: 50%;
  transition: background 0.18s;
  &:hover { background: var(--md-sys-color-surface-container); }
  svg { width: 22px; height: 22px; }
}
.sensor-title { font-size: 1.4rem; font-weight: 700; margin: 0; flex: 1; }
.live-badge {
  display: flex; align-items: center; gap: 6px; padding: 4px 12px; border-radius: 999px;
  background: var(--md-sys-color-surface-container);
  font-size: 0.78rem; font-weight: 600; color: var(--md-sys-color-on-surface-variant);
  .dot { width: 8px; height: 8px; border-radius: 50%; background: #9ca3af; }
  &.active { background: rgba(34,197,94,0.14); color: #16a34a; .dot { background: #22c55e; animation: pulse 1.4s infinite; } }
}
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }
.info-banner {
  display: flex; align-items: flex-start; gap: 10px; padding: 14px 16px; border-radius: 14px;
  border: 1px solid rgba(34,197,94,0.3); background: rgba(34,197,94,0.06);
  color: var(--md-sys-color-on-surface-variant); font-size: 0.88rem; line-height: 1.5;
}
.info-icon { width: 18px; height: 18px; color: #16a34a; flex-shrink: 0; margin-top: 2px; }
.vib-value { text-align: center; font-size: 5rem; font-weight: 700; line-height: 1; margin-top: 20px; transition: color 0.3s; }
.vib-unit { text-align: center; color: var(--md-sys-color-on-surface-variant); font-size: 1rem; }
.vib-status { text-align: center; font-size: 1.4rem; font-weight: 600; }
.chart-wrap {
  margin-top: 16px;
  border-top: 1px solid var(--md-sys-color-outline-variant);
  border-bottom: 1px solid var(--md-sys-color-outline-variant);
  padding: 8px 0;
}
.chart-svg { width: 100%; height: 220px; display: block; }
.error-box {
  display: flex; align-items: center; gap: 10px; padding: 16px; border-radius: 14px;
  background: rgba(239,68,68,0.08); color: #ef4444; font-size: 0.9rem;
  svg { width: 20px; height: 20px; }
}
</style>
