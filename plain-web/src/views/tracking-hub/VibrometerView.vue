<template>
  <div class="sensor-root">
    <div class="sensor-header">
      <button class="back-btn" @click="$router.back()">
        <i-lucide:arrow-left />
      </button>
      <h2 class="sensor-title">Vibrometer</h2>
    </div>

    <div class="info-banner">
      <i-lucide:info class="info-icon" />
      <span>This tool helps to measure the vibration of device.</span>
    </div>

    <div class="vib-value">{{ vibration.toFixed(1) }}</div>
    <div class="vib-unit">m/s²</div>
    <div class="vib-status" :style="{ color: statusColor }">{{ vibStatus }}</div>

    <div class="chart-wrap">
      <svg :width="chartW" :height="chartH" class="chart-svg">
        <polyline :points="polylinePoints" fill="none" stroke="#ef4444" stroke-width="2" stroke-linejoin="round" />
        <line x1="0" :x2="chartW" :y1="chartH * 0.25" :y2="chartH * 0.25" stroke="rgba(0,0,0,0.08)" stroke-width="1" />
        <line x1="0" :x2="chartW" :y1="chartH * 0.5" :y2="chartH * 0.5" stroke="rgba(0,0,0,0.08)" stroke-width="1" />
        <line x1="0" :x2="chartW" :y1="chartH * 0.75" :y2="chartH * 0.75" stroke="rgba(0,0,0,0.08)" stroke-width="1" />
      </svg>
    </div>

    <div v-if="!supported" class="unsupported">
      <i-lucide:alert-triangle />
      DeviceMotion not supported on this device/browser.
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'

const supported = ref(true)
const vibration = ref(0)
const history = ref<number[]>(Array(80).fill(0.1))
const chartW = 360
const chartH = 220
const MAX_VAL = 30

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
  if (v < 20) return '#ef4444'
  return '#dc2626'
})

const polylinePoints = computed(() => {
  const pts = history.value
  return pts.map((v, i) => {
    const x = (i / (pts.length - 1)) * chartW
    const y = chartH - (Math.min(v, MAX_VAL) / MAX_VAL) * chartH * 0.9 - chartH * 0.05
    return `${x},${y}`
  }).join(' ')
})

function onMotion(e: DeviceMotionEvent) {
  const a = e.acceleration
  if (a) {
    const mag = Math.sqrt((a.x ?? 0) ** 2 + (a.y ?? 0) ** 2 + (a.z ?? 0) ** 2)
    vibration.value = Math.round(mag * 10) / 10
    history.value.push(mag)
    if (history.value.length > 80) history.value.shift()
  }
}

onMounted(() => {
  if (typeof DeviceMotionEvent === 'undefined') {
    supported.value = false
    return
  }
  window.addEventListener('devicemotion', onMotion)
})
onUnmounted(() => {
  window.removeEventListener('devicemotion', onMotion)
})
</script>

<style scoped lang="scss">
.sensor-root {
  padding: 18px 20px 32px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 700px;
  margin: 0 auto;
}
.sensor-header {
  display: flex;
  align-items: center;
  gap: 12px;
}
.back-btn {
  background: none; border: none; cursor: pointer;
  color: var(--md-sys-color-on-surface);
  display: flex; align-items: center;
  padding: 6px; border-radius: 50%;
  transition: background 0.18s;
  &:hover { background: var(--md-sys-color-surface-container); }
  svg { width: 22px; height: 22px; }
}
.sensor-title { font-size: 1.4rem; font-weight: 700; margin: 0; }
.info-banner {
  display: flex; align-items: flex-start; gap: 10px;
  padding: 14px 16px; border-radius: 14px;
  border: 1px solid rgba(34,197,94,0.3);
  background: rgba(34,197,94,0.06);
  color: var(--md-sys-color-on-surface-variant);
  font-size: 0.88rem; line-height: 1.5;
}
.info-icon { width: 18px; height: 18px; color: #16a34a; flex-shrink: 0; margin-top: 2px; }

.vib-value {
  text-align: center;
  font-size: 5rem;
  font-weight: 700;
  color: #0d9488;
  line-height: 1;
  margin-top: 20px;
}
.vib-unit { text-align: center; color: var(--md-sys-color-on-surface-variant); font-size: 1rem; }
.vib-status { text-align: center; font-size: 1.4rem; font-weight: 600; }

.chart-wrap {
  margin-top: 16px;
  border-top: 1px solid var(--md-sys-color-outline-variant);
  border-bottom: 1px solid var(--md-sys-color-outline-variant);
  padding: 8px 0;
  overflow: hidden;
  width: 100%;
}
.chart-svg {
  width: 100%;
  height: 220px;
  display: block;
}
.unsupported {
  display: flex; align-items: center; gap: 10px;
  padding: 16px; border-radius: 14px;
  background: rgba(239,68,68,0.08); color: #ef4444; font-size: 0.9rem;
  svg { width: 20px; height: 20px; }
}
</style>
