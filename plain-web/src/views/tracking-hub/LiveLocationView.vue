<template>
  <div class="live-root">
    <header class="page-header">
      <router-link to="/tracking-hub" class="back-btn"><i-lucide:arrow-left /></router-link>
      <div class="title-block">
        <h2 class="title"><i-lucide:map-pin /> {{ $t('hub_live_location_title') }}</h2>
        <p class="sub" v-if="state">
          <span :class="['live-pill', { on: state.running }]">
            <span class="dot" /> {{ state.running ? $t('streaming') : $t('off') }}
          </span>
          <span class="meta">{{ state.totalPoints }} {{ $t('points') }}</span>
          <span v-if="state.latest" class="meta">
            <i-lucide:clock /> {{ formatRelative(state.latest.ts) }}
          </span>
          <span v-if="state.latest && state.latest.battery >= 0" class="meta">
            <i-lucide:battery-charging v-if="state.latest.charging" />
            <i-lucide:battery v-else />
            {{ state.latest.battery }}%
          </span>
        </p>
      </div>
      <div class="actions">
        <button class="ghost-btn" @click="centerOnLatest" :disabled="!state?.latest">
          <i-lucide:crosshair /> {{ $t('center') }}
        </button>
        <button class="ghost-btn" @click="toggleHeatmap">
          <i-lucide:flame /> {{ heatmapOn ? $t('hide_heatmap') : $t('show_heatmap') }}
        </button>
        <button class="ghost-btn danger" @click="onClear" :disabled="!state || state.totalPoints === 0">
          <i-lucide:trash-2 /> {{ $t('clear_history') }}
        </button>
      </div>
    </header>

    <div class="layout">
      <aside class="sidebar">
        <div class="settings-card">
          <div class="row">
            <span class="label">{{ $t('streaming') }}</span>
            <label class="switch">
              <input type="checkbox" :checked="state?.enabled || false" @change="onToggle" />
              <span class="track" />
            </label>
          </div>
          <div class="row col">
            <span class="label">{{ $t('interval_label') }}: <strong>{{ intervalSec }}s</strong></span>
            <input type="range" min="2" max="300" step="1" v-model.number="intervalSec" @change="onIntervalChange" />
          </div>
          <div class="row col">
            <span class="label">{{ $t('min_displacement_label') }}: <strong>{{ minDisp }}m</strong></span>
            <input type="range" min="0" max="200" step="1" v-model.number="minDisp" @change="onIntervalChange" />
          </div>
        </div>

        <div class="latest-card" v-if="state?.latest">
          <h4>{{ $t('latest_fix') }}</h4>
          <div class="kv"><span>{{ $t('latitude') }}</span><span>{{ state.latest.lat.toFixed(6) }}</span></div>
          <div class="kv"><span>{{ $t('longitude') }}</span><span>{{ state.latest.lng.toFixed(6) }}</span></div>
          <div class="kv"><span>{{ $t('accuracy') }}</span><span>{{ state.latest.accuracy.toFixed(1) }}m</span></div>
          <div class="kv"><span>{{ $t('speed') }}</span><span>{{ (state.latest.speed * 3.6).toFixed(1) }} km/h</span></div>
          <div class="kv"><span>{{ $t('altitude') }}</span><span>{{ state.latest.altitude.toFixed(1) }}m</span></div>
          <div class="kv"><span>{{ $t('provider') }}</span><span>{{ state.latest.provider }}</span></div>
          <div class="kv"><span>{{ $t('battery') }}</span><span>{{ state.latest.battery }}%{{ state.latest.charging ? ' ⚡' : '' }}</span></div>
          <div class="kv"><span>{{ $t('time') }}</span><span>{{ new Date(state.latest.ts).toLocaleString() }}</span></div>
        </div>

        <div class="points-card" v-if="points.length > 0">
          <h4>{{ $t('recent_points') }} ({{ points.length }})</h4>
          <ul class="point-list">
            <li v-for="(p, idx) in points.slice(0, 30)" :key="p.ts + '_' + idx" @click="focusPoint(p)" class="point-item">
              <span class="num">{{ idx + 1 }}</span>
              <div class="meta-col">
                <div class="primary">{{ new Date(p.ts).toLocaleTimeString() }}</div>
                <div class="secondary">
                  <span>{{ p.lat.toFixed(4) }}, {{ p.lng.toFixed(4) }}</span>
                  <span class="dot-sep">·</span>
                  <span :class="batteryClass(p.battery)">
                    {{ p.battery >= 0 ? p.battery + '%' : '?' }}
                  </span>
                </div>
              </div>
            </li>
          </ul>
        </div>
      </aside>

      <div class="map-wrap">
        <div ref="mapEl" class="map" />
        <div v-if="!points.length && !state?.enabled" class="empty-overlay">
          <i-lucide:map-pin />
          <h3>{{ $t('no_location_data') }}</h3>
          <p>{{ $t('no_location_data_hint') }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import emitter from '@/plugins/eventbus'
import toast from '@/components/toaster'
import { gqlFetch } from '@/lib/api/gql-client'
import {
  locationTrackingStateGQL,
  locationPointsGQL,
} from '@/lib/api/query'
import {
  setLocationTrackingEnabledGQL,
  setLocationTrackingIntervalGQL,
  clearLocationHistoryGQL,
} from '@/lib/api/mutation'

declare const L: any

interface IPoint {
  ts: number; lat: number; lng: number; accuracy: number; speed: number;
  altitude: number; bearing: number; battery: number; charging: boolean; provider: string;
}
interface IState {
  enabled: boolean; running: boolean; intervalSec: number; minDisplacement: number;
  totalPoints: number; latest: IPoint | null;
}

const state = ref<IState | null>(null)
const points = ref<IPoint[]>([])
const intervalSec = ref(15)
const minDisp = ref(0)
const heatmapOn = ref(false)

const mapEl = ref<HTMLDivElement | null>(null)
let map: any = null
let polyline: any = null
let markers: any[] = []
let liveMarker: any = null
let heatLayer: any = null
let mapInited = false

async function refresh() {
  try {
    const r = await gqlFetch<{ locationTrackingState: IState }>(locationTrackingStateGQL, {})
    if (!r.errors) {
      state.value = r.data.locationTrackingState
      intervalSec.value = state.value.intervalSec
      minDisp.value = state.value.minDisplacement
    }
  } catch (e) {}
  try {
    const r = await gqlFetch<{ locationPoints: IPoint[] }>(locationPointsGQL, { offset: 0, limit: 500 })
    if (!r.errors) {
      points.value = r.data.locationPoints
      drawAll()
    }
  } catch (_) {}
}

function ensureMap() {
  if (mapInited || !mapEl.value || typeof L === 'undefined') return
  mapInited = true
  const center: [number, number] = (state.value?.latest)
    ? [state.value.latest.lat, state.value.latest.lng]
    : (points.value[0] ? [points.value[0].lat, points.value[0].lng] : [20, 0])
  map = L.map(mapEl.value, { zoomControl: true }).setView(center, points.value.length ? 14 : 3)
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19, attribution: '© OpenStreetMap',
  }).addTo(map)
}

function clearMapLayers() {
  if (!map) return
  for (const m of markers) try { map.removeLayer(m) } catch (_) {}
  markers = []
  if (polyline) { try { map.removeLayer(polyline) } catch (_) {}; polyline = null }
  if (liveMarker) { try { map.removeLayer(liveMarker) } catch (_) {}; liveMarker = null }
  if (heatLayer) { try { map.removeLayer(heatLayer) } catch (_) {}; heatLayer = null }
}

function pinIcon(color: string, isLive = false): any {
  const ringClass = isLive ? 'live-ring' : ''
  return L.divIcon({
    className: 'gps-pin',
    html: `<div class="gps-pin-inner ${ringClass}" style="background:${color}"><span></span></div>`,
    iconSize: [18, 18],
    iconAnchor: [9, 9],
  })
}

function batteryColor(level: number): string {
  if (level < 0) return '#94a3b8'
  if (level >= 60) return '#22c55e'
  if (level >= 25) return '#f59e0b'
  return '#ef4444'
}

function batteryClass(level: number): string {
  if (level < 0) return 'bat-unk'
  if (level >= 60) return 'bat-good'
  if (level >= 25) return 'bat-warn'
  return 'bat-low'
}

function drawAll() {
  ensureMap()
  if (!map) return
  clearMapLayers()
  const ordered = [...points.value].reverse() // oldest -> newest
  if (ordered.length === 0) return
  // polyline of path
  const latLngs: any[] = ordered.map((p) => [p.lat, p.lng])
  polyline = L.polyline(latLngs, {
    color: '#6366f1', weight: 4, opacity: 0.85,
    dashArray: '6 8', lineCap: 'round',
  }).addTo(map)
  // pins
  ordered.forEach((p, idx) => {
    const isLive = idx === ordered.length - 1
    const m = L.marker([p.lat, p.lng], { icon: pinIcon(batteryColor(p.battery), isLive) })
    m.bindPopup(buildPopupHtml(p, idx + 1))
    m.addTo(map)
    markers.push(m)
    if (isLive) liveMarker = m
  })
  if (heatmapOn.value) addHeatmap()
}

function addHeatmap() {
  if (!map || typeof (L as any).heatLayer !== 'function') return
  const data = points.value.map((p) => [p.lat, p.lng, 0.6])
  heatLayer = (L as any).heatLayer(data, { radius: 28, blur: 22, maxZoom: 17 }).addTo(map)
}

function toggleHeatmap() {
  heatmapOn.value = !heatmapOn.value
  if (heatmapOn.value) addHeatmap()
  else if (heatLayer) { try { map.removeLayer(heatLayer) } catch (_) {}; heatLayer = null }
}

function buildPopupHtml(p: IPoint, num: number): string {
  const dir = p.bearing.toFixed(0)
  const speedKmh = (p.speed * 3.6).toFixed(1)
  return `
    <div class="gps-popup">
      <div class="popup-h"><b>#${num}</b> · ${new Date(p.ts).toLocaleString()}</div>
      <div class="popup-row"><span>📍 ${p.lat.toFixed(6)}, ${p.lng.toFixed(6)}</span></div>
      <div class="popup-row"><span>🎯 ±${p.accuracy.toFixed(1)}m</span><span>🛰️ ${p.provider}</span></div>
      <div class="popup-row"><span>🚗 ${speedKmh} km/h</span><span>🧭 ${dir}°</span></div>
      <div class="popup-row"><span>⛰️ ${p.altitude.toFixed(1)}m</span><span>${p.charging ? '⚡' : '🔋'} ${p.battery}%</span></div>
    </div>
  `
}

function appendLivePoint(p: IPoint) {
  // Insert at front of array (newest first)
  points.value = [p, ...points.value].slice(0, 500)
  drawAll()
}

function focusPoint(p: IPoint) {
  if (!map) return
  map.setView([p.lat, p.lng], 17, { animate: true })
  const idx = points.value.findIndex((x) => x.ts === p.ts && x.lat === p.lat)
  const orderedIdx = points.value.length - 1 - idx
  if (markers[orderedIdx]) markers[orderedIdx].openPopup()
}

function centerOnLatest() {
  if (!state.value?.latest || !map) return
  map.setView([state.value.latest.lat, state.value.latest.lng], 17, { animate: true })
  if (liveMarker) liveMarker.openPopup()
}

async function onToggle(e: Event) {
  const enabled = (e.target as HTMLInputElement).checked
  try {
    const r = await gqlFetch(setLocationTrackingEnabledGQL, { enabled })
    if (r.errors?.length) toast(r.errors[0].message, 'error')
    else { toast(enabled ? 'Streaming started' : 'Streaming stopped'); refresh() }
  } catch (e: any) { toast(e.message || 'Failed', 'error') }
}

async function onIntervalChange() {
  try {
    await gqlFetch(setLocationTrackingIntervalGQL, { seconds: intervalSec.value, minDisplacement: minDisp.value })
  } catch (_) {}
}

async function onClear() {
  if (!confirm('Clear all stored location history?')) return
  try {
    await gqlFetch(clearLocationHistoryGQL, {})
    points.value = []
    drawAll()
    refresh()
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
  if (state.value) {
    state.value.latest = p
    state.value.totalPoints = p.totalPoints
  }
  appendLivePoint({
    ts: p.ts, lat: p.lat, lng: p.lng, accuracy: p.accuracy, speed: p.speed,
    altitude: p.altitude, bearing: p.bearing, battery: p.battery,
    charging: p.charging, provider: p.provider,
  })
}

onMounted(async () => {
  await refresh()
  await nextTick()
  ensureMap()
  drawAll()
  emitter.on('location_update', onLocUpdate)
})
onUnmounted(() => {
  emitter.off('location_update', onLocUpdate)
  if (map) { try { map.remove() } catch (_) {} ; map = null }
  mapInited = false
})
</script>

<style lang="scss">
.gps-pin-inner {
  width: 18px; height: 18px;
  border-radius: 50%;
  border: 3px solid #fff;
  box-shadow: 0 2px 8px rgba(0,0,0,0.3);
  position: relative;
  display: block;
}
.gps-pin-inner.live-ring::before {
  content: '';
  position: absolute;
  top: -6px; left: -6px;
  width: 24px; height: 24px;
  border-radius: 50%;
  border: 2px solid currentColor;
  animation: livePulse 1.4s infinite;
}
@keyframes livePulse {
  0% { transform: scale(0.8); opacity: 1; }
  100% { transform: scale(1.6); opacity: 0; }
}
.gps-popup .popup-h { font-size: 0.86rem; margin-bottom: 6px; color: #6366f1; }
.gps-popup .popup-row { display: flex; justify-content: space-between; gap: 10px; font-size: 0.78rem; padding: 2px 0; color: #334155; }
</style>

<style lang="scss" scoped>
.live-root { display: flex; flex-direction: column; height: 100%; }

.page-header {
  display: flex; align-items: center; gap: 14px;
  padding: 14px 22px;
  border-bottom: 1px solid var(--md-sys-color-outline-variant);
  flex-wrap: wrap;
}
.back-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 36px; height: 36px; border-radius: 12px;
  background: var(--md-sys-color-surface-container);
  text-decoration: none; color: inherit;
}
.back-btn:hover { background: var(--md-sys-color-surface-container-high); }
.title-block { flex: 1; min-width: 200px; }
.title { margin: 0; display: flex; align-items: center; gap: 8px; font-size: 1.15rem; }
.title svg { width: 22px; height: 22px; color: var(--md-sys-color-primary); }
.sub { margin: 4px 0 0; display: flex; flex-wrap: wrap; gap: 8px; align-items: center; font-size: 0.84rem; color: var(--md-sys-color-on-surface-variant); }
.live-pill {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 2px 10px; border-radius: 999px;
  background: var(--md-sys-color-surface-container);
  font-weight: 600;
}
.live-pill .dot {
  width: 8px; height: 8px; border-radius: 50%; background: #94a3b8;
}
.live-pill.on { background: rgba(34,197,94,0.16); color: #16a34a; }
.live-pill.on .dot { background: #16a34a; animation: pulse 1.4s infinite; }
.meta { display: inline-flex; align-items: center; gap: 4px; }
.meta svg { width: 13px; height: 13px; }

.actions { display: flex; flex-wrap: wrap; gap: 8px; }
.ghost-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 8px 14px; border-radius: 12px;
  background: var(--md-sys-color-surface-container);
  border: 1px solid transparent;
  cursor: pointer; font-size: 0.84rem;
  color: inherit;
}
.ghost-btn:hover { background: var(--md-sys-color-surface-container-high); }
.ghost-btn:disabled { opacity: 0.45; cursor: not-allowed; }
.ghost-btn svg { width: 14px; height: 14px; }
.ghost-btn.danger { color: #dc2626; }

.layout { display: grid; grid-template-columns: 320px 1fr; flex: 1; min-height: 0; gap: 14px; padding: 14px; }
@media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }

.sidebar { display: flex; flex-direction: column; gap: 12px; overflow-y: auto; max-height: 100%; }

.settings-card, .latest-card, .points-card {
  background: var(--md-sys-color-surface-container);
  border-radius: 16px;
  padding: 14px;
}

.row { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.row.col { flex-direction: column; align-items: stretch; gap: 6px; }
.label { font-size: 0.85rem; color: var(--md-sys-color-on-surface-variant); }
.row input[type="range"] { width: 100%; accent-color: #6366f1; }

.switch { position: relative; display: inline-block; width: 42px; height: 24px; }
.switch input { opacity: 0; width: 0; height: 0; }
.switch .track {
  position: absolute; cursor: pointer; inset: 0;
  background: #94a3b8; border-radius: 12px; transition: background 0.2s;
}
.switch .track:before {
  content: ''; position: absolute;
  width: 18px; height: 18px; border-radius: 50%; background: #fff;
  top: 3px; left: 3px; transition: transform 0.2s;
}
.switch input:checked + .track { background: #6366f1; }
.switch input:checked + .track:before { transform: translateX(18px); }

.latest-card h4, .points-card h4 { margin: 0 0 8px; font-size: 0.92rem; }
.kv {
  display: flex; justify-content: space-between; gap: 10px;
  padding: 4px 0; font-size: 0.82rem;
  border-bottom: 1px dashed var(--md-sys-color-outline-variant);
}
.kv:last-child { border-bottom: none; }
.kv span:first-child { color: var(--md-sys-color-on-surface-variant); }

.point-list { list-style: none; padding: 0; margin: 0; max-height: 360px; overflow-y: auto; }
.point-item {
  display: flex; gap: 10px; align-items: center;
  padding: 6px 8px; border-radius: 10px; cursor: pointer;
}
.point-item:hover { background: var(--md-sys-color-surface-container-high); }
.point-item .num {
  width: 22px; height: 22px; border-radius: 50%;
  background: #6366f1; color: #fff;
  font-size: 0.7rem; font-weight: 700;
  display: inline-flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.meta-col { flex: 1; min-width: 0; }
.primary { font-size: 0.82rem; font-weight: 600; }
.secondary { font-size: 0.74rem; color: var(--md-sys-color-on-surface-variant); display: flex; gap: 4px; align-items: center; }
.dot-sep { opacity: 0.5; }
.bat-good { color: #16a34a; }
.bat-warn { color: #d97706; }
.bat-low { color: #dc2626; }
.bat-unk { color: #94a3b8; }

.map-wrap { position: relative; min-height: 400px; border-radius: 16px; overflow: hidden; }
.map { width: 100%; height: 100%; min-height: 500px; border-radius: 16px; }
.empty-overlay {
  position: absolute; inset: 0;
  background: rgba(255,255,255,0.92);
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  text-align: center; padding: 30px;
  pointer-events: none;
}
.empty-overlay svg { width: 56px; height: 56px; color: #94a3b8; margin-bottom: 12px; }
.empty-overlay h3 { margin: 0 0 6px; }
.empty-overlay p { margin: 0; color: var(--md-sys-color-on-surface-variant); max-width: 320px; }

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}
</style>
