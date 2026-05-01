<template>
  <div class="sensor-root">
    <div class="sensor-header">
      <button class="back-btn" @click="$router.back()">
        <i-lucide:arrow-left />
      </button>
      <h2 class="sensor-title">Accelerometer</h2>
    </div>

    <div class="info-banner">
      <i-lucide:info class="info-icon" />
      <span>This tool helps to measure the acceleration of device in any axis.</span>
    </div>

    <div class="accel-grid">
      <div class="axis-section">
        <h3 class="axis-label">Gravity</h3>
        <div class="bars-row">
          <div class="bar-col" v-for="(axis, i) in ['X','Y','Z']" :key="axis">
            <div class="bar-track">
              <div class="bar-fill gravity" :style="{ height: gravityBarHeight(i) + '%', background: gravityColors[i] }" />
            </div>
            <span class="bar-axis">{{ axis }}</span>
            <span class="bar-val">{{ gravityVals[i] }}</span>
          </div>
        </div>
      </div>
      <div class="axis-section">
        <h3 class="axis-label">Motion</h3>
        <div class="bars-row">
          <div class="bar-col" v-for="(axis, i) in ['X','Y','Z']" :key="axis">
            <div class="bar-track">
              <div class="bar-fill motion" :style="{ height: motionBarHeight(i) + '%' }" />
            </div>
            <span class="bar-axis">{{ axis }}</span>
            <span class="bar-val">{{ motionVals[i] }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="angle-label">Angle {{ angle }}°</div>

    <div class="tilt-card" :style="{ background: tiltColor }">
      <span class="tilt-text">up/down {{ upDown }}</span>
      <span class="tilt-text">Left/right {{ leftRight }}</span>
    </div>

    <div v-if="!supported" class="unsupported">
      <i-lucide:alert-triangle />
      DeviceMotion not supported on this device/browser.
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'

const supported = ref(true)
const gravityVals = ref([0, 0, 0])
const motionVals = ref([0, 0, 0])
const angle = ref(0)
const upDown = ref(0)
const leftRight = ref(0)

const gravityColors = ['#9ca3af', '#22c55e', '#3b82f6']

function gravityBarHeight(i: number) {
  const v = Math.abs(gravityVals.value[i])
  return Math.min(100, (v / 20) * 100)
}
function motionBarHeight(i: number) {
  const v = Math.abs(motionVals.value[i])
  return Math.min(100, (v / 20) * 100)
}

const tiltColor = computed(() => {
  const ud = Math.abs(upDown.value)
  const lr = Math.abs(leftRight.value)
  if (ud > 6 || lr > 6) return '#ef4444'
  if (ud > 3 || lr > 3) return '#f59e0b'
  return '#ef4444'
})

function onMotion(e: DeviceMotionEvent) {
  const g = e.accelerationIncludingGravity
  if (g) {
    gravityVals.value = [
      Math.round((g.x ?? 0) * 10) / 10,
      Math.round((g.y ?? 0) * 10) / 10,
      Math.round((g.z ?? 0) * 10) / 10,
    ]
    upDown.value = Math.round(g.y ?? 0)
    leftRight.value = Math.round(g.x ?? 0)
    const x = g.x ?? 0, y = g.y ?? 0
    angle.value = Math.round(Math.atan2(y, x) * (180 / Math.PI))
  }
  const a = e.acceleration
  if (a) {
    motionVals.value = [
      Math.round((a.x ?? 0) * 10) / 10,
      Math.round((a.y ?? 0) * 10) / 10,
      Math.round((a.z ?? 0) * 10) / 10,
    ]
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
  gap: 20px;
  max-width: 700px;
  margin: 0 auto;
}
.sensor-header {
  display: flex;
  align-items: center;
  gap: 12px;
}
.back-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: var(--md-sys-color-on-surface);
  display: flex;
  align-items: center;
  padding: 6px;
  border-radius: 50%;
  transition: background 0.18s;
  &:hover { background: var(--md-sys-color-surface-container); }
  svg { width: 22px; height: 22px; }
}
.sensor-title { font-size: 1.4rem; font-weight: 700; margin: 0; }
.info-banner {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid rgba(34,197,94,0.3);
  background: rgba(34,197,94,0.06);
  color: var(--md-sys-color-on-surface-variant);
  font-size: 0.88rem;
  line-height: 1.5;
}
.info-icon { width: 18px; height: 18px; color: #16a34a; flex-shrink: 0; margin-top: 2px; }

.accel-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}
.axis-section { display: flex; flex-direction: column; gap: 12px; }
.axis-label { font-size: 1.1rem; font-weight: 600; margin: 0; text-align: center; }
.bars-row { display: flex; justify-content: center; gap: 20px; }
.bar-col { display: flex; flex-direction: column; align-items: center; gap: 6px; }
.bar-track {
  width: 38px;
  height: 130px;
  border-radius: 12px;
  background: var(--md-sys-color-surface-container);
  display: flex;
  align-items: flex-end;
  overflow: hidden;
}
.bar-fill {
  width: 100%;
  border-radius: 12px;
  transition: height 0.25s ease;
}
.bar-fill.gravity { background: #9ca3af; }
.bar-fill.motion { background: #6366f1; }
.bar-axis { font-size: 0.82rem; color: var(--md-sys-color-on-surface-variant); }
.bar-val { font-size: 0.88rem; font-weight: 600; }

.angle-label {
  text-align: center;
  font-size: 1.3rem;
  font-weight: 700;
}

.tilt-card {
  border-radius: 20px;
  padding: 28px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin: 0 auto;
  min-width: 220px;
  max-width: 320px;
  width: 100%;
  transition: background 0.3s;
}
.tilt-text {
  color: #fff;
  font-size: 1.05rem;
  font-weight: 600;
}

.unsupported {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px;
  border-radius: 14px;
  background: rgba(239,68,68,0.08);
  color: #ef4444;
  font-size: 0.9rem;
  svg { width: 20px; height: 20px; }
}
</style>
