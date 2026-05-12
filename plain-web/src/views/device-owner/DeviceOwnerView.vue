<template>
  <div class="do-root">
    <!-- ── Header ── -->
    <header class="do-header">
      <div class="do-title-block">
        <div class="do-icon-wrap" :class="{ active: status?.isDeviceOwner }">
          <i-lucide:shield-check v-if="status?.isDeviceOwner" />
          <i-lucide:shield-off v-else />
        </div>
        <div>
          <h2 class="do-title">{{ $t('device_owner_title') }}</h2>
          <p class="do-sub">{{ $t('device_owner_subtitle') }}</p>
        </div>
      </div>
      <div class="do-header-actions">
        <button class="ghost-btn" @click="loadStatus" :disabled="loading">
          <i-lucide:refresh-cw :class="{ spin: loading }" />
          {{ $t('refresh') }}
        </button>
      </div>
    </header>

    <!-- ── Status banner ── -->
    <div class="status-banner" :class="{ active: status?.isDeviceOwner, inactive: !status?.isDeviceOwner }">
      <div class="status-left">
        <div class="status-dot" :class="{ green: status?.isDeviceOwner, red: !status?.isDeviceOwner }" />
        <div>
          <div class="status-label">{{ status?.isDeviceOwner ? $t('device_owner_status_active') : $t('device_owner_status_inactive') }}</div>
          <div class="status-sub" v-if="status?.isDeviceAdmin && !status?.isDeviceOwner">{{ $t('device_admin_active') }} — upgrade to Device Owner for full superpowers.</div>
          <div class="status-sub" v-else-if="!status?.isDeviceAdmin">Neither Device Admin nor Device Owner is set.</div>
          <div class="status-sub" v-else>{{ status?.alreadyGrantedCount }}/{{ status?.grantablePermissionsCount }} auto-grantable permissions granted</div>
        </div>
      </div>
      <div class="status-chips" v-if="status?.isDeviceOwner">
        <span class="chip" :class="{ on: status?.uninstallBlocked }">
          <i-lucide:lock v-if="status?.uninstallBlocked" /><i-lucide:unlock v-else />
          Uninstall {{ status?.uninstallBlocked ? 'Blocked' : 'Allowed' }}
        </span>
        <span class="chip" :class="{ on: status?.kioskEnabled }">
          <i-lucide:monitor />
          Kiosk {{ status?.kioskEnabled ? 'On' : 'Off' }}
        </span>
        <span class="chip" :class="{ warn: status?.cameraDisabled }">
          <i-lucide:camera-off v-if="status?.cameraDisabled" /><i-lucide:camera v-else />
          Camera {{ status?.cameraDisabled ? 'Disabled' : 'On' }}
        </span>
      </div>
    </div>

    <!-- ── Setup card (shown only if NOT device owner) ── -->
    <div v-if="!status?.isDeviceOwner" class="setup-card">
      <div class="setup-header">
        <i-lucide:terminal class="setup-icon" />
        <div>
          <h3 class="setup-title">How to Enable Device Owner</h3>
          <p class="setup-desc">Run this once via ADB (USB cable required). Accounts must be removed first, or use a fresh device.</p>
        </div>
      </div>
      <div class="setup-steps">
        <div class="setup-step-item">
          <span class="step-num">1</span>
          <div class="step-body">
            <div class="step-label">Remove all accounts from device</div>
            <div class="step-sub">Settings → Accounts → remove each Google/Samsung/etc. account. Re-add them after.</div>
          </div>
        </div>
        <div class="setup-step-item">
          <span class="step-num">2</span>
          <div class="step-body">
            <div class="step-label">Enable USB Debugging</div>
            <div class="step-sub">Settings → Developer Options → USB Debugging → ON</div>
          </div>
        </div>
        <div class="setup-step-item">
          <span class="step-num">3</span>
          <div class="step-body">
            <div class="step-label">Connect USB and run this command</div>
            <div class="cmd-row">
              <code class="cmd-text">{{ $t('device_owner_setup_cmd') }}</code>
              <button class="copy-btn small" @click="copyText($t('device_owner_setup_cmd'), 'setup')">{{ copied === 'setup' ? '✅' : '📋' }}</button>
            </div>
          </div>
        </div>
        <div class="setup-step-item">
          <span class="step-num">4</span>
          <div class="step-body">
            <div class="step-label">Verify it worked</div>
            <div class="cmd-row">
              <code class="cmd-text">{{ $t('device_owner_verify_cmd') }}</code>
              <button class="copy-btn small" @click="copyText($t('device_owner_verify_cmd'), 'verify')">{{ copied === 'verify' ? '✅' : '📋' }}</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ═══════════════ DEVICE OWNER FEATURES ═══════════════ -->
    <div v-if="status" class="features-grid">

      <!-- ── 1. Grant All Permissions ── (works only when owner) -->
      <div class="feature-card highlight-card" :class="{ locked: !status.isDeviceOwner }">
        <div class="feat-header">
          <div class="feat-icon-wrap green">
            <i-lucide:key />
          </div>
          <div class="feat-info">
            <div class="feat-title">{{ $t('grant_all_permissions') }}</div>
            <div class="feat-desc">{{ $t('grant_all_permissions_desc') }}</div>
          </div>
          <div v-if="!status.isDeviceOwner" class="locked-badge">{{ $t('requires_device_owner') }}</div>
        </div>
        <div v-if="status.isDeviceOwner" class="feat-body">
          <div class="perm-progress">
            <div class="prog-bar"><div class="prog-fill" :style="{ width: permPct + '%' }" /></div>
            <span class="prog-lbl">{{ status.alreadyGrantedCount }}/{{ status.grantablePermissionsCount }} {{ $t('permissions_already_granted') }}</span>
          </div>
          <div v-if="grantResult" class="grant-result" :class="{ ok: grantResult.success, partial: !grantResult.success }">
            <i-lucide:check-circle v-if="grantResult.success" />
            <i-lucide:alert-triangle v-else />
            <span v-if="grantResult.success">{{ $t('grant_all_success') }} ({{ grantResult.granted.length }} granted)</span>
            <span v-else>{{ $t('grant_all_partial') }} ({{ grantResult.granted.length }} granted, {{ grantResult.failed.length }} failed)</span>
          </div>
          <button class="action-btn green-btn" @click="grantAllPerms" :disabled="grantingPerms">
            <i-lucide:loader-2 v-if="grantingPerms" class="spin" />
            <i-lucide:zap v-else />
            {{ grantingPerms ? 'Granting…' : 'Grant All Permissions Now' }}
          </button>
        </div>
      </div>

      <!-- ── 2. Block Uninstall ── -->
      <div class="feature-card" :class="{ locked: !status.isDeviceOwner }">
        <div class="feat-header">
          <div class="feat-icon-wrap" :class="{ blue: status.uninstallBlocked }">
            <i-lucide:lock />
          </div>
          <div class="feat-info">
            <div class="feat-title">{{ $t('block_uninstall') }}</div>
            <div class="feat-desc">{{ $t('block_uninstall_desc') }}</div>
          </div>
          <div v-if="!status.isDeviceOwner" class="locked-badge">{{ $t('requires_device_owner') }}</div>
          <label v-else class="toggle-wrap">
            <input type="checkbox" class="toggle-input" :checked="status.uninstallBlocked"
              @change="toggleUninstallBlock" :disabled="toggling.uninstall" />
            <span class="toggle-track"><span class="toggle-thumb" /></span>
          </label>
        </div>
      </div>

      <!-- ── 3. Kiosk Mode ── -->
      <div class="feature-card" :class="{ locked: !status.isDeviceOwner }">
        <div class="feat-header">
          <div class="feat-icon-wrap" :class="{ orange: status.kioskEnabled }">
            <i-lucide:monitor-dot />
          </div>
          <div class="feat-info">
            <div class="feat-title">{{ $t('kiosk_mode') }}</div>
            <div class="feat-desc">{{ $t('kiosk_mode_desc') }}</div>
          </div>
          <div v-if="!status.isDeviceOwner" class="locked-badge">{{ $t('requires_device_owner') }}</div>
          <label v-else class="toggle-wrap">
            <input type="checkbox" class="toggle-input" :checked="status.kioskEnabled"
              @change="toggleKiosk" :disabled="toggling.kiosk" />
            <span class="toggle-track"><span class="toggle-thumb" /></span>
          </label>
        </div>
        <div v-if="status.isDeviceOwner && status.kioskEnabled" class="feat-warning">
          <i-lucide:alert-triangle />
          {{ $t('kiosk_mode_warning') }}
        </div>
      </div>

      <!-- ── 4. Disable Camera Device-Wide ── -->
      <div class="feature-card" :class="{ locked: !status.isDeviceOwner }">
        <div class="feat-header">
          <div class="feat-icon-wrap" :class="{ red: status.cameraDisabled }">
            <i-lucide:camera-off v-if="status.cameraDisabled" /><i-lucide:camera v-else />
          </div>
          <div class="feat-info">
            <div class="feat-title">{{ $t('disable_camera_dpm') }}</div>
            <div class="feat-desc">{{ $t('disable_camera_dpm_desc') }}</div>
          </div>
          <div v-if="!status.isDeviceOwner" class="locked-badge">{{ $t('requires_device_owner') }}</div>
          <label v-else class="toggle-wrap">
            <input type="checkbox" class="toggle-input" :checked="status.cameraDisabled"
              @change="toggleCamera" :disabled="toggling.camera" />
            <span class="toggle-track"><span class="toggle-thumb" /></span>
          </label>
        </div>
      </div>

      <!-- ── 5. Disable Bluetooth Device-Wide ── -->
      <div class="feature-card" :class="{ locked: !status.isDeviceOwner }">
        <div class="feat-header">
          <div class="feat-icon-wrap" :class="{ red: status.bluetoothDisabled }">
            <i-lucide:bluetooth-off v-if="status.bluetoothDisabled" /><i-lucide:bluetooth v-else />
          </div>
          <div class="feat-info">
            <div class="feat-title">{{ $t('disable_bluetooth_dpm') }}</div>
            <div class="feat-desc">{{ $t('disable_bluetooth_dpm_desc') }}</div>
            <div class="feat-tag">{{ $t('requires_android_13') }}</div>
          </div>
          <div v-if="!status.isDeviceOwner" class="locked-badge">{{ $t('requires_device_owner') }}</div>
          <label v-else class="toggle-wrap">
            <input type="checkbox" class="toggle-input" :checked="status.bluetoothDisabled"
              @change="toggleBluetooth" :disabled="toggling.bluetooth" />
            <span class="toggle-track"><span class="toggle-thumb" /></span>
          </label>
        </div>
      </div>

      <!-- ── 6. USB Debugging ── -->
      <div class="feature-card" :class="{ locked: !status.isDeviceOwner }">
        <div class="feat-header">
          <div class="feat-icon-wrap" :class="{ blue: status.usbDebuggingEnabled }">
            <i-lucide:usb />
          </div>
          <div class="feat-info">
            <div class="feat-title">{{ $t('disable_usb_debugging') }}</div>
            <div class="feat-desc">{{ $t('disable_usb_debugging_desc') }}</div>
          </div>
          <div v-if="!status.isDeviceOwner" class="locked-badge">{{ $t('requires_device_owner') }}</div>
          <label v-else class="toggle-wrap">
            <input type="checkbox" class="toggle-input" :checked="!status.usbDebuggingEnabled"
              @change="toggleUsbDebugging" :disabled="toggling.usb" />
            <span class="toggle-track"><span class="toggle-thumb" /></span>
          </label>
        </div>
        <div v-if="status.isDeviceOwner && !status.usbDebuggingEnabled" class="feat-warning">
          <i-lucide:alert-triangle />
          USB debugging is OFF. You won't be able to use ADB until re-enabled.
        </div>
      </div>

      <!-- ── 7. Global Network Proxy ── -->
      <div class="feature-card wide-card" :class="{ locked: !status.isDeviceOwner }">
        <div class="feat-header">
          <div class="feat-icon-wrap" :class="{ purple: status.globalProxy }">
            <i-lucide:globe />
          </div>
          <div class="feat-info">
            <div class="feat-title">{{ $t('global_proxy') }}</div>
            <div class="feat-desc">{{ $t('global_proxy_desc') }}</div>
            <div v-if="status.globalProxy" class="feat-active-value">Active: {{ status.globalProxy }}</div>
          </div>
          <div v-if="!status.isDeviceOwner" class="locked-badge">{{ $t('requires_device_owner') }}</div>
        </div>
        <div v-if="status.isDeviceOwner" class="feat-body">
          <div class="proxy-row">
            <input v-model="proxyHost" :placeholder="$t('proxy_host_placeholder')" class="feat-input flex-1" />
            <input v-model="proxyPort" :placeholder="$t('proxy_port_placeholder')" class="feat-input w-24" type="number" min="1" max="65535" />
            <button class="action-btn" @click="setProxy" :disabled="!proxyHost || !proxyPort || toggling.proxy">
              <i-lucide:loader-2 v-if="toggling.proxy" class="spin" />
              {{ $t('set_proxy') }}
            </button>
            <button class="action-btn danger-btn" @click="clearProxy" :disabled="!status.globalProxy || toggling.proxy">
              {{ $t('clear_proxy') }}
            </button>
          </div>
        </div>
      </div>

      <!-- ── 8. Wipe After Failed Passwords ── -->
      <div class="feature-card wide-card" :class="{ locked: !status.isDeviceOwner }">
        <div class="feat-header">
          <div class="feat-icon-wrap red">
            <i-lucide:shield-alert />
          </div>
          <div class="feat-info">
            <div class="feat-title">{{ $t('failed_password_wipe') }}</div>
            <div class="feat-desc">{{ $t('failed_password_wipe_desc') }}</div>
            <div v-if="status.maxFailedPasswordsForWipe > 0" class="feat-active-value">
              Current: wipe after {{ status.maxFailedPasswordsForWipe }} failed attempts
            </div>
            <div v-else class="feat-active-value muted">Currently disabled (0)</div>
          </div>
          <div v-if="!status.isDeviceOwner" class="locked-badge">{{ $t('requires_device_owner') }}</div>
        </div>
        <div v-if="status.isDeviceOwner" class="feat-body">
          <div class="proxy-row">
            <input v-model.number="failedPwdCount" class="feat-input w-24" type="number" min="0" max="20" placeholder="0–20" />
            <button class="action-btn" @click="setFailedPwdWipe" :disabled="toggling.failedPwd">
              <i-lucide:loader-2 v-if="toggling.failedPwd" class="spin" />
              Apply
            </button>
          </div>
        </div>
      </div>

      <!-- ── 9. Hide App from Launcher ── -->
      <div class="feature-card wide-card" :class="{ locked: !status.isDeviceOwner }">
        <div class="feat-header">
          <div class="feat-icon-wrap">
            <i-lucide:eye-off />
          </div>
          <div class="feat-info">
            <div class="feat-title">{{ $t('hide_app') }}</div>
            <div class="feat-desc">{{ $t('hide_app_desc') }}</div>
          </div>
          <div v-if="!status.isDeviceOwner" class="locked-badge">{{ $t('requires_device_owner') }}</div>
        </div>
        <div v-if="status.isDeviceOwner" class="feat-body">
          <div class="proxy-row">
            <input v-model="hideAppPkg" :placeholder="$t('app_package_placeholder')" class="feat-input flex-1" />
            <button class="action-btn" @click="hideApp(true)" :disabled="!hideAppPkg || toggling.hideApp">Hide</button>
            <button class="action-btn" @click="hideApp(false)" :disabled="!hideAppPkg || toggling.hideApp">Unhide</button>
          </div>
          <div v-if="hideAppResult" class="grant-result ok"><i-lucide:check-circle /> {{ hideAppResult }}</div>
        </div>
      </div>

    </div>

    <!-- ═══════════════ DANGER ZONE ═══════════════ -->
    <div v-if="status?.isDeviceOwner" class="danger-zone">
      <div class="danger-header">
        <i-lucide:triangle-alert class="danger-icon" />
        <div>
          <h3 class="danger-title">Danger Zone</h3>
          <p class="danger-sub">These actions are irreversible. Read carefully before using.</p>
        </div>
      </div>

      <!-- Enterprise Wipe -->
      <div class="danger-card">
        <div class="danger-card-header">
          <i-lucide:trash-2 class="dc-icon" />
          <div>
            <div class="dc-title">{{ $t('enterprise_wipe') }}</div>
            <div class="dc-desc">{{ $t('enterprise_wipe_desc') }}</div>
          </div>
        </div>
        <div class="dc-body">
          <label class="check-row">
            <input type="checkbox" v-model="wipeExternal" />
            <span>{{ $t('wipe_external_storage') }}</span>
          </label>
          <label class="check-row">
            <input type="checkbox" v-model="wipeFrp" />
            <span>{{ $t('wipe_reset_protection') }}</span>
          </label>
          <div class="proxy-row">
            <input v-model="wipeConfirm" :placeholder="$t('wipe_confirm_placeholder')" class="feat-input flex-1" />
            <button class="action-btn danger-btn" @click="executeWipe" :disabled="wipeConfirm !== 'WIPE' || toggling.wipe">
              <i-lucide:trash-2 />
              Wipe Device
            </button>
          </div>
          <div class="danger-tip">{{ $t('wipe_confirm') }}</div>
        </div>
      </div>

      <!-- Remove Device Owner -->
      <div class="danger-card">
        <div class="danger-card-header">
          <i-lucide:user-x class="dc-icon" />
          <div>
            <div class="dc-title">{{ $t('clear_device_owner') }}</div>
            <div class="dc-desc">{{ $t('clear_device_owner_desc') }}</div>
            <div class="dc-adb">ADB alternative: <code>{{ $t('device_owner_remove_cmd') }}</code>
              <button class="copy-btn small" @click="copyText($t('device_owner_remove_cmd'), 'rmcmd')">{{ copied === 'rmcmd' ? '✅' : '📋' }}</button>
            </div>
          </div>
        </div>
        <div class="dc-body">
          <div class="proxy-row">
            <input v-model="removeConfirm" :placeholder="$t('clear_device_owner_confirm_placeholder')" class="feat-input flex-1" />
            <button class="action-btn danger-btn" @click="removeOwner" :disabled="removeConfirm !== 'REMOVE' || toggling.removeOwner">
              <i-lucide:user-x />
              Remove Device Owner
            </button>
          </div>
          <div class="danger-tip">{{ $t('clear_device_owner_confirm') }}</div>
        </div>
      </div>
    </div>

    <!-- Loading overlay -->
    <div v-if="loading && !status" class="do-loading">
      <i-lucide:loader-2 class="spin" />
      <span>Loading Device Owner status…</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import { deviceOwnerStatusGQL } from '@/lib/api/query'
import {
  grantAllPermissionsToSelfGQL,
  setUninstallBlockedGQL,
  setKioskModeGQL,
  setCameraDisabledDpmGQL,
  setBluetoothDisabledDpmGQL,
  setUsbDebuggingDisabledGQL,
  setGlobalProxyGQL,
  clearGlobalProxyGQL,
  setMaxFailedPasswordsForWipeGQL,
  setAppHiddenDpmGQL,
  wipeDeviceDpmGQL,
  clearDeviceOwnerGQL,
} from '@/lib/api/mutation'
import emitter from '@/plugins/eventbus'

interface DeviceOwnerStatus {
  isDeviceOwner: boolean
  isDeviceAdmin: boolean
  uninstallBlocked: boolean
  kioskEnabled: boolean
  cameraDisabled: boolean
  bluetoothDisabled: boolean
  usbDebuggingEnabled: boolean
  globalProxy: string
  maxFailedPasswordsForWipe: number
  grantablePermissionsCount: number
  alreadyGrantedCount: number
}

interface GrantResult {
  granted: string[]
  failed: string[]
  skipped: string[]
  success: boolean
}

const status = ref<DeviceOwnerStatus | null>(null)
const loading = ref(false)
const grantingPerms = ref(false)
const grantResult = ref<GrantResult | null>(null)
const copied = ref('')
const proxyHost = ref('')
const proxyPort = ref<number | null>(null)
const failedPwdCount = ref(0)
const hideAppPkg = ref('')
const hideAppResult = ref('')
const wipeExternal = ref(false)
const wipeFrp = ref(false)
const wipeConfirm = ref('')
const removeConfirm = ref('')

const toggling = ref({
  uninstall: false, kiosk: false, camera: false, bluetooth: false,
  usb: false, proxy: false, failedPwd: false, hideApp: false,
  wipe: false, removeOwner: false,
})

const permPct = computed(() => {
  if (!status.value || status.value.grantablePermissionsCount === 0) return 0
  return Math.round((status.value.alreadyGrantedCount / status.value.grantablePermissionsCount) * 100)
})

async function loadStatus() {
  loading.value = true
  try {
    const r = await gqlFetch<{ deviceOwnerStatus: DeviceOwnerStatus }>(deviceOwnerStatusGQL)
    status.value = r?.data?.deviceOwnerStatus ?? null
    if (status.value) {
      failedPwdCount.value = status.value.maxFailedPasswordsForWipe
    }
  } finally {
    loading.value = false
  }
}

async function grantAllPerms() {
  grantingPerms.value = true
  grantResult.value = null
  try {
    const r = await gqlFetch<{ grantAllPermissionsToSelf: GrantResult }>(grantAllPermissionsToSelfGQL)
    grantResult.value = r?.data?.grantAllPermissionsToSelf ?? null
    await loadStatus()
    if (grantResult.value?.success) emitter.emit('toast', 'All permissions granted!')
  } finally {
    grantingPerms.value = false
  }
}

async function toggleUninstallBlock(e: Event) {
  const checked = (e.target as HTMLInputElement).checked
  toggling.value.uninstall = true
  try {
    await gqlFetch(setUninstallBlockedGQL, { blocked: checked })
    await loadStatus()
  } finally { toggling.value.uninstall = false }
}

async function toggleKiosk(e: Event) {
  const checked = (e.target as HTMLInputElement).checked
  toggling.value.kiosk = true
  try {
    await gqlFetch(setKioskModeGQL, { enabled: checked })
    await loadStatus()
  } finally { toggling.value.kiosk = false }
}

async function toggleCamera(e: Event) {
  const checked = (e.target as HTMLInputElement).checked
  toggling.value.camera = true
  try {
    await gqlFetch(setCameraDisabledDpmGQL, { disabled: checked })
    await loadStatus()
  } finally { toggling.value.camera = false }
}

async function toggleBluetooth(e: Event) {
  const checked = (e.target as HTMLInputElement).checked
  toggling.value.bluetooth = true
  try {
    await gqlFetch(setBluetoothDisabledDpmGQL, { disabled: checked })
    await loadStatus()
  } finally { toggling.value.bluetooth = false }
}

async function toggleUsbDebugging(e: Event) {
  const disabled = (e.target as HTMLInputElement).checked
  toggling.value.usb = true
  try {
    await gqlFetch(setUsbDebuggingDisabledGQL, { disabled })
    await loadStatus()
  } finally { toggling.value.usb = false }
}

async function setProxy() {
  if (!proxyHost.value || !proxyPort.value) return
  toggling.value.proxy = true
  try {
    await gqlFetch(setGlobalProxyGQL, { host: proxyHost.value, port: proxyPort.value })
    await loadStatus()
    emitter.emit('toast', 'Proxy set successfully')
  } finally { toggling.value.proxy = false }
}

async function clearProxy() {
  toggling.value.proxy = true
  try {
    await gqlFetch(clearGlobalProxyGQL)
    proxyHost.value = ''
    proxyPort.value = null
    await loadStatus()
    emitter.emit('toast', 'Proxy cleared')
  } finally { toggling.value.proxy = false }
}

async function setFailedPwdWipe() {
  toggling.value.failedPwd = true
  try {
    await gqlFetch(setMaxFailedPasswordsForWipeGQL, { count: failedPwdCount.value })
    await loadStatus()
    emitter.emit('toast', `Set to ${failedPwdCount.value} failed attempts`)
  } finally { toggling.value.failedPwd = false }
}

async function hideApp(hidden: boolean) {
  if (!hideAppPkg.value) return
  toggling.value.hideApp = true
  try {
    const r = await gqlFetch<{ setAppHiddenDpm: boolean }>(setAppHiddenDpmGQL, {
      packageName: hideAppPkg.value, hidden,
    })
    const ok = r?.data?.setAppHiddenDpm
    hideAppResult.value = ok ? `${hideAppPkg.value} ${hidden ? 'hidden' : 'unhidden'} successfully` : 'Failed — app may not exist or already in that state'
    setTimeout(() => { hideAppResult.value = '' }, 4000)
  } finally { toggling.value.hideApp = false }
}

async function executeWipe() {
  if (wipeConfirm.value !== 'WIPE') return
  toggling.value.wipe = true
  try {
    await gqlFetch(wipeDeviceDpmGQL, { wipeExternalStorage: wipeExternal.value, wipeResetProtection: wipeFrp.value })
  } finally { toggling.value.wipe = false }
}

async function removeOwner() {
  if (removeConfirm.value !== 'REMOVE') return
  toggling.value.removeOwner = true
  try {
    await gqlFetch(clearDeviceOwnerGQL)
    await loadStatus()
    removeConfirm.value = ''
    emitter.emit('toast', 'Device Owner removed')
  } finally { toggling.value.removeOwner = false }
}

async function copyText(text: string, key: string) {
  try { await navigator.clipboard.writeText(text) } catch {
    const el = document.createElement('textarea')
    el.value = text; document.body.appendChild(el); el.select()
    document.execCommand('copy'); document.body.removeChild(el)
  }
  copied.value = key
  setTimeout(() => { if (copied.value === key) copied.value = '' }, 2000)
}

onMounted(loadStatus)
</script>

<style scoped>
.do-root { display: flex; flex-direction: column; gap: 20px; padding: 18px; max-width: 1100px; margin: 0 auto; }

/* Header */
.do-header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; }
.do-title-block { display: flex; align-items: center; gap: 14px; }
.do-icon-wrap {
  width: 52px; height: 52px; border-radius: 16px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  background: rgba(220,38,38,0.12); color: #dc2626;
}
.do-icon-wrap.active { background: rgba(22,163,74,0.12); color: #16a34a; }
.do-icon-wrap svg { width: 28px; height: 28px; }
.do-title { margin: 0; font-size: 1.5rem; font-weight: 700; }
.do-sub { margin: 3px 0 0; color: var(--md-sys-color-on-surface-variant); font-size: 0.88rem; }
.do-header-actions { display: flex; gap: 8px; }

/* Status banner */
.status-banner {
  display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px;
  padding: 16px 20px; border-radius: 16px; border: 2px solid;
}
.status-banner.active { background: rgba(22,163,74,0.06); border-color: rgba(22,163,74,0.3); }
.status-banner.inactive { background: rgba(220,38,38,0.06); border-color: rgba(220,38,38,0.25); }
.status-left { display: flex; align-items: center; gap: 12px; }
.status-dot { width: 12px; height: 12px; border-radius: 50%; flex-shrink: 0; }
.status-dot.green { background: #16a34a; box-shadow: 0 0 8px #16a34a80; animation: pulse-g 2s infinite; }
.status-dot.red { background: #dc2626; }
@keyframes pulse-g { 0%,100% { box-shadow: 0 0 4px #16a34a80; } 50% { box-shadow: 0 0 12px #16a34aaa; } }
.status-label { font-weight: 700; font-size: 1rem; }
.status-sub { font-size: 0.82rem; color: var(--md-sys-color-on-surface-variant); margin-top: 2px; }
.status-chips { display: flex; gap: 8px; flex-wrap: wrap; }
.chip {
  display: inline-flex; align-items: center; gap: 5px; padding: 4px 12px; border-radius: 999px; font-size: 0.75rem; font-weight: 600;
  background: var(--md-sys-color-surface-container); border: 1px solid var(--md-sys-color-outline-variant);
  color: var(--md-sys-color-on-surface-variant);
}
.chip svg { width: 12px; height: 12px; }
.chip.on { background: rgba(22,163,74,0.12); border-color: rgba(22,163,74,0.3); color: #16a34a; }
.chip.warn { background: rgba(220,38,38,0.12); border-color: rgba(220,38,38,0.25); color: #dc2626; }

/* Setup card */
.setup-card {
  padding: 20px; border-radius: 18px;
  background: var(--md-sys-color-surface-container);
  border: 2px solid rgba(245,158,11,0.3);
}
.setup-header { display: flex; align-items: flex-start; gap: 14px; margin-bottom: 16px; }
.setup-icon { width: 28px; height: 28px; color: #f59e0b; flex-shrink: 0; margin-top: 2px; }
.setup-title { margin: 0 0 4px; font-size: 1.05rem; font-weight: 700; }
.setup-desc { margin: 0; font-size: 0.83rem; color: var(--md-sys-color-on-surface-variant); }
.setup-steps { display: flex; flex-direction: column; gap: 12px; }
.setup-step-item { display: flex; align-items: flex-start; gap: 12px; }
.step-num {
  width: 26px; height: 26px; border-radius: 50%; flex-shrink: 0; font-size: 0.75rem; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  background: var(--md-sys-color-primary); color: var(--md-sys-color-on-primary);
}
.step-body { flex: 1; }
.step-label { font-weight: 600; font-size: 0.88rem; margin-bottom: 4px; }
.step-sub { font-size: 0.8rem; color: var(--md-sys-color-on-surface-variant); }

/* Features grid */
.features-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 14px; }
.wide-card { grid-column: 1 / -1; }

/* Feature card */
.feature-card {
  display: flex; flex-direction: column; gap: 12px; padding: 16px; border-radius: 16px;
  background: var(--md-sys-color-surface-container);
  border: 1px solid var(--md-sys-color-outline-variant);
  transition: all 0.2s;
}
.feature-card:hover { background: var(--md-sys-color-surface-container-high); }
.feature-card.locked { opacity: 0.6; }
.feature-card.highlight-card { border: 2px solid rgba(22,163,74,0.35); background: rgba(22,163,74,0.04); }

.feat-header { display: flex; align-items: flex-start; gap: 12px; }
.feat-icon-wrap {
  width: 40px; height: 40px; border-radius: 12px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  background: var(--md-sys-color-surface-container-high); color: var(--md-sys-color-on-surface-variant);
}
.feat-icon-wrap svg { width: 20px; height: 20px; }
.feat-icon-wrap.green { background: rgba(22,163,74,0.12); color: #16a34a; }
.feat-icon-wrap.blue { background: rgba(59,130,246,0.12); color: #3b82f6; }
.feat-icon-wrap.red { background: rgba(220,38,38,0.12); color: #dc2626; }
.feat-icon-wrap.orange { background: rgba(245,158,11,0.12); color: #d97706; }
.feat-icon-wrap.purple { background: rgba(139,92,246,0.12); color: #7c3aed; }

.feat-info { flex: 1; min-width: 0; }
.feat-title { font-weight: 600; font-size: 0.9rem; margin-bottom: 3px; }
.feat-desc { font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); line-height: 1.45; }
.feat-tag { font-size: 0.68rem; font-weight: 600; color: #7c3aed; margin-top: 4px; }
.feat-active-value { font-size: 0.75rem; font-weight: 600; color: var(--md-sys-color-primary); margin-top: 4px; }
.feat-active-value.muted { color: var(--md-sys-color-on-surface-variant); font-weight: 400; }

.locked-badge {
  padding: 3px 10px; border-radius: 999px; font-size: 0.7rem; font-weight: 600; white-space: nowrap;
  background: var(--md-sys-color-surface-container-highest); color: var(--md-sys-color-on-surface-variant);
  border: 1px solid var(--md-sys-color-outline-variant);
}

.feat-body { display: flex; flex-direction: column; gap: 10px; }
.feat-warning {
  display: flex; align-items: flex-start; gap: 7px; padding: 9px 12px; border-radius: 10px;
  background: rgba(245,158,11,0.1); border: 1px solid rgba(245,158,11,0.3);
  color: #92400e; font-size: 0.78rem; line-height: 1.45;
}
.feat-warning svg { width: 14px; height: 14px; flex-shrink: 0; margin-top: 1px; }

/* Toggle */
.toggle-wrap { flex-shrink: 0; cursor: pointer; }
.toggle-input { display: none; }
.toggle-track {
  display: block; width: 44px; height: 24px; border-radius: 999px;
  background: var(--md-sys-color-surface-container-highest);
  border: 2px solid var(--md-sys-color-outline-variant);
  position: relative; transition: all 0.2s; cursor: pointer;
}
.toggle-input:checked + .toggle-track { background: var(--md-sys-color-primary); border-color: var(--md-sys-color-primary); }
.toggle-thumb {
  display: block; width: 16px; height: 16px; border-radius: 50%;
  background: var(--md-sys-color-outline); position: absolute; top: 2px; left: 2px; transition: all 0.2s;
}
.toggle-input:checked + .toggle-track .toggle-thumb { transform: translateX(20px); background: var(--md-sys-color-on-primary); }

/* Permission progress */
.perm-progress { display: flex; align-items: center; gap: 12px; }
.prog-bar { flex: 1; height: 8px; border-radius: 999px; overflow: hidden; background: var(--md-sys-color-surface-container-highest); }
.prog-fill { height: 100%; border-radius: 999px; background: linear-gradient(90deg, #16a34a, #22d3ee); transition: width 0.4s ease; }
.prog-lbl { font-size: 0.78rem; font-weight: 600; white-space: nowrap; color: var(--md-sys-color-on-surface-variant); }

/* Grant result */
.grant-result {
  display: flex; align-items: center; gap: 8px; padding: 10px 14px; border-radius: 10px; font-size: 0.82rem; font-weight: 600;
}
.grant-result svg { width: 16px; height: 16px; flex-shrink: 0; }
.grant-result.ok { background: rgba(22,163,74,0.1); color: #16a34a; border: 1px solid rgba(22,163,74,0.25); }
.grant-result.partial { background: rgba(245,158,11,0.1); color: #92400e; border: 1px solid rgba(245,158,11,0.3); }

/* Action buttons */
.action-btn {
  display: inline-flex; align-items: center; gap: 6px; padding: 8px 16px; border-radius: 999px;
  border: none; cursor: pointer; font-size: 0.83rem; font-weight: 600;
  background: var(--md-sys-color-primary); color: var(--md-sys-color-on-primary); transition: opacity 0.2s;
}
.action-btn:hover:not(:disabled) { opacity: 0.88; }
.action-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.action-btn svg { width: 14px; height: 14px; }
.green-btn { background: #16a34a; }
.danger-btn { background: #dc2626; }

/* Proxy / input rows */
.proxy-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.feat-input {
  padding: 8px 12px; border-radius: 10px; font-size: 0.83rem;
  background: var(--md-sys-color-surface); border: 1px solid var(--md-sys-color-outline-variant);
  color: var(--md-sys-color-on-surface); outline: none;
}
.feat-input:focus { border-color: var(--md-sys-color-primary); }
.flex-1 { flex: 1; min-width: 140px; }
.w-24 { width: 96px; }

/* Cmd row */
.cmd-row {
  display: flex; align-items: center; gap: 8px; padding: 8px 10px; border-radius: 8px;
  background: #0f172a; border: 1px solid #1e3a5f; margin-top: 6px;
}
.cmd-text {
  flex: 1; font-family: ui-monospace,'Cascadia Code',monospace; font-size: 0.72rem; color: #7dd3fc;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}

/* Copy btn */
.copy-btn {
  padding: 5px 12px; border-radius: 999px; border: none; cursor: pointer;
  background: var(--md-sys-color-primary); color: var(--md-sys-color-on-primary); font-size: 0.75rem; font-weight: 600;
}
.copy-btn.small { padding: 3px 8px; font-size: 0.68rem; }
.copy-btn:hover { opacity: 0.88; }

/* Danger zone */
.danger-zone {
  display: flex; flex-direction: column; gap: 14px; padding: 20px; border-radius: 18px;
  background: rgba(220,38,38,0.04); border: 2px solid rgba(220,38,38,0.25);
}
.danger-header { display: flex; align-items: flex-start; gap: 14px; margin-bottom: 4px; }
.danger-icon { width: 26px; height: 26px; color: #dc2626; flex-shrink: 0; margin-top: 2px; }
.danger-title { margin: 0 0 4px; font-size: 1rem; font-weight: 700; color: #dc2626; }
.danger-sub { margin: 0; font-size: 0.82rem; color: var(--md-sys-color-on-surface-variant); }

.danger-card {
  display: flex; flex-direction: column; gap: 12px; padding: 16px; border-radius: 14px;
  background: var(--md-sys-color-surface-container); border: 1px solid rgba(220,38,38,0.2);
}
.danger-card-header { display: flex; align-items: flex-start; gap: 12px; }
.dc-icon { width: 22px; height: 22px; color: #dc2626; flex-shrink: 0; margin-top: 2px; }
.dc-title { font-weight: 700; font-size: 0.9rem; }
.dc-desc { font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); margin-top: 3px; line-height: 1.45; }
.dc-adb { font-size: 0.72rem; margin-top: 6px; color: var(--md-sys-color-on-surface-variant); display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.dc-adb code { font-family: ui-monospace,monospace; background: var(--md-sys-color-surface-container-highest); padding: 2px 6px; border-radius: 4px; }
.dc-body { display: flex; flex-direction: column; gap: 10px; }
.check-row { display: flex; align-items: center; gap: 8px; font-size: 0.83rem; cursor: pointer; }
.danger-tip { font-size: 0.75rem; color: var(--md-sys-color-on-surface-variant); font-style: italic; }

/* Ghost btn */
.ghost-btn {
  display: inline-flex; align-items: center; gap: 6px; padding: 8px 14px; border-radius: 999px;
  background: var(--md-sys-color-surface-container); border: 1px solid var(--md-sys-color-outline-variant);
  color: inherit; cursor: pointer; font-weight: 500; font-size: 0.85rem;
}
.ghost-btn:hover { background: var(--md-sys-color-surface-container-high); }
.ghost-btn svg { width: 14px; height: 14px; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* Loading */
.do-loading { display: flex; align-items: center; justify-content: center; gap: 10px; padding: 60px; color: var(--md-sys-color-on-surface-variant); font-size: 0.9rem; }
.do-loading svg { width: 24px; height: 24px; }
</style>
