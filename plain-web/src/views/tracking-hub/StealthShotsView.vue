<template>
  <div class="ss-root">
    <header class="page-header">
      <router-link to="/tracking-hub" class="back-btn"><i-lucide:arrow-left /></router-link>
      <div class="title-block">
        <h2 class="title"><i-lucide:camera /> {{ $t('stealth_screenshots') }}</h2>
        <p class="sub">{{ $t('stealth_screenshots_desc') }}</p>
      </div>
      <div class="actions">
        <button class="ghost-btn primary" @click="onCaptureNow" :disabled="capturing || !state?.accessibilityServiceConnected || !state?.supportedByOs">
          <i-lucide:camera /> {{ $t('capture_now') }}
        </button>
        <button class="ghost-btn danger" @click="onClearAll" :disabled="!state?.totalShots">
          <i-lucide:trash-2 /> {{ $t('clear_all') }}
        </button>
      </div>
    </header>

    <div v-if="state && !state.supportedByOs" class="warn-card">
      <i-lucide:alert-triangle />
      <span>{{ $t('stealth_unsupported_os') }}</span>
    </div>
    <div v-else-if="state && !state.accessibilityServiceConnected" class="warn-card">
      <i-lucide:alert-triangle />
      <span>{{ $t('accessibility_required') }}</span>
    </div>

    <section class="settings-card">
      <div class="row">
        <span class="label">{{ $t('enabled') }}</span>
        <label class="switch">
          <input type="checkbox" :checked="state?.enabled || false" @change="onToggle" />
          <span class="track" />
        </label>
      </div>
      <div class="row col">
        <span class="label">
          {{ $t('capture_interval') }}: <strong>{{ intervalMin }} {{ $t('minutes_short') }}</strong>
        </span>
        <input type="range" min="1" max="180" step="1" v-model.number="intervalMin" @change="onConfigChange" />
      </div>
      <div class="row col">
        <span class="label">
          {{ $t('keep_count') }}: <strong>{{ keepCount }} {{ $t('shots') }}</strong>
        </span>
        <input type="range" min="10" max="500" step="10" v-model.number="keepCount" @change="onConfigChange" />
      </div>
      <div class="row stat">
        <span><i-lucide:image /> {{ state?.totalShots ?? 0 }} {{ $t('total') }}</span>
      </div>
    </section>

    <section v-if="shots.length > 0" class="shot-grid">
      <div v-for="s in shots" :key="s.id" class="shot-card-item" @click="openLightbox(s)">
        <div class="thumb-wrap">
          <img :src="shotUrl(s.fileId)" :alt="s.appLabel" loading="lazy" />
          <span class="badge" :class="{ manual: s.manual }">
            {{ s.manual ? $t('manual') : $t('auto') }}
          </span>
        </div>
        <div class="shot-meta">
          <strong>{{ s.appLabel || s.packageName || '—' }}</strong>
          <span class="time"><i-lucide:clock /> {{ formatTime(s.ts) }}</span>
          <span class="dim">{{ s.width }}×{{ s.height }} · {{ formatBytes(s.sizeBytes) }}</span>
        </div>
        <div class="shot-actions" @click.stop>
          <a class="icon-btn" :href="shotUrl(s.fileId)" target="_blank" rel="noopener" :title="$t('open')">
            <i-lucide:external-link />
          </a>
          <a class="icon-btn" :href="shotUrl(s.fileId) + '&dl=1'" :title="$t('download')">
            <i-lucide:download />
          </a>
          <button class="icon-btn danger" :title="$t('delete')" @click="onDelete(s)">
            <i-lucide:x />
          </button>
        </div>
      </div>
    </section>

    <div v-else-if="state" class="empty">
      <i-lucide:camera />
      <h3>{{ $t('no_shots_yet') }}</h3>
    </div>

    <div v-if="lightbox" class="lightbox" @click="lightbox = null">
      <img :src="shotUrl(lightbox.fileId)" :alt="lightbox.appLabel" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import emitter from '@/plugins/eventbus'
import toast from '@/components/toaster'
import { gqlFetch } from '@/lib/api/gql-client'
import { stealthScreenshotStateGQL, stealthScreenshotsGQL } from '@/lib/api/query'
import {
  setStealthScreenshotEnabledGQL, setStealthScreenshotConfigGQL,
  triggerStealthScreenshotGQL, deleteStealthScreenshotGQL, clearStealthScreenshotsGQL,
} from '@/lib/api/mutation'
import { getFileUrlByPath } from '@/lib/api/file'
import { storeToRefs } from 'pinia'
import { useTempStore } from '@/stores/temp'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const { urlTokenKey } = storeToRefs(useTempStore())

interface IShot { id: string; ts: number; packageName: string; appLabel: string; width: number; height: number; sizeBytes: number; manual: boolean; fileId: string }
interface IState { enabled: boolean; accessibilityServiceConnected: boolean; supportedByOs: boolean; intervalMin: number; keepCount: number; totalShots: number }

const state = ref<IState | null>(null)
const shots = ref<IShot[]>([])
const intervalMin = ref(15)
const keepCount = ref(100)
const capturing = ref(false)
const lightbox = ref<IShot | null>(null)

async function loadState() {
  const r = await gqlFetch<{ stealthScreenshotState: IState }>(stealthScreenshotStateGQL, {})
  if (!r.errors) {
    state.value = r.data.stealthScreenshotState
    intervalMin.value = state.value.intervalMin
    keepCount.value = state.value.keepCount
  }
}

async function loadShots() {
  const r = await gqlFetch<{ stealthScreenshots: IShot[] }>(stealthScreenshotsGQL, { offset: 0, limit: 200 })
  if (!r.errors) shots.value = r.data.stealthScreenshots
}

async function onToggle(ev: Event) {
  await gqlFetch(setStealthScreenshotEnabledGQL, { enabled: (ev.target as HTMLInputElement).checked })
  await loadState()
}
async function onConfigChange() {
  await gqlFetch(setStealthScreenshotConfigGQL, { intervalMin: intervalMin.value, keepCount: keepCount.value })
  await loadState()
}
async function onCaptureNow() {
  capturing.value = true
  try {
    const r = await gqlFetch<{ triggerStealthScreenshot: boolean }>(triggerStealthScreenshotGQL, {})
    if (!r.errors && r.data.triggerStealthScreenshot) {
      toast(t('capture_started'), 'info')
      await Promise.all([loadState(), loadShots()])
    } else {
      toast(t('capture_failed'), 'error')
    }
  } catch (_) { toast(t('capture_failed'), 'error') }
  capturing.value = false
}
async function onDelete(s: IShot) {
  await gqlFetch(deleteStealthScreenshotGQL, { id: s.id })
  shots.value = shots.value.filter(x => x.id !== s.id)
  await loadState()
}
async function onClearAll() {
  if (!confirm(t('confirm_clear_shots'))) return
  await gqlFetch(clearStealthScreenshotsGQL, {})
  shots.value = []
  await loadState()
}

function shotUrl(fileId: string): string {
  return getFileUrlByPath(urlTokenKey.value, 'stealth_shot://' + fileId)
}
function formatTime(ts: number): string { return new Date(ts).toLocaleString() }
function formatBytes(n: number): string {
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(1) + ' MB'
}
function openLightbox(s: IShot) { lightbox.value = s }

function onCaptured(payload: any) {
  if (state.value) state.value.totalShots = payload?.totalShots ?? state.value.totalShots + 1
  // Reload list (cheap; small count) so the new shot appears at the top.
  loadShots()
}

onMounted(async () => {
  await Promise.all([loadState(), loadShots()])
  emitter.on('screenshot_captured', onCaptured)
})
onUnmounted(() => {
  emitter.off('screenshot_captured', onCaptured)
})
</script>

<style scoped lang="scss">
.ss-root { padding: 18px 22px 28px; max-width: 1200px; margin: 0 auto; display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.back-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 38px; height: 38px; border-radius: 50%;
  background: var(--md-sys-color-surface-container); color: inherit; text-decoration: none;
}
.title-block { flex: 1; }
.title-block .title {
  margin: 0; font-size: 1.25rem; font-weight: 700;
  display: flex; align-items: center; gap: 8px;
}
.title svg { width: 22px; height: 22px; color: #38bdf8; }
.sub { margin: 4px 0 0; color: var(--md-sys-color-on-surface-variant); font-size: 0.85rem; }
.actions { display: flex; gap: 8px; }

.warn-card {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 16px; border-radius: 14px;
  background: rgba(245,158,11,0.12); color: #b45309;
  border: 1px solid rgba(245,158,11,0.35);
  font-size: 0.86rem;
}
.warn-card svg { width: 18px; height: 18px; }

.settings-card {
  background: var(--md-sys-color-surface-container);
  border-radius: 18px; padding: 16px;
  display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 14px;
}
.row { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.row.col { flex-direction: column; align-items: stretch; gap: 6px; }
.row.stat { color: var(--md-sys-color-on-surface-variant); font-size: 0.85rem; }
.row.stat svg { width: 14px; height: 14px; }
.label { font-size: 0.88rem; font-weight: 600; }

.switch { position: relative; width: 44px; height: 24px; flex-shrink: 0; }
.switch input { opacity: 0; width: 0; height: 0; }
.switch .track {
  position: absolute; inset: 0; border-radius: 999px;
  background: var(--md-sys-color-surface-container-highest); transition: 0.2s;
}
.switch .track::before {
  content: ''; position: absolute; left: 3px; top: 3px; width: 18px; height: 18px;
  border-radius: 50%; background: white; transition: 0.2s;
}
.switch input:checked + .track { background: #38bdf8; }
.switch input:checked + .track::before { transform: translateX(20px); }

.ghost-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 8px 14px; border-radius: 12px;
  background: var(--md-sys-color-surface-container);
  border: 1px solid var(--md-sys-color-outline-variant);
  color: inherit; font: inherit; cursor: pointer;
  text-decoration: none;
}
.ghost-btn:hover { background: var(--md-sys-color-surface-container-high); }
.ghost-btn:disabled { opacity: 0.45; cursor: not-allowed; }
.ghost-btn.danger { color: #dc2626; border-color: rgba(220,38,38,0.3); }
.ghost-btn.primary { color: #0284c7; border-color: rgba(56,189,248,0.4); background: rgba(56,189,248,0.08); }
.ghost-btn svg { width: 14px; height: 14px; }

.shot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 14px;
}
.shot-card-item {
  background: var(--md-sys-color-surface-container);
  border-radius: 14px; overflow: hidden;
  border: 1px solid var(--md-sys-color-outline-variant);
  display: flex; flex-direction: column;
  transition: transform 0.15s, box-shadow 0.15s;
  cursor: zoom-in;
}
.shot-card-item:hover { transform: translateY(-2px); box-shadow: 0 8px 18px rgba(56,189,248,0.18); }
.thumb-wrap {
  position: relative;
  aspect-ratio: 9 / 16;
  background: var(--md-sys-color-surface);
  overflow: hidden;
}
.thumb-wrap img { width: 100%; height: 100%; object-fit: cover; display: block; }
.badge {
  position: absolute; top: 8px; left: 8px;
  padding: 2px 8px; font-size: 0.7rem; font-weight: 600;
  border-radius: 999px;
  background: rgba(0,0,0,0.6); color: white;
}
.badge.manual { background: rgba(56,189,248,0.85); }
.shot-meta {
  padding: 10px 12px; display: flex; flex-direction: column; gap: 2px;
  font-size: 0.82rem;
}
.shot-meta .time {
  color: var(--md-sys-color-on-surface-variant);
  font-size: 0.74rem;
  display: inline-flex; align-items: center; gap: 4px;
}
.shot-meta .time svg { width: 11px; height: 11px; }
.shot-meta .dim { color: var(--md-sys-color-on-surface-variant); font-size: 0.72rem; }
.shot-actions {
  display: flex; gap: 4px; justify-content: flex-end;
  padding: 0 8px 8px;
}
.icon-btn {
  width: 30px; height: 30px; border-radius: 8px;
  display: inline-flex; align-items: center; justify-content: center;
  background: transparent; border: none; color: inherit; cursor: pointer;
  text-decoration: none;
}
.icon-btn:hover { background: var(--md-sys-color-surface-container-high); }
.icon-btn.danger:hover { color: #dc2626; }
.icon-btn svg { width: 14px; height: 14px; }

.empty {
  text-align: center; padding: 60px 20px;
  color: var(--md-sys-color-on-surface-variant);
  display: flex; flex-direction: column; align-items: center; gap: 8px;
}
.empty svg { width: 36px; height: 36px; opacity: 0.4; }
.empty h3 { margin: 0; font-weight: 500; font-size: 0.95rem; }

.lightbox {
  position: fixed; inset: 0; background: rgba(0,0,0,0.92);
  display: flex; align-items: center; justify-content: center;
  z-index: 9999; cursor: zoom-out;
}
.lightbox img { max-width: 95vw; max-height: 95vh; object-fit: contain; }
</style>
