<template>
  <div class="sensor-root">
    <div class="sensor-header">
      <button class="back-btn" @click="$router.back()">
        <i-lucide:arrow-left />
      </button>
      <h2 class="sensor-title">Accelerometer</h2>
      <div class="live-badge" :class="{ active: polling }">
        <span class="dot" />{{ polling ? 'Live' : 'Stopped' }}
      </div>
    </div>

    <!-- 3D Phone Visualization -->
    <div class="scene-wrap">
      <div class="scene">
        <div class="phone-3d" :style="phone3dStyle">
          <div class="phone-face front">
            <div class="phone-screen">
              <div class="screen-content">
                <div class="screen-bars">
                  <span v-for="i in 12" :key="i" class="bar"
                    :style="{ height: (20 + Math.abs(Math.sin(i + Date.now()/400)) * 28) + 'px', background: barColor(i) }" />
                </div>
                <div class="screen-label">{{ orientLabel }}</div>
              </div>
            </div>
            <div class="phone-notch"></div>
            <div class="phone-home"></div>
          </div>
          <div class="phone-face back">
            <div class="camera-bump">
              <div class="cam cam1"></div>
              <div class="cam cam2"></div>
              <div class="cam cam3"></div>
            </div>
          </div>
          <div class="phone-face left"></div>
          <div class="phone-face right">
            <div class="btn vol1"></div>
            <div class="btn vol2"></div>
            <div class="btn power"></div>
          </div>
          <div class="phone-face top"></div>
          <div class="phone-face bottom">
            <div class="port"></div>
          </div>
        </div>
      </div>

      <!-- Axis arrows -->
      <div class="axis-hud">
        <div class="axis-arrow x-axis" :style="{ transform: `scaleX(${Math.max(0.1, (gravityVals[0]+10)/20)})` }">
          <span>X {{ gravityVals[0] > 0 ? '+' : '' }}{{ gravityVals[0] }}</span>
        </div>
        <div class="axis-arrow y-axis" :style="{ transform: `scaleY(${Math.max(0.1, (gravityVals[1]+10)/20)})` }">
          <span>Y {{ gravityVals[1] > 0 ? '+' : '' }}{{ gravityVals[1] }}</span>
        </div>
        <div class="axis-arrow z-axis" :style="{ transform: `scaleX(${Math.max(0.1, (gravityVals[2]+10)/20)})` }">
          <span>Z {{ gravityVals[2] > 0 ? '+' : '' }}{{ gravityVals[2] }}</span>
        </div>
      </div>
    </div>

    <!-- Orientation badge -->
    <div class="orient-card">
      <i-lucide:smartphone class="orient-icon" />
      <div>
        <div class="orient-title">{{ orientLabel }}</div>
        <div class="orient-sub">Tilt {{ Math.abs(roll).toFixed(0) }}° roll · {{ Math.abs(pitch).toFixed(0) }}° pitch · {{ angle }}° angle</div>
      </div>
    </div>

    <!-- Motion vector bars -->
    <div class="section-title">Gravity Vector <span class="unit">(m/s²)</span></div>
    <div class="bars-row">
      <div class="bar-col" v-for="(axis, i) in ['X','Y','Z']" :key="'g'+i">
        <div class="bar-track">
          <div class="bar-fill neg" :style="{ height: negH(gravityVals[i]) + '%' }" />
          <div class="bar-center-line" />
          <div class="bar-fill pos" :style="{ height: posH(gravityVals[i]) + '%', background: gravityColors[i] }" />
        </div>
        <span class="bar-axis">{{ axis }}</span>
        <span class="bar-val" :style="{ color: gravityColors[i] }">{{ gravityVals[i] }}</span>
      </div>
    </div>

    <div class="section-title">Linear Motion <span class="unit">(m/s²)</span></div>
    <div class="bars-row">
      <div class="bar-col" v-for="(axis, i) in ['X','Y','Z']" :key="'m'+i">
        <div class="bar-track">
          <div class="bar-fill neg" :style="{ height: negH(motionVals[i]) + '%' }" />
          <div class="bar-center-line" />
          <div class="bar-fill pos motion" :style="{ height: posH(motionVals[i]) + '%' }" />
        </div>
        <span class="bar-axis">{{ axis }}</span>
        <span class="bar-val">{{ motionVals[i] }}</span>
      </div>
    </div>

    <div class="info-banner">
      <i-lucide:info class="info-icon" />
      <span>Reading TYPE_GRAVITY + TYPE_LINEAR_ACCELERATION from the phone's hardware sensors via GraphQL.</span>
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
const gravityVals = ref([0.0, 0.0, 0.0])
const motionVals = ref([0.0, 0.0, 0.0])
const angle = ref(0)
const roll = ref(0)    // rotation around X axis (tilt forward/back) degrees
const pitch = ref(0)   // rotation around Y axis (tilt left/right) degrees
const yaw = ref(0)     // rotation around Z axis (spin in plane) degrees
let timer = 0
let animTimer = 0
let tick = 0

const gravityColors = ['#ef4444', '#22c55e', '#3b82f6']

// Derived angles from gravity vector
function toDeg(r: number) { return r * 180 / Math.PI }

const orientLabel = computed(() => {
  const [gx, gy, gz] = gravityVals.value
  const absX = Math.abs(gx), absY = Math.abs(gy), absZ = Math.abs(gz)
  const dominant = Math.max(absX, absY, absZ)
  if (dominant === absZ) return gz > 0 ? 'Face Up' : 'Face Down'
  if (dominant === absY) return gy > 0 ? 'Portrait Up' : 'Portrait Down'
  return gx > 0 ? 'Landscape Right' : 'Landscape Left'
})

const phone3dStyle = computed(() => {
  // Map gravity vector to CSS rotateX / rotateY
  // rotateX: tilts top toward/away from viewer
  // rotateY: tilts left/right
  const [gx, gy, gz] = gravityVals.value
  const rx = toDeg(Math.atan2(gy, gz))   // pitch: tilt up/down
  const ry = toDeg(Math.atan2(-gx, Math.sqrt(gy * gy + gz * gz))) // roll: tilt left/right
  const rz = yaw.value                     // spin, from angle sensor
  return {
    transform: `rotateX(${rx.toFixed(1)}deg) rotateY(${ry.toFixed(1)}deg) rotateZ(${rz.toFixed(1)}deg)`,
    transition: 'transform 0.25s cubic-bezier(0.34,1.56,0.64,1)',
  }
})

function barColor(i: number) {
  const colors = ['#ef4444','#f59e0b','#22c55e','#3b82f6','#8b5cf6','#ec4899',
                  '#06b6d4','#84cc16','#f97316','#6366f1','#14b8a6','#a855f7']
  return colors[i % colors.length]
}

function posH(v: number) { return v > 0 ? Math.min(50, (v / 10) * 50) : 0 }
function negH(v: number) { return v < 0 ? Math.min(50, (-v / 10) * 50) : 0 }

async function poll() {
  try {
    const r = await gqlFetch<{ accelerometerData: any }>(accelerometerDataGQL)
    if (!r.errors && r.data?.accelerometerData) {
      const d = r.data.accelerometerData
      gravityVals.value = [parseFloat(d.gravityX.toFixed(2)), parseFloat(d.gravityY.toFixed(2)), parseFloat(d.gravityZ.toFixed(2))]
      motionVals.value = [parseFloat(d.motionX.toFixed(2)), parseFloat(d.motionY.toFixed(2)), parseFloat(d.motionZ.toFixed(2))]
      angle.value = Math.round(d.angle)
      roll.value = toDeg(Math.atan2(d.gravityY, d.gravityZ))
      pitch.value = toDeg(Math.atan2(-d.gravityX, Math.sqrt(d.gravityY ** 2 + d.gravityZ ** 2)))
      yaw.value = d.angle
      polling.value = true
      error.value = ''
    }
  } catch {
    error.value = 'Could not reach device. Make sure the phone app is running.'
    polling.value = false
  }
}

onMounted(() => {
  poll()
  timer = window.setInterval(poll, 200)
  // Animate the screen bars by forcing rerender
  animTimer = window.setInterval(() => { tick++ }, 120)
})
onUnmounted(() => {
  clearInterval(timer)
  clearInterval(animTimer)
})
</script>

<style scoped lang="scss">
.sensor-root {
  padding: 16px 18px 36px;
  display: flex; flex-direction: column; gap: 16px;
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
  &.active { background: rgba(34,197,94,0.14); color: #16a34a;
    .dot { background: #22c55e; animation: pulse 1.4s infinite; } }
}
@keyframes pulse { 0%,100%{opacity:1}50%{opacity:.4} }

/* ---- 3D SCENE ---- */
.scene-wrap {
  position: relative;
  background: radial-gradient(ellipse at 50% 30%, #1e293b 0%, #0f172a 100%);
  border-radius: 24px; overflow: hidden;
  height: 340px; display: flex; align-items: center; justify-content: center;
}
.scene {
  perspective: 800px;
  perspective-origin: 50% 40%;
  display: flex; align-items: center; justify-content: center;
  width: 100%; height: 100%;
}

/* The 3D phone box */
$pw: 90px;   // phone width
$ph: 180px;  // phone height
$pd: 16px;   // phone depth

.phone-3d {
  position: relative;
  width: $pw; height: $ph;
  transform-style: preserve-3d;
  transform: rotateX(0deg) rotateY(0deg);
  filter: drop-shadow(0 20px 40px rgba(0,0,0,0.6));
}

/* FACES */
.phone-face {
  position: absolute;
  backface-visibility: visible;
}

/* Front */
.phone-face.front {
  width: $pw; height: $ph;
  background: linear-gradient(160deg, #1e293b, #0f172a);
  border-radius: 16px;
  border: 2px solid rgba(255,255,255,0.12);
  transform: translateZ(calc(#{$pd} / 2));
  display: flex; flex-direction: column; align-items: center; justify-content: space-between;
  padding: 10px 6px 8px;
  overflow: hidden;
  box-shadow: inset 0 0 20px rgba(0,0,0,0.5);
}

/* Screen on front */
.phone-screen {
  width: 100%;
  flex: 1;
  background: linear-gradient(135deg, #0ea5e9 0%, #6366f1 50%, #ec4899 100%);
  border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  overflow: hidden;
  position: relative;
}
.screen-content {
  display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 6px;
}
.screen-bars {
  display: flex; align-items: flex-end; gap: 2px; height: 40px;
}
.bar {
  width: 4px; border-radius: 2px;
  transition: height 0.12s ease;
  opacity: 0.85;
}
.screen-label {
  font-size: 7px; color: rgba(255,255,255,0.9); font-weight: 700;
  text-align: center; letter-spacing: 0.3px;
}

.phone-notch {
  width: 24px; height: 6px; background: #0f172a; border-radius: 0 0 8px 8px;
  position: absolute; top: 10px; left: 50%; transform: translateX(-50%);
}
.phone-home {
  width: 18px; height: 6px; background: rgba(255,255,255,0.15); border-radius: 4px;
  margin-bottom: 2px;
}

/* Back */
.phone-face.back {
  width: $pw; height: $ph;
  background: linear-gradient(160deg, #334155, #1e293b);
  border-radius: 16px;
  border: 2px solid rgba(255,255,255,0.07);
  transform: rotateY(180deg) translateZ(calc(#{$pd} / 2));
  display: flex; align-items: flex-start; justify-content: flex-end;
  padding: 14px 8px;
}
.camera-bump {
  background: rgba(15,23,42,0.7); border-radius: 10px; padding: 6px;
  display: flex; flex-direction: column; gap: 4px;
  border: 1px solid rgba(255,255,255,0.07);
}
.cam {
  width: 16px; height: 16px; border-radius: 50%;
  background: radial-gradient(circle, #334155, #0f172a);
  border: 2px solid rgba(255,255,255,0.15);
  box-shadow: inset 0 0 4px rgba(0,0,0,0.8), 0 0 4px rgba(14,165,233,0.4);
}

/* Left/Right/Top/Bottom edges */
.phone-face.left {
  width: $pd; height: $ph;
  background: linear-gradient(to bottom, #334155, #1e293b);
  transform: rotateY(-90deg) translateZ(0);
  border-radius: 8px;
  left: 0; top: 0;
}
.phone-face.right {
  width: $pd; height: $ph;
  background: linear-gradient(to bottom, #334155, #1e293b);
  transform: rotateY(90deg) translateZ(calc(#{$pw}));
  border-radius: 8px;
  left: 0; top: 0;
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px;
}
.btn {
  background: #475569; border-radius: 3px;
  &.vol1, &.vol2 { width: 3px; height: 16px; }
  &.power { width: 3px; height: 22px; background: #64748b; margin-top: 8px; }
}
.phone-face.top {
  width: $pw; height: $pd;
  background: linear-gradient(to right, #334155, #1e293b);
  transform: rotateX(90deg) translateZ(0);
  border-radius: 8px 8px 0 0;
  left: 0; top: 0;
}
.phone-face.bottom {
  width: $pw; height: $pd;
  background: linear-gradient(to right, #334155, #1e293b);
  transform: rotateX(-90deg) translateZ(calc(#{$ph}));
  border-radius: 0 0 8px 8px;
  left: 0; top: 0;
  display: flex; align-items: center; justify-content: center;
}
.port {
  width: 20px; height: 4px; background: #0f172a; border-radius: 2px;
}

/* Axis HUD */
.axis-hud {
  position: absolute; bottom: 12px; left: 14px;
  display: flex; flex-direction: column; gap: 4px;
}
.axis-arrow {
  display: flex; align-items: center; gap: 4px; transform-origin: left center;
  span { font-size: 10px; font-weight: 700; white-space: nowrap; padding: 2px 6px; border-radius: 6px; }
  &.x-axis { span { background: rgba(239,68,68,0.25); color: #fca5a5; } }
  &.y-axis { span { background: rgba(34,197,94,0.25); color: #86efac; } }
  &.z-axis { span { background: rgba(59,130,246,0.25); color: #93c5fd; } }
}

/* Orient card */
.orient-card {
  display: flex; align-items: center; gap: 14px;
  background: var(--md-sys-color-surface-container); border-radius: 18px; padding: 16px 20px;
  svg { width: 26px; height: 26px; color: #6366f1; flex-shrink: 0; }
}
.orient-title { font-size: 1.1rem; font-weight: 700; }
.orient-sub { font-size: 0.8rem; color: var(--md-sys-color-on-surface-variant); margin-top: 2px; }

/* Data bars */
.section-title {
  font-size: 0.88rem; font-weight: 700; color: var(--md-sys-color-on-surface-variant);
  text-transform: uppercase; letter-spacing: 0.8px; margin-top: 4px;
  .unit { font-weight: 400; text-transform: none; }
}
.bars-row { display: flex; gap: 16px; padding: 0 8px; }
.bar-col { display: flex; flex-direction: column; align-items: center; gap: 4px; flex: 1; }
.bar-track {
  width: 100%; height: 100px; border-radius: 12px;
  background: var(--md-sys-color-surface-container);
  display: flex; flex-direction: column; justify-content: center;
  align-items: center; overflow: hidden; position: relative; gap: 0;
}
.bar-fill {
  width: 100%; transition: height 0.18s ease;
  &.pos { border-radius: 12px 12px 0 0; background: #9ca3af; }
  &.pos.motion { background: #8b5cf6; }
  &.neg { border-radius: 0 0 12px 12px; background: rgba(239,68,68,0.55); }
}
.bar-center-line {
  width: 100%; height: 1.5px; background: rgba(255,255,255,0.15); flex-shrink: 0;
}
.bar-axis { font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); font-weight: 600; }
.bar-val { font-size: 0.82rem; font-weight: 700; font-variant-numeric: tabular-nums; }

.info-banner {
  display: flex; align-items: flex-start; gap: 10px; padding: 12px 14px; border-radius: 14px;
  border: 1px solid rgba(34,197,94,0.3); background: rgba(34,197,94,0.06);
  color: var(--md-sys-color-on-surface-variant); font-size: 0.84rem; line-height: 1.5;
}
.info-icon { width: 16px; height: 16px; color: #16a34a; flex-shrink: 0; margin-top: 2px; }
.error-box {
  display: flex; align-items: center; gap: 10px; padding: 14px; border-radius: 14px;
  background: rgba(239,68,68,0.08); color: #ef4444; font-size: 0.9rem;
  svg { width: 18px; height: 18px; }
}
</style>
