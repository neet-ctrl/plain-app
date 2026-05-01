<template>
  <div class="page-root">
    <div class="page-header">
      <button class="back-btn" @click="$router.back()"><i-lucide:arrow-left /></button>
      <h2 class="page-title">SIM / Carrier Info</h2>
      <button class="refresh-btn" @click="load"><i-lucide:refresh-cw :class="{ spin: loading }" /></button>
    </div>
    <div v-if="loading && !sims.length" class="loading"><i-lucide:loader-circle class="spin" /> Reading SIM info...</div>
    <div v-else-if="!sims.length && !loading" class="empty-state">
      <i-lucide:smartphone />
      <div>No SIM card information available</div>
    </div>
    <div v-else class="sim-list">
      <div class="sim-card" v-for="sim in sims" :key="sim.slotIndex">
        <div class="sim-header">
          <div class="sim-slot-badge">SIM {{ sim.slotIndex + 1 }}</div>
          <div class="sim-state" :class="sim.simState.toLowerCase()">{{ sim.simState }}</div>
        </div>
        <div class="sim-carrier">
          <div class="carrier-icon">📡</div>
          <div>
            <div class="carrier-name">{{ sim.carrierName || 'Unknown Carrier' }}</div>
            <div class="carrier-operator">{{ sim.operatorName }}</div>
          </div>
        </div>
        <div class="sim-details">
          <div class="sd-row"><span class="sd-label">Phone Number</span><span class="sd-val">{{ sim.phoneNumber || 'N/A' }}</span></div>
          <div class="sd-row"><span class="sd-label">Network Type</span><span class="sd-val">{{ sim.networkTypeName }}</span></div>
          <div class="sd-row"><span class="sd-label">MCC / MNC</span><span class="sd-val mono">{{ sim.mcc }} / {{ sim.mnc }}</span></div>
          <div class="sd-row"><span class="sd-label">Roaming</span>
            <span class="sd-val" :style="{ color: sim.isRoaming ? '#ef4444' : '#22c55e' }">
              {{ sim.isRoaming ? '⚠ Yes' : 'No' }}
            </span>
          </div>
          <div class="sd-row"><span class="sd-label">Data Active</span>
            <span class="sd-val" :style="{ color: sim.isDataActive ? '#22c55e' : '#9ca3af' }">
              {{ sim.isDataActive ? '✓ Yes' : 'No' }}
            </span>
          </div>
          <div class="sd-row"><span class="sd-label">Signal Strength</span>
            <div class="signal-bars">
              <div class="bar" v-for="i in 5" :key="i" :class="{ active: sim.signalBars >= i }" :style="{ height: (8 + i * 4) + 'px' }"></div>
            </div>
          </div>
          <div class="sd-row"><span class="sd-label">ICCID</span><span class="sd-val mono small">{{ sim.iccid || 'N/A' }}</span></div>
        </div>
      </div>
    </div>
    <div v-if="error" class="error-box"><i-lucide:alert-triangle /> {{ error }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import { simInfoGQL } from '@/lib/api/query'

type SimInfo = {
  slotIndex: number; carrierName: string; operatorName: string; phoneNumber: string
  networkTypeName: string; mcc: string; mnc: string; isRoaming: boolean; isDataActive: boolean
  signalBars: number; simState: string; iccid: string
}

const loading = ref(false)
const error = ref('')
const sims = ref<SimInfo[]>([])

async function load() {
  loading.value = true; error.value = ''
  try {
    const r = await gqlFetch<{ simInfo: SimInfo[] }>(simInfoGQL)
    if (r.errors?.length) { error.value = r.errors[0].message; return }
    sims.value = r.data.simInfo
  } catch { error.value = 'Could not reach device.' }
  finally { loading.value = false }
}

onMounted(load)
</script>

<style scoped lang="scss">
.page-root { padding: 18px 20px 32px; display: flex; flex-direction: column; gap: 16px; max-width: 700px; margin: 0 auto; }
.page-header { display: flex; align-items: center; gap: 12px; }
.back-btn, .refresh-btn { background: none; border: none; cursor: pointer; color: var(--md-sys-color-on-surface); display: flex; align-items: center; padding: 6px; border-radius: 50%; transition: background 0.18s; &:hover { background: var(--md-sys-color-surface-container); } svg { width: 22px; height: 22px; } }
.page-title { font-size: 1.4rem; font-weight: 700; margin: 0; flex: 1; }
.spin { animation: spin 1s linear infinite; } @keyframes spin { to { transform: rotate(360deg); } }
.loading { display: flex; align-items: center; gap: 10px; color: var(--md-sys-color-on-surface-variant); svg { width: 20px; height: 20px; } }
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 48px 20px; color: var(--md-sys-color-on-surface-variant); svg { width: 48px; height: 48px; opacity: 0.4; } }
.sim-list { display: flex; flex-direction: column; gap: 16px; }
.sim-card { background: var(--md-sys-color-surface-container); border-radius: 20px; padding: 18px; }
.sim-header { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.sim-slot-badge { background: #6366f1; color: #fff; font-size: 0.8rem; font-weight: 700; padding: 3px 12px; border-radius: 999px; }
.sim-state { font-size: 0.8rem; padding: 3px 12px; border-radius: 999px; font-weight: 700; &.ready { background: rgba(34,197,94,0.14); color: #16a34a; } &.absent { background: rgba(239,68,68,0.14); color: #ef4444; } &.unknown { background: var(--md-sys-color-surface-container-high); color: var(--md-sys-color-on-surface-variant); } }
.sim-carrier { display: flex; align-items: center; gap: 14px; padding: 14px 0; border-bottom: 1px solid var(--md-sys-color-outline-variant); margin-bottom: 12px; }
.carrier-icon { font-size: 2.5rem; }
.carrier-name { font-size: 1.2rem; font-weight: 700; }
.carrier-operator { font-size: 0.85rem; color: var(--md-sys-color-on-surface-variant); }
.sim-details { display: flex; flex-direction: column; gap: 2px; }
.sd-row { display: flex; justify-content: space-between; align-items: center; padding: 8px 0; border-bottom: 1px solid var(--md-sys-color-outline-variant); &:last-child { border-bottom: none; } }
.sd-label { font-size: 0.83rem; color: var(--md-sys-color-on-surface-variant); }
.sd-val { font-size: 0.88rem; font-weight: 600; &.mono { font-family: monospace; } &.small { font-size: 0.75rem; font-weight: 400; } }
.signal-bars { display: flex; align-items: flex-end; gap: 3px; height: 28px; }
.bar { width: 6px; border-radius: 3px 3px 0 0; background: var(--md-sys-color-surface-container-high); &.active { background: #22c55e; } }
.error-box { display: flex; align-items: center; gap: 10px; padding: 14px; border-radius: 14px; font-size: 0.9rem; background: rgba(239,68,68,0.08); color: #ef4444; svg { width: 18px; height: 18px; } }
</style>
