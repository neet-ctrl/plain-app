<template>
  <div class="wf-root">
    <header class="page-header">
      <router-link to="/device-hub" class="back-btn"><i-lucide:arrow-left /></router-link>
      <div class="title-block">
        <h2 class="title"><i-lucide:wifi /> {{ $t('hub_wifi_title') }}</h2>
        <p class="sub">{{ $t('hub_wifi_desc') }}</p>
      </div>
      <div class="actions">
        <button class="ghost-btn" @click="loadAll" :disabled="loading">
          <i-lucide:refresh-cw :class="{ spin: loading }" /> {{ $t('refresh') }}
        </button>
      </div>
    </header>

    <section v-if="state" class="status-card">
      <div class="status-main">
        <div class="signal-circle" :class="signalClass(state.rssi)">
          <i-lucide:wifi v-if="state.enabled && state.connectedSsid" />
          <i-lucide:wifi-off v-else />
        </div>
        <div class="status-text">
          <strong>{{ state.connectedSsid || $t('not_connected') }}</strong>
          <span v-if="state.connectedSsid">
            {{ state.linkSpeedMbps }} Mbps · {{ formatFreq(state.frequencyMhz) }} · {{ state.rssi }} dBm
          </span>
          <span v-if="state.ipv4" class="ip">{{ state.ipv4 }}</span>
        </div>
      </div>
      <div class="status-controls">
        <label class="switch">
          <input type="checkbox" :checked="state.enabled" @change="onWifiToggle" />
          <span class="track" />
        </label>
        <span class="hint" v-if="!setEnabledWorks">{{ $t('wifi_toggle_blocked') }}</span>
      </div>
    </section>

    <section v-if="state" class="info-strip">
      <div class="info-tile">
        <i-lucide:radio-tower />
        <div>
          <strong>{{ $t('hotspot') }}</strong>
          <span>{{ hotspotLabel(state.hotspotState) }}</span>
        </div>
      </div>
      <div class="info-tile">
        <i-lucide:bookmark />
        <div>
          <strong>{{ $t('saved_networks') }}</strong>
          <span>{{ state.savedListAccessible ? $t('available') : $t('os_restricted') }}</span>
        </div>
      </div>
    </section>

    <h3 class="section-title">
      <i-lucide:radar /> {{ $t('nearby_networks') }}
      <span class="count" v-if="networks.length > 0">{{ networks.length }}</span>
    </h3>

    <section v-if="networks.length > 0" class="net-list">
      <div v-for="n in networks" :key="n.bssid + ':' + n.ssid" class="net-row" :class="{ current: n.isCurrent }">
        <div class="signal-bar" :class="signalClass(n.rssi)">
          <i-lucide:wifi />
        </div>
        <div class="net-info">
          <div class="net-name">
            <strong>{{ n.ssid || $t('hidden_network') }}</strong>
            <span v-if="n.isCurrent" class="badge connected">{{ $t('connected') }}</span>
            <span v-if="isSecure(n.capabilities)" class="badge secure"><i-lucide:lock /> {{ securityType(n.capabilities) }}</span>
            <span v-else class="badge open">{{ $t('open') }}</span>
          </div>
          <div class="net-meta">
            <span>{{ n.bssid }}</span>
            <span>{{ formatFreq(n.frequencyMhz) }}</span>
            <span>{{ n.rssi }} dBm</span>
          </div>
        </div>
      </div>
    </section>
    <div v-else-if="!loading" class="empty">
      <i-lucide:radar />
      <h3>{{ $t('no_networks_visible') }}</h3>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import toast from '@/components/toaster'
import { gqlFetch } from '@/lib/api/gql-client'
import { wifiStateGQL, wifiScanGQL } from '@/lib/api/query'
import { setWifiEnabledGQL } from '@/lib/api/mutation'

const { t } = useI18n()
interface IState { enabled: boolean; connectedSsid: string; connectedBssid: string; rssi: number; linkSpeedMbps: number; frequencyMhz: number; ipv4: string; hotspotState: string; savedListAccessible: boolean; canScan: boolean }
interface INet { ssid: string; bssid: string; capabilities: string; frequencyMhz: number; rssi: number; channelWidth: number; seenMs: number; isCurrent: boolean }

const state = ref<IState | null>(null)
const networks = ref<INet[]>([])
const loading = ref(false)
const setEnabledWorks = ref(true)

async function loadAll() {
  loading.value = true
  try {
    const [s, n] = await Promise.all([
      gqlFetch<{ wifiState: IState }>(wifiStateGQL, {}),
      gqlFetch<{ wifiScan: INet[] }>(wifiScanGQL, {}),
    ])
    if (!s.errors) state.value = s.data.wifiState
    if (!n.errors) networks.value = n.data.wifiScan
  } finally { loading.value = false }
}

async function onWifiToggle(ev: Event) {
  const want = (ev.target as HTMLInputElement).checked
  const r = await gqlFetch<{ setWifiEnabled: boolean }>(setWifiEnabledGQL, { enabled: want })
  if (r.errors || !r.data.setWifiEnabled) {
    setEnabledWorks.value = false
    toast(t('wifi_toggle_failed'), 'error')
  }
  setTimeout(loadAll, 800)
}

function signalClass(rssi: number): string {
  if (rssi >= -55) return 'excellent'
  if (rssi >= -70) return 'good'
  if (rssi >= -80) return 'weak'
  return 'poor'
}
function formatFreq(mhz: number): string {
  if (mhz === 0) return ''
  if (mhz < 3000) return '2.4 GHz'
  if (mhz < 5925) return '5 GHz'
  return '6 GHz'
}
function isSecure(cap: string): boolean { return cap.includes('WPA') || cap.includes('WEP') || cap.includes('PSK') || cap.includes('SAE') }
function securityType(cap: string): string {
  if (cap.includes('WPA3') || cap.includes('SAE')) return 'WPA3'
  if (cap.includes('WPA2')) return 'WPA2'
  if (cap.includes('WPA')) return 'WPA'
  if (cap.includes('WEP')) return 'WEP'
  return ''
}
function hotspotLabel(state: string): string {
  if (state === 'on') return t('on')
  if (state === 'off') return t('off')
  return t('os_restricted')
}

onMounted(() => loadAll())
</script>

<style scoped lang="scss">
.wf-root { padding: 18px 22px 28px; max-width: 1100px; margin: 0 auto; display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; align-items: center; gap: 14px; }
.back-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 38px; height: 38px; border-radius: 50%;
  background: var(--md-sys-color-surface-container); color: inherit; text-decoration: none;
}
.title-block { flex: 1; }
.title-block .title { margin: 0; font-size: 1.25rem; font-weight: 700; display: flex; align-items: center; gap: 8px; }
.title svg { width: 22px; height: 22px; color: #3b82f6; }
.sub { margin: 4px 0 0; color: var(--md-sys-color-on-surface-variant); font-size: 0.85rem; }
.actions { display: flex; gap: 8px; }
.ghost-btn {
  display: inline-flex; align-items: center; gap: 6px; padding: 8px 14px; border-radius: 12px;
  background: var(--md-sys-color-surface-container);
  border: 1px solid var(--md-sys-color-outline-variant);
  color: inherit; font: inherit; cursor: pointer;
}
.ghost-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.ghost-btn svg { width: 14px; height: 14px; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.status-card {
  display: flex; justify-content: space-between; align-items: center; gap: 16px;
  padding: 20px 22px; border-radius: 18px;
  background: linear-gradient(135deg, rgba(59,130,246,0.08), rgba(99,102,241,0.06));
  border: 1px solid var(--md-sys-color-outline-variant);
}
.status-main { display: flex; align-items: center; gap: 16px; }
.signal-circle {
  width: 60px; height: 60px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  background: var(--md-sys-color-surface-container-high);
}
.signal-circle svg { width: 28px; height: 28px; }
.signal-circle.excellent { background: rgba(34,197,94,0.18); color: #047857; }
.signal-circle.good { background: rgba(59,130,246,0.16); color: #1d4ed8; }
.signal-circle.weak { background: rgba(245,158,11,0.18); color: #b45309; }
.signal-circle.poor { background: rgba(239,68,68,0.18); color: #b91c1c; }
.status-text { display: flex; flex-direction: column; gap: 2px; }
.status-text strong { font-size: 1.1rem; font-weight: 700; }
.status-text span { font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); }
.ip { font-family: monospace; }

.status-controls { display: flex; flex-direction: column; align-items: flex-end; gap: 6px; }
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
.switch input:checked + .track { background: #3b82f6; }
.switch input:checked + .track::before { transform: translateX(22px); }
.hint { font-size: 0.7rem; color: var(--md-sys-color-on-surface-variant); }

.info-strip {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px;
}
.info-tile {
  display: flex; align-items: center; gap: 10px;
  padding: 14px 16px; border-radius: 14px;
  background: var(--md-sys-color-surface-container);
}
.info-tile svg { width: 22px; height: 22px; color: #3b82f6; }
.info-tile div { display: flex; flex-direction: column; }
.info-tile strong { font-size: 0.85rem; font-weight: 700; }
.info-tile span { font-size: 0.75rem; color: var(--md-sys-color-on-surface-variant); }

.section-title {
  display: flex; align-items: center; gap: 8px;
  font-size: 1rem; font-weight: 700; margin: 6px 0 0;
}
.section-title svg { width: 18px; height: 18px; color: #3b82f6; }
.section-title .count {
  margin-left: 4px; font-size: 0.72rem; padding: 2px 8px;
  background: rgba(59,130,246,0.16); color: #1d4ed8; border-radius: 999px;
  font-weight: 600;
}

.net-list { display: flex; flex-direction: column; gap: 6px; }
.net-row {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 16px; border-radius: 12px;
  background: var(--md-sys-color-surface-container);
  border: 1px solid transparent;
  transition: border-color 0.15s;
}
.net-row.current { border-color: #3b82f6; background: rgba(59,130,246,0.06); }
.signal-bar {
  width: 40px; height: 40px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  background: var(--md-sys-color-surface-container-high);
}
.signal-bar svg { width: 18px; height: 18px; }
.signal-bar.excellent { color: #047857; }
.signal-bar.good { color: #1d4ed8; }
.signal-bar.weak { color: #b45309; }
.signal-bar.poor { color: #b91c1c; }
.net-info { flex: 1; min-width: 0; }
.net-name { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.net-name strong { font-size: 0.9rem; font-weight: 600; }
.badge {
  font-size: 0.65rem; padding: 2px 7px; border-radius: 4px; font-weight: 700;
  display: inline-flex; align-items: center; gap: 3px;
}
.badge svg { width: 9px; height: 9px; }
.badge.connected { background: rgba(34,197,94,0.18); color: #047857; }
.badge.secure { background: var(--md-sys-color-surface-container-high); color: var(--md-sys-color-on-surface-variant); }
.badge.open { background: rgba(245,158,11,0.18); color: #b45309; }
.net-meta { display: flex; gap: 10px; font-size: 0.72rem; color: var(--md-sys-color-on-surface-variant); margin-top: 2px; }

.empty {
  text-align: center; padding: 50px 20px;
  color: var(--md-sys-color-on-surface-variant);
  display: flex; flex-direction: column; align-items: center; gap: 8px;
}
.empty svg { width: 36px; height: 36px; opacity: 0.5; }
.empty h3 { margin: 0; font-weight: 500; font-size: 0.95rem; }
</style>
