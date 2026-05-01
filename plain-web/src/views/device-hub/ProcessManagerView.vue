<template>
  <div class="page-root">
    <div class="page-header">
      <button class="back-btn" @click="$router.back()"><i-lucide:arrow-left /></button>
      <h2 class="page-title">Process Manager</h2>
      <button class="refresh-btn" @click="load" :disabled="loading"><i-lucide:refresh-cw :class="{ spin: loading }" /></button>
    </div>
    <div class="search-bar">
      <i-lucide:search />
      <input v-model="search" placeholder="Search apps..." />
    </div>
    <div v-if="loading && !processes.length" class="loading"><i-lucide:loader-circle class="spin" /> Loading processes...</div>
    <div class="process-list" v-else>
      <div class="proc-row" v-for="p in filtered" :key="p.pid">
        <div class="proc-info">
          <div class="proc-name">{{ p.appLabel || p.processName }}</div>
          <div class="proc-pkg">{{ p.processName }} · PID {{ p.pid }}</div>
          <div class="proc-tags">
            <span class="tag" :class="importanceClass(p.importance)">{{ p.importanceLabel }}</span>
            <span class="tag mem">{{ fmtKb(p.rssKb) }}</span>
          </div>
        </div>
        <button class="kill-btn" @click="killProc(p.pid)" title="Force Stop">
          <i-lucide:x-circle />
        </button>
      </div>
      <div v-if="!filtered.length && search" class="no-results">No results for "{{ search }}"</div>
    </div>
    <div v-if="error" class="error-box"><i-lucide:alert-triangle />{{ error }}</div>
    <div v-if="killSuccess" class="success-box"><i-lucide:check-circle /> Process killed. Refresh to update.</div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import { runningProcessesGQL } from '@/lib/api/query'
import { killProcessGQL } from '@/lib/api/mutation'

type Process = { pid: number; processName: string; appLabel: string; packageName: string; importance: number; importanceLabel: string; rssKb: number }

const loading = ref(false)
const error = ref('')
const killSuccess = ref(false)
const processes = ref<Process[]>([])
const search = ref('')

const filtered = computed(() =>
  processes.value.filter(p => !search.value || p.appLabel.toLowerCase().includes(search.value.toLowerCase()) || p.processName.toLowerCase().includes(search.value.toLowerCase()))
)

function fmtKb(kb: number) {
  if (kb < 1024) return kb + ' KB'
  return (kb / 1024).toFixed(1) + ' MB'
}

function importanceClass(imp: number) {
  if (imp <= 100) return 'fg'
  if (imp <= 200) return 'vis'
  if (imp <= 300) return 'svc'
  return 'bg'
}

async function killProc(pid: number) {
  if (!confirm(`Force stop PID ${pid}?`)) return
  const r = await gqlFetch<{ killProcess: boolean }>(killProcessGQL, { pid })
  if (r.data.killProcess) { killSuccess.value = true; setTimeout(() => killSuccess.value = false, 3000); await load() }
}

async function load() {
  loading.value = true; error.value = ''
  try {
    const r = await gqlFetch<{ runningProcesses: Process[] }>(runningProcessesGQL)
    if (r.errors?.length) { error.value = r.errors[0].message; return }
    processes.value = r.data.runningProcesses
  } catch { error.value = 'Could not reach device.' }
  finally { loading.value = false }
}

onMounted(load)
</script>

<style scoped lang="scss">
.page-root { padding: 18px 20px 32px; display: flex; flex-direction: column; gap: 14px; max-width: 800px; margin: 0 auto; }
.page-header { display: flex; align-items: center; gap: 12px; }
.back-btn, .refresh-btn { background: none; border: none; cursor: pointer; color: var(--md-sys-color-on-surface); display: flex; align-items: center; padding: 6px; border-radius: 50%; transition: background 0.18s; &:hover { background: var(--md-sys-color-surface-container); } svg { width: 22px; height: 22px; } }
.page-title { font-size: 1.4rem; font-weight: 700; margin: 0; flex: 1; }
.spin { animation: spin 1s linear infinite; } @keyframes spin { to { transform: rotate(360deg); } }
.search-bar { display: flex; align-items: center; gap: 10px; padding: 10px 14px; background: var(--md-sys-color-surface-container); border-radius: 12px; input { flex: 1; background: none; border: none; outline: none; font-size: 0.9rem; color: var(--md-sys-color-on-surface); } svg { width: 18px; height: 18px; color: var(--md-sys-color-on-surface-variant); } }
.loading { display: flex; align-items: center; gap: 10px; color: var(--md-sys-color-on-surface-variant); svg { width: 20px; height: 20px; } }
.process-list { display: flex; flex-direction: column; gap: 8px; }
.proc-row { display: flex; align-items: center; gap: 12px; padding: 12px 14px; background: var(--md-sys-color-surface-container); border-radius: 14px; }
.proc-info { flex: 1; min-width: 0; }
.proc-name { font-size: 0.95rem; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.proc-pkg { font-size: 0.76rem; color: var(--md-sys-color-on-surface-variant); margin: 2px 0; font-family: monospace; overflow: hidden; text-overflow: ellipsis; }
.proc-tags { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 4px; }
.tag { font-size: 0.7rem; padding: 2px 8px; border-radius: 999px; font-weight: 600; &.fg { background: rgba(34,197,94,0.15); color: #16a34a; } &.vis { background: rgba(99,102,241,0.15); color: #6366f1; } &.svc { background: rgba(245,158,11,0.15); color: #d97706; } &.bg { background: var(--md-sys-color-surface-container-high); color: var(--md-sys-color-on-surface-variant); } &.mem { background: var(--md-sys-color-surface-container-high); color: var(--md-sys-color-on-surface-variant); } }
.kill-btn { background: none; border: none; cursor: pointer; color: #ef4444; display: flex; align-items: center; padding: 6px; border-radius: 50%; transition: background 0.15s; &:hover { background: rgba(239,68,68,0.12); } svg { width: 20px; height: 20px; } }
.no-results { text-align: center; color: var(--md-sys-color-on-surface-variant); padding: 20px; }
.error-box { display: flex; align-items: center; gap: 10px; padding: 14px; border-radius: 14px; font-size: 0.9rem; background: rgba(239,68,68,0.08); color: #ef4444; svg { width: 18px; height: 18px; } }
.success-box { display: flex; align-items: center; gap: 10px; padding: 14px; border-radius: 14px; font-size: 0.9rem; background: rgba(34,197,94,0.08); color: #16a34a; svg { width: 18px; height: 18px; } }
</style>
