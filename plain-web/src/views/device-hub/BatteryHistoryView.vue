<template>
  <div class="bh-root">
    <header class="page-header">
      <router-link to="/device-hub" class="back-btn"><i-lucide:arrow-left /></router-link>
      <div class="title-block">
        <h2 class="title"><i-lucide:battery-charging /> {{ $t('hub_battery_title') }}</h2>
        <p class="sub">{{ $t('hub_battery_desc') }}</p>
      </div>
      <div class="actions">
        <button class="ghost-btn" @click="loadData" :disabled="loading">
          <i-lucide:refresh-cw :class="{ spin: loading }" /> {{ $t('refresh') }}
        </button>
        <button class="ghost-btn danger" @click="onClear" :disabled="!data?.samples?.length">
          <i-lucide:trash-2 /> {{ $t('clear') }}
        </button>
      </div>
    </header>

    <section v-if="data" class="hero-card" :class="{ charging: data.charging }">
      <div class="big-level">
        <div class="ring" :style="{ '--pct': data.currentLevel + '%' }">
          <div class="ring-inner">
            <strong>{{ data.currentLevel >= 0 ? data.currentLevel : '–' }}<span>%</span></strong>
            <span class="state">
              <i-lucide:zap v-if="data.charging" />
              <i-lucide:battery v-else />
              {{ data.charging ? $t('charging') : $t('discharging') }}
            </span>
          </div>
        </div>
      </div>
      <div class="info-grid">
        <div class="info-tile">
          <i-lucide:plug />
          <div><strong>{{ pluggedLabel(data.plugged) }}</strong><span>{{ $t('source') }}</span></div>
        </div>
        <div v-if="latest" class="info-tile">
          <i-lucide:thermometer />
          <div><strong>{{ latest.temperatureC.toFixed(1) }}°C</strong><span>{{ $t('temperature') }}</span></div>
        </div>
        <div v-if="latest" class="info-tile">
          <i-lucide:bolt />
          <div><strong>{{ (latest.voltageMv / 1000).toFixed(2) }} V</strong><span>{{ $t('voltage') }}</span></div>
        </div>
        <div class="info-tile">
          <i-lucide:database />
          <div><strong>{{ data.samples.length }}</strong><span>{{ $t('samples') }}</span></div>
        </div>
      </div>
    </section>

    <div class="window-row">
      <div class="window-tabs">
        <button v-for="d in windows" :key="d.value" class="win-btn" :class="{ active: days === d.value }" @click="days = d.value; loadData()">
          {{ d.label }}
        </button>
      </div>
    </div>

    <section v-if="data && data.samples.length > 1" class="chart-card">
      <h3 class="chart-title">{{ $t('battery_level_over_time') }}</h3>
      <svg class="chart" viewBox="0 0 1000 300" preserveAspectRatio="none">
        <line v-for="y in [0, 25, 50, 75, 100]" :key="y" :x1="0" :x2="1000"
          :y1="300 - (y / 100) * 280 - 10" :y2="300 - (y / 100) * 280 - 10"
          class="grid-line" />
        <text v-for="y in [0, 25, 50, 75, 100]" :key="'t' + y" x="6" :y="300 - (y / 100) * 280 - 14"
          class="grid-label">{{ y }}%</text>

        <path :d="chargingAreaPath" class="charge-band" />
        <path :d="linePath" class="level-line" />
        <path :d="areaPath" class="level-area" />
      </svg>
      <div class="legend">
        <span class="legend-item"><span class="swatch level"></span>{{ $t('level') }}</span>
        <span class="legend-item"><span class="swatch charge"></span>{{ $t('charging_periods') }}</span>
      </div>
    </section>
    <div v-else class="empty">
      <i-lucide:battery />
      <h3>{{ $t('not_enough_samples') }}</h3>
      <span class="empty-hint">{{ $t('battery_sampler_hint') }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { gqlFetch } from '@/lib/api/gql-client'
import { batteryHistoryGQL } from '@/lib/api/query'
import { clearBatteryHistoryGQL } from '@/lib/api/mutation'

const { t } = useI18n()
interface ISample { ts: number; level: number; plugged: number; temperatureC: number; voltageMv: number; status: number }
interface IData { sinceMs: number; untilMs: number; charging: boolean; currentLevel: number; plugged: string; samples: ISample[] }

const data = ref<IData | null>(null)
const loading = ref(false)
const days = ref(7)
const windows = [
  { label: '24h', value: 1 },
  { label: '7d', value: 7 },
  { label: '14d', value: 14 },
  { label: '30d', value: 30 },
]

async function loadData() {
  loading.value = true
  try {
    const r = await gqlFetch<{ batteryHistory: IData }>(batteryHistoryGQL, { days: days.value })
    if (!r.errors) data.value = r.data.batteryHistory
  } finally { loading.value = false }
}

async function onClear() {
  if (!confirm(t('confirm_clear_battery_history'))) return
  await gqlFetch(clearBatteryHistoryGQL, {})
  await loadData()
}

const latest = computed(() => data.value?.samples[data.value.samples.length - 1] ?? null)

const linePath = computed(() => buildPath('line'))
const areaPath = computed(() => buildPath('area'))
const chargingAreaPath = computed(() => {
  if (!data.value || data.value.samples.length < 2) return ''
  const ss = data.value.samples
  const span = data.value.untilMs - data.value.sinceMs
  if (span <= 0) return ''
  let d = ''
  let inCharge = false
  let startX = 0
  for (let i = 0; i < ss.length; i++) {
    const s = ss[i]
    const charging = s.plugged > 0
    const x = ((s.ts - data.value.sinceMs) / span) * 1000
    if (charging && !inCharge) { startX = x; inCharge = true }
    if (!charging && inCharge) { d += `M${startX} 10 L${x} 10 L${x} 290 L${startX} 290 Z `; inCharge = false }
  }
  if (inCharge) d += `M${startX} 10 L1000 10 L1000 290 L${startX} 290 Z`
  return d
})

function buildPath(kind: 'line' | 'area'): string {
  if (!data.value || data.value.samples.length < 2) return ''
  const ss = data.value.samples
  const span = data.value.untilMs - data.value.sinceMs
  if (span <= 0) return ''
  const points = ss.map(s => {
    const x = ((s.ts - data.value!.sinceMs) / span) * 1000
    const y = 300 - (Math.max(0, Math.min(100, s.level)) / 100) * 280 - 10
    return [x, y] as const
  })
  let d = `M${points[0][0]} ${points[0][1]}`
  for (let i = 1; i < points.length; i++) d += ` L${points[i][0]} ${points[i][1]}`
  if (kind === 'area') {
    d += ` L${points[points.length - 1][0]} 290 L${points[0][0]} 290 Z`
  }
  return d
}

function pluggedLabel(p: string): string {
  if (p === 'ac') return 'AC'
  if (p === 'usb') return 'USB'
  if (p === 'wireless') return t('wireless')
  if (p === 'unplugged') return t('unplugged')
  return t('other')
}

onMounted(() => loadData())
</script>

<style scoped lang="scss">
.bh-root { padding: 18px 22px 28px; max-width: 1200px; margin: 0 auto; display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.back-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 38px; height: 38px; border-radius: 50%;
  background: var(--md-sys-color-surface-container); color: inherit; text-decoration: none;
}
.title-block { flex: 1; }
.title-block .title { margin: 0; font-size: 1.25rem; font-weight: 700; display: flex; align-items: center; gap: 8px; }
.title svg { width: 22px; height: 22px; color: #f97316; }
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

.hero-card {
  display: flex; gap: 28px; align-items: center; padding: 22px;
  border-radius: 22px;
  background: linear-gradient(135deg, rgba(249,115,22,0.10), rgba(245,158,11,0.06));
  border: 1px solid var(--md-sys-color-outline-variant);
  flex-wrap: wrap;
}
.hero-card.charging { background: linear-gradient(135deg, rgba(34,197,94,0.10), rgba(16,185,129,0.06)); }
.big-level { flex-shrink: 0; }
.ring {
  width: 140px; height: 140px; border-radius: 50%;
  background: conic-gradient(currentColor var(--pct), var(--md-sys-color-surface-container-high) 0%);
  color: #f97316;
  display: flex; align-items: center; justify-content: center;
}
.hero-card.charging .ring { color: #16a34a; }
.ring-inner {
  width: 116px; height: 116px; border-radius: 50%;
  background: var(--md-sys-color-surface);
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px;
}
.ring-inner strong { font-size: 1.8rem; font-weight: 700; }
.ring-inner strong span { font-size: 0.9rem; color: var(--md-sys-color-on-surface-variant); margin-left: 2px; }
.ring-inner .state { font-size: 0.74rem; color: var(--md-sys-color-on-surface-variant); display: inline-flex; align-items: center; gap: 4px; }
.ring-inner svg { width: 11px; height: 11px; }

.info-grid { flex: 1; display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 12px; }
.info-tile { display: flex; align-items: center; gap: 10px; padding: 12px 14px; border-radius: 14px; background: var(--md-sys-color-surface-container); }
.info-tile svg { width: 22px; height: 22px; color: #f97316; }
.hero-card.charging .info-tile svg { color: #16a34a; }
.info-tile div { display: flex; flex-direction: column; }
.info-tile strong { font-size: 1rem; font-weight: 700; }
.info-tile span { font-size: 0.72rem; color: var(--md-sys-color-on-surface-variant); }

.window-row { display: flex; align-items: center; }
.window-tabs { display: flex; gap: 4px; background: var(--md-sys-color-surface-container); padding: 4px; border-radius: 10px; }
.win-btn { padding: 6px 14px; border-radius: 7px; border: none; background: transparent; color: inherit; font: inherit; cursor: pointer; font-size: 0.82rem; font-weight: 600; }
.win-btn.active { background: #f97316; color: white; }

.chart-card { padding: 20px; border-radius: 18px; background: var(--md-sys-color-surface-container); }
.chart-title { margin: 0 0 12px; font-size: 0.95rem; font-weight: 700; }
.chart { width: 100%; height: 280px; }
.grid-line { stroke: var(--md-sys-color-outline-variant); stroke-width: 1; stroke-dasharray: 4 4; }
.grid-label { font-size: 11px; fill: var(--md-sys-color-on-surface-variant); }
.charge-band { fill: rgba(34,197,94,0.12); }
.level-area { fill: rgba(249,115,22,0.18); }
.level-line { fill: none; stroke: #f97316; stroke-width: 2.5; stroke-linejoin: round; stroke-linecap: round; }

.legend { display: flex; gap: 16px; margin-top: 10px; font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); }
.legend-item { display: inline-flex; align-items: center; gap: 6px; }
.swatch { width: 14px; height: 8px; border-radius: 2px; }
.swatch.level { background: #f97316; }
.swatch.charge { background: rgba(34,197,94,0.5); }

.empty {
  text-align: center; padding: 60px 20px;
  color: var(--md-sys-color-on-surface-variant);
  display: flex; flex-direction: column; align-items: center; gap: 8px;
}
.empty svg { width: 36px; height: 36px; opacity: 0.5; }
.empty h3 { margin: 0; font-weight: 500; font-size: 0.95rem; }
.empty-hint { font-size: 0.78rem; max-width: 380px; }
</style>
