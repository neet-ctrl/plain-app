<template>
  <div class="perm-root">
    <header class="perm-header">
      <div class="title-block">
        <i-lucide:shield-check class="hdr-icon" />
        <div>
          <h2 class="hdr-title">{{ $t('page_title.permissions_status') }}</h2>
          <p class="hdr-sub">{{ $t('perm_subtitle') }}</p>
        </div>
      </div>
      <div class="hdr-actions">
        <button class="ghost-btn" @click="load(true)" :disabled="loading">
          <i-lucide:refresh-cw :class="{ spin: loading }" />
          {{ $t('refresh') }}
        </button>
      </div>
    </header>

    <section class="summary-card">
      <div class="summary-item granted">
        <div class="big">{{ grantedCount }}</div>
        <div class="lbl">{{ $t('perm_granted') }}</div>
      </div>
      <div class="summary-item denied">
        <div class="big">{{ deniedCount }}</div>
        <div class="lbl">{{ $t('perm_denied') }}</div>
      </div>
      <div class="summary-item total">
        <div class="big">{{ items.length }}</div>
        <div class="lbl">{{ $t('total') }}</div>
      </div>
      <div class="progress-wrap">
        <div class="progress-bar"><div class="progress-fill" :style="{ width: pct + '%' }" /></div>
        <div class="pct-lbl">{{ pct }}%</div>
      </div>
    </section>

    <div class="filter-bar">
      <button v-for="f in filters" :key="f.value" class="chip" :class="{ active: filter === f.value }"
        @click="filter = f.value">
        {{ f.label }}
        <span class="badge">{{ countFor(f.value) }}</span>
      </button>
    </div>

    <div class="cat-list" v-if="!loading">
      <div v-for="(group, cat) in grouped" :key="cat" class="cat-block">
        <div class="cat-title">
          <component :is="catIcon(cat)" />
          <span>{{ catLabel(cat) }}</span>
          <span class="cat-count">{{ group.length }}</span>
        </div>
        <div class="perm-grid">
          <div v-for="p in group" :key="p.name" class="perm-item" :class="{ ok: p.granted, bad: !p.granted }">
            <div class="perm-icon-wrap" :class="{ ok: p.granted }">
              <i-lucide:check v-if="p.granted" />
              <i-lucide:x v-else />
            </div>
            <div class="perm-body">
              <div class="perm-label">{{ p.label }}</div>
              <div class="perm-name">{{ p.name }}</div>
            </div>
            <div class="perm-status" :class="{ ok: p.granted }">
              {{ p.granted ? $t('perm_granted') : $t('perm_denied') }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="loading" class="loading">
      <i-lucide:loader-2 class="spin" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import { allPermissionsStatusGQL } from '@/lib/api/query'
import { useI18n } from 'vue-i18n'
import IconShield from '~icons/lucide/shield'
import IconMessage from '~icons/lucide/message-circle'
import IconUsers from '~icons/lucide/users'
import IconPhone from '~icons/lucide/phone'
import IconMap from '~icons/lucide/map-pin'
import IconImage from '~icons/lucide/image'
import IconBluetooth from '~icons/lucide/bluetooth'
import IconMic from '~icons/lucide/mic'
import IconBell from '~icons/lucide/bell'
import IconSettings from '~icons/lucide/settings'
import IconBox from '~icons/lucide/box'

const { t } = useI18n()

interface Item { name: string; label: string; granted: boolean; enabled: boolean; category: string }

const items = ref<Item[]>([])
const loading = ref(false)
const filter = ref<'all' | 'granted' | 'denied'>('all')

async function load(_manual = false) {
  loading.value = true
  try {
    const r = await gqlFetch<{ allPermissionsStatus: Item[] }>(allPermissionsStatusGQL)
    items.value = r?.data?.allPermissionsStatus || []
  } finally {
    loading.value = false
  }
}
onMounted(() => load())

const filters = computed(() => [
  { value: 'all' as const, label: t('all') },
  { value: 'granted' as const, label: t('perm_granted') },
  { value: 'denied' as const, label: t('perm_denied') },
])

function countFor(v: 'all' | 'granted' | 'denied') {
  if (v === 'all') return items.value.length
  if (v === 'granted') return items.value.filter(i => i.granted).length
  return items.value.filter(i => !i.granted).length
}

const filtered = computed(() => {
  if (filter.value === 'all') return items.value
  if (filter.value === 'granted') return items.value.filter(i => i.granted)
  return items.value.filter(i => !i.granted)
})

const grouped = computed(() => {
  const out: Record<string, Item[]> = {}
  for (const it of filtered.value) {
    if (!out[it.category]) out[it.category] = []
    out[it.category].push(it)
  }
  return out
})

const grantedCount = computed(() => items.value.filter(i => i.granted).length)
const deniedCount = computed(() => items.value.filter(i => !i.granted).length)
const pct = computed(() => items.value.length ? Math.round((grantedCount.value / items.value.length) * 100) : 0)

function catLabel(cat: string) {
  const map: Record<string, string> = {
    messaging: t('perm_cat_messaging'),
    contacts: t('perm_cat_contacts'),
    phone: t('perm_cat_phone'),
    location: t('perm_cat_location'),
    media_storage: t('perm_cat_media'),
    connectivity: t('perm_cat_connectivity'),
    audio: t('perm_cat_audio'),
    notifications: t('perm_cat_notifications'),
    system: t('perm_cat_system'),
    other: t('perm_cat_other'),
  }
  return map[cat] || cat
}

function catIcon(cat: string) {
  const map: Record<string, any> = {
    messaging: IconMessage,
    contacts: IconUsers,
    phone: IconPhone,
    location: IconMap,
    media_storage: IconImage,
    connectivity: IconBluetooth,
    audio: IconMic,
    notifications: IconBell,
    system: IconSettings,
    other: IconBox,
  }
  const c = map[cat] || IconShield
  return () => h(c)
}
</script>

<style scoped>
.perm-root { display: flex; flex-direction: column; gap: 16px; padding: 18px; max-width: 1200px; margin: 0 auto; }
.perm-header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; }
.title-block { display: flex; align-items: center; gap: 14px; }
.hdr-icon { width: 36px; height: 36px; color: var(--md-sys-color-primary); }
.hdr-title { margin: 0; font-size: 1.4rem; font-weight: 600; }
.hdr-sub { margin: 2px 0 0; color: var(--md-sys-color-on-surface-variant); font-size: 0.9rem; }
.hdr-actions { display: flex; gap: 8px; }

.ghost-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 8px 14px; border-radius: 999px;
  background: var(--md-sys-color-surface-container); border: 1px solid var(--md-sys-color-outline-variant);
  color: inherit; cursor: pointer; font-weight: 500; font-size: 0.85rem;
}
.ghost-btn:hover { background: var(--md-sys-color-surface-container-high); }
.ghost-btn svg { width: 14px; height: 14px; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.summary-card {
  display: grid; grid-template-columns: repeat(3, auto) 1fr; gap: 24px; align-items: center;
  padding: 20px 24px; border-radius: 18px;
  background: linear-gradient(135deg, var(--md-sys-color-surface-container), var(--md-sys-color-surface));
  border: 1px solid var(--md-sys-color-outline-variant);
}
.summary-item { text-align: center; min-width: 70px; }
.summary-item .big { font-size: 2rem; font-weight: 700; line-height: 1; }
.summary-item .lbl { font-size: 0.75rem; color: var(--md-sys-color-on-surface-variant); margin-top: 4px; }
.summary-item.granted .big { color: #16a34a; }
.summary-item.denied .big { color: #dc2626; }
.progress-wrap { display: flex; flex-direction: column; gap: 6px; min-width: 160px; }
.progress-bar {
  height: 10px; border-radius: 999px; overflow: hidden;
  background: var(--md-sys-color-surface-container-highest);
}
.progress-fill {
  height: 100%; border-radius: 999px;
  background: linear-gradient(90deg, #16a34a, #22d3ee);
  transition: width 0.4s ease;
}
.pct-lbl { font-size: 0.8rem; font-weight: 600; color: var(--md-sys-color-on-surface-variant); }

.filter-bar { display: flex; gap: 8px; flex-wrap: wrap; }
.chip {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 14px; border-radius: 999px; font-size: 0.85rem; font-weight: 500;
  background: var(--md-sys-color-surface-container); border: 1px solid var(--md-sys-color-outline-variant);
  color: inherit; cursor: pointer;
}
.chip.active { background: var(--md-sys-color-primary); color: var(--md-sys-color-on-primary); border-color: transparent; }
.chip .badge {
  background: rgba(0,0,0,0.1); padding: 1px 7px; border-radius: 999px; font-size: 0.7rem; font-weight: 600;
}
.chip.active .badge { background: rgba(255,255,255,0.25); }

.cat-list { display: flex; flex-direction: column; gap: 18px; }
.cat-block { display: flex; flex-direction: column; gap: 10px; }
.cat-title {
  display: flex; align-items: center; gap: 8px; padding: 0 4px;
  font-weight: 600; font-size: 0.95rem; color: var(--md-sys-color-on-surface);
}
.cat-title svg { width: 18px; height: 18px; color: var(--md-sys-color-primary); }
.cat-count {
  margin-left: auto; font-size: 0.75rem; font-weight: 500;
  color: var(--md-sys-color-on-surface-variant);
}

.perm-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 10px; }
.perm-item {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 14px; border-radius: 14px;
  background: var(--md-sys-color-surface-container);
  border: 1px solid var(--md-sys-color-outline-variant);
  transition: all 0.2s;
}
.perm-item:hover { background: var(--md-sys-color-surface-container-high); }
.perm-item.ok { border-left: 3px solid #16a34a; }
.perm-item.bad { border-left: 3px solid #dc2626; }
.perm-icon-wrap {
  width: 32px; height: 32px; border-radius: 8px;
  display: inline-flex; align-items: center; justify-content: center;
  background: rgba(220,38,38,0.12); color: #dc2626; flex-shrink: 0;
}
.perm-icon-wrap.ok { background: rgba(22,163,74,0.12); color: #16a34a; }
.perm-icon-wrap svg { width: 18px; height: 18px; }
.perm-body { flex: 1; min-width: 0; }
.perm-label { font-weight: 500; font-size: 0.9rem; }
.perm-name {
  font-size: 0.7rem; color: var(--md-sys-color-on-surface-variant);
  font-family: ui-monospace, monospace; margin-top: 2px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.perm-status {
  font-size: 0.7rem; font-weight: 600;
  padding: 3px 9px; border-radius: 999px;
  background: rgba(220,38,38,0.12); color: #dc2626;
}
.perm-status.ok { background: rgba(22,163,74,0.12); color: #16a34a; }

.loading { display: flex; justify-content: center; padding: 40px; color: var(--md-sys-color-on-surface-variant); }
.loading svg { width: 28px; height: 28px; }
</style>
