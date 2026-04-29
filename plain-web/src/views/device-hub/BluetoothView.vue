<template>
  <div class="bt-root">
    <header class="page-header">
      <router-link to="/device-hub" class="back-btn"><i-lucide:arrow-left /></router-link>
      <div class="title-block">
        <h2 class="title"><i-lucide:bluetooth /> {{ $t('hub_bluetooth_title') }}</h2>
        <p class="sub">{{ $t('hub_bluetooth_desc') }}</p>
      </div>
      <div class="actions">
        <button class="ghost-btn" :class="{ scanning: state?.scanning }" @click="onToggleScan" :disabled="!state?.enabled || !state?.hasScanPermission">
          <i-lucide:radar :class="{ spin: state?.scanning }" />
          {{ state?.scanning ? $t('stop_scan') : $t('start_scan') }}
        </button>
        <button class="ghost-btn" @click="loadAll">
          <i-lucide:refresh-cw /> {{ $t('refresh') }}
        </button>
      </div>
    </header>

    <section v-if="state" class="status-card">
      <div class="status-row">
        <div class="status-icon" :class="{ on: state.enabled }">
          <i-lucide:bluetooth />
        </div>
        <div class="status-text">
          <strong>{{ state.enabled ? $t('bluetooth_on') : $t('bluetooth_off') }}</strong>
          <span v-if="!state.supported">{{ $t('bluetooth_unsupported') }}</span>
          <span v-else-if="!state.hasConnectPermission">{{ $t('bluetooth_perm_required') }}</span>
          <span v-else>{{ state.pairedCount }} {{ $t('paired') }} · {{ state.nearbyCount }} {{ $t('nearby') }}</span>
        </div>
        <label class="switch">
          <input type="checkbox" :checked="state.enabled" @change="onBtToggle" :disabled="!state.supported || !state.hasConnectPermission" />
          <span class="track" />
        </label>
      </div>
    </section>

    <h3 class="section-title">
      <i-lucide:link /> {{ $t('paired_devices') }}
      <span class="count" v-if="paired.length > 0">{{ paired.length }}</span>
    </h3>
    <section v-if="paired.length > 0" class="dev-list">
      <div v-for="d in paired" :key="d.address" class="dev-row">
        <div class="dev-icon paired"><i-lucide:link /></div>
        <div class="dev-info">
          <strong>{{ d.name || $t('unnamed_device') }}</strong>
          <span class="addr">{{ d.address }}</span>
          <div class="dev-meta">
            <span class="tag">{{ d.type }}</span>
            <span v-if="d.nearby" class="tag near">{{ $t('nearby') }} · {{ d.rssi }} dBm</span>
            <span v-else class="tag offline">{{ $t('out_of_range') }}</span>
          </div>
        </div>
        <button class="action-btn danger" @click="onUnpair(d)" :disabled="busy === d.address">
          <i-lucide:unlink /> {{ $t('unpair') }}
        </button>
      </div>
    </section>
    <div v-else class="empty mini">
      <span>{{ $t('no_paired_devices') }}</span>
    </div>

    <h3 class="section-title">
      <i-lucide:radar /> {{ $t('nearby_devices') }}
      <span class="count" v-if="nearby.length > 0">{{ nearby.length }}</span>
      <span v-if="state?.scanning" class="scanning-pill">
        <i-lucide:loader-2 class="spin" /> {{ $t('scanning') }}
      </span>
    </h3>
    <section v-if="nearby.length > 0" class="dev-list">
      <div v-for="d in nearby" :key="d.address" class="dev-row">
        <div class="dev-icon" :class="rssiClass(d.rssi)"><i-lucide:bluetooth /></div>
        <div class="dev-info">
          <strong>{{ d.name || $t('unnamed_device') }}</strong>
          <span class="addr">{{ d.address }}</span>
          <div class="dev-meta">
            <span class="tag">{{ d.type }}</span>
            <span class="tag near">{{ d.rssi }} dBm</span>
            <span v-if="d.bondState === 'bonded'" class="tag paired-tag">{{ $t('paired') }}</span>
          </div>
        </div>
        <button v-if="d.bondState !== 'bonded'" class="action-btn primary" @click="onPair(d)" :disabled="busy === d.address">
          <i-lucide:link2 /> {{ $t('pair') }}
        </button>
      </div>
    </section>
    <div v-else-if="state?.scanning" class="empty mini">
      <i-lucide:loader-2 class="spin" /> <span>{{ $t('scanning_for_devices') }}</span>
    </div>
    <div v-else class="empty mini">
      <span>{{ $t('start_scan_hint') }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import toast from '@/components/toaster'
import { gqlFetch } from '@/lib/api/gql-client'
import { bluetoothStateGQL, bluetoothPairedGQL, bluetoothNearbyGQL } from '@/lib/api/query'
import { bluetoothStartScanGQL, bluetoothStopScanGQL, bluetoothPairGQL, bluetoothUnpairGQL, setBluetoothEnabledGQL } from '@/lib/api/mutation'

const { t } = useI18n()
interface IDev { address: string; name: string; type: string; bondState: string; rssi: number; firstSeenMs: number; lastSeenMs: number; nearby: boolean }
interface IState { supported: boolean; enabled: boolean; scanning: boolean; hasScanPermission: boolean; hasConnectPermission: boolean; pairedCount: number; nearbyCount: number }

const state = ref<IState | null>(null)
const paired = ref<IDev[]>([])
const nearby = ref<IDev[]>([])
const busy = ref<string | null>(null)
let pollTimer: number | null = null

async function loadAll() {
  const [s, p, n] = await Promise.all([
    gqlFetch<{ bluetoothState: IState }>(bluetoothStateGQL, {}),
    gqlFetch<{ bluetoothPaired: IDev[] }>(bluetoothPairedGQL, {}),
    gqlFetch<{ bluetoothNearby: IDev[] }>(bluetoothNearbyGQL, {}),
  ])
  if (!s.errors) state.value = s.data.bluetoothState
  if (!p.errors) paired.value = p.data.bluetoothPaired
  if (!n.errors) nearby.value = n.data.bluetoothNearby
}

async function onToggleScan() {
  if (!state.value) return
  if (state.value.scanning) {
    await gqlFetch(bluetoothStopScanGQL, {})
    if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  } else {
    const r = await gqlFetch<{ bluetoothStartScan: boolean }>(bluetoothStartScanGQL, {})
    if (r.errors || !r.data.bluetoothStartScan) {
      toast(t('scan_failed'), 'error'); return
    }
    pollTimer = window.setInterval(loadAll, 2000)
  }
  await loadAll()
}

async function onBtToggle(ev: Event) {
  const want = (ev.target as HTMLInputElement).checked
  const r = await gqlFetch<{ setBluetoothEnabled: boolean }>(setBluetoothEnabledGQL, { enabled: want })
  if (r.errors || !r.data.setBluetoothEnabled) {
    toast(t('bluetooth_toggle_blocked'), 'error')
  }
  setTimeout(loadAll, 1500)
}

async function onPair(d: IDev) {
  busy.value = d.address
  try {
    const r = await gqlFetch<{ bluetoothPair: boolean }>(bluetoothPairGQL, { address: d.address })
    if (r.errors || !r.data.bluetoothPair) toast(t('pair_failed'), 'error')
    else toast(t('pairing_started'), 'info')
  } finally {
    busy.value = null
    setTimeout(loadAll, 1500)
  }
}

async function onUnpair(d: IDev) {
  if (!confirm(t('confirm_unpair', { name: d.name || d.address }))) return
  busy.value = d.address
  try {
    const r = await gqlFetch<{ bluetoothUnpair: boolean }>(bluetoothUnpairGQL, { address: d.address })
    if (r.errors || !r.data.bluetoothUnpair) toast(t('unpair_failed'), 'error')
    else toast(t('unpaired'), 'info')
  } finally {
    busy.value = null
    setTimeout(loadAll, 800)
  }
}

function rssiClass(rssi: number): string {
  if (rssi >= -55) return 'strong'
  if (rssi >= -75) return 'medium'
  return 'weak'
}

onMounted(() => loadAll())
onUnmounted(() => { if (pollTimer) clearInterval(pollTimer) })
</script>

<style scoped lang="scss">
.bt-root { padding: 18px 22px 28px; max-width: 1100px; margin: 0 auto; display: flex; flex-direction: column; gap: 14px; }
.page-header { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.back-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 38px; height: 38px; border-radius: 50%;
  background: var(--md-sys-color-surface-container); color: inherit; text-decoration: none;
}
.title-block { flex: 1; }
.title-block .title { margin: 0; font-size: 1.25rem; font-weight: 700; display: flex; align-items: center; gap: 8px; }
.title svg { width: 22px; height: 22px; color: #6366f1; }
.sub { margin: 4px 0 0; color: var(--md-sys-color-on-surface-variant); font-size: 0.85rem; }
.actions { display: flex; gap: 8px; }
.ghost-btn {
  display: inline-flex; align-items: center; gap: 6px; padding: 8px 14px; border-radius: 12px;
  background: var(--md-sys-color-surface-container);
  border: 1px solid var(--md-sys-color-outline-variant);
  color: inherit; font: inherit; cursor: pointer;
}
.ghost-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.ghost-btn.scanning { background: rgba(99,102,241,0.16); color: #4338ca; border-color: #6366f1; }
.ghost-btn svg { width: 14px; height: 14px; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.status-card { padding: 18px 22px; border-radius: 18px; background: linear-gradient(135deg, rgba(99,102,241,0.08), rgba(139,92,246,0.06)); border: 1px solid var(--md-sys-color-outline-variant); }
.status-row { display: flex; align-items: center; gap: 16px; }
.status-icon {
  width: 48px; height: 48px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  background: var(--md-sys-color-surface-container-high);
  color: var(--md-sys-color-on-surface-variant);
}
.status-icon.on { background: rgba(99,102,241,0.18); color: #4338ca; }
.status-icon svg { width: 22px; height: 22px; }
.status-text { flex: 1; display: flex; flex-direction: column; gap: 2px; }
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
.switch input:checked + .track { background: #6366f1; }
.switch input:checked + .track::before { transform: translateX(22px); }

.section-title {
  display: flex; align-items: center; gap: 8px;
  font-size: 1rem; font-weight: 700; margin: 6px 0 0;
}
.section-title svg { width: 18px; height: 18px; color: #6366f1; }
.section-title .count {
  font-size: 0.72rem; padding: 2px 8px;
  background: rgba(99,102,241,0.16); color: #4338ca; border-radius: 999px; font-weight: 600;
}
.scanning-pill {
  margin-left: auto; display: inline-flex; align-items: center; gap: 4px;
  font-size: 0.72rem; color: #4338ca; font-weight: 600;
}
.scanning-pill svg { width: 12px; height: 12px; }

.dev-list { display: flex; flex-direction: column; gap: 6px; }
.dev-row {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 16px; border-radius: 12px;
  background: var(--md-sys-color-surface-container);
}
.dev-icon {
  width: 40px; height: 40px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  background: var(--md-sys-color-surface-container-high);
  color: var(--md-sys-color-on-surface-variant);
}
.dev-icon.paired { background: rgba(34,197,94,0.16); color: #047857; }
.dev-icon.strong { background: rgba(99,102,241,0.16); color: #4338ca; }
.dev-icon.medium { background: rgba(245,158,11,0.16); color: #b45309; }
.dev-icon.weak { background: rgba(148,163,184,0.16); color: var(--md-sys-color-on-surface-variant); }
.dev-icon svg { width: 18px; height: 18px; }
.dev-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.dev-info strong { font-size: 0.9rem; font-weight: 600; }
.addr { font-size: 0.7rem; font-family: monospace; color: var(--md-sys-color-on-surface-variant); }
.dev-meta { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 2px; }
.tag {
  font-size: 0.65rem; padding: 2px 7px; border-radius: 4px; font-weight: 600;
  background: var(--md-sys-color-surface-container-high);
  color: var(--md-sys-color-on-surface-variant);
}
.tag.near { background: rgba(99,102,241,0.16); color: #4338ca; }
.tag.offline { background: var(--md-sys-color-surface-container-high); }
.tag.paired-tag { background: rgba(34,197,94,0.16); color: #047857; }

.action-btn {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 7px 12px; border-radius: 10px; border: none;
  font: inherit; font-size: 0.78rem; font-weight: 600; cursor: pointer;
}
.action-btn.primary { background: #6366f1; color: white; }
.action-btn.primary:hover { background: #4f46e5; }
.action-btn.danger { background: rgba(239,68,68,0.12); color: #b91c1c; }
.action-btn.danger:hover { background: rgba(239,68,68,0.2); }
.action-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.action-btn svg { width: 12px; height: 12px; }

.empty.mini {
  text-align: center; padding: 20px;
  color: var(--md-sys-color-on-surface-variant);
  font-size: 0.85rem;
  display: flex; align-items: center; justify-content: center; gap: 6px;
}
.empty.mini svg { width: 14px; height: 14px; }
</style>
