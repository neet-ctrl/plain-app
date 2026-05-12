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

    <!-- Summary row -->
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

    <!-- ═══════════════ PROTECTED / ADB PERMISSIONS ═══════════════ -->
    <section class="protected-section">
      <div class="section-hdr">
        <i-lucide:terminal class="sec-icon" />
        <div class="sec-text">
          <h3 class="sec-title">Protected Permissions (ADB Required)</h3>
          <p class="sec-sub">
            These system-level permissions cannot be granted through the normal install flow.
            Connect your phone via USB with <strong>USB Debugging</strong> enabled, then run the commands below.
            Permissions granted via ADB persist until the app is uninstalled.
          </p>
        </div>
        <div class="prot-badges">
          <span class="badge-ok">✅ {{ protGranted }} granted</span>
          <span class="badge-err" v-if="protMissing > 0">❌ {{ protMissing }} missing</span>
        </div>
      </div>

      <!-- ADB setup reminder -->
      <div class="adb-setup">
        <span class="setup-step">① Enable <em>Developer Options</em></span>
        <span class="setup-step">② Enable <em>USB Debugging</em></span>
        <span class="setup-step">③ Connect USB cable</span>
        <span class="setup-step">④ Run: <code>adb devices</code></span>
        <span class="setup-step">⑤ Paste commands below</span>
      </div>

      <!-- Device Owner: Grant All Permissions button -->
      <div v-if="isDeviceOwner" class="do-grant-box">
        <div class="do-grant-left">
          <div class="do-grant-icon"><i-lucide:zap /></div>
          <div>
            <div class="do-grant-title">Device Owner Active — Grant All Permissions in One Tap</div>
            <div class="do-grant-sub">No ADB needed. PlainApp grants every protected permission to itself instantly.</div>
          </div>
        </div>
        <button class="grant-all-btn" @click="grantAllDpm" :disabled="grantingAll">
          <i-lucide:loader-2 v-if="grantingAll" class="spin" />
          <i-lucide:key v-else />
          {{ grantingAll ? 'Granting…' : 'Grant All Permissions' }}
        </button>
      </div>
      <div v-if="doGrantResult" class="do-result-row" :class="{ ok: doGrantResult.success }">
        <i-lucide:check-circle v-if="doGrantResult.success" />
        <i-lucide:alert-triangle v-else />
        {{ doGrantResult.success ? `All permissions granted! (${doGrantResult.granted.length} granted)` : `Partial: ${doGrantResult.granted.length} granted, ${doGrantResult.failed.length} failed` }}
      </div>

      <!-- "Grant All Missing" block (ADB fallback) -->
      <div v-if="protMissing > 0" class="grant-all-box">
        <div class="grant-all-hdr">
          <i-lucide:terminal class="ga-icon" />
          <span>Grant All Missing — copy &amp; paste into your terminal</span>
          <button class="copy-btn" @click="copyAll">{{ copied === '__all__' ? '✅ Copied!' : '📋 Copy All' }}</button>
        </div>
        <pre class="cmd-block">{{ allMissingCommands }}</pre>
      </div>

      <!-- All granted celebration -->
      <div v-else class="all-granted-box">
        <i-lucide:party-popper class="party-icon" />
        <span>All protected permissions are granted!</span>
      </div>

      <!-- Individual protected permission cards -->
      <div class="prot-loading" v-if="loadingProtected">
        <i-lucide:loader-2 class="spin" /> Loading…
      </div>
      <div class="prot-grid" v-else>
        <div
          v-for="p in protectedItems"
          :key="p.name"
          class="prot-card"
          :class="{ ok: p.granted, bad: !p.granted }"
        >
          <div class="prot-card-top">
            <div class="prot-icon" :class="{ ok: p.granted }">
              <i-lucide:check v-if="p.granted" />
              <i-lucide:lock v-else />
            </div>
            <div class="prot-info">
              <div class="prot-label">{{ p.label }}</div>
              <div class="prot-name">{{ p.name }}</div>
            </div>
            <div class="prot-badge" :class="{ ok: p.granted }">
              {{ p.granted ? '✅ Granted' : '❌ Missing' }}
            </div>
          </div>

          <p class="prot-desc">{{ p.description }}</p>

          <div class="prot-features">
            <span v-for="f in p.features" :key="f" class="feature-chip">{{ f }}</span>
          </div>

          <div v-if="p.settingsPath" class="settings-path">
            <i-lucide:settings class="sp-icon" />
            <span>Also via: <em>{{ p.settingsPath }}</em></span>
          </div>

          <div class="cmd-row">
            <code class="cmd-text">{{ p.adbCommand }}</code>
            <button
              class="copy-btn small"
              @click="copyCmd(p.name, p.adbCommand)"
            >{{ copied === p.name ? '✅' : '📋' }}</button>
          </div>
        </div>
      </div>
    </section>

    <!-- ═══════════════ STANDARD RUNTIME PERMISSIONS ═══════════════ -->
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
import { allPermissionsStatusGQL, protectedPermissionsStatusGQL, deviceOwnerStatusGQL } from '@/lib/api/query'
import { grantAllPermissionsToSelfGQL } from '@/lib/api/mutation'
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
interface ProtectedItem {
  name: string; label: string; description: string; features: string[]
  adbCommand: string; grantType: string; granted: boolean; settingsPath: string
}

interface GrantResult { granted: string[]; failed: string[]; skipped: string[]; success: boolean }

const items = ref<Item[]>([])
const protectedItems = ref<ProtectedItem[]>([])
const loading = ref(false)
const loadingProtected = ref(false)
const filter = ref<'all' | 'granted' | 'denied'>('all')
const copied = ref('')
const isDeviceOwner = ref(false)
const grantingAll = ref(false)
const doGrantResult = ref<GrantResult | null>(null)

async function load(_manual = false) {
  loading.value = true
  loadingProtected.value = true
  try {
    const [r1, r2, r3] = await Promise.all([
      gqlFetch<{ allPermissionsStatus: Item[] }>(allPermissionsStatusGQL),
      gqlFetch<{ protectedPermissionsStatus: ProtectedItem[] }>(protectedPermissionsStatusGQL),
      gqlFetch<{ deviceOwnerStatus: { isDeviceOwner: boolean } }>(deviceOwnerStatusGQL),
    ])
    items.value = r1?.data?.allPermissionsStatus || []
    protectedItems.value = r2?.data?.protectedPermissionsStatus || []
    isDeviceOwner.value = r3?.data?.deviceOwnerStatus?.isDeviceOwner ?? false
  } finally {
    loading.value = false
    loadingProtected.value = false
  }
}
onMounted(() => load())

async function grantAllDpm() {
  grantingAll.value = true
  doGrantResult.value = null
  try {
    const r = await gqlFetch<{ grantAllPermissionsToSelf: GrantResult }>(grantAllPermissionsToSelfGQL)
    doGrantResult.value = r?.data?.grantAllPermissionsToSelf ?? null
    await load()
  } finally {
    grantingAll.value = false
  }
}

async function copyCmd(name: string, cmd: string) {
  try {
    await navigator.clipboard.writeText(cmd)
  } catch {
    const el = document.createElement('textarea')
    el.value = cmd; document.body.appendChild(el); el.select()
    document.execCommand('copy'); document.body.removeChild(el)
  }
  copied.value = name
  setTimeout(() => { if (copied.value === name) copied.value = '' }, 2000)
}

async function copyAll() {
  const text = allMissingCommands.value
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    const el = document.createElement('textarea')
    el.value = text; document.body.appendChild(el); el.select()
    document.execCommand('copy'); document.body.removeChild(el)
  }
  copied.value = '__all__'
  setTimeout(() => { if (copied.value === '__all__') copied.value = '' }, 2000)
}

const allMissingCommands = computed(() =>
  protectedItems.value.filter(p => !p.granted).map(p => p.adbCommand).join('\n')
)

const protGranted = computed(() => protectedItems.value.filter(p => p.granted).length)
const protMissing = computed(() => protectedItems.value.filter(p => !p.granted).length)

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
    messaging: IconMessage, contacts: IconUsers, phone: IconPhone,
    location: IconMap, media_storage: IconImage, connectivity: IconBluetooth,
    audio: IconMic, notifications: IconBell, system: IconSettings, other: IconBox,
  }
  const c = map[cat] || IconShield
  return () => h(c)
}
</script>

<style scoped>
.perm-root { display: flex; flex-direction: column; gap: 20px; padding: 18px; max-width: 1200px; margin: 0 auto; }
.perm-header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; }
.title-block { display: flex; align-items: center; gap: 14px; }
.hdr-icon { width: 36px; height: 36px; color: var(--md-sys-color-primary); }
.hdr-title { margin: 0; font-size: 1.4rem; font-weight: 600; }
.hdr-sub { margin: 2px 0 0; color: var(--md-sys-color-on-surface-variant); font-size: 0.9rem; }
.hdr-actions { display: flex; gap: 8px; }

.ghost-btn {
  display: inline-flex; align-items: center; gap: 6px; padding: 8px 14px; border-radius: 999px;
  background: var(--md-sys-color-surface-container); border: 1px solid var(--md-sys-color-outline-variant);
  color: inherit; cursor: pointer; font-weight: 500; font-size: 0.85rem;
}
.ghost-btn:hover { background: var(--md-sys-color-surface-container-high); }
.ghost-btn svg { width: 14px; height: 14px; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* Summary */
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
.progress-bar { height: 10px; border-radius: 999px; overflow: hidden; background: var(--md-sys-color-surface-container-highest); }
.progress-fill { height: 100%; border-radius: 999px; background: linear-gradient(90deg, #16a34a, #22d3ee); transition: width 0.4s ease; }
.pct-lbl { font-size: 0.8rem; font-weight: 600; color: var(--md-sys-color-on-surface-variant); }

/* ── Protected section ── */
.protected-section {
  display: flex; flex-direction: column; gap: 16px; padding: 20px; border-radius: 18px;
  background: var(--md-sys-color-surface-container);
  border: 2px solid #f59e0b40;
}
.section-hdr { display: flex; align-items: flex-start; gap: 14px; flex-wrap: wrap; }
.sec-icon { width: 28px; height: 28px; color: #f59e0b; flex-shrink: 0; margin-top: 2px; }
.sec-text { flex: 1; min-width: 200px; }
.sec-title { margin: 0 0 5px; font-size: 1.1rem; font-weight: 700; }
.sec-sub { margin: 0; font-size: 0.85rem; color: var(--md-sys-color-on-surface-variant); line-height: 1.55; }
.prot-badges { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.badge-ok { padding: 4px 12px; border-radius: 999px; font-size: 0.78rem; font-weight: 600; background: rgba(22,163,74,0.12); color: #16a34a; }
.badge-err { padding: 4px 12px; border-radius: 999px; font-size: 0.78rem; font-weight: 600; background: rgba(220,38,38,0.12); color: #dc2626; }

/* ADB setup steps */
.adb-setup {
  display: flex; flex-wrap: wrap; gap: 8px; align-items: center;
  padding: 10px 14px; border-radius: 10px;
  background: var(--md-sys-color-surface); border: 1px solid var(--md-sys-color-outline-variant);
}
.setup-step { font-size: 0.8rem; color: var(--md-sys-color-on-surface-variant); }
.setup-step em { font-style: normal; font-weight: 600; color: var(--md-sys-color-on-surface); }
.setup-step code { background: var(--md-sys-color-surface-container-highest); padding: 1px 5px; border-radius: 4px; font-size: 0.78rem; font-family: ui-monospace,monospace; }
.setup-step:not(:last-child)::after { content: '›'; margin-left: 8px; color: var(--md-sys-color-outline); }

/* Grant All box */
.grant-all-box { border-radius: 12px; overflow: hidden; border: 1px solid #f59e0b50; }
.grant-all-hdr {
  display: flex; align-items: center; gap: 8px; padding: 10px 14px;
  background: #f59e0b18; font-size: 0.85rem; font-weight: 600; color: #92400e;
}
.ga-icon { width: 16px; height: 16px; flex-shrink: 0; }
.cmd-block {
  margin: 0; padding: 14px 16px;
  font-family: ui-monospace,'Cascadia Code',monospace; font-size: 0.82rem;
  white-space: pre; overflow-x: auto;
  background: #0f172a; color: #7dd3fc;
  line-height: 1.8; border-top: 1px solid #1e293b;
}

/* All granted celebration */
.all-granted-box {
  display: flex; align-items: center; gap: 10px; padding: 14px 18px; border-radius: 12px;
  background: rgba(22,163,74,0.08); border: 1px solid rgba(22,163,74,0.25);
  color: #16a34a; font-weight: 600; font-size: 0.95rem;
}
.party-icon { width: 22px; height: 22px; }

/* Copy button */
.copy-btn {
  margin-left: auto; padding: 5px 14px; border-radius: 999px; border: none; cursor: pointer;
  background: var(--md-sys-color-primary); color: var(--md-sys-color-on-primary);
  font-size: 0.78rem; font-weight: 600; white-space: nowrap; flex-shrink: 0;
}
.copy-btn.small { padding: 4px 10px; font-size: 0.72rem; }
.copy-btn:hover { opacity: 0.88; }

/* Protected cards grid */
.prot-loading { display: flex; align-items: center; gap: 8px; padding: 20px; color: var(--md-sys-color-on-surface-variant); font-size: 0.9rem; }
.prot-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 12px; }
.prot-card {
  display: flex; flex-direction: column; gap: 10px; padding: 14px; border-radius: 14px;
  background: var(--md-sys-color-surface); border: 1px solid var(--md-sys-color-outline-variant);
  transition: background 0.2s;
}
.prot-card.ok { border-left: 3px solid #16a34a; }
.prot-card.bad { border-left: 3px solid #f59e0b; }
.prot-card:hover { background: var(--md-sys-color-surface-container-high); }

.prot-card-top { display: flex; align-items: center; gap: 10px; }
.prot-icon {
  width: 34px; height: 34px; border-radius: 9px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  background: rgba(245,158,11,0.12); color: #d97706;
}
.prot-icon.ok { background: rgba(22,163,74,0.12); color: #16a34a; }
.prot-icon svg { width: 18px; height: 18px; }
.prot-info { flex: 1; min-width: 0; }
.prot-label { font-weight: 600; font-size: 0.88rem; }
.prot-name { font-size: 0.68rem; color: var(--md-sys-color-on-surface-variant); font-family: ui-monospace,monospace; margin-top: 2px; }
.prot-badge {
  padding: 3px 9px; border-radius: 999px; font-size: 0.7rem; font-weight: 600;
  background: rgba(245,158,11,0.12); color: #b45309; white-space: nowrap;
}
.prot-badge.ok { background: rgba(22,163,74,0.12); color: #16a34a; }

.prot-desc { margin: 0; font-size: 0.82rem; color: var(--md-sys-color-on-surface-variant); line-height: 1.5; }

.prot-features { display: flex; flex-wrap: wrap; gap: 5px; }
.feature-chip {
  padding: 2px 8px; border-radius: 999px; font-size: 0.7rem; font-weight: 500;
  background: var(--md-sys-color-surface-container-high);
  border: 1px solid var(--md-sys-color-outline-variant);
  color: var(--md-sys-color-on-surface-variant);
}

.settings-path { display: flex; align-items: flex-start; gap: 5px; font-size: 0.75rem; color: var(--md-sys-color-on-surface-variant); }
.sp-icon { width: 12px; height: 12px; flex-shrink: 0; margin-top: 1px; }
.settings-path em { font-style: normal; font-weight: 500; }

.cmd-row {
  display: flex; align-items: center; gap: 8px; padding: 8px 10px; border-radius: 8px;
  background: #0f172a; border: 1px solid #1e3a5f;
}
.cmd-text {
  flex: 1; font-family: ui-monospace,'Cascadia Code',monospace;
  font-size: 0.72rem; color: #7dd3fc;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}

/* Standard permissions */
.filter-bar { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 4px; }
.chip {
  display: inline-flex; align-items: center; gap: 6px; padding: 6px 14px; border-radius: 999px;
  font-size: 0.85rem; font-weight: 500;
  background: var(--md-sys-color-surface-container); border: 1px solid var(--md-sys-color-outline-variant);
  color: inherit; cursor: pointer;
}
.chip.active { background: var(--md-sys-color-primary); color: var(--md-sys-color-on-primary); border-color: transparent; }
.chip .badge { background: rgba(0,0,0,0.1); padding: 1px 7px; border-radius: 999px; font-size: 0.7rem; font-weight: 600; }
.chip.active .badge { background: rgba(255,255,255,0.25); }

.cat-list { display: flex; flex-direction: column; gap: 18px; }
.cat-block { display: flex; flex-direction: column; gap: 10px; }
.cat-title { display: flex; align-items: center; gap: 8px; padding: 0 4px; font-weight: 600; font-size: 0.95rem; color: var(--md-sys-color-on-surface); }
.cat-title svg { width: 18px; height: 18px; color: var(--md-sys-color-primary); }
.cat-count { margin-left: auto; font-size: 0.75rem; font-weight: 500; color: var(--md-sys-color-on-surface-variant); }

.perm-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 10px; }
.perm-item {
  display: flex; align-items: center; gap: 12px; padding: 12px 14px; border-radius: 14px;
  background: var(--md-sys-color-surface-container); border: 1px solid var(--md-sys-color-outline-variant);
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
.perm-name { font-size: 0.7rem; color: var(--md-sys-color-on-surface-variant); font-family: ui-monospace,monospace; margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.perm-status { font-size: 0.7rem; font-weight: 600; padding: 3px 9px; border-radius: 999px; background: rgba(220,38,38,0.12); color: #dc2626; }
.perm-status.ok { background: rgba(22,163,74,0.12); color: #16a34a; }

.loading { display: flex; justify-content: center; padding: 40px; color: var(--md-sys-color-on-surface-variant); }
.loading svg { width: 28px; height: 28px; }

/* Device Owner grant-all button */
.do-grant-box {
  display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 14px;
  padding: 16px 18px; border-radius: 14px;
  background: linear-gradient(135deg, rgba(22,163,74,0.08), rgba(34,211,238,0.05));
  border: 2px solid rgba(22,163,74,0.3);
}
.do-grant-left { display: flex; align-items: center; gap: 12px; }
.do-grant-icon {
  width: 40px; height: 40px; border-radius: 12px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  background: rgba(22,163,74,0.15); color: #16a34a;
}
.do-grant-icon svg { width: 20px; height: 20px; }
.do-grant-title { font-weight: 700; font-size: 0.9rem; color: #16a34a; }
.do-grant-sub { font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); margin-top: 2px; }
.grant-all-btn {
  display: inline-flex; align-items: center; gap: 7px; padding: 10px 20px; border-radius: 999px;
  border: none; cursor: pointer; font-size: 0.85rem; font-weight: 700;
  background: #16a34a; color: #fff; transition: opacity 0.2s; white-space: nowrap;
}
.grant-all-btn:hover:not(:disabled) { opacity: 0.88; }
.grant-all-btn:disabled { opacity: 0.55; cursor: not-allowed; }
.grant-all-btn svg { width: 15px; height: 15px; }
.do-result-row {
  display: flex; align-items: center; gap: 8px; padding: 10px 14px; border-radius: 10px;
  font-size: 0.82rem; font-weight: 600;
  background: rgba(245,158,11,0.1); border: 1px solid rgba(245,158,11,0.3); color: #92400e;
}
.do-result-row.ok { background: rgba(22,163,74,0.1); border-color: rgba(22,163,74,0.25); color: #16a34a; }
.do-result-row svg { width: 16px; height: 16px; flex-shrink: 0; }
</style>
