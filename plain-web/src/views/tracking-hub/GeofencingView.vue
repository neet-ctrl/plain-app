<template>
  <div class="geo-root">
    <header class="page-header">
      <router-link to="/tracking-hub" class="back-btn"><i-lucide:arrow-left /></router-link>
      <div class="title-block">
        <h2 class="title"><i-lucide:scan /> {{ $t('hub_geofencing_title') }}</h2>
        <p class="sub">{{ fences.length }} {{ $t('fences') }} · {{ insideCount }} {{ $t('currently_inside') }}</p>
      </div>
      <div class="actions">
        <button class="primary-btn" @click="startDrawing"><i-lucide:plus /> {{ $t('new_fence') }}</button>
      </div>
    </header>

    <div class="layout">
      <aside class="sidebar">
        <div v-if="fences.length === 0" class="empty">
          <i-lucide:scan />
          <h4>{{ $t('no_fences') }}</h4>
          <p>{{ $t('no_fences_hint') }}</p>
        </div>
        <div v-for="f in fences" :key="f.id" class="fence-card" :class="{ active: selectedId === f.id, inside: f.currentlyInside }" @click="selectFence(f)">
          <div class="fence-head">
            <span class="dot" :style="{ background: f.color }" />
            <span class="name">{{ f.name }}</span>
            <span v-if="f.currentlyInside" class="inside-badge">{{ $t('inside') }}</span>
            <button class="icon-btn" @click.stop="editFence(f)"><i-lucide:edit-3 /></button>
            <button class="icon-btn danger" @click.stop="removeFence(f)"><i-lucide:trash-2 /></button>
          </div>
          <div class="fence-meta">
            <span class="chip"><i-lucide:circle /> {{ f.radius }}m</span>
            <span class="chip" v-if="f.actionRecordAudio"><i-lucide:mic /> {{ f.recordAudioSec }}s</span>
            <span class="chip" v-if="f.actionLockApps"><i-lucide:lock /> {{ f.lockedAppIds.length }}</span>
            <span class="chip" v-if="f.actionNotifyWeb"><i-lucide:bell /></span>
            <span class="chip" v-if="f.triggerEnter">↘ {{ $t('enter') }}</span>
            <span class="chip" v-if="f.triggerExit">↗ {{ $t('exit') }}</span>
          </div>
          <p v-if="f.customNote" class="note">{{ f.customNote }}</p>
        </div>
      </aside>

      <div class="map-wrap">
        <div ref="mapEl" class="map" />
        <div v-if="drawing" class="drawing-banner">
          <i-lucide:mouse-pointer-2 /> {{ $t('click_to_place_fence') }}
          <button class="cancel-x" @click="cancelDrawing"><i-lucide:x /></button>
        </div>
      </div>

      <aside class="details" v-if="selected">
        <h3>
          <span class="dot" :style="{ background: selected.color }" />
          {{ selected.name }}
        </h3>
        <div class="tabs">
          <button :class="{ on: tab === 'events' }" @click="tab = 'events'">
            <i-lucide:list /> {{ $t('events') }} ({{ events.length }})
          </button>
          <button :class="{ on: tab === 'audios' }" @click="tab = 'audios'">
            <i-lucide:mic /> {{ $t('audio_clips') }} ({{ audios.length }})
          </button>
        </div>

        <div v-if="tab === 'events'" class="event-list">
          <div v-if="events.length === 0" class="muted">{{ $t('no_events_yet') }}</div>
          <div v-for="ev in events" :key="ev.id" class="event-item" :class="ev.type">
            <div class="ev-icon">
              <i-lucide:log-in v-if="ev.type === 'enter'" />
              <i-lucide:log-out v-else />
            </div>
            <div class="ev-body">
              <div class="ev-head">
                <strong>{{ ev.type === 'enter' ? $t('entered') : $t('exited') }}</strong>
                <span class="ev-time">{{ new Date(ev.ts).toLocaleString() }}</span>
              </div>
              <div class="ev-meta">
                <span>📍 {{ ev.lat.toFixed(5) }}, {{ ev.lng.toFixed(5) }}</span>
                <span v-if="ev.batteryLevel >= 0">🔋 {{ ev.batteryLevel }}%</span>
                <span v-if="ev.recordingFile">🎙️ {{ Math.round(ev.recordingDurationMs / 1000) }}s</span>
                <span v-if="ev.notifiedWeb">🔔</span>
                <span v-if="ev.lockedApps && ev.lockedApps.length > 0">🔒 {{ ev.lockedApps.length }}</span>
              </div>
            </div>
          </div>
        </div>

        <div v-else class="audio-list">
          <div v-if="audios.length === 0" class="muted">{{ $t('no_audios_yet') }}</div>
          <div v-for="a in audios" :key="a.id" class="audio-item">
            <i-lucide:file-audio class="audio-icon" />
            <div class="audio-body">
              <div class="audio-head">
                <strong>{{ new Date(a.ts).toLocaleString() }}</strong>
                <span class="audio-meta">{{ Math.round(a.durationMs / 1000) }}s · {{ formatBytes(a.sizeBytes) }}</span>
              </div>
              <audio controls preload="none" :src="audioUrl(a.fileId)" class="player" />
            </div>
            <button class="icon-btn danger" @click="deleteAudio(a)"><i-lucide:trash-2 /></button>
          </div>
        </div>
      </aside>
    </div>

    <div v-if="editing" class="edit-overlay" @click.self="closeEditor">
      <div class="edit-modal">
        <header class="edit-head">
          <h3>{{ editingDraft.id ? $t('edit_fence') : $t('new_fence') }}</h3>
          <button class="icon-btn" @click="closeEditor"><i-lucide:x /></button>
        </header>
        <div class="edit-body">
          <label class="field">
            <span>{{ $t('name') }}</span>
            <input type="text" v-model="editingDraft.name" :placeholder="$t('fence_name_placeholder')" />
          </label>
          <div class="row-2">
            <label class="field">
              <span>{{ $t('radius') }}: <strong>{{ editingDraft.radius }}m</strong></span>
              <input type="range" min="20" max="2000" step="10" v-model.number="editingDraft.radius" @input="updateDraftCircle" />
            </label>
            <label class="field">
              <span>{{ $t('color') }}</span>
              <input type="color" v-model="editingDraft.color" @input="updateDraftCircle" />
            </label>
          </div>

          <fieldset class="group">
            <legend>{{ $t('triggers') }}</legend>
            <label class="check"><input type="checkbox" v-model="editingDraft.triggerEnter" /> ↘ {{ $t('on_enter') }}</label>
            <label class="check"><input type="checkbox" v-model="editingDraft.triggerExit" /> ↗ {{ $t('on_exit') }}</label>
          </fieldset>

          <fieldset class="group">
            <legend>{{ $t('actions') }}</legend>
            <label class="check"><input type="checkbox" v-model="editingDraft.actionNotifyWeb" /> <i-lucide:bell /> {{ $t('action_notify_web') }}</label>
            <label class="check"><input type="checkbox" v-model="editingDraft.actionRecordAudio" /> <i-lucide:mic /> {{ $t('action_record_audio') }}</label>
            <div v-if="editingDraft.actionRecordAudio" class="indent">
              <label class="field">
                <span>{{ $t('audio_duration') }}: <strong>{{ editingDraft.recordAudioSec }}s</strong></span>
                <input type="range" min="5" max="600" step="5" v-model.number="editingDraft.recordAudioSec" />
              </label>
            </div>
            <label class="check"><input type="checkbox" v-model="editingDraft.actionLockApps" /> <i-lucide:lock /> {{ $t('action_lock_apps') }}</label>
            <div v-if="editingDraft.actionLockApps" class="indent">
              <button class="ghost-btn" @click="pickApps"><i-lucide:layout-grid /> {{ $t('select_apps') }} ({{ editingDraft.lockedAppIds.length }})</button>
              <div class="picked-apps" v-if="editingDraft.lockedAppIds.length">
                <span class="pkg-chip" v-for="pid in editingDraft.lockedAppIds" :key="pid">{{ pid }}<i-lucide:x @click="unlockApp(pid)" /></span>
              </div>
              <label class="field">
                <span>{{ $t('lock_duration') }}: <strong>{{ editingDraft.lockAppsDurationSec === 0 ? $t('until_unlocked') : editingDraft.lockAppsDurationSec + 's' }}</strong></span>
                <input type="range" min="0" max="3600" step="30" v-model.number="editingDraft.lockAppsDurationSec" />
              </label>
            </div>
          </fieldset>

          <label class="field">
            <span>{{ $t('custom_note') }}</span>
            <textarea v-model="editingDraft.customNote" rows="2" :placeholder="$t('custom_note_placeholder')"></textarea>
          </label>
          <label class="check"><input type="checkbox" v-model="editingDraft.enabled" /> {{ $t('enabled') }}</label>
        </div>
        <footer class="edit-foot">
          <button class="ghost-btn" @click="closeEditor">{{ $t('cancel') }}</button>
          <button class="primary-btn" @click="saveFence" :disabled="!editingDraft.name.trim()">{{ $t('save') }}</button>
        </footer>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, reactive } from 'vue'
import emitter from '@/plugins/eventbus'
import toast from '@/components/toaster'
import { openModal, popModal } from '@/components/modal'
import AppPickerModal from '@/components/AppPickerModal.vue'
import { gqlFetch } from '@/lib/api/gql-client'
import {
  geofencesGQL, geofenceEventsGQL, geofenceAudiosGQL, locationTrackingStateGQL,
} from '@/lib/api/query'
import {
  saveGeofenceGQL, deleteGeofenceGQL, deleteGeofenceAudioGQL,
} from '@/lib/api/mutation'
import { getFileUrlByPath } from '@/lib/api/file'
import { useTempStore } from '@/stores/temp'
import { storeToRefs } from 'pinia'

declare const L: any

interface IFence {
  id: string; name: string; lat: number; lng: number; radius: number; color: string; enabled: boolean;
  triggerEnter: boolean; triggerExit: boolean;
  actionRecordAudio: boolean; recordAudioSec: number;
  actionNotifyWeb: boolean;
  actionLockApps: boolean; lockedAppIds: string[]; lockAppsDurationSec: number;
  customNote: string; createdAt: number; currentlyInside: boolean;
}
interface IEvent {
  id: string; geofenceId: string; geofenceName: string; type: 'enter' | 'exit'; ts: number;
  lat: number; lng: number; accuracy: number; batteryLevel: number;
  recordingFile: string; recordingDurationMs: number; notifiedWeb: boolean; lockedApps: string[];
}
interface IAudio { id: string; geofenceId: string; geofenceName: string; eventId: string; ts: number; durationMs: number; sizeBytes: number; fileId: string }

const fences = ref<IFence[]>([])
const events = ref<IEvent[]>([])
const audios = ref<IAudio[]>([])
const selectedId = ref<string | null>(null)
const tab = ref<'events' | 'audios'>('events')
const editing = ref(false)
const drawing = ref(false)
const { urlTokenKey } = storeToRefs(useTempStore())

const editingDraft = reactive<IFence>({
  id: '', name: '', lat: 0, lng: 0, radius: 200, color: '#6366f1', enabled: true,
  triggerEnter: true, triggerExit: false,
  actionRecordAudio: false, recordAudioSec: 60,
  actionNotifyWeb: true,
  actionLockApps: false, lockedAppIds: [], lockAppsDurationSec: 0,
  customNote: '', createdAt: 0, currentlyInside: false,
})

const mapEl = ref<HTMLDivElement | null>(null)
let map: any = null
let mapInited = false
let fenceLayers: Record<string, { circle: any; marker: any }> = {}
let draftLayer: any = null
let phoneMarker: any = null
let drawHandler: any = null

const selected = computed(() => fences.value.find((f) => f.id === selectedId.value) || null)
const insideCount = computed(() => fences.value.filter((f) => f.currentlyInside).length)

async function loadFences() {
  const r = await gqlFetch<{ geofences: IFence[] }>(geofencesGQL, {})
  if (r.errors) return
  fences.value = r.data.geofences
  drawAllFences()
}
async function loadEvents(id: string) {
  const r = await gqlFetch<{ geofenceEvents: IEvent[] }>(geofenceEventsGQL, { offset: 0, limit: 100, geofenceId: id })
  if (!r.errors) events.value = r.data.geofenceEvents
}
async function loadAudios(id: string) {
  const r = await gqlFetch<{ geofenceAudios: IAudio[] }>(geofenceAudiosGQL, { offset: 0, limit: 100, geofenceId: id })
  if (!r.errors) audios.value = r.data.geofenceAudios
}
async function loadPhoneLocation() {
  const r = await gqlFetch<{ locationTrackingState: any }>(locationTrackingStateGQL, {})
  if (!r.errors && r.data.locationTrackingState.latest) {
    setPhoneMarker(r.data.locationTrackingState.latest.lat, r.data.locationTrackingState.latest.lng)
  }
}

function setPhoneMarker(lat: number, lng: number) {
  if (!map) return
  if (phoneMarker) { try { map.removeLayer(phoneMarker) } catch (_) {} }
  phoneMarker = L.marker([lat, lng], {
    icon: L.divIcon({
      className: 'phone-marker',
      html: '<div class="phone-pin"><div class="pulse"></div><i class="dot"></i></div>',
      iconSize: [22, 22], iconAnchor: [11, 11],
    }),
  }).bindTooltip('📱 Phone').addTo(map)
}

function ensureMap() {
  if (mapInited || !mapEl.value || typeof L === 'undefined') return
  mapInited = true
  map = L.map(mapEl.value, { zoomControl: true }).setView([20, 0], 3)
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19, attribution: '© OpenStreetMap',
  }).addTo(map)
}

function drawAllFences() {
  if (!map) return
  for (const id in fenceLayers) {
    try { map.removeLayer(fenceLayers[id].circle) } catch (_) {}
    try { map.removeLayer(fenceLayers[id].marker) } catch (_) {}
  }
  fenceLayers = {}
  const bounds: any[] = []
  for (const f of fences.value) {
    const c = L.circle([f.lat, f.lng], {
      radius: f.radius, color: f.color, fillColor: f.color,
      fillOpacity: 0.18, weight: 2,
      dashArray: f.enabled ? null : '6 4',
    }).addTo(map)
    const m = L.marker([f.lat, f.lng], {
      icon: L.divIcon({
        className: 'fence-marker',
        html: `<div class="fence-pin" style="background:${f.color}"><span>${escapeHtml(f.name.charAt(0).toUpperCase())}</span></div>`,
        iconSize: [26, 26], iconAnchor: [13, 13],
      }),
    }).addTo(map)
    m.on('click', () => selectFence(f))
    c.on('click', () => selectFence(f))
    fenceLayers[f.id] = { circle: c, marker: m }
    bounds.push([f.lat, f.lng])
  }
  if (bounds.length > 0 && !selectedId.value) {
    try { map.fitBounds(L.latLngBounds(bounds), { padding: [50, 50], maxZoom: 14 }) } catch (_) {}
  }
}

function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]!))
}

function selectFence(f: IFence) {
  selectedId.value = f.id
  loadEvents(f.id)
  loadAudios(f.id)
  if (map) map.setView([f.lat, f.lng], 16, { animate: true })
}

function startDrawing() {
  drawing.value = true
  if (!map) return
  map.getContainer().style.cursor = 'crosshair'
  drawHandler = (e: any) => {
    map.getContainer().style.cursor = ''
    map.off('click', drawHandler)
    drawHandler = null
    drawing.value = false
    Object.assign(editingDraft, {
      id: '', name: '', lat: e.latlng.lat, lng: e.latlng.lng, radius: 200, color: randomColor(), enabled: true,
      triggerEnter: true, triggerExit: false,
      actionRecordAudio: false, recordAudioSec: 60,
      actionNotifyWeb: true,
      actionLockApps: false, lockedAppIds: [], lockAppsDurationSec: 0,
      customNote: '', createdAt: 0, currentlyInside: false,
    })
    editing.value = true
    nextTick(updateDraftCircle)
  }
  map.on('click', drawHandler)
}

function cancelDrawing() {
  drawing.value = false
  if (map && drawHandler) { map.off('click', drawHandler); map.getContainer().style.cursor = ''; drawHandler = null }
}

function editFence(f: IFence) {
  Object.assign(editingDraft, JSON.parse(JSON.stringify(f)))
  editing.value = true
  nextTick(updateDraftCircle)
}

function updateDraftCircle() {
  if (!map) return
  if (draftLayer) { try { map.removeLayer(draftLayer) } catch (_) {} ; draftLayer = null }
  if (!editing.value) return
  draftLayer = L.circle([editingDraft.lat, editingDraft.lng], {
    radius: editingDraft.radius, color: editingDraft.color, fillColor: editingDraft.color,
    fillOpacity: 0.22, weight: 3, dashArray: '4 4',
  }).addTo(map)
}

function closeEditor() {
  editing.value = false
  if (draftLayer && map) { try { map.removeLayer(draftLayer) } catch (_) {} ; draftLayer = null }
}

async function saveFence() {
  if (!editingDraft.name.trim()) return
  try {
    const input = {
      id: editingDraft.id || null,
      name: editingDraft.name.trim(),
      lat: editingDraft.lat,
      lng: editingDraft.lng,
      radius: editingDraft.radius,
      color: editingDraft.color,
      enabled: editingDraft.enabled,
      triggerEnter: editingDraft.triggerEnter,
      triggerExit: editingDraft.triggerExit,
      actionRecordAudio: editingDraft.actionRecordAudio,
      recordAudioSec: editingDraft.recordAudioSec,
      actionNotifyWeb: editingDraft.actionNotifyWeb,
      actionLockApps: editingDraft.actionLockApps,
      lockedAppIds: editingDraft.lockedAppIds,
      lockAppsDurationSec: editingDraft.lockAppsDurationSec,
      customNote: editingDraft.customNote,
    }
    const r = await gqlFetch<{ saveGeofence: IFence }>(saveGeofenceGQL, { input })
    if (r.errors?.length) { toast(r.errors[0].message, 'error'); return }
    toast('Fence saved')
    closeEditor()
    await loadFences()
    if (r.data?.saveGeofence) selectFence(r.data.saveGeofence)
  } catch (e: any) { toast(e.message || 'Failed', 'error') }
}

async function removeFence(f: IFence) {
  if (!confirm(`Delete fence "${f.name}"?`)) return
  try {
    await gqlFetch(deleteGeofenceGQL, { id: f.id })
    if (selectedId.value === f.id) { selectedId.value = null; events.value = []; audios.value = [] }
    await loadFences()
  } catch (e: any) { toast(e.message || 'Failed', 'error') }
}

function pickApps() {
  openModal(AppPickerModal, {
    title: 'Apps to lock when triggered',
    initial: editingDraft.lockedAppIds,
    done: (ids: string[]) => { editingDraft.lockedAppIds = ids },
  })
}
function unlockApp(pid: string) {
  editingDraft.lockedAppIds = editingDraft.lockedAppIds.filter((x) => x !== pid)
}

function audioUrl(fileId: string): string {
  return getFileUrlByPath(urlTokenKey.value, 'geofence_audio://' + fileId)
}
function formatBytes(b: number): string {
  if (b < 1024) return b + ' B'
  if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB'
  return (b / 1024 / 1024).toFixed(1) + ' MB'
}

async function deleteAudio(a: IAudio) {
  if (!confirm('Delete this audio clip?')) return
  try {
    await gqlFetch(deleteGeofenceAudioGQL, { id: a.id })
    audios.value = audios.value.filter((x) => x.id !== a.id)
  } catch (e: any) { toast(e.message || 'Failed', 'error') }
}

function randomColor(): string {
  const colors = ['#6366f1', '#22c55e', '#f59e0b', '#ef4444', '#06b6d4', '#a855f7', '#ec4899', '#14b8a6']
  return colors[Math.floor(Math.random() * colors.length)]
}

function onLocUpdate(p: any) { setPhoneMarker(p.lat, p.lng) }
function onGeoEvent(_ev: any) { if (selectedId.value) loadEvents(selectedId.value); loadFences() }
function onGeoAudioChanged() { if (selectedId.value) loadAudios(selectedId.value) }
function onGeofencesChanged() { loadFences() }

onMounted(async () => {
  await nextTick()
  ensureMap()
  await loadFences()
  await loadPhoneLocation()
  emitter.on('location_update', onLocUpdate)
  emitter.on('geofence_event', onGeoEvent)
  emitter.on('geofence_audio_changed', onGeoAudioChanged)
  emitter.on('geofences_changed', onGeofencesChanged)
})
onUnmounted(() => {
  emitter.off('location_update', onLocUpdate)
  emitter.off('geofence_event', onGeoEvent)
  emitter.off('geofence_audio_changed', onGeoAudioChanged)
  emitter.off('geofences_changed', onGeofencesChanged)
  if (map) { try { map.remove() } catch (_) {} ; map = null }
  mapInited = false
})
</script>

<style lang="scss">
.fence-pin {
  width: 26px; height: 26px; border-radius: 50%;
  border: 3px solid #fff;
  box-shadow: 0 2px 8px rgba(0,0,0,0.35);
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-weight: 700; font-size: 0.78rem;
}
.phone-pin {
  position: relative; width: 22px; height: 22px;
}
.phone-pin .pulse {
  position: absolute; inset: -4px;
  border-radius: 50%;
  background: rgba(99,102,241,0.3);
  animation: phonePulse 1.6s infinite;
}
.phone-pin .dot {
  position: absolute; inset: 4px;
  border-radius: 50%;
  background: #6366f1; border: 2px solid #fff;
  box-shadow: 0 1px 4px rgba(0,0,0,0.3);
}
@keyframes phonePulse {
  0% { transform: scale(0.8); opacity: 0.7; }
  100% { transform: scale(1.6); opacity: 0; }
}
</style>

<style lang="scss" scoped>
.geo-root { display: flex; flex-direction: column; height: 100%; }
.page-header {
  display: flex; align-items: center; gap: 14px;
  padding: 14px 22px;
  border-bottom: 1px solid var(--md-sys-color-outline-variant);
  flex-wrap: wrap;
}
.back-btn {
  width: 36px; height: 36px; border-radius: 12px;
  background: var(--md-sys-color-surface-container);
  display: inline-flex; align-items: center; justify-content: center;
  text-decoration: none; color: inherit;
}
.title-block { flex: 1; min-width: 200px; }
.title { margin: 0; display: flex; align-items: center; gap: 8px; font-size: 1.15rem; }
.title svg { width: 22px; height: 22px; color: #22c55e; }
.sub { margin: 4px 0 0; color: var(--md-sys-color-on-surface-variant); font-size: 0.84rem; }

.actions { display: flex; gap: 8px; }
.primary-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 9px 16px; border-radius: 12px;
  background: linear-gradient(135deg, #6366f1, #a855f7);
  color: #fff; border: none;
  font-weight: 600; cursor: pointer;
}
.primary-btn:hover { box-shadow: 0 6px 14px rgba(99,102,241,0.3); }
.primary-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.primary-btn svg { width: 14px; height: 14px; }

.layout {
  display: grid; grid-template-columns: 280px 1fr 320px;
  flex: 1; min-height: 0; gap: 12px; padding: 12px;
}
@media (max-width: 1100px) { .layout { grid-template-columns: 240px 1fr; } .details { grid-column: span 2; } }
@media (max-width: 700px) { .layout { grid-template-columns: 1fr; } }

.sidebar { display: flex; flex-direction: column; gap: 8px; overflow-y: auto; max-height: 100%; }
.empty {
  text-align: center; padding: 28px;
  color: var(--md-sys-color-on-surface-variant);
}
.empty svg { width: 40px; height: 40px; opacity: 0.5; margin-bottom: 8px; }
.empty h4 { margin: 4px 0; }

.fence-card {
  background: var(--md-sys-color-surface-container);
  border-radius: 14px;
  padding: 12px;
  cursor: pointer;
  border: 2px solid transparent;
  transition: all 0.18s;
}
.fence-card:hover { background: var(--md-sys-color-surface-container-high); }
.fence-card.active { border-color: #6366f1; }
.fence-card.inside { background: rgba(34,197,94,0.08); border-color: #22c55e; }

.fence-head { display: flex; align-items: center; gap: 6px; }
.fence-head .name { flex: 1; font-weight: 600; font-size: 0.92rem; }
.fence-head .dot { width: 12px; height: 12px; border-radius: 50%; flex-shrink: 0; }
.inside-badge {
  background: #22c55e; color: #fff; font-size: 0.7rem;
  padding: 2px 8px; border-radius: 999px; font-weight: 700;
}
.icon-btn {
  width: 26px; height: 26px; border-radius: 8px;
  background: transparent; border: none; cursor: pointer;
  color: var(--md-sys-color-on-surface-variant);
  display: inline-flex; align-items: center; justify-content: center;
}
.icon-btn:hover { background: var(--md-sys-color-surface-container-highest); }
.icon-btn.danger:hover { color: #dc2626; background: rgba(220,38,38,0.12); }
.icon-btn svg { width: 14px; height: 14px; }

.fence-meta { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 6px; }
.fence-meta .chip {
  display: inline-flex; align-items: center; gap: 3px;
  padding: 2px 8px; border-radius: 999px;
  background: var(--md-sys-color-surface-container-high);
  font-size: 0.7rem; color: var(--md-sys-color-on-surface-variant);
}
.fence-meta .chip svg { width: 11px; height: 11px; }
.note { margin: 6px 0 0; font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); font-style: italic; }

.map-wrap { position: relative; min-height: 400px; border-radius: 14px; overflow: hidden; }
.map { width: 100%; height: 100%; min-height: 500px; }
.drawing-banner {
  position: absolute; top: 14px; left: 50%; transform: translateX(-50%);
  background: #6366f1; color: #fff;
  padding: 10px 16px; border-radius: 999px;
  display: inline-flex; align-items: center; gap: 8px;
  font-size: 0.84rem; font-weight: 600;
  box-shadow: 0 4px 14px rgba(99,102,241,0.4);
  z-index: 1000;
}
.drawing-banner svg { width: 14px; height: 14px; }
.cancel-x {
  background: rgba(255,255,255,0.25); border: none;
  color: #fff; width: 22px; height: 22px; border-radius: 50%;
  display: inline-flex; align-items: center; justify-content: center;
  cursor: pointer; padding: 0;
}

.details {
  background: var(--md-sys-color-surface-container);
  border-radius: 14px; padding: 14px;
  overflow-y: auto;
}
.details h3 { margin: 0 0 10px; display: flex; align-items: center; gap: 6px; }
.details h3 .dot { width: 12px; height: 12px; border-radius: 50%; }
.tabs { display: flex; gap: 4px; margin-bottom: 12px; background: var(--md-sys-color-surface-container-high); padding: 4px; border-radius: 12px; }
.tabs button {
  flex: 1; border: none; background: transparent;
  padding: 7px 10px; border-radius: 8px; cursor: pointer;
  display: inline-flex; align-items: center; justify-content: center; gap: 4px;
  font-size: 0.82rem; color: var(--md-sys-color-on-surface-variant);
}
.tabs button.on { background: #6366f1; color: #fff; }
.tabs button svg { width: 13px; height: 13px; }

.event-list, .audio-list { display: flex; flex-direction: column; gap: 8px; }
.muted { text-align: center; color: var(--md-sys-color-on-surface-variant); padding: 20px; font-size: 0.86rem; }
.event-item {
  display: flex; gap: 10px; padding: 10px;
  background: var(--md-sys-color-surface-container-high);
  border-radius: 10px;
  border-left: 3px solid #6366f1;
}
.event-item.exit { border-left-color: #f59e0b; }
.event-item.enter { border-left-color: #22c55e; }
.ev-icon {
  width: 32px; height: 32px; border-radius: 50%;
  background: var(--md-sys-color-surface);
  display: flex; align-items: center; justify-content: center;
  color: var(--md-sys-color-primary);
  flex-shrink: 0;
}
.ev-icon svg { width: 16px; height: 16px; }
.ev-body { flex: 1; min-width: 0; }
.ev-head { display: flex; justify-content: space-between; gap: 8px; font-size: 0.84rem; }
.ev-time { color: var(--md-sys-color-on-surface-variant); font-size: 0.78rem; }
.ev-meta { font-size: 0.74rem; color: var(--md-sys-color-on-surface-variant); display: flex; gap: 6px; flex-wrap: wrap; margin-top: 2px; }

.audio-item {
  display: flex; gap: 8px; padding: 10px;
  background: var(--md-sys-color-surface-container-high);
  border-radius: 10px;
  align-items: flex-start;
}
.audio-icon { width: 24px; height: 24px; color: #a855f7; margin-top: 4px; flex-shrink: 0; }
.audio-body { flex: 1; min-width: 0; }
.audio-head { display: flex; justify-content: space-between; gap: 8px; font-size: 0.84rem; margin-bottom: 4px; }
.audio-meta { color: var(--md-sys-color-on-surface-variant); font-size: 0.76rem; }
.player { width: 100%; height: 32px; }

.edit-overlay {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex; align-items: center; justify-content: center;
  z-index: 9999; padding: 14px;
}
.edit-modal {
  background: var(--md-sys-color-surface);
  border-radius: 18px; padding: 0;
  width: 100%; max-width: 520px;
  max-height: 90vh; display: flex; flex-direction: column;
  box-shadow: 0 20px 50px rgba(0,0,0,0.3);
}
.edit-head, .edit-foot {
  padding: 16px 20px;
  display: flex; justify-content: space-between; align-items: center;
}
.edit-head { border-bottom: 1px solid var(--md-sys-color-outline-variant); }
.edit-foot { border-top: 1px solid var(--md-sys-color-outline-variant); gap: 10px; justify-content: flex-end; }
.edit-head h3 { margin: 0; }
.edit-body {
  padding: 16px 20px;
  overflow-y: auto; flex: 1;
  display: flex; flex-direction: column; gap: 14px;
}

.field { display: flex; flex-direction: column; gap: 4px; }
.field span { font-size: 0.84rem; color: var(--md-sys-color-on-surface-variant); }
.field input[type="text"], .field textarea {
  padding: 9px 12px;
  border-radius: 10px;
  border: 1px solid var(--md-sys-color-outline-variant);
  background: var(--md-sys-color-surface-container);
  font-size: 0.9rem; color: inherit;
  font-family: inherit;
}
.field input[type="range"] { accent-color: #6366f1; }
.field input[type="color"] { width: 40px; height: 36px; border: none; border-radius: 8px; cursor: pointer; padding: 0; background: transparent; }

.row-2 { display: grid; grid-template-columns: 1fr auto; gap: 12px; align-items: end; }

.group {
  border: 1px solid var(--md-sys-color-outline-variant);
  border-radius: 12px;
  padding: 10px 14px;
  display: flex; flex-direction: column; gap: 8px;
}
.group legend { font-size: 0.82rem; color: var(--md-sys-color-on-surface-variant); padding: 0 6px; }
.check {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: 0.88rem; cursor: pointer;
}
.check svg { width: 14px; height: 14px; color: var(--md-sys-color-primary); }
.indent { padding-left: 22px; display: flex; flex-direction: column; gap: 8px; }
.ghost-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 7px 12px; border-radius: 10px;
  background: var(--md-sys-color-surface-container);
  border: 1px solid var(--md-sys-color-outline-variant);
  cursor: pointer; font-size: 0.84rem; color: inherit;
}
.ghost-btn svg { width: 13px; height: 13px; }
.picked-apps { display: flex; flex-wrap: wrap; gap: 4px; }
.pkg-chip {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 3px 8px; border-radius: 999px;
  background: var(--md-sys-color-surface-container);
  font-size: 0.74rem;
}
.pkg-chip svg { width: 12px; height: 12px; cursor: pointer; opacity: 0.6; }
.pkg-chip svg:hover { opacity: 1; color: #dc2626; }
</style>
