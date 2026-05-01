<template>
  <div class="sensor-root">
    <div class="sensor-header">
      <button class="back-btn" @click="$router.back()">
        <i-lucide:arrow-left />
      </button>
      <h2 class="sensor-title">Sound Meter</h2>
      <div class="mic-btn" :class="{ active: running }" @click="toggleMic">
        <i-lucide:mic v-if="!running" />
        <i-lucide:mic-off v-else />
      </div>
    </div>

    <div class="info-banner">
      <i-lucide:info class="info-icon" />
      <span>Measures sound level using the phone's microphone via the device GraphQL API.</span>
    </div>

    <div v-if="!running && db === 0" class="start-hint">
      <i-lucide:mic class="big-icon" />
      <p>Tap the mic button to start measuring on the phone</p>
    </div>

    <template v-if="running || db > 0">
      <div class="db-value" :style="{ color: dbColor }">{{ db }}</div>
      <div class="db-unit">dB</div>

      <div class="db-bar-wrap">
        <div class="db-bar-track">
          <div class="db-bar-fill" :style="{ width: dbPercent + '%', background: dbColor }" />
        </div>
        <div class="db-ticks"><span>0</span><span>30</span><span>60</span><span>90</span><span>120</span></div>
      </div>

      <div class="db-level" :style="{ color: dbColor }">{{ dbLabel }}</div>

      <div class="ref-card">
        <div class="ref-title">Decibel (dB) Reference</div>
        <div class="ref-row" v-for="row in refTable" :key="row.range"
          :class="{ active: db >= row.min && db < row.max }">
          <span class="ref-range">{{ row.range }}</span>
          <span class="ref-desc">{{ row.desc }}</span>
        </div>
      </div>
    </template>

    <div v-if="error" class="error-box"><i-lucide:alert-triangle />{{ error }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import { soundLevelGQL } from '@/lib/api/query'
import { startSoundMeterGQL, stopSoundMeterGQL } from '@/lib/api/mutation'

const running = ref(false)
const db = ref(0)
const error = ref('')
let pollTimer = 0

const refTable = [
  { range: '0-30 dB', min: 0, max: 30, desc: 'Faint (e.g., whisper, quiet library)' },
  { range: '30-50 dB', min: 30, max: 50, desc: 'Quiet (e.g., quiet home, rainfall)' },
  { range: '50-70 dB', min: 50, max: 70, desc: 'Moderate (e.g., normal conversation, washing machine)' },
  { range: '70-90 dB', min: 70, max: 90, desc: 'Loud (e.g., vacuum cleaner, city traffic).' },
  { range: '90-110 dB', min: 90, max: 110, desc: 'Very Loud (e.g., motorcycle, concert).' },
  { range: '110+ dB', min: 110, max: 200, desc: 'Painful (e.g., jet engine, sirens).' },
]

const dbPercent = computed(() => Math.min(100, (db.value / 120) * 100))
const dbColor = computed(() => {
  if (db.value < 50) return '#16a34a'
  if (db.value < 70) return '#22c55e'
  if (db.value < 90) return '#f59e0b'
  return '#ef4444'
})
const dbLabel = computed(() => {
  if (db.value < 30) return 'Faint'
  if (db.value < 50) return 'Quiet'
  if (db.value < 70) return 'Moderate'
  if (db.value < 90) return 'Loud'
  if (db.value < 110) return 'Very Loud'
  return 'Painful'
})

async function pollLevel() {
  try {
    const r = await gqlFetch<{ soundLevel: number }>(soundLevelGQL)
    if (!r.errors) db.value = Math.round(r.data.soundLevel)
  } catch {}
}

async function toggleMic() {
  error.value = ''
  if (running.value) {
    clearInterval(pollTimer)
    running.value = false
    db.value = 0
    try { await gqlFetch(stopSoundMeterGQL) } catch {}
    return
  }
  try {
    const r = await gqlFetch(startSoundMeterGQL)
    if (r.errors?.length) {
      error.value = r.errors[0].message
      return
    }
    running.value = true
    pollTimer = window.setInterval(pollLevel, 300)
  } catch {
    error.value = 'Could not reach device. Ensure phone app is running.'
  }
}

onUnmounted(async () => {
  clearInterval(pollTimer)
  if (running.value) { try { await gqlFetch(stopSoundMeterGQL) } catch {} }
})
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
.mic-btn {
  width: 44px; height: 44px; border-radius: 50%;
  background: var(--md-sys-color-surface-container);
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; transition: all 0.2s;
  svg { width: 22px; height: 22px; color: var(--md-sys-color-on-surface-variant); }
  &.active { background: #22c55e; svg { color: #fff; } }
  &:hover { transform: scale(1.08); }
}
.info-banner {
  display: flex; align-items: flex-start; gap: 10px; padding: 14px 16px; border-radius: 14px;
  border: 1px solid rgba(34,197,94,0.3); background: rgba(34,197,94,0.06);
  color: var(--md-sys-color-on-surface-variant); font-size: 0.88rem; line-height: 1.5;
}
.info-icon { width: 18px; height: 18px; color: #16a34a; flex-shrink: 0; margin-top: 2px; }
.start-hint {
  display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 40px 20px;
  color: var(--md-sys-color-on-surface-variant);
  .big-icon { width: 48px; height: 48px; color: #16a34a; }
  p { margin: 0; font-size: 0.95rem; }
}
.db-value { text-align: center; font-size: 6rem; font-weight: 700; line-height: 1; margin-top: 16px; transition: color 0.3s; }
.db-unit { text-align: center; color: var(--md-sys-color-on-surface-variant); font-size: 1.1rem; }
.db-level { text-align: center; font-size: 1.5rem; font-weight: 600; margin-top: 4px; }
.db-bar-wrap { padding: 0 8px; }
.db-bar-track { height: 10px; border-radius: 999px; background: var(--md-sys-color-surface-container); overflow: hidden; }
.db-bar-fill { height: 100%; border-radius: 999px; transition: width 0.2s, background 0.3s; }
.db-ticks { display: flex; justify-content: space-between; font-size: 0.72rem; color: var(--md-sys-color-on-surface-variant); margin-top: 4px; }
.ref-card { border-radius: 16px; background: var(--md-sys-color-surface-container); padding: 16px; display: flex; flex-direction: column; gap: 10px; }
.ref-title { font-weight: 700; font-size: 0.95rem; margin-bottom: 4px; }
.ref-row {
  display: flex; gap: 12px; font-size: 0.84rem; color: var(--md-sys-color-on-surface-variant);
  padding: 8px 10px; border-radius: 10px; transition: background 0.2s;
  &.active { background: rgba(34,197,94,0.12); color: var(--md-sys-color-on-surface); font-weight: 600; }
}
.ref-range { font-weight: 600; min-width: 78px; flex-shrink: 0; }
.error-box {
  display: flex; align-items: center; gap: 10px; padding: 14px; border-radius: 14px;
  background: rgba(239,68,68,0.08); color: #ef4444; font-size: 0.9rem;
  svg { width: 18px; height: 18px; }
}
</style>
