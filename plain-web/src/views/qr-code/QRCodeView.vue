<template>
  <div class="page-root">
    <div class="page-header">
      <button class="back-btn" @click="$router.back()"><i-lucide:arrow-left /></button>
      <h2 class="page-title">QR Code Tool</h2>
    </div>
    <div class="tab-bar">
      <button class="tab" :class="{ active: tab === 'gen' }" @click="tab = 'gen'">Generate</button>
      <button class="tab" :class="{ active: tab === 'scan' }" @click="tab = 'scan'">Scan via Device</button>
    </div>

    <div v-if="tab === 'gen'">
      <div class="gen-card">
        <div class="field-label">Content</div>
        <textarea v-model="genText" rows="3" placeholder="Enter URL, text, or any content..." class="gen-textarea" />
        <div class="size-row">
          <label>Size: {{ genSize }}px</label>
          <input type="range" min="128" max="512" step="32" v-model.number="genSize" />
        </div>
        <button class="gen-btn" @click="generateQR" :disabled="!genText.trim()">
          <i-lucide:qr-code /> Generate QR Code
        </button>
      </div>
      <div v-if="qrDataUrl" class="qr-preview">
        <img :src="qrDataUrl" alt="QR Code" :style="{ width: genSize + 'px', height: genSize + 'px' }" />
        <a :href="qrDataUrl" download="qrcode.png" class="dl-btn">
          <i-lucide:download /> Download PNG
        </a>
      </div>
    </div>

    <div v-if="tab === 'scan'">
      <div class="scan-info">
        <i-lucide:camera class="si-icon" />
        <div>
          <div class="si-title">Trigger QR Scan on Device</div>
          <div class="si-desc">Sends a command to the app to open the camera scanner. The decoded content will appear below.</div>
        </div>
      </div>
      <button class="scan-btn" @click="triggerScan" :disabled="scanning">
        <i-lucide:loader-circle v-if="scanning" class="spin" />
        <i-lucide:scan-line v-else />
        {{ scanning ? 'Scanning...' : 'Trigger Scan on Device' }}
      </button>
      <div v-if="scanResult" class="scan-result">
        <div class="sr-label">Scan Result</div>
        <div class="sr-content">{{ scanResult }}</div>
        <button class="copy-btn" @click="copyScanResult"><i-lucide:copy /> Copy</button>
      </div>
    </div>

    <div v-if="error" class="error-box"><i-lucide:alert-triangle /> {{ error }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import { triggerQrScanGQL } from '@/lib/api/query'

const tab = ref<'gen' | 'scan'>('gen')
const genText = ref('')
const genSize = ref(256)
const qrDataUrl = ref('')
const scanning = ref(false)
const scanResult = ref('')
const error = ref('')

async function generateQR() {
  if (!genText.value.trim()) return
  const QRCode = (await import('qrcode')).default
  qrDataUrl.value = await QRCode.toDataURL(genText.value, { width: genSize.value, margin: 2 })
}

async function triggerScan() {
  scanning.value = true; error.value = ''; scanResult.value = ''
  try {
    const r = await gqlFetch<{ triggerQrScan: string }>(triggerQrScanGQL)
    if (r.errors?.length) { error.value = r.errors[0].message; return }
    scanResult.value = r.data.triggerQrScan
  } catch { error.value = 'Could not reach device.' }
  finally { scanning.value = false }
}

async function copyScanResult() {
  await navigator.clipboard.writeText(scanResult.value)
}
</script>

<style scoped lang="scss">
.page-root { padding: 18px 20px 32px; display: flex; flex-direction: column; gap: 16px; max-width: 700px; margin: 0 auto; }
.page-header { display: flex; align-items: center; gap: 12px; }
.back-btn { background: none; border: none; cursor: pointer; color: var(--md-sys-color-on-surface); display: flex; align-items: center; padding: 6px; border-radius: 50%; transition: background 0.18s; &:hover { background: var(--md-sys-color-surface-container); } svg { width: 22px; height: 22px; } }
.page-title { font-size: 1.4rem; font-weight: 700; margin: 0; flex: 1; }
.tab-bar { display: flex; gap: 8px; }
.tab { padding: 8px 20px; border-radius: 999px; border: none; cursor: pointer; font-weight: 600; font-size: 0.88rem; background: var(--md-sys-color-surface-container); color: var(--md-sys-color-on-surface-variant); transition: all 0.2s; &.active { background: #6366f1; color: #fff; } }
.gen-card { background: var(--md-sys-color-surface-container); border-radius: 18px; padding: 18px; display: flex; flex-direction: column; gap: 12px; }
.field-label { font-size: 0.85rem; font-weight: 600; color: var(--md-sys-color-on-surface-variant); }
.gen-textarea { border: 1px solid var(--md-sys-color-outline-variant); border-radius: 10px; padding: 10px 12px; font-size: 0.9rem; background: var(--md-sys-color-surface); color: var(--md-sys-color-on-surface); resize: vertical; font-family: inherit; &:focus { outline: 2px solid #6366f1; } }
.size-row { display: flex; align-items: center; gap: 12px; font-size: 0.85rem; input { flex: 1; } }
.gen-btn { display: flex; align-items: center; gap: 8px; padding: 10px 18px; background: #6366f1; color: #fff; border: none; border-radius: 10px; cursor: pointer; font-size: 0.9rem; font-weight: 600; &:hover:not(:disabled) { background: #4f46e5; } &:disabled { opacity: 0.5; cursor: not-allowed; } svg { width: 18px; height: 18px; } }
.qr-preview { background: var(--md-sys-color-surface-container); border-radius: 18px; padding: 20px; display: flex; flex-direction: column; align-items: center; gap: 14px; img { border-radius: 8px; background: #fff; padding: 8px; } }
.dl-btn { display: flex; align-items: center; gap: 6px; padding: 8px 16px; border-radius: 10px; background: var(--md-sys-color-surface-container-high); text-decoration: none; color: var(--md-sys-color-on-surface); font-size: 0.88rem; svg { width: 16px; height: 16px; } }
.scan-info { display: flex; gap: 14px; padding: 14px; background: var(--md-sys-color-surface-container); border-radius: 14px; }
.si-icon { width: 32px; height: 32px; color: #6366f1; flex-shrink: 0; }
.si-title { font-weight: 700; margin-bottom: 4px; }
.si-desc { font-size: 0.83rem; color: var(--md-sys-color-on-surface-variant); line-height: 1.5; }
.scan-btn { display: flex; align-items: center; gap: 8px; padding: 12px 24px; background: #6366f1; color: #fff; border: none; border-radius: 12px; cursor: pointer; font-size: 0.95rem; font-weight: 600; align-self: flex-start; &:hover:not(:disabled) { background: #4f46e5; } &:disabled { opacity: 0.7; cursor: not-allowed; } svg { width: 18px; height: 18px; } }
.spin { animation: spin 1s linear infinite; } @keyframes spin { to { transform: rotate(360deg); } }
.scan-result { background: var(--md-sys-color-surface-container); border-radius: 14px; padding: 16px; }
.sr-label { font-size: 0.8rem; font-weight: 600; color: var(--md-sys-color-on-surface-variant); margin-bottom: 6px; }
.sr-content { font-size: 0.95rem; word-break: break-all; margin-bottom: 10px; }
.copy-btn { display: flex; align-items: center; gap: 6px; padding: 6px 14px; border: 1px solid var(--md-sys-color-outline-variant); border-radius: 8px; background: none; cursor: pointer; font-size: 0.82rem; color: var(--md-sys-color-on-surface); svg { width: 14px; height: 14px; } }
.error-box { display: flex; align-items: center; gap: 10px; padding: 14px; border-radius: 14px; font-size: 0.9rem; background: rgba(239,68,68,0.08); color: #ef4444; svg { width: 18px; height: 18px; } }
</style>
