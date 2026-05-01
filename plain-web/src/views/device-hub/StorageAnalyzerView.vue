<template>
  <div class="page-root">
    <div class="page-header">
      <button class="back-btn" @click="$router.back()"><i-lucide:arrow-left /></button>
      <h2 class="page-title">Storage Analyzer</h2>
      <button class="refresh-btn" @click="load" :disabled="loading"><i-lucide:refresh-cw :class="{ spin: loading }" /></button>
    </div>
    <div v-if="loading && !data" class="loading"><i-lucide:loader-circle class="spin" /> Analyzing storage...</div>
    <div v-else-if="data">
      <div class="usage-bar-wrap">
        <div class="usage-bar">
          <div v-for="seg in segments" :key="seg.label" class="usage-seg"
            :style="{ width: seg.pct + '%', background: seg.color }" :title="seg.label" />
        </div>
        <div class="usage-labels">
          <div class="ul-item" v-for="seg in segments" :key="seg.label">
            <div class="ul-dot" :style="{ background: seg.color }"></div>
            <span class="ul-label">{{ seg.label }}</span>
            <span class="ul-size">{{ fmtBytes(seg.bytes) }}</span>
          </div>
        </div>
      </div>
      <div class="summary-grid">
        <div class="sum-card">
          <div class="sum-label">Total</div>
          <div class="sum-value">{{ fmtBytes(data.totalBytes) }}</div>
        </div>
        <div class="sum-card used">
          <div class="sum-label">Used</div>
          <div class="sum-value">{{ fmtBytes(data.usedBytes) }}</div>
          <div class="sum-pct">{{ Math.round(data.usedBytes / data.totalBytes * 100) }}%</div>
        </div>
        <div class="sum-card free">
          <div class="sum-label">Free</div>
          <div class="sum-value">{{ fmtBytes(data.freeBytes) }}</div>
          <div class="sum-pct">{{ Math.round(data.freeBytes / data.totalBytes * 100) }}%</div>
        </div>
      </div>
    </div>
    <div v-if="error" class="error-box"><i-lucide:alert-triangle />{{ error }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import { storageBreakdownGQL } from '@/lib/api/query'

type StorageData = {
  totalBytes: number; usedBytes: number; freeBytes: number
  appsBytes: number; imagesBytes: number; videosBytes: number
  audioBytes: number; documentsBytes: number; otherBytes: number; cacheBytes: number
}

const loading = ref(false)
const error = ref('')
const data = ref<StorageData | null>(null)

const segments = computed(() => {
  if (!data.value) return []
  const d = data.value
  return [
    { label: 'Apps', bytes: d.appsBytes, color: '#6366f1' },
    { label: 'Photos', bytes: d.imagesBytes, color: '#22c55e' },
    { label: 'Videos', bytes: d.videosBytes, color: '#3b82f6' },
    { label: 'Audio', bytes: d.audioBytes, color: '#f59e0b' },
    { label: 'Cache', bytes: d.cacheBytes, color: '#f97316' },
    { label: 'Other', bytes: d.otherBytes, color: '#6b7280' },
    { label: 'Free', bytes: d.freeBytes, color: 'rgba(100,116,139,0.2)' },
  ].filter(s => s.bytes > 0).map(s => ({ ...s, pct: s.bytes / d.totalBytes * 100 }))
})

function fmtBytes(b: number) {
  if (b <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(b) / Math.log(1024))
  return (b / Math.pow(1024, i)).toFixed(1) + ' ' + units[i]
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const r = await gqlFetch<{ storageBreakdown: StorageData }>(storageBreakdownGQL)
    if (r.errors?.length) { error.value = r.errors[0].message; return }
    data.value = r.data.storageBreakdown
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
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.loading { display: flex; align-items: center; gap: 10px; color: var(--md-sys-color-on-surface-variant); svg { width: 20px; height: 20px; } }
.usage-bar-wrap { background: var(--md-sys-color-surface-container); border-radius: 18px; padding: 18px; }
.usage-bar { display: flex; height: 28px; border-radius: 14px; overflow: hidden; gap: 2px; margin-bottom: 16px; }
.usage-seg { height: 100%; transition: width 0.5s ease; min-width: 2px; }
.usage-labels { display: flex; flex-wrap: wrap; gap: 8px 16px; }
.ul-item { display: flex; align-items: center; gap: 6px; }
.ul-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.ul-label { font-size: 0.82rem; }
.ul-size { font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); font-variant-numeric: tabular-nums; }
.summary-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.sum-card { background: var(--md-sys-color-surface-container); border-radius: 14px; padding: 16px 12px; text-align: center; &.used { border: 1px solid rgba(239,68,68,0.3); } &.free { border: 1px solid rgba(34,197,94,0.3); } }
.sum-label { font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); margin-bottom: 4px; }
.sum-value { font-size: 1.2rem; font-weight: 700; font-variant-numeric: tabular-nums; }
.sum-pct { font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); margin-top: 2px; }
.error-box { display: flex; align-items: center; gap: 10px; padding: 14px; border-radius: 14px; font-size: 0.9rem; background: rgba(239,68,68,0.08); color: #ef4444; svg { width: 18px; height: 18px; } }
</style>
