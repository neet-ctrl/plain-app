<template>
  <div class="sensor-root">
    <div class="sensor-header">
      <button class="back-btn" @click="$router.back()">
        <i-lucide:arrow-left />
      </button>
      <h2 class="sensor-title">Brightness Meter</h2>
      <div class="live-badge" :class="{ active: polling }">
        <span class="dot" />{{ polling ? 'Live' : '—' }}
      </div>
    </div>

    <div class="info-banner">
      <i-lucide:info class="info-icon" />
      <span>This tool helps to measure the brightness of environment around the device.</span>
    </div>

    <div class="gauge-wrap">
      <svg viewBox="0 0 300 190" class="gauge-svg">
        <defs>
          <linearGradient id="arcGrad" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%"   stop-color="#374151" />
            <stop offset="20%"  stop-color="#6366f1" />
            <stop offset="45%"  stop-color="#0d9488" />
            <stop offset="70%"  stop-color="#22c55e" />
            <stop offset="100%" stop-color="#fbbf24" />
          </linearGradient>
        </defs>

        <path d="M 25 165 A 125 125 0 0 1 275 165"
          fill="none" stroke="var(--md-sys-color-surface-container)"
          stroke-width="18" stroke-linecap="round" />

        <path d="M 25 165 A 125 125 0 0 1 275 165"
          fill="none" stroke="url(#arcGrad)"
          stroke-width="18" stroke-linecap="round"
          :stroke-dasharray="arcLen" :stroke-dashoffset="arcOffset"
          style="transition: stroke-dashoffset 0.4s ease" />

        <line :x1="needleX1" :y1="needleY1" :x2="needleX2" :y2="needleY2"
          stroke="#333" stroke-width="3" stroke-linecap="round" />
        <circle cx="150" cy="165" r="7" fill="#444" />

        <text x="150" y="130" text-anchor="middle" font-size="38" font-weight="700"
          :fill="gaugeColor">{{ displayLux }}</text>
        <text x="150" y="149" text-anchor="middle" font-size="13" fill="#888">lux</text>

        <text x="22"  y="185" text-anchor="middle" font-size="9" fill="#888">0</text>
        <text x="150" y="46"  text-anchor="middle" font-size="9" fill="#888">5k</text>
        <text x="278" y="185" text-anchor="middle" font-size="9" fill="#888">10k</text>
      </svg>
    </div>

    <div class="env-label" :style="{ color: gaugeColor }">{{ envLabel }}</div>

    <div class="lux-table">
      <div class="lux-row" v-for="row in luxTable" :key="row.label"
        :class="{ active: isActive(row) }">
        <div class="lux-dot" :style="{ background: row.color }"></div>
        <span class="lux-range">{{ row.range }}</span>
        <span class="lux-desc">{{ row.label }}</span>
      </div>
    </div>

    <div v-if="noSensor" class="warn-box">
      <i-lucide:alert-triangle />
      This device does not have an ambient light sensor.
    </div>
    <div v-else-if="error" class="error-box">
      <i-lucide:alert-triangle />{{ error }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import { ambientLightGQL } from '@/lib/api/query'

const polling = ref(false)
const noSensor = ref(false)
const error = ref('')
const lux = ref(0)
let timer = 0

const luxTable = [
  { min: 0,      max: 10,     range: '0–10 lux',       label: 'Dark Room',         color: '#1e293b' },
  { min: 10,     max: 50,     range: '10–50 lux',      label: 'Dim Room',          color: '#374151' },
  { min: 50,     max: 200,    range: '50–200 lux',     label: 'Normal Room',       color: '#6366f1' },
  { min: 200,    max: 500,    range: '200–500 lux',    label: 'Bright Room',       color: '#0d9488' },
  { min: 500,    max: 1000,   range: '500–1k lux',     label: 'Very Bright Room',  color: '#22c55e' },
  { min: 1000,   max: 5000,   range: '1k–5k lux',     label: 'Overcast Day',      color: '#84cc16' },
  { min: 5000,   max: 10000,  range: '5k–10k lux',    label: 'Cloudy Daylight',   color: '#f59e0b' },
  { min: 10000,  max: Infinity, range: '10k+ lux',    label: 'Direct Sunlight',   color: '#ef4444' },
]

function isActive(row: typeof luxTable[0]) {
  return lux.value >= row.min && lux.value < row.max
}

const activeRow = computed(() => luxTable.find(r => isActive(r)) ?? luxTable[0])
const envLabel = computed(() => activeRow.value.label)
const gaugeColor = computed(() => activeRow.value.color)

const displayLux = computed(() => {
  const v = lux.value
  if (v >= 1000) return (v / 1000).toFixed(1) + 'k'
  return Math.round(v).toString()
})

const MAX_LUX = 10000
const arcLen = 393
const arcOffset = computed(() => {
  const pct = Math.min(1, lux.value / MAX_LUX)
  return arcLen * (1 - pct)
})

const NEEDLE_ANGLE = computed(() => -180 + Math.min(1, lux.value / MAX_LUX) * 180)
const needleX1 = 150, needleY1 = 165
const needleX2 = computed(() => 150 + 115 * Math.cos((NEEDLE_ANGLE.value * Math.PI) / 180))
const needleY2 = computed(() => 165 + 115 * Math.sin((NEEDLE_ANGLE.value * Math.PI) / 180))

async function poll() {
  try {
    const r = await gqlFetch<{ ambientLight: number }>(ambientLightGQL)
    if (r.errors?.length) { error.value = r.errors[0].message; return }
    const val = r.data.ambientLight
    if (val < 0) {
      noSensor.value = true
      return
    }
    lux.value = val
    polling.value = true
    error.value = ''
  } catch {
    error.value = 'Could not reach device. Make sure the phone app is running.'
    polling.value = false
  }
}

onMounted(() => {
  poll()
  timer = window.setInterval(poll, 500)
})
onUnmounted(() => clearInterval(timer))
</script>

<style scoped lang="scss">
.sensor-root {
  padding: 18px 20px 32px;
  display: flex; flex-direction: column; gap: 14px;
  max-width: 700px; margin: 0 auto;
}
.sensor-header { display: flex; align-items: center; gap: 12px; }
.back-btn {
  background: none; border: none; cursor: pointer; color: var(--md-sys-color-on-surface);
  display: flex; align-items: center; padding: 6px; border-radius: 50%; transition: background 0.18s;
  &:hover { background: var(--md-sys-color-surface-container); }
  svg { width: 22px; height: 22px; }
}
.sensor-title { font-size: 1.4rem; font-weight: 700; margin: 0; flex: 1; }
.live-badge {
  display: flex; align-items: center; gap: 6px; padding: 4px 12px; border-radius: 999px;
  background: var(--md-sys-color-surface-container); font-size: 0.78rem; font-weight: 600;
  color: var(--md-sys-color-on-surface-variant);
  .dot { width: 8px; height: 8px; border-radius: 50%; background: #9ca3af; }
  &.active { background: rgba(34,197,94,0.14); color: #16a34a; .dot { background: #22c55e; animation: pulse 1.4s infinite; } }
}
@keyframes pulse { 0%,100%{opacity:1}50%{opacity:.4} }
.info-banner {
  display: flex; align-items: flex-start; gap: 10px; padding: 14px 16px; border-radius: 14px;
  border: 1px solid rgba(34,197,94,0.3); background: rgba(34,197,94,0.06);
  color: var(--md-sys-color-on-surface-variant); font-size: 0.88rem; line-height: 1.5;
}
.info-icon { width: 18px; height: 18px; color: #16a34a; flex-shrink: 0; margin-top: 2px; }
.gauge-wrap { display: flex; justify-content: center; padding: 8px 0; }
.gauge-svg { width: 100%; max-width: 360px; height: auto; overflow: visible; }
.env-label {
  text-align: center; font-size: 1.7rem; font-weight: 800;
  transition: color 0.4s; letter-spacing: -0.5px;
}
.lux-table {
  display: flex; flex-direction: column; gap: 6px;
  background: var(--md-sys-color-surface-container); border-radius: 16px; padding: 14px;
}
.lux-row {
  display: flex; align-items: center; gap: 10px; font-size: 0.84rem;
  color: var(--md-sys-color-on-surface-variant); padding: 7px 10px; border-radius: 10px; transition: background 0.25s;
  &.active {
    background: rgba(255,255,255,0.07);
    color: var(--md-sys-color-on-surface); font-weight: 600;
    .lux-dot { transform: scale(1.3); }
  }
}
.lux-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; transition: transform 0.2s; }
.lux-range { font-weight: 600; min-width: 88px; flex-shrink: 0; font-variant-numeric: tabular-nums; }
.warn-box, .error-box {
  display: flex; align-items: center; gap: 10px; padding: 14px; border-radius: 14px;
  font-size: 0.9rem; svg { width: 18px; height: 18px; }
}
.warn-box { background: rgba(245,158,11,0.09); color: #d97706; }
.error-box { background: rgba(239,68,68,0.08); color: #ef4444; }
</style>
