<template>
  <div class="scroll-content">
    <div class="top-app-bar">
      <router-link to="/device-hub" class="back-btn">
        <i-lucide:arrow-left />
      </router-link>
      <div class="title">Per-App Lock Manager</div>
    </div>

    <div class="page-body">
      <!-- Set / Edit lock for an app -->
      <section class="card">
        <h5 class="card-title">
          <i-lucide:shield-check class="icon-inline" />
          Add / Update App Lock
        </h5>
        <div class="card-body">
          <div class="field-row">
            <label class="field-label">App Package Name</label>
            <div class="app-input-row">
              <input v-model="form.packageName" class="text-input" placeholder="com.example.app" list="installed-apps-list" />
              <datalist id="installed-apps-list">
                <option v-for="a in installedApps" :key="a.id" :value="a.packageName">{{ a.label }}</option>
              </datalist>
            </div>
            <p v-if="installedApps.length" class="hint">Type or pick from {{ installedApps.length }} installed apps</p>
          </div>

          <div class="field-row">
            <label class="field-label">Lock Type</label>
            <div class="radio-row">
              <label class="radio-label">
                <input type="radio" v-model="form.lockType" value="pin" />
                PIN (digits)
              </label>
              <label class="radio-label">
                <input type="radio" v-model="form.lockType" value="pattern" />
                Pattern (3×3 grid)
              </label>
            </div>
          </div>

          <div v-if="form.lockType === 'pin'" class="field-row">
            <label class="field-label">PIN (min 4 digits)</label>
            <div class="pin-row">
              <input
                v-model="form.credential"
                :type="showCredential ? 'text' : 'password'"
                inputmode="numeric"
                class="text-input pin-input"
                placeholder="••••"
                maxlength="12"
              />
              <button class="eye-btn" @click="showCredential = !showCredential" type="button" title="Show/hide">
                <i-lucide:eye v-if="!showCredential" />
                <i-lucide:eye-off v-else />
              </button>
            </div>
          </div>

          <div v-if="form.lockType === 'pattern'" class="field-row">
            <label class="field-label">Draw Pattern</label>
            <div class="pattern-grid-wrap">
              <div class="pattern-grid">
                <button
                  v-for="n in 9"
                  :key="n"
                  class="pattern-dot"
                  :class="{ selected: patternSeq.includes(n), last: patternSeq[patternSeq.length - 1] === n }"
                  @click="togglePatternDot(n)"
                  type="button"
                >
                  <span class="dot-num">{{ n }}</span>
                </button>
              </div>
              <div class="pattern-preview">
                Pattern: <span class="mono">{{ patternSeq.length ? patternSeq.join('-') : 'none' }}</span>
                <button v-if="patternSeq.length" class="link-btn" @click="patternSeq = []" type="button">Clear</button>
              </div>
            </div>
          </div>

          <label class="check-row">
            <input type="checkbox" v-model="form.biometricEnabled" />
            Allow biometric (fingerprint) as alternative
          </label>

          <div class="action-row">
            <button class="primary-btn" :disabled="saving" @click="saveLock">
              <span v-if="saving">Saving…</span>
              <span v-else>Set Lock</span>
            </button>
          </div>
        </div>
      </section>

      <!-- Locked apps list -->
      <section class="card">
        <h5 class="card-title">
          <i-lucide:lock class="icon-inline" />
          Locked Apps ({{ lockedApps.length }})
        </h5>
        <div class="card-body">
          <p v-if="!lockedApps.length" class="empty-hint">No apps are locked yet.</p>
          <div v-for="app in lockedApps" :key="app.packageName" class="app-row">
            <div class="app-info">
              <div class="app-pkg">{{ app.packageName }}</div>
              <div class="app-meta">
                <span class="badge" :class="app.lockType">{{ app.lockType.toUpperCase() }}</span>
                <span v-if="app.biometricEnabled" class="badge bio">Bio</span>
                <span class="badge neutral">{{ app.totalAttempts }} attempts</span>
                <span v-if="app.wrongAttempts > 0" class="badge warn">{{ app.wrongAttempts }} wrong</span>
                <!-- Session unlock badge -->
                <span v-if="sessionMap[app.packageName]?.unlocked" class="badge unlocked">
                  <i-lucide:unlock class="badge-icon" />
                  Unlocked — {{ formatSecs(sessionMap[app.packageName].secondsRemaining) }} left
                </span>
              </div>
            </div>
            <div class="app-actions">
              <button
                class="icon-btn unlock-btn"
                :class="{ active: sessionMap[app.packageName]?.unlocked }"
                :title="sessionMap[app.packageName]?.unlocked ? 'Extend unlock session' : 'Unlock for 10 minutes'"
                @click="openUnlock(app)"
              >
                <i-lucide:unlock-keyhole />
              </button>
              <button class="icon-btn reveal-btn" title="Reveal credential (master password required)" @click="openReveal(app)">
                <i-lucide:eye />
              </button>
              <button class="icon-btn log-btn" title="View attempt log" @click="openAttempts(app)">
                <i-lucide:list />
              </button>
              <button class="icon-btn danger-btn" title="Remove lock" @click="removeLock(app.packageName)">
                <i-lucide:trash-2 />
              </button>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- Unlock session dialog -->
    <div v-if="unlockDialog.open" class="modal-backdrop" @click.self="unlockDialog.open = false">
      <div class="modal">
        <h4 class="modal-title">
          <i-lucide:unlock-keyhole class="icon-inline" />
          Unlock for 10 Minutes
        </h4>
        <p class="modal-desc">
          Enter the PIN, pattern sequence, or master password to grant a 10-minute unlock window for
          <code>{{ unlockDialog.packageName }}</code> on the device.
        </p>

        <div class="pin-row">
          <input
            ref="unlockInputRef"
            v-model="unlockDialog.credential"
            :type="unlockDialog.showCred ? 'text' : 'password'"
            class="text-input"
            placeholder="PIN / pattern sequence / master password"
            @keyup.enter="doUnlock"
          />
          <button class="eye-btn" @click="unlockDialog.showCred = !unlockDialog.showCred" type="button">
            <i-lucide:eye v-if="!unlockDialog.showCred" />
            <i-lucide:eye-off v-else />
          </button>
        </div>

        <div v-if="unlockDialog.result === true" class="unlock-success">
          <i-lucide:check-circle class="success-icon" />
          App unlocked for 10 minutes. The lock will re-engage automatically.
        </div>
        <div v-else-if="unlockDialog.result === false" class="unlock-fail">
          <i-lucide:x-circle class="fail-icon" />
          Wrong credential. Try again.
        </div>

        <div class="modal-actions">
          <button class="primary-btn" @click="doUnlock" :disabled="unlockDialog.loading">
            {{ unlockDialog.loading ? 'Verifying…' : 'Unlock' }}
          </button>
          <button class="secondary-btn" @click="unlockDialog.open = false">Close</button>
        </div>
      </div>
    </div>

    <!-- Reveal credential dialog -->
    <div v-if="revealDialog.open" class="modal-backdrop" @click.self="revealDialog.open = false">
      <div class="modal">
        <h4 class="modal-title">Reveal Credential</h4>
        <p class="modal-desc">Enter the master password to reveal the stored credential for <code>{{ revealDialog.packageName }}</code>.</p>
        <div class="pin-row">
          <input
            v-model="revealDialog.masterPwd"
            :type="revealDialog.showMaster ? 'text' : 'password'"
            class="text-input"
            placeholder="Master password"
          />
          <button class="eye-btn" @click="revealDialog.showMaster = !revealDialog.showMaster" type="button">
            <i-lucide:eye v-if="!revealDialog.showMaster" />
            <i-lucide:eye-off v-else />
          </button>
        </div>
        <div v-if="revealDialog.result" class="reveal-result">
          <span class="reveal-label">Credential:</span>
          <span class="mono reveal-value">{{ revealDialog.result }}</span>
        </div>
        <div class="modal-actions">
          <button class="primary-btn" @click="doReveal" :disabled="revealDialog.loading">
            {{ revealDialog.loading ? 'Revealing…' : 'Reveal' }}
          </button>
          <button class="secondary-btn" @click="revealDialog.open = false">Close</button>
        </div>
      </div>
    </div>

    <!-- Attempt log dialog -->
    <div v-if="attemptsDialog.open" class="modal-backdrop" @click.self="attemptsDialog.open = false">
      <div class="modal modal-wide">
        <h4 class="modal-title">Attempt Log — {{ attemptsDialog.packageName }}</h4>
        <div class="attempts-toolbar">
          <label class="check-row inline">
            <input type="checkbox" v-model="selectAllAttempts" @change="toggleSelectAll" />
            Select all
          </label>
          <button
            v-if="attemptsDialog.selected.size > 0"
            class="danger-btn-sm"
            @click="deleteSelectedAttempts"
            :disabled="attemptsDialog.deleting"
          >
            Delete selected ({{ attemptsDialog.selected.size }})
          </button>
          <button class="danger-btn-sm ml-auto" @click="clearAllAttempts" :disabled="attemptsDialog.deleting">
            Clear all
          </button>
        </div>
        <div class="attempts-list">
          <p v-if="!attemptsDialog.items.length" class="empty-hint">No attempts recorded.</p>
          <div v-for="a in attemptsDialog.items" :key="a.id" class="attempt-row" :class="{ success: a.success, fail: !a.success }">
            <input
              type="checkbox"
              :checked="attemptsDialog.selected.has(a.id)"
              @change="toggleAttemptSelect(a.id)"
            />
            <i-lucide:check-circle v-if="a.success" class="attempt-icon ok" />
            <i-lucide:x-circle v-else class="attempt-icon fail" />
            <span class="attempt-ts">{{ formatTs(a.timestamp) }}</span>
            <span class="attempt-label">{{ a.success ? 'Unlocked' : 'Wrong password' }}</span>
          </div>
        </div>
        <div class="modal-actions">
          <button class="secondary-btn" @click="attemptsDialog.open = false">Close</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import emitter from '@/plugins/eventbus'

const INSTALLED_APPS_QUERY = `
  query packages($limit: Int!, $offset: Int!, $query: String!, $sortBy: FileSortBy!) {
    packages(limit: $limit, offset: $offset, query: $query, sortBy: $sortBy) {
      id
      name
    }
  }
`

const PER_APP_LOCKS_QUERY = `
  query perAppLocks {
    perAppLocks {
      packageName
      lockType
      biometricEnabled
      totalAttempts
      wrongAttempts
    }
  }
`

const PER_APP_ATTEMPTS_QUERY = `
  query perAppLockAttempts($packageName: String!) {
    perAppLockAttempts(packageName: $packageName) {
      id
      packageName
      timestamp
      success
    }
  }
`

const PER_APP_SESSIONS_QUERY = `
  query perAppLockSessions {
    perAppLockSessions {
      packageName
      unlocked
      secondsRemaining
    }
  }
`

const SET_LOCK_MUTATION = `
  mutation setPerAppLock($packageName: String!, $lockType: String!, $credential: String!, $biometricEnabled: Boolean!) {
    setPerAppLock(packageName: $packageName, lockType: $lockType, credential: $credential, biometricEnabled: $biometricEnabled)
  }
`

const REMOVE_LOCK_MUTATION = `
  mutation removePerAppLock($packageName: String!) {
    removePerAppLock(packageName: $packageName)
  }
`

const VERIFY_LOCK_MUTATION = `
  mutation verifyPerAppLock($packageName: String!, $credential: String!) {
    verifyPerAppLock(packageName: $packageName, credential: $credential)
  }
`

const REVEAL_MUTATION = `
  mutation revealPerAppLockCredential($packageName: String!, $masterPassword: String!) {
    revealPerAppLockCredential(packageName: $packageName, masterPassword: $masterPassword)
  }
`

const DELETE_ATTEMPTS_MUTATION = `
  mutation deletePerAppLockAttempts($packageName: String!, $ids: [Long!]!) {
    deletePerAppLockAttempts(packageName: $packageName, ids: $ids)
  }
`

interface AppLockEntry {
  packageName: string
  lockType: string
  biometricEnabled: boolean
  totalAttempts: number
  wrongAttempts: number
}

interface AttemptEntry {
  id: number
  packageName: string
  timestamp: number
  success: boolean
}

interface InstalledApp {
  id: string
  label: string
  packageName: string
}

interface SessionEntry {
  packageName: string
  unlocked: boolean
  secondsRemaining: number
}

const lockedApps = ref<AppLockEntry[]>([])
const installedApps = ref<InstalledApp[]>([])
const sessionMap = ref<Record<string, SessionEntry>>({})
const saving = ref(false)
const showCredential = ref(false)
const patternSeq = ref<number[]>([])
const selectAllAttempts = ref(false)
const unlockInputRef = ref<HTMLInputElement | null>(null)

let sessionPollTimer: ReturnType<typeof setInterval> | null = null

const form = reactive({
  packageName: '',
  lockType: 'pin',
  credential: '',
  biometricEnabled: false,
})

const unlockDialog = reactive({
  open: false,
  packageName: '',
  credential: '',
  showCred: false,
  loading: false,
  result: null as boolean | null,
})

const revealDialog = reactive({
  open: false,
  packageName: '',
  masterPwd: '',
  showMaster: false,
  loading: false,
  result: '',
})

const attemptsDialog = reactive({
  open: false,
  packageName: '',
  items: [] as AttemptEntry[],
  selected: new Set<number>(),
  deleting: false,
})

function formatTs(ts: number): string {
  return new Date(ts).toLocaleString()
}

function formatSecs(secs: number): string {
  const m = Math.floor(secs / 60)
  const s = secs % 60
  return m > 0 ? `${m}m ${s}s` : `${s}s`
}

function togglePatternDot(n: number) {
  const idx = patternSeq.value.indexOf(n)
  if (idx >= 0) {
    patternSeq.value = patternSeq.value.slice(0, idx)
  } else {
    patternSeq.value = [...patternSeq.value, n]
  }
  form.credential = patternSeq.value.join('')
}

async function fetchLockedApps() {
  const r = await gqlFetch<{ perAppLocks: AppLockEntry[] }>(PER_APP_LOCKS_QUERY)
  if (r.errors?.length) { emitter.emit('toast', r.errors[0].message); return }
  lockedApps.value = r.data.perAppLocks
}

async function fetchInstalledApps() {
  try {
    const r = await gqlFetch<{ packages: any[] }>(INSTALLED_APPS_QUERY, {
      limit: 500,
      offset: 0,
      query: '',
      sortBy: 'NAME_ASC',
    })
    if (r.errors?.length) return
    installedApps.value = (r.data.packages || []).map((p: any) => ({
      id: p.id,
      label: p.name || p.id,
      packageName: p.id,
    }))
  } catch (_) {}
}

async function fetchSessions() {
  try {
    const r = await gqlFetch<{ perAppLockSessions: SessionEntry[] }>(PER_APP_SESSIONS_QUERY)
    if (r.errors?.length) return
    const map: Record<string, SessionEntry> = {}
    for (const s of r.data.perAppLockSessions) map[s.packageName] = s
    sessionMap.value = map
  } catch (_) {}
}

async function saveLock() {
  if (!form.packageName.trim()) { emitter.emit('toast', 'Package name is required'); return }
  const cred = form.lockType === 'pattern' ? patternSeq.value.join('') : form.credential
  if (!cred) { emitter.emit('toast', 'Credential is required'); return }
  if (form.lockType === 'pin' && (cred.length < 4 || !/^\d+$/.test(cred))) {
    emitter.emit('toast', 'PIN must be at least 4 digits')
    return
  }
  if (form.lockType === 'pattern' && patternSeq.value.length < 4) {
    emitter.emit('toast', 'Pattern must have at least 4 points')
    return
  }
  saving.value = true
  try {
    const r = await gqlFetch(SET_LOCK_MUTATION, {
      packageName: form.packageName.trim(),
      lockType: form.lockType,
      credential: cred,
      biometricEnabled: form.biometricEnabled,
    })
    if (r.errors?.length) { emitter.emit('toast', r.errors[0].message); return }
    emitter.emit('toast', 'Lock saved')
    form.packageName = ''
    form.credential = ''
    patternSeq.value = []
    form.biometricEnabled = false
    showCredential.value = false
    await fetchLockedApps()
  } catch (e: any) {
    emitter.emit('toast', e?.message ?? 'network_error')
  } finally {
    saving.value = false
  }
}

async function removeLock(pkg: string) {
  if (!confirm(`Remove lock for ${pkg}?`)) return
  const r = await gqlFetch(REMOVE_LOCK_MUTATION, { packageName: pkg })
  if (r.errors?.length) { emitter.emit('toast', r.errors[0].message); return }
  emitter.emit('toast', 'Lock removed')
  await fetchLockedApps()
  await fetchSessions()
}

function openUnlock(app: AppLockEntry) {
  unlockDialog.open = true
  unlockDialog.packageName = app.packageName
  unlockDialog.credential = ''
  unlockDialog.showCred = false
  unlockDialog.loading = false
  unlockDialog.result = null
  nextTick(() => unlockInputRef.value?.focus())
}

async function doUnlock() {
  if (!unlockDialog.credential.trim()) { emitter.emit('toast', 'Enter the credential'); return }
  unlockDialog.loading = true
  unlockDialog.result = null
  try {
    const r = await gqlFetch<{ verifyPerAppLock: boolean }>(VERIFY_LOCK_MUTATION, {
      packageName: unlockDialog.packageName,
      credential: unlockDialog.credential.trim(),
    })
    if (r.errors?.length) { emitter.emit('toast', r.errors[0].message); return }
    const ok = r.data.verifyPerAppLock
    unlockDialog.result = ok
    if (ok) {
      unlockDialog.credential = ''
      await fetchSessions()
      await fetchLockedApps()
    }
  } catch (e: any) {
    emitter.emit('toast', e?.message ?? 'network_error')
  } finally {
    unlockDialog.loading = false
  }
}

function openReveal(app: AppLockEntry) {
  revealDialog.open = true
  revealDialog.packageName = app.packageName
  revealDialog.masterPwd = ''
  revealDialog.showMaster = false
  revealDialog.result = ''
  revealDialog.loading = false
}

async function doReveal() {
  if (!revealDialog.masterPwd) { emitter.emit('toast', 'Enter the master password'); return }
  revealDialog.loading = true
  try {
    const r = await gqlFetch<{ revealPerAppLockCredential: string }>(REVEAL_MUTATION, {
      packageName: revealDialog.packageName,
      masterPassword: revealDialog.masterPwd,
    })
    if (r.errors?.length) { emitter.emit('toast', r.errors[0].message); return }
    revealDialog.result = r.data.revealPerAppLockCredential
  } catch (e: any) {
    emitter.emit('toast', e?.message ?? 'network_error')
  } finally {
    revealDialog.loading = false
  }
}

async function openAttempts(app: AppLockEntry) {
  attemptsDialog.open = true
  attemptsDialog.packageName = app.packageName
  attemptsDialog.selected = new Set()
  selectAllAttempts.value = false
  const r = await gqlFetch<{ perAppLockAttempts: AttemptEntry[] }>(PER_APP_ATTEMPTS_QUERY, { packageName: app.packageName })
  if (r.errors?.length) { emitter.emit('toast', r.errors[0].message); return }
  attemptsDialog.items = r.data.perAppLockAttempts
}

function toggleAttemptSelect(id: number) {
  if (attemptsDialog.selected.has(id)) {
    attemptsDialog.selected.delete(id)
  } else {
    attemptsDialog.selected.add(id)
  }
  selectAllAttempts.value = attemptsDialog.selected.size === attemptsDialog.items.length
}

function toggleSelectAll() {
  if (selectAllAttempts.value) {
    attemptsDialog.selected = new Set(attemptsDialog.items.map((a) => a.id))
  } else {
    attemptsDialog.selected = new Set()
  }
}

async function deleteSelectedAttempts() {
  attemptsDialog.deleting = true
  try {
    const ids = Array.from(attemptsDialog.selected)
    const r = await gqlFetch(DELETE_ATTEMPTS_MUTATION, { packageName: attemptsDialog.packageName, ids })
    if (r.errors?.length) { emitter.emit('toast', r.errors[0].message); return }
    emitter.emit('toast', 'Deleted')
    attemptsDialog.items = attemptsDialog.items.filter((a) => !attemptsDialog.selected.has(a.id))
    attemptsDialog.selected = new Set()
    selectAllAttempts.value = false
    await fetchLockedApps()
  } finally {
    attemptsDialog.deleting = false
  }
}

async function clearAllAttempts() {
  if (!confirm('Clear ALL attempt logs for this app?')) return
  attemptsDialog.deleting = true
  try {
    const r = await gqlFetch(DELETE_ATTEMPTS_MUTATION, { packageName: attemptsDialog.packageName, ids: [] })
    if (r.errors?.length) { emitter.emit('toast', r.errors[0].message); return }
    emitter.emit('toast', 'Cleared')
    attemptsDialog.items = []
    attemptsDialog.selected = new Set()
    selectAllAttempts.value = false
    await fetchLockedApps()
  } finally {
    attemptsDialog.deleting = false
  }
}

onMounted(async () => {
  await Promise.all([fetchLockedApps(), fetchInstalledApps(), fetchSessions()])
  // Poll session status every 15 seconds so the countdown badge updates
  sessionPollTimer = setInterval(fetchSessions, 15000)
})

onUnmounted(() => {
  if (sessionPollTimer) clearInterval(sessionPollTimer)
})
</script>

<style lang="scss" scoped>
.scroll-content { padding: 0 0 32px; }
.top-app-bar {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 16px; position: sticky; top: 0;
  background: var(--md-sys-color-surface); z-index: 10;
  border-bottom: 1px solid var(--md-sys-color-outline-variant);
  .back-btn { display: flex; align-items: center; color: var(--md-sys-color-on-surface); }
  .title { font-size: 1.1rem; font-weight: 600; }
}
.page-body { display: flex; flex-direction: column; gap: 16px; padding: 16px; }
.card {
  background: var(--md-sys-color-surface-container);
  border-radius: 12px; padding: 16px;
}
.card-title {
  font-size: 1rem; font-weight: 600; margin: 0 0 12px;
  display: flex; align-items: center; gap: 8px;
}
.icon-inline { width: 18px; height: 18px; }
.field-row { margin-bottom: 14px; }
.field-label { display: block; font-size: 0.85rem; font-weight: 500; margin-bottom: 6px; color: var(--md-sys-color-on-surface-variant); }
.text-input {
  width: 100%; padding: 8px 12px; border-radius: 8px;
  border: 1px solid var(--md-sys-color-outline);
  background: var(--md-sys-color-surface);
  color: var(--md-sys-color-on-surface);
  font-size: 0.95rem; outline: none;
  box-sizing: border-box;
  &:focus { border-color: var(--md-sys-color-primary); }
}
.pin-input { max-width: 220px; }
.app-input-row { display: flex; gap: 8px; align-items: center; }
.pin-row { display: flex; align-items: center; gap: 8px; }
.eye-btn {
  background: none; border: none; cursor: pointer; padding: 6px;
  color: var(--md-sys-color-on-surface-variant);
  display: flex; align-items: center;
  &:hover { color: var(--md-sys-color-primary); }
}
.radio-row { display: flex; gap: 20px; }
.radio-label { display: flex; align-items: center; gap: 6px; cursor: pointer; font-size: 0.9rem; }
.check-row { display: flex; align-items: center; gap: 8px; cursor: pointer; font-size: 0.9rem; margin: 8px 0; }
.check-row.inline { display: inline-flex; }
.hint { font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); margin: 4px 0 0; }
.action-row { margin-top: 12px; }
.primary-btn {
  padding: 9px 20px; border-radius: 8px; border: none; cursor: pointer;
  background: var(--md-sys-color-primary); color: var(--md-sys-color-on-primary);
  font-size: 0.9rem; font-weight: 500;
  &:disabled { opacity: 0.6; cursor: not-allowed; }
  &:hover:not(:disabled) { opacity: 0.9; }
}
.secondary-btn {
  padding: 8px 18px; border-radius: 8px; cursor: pointer;
  background: var(--md-sys-color-surface-container-high);
  border: 1px solid var(--md-sys-color-outline); color: var(--md-sys-color-on-surface);
  font-size: 0.9rem;
}
.empty-hint { font-size: 0.85rem; color: var(--md-sys-color-on-surface-variant); margin: 0; }
.app-row {
  display: flex; align-items: center; justify-content: space-between;
  gap: 12px; padding: 10px 0;
  border-bottom: 1px solid var(--md-sys-color-outline-variant);
  &:last-child { border-bottom: none; }
}
.app-info { flex: 1; min-width: 0; }
.app-pkg { font-size: 0.88rem; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.app-meta { display: flex; gap: 6px; margin-top: 4px; flex-wrap: wrap; align-items: center; }
.badge {
  font-size: 0.72rem; padding: 2px 7px; border-radius: 99px; font-weight: 500;
  display: inline-flex; align-items: center; gap: 4px;
  &.pin { background: #e3f2fd; color: #1565c0; }
  &.pattern { background: #f3e5f5; color: #6a1b9a; }
  &.bio { background: #e8f5e9; color: #2e7d32; }
  &.neutral { background: var(--md-sys-color-surface-container-high); color: var(--md-sys-color-on-surface-variant); }
  &.warn { background: #fff3e0; color: #e65100; }
  &.unlocked { background: #e8f5e9; color: #1b5e20; font-weight: 600; }
}
.badge-icon { width: 11px; height: 11px; }
.app-actions { display: flex; gap: 4px; }
.icon-btn {
  background: none; border: none; cursor: pointer; padding: 6px; border-radius: 6px;
  color: var(--md-sys-color-on-surface-variant);
  display: flex; align-items: center; justify-content: center;
  &:hover { background: var(--md-sys-color-surface-container-high); }
}
.danger-btn { color: var(--md-sys-color-error); }
.unlock-btn {
  &.active { color: #2e7d32; }
  &:hover { color: var(--md-sys-color-primary); }
}
.modal-backdrop {
  position: fixed; inset: 0; background: rgba(0,0,0,0.4); z-index: 100;
  display: flex; align-items: center; justify-content: center;
}
.modal {
  background: var(--md-sys-color-surface); border-radius: 16px; padding: 24px;
  width: 90%; max-width: 460px; max-height: 80vh; overflow-y: auto;
  &.modal-wide { max-width: 620px; }
}
.modal-title { font-size: 1rem; font-weight: 600; margin: 0 0 8px; display: flex; align-items: center; gap: 8px; }
.modal-desc { font-size: 0.85rem; color: var(--md-sys-color-on-surface-variant); margin: 0 0 14px; }
.modal-actions { display: flex; gap: 10px; margin-top: 16px; }
.unlock-success {
  margin-top: 14px; padding: 12px 14px; border-radius: 10px;
  background: #e8f5e9; color: #1b5e20;
  display: flex; align-items: center; gap: 10px; font-size: 0.88rem;
}
.unlock-fail {
  margin-top: 14px; padding: 12px 14px; border-radius: 10px;
  background: #ffebee; color: #b71c1c;
  display: flex; align-items: center; gap: 10px; font-size: 0.88rem;
}
.success-icon { width: 20px; height: 20px; flex-shrink: 0; color: #2e7d32; }
.fail-icon { width: 20px; height: 20px; flex-shrink: 0; color: #c62828; }
.reveal-result {
  background: var(--md-sys-color-surface-container-high);
  border-radius: 8px; padding: 10px 14px; margin-top: 12px;
  display: flex; align-items: center; gap: 10px;
}
.reveal-label { font-size: 0.8rem; color: var(--md-sys-color-on-surface-variant); }
.reveal-value { font-size: 1rem; font-weight: 600; letter-spacing: 2px; }
.mono { font-family: monospace; }
.pattern-grid-wrap { display: inline-flex; flex-direction: column; gap: 8px; }
.pattern-grid {
  display: grid; grid-template-columns: repeat(3, 56px); gap: 8px;
}
.pattern-dot {
  width: 56px; height: 56px; border-radius: 50%;
  background: var(--md-sys-color-surface-container-high);
  border: 2px solid var(--md-sys-color-outline-variant);
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  font-size: 0.85rem; font-weight: 600; color: var(--md-sys-color-on-surface-variant);
  transition: all .15s;
  &.selected {
    background: var(--md-sys-color-primary-container);
    border-color: var(--md-sys-color-primary);
    color: var(--md-sys-color-on-primary-container);
  }
  &.last {
    background: var(--md-sys-color-primary);
    color: var(--md-sys-color-on-primary);
  }
}
.pattern-preview {
  font-size: 0.82rem; color: var(--md-sys-color-on-surface-variant);
  display: flex; align-items: center; gap: 8px;
}
.link-btn {
  background: none; border: none; cursor: pointer; color: var(--md-sys-color-primary); font-size: 0.82rem;
}
.attempts-toolbar {
  display: flex; align-items: center; gap: 10px; margin-bottom: 10px; flex-wrap: wrap;
}
.danger-btn-sm {
  padding: 5px 12px; border-radius: 6px; border: none; cursor: pointer;
  background: var(--md-sys-color-error-container); color: var(--md-sys-color-on-error-container);
  font-size: 0.82rem;
  &:disabled { opacity: 0.6; cursor: not-allowed; }
}
.ml-auto { margin-left: auto; }
.attempts-list { max-height: 340px; overflow-y: auto; }
.attempt-row {
  display: flex; align-items: center; gap: 10px; padding: 8px 0;
  border-bottom: 1px solid var(--md-sys-color-outline-variant);
  &:last-child { border-bottom: none; }
  &.success { background: rgba(76,175,80,.05); }
  &.fail { background: rgba(244,67,54,.05); }
}
.attempt-icon { width: 18px; height: 18px; flex-shrink: 0; &.ok { color: #2e7d32; } &.fail { color: #c62828; } }
.attempt-ts { font-size: 0.8rem; color: var(--md-sys-color-on-surface-variant); min-width: 140px; }
.attempt-label { font-size: 0.85rem; }
</style>
