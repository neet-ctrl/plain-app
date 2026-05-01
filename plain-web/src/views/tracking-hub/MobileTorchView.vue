<template>
  <div class="sensor-root">
    <div class="sensor-header">
      <button class="back-btn" @click="stopAll(); $router.back()">
        <i-lucide:arrow-left />
      </button>
      <h2 class="sensor-title">Mobile Torch</h2>
      <button class="reset-btn" @click="stopAll" title="Turn Off">
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
        <input type="range" min="100" max="2000" step="50" v-model.number="blinkOnMs" class="dur-slider" @change="applyBlink" />
        <span class="dur-val">{{ blinkOnMs }}ms</span>
      </div>
      <div class="dur-row">
        <span class="dur-label">OFF duration</span>
        <input type="range" min="100" max="2000" step="50" v-model.number="blinkOffMs" class="dur-slider" @change="applyBlink" />
        <span class="dur-val">{{ blinkOffMs }}ms</span>
      </div>
    </div>

    <div class="status-card" :class="{ active: activeMode !== 'none' }">
      <div class="status-indicator" :class="{ on: torchIndicatorOn }"></div>
      <div class="status-info">
        <div class="status-title">{{ statusTitle }}</div>
        <div class="status-desc">{{ statusDesc }}</div>
      </div>
    </div>

    <div v-if="activeMode === 'screen'" class="screen-overlay" :style="{ background: screenColor }"></div>

    <div v-if="error" class="error-box"><i-lucide:alert-triangle />{{ error }}</div>

    <div class="info-note">
      <i-lucide:info />
      Torch runs on the phone — controls the real camera flash remotely.
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import { startTorchPatternGQL, stopTorchPatternGQL } from '@/lib/api/mutation'

const activeMode = ref<'none'|'normal'|'blink'|'sos'|'screen'>('none')
const torchIndicatorOn = ref(false)
const error = ref('')
const blinkOnMs = ref(500)
const blinkOffMs = ref(500)
const screenColor = ref('transparent')
let screenTimer = 0
let blinkIndicatorTimer = 0

const statusTitle = computed(() => {
  if (activeMode.value === 'none') return 'OFF'
  if (activeMode.value === 'normal') return 'ON'
  if (activeMode.value === 'blink') return 'BLINKING'
  if (activeMode.value === 'sos') return 'SOS SIGNAL'
  if (activeMode.value === 'screen') return 'SCREEN FLASH'
  return 'OFF'
})
const statusDesc = computed(() => {
  if (activeMode.value === 'none') return 'FlashLight is OFF.'
  if (activeMode.value === 'normal') return 'Phone flash is ON.'
  if (activeMode.value === 'blink') return `Blinking: ${blinkOnMs.value}ms ON / ${blinkOffMs.value}ms OFF`
  if (activeMode.value === 'sos') return 'Sending SOS signal (... --- ...) on phone flash'
  if (activeMode.value === 'screen') return 'Using browser screen as flash'
  return 'FlashLight is OFF.'
})

function clearTimers() {
  clearInterval(screenTimer)
  clearInterval(blinkIndicatorTimer)
  torchIndicatorOn.value = false
  screenColor.value = 'transparent'
}

async function stopAll() {
  clearTimers()
  activeMode.value = 'none'
  try { await gqlFetch(stopTorchPatternGQL) } catch {}
}

const screenColors = ['#ffffff','#ffff00','#ff4444','#00ff00','#4488ff','#ffffff']

async function selectMode(mode: 'normal'|'blink'|'sos'|'screen') {
  error.value = ''
  clearTimers()

  if (activeMode.value === mode) {
    await stopAll()
    return
  }

  if (mode === 'screen') {
    activeMode.value = 'screen'
    let idx = 0
    screenTimer = window.setInterval(() => {
      screenColor.value = screenColors[idx++ % screenColors.length]
      torchIndicatorOn.value = idx % 2 === 0
    }, 300)
    return
  }

  try {
    const onMs = mode === 'blink' ? blinkOnMs.value : 500
    const offMs = mode === 'blink' ? blinkOffMs.value : 500
    const r = await gqlFetch(startTorchPatternGQL, { type: mode, onMs, offMs })
    if (r.errors?.length) {
      error.value = r.errors[0].message
      return
    }
    activeMode.value = mode
    if (mode === 'normal') {
      torchIndicatorOn.value = true
    } else {
      let on = false
      const interval = mode === 'blink' ? Math.min(blinkOnMs.value, blinkOffMs.value) : 300
      blinkIndicatorTimer = window.setInterval(() => {
        on = !on
        torchIndicatorOn.value = on
      }, interval)
    }
  } catch {
    error.value = 'Could not reach device. Make sure the phone app is running.'
  }
}

async function applyBlink() {
  if (activeMode.value === 'blink') {
    clearTimers()
    await selectMode('blink')
  }
}

onUnmounted(stopAll)
</script>

<style scoped lang="scss">
.sensor-root { padding: 18px 20px 32px; display: flex; flex-direction: column; gap: 18px; max-width: 700px; margin: 0 auto; position: relative; }
.sensor-header { display: flex; align-items: center; gap: 12px; }
.back-btn, .reset-btn {
  background: none; border: none; cursor: pointer; color: var(--md-sys-color-on-surface);
  display: flex; align-items: center; padding: 6px; border-radius: 50%; transition: background 0.18s;
  &:hover { background: var(--md-sys-color-surface-container); }
  svg { width: 22px; height: 22px; }
}
.sensor-title { font-size: 1.4rem; font-weight: 700; margin: 0; flex: 1; }
.mode-label { font-size: 1rem; font-weight: 600; color: var(--md-sys-color-on-surface-variant); }
.mode-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.mode-card {
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px;
  padding: 24px 16px; border-radius: 18px; border: 1.5px solid var(--md-sys-color-outline-variant);
  background: var(--md-sys-color-surface-container); cursor: pointer;
  font-size: 0.9rem; font-weight: 600; color: var(--md-sys-color-on-surface); transition: all 0.2s;
  &:hover { transform: translateY(-2px); box-shadow: 0 6px 18px rgba(0,0,0,0.1); }
  &.active { border-color: #0d9488; background: rgba(13,148,136,0.1); color: #0d9488; .mode-icon, .sos-text-icon { color: #0d9488; } }
}
.mode-icon { width: 28px; height: 28px; color: var(--md-sys-color-on-surface-variant); }
.sos-text-icon { font-size: 1.4rem; font-weight: 900; color: var(--md-sys-color-on-surface-variant); }
.duration-card { background: var(--md-sys-color-surface-container); border-radius: 16px; padding: 16px; display: flex; flex-direction: column; gap: 12px; }
.dur-header { display: flex; align-items: center; gap: 8px; }
.dur-icon { width: 18px; height: 18px; color: #0d9488; }
.dur-title { font-weight: 600; font-size: 0.95rem; }
.dur-row { display: flex; align-items: center; gap: 10px; }
.dur-label { font-size: 0.82rem; color: var(--md-sys-color-on-surface-variant); min-width: 90px; }
.dur-slider { flex: 1; accent-color: #0d9488; }
.dur-val { font-size: 0.82rem; font-weight: 600; min-width: 48px; text-align: right; color: #0d9488; }
.status-card {
  display: flex; align-items: center; gap: 16px; padding: 18px 20px; border-radius: 16px;
  background: var(--md-sys-color-surface-container); transition: background 0.3s;
  &.active { background: rgba(13,148,136,0.08); }
}
.status-indicator {
  width: 18px; height: 18px; border-radius: 50%; background: #9ca3af; transition: background 0.3s, box-shadow 0.3s; flex-shrink: 0;
  &.on { background: #fbbf24; box-shadow: 0 0 14px rgba(251,191,36,0.7); animation: glow 0.8s ease-in-out infinite alternate; }
}
@keyframes glow { from { box-shadow: 0 0 8px rgba(251,191,36,0.6); } to { box-shadow: 0 0 22px rgba(251,191,36,0.9); } }
.status-title { font-size: 1.1rem; font-weight: 700; }
.status-desc { font-size: 0.84rem; color: var(--md-sys-color-on-surface-variant); margin-top: 2px; }
.screen-overlay { position: fixed; inset: 0; z-index: 9999; pointer-events: none; transition: background 0.1s; }
.error-box {
  display: flex; align-items: center; gap: 10px; padding: 14px; border-radius: 14px;
  background: rgba(239,68,68,0.08); color: #ef4444; font-size: 0.9rem;
  svg { width: 18px; height: 18px; }
}
.info-note {
  display: flex; align-items: center; gap: 8px; padding: 12px 14px; border-radius: 14px;
  background: rgba(13,148,136,0.07); color: #0d9488; font-size: 0.84rem; font-weight: 500;
  svg { width: 16px; height: 16px; flex-shrink: 0; }
}
</style>
