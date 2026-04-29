<template>
  <div class="hub-root">
    <div class="hub-header">
      <div class="hub-title-block">
        <i-lucide:settings-2 class="hub-icon" />
        <div>
          <h2 class="hub-title">{{ $t('device_hub_title') }}</h2>
          <p class="hub-sub">{{ $t('device_hub_subtitle') }}</p>
        </div>
      </div>
    </div>

    <div class="hub-grid">
      <router-link to="/device-hub/app-launcher" class="hub-card launch-card">
        <div class="card-icon-wrap">
          <i-lucide:rocket class="card-icon" />
        </div>
        <div class="card-body">
          <h3 class="card-title">{{ $t('hub_app_launcher_title') }}</h3>
          <p class="card-desc">{{ $t('hub_app_launcher_desc') }}</p>
          <div class="card-meta">
            <span class="chip neutral"><i-lucide:layout-grid /> {{ $t('open_remotely') }}</span>
          </div>
        </div>
        <i-lucide:arrow-right class="card-chev" />
      </router-link>

      <router-link to="/device-hub/net-usage" class="hub-card net-card">
        <div class="card-icon-wrap">
          <i-lucide:activity class="card-icon" />
          <span v-if="netState && !netState.usageAccessGranted" class="status-dot warn" />
        </div>
        <div class="card-body">
          <h3 class="card-title">{{ $t('hub_net_usage_title') }}</h3>
          <p class="card-desc">{{ $t('hub_net_usage_desc') }}</p>
          <div class="card-meta">
            <span v-if="netState?.usageAccessGranted" class="chip on"><i-lucide:wifi /> {{ netState.activeNetwork }}</span>
            <span v-else class="chip warn"><i-lucide:lock /> {{ $t('needs_usage_access') }}</span>
            <span v-if="netState?.usageAccessGranted" class="chip neutral"><i-lucide:download /> {{ formatBytes(netState.totalRx) }}</span>
          </div>
        </div>
        <i-lucide:arrow-right class="card-chev" />
      </router-link>

      <router-link to="/device-hub/wifi" class="hub-card wifi-card">
        <div class="card-icon-wrap">
          <i-lucide:wifi class="card-icon" />
          <span v-if="wifiState?.enabled" class="status-dot live" />
        </div>
        <div class="card-body">
          <h3 class="card-title">{{ $t('hub_wifi_title') }}</h3>
          <p class="card-desc">{{ $t('hub_wifi_desc') }}</p>
          <div class="card-meta">
            <span class="chip" :class="{ on: wifiState?.enabled }">
              <i-lucide:circle-dot v-if="wifiState?.enabled" />
              <i-lucide:circle v-else />
              {{ wifiState?.enabled ? $t('on') : $t('off') }}
            </span>
            <span v-if="wifiState?.connectedSsid" class="chip neutral">
              <i-lucide:link /> {{ wifiState.connectedSsid }}
            </span>
          </div>
        </div>
        <i-lucide:arrow-right class="card-chev" />
      </router-link>

      <router-link to="/device-hub/bluetooth" class="hub-card bt-card">
        <div class="card-icon-wrap">
          <i-lucide:bluetooth class="card-icon" />
          <span v-if="btState?.scanning" class="status-dot live" />
        </div>
        <div class="card-body">
          <h3 class="card-title">{{ $t('hub_bluetooth_title') }}</h3>
          <p class="card-desc">{{ $t('hub_bluetooth_desc') }}</p>
          <div class="card-meta">
            <span class="chip" :class="{ on: btState?.enabled }">
              <i-lucide:circle-dot v-if="btState?.enabled" />
              <i-lucide:circle v-else />
              {{ btState?.enabled ? $t('on') : $t('off') }}
            </span>
            <span v-if="btState" class="chip neutral">
              <i-lucide:link /> {{ btState.pairedCount }} {{ $t('paired') }}
            </span>
          </div>
        </div>
        <i-lucide:arrow-right class="card-chev" />
      </router-link>

      <router-link to="/device-hub/battery" class="hub-card bat-card">
        <div class="card-icon-wrap">
          <i-lucide:battery-charging class="card-icon" />
          <span v-if="batState?.charging" class="status-dot live" />
        </div>
        <div class="card-body">
          <h3 class="card-title">{{ $t('hub_battery_title') }}</h3>
          <p class="card-desc">{{ $t('hub_battery_desc') }}</p>
          <div class="card-meta">
            <span v-if="batState && batState.currentLevel >= 0" class="chip on">
              <i-lucide:zap /> {{ batState.currentLevel }}%
            </span>
            <span v-if="batState" class="chip neutral">
              <i-lucide:database /> {{ batState.samples.length }} {{ $t('samples') }}
            </span>
          </div>
        </div>
        <i-lucide:arrow-right class="card-chev" />
      </router-link>

      <router-link to="/device-hub/packet-capture" class="hub-card pkt-card">
        <div class="card-icon-wrap">
          <i-lucide:radio class="card-icon" />
          <span v-if="pktState?.running" class="status-dot live" />
        </div>
        <div class="card-body">
          <h3 class="card-title">{{ $t('hub_packet_capture_title') }}</h3>
          <p class="card-desc">{{ $t('hub_packet_capture_desc') }}</p>
          <div class="card-meta">
            <span class="chip" :class="{ on: pktState?.running }">
              <i-lucide:circle-dot v-if="pktState?.running" />
              <i-lucide:circle v-else />
              {{ pktState?.running ? $t('streaming') : $t('off') }}
            </span>
            <span v-if="pktState" class="chip neutral">
              <i-lucide:list /> {{ pktState.totalEntries }} {{ $t('entries') }}
            </span>
          </div>
        </div>
        <i-lucide:arrow-right class="card-chev" />
      </router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import {
  networkUsageGQL, wifiStateGQL, bluetoothStateGQL, batteryHistoryGQL, packetCaptureStateGQL,
} from '@/lib/api/query'

const netState = ref<any>(null)
const wifiState = ref<any>(null)
const btState = ref<any>(null)
const batState = ref<any>(null)
const pktState = ref<any>(null)

function formatBytes(n: number): string {
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  if (n < 1024 * 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + ' MB'
  return (n / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

async function loadAll() {
  const [n, w, b, bat, p] = await Promise.allSettled([
    gqlFetch(networkUsageGQL, { windowDays: 7 }),
    gqlFetch(wifiStateGQL, {}),
    gqlFetch(bluetoothStateGQL, {}),
    gqlFetch(batteryHistoryGQL, { days: 1 }),
    gqlFetch(packetCaptureStateGQL, {}),
  ])
  if (n.status === 'fulfilled' && !n.value.errors) netState.value = (n.value as any).data.networkUsage
  if (w.status === 'fulfilled' && !w.value.errors) wifiState.value = (w.value as any).data.wifiState
  if (b.status === 'fulfilled' && !b.value.errors) btState.value = (b.value as any).data.bluetoothState
  if (bat.status === 'fulfilled' && !bat.value.errors) batState.value = (bat.value as any).data.batteryHistory
  if (p.status === 'fulfilled' && !p.value.errors) pktState.value = (p.value as any).data.packetCaptureState
}

onMounted(() => { loadAll() })
</script>

<style scoped lang="scss">
.hub-root { padding: 18px 22px 28px; max-width: 1200px; margin: 0 auto; display: flex; flex-direction: column; gap: 18px; }
.hub-header { display: flex; align-items: center; gap: 12px; }
.hub-title-block { display: flex; align-items: center; gap: 14px; }
.hub-icon { width: 32px; height: 32px; color: #06b6d4; }
.hub-title { margin: 0; font-size: 1.4rem; font-weight: 700; }
.hub-sub { margin: 4px 0 0; color: var(--md-sys-color-on-surface-variant); font-size: 0.88rem; }

.hub-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(310px, 1fr));
  gap: 14px;
}
.hub-card {
  position: relative;
  display: flex; align-items: center; gap: 16px;
  padding: 18px 18px 18px 16px;
  border-radius: 18px;
  background: var(--md-sys-color-surface-container);
  border: 1px solid var(--md-sys-color-outline-variant);
  text-decoration: none; color: inherit;
  transition: transform 0.15s, box-shadow 0.15s, border-color 0.15s;
  overflow: hidden;
}
.hub-card::before {
  content: ''; position: absolute; inset: 0;
  background: linear-gradient(135deg, var(--accent, #06b6d4) 0%, transparent 60%);
  opacity: 0.06; pointer-events: none;
}
.hub-card:hover { transform: translateY(-2px); box-shadow: 0 10px 24px rgba(0,0,0,0.10); border-color: var(--accent, #06b6d4); }
.launch-card { --accent: #f59e0b; }
.net-card    { --accent: #10b981; }
.wifi-card   { --accent: #3b82f6; }
.bt-card     { --accent: #6366f1; }
.bat-card    { --accent: #f97316; }
.pkt-card    { --accent: #ec4899; }

.card-icon-wrap {
  position: relative; flex-shrink: 0;
  width: 56px; height: 56px;
  display: flex; align-items: center; justify-content: center;
  background: var(--md-sys-color-surface-container-high);
  border-radius: 14px;
  color: var(--accent, #06b6d4);
}
.card-icon { width: 28px; height: 28px; }
.status-dot {
  position: absolute; top: -2px; right: -2px;
  width: 12px; height: 12px; border-radius: 50%;
  border: 2px solid var(--md-sys-color-surface-container);
  background: #94a3b8;
}
.status-dot.live { background: #22c55e; box-shadow: 0 0 0 3px rgba(34,197,94,0.25); animation: pulse 1.5s infinite; }
.status-dot.warn { background: #f59e0b; }
@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 3px rgba(34,197,94,0.25); }
  50% { box-shadow: 0 0 0 6px rgba(34,197,94,0); }
}

.card-body { flex: 1; min-width: 0; }
.card-title { margin: 0; font-size: 1.02rem; font-weight: 700; }
.card-desc { margin: 4px 0 8px; color: var(--md-sys-color-on-surface-variant); font-size: 0.82rem; line-height: 1.35; }
.card-meta { display: flex; gap: 6px; flex-wrap: wrap; }
.chip {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 3px 8px; border-radius: 999px;
  font-size: 0.72rem; font-weight: 600;
  background: var(--md-sys-color-surface-container-high);
  color: var(--md-sys-color-on-surface-variant);
}
.chip.on { background: rgba(34,197,94,0.16); color: #047857; }
.chip.warn { background: rgba(245,158,11,0.18); color: #b45309; }
.chip.neutral { background: var(--md-sys-color-surface-container-high); }
.chip svg { width: 11px; height: 11px; }

.card-chev { width: 18px; height: 18px; color: var(--md-sys-color-on-surface-variant); flex-shrink: 0; }
</style>
