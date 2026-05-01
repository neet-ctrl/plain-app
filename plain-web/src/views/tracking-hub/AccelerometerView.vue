<template>
  <div class="sensor-root">
    <div class="sensor-header">
      <button class="back-btn" @click="$router.back()">
        <i-lucide:arrow-left />
      </button>
      <h2 class="sensor-title">Accelerometer</h2>
      <div class="live-badge" :class="{ active: polling }">
        <span class="dot" />
        {{ polling ? 'Live' : 'Stopped' }}
      </div>
    </div>

    <div class="info-banner">
      <i-lucide:info class="info-icon" />
      <span>This tool helps to measure the acceleration of device in any axis (reading from phone sensors via GraphQL).</span>
    </div>

    <div class="accel-grid">
      <div class="axis-section">
        <h3 class="axis-label">Gravity</h3>
        <div class="bars-row">
          <div class="bar-col" v-for="(axis, i) in ['X','Y','Z']" :key="'g'+axis">
            <div class="bar-track">
              <div class="bar-fill" :style="{ height: gravityBarH(i) + '%', background: gravityColors[i] }" />
            </div>
            <span class="bar-axis">{{ axis }}</span>
            <span class="bar-val">{{ gravityVals[i] }}</span>
          </div>
        </div>
      </div>
      <div class="axis-section">
        <h3 class="axis-label">Motion</h3>
        <div class="bars-row">
          <div class="bar-col" v-for="(axis, i) in ['X','Y','Z']" :key="'m'+axis">
            <div class="bar-track">
              <div class="bar-fill motion" :style="{ height: motionBarH(i) + '%' }" />
            </div>
            <span class="bar-axis">{{ axis }}</span>
            <span class="bar-val">{{ motionVals[i] }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="angle-label">Angle {{ angle }}°</div>

    <div class="tilt-card" :style="{ background: tiltBg }">
      <span class="tilt-text">up/down {{ upDown }}</span>
      <span class="tilt-text">Left/right {{ leftRight }}</span>
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
const gravityVals = ref([0, 0, 0])
const motionVals = ref([0, 0, 0])
const angle = ref(0)
const upDown = ref(0)
const leftRight = ref(0)
let timer = 0

const gravityColors = ['#9ca3af', '#22c55e', '#3b82f6']

function gravityBarH(i: number) { return Math.min(100, (Math.abs(gravityVals.value[i]) / 20) * 100) }
function motionBarH(i: number) { return Math.min(100, (Math.abs(motionVals.value[i]) / 20) * 100) }

const tiltBg = computed(() => {
  const v = Math.max(Math.abs(upDown.value), Math.abs(leftRight.value))
  if (v > 6) return '#ef4444'
  if (v > 3) return '#f59e0b'
  return '#ef4444'
})

async function poll() {
  try {
    const r = await gqlFetch<{ accelerometerData: any }>(accelerometerDataGQL)
    if (!r.errors && r.data?.accelerometerData) {
      const d = r.data.accelerometerData
      gravityVals.value = [d.gravityX, d.gravityY, d.gravityZ]
      motionVals.value = [d.motionX, d.motionY, d.motionZ]
      angle.value = d.angle
      upDown.value = Math.round(d.gravityY)
      leftRight.value = Math.round(d.gravityX)
      polling.value = true
    }
  } catch (e: any) {
    error.value = 'Could not reach device. Make sure the phone app is running.'
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
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 700px;
  margin: 0 auto;
}
.sensor-header { display: flex; align-items: center; gap: 12px; }
.back-btn {
  background: none; border: none; cursor: pointer;
  color: var(--md-sys-color-on-surface);
  display: flex; align-items: center;
  padding: 6px; border-radius: 50%;
  transition: background 0.18s;
  &:hover { background: var(--md-sys-color-surface-container); }
  svg { width: 22px; height: 22px; }
}
.sensor-title { font-size: 1.4rem; font-weight: 700; margin: 0; flex: 1; }
.live-badge {
  display: flex; align-items: center; gap: 6px;
  padding: 4px 12px; border-radius: 999px;
  background: var(--md-sys-color-surface-container);
  font-size: 0.78rem; font-weight: 600;
  color: var(--md-sys-color-on-surface-variant);
  .dot { width: 8px; height: 8px; border-radius: 50%; background: #9ca3af; }
  &.active { background: rgba(34,197,94,0.14); color: #16a34a; .dot { background: #22c55e; animation: pulse 1.4s infinite; } }
}
@keyframes pulse {
  0%, 100% { opacity: 1; } 50% { opacity: 0.4; }
}
.info-banner {
  display: flex; align-items: flex-start; gap: 10px;
  padding: 14px 16px; border-radius: 14px;
  border: 1px solid rgba(34,197,94,0.3);
  background: rgba(34,197,94,0.06);
  color: var(--md-sys-color-on-surface-variant); font-size: 0.88rem; line-height: 1.5;
}
.info-icon { width: 18px; height: 18px; color: #16a34a; flex-shrink: 0; margin-top: 2px; }
.accel-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }
.axis-section { display: flex; flex-direction: column; gap: 12px; }
.axis-label { font-size: 1.1rem; font-weight: 600; margin: 0; text-align: center; }
.bars-row { display: flex; justify-content: center; gap: 20px; }
.bar-col { display: flex; flex-direction: column; align-items: center; gap: 6px; }
.bar-track {
  width: 38px; height: 130px; border-radius: 12px;
  background: var(--md-sys-color-surface-container);
  display: flex; align-items: flex-end; overflow: hidden;
}
.bar-fill { width: 100%; border-radius: 12px; transition: height 0.25s ease; background: #9ca3af; }
.bar-fill.motion { background: #6366f1; }
.bar-axis { font-size: 0.82rem; color: var(--md-sys-color-on-surface-variant); }
.bar-val { font-size: 0.88rem; font-weight: 600; }
.angle-label { text-align: center; font-size: 1.3rem; font-weight: 700; }
.tilt-card {
  border-radius: 20px; padding: 28px 24px;
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  margin: 0 auto; min-width: 220px; max-width: 320px; width: 100%;
  transition: background 0.3s;
}
.tilt-text { color: #fff; font-size: 1.05rem; font-weight: 600; }
.error-box {
  display: flex; align-items: center; gap: 10px;
  padding: 16px; border-radius: 14px;
  background: rgba(239,68,68,0.08); color: #ef4444; font-size: 0.9rem;
  svg { width: 20px; height: 20px; }
}
</style>
