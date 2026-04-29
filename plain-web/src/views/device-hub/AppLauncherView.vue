<template>
  <div class="al-root">
    <header class="page-header">
      <router-link to="/device-hub" class="back-btn"><i-lucide:arrow-left /></router-link>
      <div class="title-block">
        <h2 class="title"><i-lucide:rocket /> {{ $t('hub_app_launcher_title') }}</h2>
        <p class="sub">{{ $t('hub_app_launcher_desc') }}</p>
      </div>
      <div class="actions">
        <button class="ghost-btn" @click="loadApps" :disabled="loading">
          <i-lucide:refresh-cw :class="{ spin: loading }" /> {{ $t('refresh') }}
        </button>
      </div>
    </header>

    <div class="toolbar">
      <div class="search-wrap">
        <i-lucide:search class="search-icon" />
        <input v-model="search" type="text" :placeholder="$t('search_apps')" class="search-input" />
        <button v-if="search" class="clear-btn" @click="search = ''"><i-lucide:x /></button>
      </div>
      <div class="filter-row">
        <button class="filter-btn" :class="{ active: filter === 'all' }" @click="filter = 'all'">
          {{ $t('all') }} <span class="count">{{ apps.length }}</span>
        </button>
        <button class="filter-btn" :class="{ active: filter === 'user' }" @click="filter = 'user'">
          {{ $t('user_apps') }} <span class="count">{{ userCount }}</span>
        </button>
        <button class="filter-btn" :class="{ active: filter === 'system' }" @click="filter = 'system'">
          {{ $t('system_apps') }} <span class="count">{{ systemCount }}</span>
        </button>
        <button class="filter-btn" :class="{ active: filter === 'launchable' }" @click="filter = 'launchable'">
          {{ $t('launchable') }} <span class="count">{{ launchableCount }}</span>
        </button>
      </div>
    </div>

    <section v-if="filtered.length > 0" class="grid">
      <div v-for="a in filtered" :key="a.packageName" class="app-card">
        <div class="icon-wrap">
          <img :src="iconUrl(a.packageName)" :alt="a.label" loading="lazy" @error="onIconErr" />
        </div>
        <div class="meta">
          <strong class="name">{{ a.label }}</strong>
          <span class="pkg">{{ a.packageName }}</span>
          <span class="ver" v-if="a.versionName">v{{ a.versionName }}</span>
        </div>
        <div class="row">
          <span v-if="a.isSystem" class="tag system">{{ $t('system') }}</span>
          <button
            class="launch-btn"
            :disabled="!a.launchable || launching === a.packageName"
            @click="onLaunch(a)"
            :title="a.launchable ? $t('launch_on_phone') : $t('not_launchable')"
          >
            <i-lucide:play v-if="launching !== a.packageName" />
            <i-lucide:loader-2 v-else class="spin" />
            {{ a.launchable ? $t('launch') : $t('no_ui') }}
          </button>
        </div>
      </div>
    </section>
    <div v-else-if="!loading" class="empty">
      <i-lucide:rocket />
      <h3>{{ $t('no_apps_found') }}</h3>
    </div>
    <div v-else class="empty">
      <i-lucide:loader-2 class="spin" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useTempStore } from '@/stores/temp'
import toast from '@/components/toaster'
import { useI18n } from 'vue-i18n'
import { gqlFetch } from '@/lib/api/gql-client'
import { launchAppsGQL } from '@/lib/api/query'
import { launchAppGQL } from '@/lib/api/mutation'
import { getFileUrlByPath } from '@/lib/api/file'

const { t } = useI18n()
const { urlTokenKey } = storeToRefs(useTempStore())

interface IApp {
  packageName: string; label: string; versionName: string;
  isSystem: boolean; installedAt: number; updatedAt: number; launchable: boolean
}

const apps = ref<IApp[]>([])
const loading = ref(false)
const search = ref('')
const filter = ref<'all' | 'user' | 'system' | 'launchable'>('user')
const launching = ref<string | null>(null)

async function loadApps() {
  loading.value = true
  try {
    const r = await gqlFetch<{ launchApps: IApp[] }>(launchAppsGQL, { query: '' })
    if (!r.errors) apps.value = r.data.launchApps
  } finally { loading.value = false }
}

const userCount = computed(() => apps.value.filter(a => !a.isSystem).length)
const systemCount = computed(() => apps.value.filter(a => a.isSystem).length)
const launchableCount = computed(() => apps.value.filter(a => a.launchable).length)

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  return apps.value.filter(a => {
    if (filter.value === 'user' && a.isSystem) return false
    if (filter.value === 'system' && !a.isSystem) return false
    if (filter.value === 'launchable' && !a.launchable) return false
    if (q && !a.label.toLowerCase().includes(q) && !a.packageName.toLowerCase().includes(q)) return false
    return true
  })
})

function iconUrl(pkg: string): string {
  return getFileUrlByPath(urlTokenKey.value, 'applauncher_icon://' + pkg)
}

function onIconErr(ev: Event) {
  (ev.target as HTMLImageElement).style.visibility = 'hidden'
}

async function onLaunch(a: IApp) {
  if (!a.launchable) return
  launching.value = a.packageName
  try {
    const r = await gqlFetch<{ launchApp: boolean }>(launchAppGQL, { packageName: a.packageName })
    if (!r.errors && r.data.launchApp) {
      toast(t('launched_on_phone', { app: a.label }), 'info')
    } else {
      toast(t('launch_failed'), 'error')
    }
  } catch (_) { toast(t('launch_failed'), 'error') }
  finally { launching.value = null }
}

onMounted(() => { loadApps() })
</script>

<style scoped lang="scss">
.al-root { padding: 18px 22px 28px; max-width: 1300px; margin: 0 auto; display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; align-items: center; gap: 14px; }
.back-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 38px; height: 38px; border-radius: 50%;
  background: var(--md-sys-color-surface-container); color: inherit; text-decoration: none;
}
.title-block { flex: 1; }
.title-block .title { margin: 0; font-size: 1.25rem; font-weight: 700; display: flex; align-items: center; gap: 8px; }
.title svg { width: 22px; height: 22px; color: #f59e0b; }
.sub { margin: 4px 0 0; color: var(--md-sys-color-on-surface-variant); font-size: 0.85rem; }
.actions { display: flex; gap: 8px; }

.ghost-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 8px 14px; border-radius: 12px;
  background: var(--md-sys-color-surface-container);
  border: 1px solid var(--md-sys-color-outline-variant);
  color: inherit; font: inherit; cursor: pointer;
}
.ghost-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.ghost-btn:hover { background: var(--md-sys-color-surface-container-high); }
.ghost-btn svg { width: 14px; height: 14px; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.toolbar { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; }
.search-wrap { position: relative; flex: 1; min-width: 240px; }
.search-icon { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); width: 16px; height: 16px; color: var(--md-sys-color-on-surface-variant); }
.search-input {
  width: 100%; padding: 10px 36px 10px 36px;
  border-radius: 12px;
  border: 1px solid var(--md-sys-color-outline-variant);
  background: var(--md-sys-color-surface-container);
  color: inherit; font: inherit; outline: none;
}
.search-input:focus { border-color: #f59e0b; }
.clear-btn {
  position: absolute; right: 8px; top: 50%; transform: translateY(-50%);
  background: none; border: none; cursor: pointer; padding: 4px;
  color: var(--md-sys-color-on-surface-variant);
}
.filter-row { display: flex; gap: 6px; flex-wrap: wrap; }
.filter-btn {
  padding: 6px 12px; border-radius: 999px; font-size: 0.78rem; font-weight: 600;
  background: var(--md-sys-color-surface-container);
  border: 1px solid var(--md-sys-color-outline-variant);
  color: inherit; cursor: pointer;
  display: inline-flex; align-items: center; gap: 6px;
}
.filter-btn.active { background: #f59e0b; color: white; border-color: #f59e0b; }
.filter-btn .count { font-weight: 500; opacity: 0.8; }

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}
.app-card {
  display: flex; flex-direction: column; gap: 8px;
  padding: 14px;
  background: var(--md-sys-color-surface-container);
  border-radius: 14px;
  border: 1px solid var(--md-sys-color-outline-variant);
  transition: transform 0.15s, border-color 0.15s;
}
.app-card:hover { transform: translateY(-1px); border-color: #f59e0b; }
.icon-wrap {
  align-self: center; width: 56px; height: 56px;
  border-radius: 14px; overflow: hidden;
  background: var(--md-sys-color-surface-container-high);
  display: flex; align-items: center; justify-content: center;
}
.icon-wrap img { width: 100%; height: 100%; object-fit: contain; }
.meta { text-align: center; display: flex; flex-direction: column; gap: 2px; }
.name { font-size: 0.9rem; font-weight: 600; word-break: break-word; }
.pkg { font-size: 0.7rem; color: var(--md-sys-color-on-surface-variant); word-break: break-all; }
.ver { font-size: 0.7rem; color: var(--md-sys-color-on-surface-variant); }
.row { display: flex; align-items: center; justify-content: space-between; gap: 6px; margin-top: 4px; }
.tag { font-size: 0.65rem; padding: 2px 6px; border-radius: 4px; font-weight: 600; }
.tag.system { background: rgba(99,102,241,0.15); color: #4338ca; }
.launch-btn {
  flex: 1;
  display: inline-flex; align-items: center; justify-content: center; gap: 6px;
  padding: 8px 12px; border-radius: 10px; border: none;
  background: #f59e0b; color: white; cursor: pointer; font: inherit;
  font-size: 0.78rem; font-weight: 600;
  transition: background 0.15s;
}
.launch-btn:hover:not(:disabled) { background: #d97706; }
.launch-btn:disabled { background: var(--md-sys-color-surface-container-high); color: var(--md-sys-color-on-surface-variant); cursor: not-allowed; }
.launch-btn svg { width: 14px; height: 14px; }

.empty {
  text-align: center; padding: 60px 20px;
  color: var(--md-sys-color-on-surface-variant);
  display: flex; flex-direction: column; align-items: center; gap: 8px;
}
.empty svg { width: 36px; height: 36px; opacity: 0.5; }
.empty h3 { margin: 0; font-weight: 500; font-size: 0.95rem; }
</style>
