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
      <span>Reads the phone's screen brightness level (0–100%) via the device API.</span>
    </div>

    <div class="gauge-wrap">
      <svg viewBox="0 0 260 160" class="gauge-svg">
        <path d="M 20 140 A 110 110 0 0 1 240 140" fill="none" stroke="var(--md-sys-color-surface-container)" stroke-width="18" stroke-linecap="round" />
        <path d="M 20 140 A 110 110 0 0 1 240 140" fill="none" :stroke="gaugeColor" stroke-width="18" stroke-linecap="round"
          :stroke-dasharray="arcLen" :stroke-dashoffset="arcOffset" />
        <line :x1="needleX1" :y1="needleY1" :x2="needleX2" :y2="needleY2" stroke="#111" stroke-width="2.5" stroke-linecap="round" />
        <circle cx="130" cy="140" r="6" fill="#333" />
        <text x="130" y="118" text-anchor="middle" font-size="30" font-weight="700" :fill="gaugeColor">{{ brightness }}%</text>
        <text x="130" y="133" text-anchor="middle" font-size="11" fill="#888">brightness</text>
      </svg>
    </div>

    <div class="bright-status" :style="{ color: gaugeColor }">{{ brightnessLabel }}</div>

    <div class="bright-ref">
      <div class="ref-row" v-for="row in brightTable" :key="row.label"
        :class="{ active: brightness >= row.min && brightness <= row.max }">
        <div class="ref-dot" :style="{ background: row.color }"></div>
        <span class="ref-range">{{ row.range }}</span>
        <span class="ref-desc">{{ row.label }}</span>
      </div>
    </div>

    <div v-if="error" class="error-box"><i-lucide:alert-triangle />{{ error }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import { brightnessGQL } from '@/lib/api/query'

const polling = ref(false)
const error = ref('')
const brightness = ref(0)
let timer = 0

const brightTable = [
  { min: 0,  max: 20,  range: '0–20%',   label: 'Very Dim',     color: '#374151' },
  { min: 21, max: 40,  range: '21–40%',  label: 'Dim',          color: '#6366f1' },
  { min: 41, max: 60,  range: '41–60%',  label: 'Moderate',     color: '#0d9488' },
  { min: 61, max: 80,  range: '61–80%',  label: 'Bright',       color: '#22c55e' },
  { min: 81, max: 100, range: '81–100%', label: 'Very Bright',  color: '#f59e0b' },
]

const gaugeColor = computed(() => {
  const v = brightness.value
  if (v <= 20) return '#374151'
  if (v <= 40) return '#6366f1'
  if (v <= 60) return '#0d9488'
  if (v <= 80) return '#22c55e'
  return '#f59e0b'
})
const brightnessLabel = computed(() => {
  const row = brightTable.find(r => brightness.value >= r.min && brightness.value <= r.max)
  return row?.label ?? 'Unknown'
})

const arcLen = 345
const arcOffset = computed(() => arcLen * (1 - brightness.value / 100))
const NEEDLE_ANGLE = computed(() => -180 + (brightness.value / 100) * 180)
const needleX1 = 130
const needleY1 = 140
const needleX2 = computed(() => 130 + 90 * Math.cos((NEEDLE_ANGLE.value * Math.PI) / 180))
const needleY2 = computed(() => 140 + 90 * Math.sin((NEEDLE_ANGLE.value * Math.PI) / 180))

async function poll() {
  try {
    const r = await gqlFetch<{ brightness: number }>(brightnessGQL)
    if (!r.errors) {
      brightness.value = Math.round(r.data.brightness)
      polling.value = true
    }
  } catch {
    error.value = 'Could not reach device.'
    polling.value = false
  }
}

onMounted(() => {
  poll()
  timer = window.setInterval(poll, 1000)
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
  background: var(--md-sys-color-surface-container); font-size: 0.78rem; font-weight: 600; color: var(--md-sys-color-on-surface-variant);
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
.gauge-wrap { display: flex; justify-content: center; padding: 12px 0; }
.gauge-svg { width: 100%; max-width: 320px; height: auto; overflow: visible; }
.bright-status { text-align: center; font-size: 1.5rem; font-weight: 700; transition: color 0.3s; }
.bright-ref {
  display: flex; flex-direction: column; gap: 8px;
  background: var(--md-sys-color-surface-container); border-radius: 16px; padding: 16px;
}
.ref-row {
  display: flex; align-items: center; gap: 10px; font-size: 0.85rem;
  color: var(--md-sys-color-on-surface-variant); padding: 6px 8px; border-radius: 10px; transition: background 0.2s;
  &.active { background: rgba(34,197,94,0.1); color: var(--md-sys-color-on-surface); font-weight: 600; }
}
.ref-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.ref-range { font-weight: 600; min-width: 70px; flex-shrink: 0; }
.error-box {
  display: flex; align-items: center; gap: 10px; padding: 14px; border-radius: 14px;
  background: rgba(239,68,68,0.08); color: #ef4444; font-size: 0.9rem;
  svg { width: 18px; height: 18px; }
}
</style>
