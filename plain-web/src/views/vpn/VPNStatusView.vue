<template>
  <div class="page-root">
    <div class="page-header">
      <button class="back-btn" @click="$router.back()"><i-lucide:arrow-left /></button>
      <h2 class="page-title">VPN Status</h2>
      <button class="refresh-btn" @click="load"><i-lucide:refresh-cw :class="{ spin: loading }" /></button>
    </div>
    <div v-if="loading && !data" class="loading"><i-lucide:loader-circle class="spin" /> Checking VPN status...</div>
    <div v-else-if="data">
      <div class="status-card" :class="data.isConnected ? 'connected' : 'disconnected'">
        <div class="sc-icon">{{ data.isConnected ? '🔒' : '🔓' }}</div>
        <div class="sc-state">{{ data.isConnected ? 'CONNECTED' : 'DISCONNECTED' }}</div>
        <div class="sc-network" v-if="data.isConnected">via {{ data.vpnName || 'VPN' }}</div>
        <div class="sc-network" v-else>No active VPN tunnel</div>
      </div>
      <div class="detail-card" v-if="data.isConnected">
        <div class="detail-row"><span class="dr-label">VPN App</span><span class="dr-val">{{ data.vpnPackage || '—' }}</span></div>
        <div class="detail-row"><span class="dr-label">VPN IP</span><span class="dr-val mono">{{ data.vpnIp || '—' }}</span></div>
        <div class="detail-row"><span class="dr-label">Type</span><span class="dr-val">{{ data.vpnType || '—' }}</span></div>
        <div class="detail-row"><span class="dr-label">MTU</span><span class="dr-val">{{ data.mtu > 0 ? data.mtu : '—' }}</span></div>
      </div>
      <div class="network-card">
        <div class="nc-title">Active Network Interfaces</div>
        <div class="if-row" v-for="iface in data.interfaces" :key="iface.name">
          <span class="if-name">{{ iface.name }}</span>
          <span class="if-addr mono">{{ iface.address }}</span>
        </div>
        <div v-if="!data.interfaces?.length" class="if-empty">No interfaces reported</div>
      </div>
    </div>
    <div v-if="error" class="error-box"><i-lucide:alert-triangle /> {{ error }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import { vpnStatusGQL } from '@/lib/api/query'

type VPNData = {
  isConnected: boolean; vpnName: string; vpnPackage: string; vpnIp: string;
  vpnType: string; mtu: number; interfaces: Array<{ name: string; address: string }>
}

const loading = ref(false)
const error = ref('')
const data = ref<VPNData | null>(null)
let timer = 0

async function load() {
  loading.value = true; error.value = ''
  try {
    const r = await gqlFetch<{ vpnStatus: VPNData }>(vpnStatusGQL)
    if (r.errors?.length) { error.value = r.errors[0].message; return }
    data.value = r.data.vpnStatus
  } catch { error.value = 'Could not reach device.' }
  finally { loading.value = false }
}

onMounted(() => { load(); timer = window.setInterval(load, 5000) })
onUnmounted(() => clearInterval(timer))
</script>

<style scoped lang="scss">
.page-root { padding: 18px 20px 32px; display: flex; flex-direction: column; gap: 16px; max-width: 700px; margin: 0 auto; }
.page-header { display: flex; align-items: center; gap: 12px; }
.back-btn, .refresh-btn { background: none; border: none; cursor: pointer; color: var(--md-sys-color-on-surface); display: flex; align-items: center; padding: 6px; border-radius: 50%; transition: background 0.18s; &:hover { background: var(--md-sys-color-surface-container); } svg { width: 22px; height: 22px; } }
.page-title { font-size: 1.4rem; font-weight: 700; margin: 0; flex: 1; }
.spin { animation: spin 1s linear infinite; } @keyframes spin { to { transform: rotate(360deg); } }
.loading { display: flex; align-items: center; gap: 10px; color: var(--md-sys-color-on-surface-variant); svg { width: 20px; height: 20px; } }
.status-card { border-radius: 24px; padding: 36px 20px; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 8px; &.connected { background: rgba(34,197,94,0.12); border: 2px solid rgba(34,197,94,0.4); } &.disconnected { background: rgba(239,68,68,0.1); border: 2px solid rgba(239,68,68,0.3); } }
.sc-icon { font-size: 3.5rem; }
.sc-state { font-size: 2rem; font-weight: 800; .connected & { color: #16a34a; } .disconnected & { color: #ef4444; } }
.sc-network { color: var(--md-sys-color-on-surface-variant); font-size: 0.95rem; }
.detail-card { background: var(--md-sys-color-surface-container); border-radius: 16px; padding: 16px; }
.detail-row { display: flex; justify-content: space-between; align-items: center; padding: 8px 0; border-bottom: 1px solid var(--md-sys-color-outline-variant); &:last-child { border-bottom: none; } }
.dr-label { font-size: 0.85rem; color: var(--md-sys-color-on-surface-variant); }
.dr-val { font-size: 0.88rem; font-weight: 600; &.mono { font-family: monospace; } }
.network-card { background: var(--md-sys-color-surface-container); border-radius: 16px; padding: 16px; }
.nc-title { font-size: 0.85rem; font-weight: 600; color: var(--md-sys-color-on-surface-variant); margin-bottom: 10px; }
.if-row { display: flex; justify-content: space-between; padding: 6px 0; border-bottom: 1px solid var(--md-sys-color-outline-variant); &:last-child { border-bottom: none; } }
.if-name { font-weight: 600; font-size: 0.88rem; }
.if-addr { font-size: 0.82rem; color: var(--md-sys-color-on-surface-variant); font-family: monospace; }
.if-empty { color: var(--md-sys-color-on-surface-variant); font-size: 0.88rem; }
.error-box { display: flex; align-items: center; gap: 10px; padding: 14px; border-radius: 14px; font-size: 0.9rem; background: rgba(239,68,68,0.08); color: #ef4444; svg { width: 18px; height: 18px; } }
</style>
