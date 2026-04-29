<template>
  <div class="nu-root">
    <header class="page-header">
      <router-link to="/device-hub" class="back-btn"><i-lucide:arrow-left /></router-link>
      <div class="title-block">
        <h2 class="title"><i-lucide:activity /> {{ $t('hub_net_usage_title') }}</h2>
        <p class="sub">{{ $t('hub_net_usage_desc') }}</p>
      </div>
      <div class="actions">
        <button class="ghost-btn" @click="loadData" :disabled="loading">
          <i-lucide:refresh-cw :class="{ spin: loading }" /> {{ $t('refresh') }}
        </button>
      </div>
    </header>

    <div v-if="data && !data.usageAccessGranted" class="warn-card">
      <i-lucide:lock />
      <div class="warn-text">
        <strong>{{ $t('needs_usage_access') }}</strong>
        <span>{{ $t('grant_usage_access_hint') }}</span>
      </div>
    </div>

    <div class="window-row">
      <div class="window-tabs">
        <button v-for="d in windows" :key="d.value" class="win-btn" :class="{ active: windowDays === d.value }" @click="windowDays = d.value; loadData()">
          {{ d.label }}
        </button>
      </div>
      <div v-if="data" class="totals">
        <div class="total-card down">
          <i-lucide:arrow-down />
          <div>
            <strong>{{ formatBytes(data.totalRx) }}</strong>
            <span>{{ $t('downloaded') }}</span>
          </div>
        </div>
        <div class="total-card up">
          <i-lucide:arrow-up />
          <div>
            <strong>{{ formatBytes(data.totalTx) }}</strong>
            <span>{{ $t('uploaded') }}</span>
          </div>
        </div>
      </div>
    </div>

    <section v-if="data && data.apps.length > 0" class="app-list">
      <div v-for="a in data.apps.slice(0, 100)" :key="a.packageName" class="app-row">
        <div class="app-info">
          <strong class="app-label">{{ a.label }}</strong>
          <span class="app-pkg">{{ a.packageName }}</span>
        </div>
        <div class="bar-wrap">
          <div class="bar-track">
            <div class="bar-fill rx" :style="{ width: barPct(a.rxBytes) + '%' }" :title="formatBytes(a.rxBytes)"></div>
            <div class="bar-fill tx" :style="{ width: barPct(a.txBytes) + '%' }" :title="formatBytes(a.txBytes)"></div>
          </div>
          <div class="bar-meta">
            <span class="meta-item rx"><i-lucide:arrow-down /> {{ formatBytes(a.rxBytes) }}</span>
            <span class="meta-item tx"><i-lucide:arrow-up /> {{ formatBytes(a.txBytes) }}</span>
            <span v-if="a.rxBytesMobile + a.txBytesMobile > 0" class="meta-item mobile">
              <i-lucide:smartphone /> {{ formatBytes(a.rxBytesMobile + a.txBytesMobile) }}
            </span>
            <span v-if="a.rxBytesWifi + a.txBytesWifi > 0" class="meta-item wifi">
              <i-lucide:wifi /> {{ formatBytes(a.rxBytesWifi + a.txBytesWifi) }}
            </span>
          </div>
        </div>
      </div>
    </section>
    <div v-else-if="data && data.usageAccessGranted" class="empty">
      <i-lucide:activity />
      <h3>{{ $t('no_network_data') }}</h3>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { gqlFetch } from '@/lib/api/gql-client'
import { networkUsageGQL } from '@/lib/api/query'

const { t } = useI18n()
interface IApp { packageName: string; label: string; rxBytes: number; txBytes: number; rxBytesWifi: number; txBytesWifi: number; rxBytesMobile: number; txBytesMobile: number }
interface IData { sinceMs: number; untilMs: number; totalRx: number; totalTx: number; activeNetwork: string; usageAccessGranted: boolean; apps: IApp[] }

const data = ref<IData | null>(null)
const loading = ref(false)
const windowDays = ref(7)
const windows = [
  { label: '24h', value: 1 },
  { label: '7d', value: 7 },
  { label: '30d', value: 30 },
  { label: '90d', value: 90 },
]

async function loadData() {
  loading.value = true
  try {
    const r = await gqlFetch<{ networkUsage: IData }>(networkUsageGQL, { windowDays: windowDays.value })
    if (!r.errors) data.value = r.data.networkUsage
  } finally { loading.value = false }
}

const maxAppBytes = computed(() => {
  if (!data.value || data.value.apps.length === 0) return 1
  return Math.max(...data.value.apps.map(a => a.rxBytes + a.txBytes))
})

function barPct(bytes: number): number {
  if (maxAppBytes.value === 0) return 0
  return Math.min(100, (bytes / maxAppBytes.value) * 100)
}

function formatBytes(n: number): string {
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  if (n < 1024 * 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + ' MB'
  return (n / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

onMounted(() => loadData())
</script>

<style scoped lang="scss">
.nu-root { padding: 18px 22px 28px; max-width: 1200px; margin: 0 auto; display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; align-items: center; gap: 14px; }
.back-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 38px; height: 38px; border-radius: 50%;
  background: var(--md-sys-color-surface-container); color: inherit; text-decoration: none;
}
.title-block { flex: 1; }
.title-block .title { margin: 0; font-size: 1.25rem; font-weight: 700; display: flex; align-items: center; gap: 8px; }
.title svg { width: 22px; height: 22px; color: #10b981; }
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

.warn-card {
  display: flex; align-items: flex-start; gap: 12px;
  padding: 14px 18px; border-radius: 14px;
  background: rgba(245,158,11,0.12);
  border: 1px solid rgba(245,158,11,0.35); color: #b45309;
  font-size: 0.85rem;
}
.warn-card svg { width: 22px; height: 22px; flex-shrink: 0; margin-top: 2px; }
.warn-text { display: flex; flex-direction: column; gap: 2px; }
.warn-text strong { font-size: 0.9rem; }

.window-row {
  display: flex; align-items: center; justify-content: space-between;
  flex-wrap: wrap; gap: 12px;
}
.window-tabs { display: flex; gap: 4px; background: var(--md-sys-color-surface-container); padding: 4px; border-radius: 10px; }
.win-btn {
  padding: 6px 14px; border-radius: 7px; border: none;
  background: transparent; color: inherit; font: inherit; cursor: pointer;
  font-size: 0.82rem; font-weight: 600;
}
.win-btn.active { background: #10b981; color: white; }
.totals { display: flex; gap: 10px; }
.total-card {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px; border-radius: 12px;
  background: var(--md-sys-color-surface-container);
  border: 1px solid var(--md-sys-color-outline-variant);
}
.total-card svg { width: 18px; height: 18px; }
.total-card.down svg { color: #10b981; }
.total-card.up svg { color: #f59e0b; }
.total-card div { display: flex; flex-direction: column; }
.total-card strong { font-size: 1rem; font-weight: 700; }
.total-card span { font-size: 0.7rem; color: var(--md-sys-color-on-surface-variant); }

.app-list { display: flex; flex-direction: column; gap: 6px; }
.app-row {
  display: flex; gap: 14px; align-items: center;
  padding: 12px 16px;
  background: var(--md-sys-color-surface-container);
  border-radius: 12px;
}
.app-info { width: 200px; flex-shrink: 0; display: flex; flex-direction: column; gap: 2px; }
.app-label { font-size: 0.88rem; font-weight: 600; }
.app-pkg { font-size: 0.7rem; color: var(--md-sys-color-on-surface-variant); word-break: break-all; }
.bar-wrap { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6px; }
.bar-track {
  position: relative;
  height: 8px; border-radius: 4px;
  background: var(--md-sys-color-surface-container-high);
  overflow: hidden;
}
.bar-fill {
  position: absolute; left: 0; top: 0; height: 100%;
  border-radius: 4px;
  transition: width 0.4s;
}
.bar-fill.rx { background: linear-gradient(90deg, #10b981, #34d399); z-index: 1; }
.bar-fill.tx { background: linear-gradient(90deg, #f59e0b, #fbbf24); opacity: 0.6; }
.bar-meta { display: flex; gap: 12px; flex-wrap: wrap; font-size: 0.74rem; }
.meta-item { display: inline-flex; align-items: center; gap: 4px; color: var(--md-sys-color-on-surface-variant); }
.meta-item svg { width: 11px; height: 11px; }
.meta-item.rx { color: #047857; }
.meta-item.tx { color: #b45309; }

.empty {
  text-align: center; padding: 60px 20px;
  color: var(--md-sys-color-on-surface-variant);
  display: flex; flex-direction: column; align-items: center; gap: 8px;
}
.empty svg { width: 36px; height: 36px; opacity: 0.5; }
.empty h3 { margin: 0; font-weight: 500; font-size: 0.95rem; }
</style>
