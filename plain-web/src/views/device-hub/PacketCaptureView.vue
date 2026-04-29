<template>
  <div class="pc-root">
    <header class="page-header">
      <router-link to="/device-hub" class="back-btn"><i-lucide:arrow-left /></router-link>
      <div class="title-block">
        <h2 class="title"><i-lucide:radio /> {{ $t('hub_packet_capture_title') }}</h2>
        <p class="sub">{{ $t('hub_packet_capture_desc') }}</p>
      </div>
      <div class="actions">
        <button class="ghost-btn" @click="loadAll" :disabled="loading">
          <i-lucide:refresh-cw :class="{ spin: loading }" /> {{ $t('refresh') }}
        </button>
        <button class="ghost-btn danger" @click="onClear" :disabled="!entries.length">
          <i-lucide:trash-2 /> {{ $t('clear') }}
        </button>
      </div>
    </header>

    <section v-if="state" class="control-card" :class="{ live: state.running }">
      <div class="left">
        <div class="status-icon" :class="{ live: state.running }">
          <i-lucide:radio v-if="state.running" />
          <i-lucide:radio-tower v-else />
        </div>
        <div class="status-text">
          <strong v-if="state.running">{{ $t('vpn_capture_running') }}</strong>
          <strong v-else-if="state.enabled && state.needsConsent">{{ $t('vpn_consent_required') }}</strong>
          <strong v-else-if="!state.supported">{{ $t('vpn_unsupported') }}</strong>
          <strong v-else>{{ $t('vpn_capture_off') }}</strong>
          <span>{{ state.totalEntries }} {{ $t('entries_logged') }}</span>
        </div>
      </div>
      <div class="right">
        <label class="switch">
          <input type="checkbox" :checked="state.enabled" @change="onToggle" :disabled="!state.supported" />
          <span class="track" />
        </label>
      </div>
    </section>

    <div v-if="state?.needsConsent" class="warn-card">
      <i-lucide:shield-alert />
      <div class="warn-text">
        <strong>{{ $t('vpn_consent_required') }}</strong>
        <span>{{ $t('vpn_consent_hint') }}</span>
      </div>
    </div>

    <div class="search-wrap">
      <i-lucide:search class="search-icon" />
      <input v-model="hostFilter" type="text" :placeholder="$t('filter_by_host')" class="search-input" @input="onFilterInput" />
      <button v-if="hostFilter" class="clear-btn" @click="hostFilter = ''; loadEntries()"><i-lucide:x /></button>
    </div>

    <section v-if="entries.length > 0" class="log-list">
      <div v-for="e in entries" :key="e.id" class="log-row">
        <div class="proto-pill" :class="e.protocol.toLowerCase()">{{ e.protocol }}</div>
        <div class="entry-body">
          <strong class="host">{{ e.host }}<span class="port" v-if="e.port">:{{ e.port }}</span></strong>
          <div class="meta">
            <span class="ts">{{ formatTime(e.ts) }}</span>
            <span v-if="e.resolvedIp" class="ip"><i-lucide:globe /> {{ e.resolvedIp }}</span>
            <span v-if="e.appLabel" class="app"><i-lucide:box /> {{ e.appLabel }}</span>
            <span v-if="e.sizeBytes > 0" class="size">{{ e.sizeBytes }} B</span>
          </div>
        </div>
      </div>
    </section>
    <div v-else-if="state?.running" class="empty">
      <i-lucide:loader-2 class="spin" />
      <h3>{{ $t('waiting_for_traffic') }}</h3>
    </div>
    <div v-else class="empty">
      <i-lucide:radio />
      <h3>{{ $t('no_packets_yet') }}</h3>
      <span class="empty-hint">{{ $t('packet_capture_hint') }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import toast from '@/components/toaster'
import { gqlFetch } from '@/lib/api/gql-client'
import { packetCaptureStateGQL, packetEntriesGQL } from '@/lib/api/query'
import { setPacketCaptureEnabledGQL, clearPacketEntriesGQL } from '@/lib/api/mutation'

const { t } = useI18n()
interface IState { supported: boolean; enabled: boolean; running: boolean; totalEntries: number; needsConsent: boolean }
interface IEntry { id: string; ts: number; host: string; port: number; protocol: string; appPackage: string; appLabel: string; sizeBytes: number; resolvedIp: string }

const state = ref<IState | null>(null)
const entries = ref<IEntry[]>([])
const loading = ref(false)
const hostFilter = ref('')
let pollTimer: number | null = null
let filterDebounce: number | null = null

async function loadState() {
  const r = await gqlFetch<{ packetCaptureState: IState }>(packetCaptureStateGQL, {})
  if (!r.errors) state.value = r.data.packetCaptureState
}
async function loadEntries() {
  const r = await gqlFetch<{ packetEntries: IEntry[] }>(packetEntriesGQL, { offset: 0, limit: 200, host: hostFilter.value })
  if (!r.errors) entries.value = r.data.packetEntries
}
async function loadAll() {
  loading.value = true
  try { await Promise.all([loadState(), loadEntries()]) }
  finally { loading.value = false }
}

function onFilterInput() {
  if (filterDebounce) clearTimeout(filterDebounce)
  filterDebounce = window.setTimeout(loadEntries, 300)
}

async function onToggle(ev: Event) {
  const want = (ev.target as HTMLInputElement).checked
  const r = await gqlFetch<{ setPacketCaptureEnabled: boolean }>(setPacketCaptureEnabledGQL, { enabled: want })
  if (r.errors) {
    toast(t('vpn_toggle_failed'), 'error')
  } else if (want && state.value && state.value.needsConsent) {
    toast(t('vpn_consent_hint'), 'info')
  }
  setTimeout(loadAll, 600)
}

async function onClear() {
  if (!confirm(t('confirm_clear_packets'))) return
  await gqlFetch(clearPacketEntriesGQL, {})
  await loadAll()
}

function formatTime(ts: number): string {
  const d = new Date(ts)
  return d.toLocaleString()
}

onMounted(() => {
  loadAll()
  pollTimer = window.setInterval(() => {
    if (state.value?.running) loadAll()
  }, 4000)
})
onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  if (filterDebounce) clearTimeout(filterDebounce)
})
</script>

<style scoped lang="scss">
.pc-root { padding: 18px 22px 28px; max-width: 1100px; margin: 0 auto; display: flex; flex-direction: column; gap: 14px; }
.page-header { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.back-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 38px; height: 38px; border-radius: 50%;
  background: var(--md-sys-color-surface-container); color: inherit; text-decoration: none;
}
.title-block { flex: 1; }
.title-block .title { margin: 0; font-size: 1.25rem; font-weight: 700; display: flex; align-items: center; gap: 8px; }
.title svg { width: 22px; height: 22px; color: #ec4899; }
.sub { margin: 4px 0 0; color: var(--md-sys-color-on-surface-variant); font-size: 0.85rem; }
.actions { display: flex; gap: 8px; }
.ghost-btn {
  display: inline-flex; align-items: center; gap: 6px; padding: 8px 14px; border-radius: 12px;
  background: var(--md-sys-color-surface-container);
  border: 1px solid var(--md-sys-color-outline-variant);
  color: inherit; font: inherit; cursor: pointer;
}
.ghost-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.ghost-btn.danger { color: #dc2626; border-color: rgba(220,38,38,0.3); }
.ghost-btn svg { width: 14px; height: 14px; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.control-card {
  display: flex; align-items: center; justify-content: space-between; gap: 16px;
  padding: 18px 22px; border-radius: 18px;
  background: linear-gradient(135deg, rgba(236,72,153,0.08), rgba(244,114,182,0.06));
  border: 1px solid var(--md-sys-color-outline-variant);
}
.control-card.live { border-color: #ec4899; box-shadow: 0 0 0 1px rgba(236,72,153,0.3); }
.left { display: flex; align-items: center; gap: 14px; }
.status-icon {
  width: 48px; height: 48px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  background: var(--md-sys-color-surface-container-high);
  color: var(--md-sys-color-on-surface-variant);
}
.status-icon.live { background: rgba(236,72,153,0.18); color: #be185d; animation: glow 2s infinite; }
@keyframes glow {
  0%, 100% { box-shadow: 0 0 0 0 rgba(236,72,153,0.4); }
  50% { box-shadow: 0 0 0 6px rgba(236,72,153,0); }
}
.status-icon svg { width: 22px; height: 22px; }
.status-text { display: flex; flex-direction: column; gap: 2px; }
.status-text strong { font-size: 1rem; font-weight: 700; }
.status-text span { font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); }

.switch { position: relative; width: 50px; height: 28px; }
.switch input { opacity: 0; width: 0; height: 0; }
.switch .track {
  position: absolute; inset: 0; border-radius: 999px;
  background: var(--md-sys-color-surface-container-highest); transition: 0.2s; cursor: pointer;
}
.switch .track::before {
  content: ''; position: absolute; left: 3px; top: 3px; width: 22px; height: 22px;
  border-radius: 50%; background: white; transition: 0.2s;
}
.switch input:checked + .track { background: #ec4899; }
.switch input:checked + .track::before { transform: translateX(22px); }

.warn-card {
  display: flex; align-items: flex-start; gap: 12px;
  padding: 14px 18px; border-radius: 14px;
  background: rgba(245,158,11,0.12);
  border: 1px solid rgba(245,158,11,0.35); color: #b45309;
  font-size: 0.85rem;
}
.warn-card svg { width: 22px; height: 22px; flex-shrink: 0; margin-top: 2px; }
.warn-text { display: flex; flex-direction: column; gap: 2px; }

.search-wrap { position: relative; }
.search-icon { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); width: 16px; height: 16px; color: var(--md-sys-color-on-surface-variant); }
.search-input {
  width: 100%; padding: 10px 36px;
  border-radius: 12px;
  border: 1px solid var(--md-sys-color-outline-variant);
  background: var(--md-sys-color-surface-container);
  color: inherit; font: inherit; outline: none;
}
.search-input:focus { border-color: #ec4899; }
.clear-btn {
  position: absolute; right: 8px; top: 50%; transform: translateY(-50%);
  background: none; border: none; cursor: pointer; padding: 4px;
  color: var(--md-sys-color-on-surface-variant);
}

.log-list { display: flex; flex-direction: column; gap: 4px; }
.log-row {
  display: flex; gap: 12px; align-items: center;
  padding: 10px 14px; border-radius: 10px;
  background: var(--md-sys-color-surface-container);
}
.proto-pill {
  font-size: 0.65rem; font-weight: 700;
  padding: 3px 8px; border-radius: 4px;
  background: var(--md-sys-color-surface-container-high);
  color: var(--md-sys-color-on-surface-variant);
  min-width: 44px; text-align: center;
}
.proto-pill.dns { background: rgba(99,102,241,0.16); color: #4338ca; }
.proto-pill.https { background: rgba(34,197,94,0.16); color: #047857; }
.proto-pill.http { background: rgba(245,158,11,0.16); color: #b45309; }
.proto-pill.tcp { background: rgba(59,130,246,0.16); color: #1d4ed8; }
.proto-pill.udp { background: rgba(236,72,153,0.16); color: #be185d; }

.entry-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.host { font-size: 0.88rem; font-weight: 600; word-break: break-all; }
.port { color: var(--md-sys-color-on-surface-variant); font-weight: 500; }
.meta { display: flex; gap: 12px; flex-wrap: wrap; font-size: 0.72rem; color: var(--md-sys-color-on-surface-variant); }
.meta .app { display: inline-flex; align-items: center; gap: 4px; }
.meta svg { width: 11px; height: 11px; }

.empty {
  text-align: center; padding: 60px 20px;
  color: var(--md-sys-color-on-surface-variant);
  display: flex; flex-direction: column; align-items: center; gap: 8px;
}
.empty svg { width: 36px; height: 36px; opacity: 0.5; }
.empty h3 { margin: 0; font-weight: 500; font-size: 0.95rem; }
.empty-hint { font-size: 0.78rem; max-width: 380px; }
</style>
