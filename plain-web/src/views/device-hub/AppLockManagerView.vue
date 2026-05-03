<template>
  <div class="alm-root">
    <!-- ── Header ── -->
    <div class="alm-header">
      <router-link to="/device-hub" class="alm-back"><i-lucide:arrow-left /></router-link>
      <div class="alm-header-text">
        <div class="alm-title">Per-App Lock Manager</div>
        <div class="alm-sub">{{ installedApps.length }} apps · {{ lockedApps.length }} locked</div>
      </div>
      <button v-if="!selectionMode" class="alm-hdr-btn" @click="enterSelection" title="Select apps to lock">
        <i-lucide:mouse-pointer-click />
      </button>
      <button v-else class="alm-hdr-btn cancel" @click="exitSelection" title="Cancel">
        <i-lucide:x />
      </button>
    </div>

    <!-- ── Tab bar ── -->
    <div class="alm-tabs">
      <button class="alm-tab" :class="{ active: tab === 'apps' }" @click="tab = 'apps'">
        <i-lucide:smartphone class="tab-icon" />All Apps
      </button>
      <button class="alm-tab" :class="{ active: tab === 'locked' }" @click="tab = 'locked'">
        <i-lucide:lock class="tab-icon" />
        Locked
        <span v-if="lockedApps.length" class="tab-badge">{{ lockedApps.length }}</span>
      </button>
    </div>

    <!-- ════════════════ ALL APPS TAB ════════════════ -->
    <div v-if="tab === 'apps'" class="alm-body">
      <!-- Search -->
      <div class="alm-search-row">
        <div class="alm-search">
          <i-lucide:search class="search-ico" />
          <input v-model="searchQ" class="search-inp" placeholder="Search apps…" />
          <button v-if="searchQ" class="search-clear" @click="searchQ = ''"><i-lucide:x /></button>
        </div>
        <div class="alm-hint" v-if="!selectionMode">Hold any app to select</div>
        <div class="alm-hint sel" v-else>{{ selected.size }} selected</div>
      </div>

      <!-- Loading skeleton -->
      <div v-if="appsLoading" class="app-grid">
        <div v-for="n in 12" :key="n" class="app-card skeleton" />
      </div>

      <!-- App Grid -->
      <div v-else class="app-grid">
        <div
          v-for="app in filteredApps"
          :key="app.packageName"
          class="app-card"
          :class="{
            selected: selected.has(app.packageName),
            locked: isAppLocked(app.packageName),
            selecting: selectionMode,
          }"
          @mousedown="onPressStart(app.packageName)"
          @mouseup="onPressEnd(app.packageName)"
          @mouseleave="onPressCancel"
          @touchstart.prevent="onPressStart(app.packageName)"
          @touchend.prevent="onPressEnd(app.packageName)"
          @touchcancel="onPressCancel"
          @click="onCardClick(app.packageName)"
        >
          <!-- Selection checkbox -->
          <div v-if="selectionMode" class="card-checkbox" :class="{ checked: selected.has(app.packageName) }">
            <i-lucide:check v-if="selected.has(app.packageName)" class="chk-ico" />
          </div>
          <!-- Lock badge -->
          <div v-if="isAppLocked(app.packageName)" class="card-locked-badge">
            <i-lucide:lock class="lock-badge-ico" />
          </div>
          <!-- Avatar -->
          <div class="app-avatar" :style="{ background: avatarGradient(app.packageName) }">
            <span class="avatar-letter">{{ (app.label || app.packageName).charAt(0).toUpperCase() }}</span>
          </div>
          <div class="app-card-name">{{ app.label || app.packageName }}</div>
          <div class="app-card-pkg">{{ shortPkg(app.packageName) }}</div>
        </div>
      </div>

      <p v-if="!appsLoading && filteredApps.length === 0" class="empty-state">
        No apps match "{{ searchQ }}"
      </p>
    </div>

    <!-- ════════════════ LOCKED APPS TAB ════════════════ -->
    <div v-if="tab === 'locked'" class="alm-body">
      <p v-if="!lockedApps.length" class="empty-state-big">
        <i-lucide:shield-off class="empty-ico" />
        No apps are locked yet.<br />
        <span class="empty-sub">Go to "All Apps", hold any app and select "Lock".</span>
      </p>

      <div v-else class="locked-list">
        <div v-for="app in enrichedLockedApps" :key="app.packageName" class="locked-card">
          <!-- Card header -->
          <div class="lc-header">
            <div class="lc-avatar" :style="{ background: avatarGradient(app.packageName) }">
              <span class="avatar-letter sm">{{ (appLabelMap[app.packageName] || app.packageName).charAt(0).toUpperCase() }}</span>
            </div>
            <div class="lc-info">
              <div class="lc-name">{{ appLabelMap[app.packageName] || app.packageName }}</div>
              <div class="lc-pkg">{{ app.packageName }}</div>
            </div>
            <div class="lc-badges">
              <span class="lc-badge" :class="app.lockType">{{ app.lockType.toUpperCase() }}</span>
              <span v-if="sessionMap[app.packageName]?.unlocked" class="lc-badge session">
                <i-lucide:unlock class="sbadge-ico" />
                {{ formatSecs(sessionMap[app.packageName].secondsRemaining) }}
              </span>
            </div>
          </div>

          <!-- Credential display -->
          <div class="lc-cred-section">
            <div class="lc-cred-label">
              <i-lucide:shield class="cred-ico" />
              {{ app.lockType === 'pattern' ? 'Pattern sequence' : 'PIN' }}
            </div>

            <!-- Pattern visual -->
            <div v-if="app.lockType === 'pattern'" class="cred-pattern-wrap">
              <div class="mini-grid">
                <div
                  v-for="n in 9"
                  :key="n"
                  class="mini-dot"
                  :class="{ active: patternDots(app.credential).includes(n), first: patternDots(app.credential)[0] === n }"
                >
                  <span class="mini-num">{{ n }}</span>
                  <span v-if="patternDots(app.credential).includes(n)" class="mini-order">
                    {{ patternDots(app.credential).indexOf(n) + 1 }}
                  </span>
                </div>
              </div>
              <div class="cred-seq">Sequence: <span class="mono">{{ app.credential.split('').join(' → ') }}</span></div>
            </div>

            <!-- PIN display -->
            <div v-else class="cred-pin-wrap">
              <div class="pin-dots-row">
                <div v-for="(d, i) in app.credential.split('')" :key="i" class="pin-digit-box">{{ d }}</div>
              </div>
            </div>
          </div>

          <!-- Stats row -->
          <div class="lc-stats">
            <div class="stat-chip">
              <i-lucide:activity class="stat-ico" />
              {{ app.totalAttempts }} total
            </div>
            <div class="stat-chip warn" v-if="app.wrongAttempts > 0">
              <i-lucide:alert-triangle class="stat-ico" />
              {{ app.wrongAttempts }} wrong
            </div>
            <div class="stat-chip ok" v-if="app.totalAttempts - app.wrongAttempts > 0">
              <i-lucide:check-circle class="stat-ico" />
              {{ app.totalAttempts - app.wrongAttempts }} ok
            </div>
          </div>

          <!-- Action row -->
          <div class="lc-actions">
            <button
              class="lc-btn unlock"
              :class="{ active: sessionMap[app.packageName]?.unlocked }"
              @click="openUnlock(app)"
              :title="sessionMap[app.packageName]?.unlocked ? 'Extend session' : 'Unlock for 10 min'"
            >
              <i-lucide:unlock-keyhole class="btn-ico" />
              {{ sessionMap[app.packageName]?.unlocked ? 'Extend' : 'Unlock 10m' }}
            </button>
            <button class="lc-btn log" @click="openAttempts(app)">
              <i-lucide:list class="btn-ico" />
              Log
            </button>
            <button class="lc-btn danger" @click="removeLock(app.packageName)">
              <i-lucide:trash-2 class="btn-ico" />
              Remove
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- ── Floating selection bar ── -->
    <Transition name="slide-up">
      <div v-if="selectionMode && selected.size > 0" class="float-bar">
        <div class="float-info">
          <i-lucide:check-square class="float-ico" />
          {{ selected.size }} app{{ selected.size > 1 ? 's' : '' }} selected
        </div>
        <button class="float-lock-btn" @click="openBulkLock">
          <i-lucide:lock class="btn-ico" />
          Lock Selected
        </button>
      </div>
    </Transition>

    <!-- ════ BULK LOCK SETUP MODAL ════ -->
    <Transition name="fade">
      <div v-if="bulkLock.open" class="modal-bg" @click.self="bulkLock.open = false">
        <div class="modal-box">
          <div class="modal-head">
            <i-lucide:shield-plus class="modal-head-ico" />
            <div>
              <div class="modal-title">Lock {{ selected.size }} App{{ selected.size > 1 ? 's' : '' }}</div>
              <div class="modal-sub">Choose one lock method applied to all selected apps</div>
            </div>
          </div>

          <!-- Selected apps preview -->
          <div class="selected-chips">
            <span v-for="pkg in Array.from(selected)" :key="pkg" class="sel-chip">
              <span class="sel-chip-dot" :style="{ background: avatarGradient(pkg) }">
                {{ (appLabelMap[pkg] || pkg).charAt(0).toUpperCase() }}
              </span>
              {{ appLabelMap[pkg] || shortPkg(pkg) }}
            </span>
          </div>

          <!-- Lock type -->
          <div class="modal-section">
            <div class="modal-label">Lock Type</div>
            <div class="type-toggle">
              <button class="type-btn" :class="{ active: bulkLock.lockType === 'pin' }" @click="bulkLock.lockType = 'pin'">
                <i-lucide:hash class="type-ico" /> PIN
              </button>
              <button class="type-btn" :class="{ active: bulkLock.lockType === 'pattern' }" @click="bulkLock.lockType = 'pattern'">
                <i-lucide:circle-dot class="type-ico" /> Pattern
              </button>
            </div>
          </div>

          <!-- PIN entry -->
          <div v-if="bulkLock.lockType === 'pin'" class="modal-section">
            <div class="modal-label">PIN <span class="modal-label-hint">(min 4 digits)</span></div>
            <div class="pin-entry-row">
              <input
                v-model="bulkLock.credential"
                :type="bulkLock.showPin ? 'text' : 'password'"
                inputmode="numeric"
                class="modal-input"
                placeholder="Enter PIN…"
                maxlength="12"
              />
              <button class="eye-btn" @click="bulkLock.showPin = !bulkLock.showPin">
                <i-lucide:eye v-if="!bulkLock.showPin" /><i-lucide:eye-off v-else />
              </button>
            </div>
          </div>

          <!-- Pattern entry -->
          <div v-if="bulkLock.lockType === 'pattern'" class="modal-section">
            <div class="modal-label">Draw Pattern <span class="modal-label-hint">(min 4 dots)</span></div>
            <div class="pattern-grid-wrap">
              <div class="pattern-grid">
                <button
                  v-for="n in 9" :key="n"
                  class="pattern-dot"
                  :class="{
                    selected: bulkLock.patternSeq.includes(n),
                    last: bulkLock.patternSeq[bulkLock.patternSeq.length - 1] === n
                  }"
                  @click="toggleBulkPattern(n)"
                  type="button"
                >
                  <span class="dot-label">{{ n }}</span>
                  <span v-if="bulkLock.patternSeq.includes(n)" class="dot-order">
                    {{ bulkLock.patternSeq.indexOf(n) + 1 }}
                  </span>
                </button>
              </div>
              <div class="pattern-preview-row">
                <span class="mono">{{ bulkLock.patternSeq.length ? bulkLock.patternSeq.join(' → ') : '–' }}</span>
                <button v-if="bulkLock.patternSeq.length" class="link-btn" @click="bulkLock.patternSeq = []">Clear</button>
              </div>
            </div>
          </div>

          <div class="modal-actions">
            <button class="modal-confirm-btn" :disabled="bulkLock.saving" @click="confirmBulkLock">
              <i-lucide:shield-check class="btn-ico" v-if="!bulkLock.saving" />
              <span>{{ bulkLock.saving ? `Locking ${bulkLock.progress}/${selected.size}…` : `Apply Lock` }}</span>
            </button>
            <button class="modal-cancel-btn" @click="bulkLock.open = false">Cancel</button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- ════ UNLOCK SESSION MODAL ════ -->
    <Transition name="fade">
      <div v-if="unlockDialog.open" class="modal-bg" @click.self="unlockDialog.open = false">
        <div class="modal-box">
          <div class="modal-head">
            <i-lucide:unlock-keyhole class="modal-head-ico green" />
            <div>
              <div class="modal-title">Unlock for 10 Minutes</div>
              <div class="modal-sub mono">{{ unlockDialog.packageName }}</div>
            </div>
          </div>
          <div class="modal-section">
            <div class="modal-label">Credential or master password</div>
            <div class="pin-entry-row">
              <input
                ref="unlockInputRef"
                v-model="unlockDialog.credential"
                :type="unlockDialog.showCred ? 'text' : 'password'"
                class="modal-input"
                placeholder="PIN / pattern sequence / master password"
                @keyup.enter="doUnlock"
              />
              <button class="eye-btn" @click="unlockDialog.showCred = !unlockDialog.showCred">
                <i-lucide:eye v-if="!unlockDialog.showCred" /><i-lucide:eye-off v-else />
              </button>
            </div>
          </div>
          <Transition name="fade">
            <div v-if="unlockDialog.result === true" class="result-banner ok">
              <i-lucide:check-circle class="rb-ico" /> Unlocked for 10 minutes — lock re-engages automatically.
            </div>
            <div v-else-if="unlockDialog.result === false" class="result-banner fail">
              <i-lucide:x-circle class="rb-ico" /> Wrong credential. Try again.
            </div>
          </Transition>
          <div class="modal-actions">
            <button class="modal-confirm-btn green" :disabled="unlockDialog.loading" @click="doUnlock">
              {{ unlockDialog.loading ? 'Verifying…' : 'Unlock' }}
            </button>
            <button class="modal-cancel-btn" @click="unlockDialog.open = false">Close</button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- ════ ATTEMPT LOG MODAL ════ -->
    <Transition name="fade">
      <div v-if="attemptsDialog.open" class="modal-bg" @click.self="attemptsDialog.open = false">
        <div class="modal-box wide">
          <div class="modal-head">
            <i-lucide:list class="modal-head-ico" />
            <div>
              <div class="modal-title">Attempt Log</div>
              <div class="modal-sub mono">{{ attemptsDialog.packageName }}</div>
            </div>
          </div>
          <div class="attempts-toolbar">
            <label class="chk-label">
              <input type="checkbox" v-model="selectAllAttempts" @change="toggleSelectAll" />
              Select all
            </label>
            <button v-if="attemptsDialog.selected.size > 0" class="danger-sm-btn" @click="deleteSelectedAttempts" :disabled="attemptsDialog.deleting">
              Delete ({{ attemptsDialog.selected.size }})
            </button>
            <button class="danger-sm-btn ml-auto" @click="clearAllAttempts" :disabled="attemptsDialog.deleting">Clear all</button>
          </div>
          <div class="attempts-list">
            <p v-if="!attemptsDialog.items.length" class="empty-state">No attempts recorded.</p>
            <div v-for="a in attemptsDialog.items" :key="a.id" class="attempt-row" :class="{ ok: a.success, fail: !a.success }">
              <input type="checkbox" :checked="attemptsDialog.selected.has(a.id)" @change="toggleAttemptSelect(a.id)" />
              <i-lucide:check-circle v-if="a.success" class="att-ico ok" />
              <i-lucide:x-circle v-else class="att-ico fail" />
              <span class="att-ts">{{ formatTs(a.timestamp) }}</span>
              <span class="att-label">{{ a.success ? 'Unlocked' : 'Wrong password' }}</span>
            </div>
          </div>
          <div class="modal-actions">
            <button class="modal-cancel-btn" @click="attemptsDialog.open = false">Close</button>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import emitter from '@/plugins/eventbus'

/* ── GraphQL ── */
const PACKAGES_Q = `query packages($limit:Int!,$offset:Int!,$query:String!,$sortBy:FileSortBy!){packages(limit:$limit,offset:$offset,query:$query,sortBy:$sortBy){id name}}`
const LOCKS_Q = `query perAppLocks{perAppLocks{packageName lockType biometricEnabled totalAttempts wrongAttempts credential}}`
const ATTEMPTS_Q = `query perAppLockAttempts($packageName:String!){perAppLockAttempts(packageName:$packageName){id packageName timestamp success}}`
const SESSIONS_Q = `query perAppLockSessions{perAppLockSessions{packageName unlocked secondsRemaining}}`
const SET_LOCK_M = `mutation setPerAppLock($packageName:String!,$lockType:String!,$credential:String!,$biometricEnabled:Boolean!){setPerAppLock(packageName:$packageName,lockType:$lockType,credential:$credential,biometricEnabled:$biometricEnabled)}`
const REMOVE_LOCK_M = `mutation removePerAppLock($packageName:String!){removePerAppLock(packageName:$packageName)}`
const VERIFY_M = `mutation verifyPerAppLock($packageName:String!,$credential:String!){verifyPerAppLock(packageName:$packageName,credential:$credential)}`
const DEL_ATTEMPTS_M = `mutation deletePerAppLockAttempts($packageName:String!,$ids:[Long!]!){deletePerAppLockAttempts(packageName:$packageName,ids:$ids)}`

/* ── Types ── */
interface AppEntry { id: string; label: string; packageName: string }
interface LockEntry { packageName: string; lockType: string; biometricEnabled: boolean; totalAttempts: number; wrongAttempts: number; credential: string }
interface AttemptEntry { id: number; packageName: string; timestamp: number; success: boolean }
interface SessionEntry { packageName: string; unlocked: boolean; secondsRemaining: number }

/* ── State ── */
const tab = ref<'apps' | 'locked'>('apps')
const searchQ = ref('')
const appsLoading = ref(true)
const installedApps = ref<AppEntry[]>([])
const lockedApps = ref<LockEntry[]>([])
const sessionMap = ref<Record<string, SessionEntry>>({})
const selectionMode = ref(false)
const selected = ref(new Set<string>())
const unlockInputRef = ref<HTMLInputElement | null>(null)
const selectAllAttempts = ref(false)
let sessionTimer: ReturnType<typeof setInterval> | null = null
let longPressTimer: ReturnType<typeof setTimeout> | null = null
const LONG_PRESS_MS = 500

/* ── Computed ── */
const appLabelMap = computed(() => {
  const m: Record<string, string> = {}
  for (const a of installedApps.value) m[a.packageName] = a.label
  return m
})

const filteredApps = computed(() => {
  const q = searchQ.value.toLowerCase().trim()
  if (!q) return installedApps.value
  return installedApps.value.filter(a => a.label.toLowerCase().includes(q) || a.packageName.toLowerCase().includes(q))
})

const lockedPkgSet = computed(() => new Set(lockedApps.value.map(a => a.packageName)))

const enrichedLockedApps = computed(() => lockedApps.value)

/* ── Dialogs ── */
const bulkLock = reactive({ open: false, lockType: 'pin', credential: '', patternSeq: [] as number[], showPin: false, saving: false, progress: 0 })
const unlockDialog = reactive({ open: false, packageName: '', credential: '', showCred: false, loading: false, result: null as boolean | null })
const attemptsDialog = reactive({ open: false, packageName: '', items: [] as AttemptEntry[], selected: new Set<number>(), deleting: false })

/* ── Helpers ── */
const AVATAR_GRADIENTS = [
  'linear-gradient(135deg,#7c3aed,#4f46e5)',
  'linear-gradient(135deg,#2563eb,#0891b2)',
  'linear-gradient(135deg,#059669,#10b981)',
  'linear-gradient(135deg,#d97706,#f59e0b)',
  'linear-gradient(135deg,#dc2626,#e11d48)',
  'linear-gradient(135deg,#c026d3,#7c3aed)',
  'linear-gradient(135deg,#0891b2,#2563eb)',
  'linear-gradient(135deg,#16a34a,#059669)',
]
function avatarGradient(pkg: string) {
  let h = 0
  for (const c of pkg) h = (h * 31 + c.charCodeAt(0)) & 0x7fffffff
  return AVATAR_GRADIENTS[h % AVATAR_GRADIENTS.length]
}
function shortPkg(pkg: string) {
  const parts = pkg.split('.')
  return parts.length > 2 ? parts.slice(-2).join('.') : pkg
}
function isAppLocked(pkg: string) { return lockedPkgSet.value.has(pkg) }
function formatTs(ts: number) { return new Date(ts).toLocaleString() }
function formatSecs(s: number) { return s >= 60 ? `${Math.floor(s / 60)}m ${s % 60}s` : `${s}s` }
function patternDots(seq: string): number[] { return seq.split('').map(Number).filter(n => n >= 1 && n <= 9) }

/* ── Long press ── */
function onPressStart(pkg: string) {
  longPressTimer = setTimeout(() => {
    selectionMode.value = true
    toggleSelect(pkg)
  }, LONG_PRESS_MS)
}
function onPressEnd(pkg: string) {
  if (longPressTimer) { clearTimeout(longPressTimer); longPressTimer = null }
}
function onPressCancel() {
  if (longPressTimer) { clearTimeout(longPressTimer); longPressTimer = null }
}
function onCardClick(pkg: string) {
  if (selectionMode.value) toggleSelect(pkg)
}
function toggleSelect(pkg: string) {
  const s = new Set(selected.value)
  if (s.has(pkg)) s.delete(pkg); else s.add(pkg)
  selected.value = s
}
function enterSelection() { selectionMode.value = true }
function exitSelection() { selectionMode.value = false; selected.value = new Set() }

/* ── Fetch ── */
async function fetchApps() {
  appsLoading.value = true
  try {
    const r = await gqlFetch<{ packages: any[] }>(PACKAGES_Q, { limit: 1000, offset: 0, query: '', sortBy: 'NAME_ASC' })
    if (r.errors?.length) return
    installedApps.value = (r.data.packages || []).map((p: any) => ({ id: p.id, label: p.name || p.id, packageName: p.id }))
  } catch (_) {}
  appsLoading.value = false
}
async function fetchLocks() {
  const r = await gqlFetch<{ perAppLocks: LockEntry[] }>(LOCKS_Q)
  if (!r.errors?.length) lockedApps.value = r.data.perAppLocks
}
async function fetchSessions() {
  try {
    const r = await gqlFetch<{ perAppLockSessions: SessionEntry[] }>(SESSIONS_Q)
    if (r.errors?.length) return
    const m: Record<string, SessionEntry> = {}
    for (const s of r.data.perAppLockSessions) m[s.packageName] = s
    sessionMap.value = m
  } catch (_) {}
}

/* ── Bulk lock ── */
function openBulkLock() {
  bulkLock.open = true
  bulkLock.lockType = 'pin'
  bulkLock.credential = ''
  bulkLock.patternSeq = []
  bulkLock.showPin = false
  bulkLock.saving = false
  bulkLock.progress = 0
}
function toggleBulkPattern(n: number) {
  const idx = bulkLock.patternSeq.indexOf(n)
  if (idx >= 0) bulkLock.patternSeq = bulkLock.patternSeq.slice(0, idx)
  else bulkLock.patternSeq = [...bulkLock.patternSeq, n]
}
async function confirmBulkLock() {
  const cred = bulkLock.lockType === 'pattern' ? bulkLock.patternSeq.join('') : bulkLock.credential
  if (!cred) { emitter.emit('toast', 'Enter a credential'); return }
  if (bulkLock.lockType === 'pin' && (cred.length < 4 || !/^\d+$/.test(cred))) { emitter.emit('toast', 'PIN must be ≥4 digits'); return }
  if (bulkLock.lockType === 'pattern' && bulkLock.patternSeq.length < 4) { emitter.emit('toast', 'Pattern must have ≥4 dots'); return }
  bulkLock.saving = true
  bulkLock.progress = 0
  const pkgs = Array.from(selected.value)
  for (const pkg of pkgs) {
    await gqlFetch(SET_LOCK_M, { packageName: pkg, lockType: bulkLock.lockType, credential: cred, biometricEnabled: false })
    bulkLock.progress++
  }
  bulkLock.saving = false
  bulkLock.open = false
  exitSelection()
  emitter.emit('toast', `Locked ${pkgs.length} app${pkgs.length > 1 ? 's' : ''}`)
  await fetchLocks()
  await fetchSessions()
}

/* ── Unlock session ── */
function openUnlock(app: LockEntry) {
  unlockDialog.open = true
  unlockDialog.packageName = app.packageName
  unlockDialog.credential = ''
  unlockDialog.showCred = false
  unlockDialog.loading = false
  unlockDialog.result = null
  nextTick(() => unlockInputRef.value?.focus())
}
async function doUnlock() {
  if (!unlockDialog.credential.trim()) { emitter.emit('toast', 'Enter credential'); return }
  unlockDialog.loading = true
  unlockDialog.result = null
  try {
    const r = await gqlFetch<{ verifyPerAppLock: boolean }>(VERIFY_M, {
      packageName: unlockDialog.packageName,
      credential: unlockDialog.credential.trim(),
    })
    if (r.errors?.length) { emitter.emit('toast', r.errors[0].message); return }
    const ok = r.data.verifyPerAppLock
    unlockDialog.result = ok
    if (ok) { unlockDialog.credential = ''; await fetchSessions(); await fetchLocks() }
  } catch (e: any) {
    emitter.emit('toast', e?.message ?? 'error')
  } finally {
    unlockDialog.loading = false
  }
}

/* ── Remove ── */
async function removeLock(pkg: string) {
  if (!confirm(`Remove lock for "${appLabelMap.value[pkg] || pkg}"?`)) return
  await gqlFetch(REMOVE_LOCK_M, { packageName: pkg })
  emitter.emit('toast', 'Lock removed')
  await fetchLocks()
  await fetchSessions()
}

/* ── Attempt log ── */
async function openAttempts(app: LockEntry) {
  attemptsDialog.open = true
  attemptsDialog.packageName = app.packageName
  attemptsDialog.selected = new Set()
  selectAllAttempts.value = false
  const r = await gqlFetch<{ perAppLockAttempts: AttemptEntry[] }>(ATTEMPTS_Q, { packageName: app.packageName })
  if (!r.errors?.length) attemptsDialog.items = r.data.perAppLockAttempts
}
function toggleAttemptSelect(id: number) {
  const s = new Set(attemptsDialog.selected)
  if (s.has(id)) s.delete(id); else s.add(id)
  attemptsDialog.selected = s
  selectAllAttempts.value = s.size === attemptsDialog.items.length
}
function toggleSelectAll() {
  attemptsDialog.selected = selectAllAttempts.value
    ? new Set(attemptsDialog.items.map(a => a.id))
    : new Set()
}
async function deleteSelectedAttempts() {
  attemptsDialog.deleting = true
  try {
    const ids = Array.from(attemptsDialog.selected)
    const r = await gqlFetch(DEL_ATTEMPTS_M, { packageName: attemptsDialog.packageName, ids })
    if (r.errors?.length) { emitter.emit('toast', r.errors[0].message); return }
    attemptsDialog.items = attemptsDialog.items.filter(a => !attemptsDialog.selected.has(a.id))
    attemptsDialog.selected = new Set()
    selectAllAttempts.value = false
    await fetchLocks()
  } finally { attemptsDialog.deleting = false }
}
async function clearAllAttempts() {
  if (!confirm('Clear all attempt logs?')) return
  attemptsDialog.deleting = true
  try {
    await gqlFetch(DEL_ATTEMPTS_M, { packageName: attemptsDialog.packageName, ids: [] })
    attemptsDialog.items = []
    attemptsDialog.selected = new Set()
    selectAllAttempts.value = false
    await fetchLocks()
  } finally { attemptsDialog.deleting = false }
}

/* ── Lifecycle ── */
onMounted(async () => {
  await Promise.all([fetchApps(), fetchLocks(), fetchSessions()])
  sessionTimer = setInterval(fetchSessions, 15000)
})
onUnmounted(() => { if (sessionTimer) clearInterval(sessionTimer) })
</script>

<style lang="scss" scoped>
/* ── Root ── */
.alm-root {
  display: flex; flex-direction: column; height: 100%;
  background: var(--md-sys-color-surface); overflow: hidden;
}

/* ── Header ── */
.alm-header {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 16px 12px;
  background: linear-gradient(135deg, #7c3aed 0%, #4f46e5 100%);
  color: #fff; flex-shrink: 0;
}
.alm-back {
  display: flex; align-items: center; justify-content: center;
  width: 36px; height: 36px; border-radius: 50%;
  color: rgba(255,255,255,0.9); text-decoration: none;
  transition: background .15s;
  &:hover { background: rgba(255,255,255,0.15); }
}
.alm-header-text { flex: 1; }
.alm-title { font-size: 1.1rem; font-weight: 700; line-height: 1.2; }
.alm-sub { font-size: 0.75rem; opacity: 0.8; margin-top: 1px; }
.alm-hdr-btn {
  display: flex; align-items: center; justify-content: center;
  width: 36px; height: 36px; border-radius: 50%; border: none; cursor: pointer;
  background: rgba(255,255,255,0.15); color: #fff;
  transition: background .15s;
  &:hover { background: rgba(255,255,255,0.25); }
  &.cancel { background: rgba(255,80,80,0.3); }
}

/* ── Tabs ── */
.alm-tabs {
  display: flex; gap: 0; flex-shrink: 0;
  background: var(--md-sys-color-surface-container);
  border-bottom: 1px solid var(--md-sys-color-outline-variant);
}
.alm-tab {
  flex: 1; display: flex; align-items: center; justify-content: center; gap: 6px;
  padding: 12px 8px; border: none; cursor: pointer;
  background: none; color: var(--md-sys-color-on-surface-variant);
  font-size: 0.87rem; font-weight: 500; position: relative;
  transition: color .15s;
  &.active {
    color: #7c3aed;
    &::after {
      content: ''; position: absolute; bottom: 0; left: 0; right: 0;
      height: 2px; background: #7c3aed; border-radius: 2px 2px 0 0;
    }
  }
}
.tab-icon { width: 15px; height: 15px; }
.tab-badge {
  background: #7c3aed; color: #fff; border-radius: 99px;
  font-size: 0.7rem; padding: 1px 6px; font-weight: 600;
}

/* ── Body ── */
.alm-body { flex: 1; overflow-y: auto; padding: 12px 12px 120px; }

/* ── Search ── */
.alm-search-row { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.alm-search {
  flex: 1; display: flex; align-items: center; gap: 8px;
  background: var(--md-sys-color-surface-container);
  border: 1px solid var(--md-sys-color-outline-variant);
  border-radius: 12px; padding: 8px 12px;
  &:focus-within { border-color: #7c3aed; }
}
.search-ico { width: 16px; height: 16px; color: var(--md-sys-color-on-surface-variant); flex-shrink: 0; }
.search-inp {
  flex: 1; border: none; background: none; outline: none;
  font-size: 0.9rem; color: var(--md-sys-color-on-surface);
}
.search-clear {
  background: none; border: none; cursor: pointer; padding: 0;
  color: var(--md-sys-color-on-surface-variant); display: flex; align-items: center;
}
.alm-hint { font-size: 0.75rem; color: var(--md-sys-color-on-surface-variant); white-space: nowrap; &.sel { color: #7c3aed; font-weight: 600; } }

/* ── App Grid ── */
.app-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(90px, 1fr));
  gap: 10px;
}
.app-card {
  position: relative; display: flex; flex-direction: column; align-items: center;
  padding: 14px 8px 10px;
  background: var(--md-sys-color-surface-container);
  border: 1.5px solid transparent;
  border-radius: 16px; cursor: pointer; user-select: none;
  transition: all .15s;
  &:hover { border-color: var(--md-sys-color-outline-variant); transform: translateY(-1px); }
  &.locked { border-color: rgba(124,58,237,0.3); background: rgba(124,58,237,0.05); }
  &.selected { border-color: #7c3aed; background: rgba(124,58,237,0.1); box-shadow: 0 0 0 2px rgba(124,58,237,0.2); }
  &.selecting { cursor: pointer; }
}
.skeleton {
  height: 110px;
  background: linear-gradient(90deg, var(--md-sys-color-surface-container) 25%, var(--md-sys-color-surface-container-high) 50%, var(--md-sys-color-surface-container) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
}
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }

.card-checkbox {
  position: absolute; top: 7px; left: 7px;
  width: 20px; height: 20px; border-radius: 50%;
  border: 2px solid var(--md-sys-color-outline);
  background: var(--md-sys-color-surface);
  display: flex; align-items: center; justify-content: center;
  transition: all .15s;
  &.checked { border-color: #7c3aed; background: #7c3aed; }
}
.chk-ico { width: 12px; height: 12px; color: #fff; }
.card-locked-badge {
  position: absolute; top: 7px; right: 7px;
  width: 18px; height: 18px; border-radius: 50%;
  background: #7c3aed; display: flex; align-items: center; justify-content: center;
}
.lock-badge-ico { width: 10px; height: 10px; color: #fff; }
.app-avatar {
  width: 52px; height: 52px; border-radius: 14px;
  display: flex; align-items: center; justify-content: center;
  margin-bottom: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}
.avatar-letter { color: #fff; font-size: 1.3rem; font-weight: 700; &.sm { font-size: 1rem; } }
.app-card-name {
  font-size: 0.75rem; font-weight: 600; text-align: center;
  color: var(--md-sys-color-on-surface);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  width: 100%;
}
.app-card-pkg {
  font-size: 0.65rem; color: var(--md-sys-color-on-surface-variant);
  text-align: center; margin-top: 2px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; width: 100%;
}

/* ── Empty states ── */
.empty-state { text-align: center; color: var(--md-sys-color-on-surface-variant); font-size: 0.85rem; padding: 20px 0; }
.empty-state-big {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 60px 20px; text-align: center;
  color: var(--md-sys-color-on-surface-variant); font-size: 0.95rem; line-height: 1.8;
  gap: 12px;
}
.empty-ico { width: 48px; height: 48px; opacity: 0.3; }
.empty-sub { font-size: 0.82rem; opacity: 0.7; }

/* ── Locked list ── */
.locked-list { display: flex; flex-direction: column; gap: 14px; }
.locked-card {
  background: var(--md-sys-color-surface-container);
  border-radius: 18px; padding: 16px;
  border: 1px solid var(--md-sys-color-outline-variant);
}
.lc-header { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.lc-avatar {
  width: 44px; height: 44px; border-radius: 12px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}
.lc-info { flex: 1; min-width: 0; }
.lc-name { font-size: 0.9rem; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lc-pkg { font-size: 0.73rem; color: var(--md-sys-color-on-surface-variant); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lc-badges { display: flex; flex-direction: column; gap: 4px; align-items: flex-end; flex-shrink: 0; }
.lc-badge {
  font-size: 0.68rem; padding: 2px 8px; border-radius: 99px; font-weight: 600;
  display: inline-flex; align-items: center; gap: 3px;
  &.pin { background: #ede9fe; color: #6d28d9; }
  &.pattern { background: #fdf4ff; color: #9333ea; }
  &.session { background: #d1fae5; color: #065f46; }
}
.sbadge-ico { width: 10px; height: 10px; }

/* ── Credential display ── */
.lc-cred-section {
  background: var(--md-sys-color-surface-container-low, rgba(0,0,0,0.03));
  border-radius: 12px; padding: 12px 14px; margin-bottom: 12px;
  border: 1px solid var(--md-sys-color-outline-variant);
}
.lc-cred-label {
  display: flex; align-items: center; gap: 6px; font-size: 0.75rem;
  color: var(--md-sys-color-on-surface-variant); font-weight: 600;
  text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 10px;
}
.cred-ico { width: 13px; height: 13px; }

/* Pattern mini-grid */
.cred-pattern-wrap { display: flex; flex-direction: column; gap: 10px; }
.mini-grid {
  display: grid; grid-template-columns: repeat(3, 36px); gap: 6px;
}
.mini-dot {
  width: 36px; height: 36px; border-radius: 50%;
  background: var(--md-sys-color-surface-container-high);
  border: 2px solid var(--md-sys-color-outline-variant);
  display: flex; align-items: center; justify-content: center;
  position: relative; font-size: 0.7rem; color: var(--md-sys-color-on-surface-variant);
  &.active {
    background: linear-gradient(135deg, #7c3aed, #4f46e5);
    border-color: #7c3aed; color: #fff;
  }
  &.first { background: linear-gradient(135deg, #059669, #10b981); border-color: #059669; }
}
.mini-num { font-weight: 700; font-size: 0.75rem; }
.mini-order {
  position: absolute; top: -6px; right: -6px;
  background: #7c3aed; color: #fff; border-radius: 99px;
  font-size: 0.6rem; font-weight: 700; padding: 0 3px; min-width: 14px; text-align: center;
  .first & { background: #059669; }
}
.cred-seq { font-size: 0.8rem; color: var(--md-sys-color-on-surface-variant); }

/* PIN display */
.cred-pin-wrap {}
.pin-dots-row { display: flex; gap: 6px; flex-wrap: wrap; }
.pin-digit-box {
  width: 36px; height: 42px; border-radius: 10px;
  background: linear-gradient(135deg, #7c3aed, #4f46e5);
  color: #fff; font-size: 1.1rem; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 2px 6px rgba(124,58,237,0.3);
  letter-spacing: 0;
}

/* ── Stats ── */
.lc-stats { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 12px; }
.stat-chip {
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 0.75rem; padding: 3px 9px; border-radius: 99px;
  background: var(--md-sys-color-surface-container-high);
  color: var(--md-sys-color-on-surface-variant);
  &.warn { background: #fff3e0; color: #c2410c; }
  &.ok { background: #d1fae5; color: #065f46; }
}
.stat-ico { width: 12px; height: 12px; }

/* ── Locked card actions ── */
.lc-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.lc-btn {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 7px 14px; border-radius: 10px; border: none; cursor: pointer;
  font-size: 0.8rem; font-weight: 600; transition: all .15s;
  &.unlock {
    background: #ede9fe; color: #6d28d9;
    &.active { background: #d1fae5; color: #065f46; }
    &:hover:not(.active) { background: #ddd6fe; }
    &.active:hover { background: #a7f3d0; }
  }
  &.log { background: var(--md-sys-color-surface-container-high); color: var(--md-sys-color-on-surface-variant); &:hover { background: var(--md-sys-color-outline-variant); } }
  &.danger { background: #fee2e2; color: #b91c1c; &:hover { background: #fecaca; } }
}
.btn-ico { width: 14px; height: 14px; }

/* ── Floating bar ── */
.float-bar {
  position: fixed; bottom: 20px; left: 50%; transform: translateX(-50%);
  display: flex; align-items: center; gap: 16px;
  background: #1e1b4b; color: #fff;
  padding: 14px 20px; border-radius: 20px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.35);
  z-index: 50; min-width: 280px; max-width: 90vw;
}
.float-info { display: flex; align-items: center; gap: 8px; flex: 1; font-size: 0.9rem; font-weight: 600; }
.float-ico { width: 18px; height: 18px; }
.float-lock-btn {
  display: flex; align-items: center; gap: 6px;
  padding: 9px 18px; border-radius: 12px; border: none; cursor: pointer;
  background: linear-gradient(135deg, #7c3aed, #4f46e5);
  color: #fff; font-size: 0.88rem; font-weight: 700;
  box-shadow: 0 4px 12px rgba(124,58,237,0.4);
  transition: opacity .15s;
  &:hover { opacity: 0.9; }
}

/* ── Modals ── */
.modal-bg {
  position: fixed; inset: 0; background: rgba(0,0,0,0.5); z-index: 100;
  display: flex; align-items: flex-end; justify-content: center;
  padding: 0 0 0;
  @media (min-width: 600px) { align-items: center; }
}
.modal-box {
  background: var(--md-sys-color-surface);
  border-radius: 24px 24px 0 0; padding: 24px; width: 100%; max-width: 520px;
  max-height: 90vh; overflow-y: auto;
  @media (min-width: 600px) { border-radius: 24px; }
  &.wide { max-width: 660px; }
}
.modal-head {
  display: flex; align-items: flex-start; gap: 14px; margin-bottom: 20px;
}
.modal-head-ico {
  width: 32px; height: 32px; color: #7c3aed; flex-shrink: 0; margin-top: 2px;
  &.green { color: #059669; }
}
.modal-title { font-size: 1.05rem; font-weight: 700; }
.modal-sub { font-size: 0.8rem; color: var(--md-sys-color-on-surface-variant); margin-top: 2px; }
.modal-section { margin-bottom: 18px; }
.modal-label {
  font-size: 0.78rem; font-weight: 600; text-transform: uppercase;
  letter-spacing: 0.5px; color: var(--md-sys-color-on-surface-variant); margin-bottom: 8px;
}
.modal-label-hint { text-transform: none; font-weight: 400; letter-spacing: 0; }
.modal-input {
  width: 100%; padding: 10px 12px; border-radius: 10px;
  border: 1.5px solid var(--md-sys-color-outline);
  background: var(--md-sys-color-surface-container);
  color: var(--md-sys-color-on-surface);
  font-size: 0.95rem; outline: none; box-sizing: border-box;
  &:focus { border-color: #7c3aed; }
}
.pin-entry-row { display: flex; gap: 8px; align-items: center; }
.eye-btn {
  background: none; border: none; cursor: pointer; padding: 8px;
  color: var(--md-sys-color-on-surface-variant); display: flex; align-items: center;
  &:hover { color: #7c3aed; }
}
.modal-actions { display: flex; gap: 10px; margin-top: 20px; flex-wrap: wrap; }
.modal-confirm-btn {
  flex: 1; display: flex; align-items: center; justify-content: center; gap: 6px;
  padding: 12px; border-radius: 12px; border: none; cursor: pointer;
  background: linear-gradient(135deg, #7c3aed, #4f46e5); color: #fff;
  font-size: 0.92rem; font-weight: 700;
  box-shadow: 0 4px 12px rgba(124,58,237,0.3);
  &.green { background: linear-gradient(135deg, #059669, #10b981); box-shadow: 0 4px 12px rgba(5,150,105,0.3); }
  &:disabled { opacity: 0.6; cursor: not-allowed; }
}
.modal-cancel-btn {
  padding: 12px 20px; border-radius: 12px; cursor: pointer;
  background: var(--md-sys-color-surface-container-high);
  border: 1px solid var(--md-sys-color-outline);
  color: var(--md-sys-color-on-surface); font-size: 0.92rem;
}

/* Selected chips preview */
.selected-chips { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 18px; }
.sel-chip {
  display: inline-flex; align-items: center; gap: 6px;
  background: var(--md-sys-color-surface-container-high);
  border-radius: 99px; padding: 4px 10px 4px 4px;
  font-size: 0.78rem; font-weight: 500;
}
.sel-chip-dot {
  width: 22px; height: 22px; border-radius: 50%;
  display: inline-flex; align-items: center; justify-content: center;
  color: #fff; font-size: 0.72rem; font-weight: 700; flex-shrink: 0;
}

/* Type toggle */
.type-toggle { display: flex; gap: 8px; }
.type-btn {
  flex: 1; display: flex; align-items: center; justify-content: center; gap: 6px;
  padding: 10px; border-radius: 10px; border: 1.5px solid var(--md-sys-color-outline);
  cursor: pointer; background: none; color: var(--md-sys-color-on-surface-variant);
  font-size: 0.88rem; font-weight: 500; transition: all .15s;
  &.active { border-color: #7c3aed; background: #ede9fe; color: #6d28d9; }
}
.type-ico { width: 16px; height: 16px; }

/* Pattern grid (bulk lock modal) */
.pattern-grid-wrap { display: flex; flex-direction: column; gap: 10px; }
.pattern-grid { display: grid; grid-template-columns: repeat(3, 56px); gap: 8px; }
.pattern-dot {
  width: 56px; height: 56px; border-radius: 50%; position: relative;
  background: var(--md-sys-color-surface-container-high);
  border: 2px solid var(--md-sys-color-outline-variant);
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  font-size: 0.85rem; font-weight: 600; color: var(--md-sys-color-on-surface-variant);
  transition: all .15s;
  &.selected { background: linear-gradient(135deg,#7c3aed,#4f46e5); border-color: #7c3aed; color: #fff; }
  &.last { box-shadow: 0 0 0 3px rgba(124,58,237,0.4); }
}
.dot-label { font-size: 0.85rem; font-weight: 600; }
.dot-order {
  position: absolute; top: -5px; right: -5px;
  background: #4f46e5; color: #fff; border-radius: 99px;
  font-size: 0.6rem; font-weight: 700; padding: 0 3px; min-width: 14px; text-align: center;
}
.pattern-preview-row { display: flex; align-items: center; gap: 10px; font-size: 0.82rem; color: var(--md-sys-color-on-surface-variant); }
.link-btn { background: none; border: none; cursor: pointer; color: #7c3aed; font-size: 0.82rem; padding: 0; }

/* Result banners */
.result-banner {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 14px; border-radius: 12px; font-size: 0.87rem;
  margin-top: 14px;
  &.ok { background: #d1fae5; color: #065f46; }
  &.fail { background: #fee2e2; color: #b91c1c; }
}
.rb-ico { width: 18px; height: 18px; flex-shrink: 0; }

/* Attempt log */
.attempts-toolbar {
  display: flex; align-items: center; gap: 10px; margin-bottom: 12px; flex-wrap: wrap;
}
.chk-label { display: flex; align-items: center; gap: 6px; cursor: pointer; font-size: 0.85rem; }
.danger-sm-btn {
  padding: 5px 12px; border-radius: 8px; border: none; cursor: pointer;
  background: #fee2e2; color: #b91c1c; font-size: 0.82rem; font-weight: 500;
  &:disabled { opacity: 0.5; cursor: not-allowed; }
}
.ml-auto { margin-left: auto; }
.attempts-list { max-height: 320px; overflow-y: auto; }
.attempt-row {
  display: flex; align-items: center; gap: 10px; padding: 8px 4px;
  border-bottom: 1px solid var(--md-sys-color-outline-variant);
  &:last-child { border-bottom: none; }
  &.ok { background: rgba(5,150,105,0.04); border-radius: 6px; }
  &.fail { background: rgba(220,38,38,0.04); border-radius: 6px; }
}
.att-ico { width: 16px; height: 16px; flex-shrink: 0; &.ok { color: #059669; } &.fail { color: #dc2626; } }
.att-ts { font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); min-width: 130px; }
.att-label { font-size: 0.83rem; }

/* ── Transitions ── */
.slide-up-enter-active, .slide-up-leave-active { transition: transform .25s ease, opacity .25s ease; }
.slide-up-enter-from, .slide-up-leave-to { transform: translateX(-50%) translateY(80px); opacity: 0; }
.fade-enter-active, .fade-leave-active { transition: opacity .2s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

/* ── Misc ── */
.mono { font-family: 'JetBrains Mono', 'Fira Code', monospace; }
</style>
