<template>
  <div class="sensor-root">
    <div class="sensor-header">
      <button class="back-btn" @click="$router.back()">
        <i-lucide:arrow-left />
      </button>
      <h2 class="sensor-title">Speedometer</h2>
      <button class="reset-btn" @click="resetStats" title="Reset">
        <i-lucide:rotate-ccw />
      </button>
    </div>

    <div class="gauge-wrap">
      <svg viewBox="0 0 260 170" class="gauge-svg">
        <path d="M 20 150 A 110 110 0 0 1 240 150" fill="none" stroke="var(--md-sys-color-surface-container)" stroke-width="14" stroke-linecap="round" />
        <path d="M 20 150 A 110 110 0 0 1 240 150" fill="none" stroke="#0d9488" stroke-width="14" stroke-linecap="round"
          :stroke-dasharray="arcLen" :stroke-dashoffset="speedOffset" />
        <line :x1="needleX1" :y1="needleY1" :x2="needleX2" :y2="needleY2" stroke="#111" stroke-width="2.5" stroke-linecap="round" />
        <circle cx="130" cy="150" r="6" fill="#333" />
        <text x="130" y="125" text-anchor="middle" font-size="34" font-weight="700" fill="var(--md-sys-color-on-surface)">{{ Math.round(speed) }}</text>
        <text x="130" y="142" text-anchor="middle" font-size="12" fill="#888">km/h</text>
      </svg>
    </div>

    <div class="stats-grid">
      <div class="stat-card wide">
        <i-lucide:navigation class="stat-icon" />
        <div class="stat-label">Heading</div>
        <div class="stat-val">{{ heading }}</div>
      </div>
      <div class="stat-card">
        <i-lucide:trending-up class="stat-icon" />
        <div class="stat-label">Max Speed</div>
        <div class="stat-val">{{ maxSpeed.toFixed(1) }} km/h</div>
      </div>
      <div class="stat-card">
        <i-lucide:arrow-left-right class="stat-icon" />
        <div class="stat-label">Avg Speed</div>
        <div class="stat-val">{{ avgSpeed.toFixed(1) }} km/h</div>
      </div>
      <div class="stat-card">
        <i-lucide:map class="stat-icon" />
        <div class="stat-label">Distance</div>
        <div class="stat-val">{{ distance.toFixed(3) }} km</div>
      </div>
      <div class="stat-card">
        <i-lucide:timer class="stat-icon" />
        <div class="stat-label">Time</div>
        <div class="stat-val">{{ elapsedTime }}</div>
      </div>
    </div>

    <button class="start-btn" :class="{ stop: running }" @click="toggleTracking">
      {{ running ? 'Stop' : 'Start' }}
    </button>

    <div v-if="error" class="error-box"><i-lucide:alert-triangle />{{ error }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import { deviceLocationGQL } from '@/lib/api/query'

const running = ref(false)
const speed = ref(0)
const maxSpeed = ref(0)
const heading = ref('NIL')
const distance = ref(0)
const error = ref('')
let lastLat = 0, lastLon = 0, lastTs = 0
let speedSum = 0, speedCount = 0
let timer = 0
let startTime = 0
const elapsed = ref(0)
let elapsedTimer = 0

const arcLen = 345
const MAX_SPEED = 200
const speedOffset = computed(() => arcLen * (1 - Math.min(1, speed.value / MAX_SPEED)))
const NEEDLE_ANGLE = computed(() => -180 + Math.min(1, speed.value / MAX_SPEED) * 180)
const needleX1 = 130, needleY1 = 150
const needleX2 = computed(() => 130 + 100 * Math.cos((NEEDLE_ANGLE.value * Math.PI) / 180))
const needleY2 = computed(() => 150 + 100 * Math.sin((NEEDLE_ANGLE.value * Math.PI) / 180))
const avgSpeed = computed(() => speedCount > 0 ? speedSum / speedCount : 0)
const elapsedTime = computed(() => {
  const s = elapsed.value
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60
  return `${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:${String(sec).padStart(2,'0')}`
})

function haversineKm(lat1: number, lon1: number, lat2: number, lon2: number) {
  const R = 6371, dLat = (lat2-lat1)*Math.PI/180, dLon = (lon2-lon1)*Math.PI/180
  const a = Math.sin(dLat/2)**2 + Math.cos(lat1*Math.PI/180)*Math.cos(lat2*Math.PI/180)*Math.sin(dLon/2)**2
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a))
}

function degreesToDir(bearing: number) {
  const dirs = ['N','NE','E','SE','S','SW','W','NW']
  return dirs[Math.round(((bearing % 360) + 360) % 360 / 45) % 8]
}

async function poll() {
  try {
    const r = await gqlFetch<{ deviceLocation: any }>(deviceLocationGQL)
    if (r.errors?.length) { error.value = r.errors[0].message; return }
    const loc = r.data.deviceLocation
    if (!loc) return

    if (lastLat !== 0 && lastLon !== 0) {
      const dt = (loc.timestamp - lastTs) / 1000
      if (dt > 0) {
        const dist = haversineKm(lastLat, lastLon, loc.latitude, loc.longitude)
        const kmh = Math.max(0, (dist / dt) * 3600)
        speed.value = kmh
        if (kmh > maxSpeed.value) maxSpeed.value = kmh
        speedSum += kmh; speedCount++
        distance.value += dist
      }
    }
    lastLat = loc.latitude; lastLon = loc.longitude; lastTs = loc.timestamp
    heading.value = degreesToDir(0)
    error.value = ''
  } catch {
    error.value = 'Could not reach device. Make sure phone app is running and location is on.'
  }
}

function toggleTracking() {
  if (running.value) {
    running.value = false
    clearInterval(timer)
    clearInterval(elapsedTimer)
    speed.value = 0
  } else {
    error.value = ''
    running.value = true
    startTime = Date.now()
    elapsedTimer = window.setInterval(() => { elapsed.value = Math.floor((Date.now() - startTime) / 1000) }, 1000)
    timer = window.setInterval(poll, 1000)
    poll()
  }
}

function resetStats() {
  speed.value = 0; maxSpeed.value = 0; speedSum = 0; speedCount = 0
  distance.value = 0; elapsed.value = 0; heading.value = 'NIL'
  lastLat = 0; lastLon = 0; lastTs = 0
}

onUnmounted(() => {
  clearInterval(timer)
  clearInterval(elapsedTimer)
})
</script>

<style scoped lang="scss">
.sensor-root { padding: 18px 20px 32px; display: flex; flex-direction: column; gap: 16px; max-width: 700px; margin: 0 auto; }
.sensor-header { display: flex; align-items: center; gap: 12px; }
.back-btn, .reset-btn {
  background: none; border: none; cursor: pointer; color: var(--md-sys-color-on-surface);
  display: flex; align-items: center; padding: 6px; border-radius: 50%; transition: background 0.18s;
  &:hover { background: var(--md-sys-color-surface-container); }
  svg { width: 22px; height: 22px; }
}
.sensor-title { font-size: 1.4rem; font-weight: 700; margin: 0; flex: 1; }
.gauge-wrap { display: flex; justify-content: center; }
.gauge-svg { width: 100%; max-width: 320px; height: auto; overflow: visible; }
.stats-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; .wide { grid-column: 1 / -1; } }
.stat-card {
  background: var(--md-sys-color-surface-container); border-radius: 16px; padding: 16px;
  display: flex; flex-direction: column; align-items: center; gap: 4px;
}
.stat-icon { width: 20px; height: 20px; color: #0d9488; }
.stat-label { font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); }
.stat-val { font-size: 1.1rem; font-weight: 700; }
.start-btn {
  width: 100%; padding: 16px; border-radius: 18px; border: none; cursor: pointer;
  font-size: 1.1rem; font-weight: 700; background: #0d9488; color: #fff; transition: all 0.2s; margin-top: 4px;
  &:hover { background: #0f766e; transform: translateY(-1px); }
  &.stop { background: #ef4444; &:hover { background: #dc2626; } }
}
.error-box {
  display: flex; align-items: center; gap: 10px; padding: 14px; border-radius: 14px;
  background: rgba(239,68,68,0.08); color: #ef4444; font-size: 0.9rem;
  svg { width: 18px; height: 18px; }
}
</style>
