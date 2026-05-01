<template>
  <div class="page-root">
    <div class="page-header">
      <button class="back-btn" @click="$router.back()"><i-lucide:arrow-left /></button>
      <h2 class="page-title">Scheduled SMS Manager</h2>
      <button class="add-btn" @click="showForm = !showForm">
        <i-lucide:plus />
      </button>
    </div>

    <div v-if="showForm" class="form-card">
      <div class="fc-title">Schedule New SMS</div>
      <label class="field-label">Recipient Number</label>
      <input v-model="form.recipient" type="tel" placeholder="+1234567890" class="field-input" />
      <label class="field-label">Message</label>
      <textarea v-model="form.message" rows="3" placeholder="Type your SMS here..." class="field-textarea" />
      <label class="field-label">Send At</label>
      <input v-model="form.sendAt" type="datetime-local" class="field-input" />
      <div class="form-actions">
        <button class="cancel-btn" @click="resetForm">Cancel</button>
        <button class="save-btn" @click="scheduleMessage" :disabled="!form.recipient || !form.message || !form.sendAt">
          <i-lucide:calendar-clock /> Schedule SMS
        </button>
      </div>
    </div>

    <div v-if="loading" class="loading"><i-lucide:loader-circle class="spin" /> Loading scheduled messages...</div>
    <div v-else-if="!scheduled.length" class="empty-state">
      <i-lucide:calendar-x2 />
      <div>No scheduled SMS messages</div>
      <div class="es-sub">Add one using the + button above.</div>
    </div>
    <div v-else class="sched-list">
      <div class="sched-row" v-for="s in scheduled" :key="s.id" :class="{ sent: s.sent, overdue: s.overdue && !s.sent }">
        <div class="sr-info">
          <div class="sr-recipient">{{ s.recipient }}</div>
          <div class="sr-message">{{ s.message }}</div>
          <div class="sr-time"><i-lucide:clock :size="12" /> {{ fmtDate(s.sendAt) }}</div>
          <div class="sr-badge">
            <span class="badge sent-b" v-if="s.sent">Sent</span>
            <span class="badge overdue-b" v-else-if="s.overdue">Overdue</span>
            <span class="badge pending-b" v-else>Pending</span>
          </div>
        </div>
        <button class="del-btn" @click="deleteScheduled(s.id)" title="Delete"><i-lucide:trash-2 /></button>
      </div>
    </div>

    <div v-if="error" class="error-box"><i-lucide:alert-triangle /> {{ error }}</div>
    <div v-if="success" class="success-box"><i-lucide:check-circle /> {{ success }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import { scheduledSmsListGQL } from '@/lib/api/query'
import { scheduleSmsGQL, deleteScheduledSmsGQL } from '@/lib/api/mutation'

type ScheduledSms = { id: string; recipient: string; message: string; sendAt: number; sent: boolean; overdue: boolean }

const loading = ref(false)
const error = ref('')
const success = ref('')
const scheduled = ref<ScheduledSms[]>([])
const showForm = ref(false)

const form = ref({ recipient: '', message: '', sendAt: '' })

function resetForm() { form.value = { recipient: '', message: '', sendAt: '' }; showForm.value = false }

function fmtDate(ts: number) {
  return new Date(ts).toLocaleString(undefined, { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

async function load() {
  loading.value = true; error.value = ''
  try {
    const r = await gqlFetch<{ scheduledSmsList: ScheduledSms[] }>(scheduledSmsListGQL)
    if (r.errors?.length) { error.value = r.errors[0].message; return }
    scheduled.value = r.data.scheduledSmsList
  } catch { error.value = 'Could not reach device.' }
  finally { loading.value = false }
}

async function scheduleMessage() {
  error.value = ''; success.value = ''
  try {
    const sendAtMs = new Date(form.value.sendAt).getTime()
    const r = await gqlFetch<{ scheduleSms: boolean }>(scheduleSmsGQL, {
      recipient: form.value.recipient,
      message: form.value.message,
      sendAt: sendAtMs,
    })
    if (r.errors?.length) { error.value = r.errors[0].message; return }
    success.value = 'SMS scheduled successfully!'
    setTimeout(() => success.value = '', 3000)
    resetForm()
    await load()
  } catch { error.value = 'Could not reach device.' }
}

async function deleteScheduled(id: string) {
  if (!confirm('Delete this scheduled SMS?')) return
  const r = await gqlFetch<{ deleteScheduledSms: boolean }>(deleteScheduledSmsGQL, { id })
  if (r.data.deleteScheduledSms) await load()
}

onMounted(load)
</script>

<style scoped lang="scss">
.page-root { padding: 18px 20px 32px; display: flex; flex-direction: column; gap: 14px; max-width: 700px; margin: 0 auto; }
.page-header { display: flex; align-items: center; gap: 12px; }
.back-btn { background: none; border: none; cursor: pointer; color: var(--md-sys-color-on-surface); display: flex; align-items: center; padding: 6px; border-radius: 50%; transition: background 0.18s; &:hover { background: var(--md-sys-color-surface-container); } svg { width: 22px; height: 22px; } }
.page-title { font-size: 1.4rem; font-weight: 700; margin: 0; flex: 1; }
.add-btn { background: #6366f1; color: #fff; border: none; cursor: pointer; display: flex; align-items: center; padding: 8px; border-radius: 50%; &:hover { background: #4f46e5; } svg { width: 20px; height: 20px; } }
.form-card { background: var(--md-sys-color-surface-container); border-radius: 18px; padding: 18px; display: flex; flex-direction: column; gap: 8px; }
.fc-title { font-weight: 700; font-size: 1rem; margin-bottom: 6px; }
.field-label { font-size: 0.82rem; font-weight: 600; color: var(--md-sys-color-on-surface-variant); }
.field-input, .field-textarea { border: 1px solid var(--md-sys-color-outline-variant); border-radius: 10px; padding: 9px 12px; font-size: 0.9rem; background: var(--md-sys-color-surface); color: var(--md-sys-color-on-surface); font-family: inherit; &:focus { outline: 2px solid #6366f1; } }
.field-textarea { resize: vertical; }
.form-actions { display: flex; gap: 10px; margin-top: 6px; }
.cancel-btn { padding: 9px 20px; border-radius: 10px; border: 1px solid var(--md-sys-color-outline-variant); background: none; cursor: pointer; color: var(--md-sys-color-on-surface); font-size: 0.9rem; }
.save-btn { display: flex; align-items: center; gap: 8px; padding: 9px 20px; background: #6366f1; color: #fff; border: none; border-radius: 10px; cursor: pointer; font-size: 0.9rem; font-weight: 600; &:hover:not(:disabled) { background: #4f46e5; } &:disabled { opacity: 0.5; cursor: not-allowed; } svg { width: 16px; height: 16px; } }
.spin { animation: spin 1s linear infinite; } @keyframes spin { to { transform: rotate(360deg); } }
.loading { display: flex; align-items: center; gap: 10px; color: var(--md-sys-color-on-surface-variant); svg { width: 20px; height: 20px; } }
.empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; padding: 48px 20px; color: var(--md-sys-color-on-surface-variant); svg { width: 48px; height: 48px; opacity: 0.4; } .es-sub { font-size: 0.82rem; } }
.sched-list { display: flex; flex-direction: column; gap: 10px; }
.sched-row { display: flex; align-items: flex-start; gap: 12px; padding: 14px; background: var(--md-sys-color-surface-container); border-radius: 14px; border-left: 3px solid transparent; &.sent { opacity: 0.6; border-left-color: #22c55e; } &.overdue { border-left-color: #ef4444; } }
.sr-info { flex: 1; }
.sr-recipient { font-weight: 700; font-size: 0.95rem; }
.sr-message { font-size: 0.88rem; color: var(--md-sys-color-on-surface-variant); margin: 3px 0; }
.sr-time { display: flex; align-items: center; gap: 4px; font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); margin-bottom: 6px; }
.badge { font-size: 0.7rem; padding: 2px 8px; border-radius: 999px; font-weight: 700; &.sent-b { background: rgba(34,197,94,0.15); color: #16a34a; } &.overdue-b { background: rgba(239,68,68,0.15); color: #ef4444; } &.pending-b { background: rgba(99,102,241,0.15); color: #6366f1; } }
.del-btn { background: none; border: none; cursor: pointer; color: #ef4444; display: flex; align-items: center; padding: 6px; border-radius: 50%; transition: background 0.15s; &:hover { background: rgba(239,68,68,0.1); } svg { width: 18px; height: 18px; } }
.error-box { display: flex; align-items: center; gap: 10px; padding: 14px; border-radius: 14px; font-size: 0.9rem; background: rgba(239,68,68,0.08); color: #ef4444; svg { width: 18px; height: 18px; } }
.success-box { display: flex; align-items: center; gap: 10px; padding: 14px; border-radius: 14px; font-size: 0.9rem; background: rgba(34,197,94,0.08); color: #16a34a; svg { width: 18px; height: 18px; } }
</style>
