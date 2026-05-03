<template>
  <div class="ic-root">
    <!-- Header -->
    <div class="ic-header">
      <router-link to="/device-hub" class="ic-back"><i-lucide:arrow-left /></router-link>
      <div class="ic-header-text">
        <div class="ic-title">Intruder Captures</div>
        <div class="ic-sub">{{ total }} capture{{ total !== 1 ? 's' : '' }} · Auto-photos on wrong password/PIN</div>
      </div>
      <button class="ic-hdr-btn" :class="{ active: selMode }" @click="toggleSelMode" :title="selMode ? 'Cancel' : 'Select'">
        <i-lucide:check-square v-if="!selMode" />
        <i-lucide:x v-else />
      </button>
    </div>

    <!-- Trigger filter bar -->
    <div class="ic-filter-bar">
      <button
        v-for="f in filters"
        :key="f.key"
        class="ic-filter-btn"
        :class="{ active: activeFilter === f.key }"
        @click="activeFilter = f.key; loadCaptures()"
      >
        <span :class="'dot dot-' + f.key" />{{ f.label }}
        <span v-if="f.key !== 'all' && countByTrigger[f.key]" class="fbadge">{{ countByTrigger[f.key] }}</span>
      </button>
    </div>

    <!-- Toolbar (shown when selection mode active) -->
    <transition name="slide-up">
      <div v-if="selMode" class="ic-toolbar">
        <label class="sel-all-label">
          <input type="checkbox" :checked="selected.size === filteredCaptures.length && filteredCaptures.length > 0" @change="toggleSelectAll" />
          <span>{{ selected.size }} selected</span>
        </label>
        <div class="toolbar-actions">
          <button class="tb-btn danger" :disabled="selected.size === 0" @click="deleteSelected">
            <i-lucide:trash-2 /> Delete ({{ selected.size }})
          </button>
        </div>
      </div>
    </transition>

    <!-- Action bar (clear all) -->
    <div class="ic-action-bar">
      <button class="ab-btn" @click="loadCaptures" :disabled="loading">
        <i-lucide:refresh-cw :class="{ spinning: loading }" /> Refresh
      </button>
      <div class="spacer" />
      <button class="ab-btn danger" :disabled="total === 0 || clearing" @click="confirmClearAll">
        <i-lucide:trash-2 /> Clear All
      </button>
    </div>

    <!-- Loading skeleton -->
    <div v-if="loading && captures.length === 0" class="ic-grid">
      <div v-for="n in 8" :key="n" class="ic-card skeleton" />
    </div>

    <!-- Empty state -->
    <div v-else-if="filteredCaptures.length === 0" class="ic-empty">
      <i-lucide:shield-check class="empty-ico" />
      <p>No intruder captures yet</p>
      <p class="empty-sub">A front photo is taken automatically whenever someone enters a wrong password, PIN, pattern, or security answer.</p>
    </div>

    <!-- Grid -->
    <div v-else class="ic-grid">
      <div
        v-for="cap in filteredCaptures"
        :key="cap.id"
        class="ic-card"
        :class="{ selected: selected.has(cap.id), selecting: selMode }"
        @click="selMode ? toggleSelect(cap.id) : openPhoto(cap)"
      >
        <!-- Selection checkbox -->
        <div v-if="selMode" class="ic-sel-check" @click.stop="toggleSelect(cap.id)">
          <div class="sel-circle" :class="{ checked: selected.has(cap.id) }">
            <i-lucide:check v-if="selected.has(cap.id)" class="chk-ico" />
          </div>
        </div>

        <!-- Photo or placeholder -->
        <div class="ic-photo-wrap">
          <img
            v-if="cap.hasPhoto && cap.fileId"
            :src="getFileUrl(cap.fileId)"
            class="ic-photo"
            :alt="'Intruder · ' + formatTime(cap.timestamp)"
            loading="lazy"
            @error="(e: Event) => (e.target as HTMLImageElement).style.display='none'"
          />
          <div v-else class="ic-photo-placeholder">
            <i-lucide:camera-off class="ph-ico" />
            <span>No photo</span>
          </div>
          <div class="ic-photo-overlay">
            <span :class="'trig-badge trig-' + cap.trigger">{{ triggerLabel(cap.trigger) }}</span>
          </div>
        </div>

        <!-- Card body -->
        <div class="ic-card-body">
          <div class="ic-ts">{{ formatTime(cap.timestamp) }}</div>
          <div class="ic-detail" :title="cap.triggerDetail">{{ cap.triggerDetail }}</div>

          <!-- Location -->
          <div class="ic-loc-row">
            <template v-if="cap.hasLocation">
              <i-lucide:map-pin class="loc-ico" />
              <a
                :href="`https://maps.google.com/?q=${cap.lat},${cap.lng}`"
                target="_blank"
                rel="noopener"
                class="loc-link"
                @click.stop
              >{{ cap.lat.toFixed(5) }}, {{ cap.lng.toFixed(5) }}</a>
            </template>
            <template v-else>
              <i-lucide:map-pin-off class="loc-ico no-loc" />
              <span class="no-loc-txt">No location</span>
            </template>
          </div>

          <!-- Actions row -->
          <div class="ic-actions">
            <a
              v-if="cap.hasPhoto && cap.fileId"
              :href="getFileUrl(cap.fileId) + '&dl=1'"
              download
              class="ic-act-btn"
              @click.stop
              title="Download photo"
            >
              <i-lucide:download />
            </a>
            <a
              v-if="cap.hasLocation"
              :href="`https://maps.google.com/?q=${cap.lat},${cap.lng}`"
              target="_blank"
              rel="noopener"
              class="ic-act-btn"
              @click.stop
              title="Open in Maps"
            >
              <i-lucide:navigation />
            </a>
            <button class="ic-act-btn danger" @click.stop="deleteSingle(cap)" title="Delete">
              <i-lucide:trash-2 />
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Load more -->
    <div v-if="captures.length < total" class="ic-load-more">
      <button class="load-btn" :disabled="loading" @click="loadMore">
        <i-lucide:chevron-down /> Load more ({{ total - captures.length }} remaining)
      </button>
    </div>

    <!-- Lightbox -->
    <transition name="fade">
      <div v-if="lightbox" class="ic-lightbox" @click.self="lightbox = null">
        <div class="lb-inner">
          <button class="lb-close" @click="lightbox = null"><i-lucide:x /></button>
          <img :src="getFileUrl(lightbox.fileId)" class="lb-img" :alt="lightbox.triggerDetail" />
          <div class="lb-meta">
            <span :class="'trig-badge trig-' + lightbox.trigger">{{ triggerLabel(lightbox.trigger) }}</span>
            <span class="lb-ts">{{ formatTime(lightbox.timestamp) }}</span>
            <span class="lb-detail">{{ lightbox.triggerDetail }}</span>
            <a
              v-if="lightbox.hasLocation"
              :href="`https://maps.google.com/?q=${lightbox.lat},${lightbox.lng}`"
              target="_blank"
              rel="noopener"
              class="lb-maps"
            >
              <i-lucide:map-pin /> {{ lightbox.lat.toFixed(5) }}, {{ lightbox.lng.toFixed(5) }}
            </a>
          </div>
        </div>
      </div>
    </transition>

    <!-- Confirm clear dialog -->
    <transition name="fade">
      <div v-if="showClearConfirm" class="ic-confirm-overlay" @click.self="showClearConfirm = false">
        <div class="ic-confirm-box">
          <div class="confirm-title"><i-lucide:alert-triangle class="warn-ico" /> Clear all captures?</div>
          <p class="confirm-desc">This will permanently delete all {{ total }} intruder captures and their photos from the device. This cannot be undone.</p>
          <div class="confirm-actions">
            <button class="conf-btn cancel" @click="showClearConfirm = false">Cancel</button>
            <button class="conf-btn danger" :disabled="clearing" @click="clearAll">
              {{ clearing ? 'Clearing…' : 'Clear All' }}
            </button>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import { getFileUrl } from '@/lib/api/file'

const PAGE_SIZE = 24

const captures = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const clearing = ref(false)
const selMode = ref(false)
const selected = ref<Set<string>>(new Set())
const lightbox = ref<any>(null)
const showClearConfirm = ref(false)
const activeFilter = ref('all')
let pollTimer: ReturnType<typeof setInterval> | null = null

const filters = [
  { key: 'all', label: 'All' },
  { key: 'per_app_lock', label: 'App Lock' },
  { key: 'app_pin', label: 'App PIN' },
  { key: 'security_qa', label: 'Security Q&A' },
  { key: 'telegram_bot', label: 'Telegram Bot' },
]

const countByTrigger = computed(() => {
  const m: Record<string, number> = {}
  captures.value.forEach((c) => {
    m[c.trigger] = (m[c.trigger] || 0) + 1
  })
  return m
})

const filteredCaptures = computed(() => {
  if (activeFilter.value === 'all') return captures.value
  return captures.value.filter((c) => c.trigger === activeFilter.value)
})

const INTRUDER_CAPTURES_QUERY = `
  query IntruderCaptures($offset: Int!, $limit: Int!) {
    intruderCaptures(offset: $offset, limit: $limit) {
      id timestamp trigger triggerDetail hasPhoto fileId lat lng hasLocation
    }
  }
`

const INTRUDER_COUNT_QUERY = `
  query { intruderCapturesCount }
`

const DELETE_CAPTURES_MUTATION = `
  mutation DeleteIntruderCaptures($ids: [String!]!) {
    deleteIntruderCaptures(ids: $ids)
  }
`

const CLEAR_CAPTURES_MUTATION = `
  mutation { clearIntruderCaptures }
`

async function loadCaptures() {
  loading.value = true
  try {
    const [countRes, listRes] = await Promise.all([
      gqlFetch(INTRUDER_COUNT_QUERY, {}),
      gqlFetch(INTRUDER_CAPTURES_QUERY, { offset: 0, limit: PAGE_SIZE }),
    ])
    if (countRes?.data?.intruderCapturesCount !== undefined) {
      total.value = countRes.data.intruderCapturesCount
    }
    if (listRes?.data?.intruderCaptures) {
      captures.value = listRes.data.intruderCaptures
    }
  } catch (_) {}
  loading.value = false
}

async function loadMore() {
  loading.value = true
  try {
    const res = await gqlFetch(INTRUDER_CAPTURES_QUERY, {
      offset: captures.value.length,
      limit: PAGE_SIZE,
    })
    if (res?.data?.intruderCaptures) {
      captures.value.push(...res.data.intruderCaptures)
    }
  } catch (_) {}
  loading.value = false
}

function toggleSelMode() {
  selMode.value = !selMode.value
  if (!selMode.value) selected.value = new Set()
}

function toggleSelect(id: string) {
  const s = new Set(selected.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selected.value = s
}

function toggleSelectAll() {
  if (selected.value.size === filteredCaptures.value.length) {
    selected.value = new Set()
  } else {
    selected.value = new Set(filteredCaptures.value.map((c) => c.id))
  }
}

async function deleteSelected() {
  if (selected.value.size === 0) return
  const ids = [...selected.value]
  try {
    await gqlFetch(DELETE_CAPTURES_MUTATION, { ids })
    captures.value = captures.value.filter((c) => !selected.value.has(c.id))
    total.value = Math.max(0, total.value - ids.length)
    selected.value = new Set()
    selMode.value = false
  } catch (_) {}
}

async function deleteSingle(cap: any) {
  try {
    await gqlFetch(DELETE_CAPTURES_MUTATION, { ids: [cap.id] })
    captures.value = captures.value.filter((c) => c.id !== cap.id)
    total.value = Math.max(0, total.value - 1)
  } catch (_) {}
}

function confirmClearAll() {
  showClearConfirm.value = true
}

async function clearAll() {
  clearing.value = true
  try {
    await gqlFetch(CLEAR_CAPTURES_MUTATION, {})
    captures.value = []
    total.value = 0
    showClearConfirm.value = false
    selected.value = new Set()
    selMode.value = false
  } catch (_) {}
  clearing.value = false
}

function openPhoto(cap: any) {
  if (cap.hasPhoto && cap.fileId) lightbox.value = cap
}

function triggerLabel(trigger: string): string {
  switch (trigger) {
    case 'per_app_lock': return 'App Lock'
    case 'app_pin': return 'App PIN'
    case 'security_qa': return 'Security Q&A'
    case 'telegram_bot': return 'Telegram Bot'
    default: return trigger
  }
}

function formatTime(ts: number): string {
  return new Date(ts).toLocaleString(undefined, {
    month: 'short', day: 'numeric', year: 'numeric',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}

onMounted(() => {
  loadCaptures()
  pollTimer = setInterval(loadCaptures, 20000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped lang="scss">
.ic-root {
  padding: 18px 22px 40px;
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* Header */
.ic-header {
  display: flex;
  align-items: center;
  gap: 12px;
  background: linear-gradient(135deg, #dc2626 0%, #7c3aed 100%);
  border-radius: 18px;
  padding: 16px 20px;
  color: #fff;
}
.ic-back {
  display: flex; align-items: center; justify-content: center;
  width: 36px; height: 36px; border-radius: 50%;
  background: rgba(255,255,255,0.18); color: #fff;
  text-decoration: none; flex-shrink: 0;
  transition: background 0.15s;
  &:hover { background: rgba(255,255,255,0.32); }
}
.ic-header-text { flex: 1; min-width: 0; }
.ic-title { font-size: 1.15rem; font-weight: 700; }
.ic-sub { font-size: 0.8rem; opacity: 0.85; margin-top: 2px; }
.ic-hdr-btn {
  width: 36px; height: 36px; border-radius: 50%;
  background: rgba(255,255,255,0.18); border: none; color: #fff;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  transition: background 0.15s;
  &:hover { background: rgba(255,255,255,0.32); }
  &.active { background: rgba(255,255,255,0.38); }
}

/* Filter bar */
.ic-filter-bar {
  display: flex; flex-wrap: wrap; gap: 8px;
}
.ic-filter-btn {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 14px; border-radius: 20px; border: 1.5px solid var(--md-sys-color-outline-variant);
  background: var(--md-sys-color-surface-container); color: inherit;
  font-size: 0.83rem; cursor: pointer; transition: all 0.15s;
  &:hover { border-color: #dc2626; }
  &.active { background: #dc2626; color: #fff; border-color: #dc2626; }
}
.dot {
  width: 8px; height: 8px; border-radius: 50%;
  &.dot-all { background: #64748b; }
  &.dot-per_app_lock { background: #7c3aed; }
  &.dot-app_pin { background: #d97706; }
  &.dot-security_qa { background: #0891b2; }
  &.dot-telegram_bot { background: #2563eb; }
}
.fbadge {
  background: rgba(0,0,0,0.12); border-radius: 10px;
  padding: 1px 6px; font-size: 0.72rem; font-weight: 600;
}

/* Toolbar */
.ic-toolbar {
  display: flex; align-items: center; gap: 12px;
  background: var(--md-sys-color-surface-container-high);
  border: 1.5px solid var(--md-sys-color-outline-variant);
  border-radius: 14px; padding: 10px 16px;
}
.sel-all-label {
  display: flex; align-items: center; gap: 8px; cursor: pointer;
  font-size: 0.88rem; font-weight: 600; flex: 1;
}
.toolbar-actions { display: flex; gap: 8px; }
.tb-btn {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 16px; border-radius: 10px; border: none; cursor: pointer;
  font-size: 0.85rem; font-weight: 600; transition: all 0.15s;
  &.danger {
    background: #dc2626; color: #fff;
    &:hover:not(:disabled) { background: #b91c1c; }
    &:disabled { opacity: 0.4; cursor: not-allowed; }
  }
}

/* Action bar */
.ic-action-bar {
  display: flex; align-items: center; gap: 10px;
}
.spacer { flex: 1; }
.ab-btn {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 16px; border-radius: 10px; border: 1.5px solid var(--md-sys-color-outline-variant);
  background: var(--md-sys-color-surface-container); color: inherit;
  font-size: 0.83rem; font-weight: 600; cursor: pointer; transition: all 0.15s;
  &:hover:not(:disabled) { border-color: var(--md-sys-color-primary); }
  &.danger {
    border-color: #dc2626; color: #dc2626;
    &:hover:not(:disabled) { background: #dc2626; color: #fff; }
    &:disabled { opacity: 0.4; cursor: not-allowed; }
  }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
}
.spinning { animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* Grid */
.ic-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(270px, 1fr));
  gap: 14px;
}

/* Card */
.ic-card {
  position: relative;
  border-radius: 16px;
  border: 2px solid transparent;
  background: var(--md-sys-color-surface-container);
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s, border-color 0.15s;
  &:hover { transform: translateY(-2px); box-shadow: 0 8px 24px rgba(0,0,0,0.12); }
  &.selected { border-color: #dc2626 !important; }
  &.selecting { cursor: default; }
  &.skeleton {
    height: 280px;
    background: linear-gradient(90deg, var(--md-sys-color-surface-container) 25%, var(--md-sys-color-surface-container-high) 50%, var(--md-sys-color-surface-container) 75%);
    background-size: 200% 100%;
    animation: shimmer 1.3s infinite;
  }
}
@keyframes shimmer { to { background-position: -200% 0; } }

/* Selection check */
.ic-sel-check {
  position: absolute; top: 10px; left: 10px; z-index: 10;
}
.sel-circle {
  width: 26px; height: 26px; border-radius: 50%;
  border: 2.5px solid #fff; background: rgba(0,0,0,0.3);
  display: flex; align-items: center; justify-content: center;
  transition: all 0.15s;
  &.checked { background: #dc2626; border-color: #dc2626; }
}
.chk-ico { width: 14px; height: 14px; color: #fff; }

/* Photo */
.ic-photo-wrap {
  position: relative; height: 180px; overflow: hidden;
  background: #0f172a;
}
.ic-photo {
  width: 100%; height: 100%; object-fit: cover;
  transition: transform 0.2s;
  .ic-card:hover & { transform: scale(1.04); }
}
.ic-photo-placeholder {
  width: 100%; height: 100%;
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px;
  color: #64748b;
  .ph-ico { width: 36px; height: 36px; }
  span { font-size: 0.78rem; }
}
.ic-photo-overlay {
  position: absolute; bottom: 8px; left: 8px; display: flex; gap: 6px;
}

/* Trigger badges */
.trig-badge {
  display: inline-block; padding: 3px 10px; border-radius: 20px;
  font-size: 0.72rem; font-weight: 700; letter-spacing: 0.02em;
}
.trig-per_app_lock { background: #7c3aed; color: #fff; }
.trig-app_pin { background: #d97706; color: #fff; }
.trig-security_qa { background: #0891b2; color: #fff; }
.trig-telegram_bot { background: #2563eb; color: #fff; }

/* Card body */
.ic-card-body { padding: 12px 14px 14px; display: flex; flex-direction: column; gap: 6px; }
.ic-ts { font-size: 0.78rem; font-weight: 600; color: var(--md-sys-color-on-surface-variant); }
.ic-detail {
  font-size: 0.83rem; line-height: 1.3;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.ic-loc-row {
  display: flex; align-items: center; gap: 5px; font-size: 0.78rem;
  .loc-ico { width: 13px; height: 13px; color: #10b981; flex-shrink: 0; }
  .loc-ico.no-loc { color: #94a3b8; }
  .loc-link { color: #10b981; text-decoration: none; &:hover { text-decoration: underline; } }
  .no-loc-txt { color: #94a3b8; }
}

/* Card actions */
.ic-actions {
  display: flex; gap: 6px; margin-top: 4px;
}
.ic-act-btn {
  display: flex; align-items: center; justify-content: center;
  width: 32px; height: 32px; border-radius: 8px; border: none;
  background: var(--md-sys-color-surface-container-high);
  color: inherit; cursor: pointer; text-decoration: none;
  transition: all 0.15s; font-size: 0.78rem;
  &:hover { background: var(--md-sys-color-surface-variant); }
  &.danger { color: #dc2626; &:hover { background: #dc2626; color: #fff; } }
  svg { width: 15px; height: 15px; }
}

/* Empty state */
.ic-empty {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 10px; padding: 60px 20px; text-align: center; color: var(--md-sys-color-on-surface-variant);
  .empty-ico { width: 52px; height: 52px; color: #10b981; }
  p { margin: 0; font-size: 1rem; font-weight: 600; }
  .empty-sub { font-size: 0.83rem; max-width: 420px; font-weight: 400; line-height: 1.5; }
}

/* Load more */
.ic-load-more { display: flex; justify-content: center; }
.load-btn {
  display: flex; align-items: center; gap: 6px;
  padding: 10px 24px; border-radius: 20px;
  border: 1.5px solid var(--md-sys-color-outline-variant);
  background: var(--md-sys-color-surface-container); color: inherit;
  font-size: 0.85rem; cursor: pointer; transition: all 0.15s;
  &:hover:not(:disabled) { border-color: #dc2626; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
}

/* Lightbox */
.ic-lightbox {
  position: fixed; inset: 0; z-index: 1000;
  background: rgba(0,0,0,0.88); backdrop-filter: blur(6px);
  display: flex; align-items: center; justify-content: center;
  padding: 20px;
}
.lb-inner {
  position: relative; max-width: 860px; width: 100%;
  background: var(--md-sys-color-surface-container);
  border-radius: 20px; overflow: hidden;
}
.lb-close {
  position: absolute; top: 12px; right: 12px; z-index: 10;
  width: 36px; height: 36px; border-radius: 50%;
  background: rgba(0,0,0,0.4); border: none; color: #fff;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
}
.lb-img { width: 100%; max-height: 60vh; object-fit: contain; display: block; background: #000; }
.lb-meta {
  padding: 14px 18px; display: flex; flex-wrap: wrap; align-items: center; gap: 10px;
}
.lb-ts { font-size: 0.82rem; color: var(--md-sys-color-on-surface-variant); }
.lb-detail { font-size: 0.88rem; flex: 1; }
.lb-maps {
  display: flex; align-items: center; gap: 4px;
  color: #10b981; font-size: 0.82rem; text-decoration: none;
  &:hover { text-decoration: underline; }
  svg { width: 13px; height: 13px; }
}

/* Confirm overlay */
.ic-confirm-overlay {
  position: fixed; inset: 0; z-index: 1000;
  background: rgba(0,0,0,0.55); backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center; padding: 20px;
}
.ic-confirm-box {
  background: var(--md-sys-color-surface-container-high);
  border-radius: 20px; padding: 28px 24px; max-width: 400px; width: 100%;
  box-shadow: 0 20px 60px rgba(0,0,0,0.3);
}
.confirm-title {
  display: flex; align-items: center; gap: 8px;
  font-size: 1rem; font-weight: 700; margin-bottom: 12px;
  .warn-ico { width: 20px; height: 20px; color: #d97706; }
}
.confirm-desc { font-size: 0.88rem; color: var(--md-sys-color-on-surface-variant); margin: 0 0 20px; line-height: 1.5; }
.confirm-actions { display: flex; gap: 10px; justify-content: flex-end; }
.conf-btn {
  padding: 10px 20px; border-radius: 10px; border: none;
  font-size: 0.88rem; font-weight: 600; cursor: pointer; transition: all 0.15s;
  &.cancel {
    background: var(--md-sys-color-surface-container); color: inherit;
    border: 1.5px solid var(--md-sys-color-outline-variant);
    &:hover { border-color: var(--md-sys-color-primary); }
  }
  &.danger {
    background: #dc2626; color: #fff;
    &:hover:not(:disabled) { background: #b91c1c; }
    &:disabled { opacity: 0.5; cursor: not-allowed; }
  }
}

/* Transitions */
.slide-up-enter-active, .slide-up-leave-active { transition: all 0.2s ease; }
.slide-up-enter-from, .slide-up-leave-to { opacity: 0; transform: translateY(-10px); }
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
