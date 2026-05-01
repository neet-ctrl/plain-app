<template>
  <div class="sensor-root">
    <div class="sensor-header">
      <button class="back-btn" @click="stopAll(); $router.back()">
        <i-lucide:arrow-left />
      </button>
      <h2 class="sensor-title">Mobile Torch</h2>
      <button class="reset-btn" @click="stopAll" title="Stop All">
        <i-lucide:power-off />
      </button>
    </div>

    <div class="mode-label">Select Mode</div>

    <div class="mode-grid">
      <button class="mode-card" :class="{ active: activeMode === 'normal' }" @click="selectMode('normal')">
        <i-lucide:zap class="mode-icon" />
        <span>Normal Torch</span>
      </button>
      <button class="mode-card" :class="{ active: activeMode === 'blink' }" @click="selectMode('blink')">
        <i-lucide:asterisk class="mode-icon" />
        <span>Blinking Signal</span>
      </button>
      <button class="mode-card" :class="{ active: activeMode === 'sos' }" @click="selectMode('sos')">
        <span class="sos-text-icon">SOS</span>
        <span>SOS Signal</span>
      </button>
      <button class="mode-card" :class="{ active: activeMode === 'screen' }" @click="selectMode('screen')">
        <i-lucide:monitor class="mode-icon" />
        <span>Screen Colors</span>
      </button>
    </div>

    <div v-if="activeMode === 'blink'" class="duration-card">
      <div class="dur-header">
        <i-lucide:timer class="dur-icon" />
        <span class="dur-title">Blink Duration</span>
      </div>
      <div class="dur-row">
        <span class="dur-label">ON duration</span>
        <input type="range" min="100" max="2000" step="50" v-model.number="blinkOnMs" class="dur-slider" />
        <span class="dur-val">{{ blinkOnMs }}ms</span>
      </div>
      <div class="dur-row">
        <span class="dur-label">OFF duration</span>
        <input type="range" min="100" max="2000" step="50" v-model.number="blinkOffMs" class="dur-slider" />
        <span class="dur-val">{{ blinkOffMs }}ms</span>
      </div>
    </div>

    <div class="status-card" :class="statusClass">
      <div class="status-indicator" :class="{ on: torchOn }"></div>
      <div class="status-info">
        <div class="status-title">{{ statusTitle }}</div>
        <div class="status-desc">{{ statusDesc }}</div>
      </div>
    </div>

    <div v-if="activeMode === 'screen'" class="screen-overlay" :style="{ background: screenColor }"></div>

    <div v-if="error" class="error-box">
      <i-lucide:alert-triangle />
      {{ error }}
    </div>

    <div v-if="!hasTorchSupport" class="warn-box">
      <i-lucide:info />
      Real torch not available on this browser/device. Screen flash mode works on all devices.
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted, watch } from 'vue'

const activeMode = ref<'none'|'normal'|'sos'|'blink'|'screen'>('none')
const torchOn = ref(false)
const error = ref('')
const hasTorchSupport = ref(true)
const blinkOnMs = ref(500)
const blinkOffMs = ref(500)
const screenColor = ref('transparent')

let stream: MediaStream | null = null
let track: MediaStreamTrack | null = null
let blinkTimer = 0
let sosTimer = 0
let screenTimer = 0

const statusTitle = computed(() => {
  if (activeMode.value === 'none') return 'OFF'
  if (activeMode.value === 'normal') return torchOn.value ? 'ON' : 'OFF'
  if (activeMode.value === 'blink') return 'BLINKING'
  if (activeMode.value === 'sos') return 'SOS'
  if (activeMode.value === 'screen') return 'SCREEN FLASH'
  return 'OFF'
})
const statusDesc = computed(() => {
  if (activeMode.value === 'none') return 'FlashLight is OFF.'
  if (activeMode.value === 'normal') return torchOn.value ? 'FlashLight is ON.' : 'FlashLight is OFF.'
  if (activeMode.value === 'blink') return `Blinking: ${blinkOnMs.value}ms ON / ${blinkOffMs.value}ms OFF`
  if (activeMode.value === 'sos') return 'Sending SOS signal (... --- ...)'
  if (activeMode.value === 'screen') return 'Using screen as flash'
  return 'FlashLight is OFF.'
})
const statusClass = computed(() => {
  if (activeMode.value === 'none') return 'off'
  if (torchOn.value || activeMode.value !== 'none') return 'active'
  return 'off'
})

async function getTorch() {
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'environment' }
    })
    track = stream.getVideoTracks()[0]
    const caps = track.getCapabilities() as any
    if (!caps.torch) {
      hasTorchSupport.value = false
    }
    return true
  } catch {
    hasTorchSupport.value = false
    return false
  }
}

async function setTorch(on: boolean) {
  if (!track) return
  try {
    await track.applyConstraints({ advanced: [{ torch: on } as any] })
    torchOn.value = on
  } catch {
    torchOn.value = on
  }
}

function clearTimers() {
  clearTimeout(blinkTimer)
  clearTimeout(sosTimer)
  clearInterval(screenTimer)
}

function stopAll() {
  clearTimers()
  if (track) setTorch(false)
  stream?.getTracks().forEach(t => t.stop())
  stream = null
  track = null
  torchOn.value = false
  activeMode.value = 'none'
  screenColor.value = 'transparent'
}

function startBlink() {
  let isOn = false
  function toggle() {
    isOn = !isOn
    setTorch(isOn)
    torchOn.value = isOn
    if (activeMode.value === 'blink') {
      blinkTimer = window.setTimeout(toggle, isOn ? blinkOnMs.value : blinkOffMs.value)
    }
  }
  toggle()
}

function startSOS() {
  const dot = 200
  const dash = 600
  const gap = 200
  const letterGap = 500
  const wordGap = 1000
  const pattern = [
    dot, gap, dot, gap, dot, letterGap,
    dash, gap, dash, gap, dash, letterGap,
    dot, gap, dot, gap, dot, wordGap,
  ]
  let i = 0
  let isOn = false
  function next() {
    if (activeMode.value !== 'sos') { setTorch(false); return }
    if (i >= pattern.length) { i = 0 }
    isOn = !isOn
    setTorch(isOn)
    torchOn.value = isOn
    sosTimer = window.setTimeout(next, pattern[i++])
  }
  next()
}

const screenColors = ['#ffffff','#ffff00','#ff0000','#00ff00','#0000ff','#ffffff']
function startScreenFlash() {
  let idx = 0
  screenTimer = window.setInterval(() => {
    screenColor.value = screenColors[idx % screenColors.length]
    idx++
  }, 300)
}

async function selectMode(mode: 'normal'|'blink'|'sos'|'screen') {
  clearTimers()
  if (track) await setTorch(false)

  if (activeMode.value === mode) {
    stopAll()
    return
  }

  activeMode.value = mode
  screenColor.value = 'transparent'
  error.value = ''

  if (mode === 'screen') {
    startScreenFlash()
    return
  }

  if (!track) {
    const ok = await getTorch()
    if (!ok && mode !== 'screen') {
      error.value = 'Camera/torch access denied. Try screen colors mode.'
      activeMode.value = 'none'
      return
    }
  }

  if (mode === 'normal') {
    await setTorch(true)
  } else if (mode === 'blink') {
    startBlink()
  } else if (mode === 'sos') {
    startSOS()
  }
}

watch([blinkOnMs, blinkOffMs], () => {
  if (activeMode.value === 'blink') {
    clearTimeout(blinkTimer)
    startBlink()
  }
})

onUnmounted(stopAll)
</script>

<style scoped lang="scss">
.sensor-root {
  padding: 18px 20px 32px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  max-width: 700px;
  margin: 0 auto;
  position: relative;
}
.sensor-header {
  display: flex;
  align-items: center;
  gap: 12px;
}
.back-btn, .reset-btn {
  background: none; border: none; cursor: pointer;
  color: var(--md-sys-color-on-surface);
  display: flex; align-items: center;
  padding: 6px; border-radius: 50%;
  transition: background 0.18s;
  &:hover { background: var(--md-sys-color-surface-container); }
  svg { width: 22px; height: 22px; }
}
.sensor-title { font-size: 1.4rem; font-weight: 700; margin: 0; flex: 1; }

.mode-label { font-size: 1rem; font-weight: 600; color: var(--md-sys-color-on-surface-variant); }

.mode-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.mode-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 24px 16px;
  border-radius: 18px;
  border: 1.5px solid var(--md-sys-color-outline-variant);
  background: var(--md-sys-color-surface-container);
  cursor: pointer;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--md-sys-color-on-surface);
  transition: all 0.2s;
  &:hover { transform: translateY(-2px); box-shadow: 0 6px 18px rgba(0,0,0,0.1); }
  &.active {
    border-color: #0d9488;
    background: rgba(13,148,136,0.1);
    color: #0d9488;
    .mode-icon { color: #0d9488; }
    .sos-text-icon { color: #0d9488; }
  }
}
.mode-icon { width: 28px; height: 28px; color: var(--md-sys-color-on-surface-variant); }
.sos-text-icon { font-size: 1.4rem; font-weight: 900; color: var(--md-sys-color-on-surface-variant); }

.duration-card {
  background: var(--md-sys-color-surface-container);
  border-radius: 16px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.dur-header { display: flex; align-items: center; gap: 8px; }
.dur-icon { width: 18px; height: 18px; color: #0d9488; }
.dur-title { font-weight: 600; font-size: 0.95rem; }
.dur-row { display: flex; align-items: center; gap: 10px; }
.dur-label { font-size: 0.82rem; color: var(--md-sys-color-on-surface-variant); min-width: 90px; }
.dur-slider { flex: 1; accent-color: #0d9488; }
.dur-val { font-size: 0.82rem; font-weight: 600; min-width: 48px; text-align: right; color: #0d9488; }

.status-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 20px;
  border-radius: 16px;
  background: var(--md-sys-color-surface-container);
  transition: background 0.3s;
  &.active { background: rgba(13,148,136,0.08); }
}
.status-indicator {
  width: 18px; height: 18px; border-radius: 50%;
  background: #9ca3af;
  transition: background 0.3s, box-shadow 0.3s;
  flex-shrink: 0;
  &.on {
    background: #fbbf24;
    box-shadow: 0 0 12px rgba(251,191,36,0.7);
    animation: glow 0.8s ease-in-out infinite alternate;
  }
}
@keyframes glow {
  from { box-shadow: 0 0 8px rgba(251,191,36,0.6); }
  to   { box-shadow: 0 0 20px rgba(251,191,36,0.9); }
}
.status-title { font-size: 1.1rem; font-weight: 700; }
.status-desc { font-size: 0.84rem; color: var(--md-sys-color-on-surface-variant); margin-top: 2px; }

.screen-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  pointer-events: none;
  transition: background 0.1s;
  mix-blend-mode: screen;
}

.error-box {
  display: flex; align-items: center; gap: 10px;
  padding: 14px; border-radius: 14px;
  background: rgba(239,68,68,0.08); color: #ef4444; font-size: 0.9rem;
  svg { width: 18px; height: 18px; }
}
.warn-box {
  display: flex; align-items: center; gap: 10px;
  padding: 14px; border-radius: 14px;
  background: rgba(245,158,11,0.08); color: #d97706; font-size: 0.88rem;
  svg { width: 18px; height: 18px; }
}
</style>
