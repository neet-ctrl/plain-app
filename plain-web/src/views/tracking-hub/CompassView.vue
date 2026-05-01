<template>
  <div class="sensor-root">
    <div class="sensor-header">
      <button class="back-btn" @click="$router.back()"><i-lucide:arrow-left /></button>
      <h2 class="sensor-title">Compass</h2>
      <div class="live-badge" :class="{ active: polling }">
        <span class="dot" />{{ polling ? 'Live' : '—' }}
      </div>
    </div>
    <div v-if="!supported && checked" class="warn-box">
      <i-lucide:alert-triangle /> This device does not have a magnetometer sensor.
    </div>
    <div v-else>
      <div class="info-banner">
        <i-lucide:info class="info-icon" />
        <span>Uses the magnetic field sensor to show your real compass heading in degrees and cardinal direction.</span>
      </div>

      <div class="compass-wrap">
        <div class="compass-ring" :style="{ transform: `rotate(${-data.heading}deg)` }">
          <div class="compass-n">N</div>
          <div class="compass-s">S</div>
          <div class="compass-e">E</div>
          <div class="compass-w">W</div>
          <div class="compass-tick" v-for="t in ticks" :key="t" :style="{ transform: `rotate(${t}deg) translateX(-50%)` }"></div>
        </div>
        <div class="compass-needle">
          <div class="needle-n"></div>
          <div class="needle-s"></div>
        </div>
        <div class="compass-center"></div>
      </div>

      <div class="heading-card">
        <div class="heading-value">{{ data.heading.toFixed(1) }}°</div>
        <div class="heading-cardinal">{{ data.cardinalDir }}</div>
        <div class="heading-desc">{{ cardinalDesc }}</div>
      </div>

      <div class="mag-fields">
        <div class="mag-row" v-for="ax in magAxes" :key="ax.label">
          <span class="mag-ax">{{ ax.label }}</span>
          <div class="mag-bar-track">
            <div class="mag-bar" :style="{ width: ax.pct + '%' }"></div>
          </div>
          <span class="mag-val">{{ ax.value.toFixed(2) }} µT</span>
        </div>
      </div>
      <div v-if="error" class="error-box"><i-lucide:alert-triangle />{{ error }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import { magnetometerDataGQL, magnetometerSupportedGQL } from '@/lib/api/query'

const polling = ref(false)
const supported = ref(true)
const checked = ref(false)
const error = ref('')
let timer = 0

const data = ref({ x: 0, y: 0, z: 0, heading: 0, cardinalDir: 'N' })
const ticks = Array.from({ length: 36 }, (_, i) => i * 10)

const cardinalDesc = computed(() => {
  const map: Record<string, string> = {
    N: 'North', NE: 'North-East', E: 'East', SE: 'South-East',
    S: 'South', SW: 'South-West', W: 'West', NW: 'North-West',
  }
  return map[data.value.cardinalDir] ?? ''
})

const magAxes = computed(() => {
  const max = 100
  return [
    { label: 'X', value: data.value.x, pct: Math.min(100, (Math.abs(data.value.x) / max) * 100) },
    { label: 'Y', value: data.value.y, pct: Math.min(100, (Math.abs(data.value.y) / max) * 100) },
    { label: 'Z', value: data.value.z, pct: Math.min(100, (Math.abs(data.value.z) / max) * 100) },
  ]
})

async function poll() {
  try {
    if (!checked.value) {
      const s = await gqlFetch<{ magnetometerSupported: boolean }>(magnetometerSupportedGQL)
      supported.value = s.data.magnetometerSupported
      checked.value = true
      if (!supported.value) return
    }
    const r = await gqlFetch<{ magnetometerData: typeof data.value }>(magnetometerDataGQL)
    if (r.errors?.length) { error.value = r.errors[0].message; return }
    data.value = r.data.magnetometerData
    polling.value = true
  } catch {
    error.value = 'Could not reach device.'
    polling.value = false
  }
}

onMounted(() => { poll(); timer = window.setInterval(poll, 400) })
onUnmounted(() => clearInterval(timer))
</script>

<style scoped lang="scss">
.sensor-root { padding: 18px 20px 32px; display: flex; flex-direction: column; gap: 14px; max-width: 700px; margin: 0 auto; }
.sensor-header { display: flex; align-items: center; gap: 12px; }
.back-btn { background: none; border: none; cursor: pointer; color: var(--md-sys-color-on-surface); display: flex; align-items: center; padding: 6px; border-radius: 50%; transition: background 0.18s; &:hover { background: var(--md-sys-color-surface-container); } svg { width: 22px; height: 22px; } }
.sensor-title { font-size: 1.4rem; font-weight: 700; margin: 0; flex: 1; }
.live-badge { display: flex; align-items: center; gap: 6px; padding: 4px 12px; border-radius: 999px; background: var(--md-sys-color-surface-container); font-size: 0.78rem; font-weight: 600; color: var(--md-sys-color-on-surface-variant); .dot { width: 8px; height: 8px; border-radius: 50%; background: #9ca3af; } &.active { background: rgba(34,197,94,0.14); color: #16a34a; .dot { background: #22c55e; animation: pulse 1.4s infinite; } } }
@keyframes pulse { 0%,100%{opacity:1}50%{opacity:.4} }
.info-banner { display: flex; align-items: flex-start; gap: 10px; padding: 14px 16px; border-radius: 14px; border: 1px solid rgba(245,158,11,0.3); background: rgba(245,158,11,0.06); color: var(--md-sys-color-on-surface-variant); font-size: 0.88rem; line-height: 1.5; }
.info-icon { width: 18px; height: 18px; color: #f59e0b; flex-shrink: 0; margin-top: 2px; }
.compass-wrap { position: relative; width: 260px; height: 260px; margin: 8px auto; }
.compass-ring { position: absolute; inset: 0; border: 3px solid var(--md-sys-color-surface-container-high); border-radius: 50%; transition: transform 0.4s ease; }
.compass-n, .compass-s, .compass-e, .compass-w { position: absolute; font-size: 1.1rem; font-weight: 800; }
.compass-n { top: 8px; left: 50%; transform: translateX(-50%); color: #ef4444; }
.compass-s { bottom: 8px; left: 50%; transform: translateX(-50%); color: var(--md-sys-color-on-surface-variant); }
.compass-e { right: 10px; top: 50%; transform: translateY(-50%); color: var(--md-sys-color-on-surface-variant); }
.compass-w { left: 10px; top: 50%; transform: translateY(-50%); color: var(--md-sys-color-on-surface-variant); }
.compass-needle { position: absolute; top: 50%; left: 50%; width: 4px; height: 100px; transform: translate(-50%, -100%); }
.needle-n { width: 100%; height: 55%; background: #ef4444; border-radius: 999px 999px 0 0; }
.needle-s { width: 100%; height: 45%; background: var(--md-sys-color-on-surface-variant); border-radius: 0 0 999px 999px; opacity: 0.5; }
.compass-center { position: absolute; top: 50%; left: 50%; width: 12px; height: 12px; background: #fff; border: 2px solid #374151; border-radius: 50%; transform: translate(-50%, -50%); z-index: 2; }
.heading-card { background: var(--md-sys-color-surface-container); border-radius: 16px; padding: 20px; text-align: center; }
.heading-value { font-size: 2.8rem; font-weight: 800; }
.heading-cardinal { font-size: 1.5rem; font-weight: 700; color: #f59e0b; margin: 4px 0; }
.heading-desc { color: var(--md-sys-color-on-surface-variant); font-size: 0.9rem; }
.mag-fields { background: var(--md-sys-color-surface-container); border-radius: 16px; padding: 14px; display: flex; flex-direction: column; gap: 8px; }
.mag-row { display: flex; align-items: center; gap: 10px; }
.mag-ax { font-weight: 700; min-width: 18px; }
.mag-bar-track { flex: 1; height: 6px; border-radius: 999px; background: var(--md-sys-color-surface-container-high); overflow: hidden; }
.mag-bar { height: 100%; background: #f59e0b; border-radius: 999px; transition: width 0.3s ease; }
.mag-val { font-size: 0.82rem; color: var(--md-sys-color-on-surface-variant); min-width: 80px; text-align: right; font-variant-numeric: tabular-nums; }
.warn-box, .error-box { display: flex; align-items: center; gap: 10px; padding: 14px; border-radius: 14px; font-size: 0.9rem; svg { width: 18px; height: 18px; } }
.warn-box { background: rgba(245,158,11,0.09); color: #d97706; }
.error-box { background: rgba(239,68,68,0.08); color: #ef4444; }
</style>
