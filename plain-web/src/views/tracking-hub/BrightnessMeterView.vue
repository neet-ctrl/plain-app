<template>
  <div class="sensor-root">
    <div class="sensor-header">
      <button class="back-btn" @click="$router.back()">
        <i-lucide:arrow-left />
      </button>
      <h2 class="sensor-title">Brightness Meter</h2>
    </div>

    <div class="info-banner">
      <i-lucide:info class="info-icon" />
      <span>This tool helps to measure the brightness of environment around the device</span>
    </div>

    <div class="gauge-wrap">
      <svg viewBox="0 0 260 160" class="gauge-svg">
        <path d="M 20 140 A 110 110 0 0 1 240 140" fill="none" stroke="var(--md-sys-color-surface-container)" stroke-width="18" stroke-linecap="round" />
        <path d="M 20 140 A 110 110 0 0 1 240 140" fill="none" :stroke="gaugeColor" stroke-width="18" stroke-linecap="round"
          :stroke-dasharray="arcLen" :stroke-dashoffset="arcOffset" />
        <line :x1="needleX1" :y1="needleY1" :x2="needleX2" :y2="needleY2" stroke="#111" stroke-width="2.5" stroke-linecap="round" />
        <circle cx="130" cy="140" r="6" fill="#333" />
        <text x="130" y="118" text-anchor="middle" font-size="26" font-weight="700" :fill="gaugeColor">{{ lux }}</text>
        <text x="130" y="132" text-anchor="middle" font-size="11" fill="#888">lux</text>
      </svg>
    </div>

    <div class="lux-status" :style="{ color: gaugeColor }">{{ luxLabel }}</div>

    <div class="lux-ref">
      <div class="ref-row" v-for="row in luxTable" :key="row.label"
        :class="{ active: lux >= row.min && lux < row.max }">
        <div class="ref-dot" :style="{ background: row.color }"></div>
        <span class="ref-range">{{ row.range }}</span>
        <span class="ref-desc">{{ row.label }}</span>
      </div>
    </div>

    <div v-if="!supported" class="unsupported">
      <i-lucide:alert-triangle />
      Ambient Light Sensor not supported. Showing simulated data.
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'

const supported = ref(true)
const lux = ref(0)

const luxTable = [
  { min: 0, max: 10, range: '0-10 lux', label: 'Very Dark / Night', color: '#374151' },
  { min: 10, max: 100, range: '10-100 lux', label: 'Dark Room', color: '#6366f1' },
  { min: 100, max: 400, range: '100-400 lux', label: 'Indoor / Overcast', color: '#0d9488' },
  { min: 400, max: 1000, range: '400-1000 lux', label: 'Bright Office', color: '#22c55e' },
  { min: 1000, max: 10000, range: '1000-10000 lux', label: 'Daylight', color: '#f59e0b' },
  { min: 10000, max: 999999, range: '10000+ lux', label: 'Direct Sunlight', color: '#ef4444' },
]

const gaugeColor = computed(() => {
  const v = lux.value
  if (v < 10) return '#374151'
  if (v < 100) return '#6366f1'
  if (v < 400) return '#0d9488'
  if (v < 1000) return '#22c55e'
  if (v < 10000) return '#f59e0b'
  return '#ef4444'
})
const luxLabel = computed(() => {
  const row = luxTable.find(r => lux.value >= r.min && lux.value < r.max)
  return row?.label ?? 'Unknown'
})

const MAX_LUX = 100000
const ARC_TOTAL = 345
const arcLen = ARC_TOTAL
const arcOffset = computed(() => {
  const pct = Math.min(1, Math.log10(Math.max(1, lux.value)) / Math.log10(MAX_LUX))
  return ARC_TOTAL * (1 - pct)
})

const NEEDLE_ANGLE = computed(() => {
  const pct = Math.min(1, Math.log10(Math.max(1, lux.value)) / Math.log10(MAX_LUX))
  return -180 + pct * 180
})
const needleX1 = computed(() => 130)
const needleY1 = computed(() => 140)
const needleX2 = computed(() => 130 + 90 * Math.cos((NEEDLE_ANGLE.value * Math.PI) / 180))
const needleY2 = computed(() => 140 + 90 * Math.sin((NEEDLE_ANGLE.value * Math.PI) / 180))

let sensor: any = null
let interval = 0

onMounted(() => {
  if ('AmbientLightSensor' in window) {
    try {
      sensor = new (window as any).AmbientLightSensor()
      sensor.addEventListener('reading', () => { lux.value = Math.round(sensor.illuminance) })
      sensor.addEventListener('error', () => { supported.value = false; startSimulation() })
      sensor.start()
    } catch {
      supported.value = false
      startSimulation()
    }
  } else {
    supported.value = false
    startSimulation()
  }
})

function startSimulation() {
  let t = 0
  interval = window.setInterval(() => {
    t += 0.05
    lux.value = Math.round(Math.abs(50 + 30 * Math.sin(t) + 10 * Math.sin(t * 3)))
  }, 500)
}

onUnmounted(() => {
  sensor?.stop()
  clearInterval(interval)
})
</script>

<style scoped lang="scss">
.sensor-root {
  padding: 18px 20px 32px;
  display: flex;
  flex-direction: column;
  gap: 14px;
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

.gauge-wrap {
  display: flex;
  justify-content: center;
  padding: 12px 0;
}
.gauge-svg {
  width: 100%;
  max-width: 320px;
  height: auto;
  overflow: visible;
}

.lux-status {
  text-align: center;
  font-size: 1.5rem;
  font-weight: 700;
  transition: color 0.3s;
}

.lux-ref {
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: var(--md-sys-color-surface-container);
  border-radius: 16px;
  padding: 16px;
}
.ref-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 0.85rem;
  color: var(--md-sys-color-on-surface-variant);
  padding: 6px 8px;
  border-radius: 10px;
  transition: background 0.2s;
  &.active {
    background: rgba(34,197,94,0.1);
    color: var(--md-sys-color-on-surface);
    font-weight: 600;
  }
}
.ref-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.ref-range { font-weight: 600; min-width: 90px; flex-shrink: 0; }

.unsupported {
  display: flex; align-items: center; gap: 10px;
  padding: 14px; border-radius: 14px;
  background: rgba(245,158,11,0.08); color: #f59e0b; font-size: 0.88rem;
  svg { width: 18px; height: 18px; }
}
</style>
