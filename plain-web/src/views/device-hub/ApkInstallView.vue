<template>
  <div class="apk-root">
    <header class="apk-header">
      <button class="back-btn" @click="$router.back()">
        <i-lucide:arrow-left />
      </button>
      <div class="title-block">
        <div class="header-icon-wrap">
          <i-lucide:package-open class="header-icon" />
        </div>
        <div>
          <h2 class="hdr-title">APK Installer</h2>
          <p class="hdr-sub">Install or update apps remotely from your browser</p>
        </div>
      </div>
      <div class="owner-badge" :class="{ active: deviceOwnerKnown && isDeviceOwner }">
        <i-lucide:shield-check v-if="isDeviceOwner" />
        <i-lucide:shield-alert v-else />
        {{ isDeviceOwner ? 'Zero-touch (Device Owner)' : 'Tap-to-install mode' }}
      </div>
    </header>

    <!-- Device Owner info banner -->
    <div v-if="!isDeviceOwner && deviceOwnerKnown" class="info-banner">
      <i-lucide:info class="banner-icon" />
      <div>
        <strong>Want fully silent installs?</strong>
        Run once via ADB to become Device Owner — then every install happens without any tap on the device:
        <code class="inline-code">adb shell dpm set-device-owner com.ismartcoding.plain/.receivers.PlainDeviceAdminReceiver</code>
        <button class="copy-btn-small" @click="copyCmd">{{ cmdCopied ? '✅ Copied' : '📋 Copy' }}</button>
      </div>
    </div>

    <!-- Method tabs -->
    <div class="method-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        class="method-tab"
        :class="{ active: activeTab === tab.id }"
        @click="activeTab = tab.id"
      >
        <component :is="tab.icon" class="tab-icon" />
        {{ tab.label }}
      </button>
    </div>

    <!-- ═══ TAB 1: Browser Upload ═══ -->
    <div v-if="activeTab === 'upload'" class="method-card upload-card">
      <div class="method-hdr">
        <i-lucide:upload-cloud class="method-icon" />
        <div>
          <h3 class="method-title">Upload from Browser</h3>
          <p class="method-desc">Select or drag-and-drop an .apk file from your computer. It streams directly to the device and installs.</p>
        </div>
      </div>

      <div
        class="drop-zone"
        :class="{ dragging: isDragging, 'has-file': uploadFile !== null }"
        @dragenter.prevent="isDragging = true"
        @dragover.prevent="isDragging = true"
        @dragleave.prevent="isDragging = false"
        @drop.prevent="onDrop"
        @click="triggerFileInput"
      >
        <input
          ref="fileInput"
          type="file"
          accept=".apk"
          class="hidden-input"
          @change="onFileChange"
        />
        <div v-if="!uploadFile" class="drop-inner">
          <i-lucide:package-open class="drop-icon" />
          <p class="drop-label">Drop APK here or <span class="drop-link">click to browse</span></p>
          <p class="drop-hint">Only .apk files accepted</p>
        </div>
        <div v-else class="file-info">
          <i-lucide:file-check class="file-icon" />
          <div>
            <p class="file-name">{{ uploadFile.name }}</p>
            <p class="file-size">{{ formatBytes(uploadFile.size) }}</p>
          </div>
          <button class="clear-btn" @click.stop="clearFile"><i-lucide:x /></button>
        </div>
      </div>

      <!-- Upload progress -->
      <div v-if="uploadProgress > 0 && uploadProgress < 100" class="progress-wrap">
        <div class="progress-bar-bg">
          <div class="progress-bar-fill" :style="{ width: uploadProgress + '%' }" />
        </div>
        <span class="progress-pct">{{ uploadProgress }}%</span>
      </div>

      <!-- Result -->
      <div v-if="uploadResult" class="result-box" :class="{ success: !uploadResult.error, error: !!uploadResult.error }">
        <i-lucide:check-circle v-if="!uploadResult.error" />
        <i-lucide:alert-circle v-else />
        <div>
          <p class="result-title">{{ uploadResult.error ? 'Upload failed' : (uploadResult.deviceOwner ? 'Silent install triggered' : 'Install dialog opened') }}</p>
          <p class="result-sub">{{ uploadResult.error || (uploadResult.deviceOwner ? 'No tap required — PlainApp is Device Owner.' : 'Tap "Install" on the device to confirm.') }}</p>
        </div>
      </div>

      <button
        class="install-btn"
        :disabled="!uploadFile || uploading"
        @click="doUpload"
      >
        <i-lucide:loader-2 v-if="uploading" class="spin" />
        <i-lucide:upload v-else />
        {{ uploading ? 'Uploading…' : 'Install APK' }}
      </button>
    </div>

    <!-- ═══ TAB 2: URL Download ═══ -->
    <div v-if="activeTab === 'url'" class="method-card url-card">
      <div class="method-hdr">
        <i-lucide:link class="method-icon" />
        <div>
          <h3 class="method-title">Install from URL</h3>
          <p class="method-desc">Enter a direct HTTPS link to an .apk file. The phone downloads it in the background and installs automatically.</p>
        </div>
      </div>

      <div class="url-input-wrap">
        <i-lucide:link class="url-icon" />
        <input
          v-model="apkUrl"
          type="url"
          class="url-input"
          placeholder="https://example.com/app.apk"
          @keydown.enter="doUrlInstall"
        />
      </div>

      <div v-if="urlResult" class="result-box" :class="{ success: !urlResult.error, error: !!urlResult.error }">
        <i-lucide:check-circle v-if="!urlResult.error" />
        <i-lucide:alert-circle v-else />
        <div>
          <p class="result-title">{{ urlResult.error ? 'Download/install failed' : (urlResult.deviceOwner ? 'Silent install triggered' : 'Install dialog opened') }}</p>
          <p class="result-sub">{{ urlResult.error || (urlResult.deviceOwner ? 'No tap required — PlainApp is Device Owner.' : 'Tap "Install" on the device to confirm.') }}</p>
        </div>
      </div>

      <button
        class="install-btn"
        :disabled="!apkUrl.trim() || urlLoading"
        @click="doUrlInstall"
      >
        <i-lucide:loader-2 v-if="urlLoading" class="spin" />
        <i-lucide:download v-else />
        {{ urlLoading ? 'Downloading & installing…' : 'Download & Install' }}
      </button>

      <p class="url-note">
        <i-lucide:alert-triangle />
        Telegram Bot API caps at 20 MB for file send. Use this for larger APKs.
      </p>
    </div>

    <!-- ═══ TAB 3: From Storage ═══ -->
    <div v-if="activeTab === 'storage'" class="method-card storage-card">
      <div class="method-hdr">
        <i-lucide:hard-drive class="method-icon" />
        <div>
          <h3 class="method-title">From Device Storage</h3>
          <p class="method-desc">Enter the full path to an APK already on the device, or browse common download locations.</p>
        </div>
      </div>

      <div class="path-input-wrap">
        <i-lucide:folder-open class="path-icon" />
        <input
          v-model="storagePath"
          type="text"
          class="path-input"
          placeholder="/storage/emulated/0/Download/app.apk"
          @keydown.enter="doStorageInstall"
        />
      </div>

      <!-- Quick-pick common paths -->
      <div class="quick-paths">
        <p class="qp-label"><i-lucide:bookmark /> Common locations — click to prefill:</p>
        <div class="qp-chips">
          <button
            v-for="p in commonPaths"
            :key="p"
            class="qp-chip"
            @click="storagePath = p"
          >{{ p }}</button>
        </div>
      </div>

      <div v-if="storageResult" class="result-box" :class="{ success: !storageResult.error, error: !!storageResult.error }">
        <i-lucide:check-circle v-if="!storageResult.error" />
        <i-lucide:alert-circle v-else />
        <div>
          <p class="result-title">{{ storageResult.error ? 'Install failed' : (storageResult.deviceOwner ? 'Silent install triggered' : 'Install dialog opened') }}</p>
          <p class="result-sub">{{ storageResult.error || (storageResult.deviceOwner ? 'No tap required — PlainApp is Device Owner.' : 'Tap "Install" on the device to confirm.') }}</p>
        </div>
      </div>

      <button
        class="install-btn"
        :disabled="!storagePath.trim() || storageLoading"
        @click="doStorageInstall"
      >
        <i-lucide:loader-2 v-if="storageLoading" class="spin" />
        <i-lucide:package-check v-else />
        {{ storageLoading ? 'Installing…' : 'Install from Path' }}
      </button>
    </div>

    <!-- Install history note -->
    <div class="install-note">
      <i-lucide:info class="note-icon" />
      <span>APK must be signed with the same key as the currently installed version (debug APK on debug install, release on release). Downgrades are blocked by Android.</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import { getApiBaseUrl } from '@/lib/api/api'
import { installPackageGQL, installPackageFromUrlGQL } from '@/lib/api/mutation'
import ILucideUploadCloud from '~icons/lucide/upload-cloud'
import ILucideLink from '~icons/lucide/link'
import ILucideHardDrive from '~icons/lucide/hard-drive'

interface InstallResult {
  error?: string
  deviceOwner?: boolean
}

const tabs = [
  { id: 'upload', label: 'Upload APK', icon: ILucideUploadCloud },
  { id: 'url', label: 'From URL', icon: ILucideLink },
  { id: 'storage', label: 'From Storage', icon: ILucideHardDrive },
]

const activeTab = ref<'upload' | 'url' | 'storage'>('upload')

const isDeviceOwner = ref(false)
const deviceOwnerKnown = ref(false)
const cmdCopied = ref(false)

const fileInput = ref<HTMLInputElement | null>(null)
const isDragging = ref(false)
const uploadFile = ref<File | null>(null)
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadResult = ref<InstallResult | null>(null)

const apkUrl = ref('')
const urlLoading = ref(false)
const urlResult = ref<InstallResult | null>(null)

const storagePath = ref('')
const storageLoading = ref(false)
const storageResult = ref<InstallResult | null>(null)

const commonPaths = [
  '/storage/emulated/0/Download/',
  '/sdcard/Download/',
  '/storage/emulated/0/',
]

function formatBytes(n: number): string {
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  if (n < 1024 * 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + ' MB'
  return (n / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

function triggerFileInput() { fileInput.value?.click() }

function onDrop(e: DragEvent) {
  isDragging.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file && file.name.toLowerCase().endsWith('.apk')) {
    uploadFile.value = file
    uploadResult.value = null
  }
}

function onFileChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (file) {
    uploadFile.value = file
    uploadResult.value = null
  }
}

function clearFile() {
  uploadFile.value = null
  uploadResult.value = null
  uploadProgress.value = 0
  if (fileInput.value) fileInput.value.value = ''
}

async function doUpload() {
  if (!uploadFile.value || uploading.value) return
  uploading.value = true
  uploadProgress.value = 0
  uploadResult.value = null
  try {
    const formData = new FormData()
    formData.append('file', uploadFile.value)
    const clientId = localStorage.getItem('client_id') ?? ''
    await new Promise<void>((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      xhr.upload.addEventListener('progress', (e) => {
        if (e.lengthComputable) {
          uploadProgress.value = Math.round((e.loaded / e.total) * 100)
        }
      })
      xhr.addEventListener('load', () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          try {
            const res = JSON.parse(xhr.responseText)
            isDeviceOwner.value = res.deviceOwner ?? false
            deviceOwnerKnown.value = true
            uploadResult.value = { deviceOwner: res.deviceOwner ?? false }
          } catch {
            uploadResult.value = { deviceOwner: false }
          }
          resolve()
        } else {
          reject(new Error(xhr.responseText || `HTTP ${xhr.status}`))
        }
      })
      xhr.addEventListener('error', () => reject(new Error('Network error')))
      xhr.open('POST', `${getApiBaseUrl()}/apk_upload`, true)
      xhr.setRequestHeader('c-id', clientId)
      xhr.send(formData)
    })
  } catch (err: any) {
    uploadResult.value = { error: err?.message ?? 'Upload failed' }
  } finally {
    uploading.value = false
    uploadProgress.value = 0
  }
}

async function doUrlInstall() {
  const url = apkUrl.value.trim()
  if (!url || urlLoading.value) return
  urlLoading.value = true
  urlResult.value = null
  try {
    const res = await gqlFetch<{ installPackageFromUrl: boolean }>(installPackageFromUrlGQL, { url })
    if (res.errors?.length) throw new Error(res.errors[0].message)
    const owner = res.data?.installPackageFromUrl ?? false
    isDeviceOwner.value = owner
    deviceOwnerKnown.value = true
    urlResult.value = { deviceOwner: owner }
  } catch (err: any) {
    urlResult.value = { error: err?.message ?? 'Failed' }
  } finally {
    urlLoading.value = false
  }
}

async function doStorageInstall() {
  const path = storagePath.value.trim()
  if (!path || storageLoading.value) return
  storageLoading.value = true
  storageResult.value = null
  try {
    const res = await gqlFetch<{ installPackage: { packageName: string } }>(installPackageGQL, { path })
    if (res.errors?.length) throw new Error(res.errors[0].message)
    storageResult.value = { deviceOwner: isDeviceOwner.value }
  } catch (err: any) {
    storageResult.value = { error: err?.message ?? 'Failed' }
  } finally {
    storageLoading.value = false
  }
}

async function copyCmd() {
  const cmd = 'adb shell dpm set-device-owner com.ismartcoding.plain/.receivers.PlainDeviceAdminReceiver'
  try { await navigator.clipboard.writeText(cmd) } catch { /* noop */ }
  cmdCopied.value = true
  setTimeout(() => { cmdCopied.value = false }, 2000)
}

onMounted(() => {
  deviceOwnerKnown.value = false
})
</script>

<style scoped lang="scss">
$accent: #f97316;

.apk-root {
  padding: 18px 22px 40px;
  max-width: 860px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.apk-header {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}
.back-btn {
  display: flex; align-items: center; justify-content: center;
  width: 36px; height: 36px; border-radius: 50%;
  background: var(--md-sys-color-surface-container);
  border: 1px solid var(--md-sys-color-outline-variant);
  cursor: pointer; color: inherit; flex-shrink: 0;
  svg { width: 18px; height: 18px; }
}
.back-btn:hover { background: var(--md-sys-color-surface-container-high); }

.title-block { display: flex; align-items: center; gap: 14px; flex: 1; }
.header-icon-wrap {
  width: 52px; height: 52px; border-radius: 16px;
  display: flex; align-items: center; justify-content: center;
  background: rgba(249,115,22,0.12); color: $accent; flex-shrink: 0;
}
.header-icon { width: 28px; height: 28px; }
.hdr-title { margin: 0; font-size: 1.4rem; font-weight: 700; }
.hdr-sub { margin: 3px 0 0; color: var(--md-sys-color-on-surface-variant); font-size: 0.87rem; }

.owner-badge {
  display: inline-flex; align-items: center; gap: 7px;
  padding: 7px 16px; border-radius: 999px;
  font-size: 0.8rem; font-weight: 600;
  background: rgba(245,158,11,0.12); color: #b45309;
  border: 1px solid rgba(245,158,11,0.25);
  svg { width: 16px; height: 16px; }
  &.active { background: rgba(34,197,94,0.12); color: #047857; border-color: rgba(34,197,94,0.3); }
}

.info-banner {
  display: flex; align-items: flex-start; gap: 12px;
  padding: 14px 18px; border-radius: 14px;
  background: rgba(59,130,246,0.06);
  border: 1px solid rgba(59,130,246,0.2);
  font-size: 0.85rem; color: var(--md-sys-color-on-surface-variant); line-height: 1.6;
  strong { color: var(--md-sys-color-on-surface); }
}
.banner-icon { width: 18px; height: 18px; color: #3b82f6; flex-shrink: 0; margin-top: 2px; }
.inline-code {
  display: inline-block; margin: 4px 6px 4px 0;
  padding: 2px 8px; border-radius: 5px;
  font-family: ui-monospace,monospace; font-size: 0.77rem;
  background: #0f172a; color: #7dd3fc;
}
.copy-btn-small {
  padding: 3px 10px; border-radius: 999px; border: none; cursor: pointer;
  background: var(--md-sys-color-primary); color: var(--md-sys-color-on-primary);
  font-size: 0.75rem; font-weight: 600;
}

/* Method tabs */
.method-tabs {
  display: flex; gap: 8px; flex-wrap: wrap;
}
.method-tab {
  display: inline-flex; align-items: center; gap: 7px;
  padding: 9px 20px; border-radius: 999px;
  font-size: 0.88rem; font-weight: 600;
  background: var(--md-sys-color-surface-container);
  border: 1px solid var(--md-sys-color-outline-variant);
  color: var(--md-sys-color-on-surface-variant);
  cursor: pointer; transition: all 0.15s;
  &.active {
    background: $accent; color: #fff; border-color: transparent;
    box-shadow: 0 4px 12px rgba(249,115,22,0.28);
  }
  &:not(.active):hover { background: var(--md-sys-color-surface-container-high); }
}
.tab-icon { width: 16px; height: 16px; }

/* Method cards */
.method-card {
  border-radius: 20px;
  background: var(--md-sys-color-surface-container);
  border: 1px solid var(--md-sys-color-outline-variant);
  padding: 24px;
  display: flex; flex-direction: column; gap: 18px;
}
.method-hdr { display: flex; align-items: flex-start; gap: 14px; }
.method-icon { width: 26px; height: 26px; color: $accent; flex-shrink: 0; margin-top: 2px; }
.method-title { margin: 0 0 4px; font-size: 1.05rem; font-weight: 700; }
.method-desc { margin: 0; font-size: 0.83rem; color: var(--md-sys-color-on-surface-variant); line-height: 1.55; }

/* Drop zone */
.drop-zone {
  position: relative;
  border: 2px dashed var(--md-sys-color-outline-variant);
  border-radius: 16px;
  min-height: 140px;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; transition: all 0.2s;
  background: var(--md-sys-color-surface);
  &.dragging { border-color: $accent; background: rgba(249,115,22,0.06); }
  &.has-file { border-style: solid; border-color: #22c55e; background: rgba(34,197,94,0.04); }
  &:hover { border-color: $accent; background: rgba(249,115,22,0.04); }
}
.hidden-input { position: absolute; inset: 0; opacity: 0; cursor: pointer; width: 100%; height: 100%; }
.drop-inner { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 20px; text-align: center; }
.drop-icon { width: 40px; height: 40px; color: $accent; }
.drop-label { margin: 0; font-size: 0.92rem; font-weight: 600; }
.drop-link { color: $accent; text-decoration: underline; }
.drop-hint { margin: 0; font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); }

.file-info {
  display: flex; align-items: center; gap: 14px; padding: 14px 18px; width: 100%;
}
.file-icon { width: 32px; height: 32px; color: #22c55e; flex-shrink: 0; }
.file-name { margin: 0; font-weight: 600; font-size: 0.9rem; word-break: break-all; }
.file-size { margin: 2px 0 0; font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); }
.clear-btn {
  margin-left: auto; display: flex; align-items: center; justify-content: center;
  width: 32px; height: 32px; border-radius: 50%;
  background: var(--md-sys-color-surface-container-high);
  border: none; cursor: pointer; color: inherit; flex-shrink: 0;
  svg { width: 16px; height: 16px; }
}

/* Progress */
.progress-wrap { display: flex; align-items: center; gap: 12px; }
.progress-bar-bg {
  flex: 1; height: 8px; border-radius: 999px;
  background: var(--md-sys-color-surface-container-highest); overflow: hidden;
}
.progress-bar-fill {
  height: 100%; border-radius: 999px;
  background: linear-gradient(90deg, $accent, #fb923c);
  transition: width 0.2s;
}
.progress-pct { font-size: 0.8rem; font-weight: 600; color: $accent; min-width: 38px; text-align: right; }

/* URL input */
.url-input-wrap {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px; border-radius: 12px;
  background: var(--md-sys-color-surface);
  border: 1px solid var(--md-sys-color-outline-variant);
  &:focus-within { border-color: $accent; }
}
.url-icon { width: 18px; height: 18px; color: var(--md-sys-color-on-surface-variant); flex-shrink: 0; }
.url-input {
  flex: 1; border: none; background: transparent; color: inherit; font-size: 0.9rem;
  outline: none;
  &::placeholder { color: var(--md-sys-color-on-surface-variant); }
}
.url-note {
  display: flex; align-items: center; gap: 6px; margin: 0;
  font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant);
  svg { width: 14px; height: 14px; flex-shrink: 0; }
}

/* Path input */
.path-input-wrap {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px; border-radius: 12px;
  background: var(--md-sys-color-surface);
  border: 1px solid var(--md-sys-color-outline-variant);
  &:focus-within { border-color: $accent; }
}
.path-icon { width: 18px; height: 18px; color: var(--md-sys-color-on-surface-variant); flex-shrink: 0; }
.path-input {
  flex: 1; border: none; background: transparent; color: inherit; font-size: 0.88rem;
  font-family: ui-monospace, monospace; outline: none;
  &::placeholder { color: var(--md-sys-color-on-surface-variant); font-family: inherit; }
}

/* Quick paths */
.quick-paths { display: flex; flex-direction: column; gap: 8px; }
.qp-label {
  display: flex; align-items: center; gap: 6px; margin: 0;
  font-size: 0.78rem; font-weight: 600; color: var(--md-sys-color-on-surface-variant);
  svg { width: 13px; height: 13px; }
}
.qp-chips { display: flex; flex-wrap: wrap; gap: 7px; }
.qp-chip {
  padding: 4px 12px; border-radius: 999px;
  background: var(--md-sys-color-surface);
  border: 1px solid var(--md-sys-color-outline-variant);
  font-size: 0.72rem; font-family: ui-monospace, monospace;
  color: var(--md-sys-color-on-surface-variant); cursor: pointer;
  &:hover { border-color: $accent; color: $accent; }
}

/* Result box */
.result-box {
  display: flex; align-items: flex-start; gap: 12px;
  padding: 14px 18px; border-radius: 12px;
  &.success { background: rgba(34,197,94,0.08); border: 1px solid rgba(34,197,94,0.25); }
  &.error { background: rgba(239,68,68,0.08); border: 1px solid rgba(239,68,68,0.25); }
  svg {
    width: 22px; height: 22px; flex-shrink: 0; margin-top: 1px;
    .success & { color: #22c55e; }
    .error & { color: #ef4444; }
  }
}
.result-title {
  margin: 0; font-size: 0.9rem; font-weight: 700;
  .success & { color: #047857; }
  .error & { color: #dc2626; }
}
.result-sub {
  margin: 3px 0 0; font-size: 0.8rem; color: var(--md-sys-color-on-surface-variant);
  line-height: 1.5;
}

/* Install button */
.install-btn {
  display: inline-flex; align-items: center; justify-content: center; gap: 8px;
  padding: 12px 28px; border-radius: 999px; border: none; cursor: pointer;
  background: $accent; color: #fff; font-size: 0.92rem; font-weight: 700;
  transition: opacity 0.15s, transform 0.1s;
  align-self: flex-start;
  svg { width: 18px; height: 18px; }
  &:hover:not(:disabled) { opacity: 0.88; transform: translateY(-1px); }
  &:disabled { opacity: 0.45; cursor: not-allowed; transform: none; }
}

/* Bottom note */
.install-note {
  display: flex; align-items: flex-start; gap: 10px;
  padding: 12px 16px; border-radius: 12px;
  font-size: 0.8rem; color: var(--md-sys-color-on-surface-variant);
  background: var(--md-sys-color-surface-container);
  border: 1px solid var(--md-sys-color-outline-variant);
  line-height: 1.55;
}
.note-icon { width: 16px; height: 16px; flex-shrink: 0; margin-top: 1px; }

.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
