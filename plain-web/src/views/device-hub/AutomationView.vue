<template>
  <div class="auto-root">
    <header class="auto-head">
      <router-link to="/device-hub" class="back-link">
        <i-lucide:arrow-left /> {{ $t('device_hub_title') }}
      </router-link>
      <div class="head-main">
        <i-lucide:zap class="head-icon" />
        <div>
          <h2 class="head-title">{{ $t('hub_automation_title') }}</h2>
          <p class="head-sub">{{ $t('hub_automation_desc') }}</p>
        </div>
      </div>
      <div class="head-actions">
        <label class="toggle">
          <input type="checkbox" :checked="state?.enabled" @change="onEnabledChange" />
          <span>{{ state?.enabled ? $t('automation_engine_on') : $t('automation_engine_off') }}</span>
        </label>
        <button class="btn primary" @click="openNew('rule')">
          <i-lucide:plus /> {{ $t('automation_new_rule') }}
        </button>
        <button class="btn" @click="openNew('schedule')">
          <i-lucide:calendar-plus /> {{ $t('automation_new_schedule') }}
        </button>
      </div>
    </header>

    <div class="stat-strip" v-if="state">
      <div class="stat">
        <div class="stat-num">{{ state.activeCount }} / {{ state.ruleCount }}</div>
        <div class="stat-lbl">{{ $t('automation_active_rules') }}</div>
      </div>
      <div class="stat">
        <div class="stat-num">{{ state.nextScheduledMs ? formatRelative(state.nextScheduledMs) : '—' }}</div>
        <div class="stat-lbl">{{ $t('automation_next_run') }}</div>
      </div>
      <div class="stat">
        <div class="stat-num">{{ runs.length }}</div>
        <div class="stat-lbl">{{ $t('automation_recent_runs') }}</div>
      </div>
    </div>

    <div class="tabs">
      <button :class="{ on: tab === 'rules' }" @click="tab = 'rules'">
        <i-lucide:list-checks /> {{ $t('automation_tab_rules') }}
      </button>
      <button :class="{ on: tab === 'schedules' }" @click="tab = 'schedules'">
        <i-lucide:calendar-clock /> {{ $t('automation_tab_schedules') }}
      </button>
      <button :class="{ on: tab === 'history' }" @click="tab = 'history'">
        <i-lucide:history /> {{ $t('automation_tab_history') }}
      </button>
    </div>

    <!-- Rules / Schedules list -->
    <section v-if="tab !== 'history'" class="rule-list">
      <div v-if="filteredRules.length === 0" class="empty">
        <i-lucide:inbox />
        <p>{{ tab === 'schedules' ? $t('automation_no_schedules') : $t('automation_no_rules') }}</p>
      </div>
      <article v-for="r in filteredRules" :key="r.id" class="rule-card" :class="{ off: !r.enabled }">
        <div class="rule-top">
          <button class="enable-dot" :class="{ on: r.enabled }" @click="toggleEnabled(r)" :title="r.enabled ? $t('on') : $t('off')">
            <i-lucide:zap v-if="r.enabled" />
            <i-lucide:zap-off v-else />
          </button>
          <div class="rule-name">
            <h3>{{ r.name }}</h3>
            <div class="rule-meta">
              <span class="badge trig">{{ triggerLabel(r) }}</span>
              <span v-for="(c, i) in r.conditions" :key="i" class="badge cond">{{ conditionLabel(c) }}</span>
              <span v-if="r.cooldownMs > 0" class="badge neutral">
                <i-lucide:hourglass /> {{ formatDuration(r.cooldownMs) }}
              </span>
              <span v-if="r.lastRunMs > 0" class="badge dim">
                <i-lucide:clock /> {{ formatRelative(r.lastRunMs) }}
              </span>
            </div>
          </div>
          <div class="rule-actions">
            <button class="icon-btn" @click="runNow(r)" :title="$t('automation_run_now')">
              <i-lucide:play />
            </button>
            <button class="icon-btn" @click="openEdit(r)" :title="$t('edit')">
              <i-lucide:pencil />
            </button>
            <button class="icon-btn danger" @click="confirmDelete(r)" :title="$t('delete')">
              <i-lucide:trash-2 />
            </button>
          </div>
        </div>
        <ol class="action-chain">
          <li v-for="(a, i) in r.actions" :key="i">
            <span class="step">{{ i + 1 }}</span>
            <component :is="actionIcon(a.type)" />
            <span class="act-name">{{ actionLabel(a) }}</span>
          </li>
        </ol>
      </article>
    </section>

    <!-- History tab -->
    <section v-else class="history">
      <div class="hist-head">
        <h3>{{ $t('automation_history') }}</h3>
        <button class="btn ghost" @click="onClearRuns">
          <i-lucide:eraser /> {{ $t('clear') }}
        </button>
      </div>
      <div v-if="runs.length === 0" class="empty"><i-lucide:inbox /><p>{{ $t('automation_no_runs') }}</p></div>
      <div v-for="r in runs" :key="r.id" class="run-row" :class="{ ok: r.ok, fail: !r.ok }">
        <div class="run-when">
          <i-lucide:circle-check v-if="r.ok" />
          <i-lucide:circle-x v-else />
          <span>{{ formatRelative(r.ts) }}</span>
        </div>
        <div class="run-name">
          <strong>{{ r.ruleName || $t('unnamed') }}</strong>
          <span class="src">{{ r.source }}</span>
        </div>
        <ul class="run-log">
          <li v-for="(l, i) in r.log" :key="i">{{ l }}</li>
        </ul>
      </div>
    </section>

    <!-- Editor modal -->
    <div v-if="editor" class="modal-back" @click.self="editor = null">
      <div class="modal">
        <header class="modal-head">
          <h3>{{ editor.id ? $t('automation_edit_rule') : (editor.kind === 'schedule' ? $t('automation_new_schedule') : $t('automation_new_rule')) }}</h3>
          <button class="icon-btn" @click="editor = null"><i-lucide:x /></button>
        </header>

        <div class="modal-body">
          <label class="field">
            <span>{{ $t('automation_rule_name') }}</span>
            <input v-model="editor.name" type="text" :placeholder="$t('automation_rule_name')" />
          </label>

          <!-- Trigger -->
          <fieldset>
            <legend><i-lucide:bolt /> {{ $t('automation_trigger') }}</legend>
            <select v-model="editor.trigger.type" @change="onTriggerTypeChange">
              <option v-for="t in triggerTypes(editor.kind)" :key="t" :value="t">{{ $t('trig_' + t) }}</option>
            </select>
            <div class="param-grid">
              <template v-if="editor.trigger.type === 'time'">
                <label class="field">
                  <span>{{ $t('automation_time') }}</span>
                  <input type="time" :value="timeValue(editor.trigger.params)" @input="setTimeValue($event, editor.trigger.params)" />
                </label>
                <label class="field">
                  <span>{{ $t('automation_days') }}</span>
                  <div class="dow-row">
                    <button type="button" v-for="d in dayLabels" :key="d.v" :class="{ on: dowSelected(editor.trigger.params, d.v) }" @click="toggleDow(editor.trigger.params, d.v)">{{ d.l }}</button>
                  </div>
                </label>
              </template>
              <template v-else-if="editor.trigger.type === 'scheduled_once'">
                <label class="field">
                  <span>{{ $t('automation_at') }}</span>
                  <input type="datetime-local" :value="dtValue(editor.trigger.params)" @input="setDtValue($event, editor.trigger.params)" />
                </label>
              </template>
              <template v-else-if="editor.trigger.type === 'battery_level'">
                <label class="field">
                  <span>{{ $t('automation_op') }}</span>
                  <select :value="editor.trigger.params.find(p => p.key === 'op')?.value || '<'" @change="setParam($event, editor.trigger.params, 'op')">
                    <option value="<">&lt;</option><option value="<=">&le;</option>
                    <option value=">">&gt;</option><option value=">=">&ge;</option>
                    <option value="==">=</option>
                  </select>
                </label>
                <label class="field">
                  <span>{{ $t('automation_threshold') }} (%)</span>
                  <input type="number" min="0" max="100" :value="paramVal(editor.trigger.params, 'threshold', '20')" @input="setParam($event, editor.trigger.params, 'threshold')" />
                </label>
              </template>
              <template v-else-if="editor.trigger.type === 'battery_charging'">
                <label class="field">
                  <span>{{ $t('automation_state') }}</span>
                  <select :value="paramVal(editor.trigger.params, 'state', 'true')" @change="setParam($event, editor.trigger.params, 'state')">
                    <option value="true">{{ $t('charging') }}</option>
                    <option value="false">{{ $t('discharging') }}</option>
                  </select>
                </label>
              </template>
              <template v-else-if="editor.trigger.type === 'incoming_call' || editor.trigger.type === 'sms_received'">
                <label class="field">
                  <span>{{ $t('automation_from') }}</span>
                  <input type="text" :value="paramVal(editor.trigger.params, 'from', '')" @input="setParam($event, editor.trigger.params, 'from')" placeholder="+15551234567 / blank for any" />
                </label>
                <label v-if="editor.trigger.type === 'sms_received'" class="field">
                  <span>{{ $t('automation_contains') }}</span>
                  <input type="text" :value="paramVal(editor.trigger.params, 'contains', '')" @input="setParam($event, editor.trigger.params, 'contains')" />
                </label>
              </template>
              <template v-else-if="editor.trigger.type === 'wifi_connected'">
                <label class="field">
                  <span>SSID</span>
                  <input type="text" :value="paramVal(editor.trigger.params, 'ssid', '')" @input="setParam($event, editor.trigger.params, 'ssid')" />
                </label>
              </template>
              <template v-else-if="editor.trigger.type === 'app_launched'">
                <label class="field">
                  <span>{{ $t('package') }}</span>
                  <input type="text" :value="paramVal(editor.trigger.params, 'package', '')" @input="setParam($event, editor.trigger.params, 'package')" placeholder="com.example.app" />
                </label>
              </template>
            </div>
          </fieldset>

          <!-- Conditions -->
          <fieldset v-if="editor.kind !== 'schedule'">
            <legend><i-lucide:filter /> {{ $t('automation_conditions') }} <span class="legend-sub">({{ $t('automation_conditions_hint') }})</span></legend>
            <div v-for="(c, i) in editor.conditions" :key="i" class="cond-row">
              <select v-model="c.type">
                <option v-for="t in conditionTypes" :key="t" :value="t">{{ $t('cond_' + t) }}</option>
              </select>
              <template v-if="c.type === 'time_window'">
                <input type="time" :value="paramVal(c.params, 'from', '09:00')" @input="setParam($event, c.params, 'from')" />
                <span>→</span>
                <input type="time" :value="paramVal(c.params, 'to', '17:00')" @input="setParam($event, c.params, 'to')" />
              </template>
              <template v-else-if="c.type === 'battery_level'">
                <select :value="paramVal(c.params, 'op', '<')" @change="setParam($event, c.params, 'op')">
                  <option value="<">&lt;</option><option value=">">&gt;</option>
                </select>
                <input type="number" min="0" max="100" :value="paramVal(c.params, 'threshold', '20')" @input="setParam($event, c.params, 'threshold')" />
                <span>%</span>
              </template>
              <template v-else-if="c.type === 'wifi_ssid'">
                <input type="text" :value="paramVal(c.params, 'ssid', '')" @input="setParam($event, c.params, 'ssid')" placeholder="SSID" />
              </template>
              <template v-else-if="c.type === 'charging' || c.type === 'silent_mode'">
                <select :value="paramVal(c.params, 'state', 'true')" @change="setParam($event, c.params, 'state')">
                  <option value="true">{{ $t('on') }}</option><option value="false">{{ $t('off') }}</option>
                </select>
              </template>
              <button type="button" class="icon-btn danger" @click="editor.conditions.splice(i, 1)"><i-lucide:trash-2 /></button>
            </div>
            <button class="btn ghost small" @click="editor.conditions.push({ type: 'time_window', params: [] })">
              <i-lucide:plus /> {{ $t('automation_add_condition') }}
            </button>
          </fieldset>

          <!-- Actions -->
          <fieldset>
            <legend><i-lucide:rocket /> {{ $t('automation_actions') }}</legend>
            <div class="action-list">
              <div v-for="(a, i) in editor.actions" :key="i" class="action-row">
                <span class="step">{{ i + 1 }}</span>
                <select v-model="a.type" @change="a.params = []">
                  <option v-for="t in actionTypes" :key="t" :value="t">{{ $t('act_' + t) }}</option>
                </select>
                <div class="action-params">
                  <template v-if="a.type === 'send_sms'">
                    <input type="tel" :value="paramVal(a.params, 'to', '')" @input="setParam($event, a.params, 'to')" :placeholder="$t('automation_to_number')" />
                    <textarea rows="2" :value="paramVal(a.params, 'body', '')" @input="setParam($event, a.params, 'body')" :placeholder="$t('automation_message')"></textarea>
                  </template>
                  <template v-else-if="a.type === 'make_call'">
                    <input type="tel" :value="paramVal(a.params, 'to', '')" @input="setParam($event, a.params, 'to')" :placeholder="$t('automation_to_number')" />
                  </template>
                  <template v-else-if="a.type === 'launch_app'">
                    <input type="text" :value="paramVal(a.params, 'package', '')" @input="setParam($event, a.params, 'package')" placeholder="com.example.app" />
                  </template>
                  <template v-else-if="a.type === 'set_ringer'">
                    <select :value="paramVal(a.params, 'mode', 'silent')" @change="setParam($event, a.params, 'mode')">
                      <option value="silent">{{ $t('silent') }}</option>
                      <option value="vibrate">{{ $t('vibrate_label') }}</option>
                      <option value="normal">{{ $t('normal') }}</option>
                    </select>
                  </template>
                  <template v-else-if="a.type === 'set_volume'">
                    <select :value="paramVal(a.params, 'stream', 'music')" @change="setParam($event, a.params, 'stream')">
                      <option value="music">{{ $t('stream_music') }}</option>
                      <option value="ring">{{ $t('stream_ring') }}</option>
                      <option value="alarm">{{ $t('stream_alarm') }}</option>
                      <option value="notification">{{ $t('stream_notif') }}</option>
                      <option value="voice">{{ $t('stream_voice') }}</option>
                    </select>
                    <input type="range" min="0" max="100" :value="paramVal(a.params, 'percent', '50')" @input="setParam($event, a.params, 'percent')" />
                    <span>{{ paramVal(a.params, 'percent', '50') }}%</span>
                  </template>
                  <template v-else-if="a.type === 'toggle_wifi' || a.type === 'toggle_bluetooth' || a.type === 'toggle_dnd' || a.type === 'flashlight'">
                    <select :value="paramVal(a.params, 'state', 'true')" @change="setParam($event, a.params, 'state')">
                      <option value="true">{{ $t('on') }}</option>
                      <option value="false">{{ $t('off') }}</option>
                    </select>
                  </template>
                  <template v-else-if="a.type === 'speak'">
                    <textarea rows="2" :value="paramVal(a.params, 'text', '')" @input="setParam($event, a.params, 'text')" :placeholder="$t('automation_speak_hint')"></textarea>
                  </template>
                  <template v-else-if="a.type === 'notify'">
                    <input type="text" :value="paramVal(a.params, 'title', '')" @input="setParam($event, a.params, 'title')" :placeholder="$t('automation_notify_title')" />
                    <textarea rows="2" :value="paramVal(a.params, 'body', '')" @input="setParam($event, a.params, 'body')" :placeholder="$t('automation_notify_body')"></textarea>
                  </template>
                  <template v-else-if="a.type === 'set_clipboard'">
                    <textarea rows="2" :value="paramVal(a.params, 'text', '')" @input="setParam($event, a.params, 'text')" placeholder="text…"></textarea>
                  </template>
                  <template v-else-if="a.type === 'send_webhook' || a.type === 'http_get'">
                    <input type="url" :value="paramVal(a.params, 'url', '')" @input="setParam($event, a.params, 'url')" placeholder="https://example.com/hook" />
                    <select v-if="a.type === 'send_webhook'" :value="paramVal(a.params, 'method', 'POST')" @change="setParam($event, a.params, 'method')">
                      <option>POST</option><option>PUT</option><option>PATCH</option><option>DELETE</option><option>GET</option>
                    </select>
                    <textarea rows="2" :value="paramVal(a.params, 'body', '')" @input="setParam($event, a.params, 'body')" placeholder='{"event":"battery_low"}'></textarea>
                    <input type="text" :value="paramVal(a.params, 'captureAs', '')" @input="setParam($event, a.params, 'captureAs')" :placeholder="$t('automation_capture_as')" />
                  </template>
                  <template v-else-if="a.type === 'delay'">
                    <input type="number" min="100" max="600000" :value="paramVal(a.params, 'ms', '1000')" @input="setParam($event, a.params, 'ms')" /> ms
                  </template>
                  <template v-else-if="a.type === 'vibrate'">
                    <input type="number" min="10" max="5000" :value="paramVal(a.params, 'ms', '250')" @input="setParam($event, a.params, 'ms')" /> ms
                  </template>
                  <template v-else-if="a.type === 'take_photo'">
                    <select :value="paramVal(a.params, 'camera', 'back')" @change="setParam($event, a.params, 'camera')">
                      <option value="back">{{ $t('camera_back') }}</option>
                      <option value="front">{{ $t('camera_front') }}</option>
                    </select>
                    <select :value="paramVal(a.params, 'flash', 'off')" @change="setParam($event, a.params, 'flash')">
                      <option value="off">{{ $t('flash_off') }}</option>
                      <option value="on">{{ $t('flash_on') }}</option>
                    </select>
                  </template>
                  <template v-else-if="a.type === 'start_recording'">
                    <input type="number" min="1" max="600" :value="paramVal(a.params, 'seconds', '30')" @input="setParam($event, a.params, 'seconds')" /> s
                    <select :value="paramVal(a.params, 'source', 'mic')" @change="setParam($event, a.params, 'source')">
                      <option value="mic">{{ $t('mic') }}</option>
                      <option value="cam">{{ $t('camera_back') }}</option>
                    </select>
                  </template>
                  <template v-else-if="a.type === 'set_variable'">
                    <input type="text" :value="paramVal(a.params, 'key', '')" @input="setParam($event, a.params, 'key')" placeholder="key" />
                    <input type="text" :value="paramVal(a.params, 'value', '')" @input="setParam($event, a.params, 'value')" placeholder="value (supports {{tokens}})" />
                  </template>
                </div>
                <div class="row-actions">
                  <button type="button" class="icon-btn" :disabled="i === 0" @click="moveAction(i, -1)"><i-lucide:chevron-up /></button>
                  <button type="button" class="icon-btn" :disabled="i === editor.actions.length - 1" @click="moveAction(i, 1)"><i-lucide:chevron-down /></button>
                  <button type="button" class="icon-btn danger" @click="editor.actions.splice(i, 1)"><i-lucide:trash-2 /></button>
                </div>
              </div>
            </div>
            <button class="btn ghost small" @click="editor.actions.push({ type: 'notify', params: [] })">
              <i-lucide:plus /> {{ $t('automation_add_action') }}
            </button>
          </fieldset>

          <!-- Advanced -->
          <fieldset>
            <legend><i-lucide:settings-2 /> {{ $t('automation_advanced') }}</legend>
            <label class="field">
              <span>{{ $t('automation_cooldown') }} (ms)</span>
              <input type="number" min="0" v-model.number="editor.cooldownMs" />
            </label>
            <p class="hint">{{ $t('automation_token_hint') }}</p>
          </fieldset>
        </div>

        <footer class="modal-foot">
          <button class="btn ghost" @click="editor = null">{{ $t('cancel') }}</button>
          <button class="btn primary" @click="onSave">
            <i-lucide:save /> {{ $t('save') }}
          </button>
        </footer>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import {
  automationStateGQL, automationRulesGQL, automationRunsGQL,
  upsertAutomationRuleGQL, setAutomationRuleEnabledGQL, deleteAutomationRuleGQL,
  runAutomationRuleGQL, setAutomationEnabledGQL, clearAutomationRunsGQL,
} from '@/lib/api/query'

interface Kv { key: string; value: string }
interface Trigger { type: string; params: Kv[] }
interface Cond { type: string; params: Kv[] }
interface Action { type: string; params: Kv[] }
interface Rule {
  id: string; name: string; enabled: boolean; kind: string
  trigger: Trigger; conditions: Cond[]; actions: Action[]
  cooldownMs: number; lastRunMs: number; createdMs: number; updatedMs: number
}
interface Run { id: string; ruleId: string; ruleName: string; ts: number; ok: boolean; source: string; log: string[] }

const state = ref<any>(null)
const rules = ref<Rule[]>([])
const runs = ref<Run[]>([])
const tab = ref<'rules' | 'schedules' | 'history'>('rules')
const editor = ref<Rule | null>(null)

// Lists ------------------------------------------------------------
const triggerTypesAll = ['manual', 'time', 'battery_level', 'battery_charging', 'incoming_call', 'sms_received', 'wifi_connected', 'bluetooth_connected', 'app_launched', 'headphones', 'boot_completed']
const triggerTypesSched = ['scheduled_once', 'time']
const conditionTypes = ['time_window', 'day_of_week', 'battery_level', 'charging', 'wifi_ssid', 'silent_mode']
const actionTypes = ['notify', 'send_sms', 'make_call', 'launch_app', 'speak', 'vibrate', 'set_ringer', 'set_volume', 'toggle_wifi', 'toggle_bluetooth', 'toggle_dnd', 'set_clipboard', 'send_webhook', 'http_get', 'flashlight', 'lock_screen', 'take_photo', 'start_recording', 'stop_recording', 'delay', 'set_variable']
const dayLabels = [
  { v: 1, l: 'Mo' }, { v: 2, l: 'Tu' }, { v: 3, l: 'We' }, { v: 4, l: 'Th' },
  { v: 5, l: 'Fr' }, { v: 6, l: 'Sa' }, { v: 7, l: 'Su' },
]

const filteredRules = computed(() => rules.value.filter(r => tab.value === 'schedules' ? r.kind === 'schedule' : r.kind !== 'schedule'))

function triggerTypes(kind: string) { return kind === 'schedule' ? triggerTypesSched : triggerTypesAll }

// Loaders ----------------------------------------------------------
async function load() {
  const [s, r, h] = await Promise.allSettled([
    gqlFetch(automationStateGQL, {}),
    gqlFetch(automationRulesGQL, {}),
    gqlFetch(automationRunsGQL, { limit: 50 }),
  ])
  if (s.status === 'fulfilled' && !(s.value as any).errors) state.value = (s.value as any).data.automationState
  if (r.status === 'fulfilled' && !(r.value as any).errors) rules.value = (r.value as any).data.automationRules
  if (h.status === 'fulfilled' && !(h.value as any).errors) runs.value = (h.value as any).data.automationRuns
}

onMounted(load)

// Param helpers ----------------------------------------------------
function paramVal(arr: Kv[], k: string, def = ''): string {
  return arr.find(p => p.key === k)?.value ?? def
}
function setParam(e: Event, arr: Kv[], k: string) {
  const v = (e.target as HTMLInputElement).value
  const idx = arr.findIndex(p => p.key === k)
  if (idx >= 0) arr[idx].value = v
  else arr.push({ key: k, value: v })
}
function timeValue(arr: Kv[]): string {
  const h = paramVal(arr, 'hour', '9'); const m = paramVal(arr, 'minute', '0')
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}
function setTimeValue(e: Event, arr: Kv[]) {
  const [h, m] = (e.target as HTMLInputElement).value.split(':')
  setParamVal(arr, 'hour', h); setParamVal(arr, 'minute', m)
}
function dtValue(arr: Kv[]): string {
  const ms = parseInt(paramVal(arr, 'atMs', '0')) || (Date.now() + 60_000)
  const d = new Date(ms); const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}
function setDtValue(e: Event, arr: Kv[]) {
  const v = (e.target as HTMLInputElement).value
  if (!v) return
  setParamVal(arr, 'atMs', String(new Date(v).getTime()))
}
function setParamVal(arr: Kv[], k: string, v: string) {
  const i = arr.findIndex(p => p.key === k)
  if (i >= 0) arr[i].value = v; else arr.push({ key: k, value: v })
}
function dowSelected(arr: Kv[], d: number): boolean {
  const days = (paramVal(arr, 'days', '1,2,3,4,5,6,7') || '').split(',').map(s => parseInt(s.trim())).filter(Boolean)
  return days.includes(d)
}
function toggleDow(arr: Kv[], d: number) {
  let days = (paramVal(arr, 'days', '1,2,3,4,5,6,7') || '').split(',').map(s => parseInt(s.trim())).filter(Boolean)
  if (days.includes(d)) days = days.filter(x => x !== d)
  else days = [...days, d].sort()
  setParamVal(arr, 'days', days.join(','))
}

function onTriggerTypeChange() {
  if (!editor.value) return
  editor.value.trigger.params = []
  if (editor.value.trigger.type === 'time') {
    setParamVal(editor.value.trigger.params, 'hour', '21')
    setParamVal(editor.value.trigger.params, 'minute', '0')
    setParamVal(editor.value.trigger.params, 'days', '1,2,3,4,5,6,7')
  }
  if (editor.value.trigger.type === 'scheduled_once') {
    setParamVal(editor.value.trigger.params, 'atMs', String(Date.now() + 60 * 60 * 1000))
  }
}

// Editor open/save -------------------------------------------------
function blank(kind: string): Rule {
  const trig = kind === 'schedule' ? 'scheduled_once' : 'manual'
  const r: Rule = {
    id: '', name: kind === 'schedule' ? 'Scheduled action' : 'New rule',
    enabled: true, kind, cooldownMs: 0, lastRunMs: 0, createdMs: 0, updatedMs: 0,
    trigger: { type: trig, params: [] },
    conditions: [], actions: [{ type: 'notify', params: [{ key: 'title', value: 'Hello' }, { key: 'body', value: 'It worked' }] }],
  }
  if (trig === 'scheduled_once') setParamVal(r.trigger.params, 'atMs', String(Date.now() + 60 * 60 * 1000))
  return r
}
function openNew(kind: string) {
  editor.value = blank(kind)
  if (kind === 'schedule') tab.value = 'schedules'
  else tab.value = 'rules'
}
function openEdit(r: Rule) {
  editor.value = JSON.parse(JSON.stringify(r))
}
async function onSave() {
  if (!editor.value) return
  const e = editor.value
  const input = {
    id: e.id, name: e.name, enabled: e.enabled, kind: e.kind, cooldownMs: e.cooldownMs,
    trigger: { type: e.trigger.type, params: e.trigger.params },
    conditions: e.conditions, actions: e.actions,
  }
  await gqlFetch(upsertAutomationRuleGQL, { input })
  editor.value = null
  await load()
}
async function toggleEnabled(r: Rule) {
  await gqlFetch(setAutomationRuleEnabledGQL, { id: r.id, enabled: !r.enabled })
  await load()
}
async function runNow(r: Rule) {
  await gqlFetch(runAutomationRuleGQL, { id: r.id })
  setTimeout(load, 600)
}
async function confirmDelete(r: Rule) {
  if (!confirm(`Delete "${r.name}"?`)) return
  await gqlFetch(deleteAutomationRuleGQL, { id: r.id })
  await load()
}
async function onEnabledChange(e: Event) {
  await gqlFetch(setAutomationEnabledGQL, { enabled: (e.target as HTMLInputElement).checked })
  await load()
}
async function onClearRuns() {
  await gqlFetch(clearAutomationRunsGQL, {})
  await load()
}
function moveAction(i: number, delta: number) {
  if (!editor.value) return
  const arr = editor.value.actions
  const j = i + delta
  if (j < 0 || j >= arr.length) return
  const [it] = arr.splice(i, 1); arr.splice(j, 0, it)
}

// Labels -----------------------------------------------------------
function triggerLabel(r: Rule): string {
  const t = r.trigger
  switch (t.type) {
    case 'time': return `${paramVal(t.params, 'hour', '0').padStart(2, '0')}:${paramVal(t.params, 'minute', '0').padStart(2, '0')}`
    case 'scheduled_once': {
      const ms = parseInt(paramVal(t.params, 'atMs', '0'))
      return ms ? new Date(ms).toLocaleString() : 'once'
    }
    case 'battery_level': return `🔋 ${paramVal(t.params, 'op', '<')} ${paramVal(t.params, 'threshold', '20')}%`
    case 'battery_charging': return paramVal(t.params, 'state', 'true') === 'true' ? '⚡ charging' : '⚡ unplugged'
    case 'incoming_call': return `📞 ${paramVal(t.params, 'from', 'any')}`
    case 'sms_received': return `💬 ${paramVal(t.params, 'from', 'any')}`
    case 'wifi_connected': return `📶 ${paramVal(t.params, 'ssid', 'any')}`
    case 'app_launched': return `▶ ${paramVal(t.params, 'package', 'any')}`
    default: return t.type
  }
}
function conditionLabel(c: Cond): string {
  switch (c.type) {
    case 'time_window': return `${paramVal(c.params, 'from', '')}–${paramVal(c.params, 'to', '')}`
    case 'battery_level': return `🔋 ${paramVal(c.params, 'op', '<')} ${paramVal(c.params, 'threshold', '0')}%`
    case 'wifi_ssid': return `📶 ${paramVal(c.params, 'ssid', '')}`
    default: return c.type
  }
}
function actionLabel(a: Action): string {
  switch (a.type) {
    case 'send_sms': return `SMS → ${paramVal(a.params, 'to', '?')}`
    case 'make_call': return `Call ${paramVal(a.params, 'to', '?')}`
    case 'launch_app': return `Launch ${paramVal(a.params, 'package', '?')}`
    case 'set_ringer': return `Ringer: ${paramVal(a.params, 'mode', '')}`
    case 'set_volume': return `Vol ${paramVal(a.params, 'stream', '')} = ${paramVal(a.params, 'percent', '')}%`
    case 'toggle_wifi': return `Wi-Fi ${paramVal(a.params, 'state', 'true') === 'true' ? 'on' : 'off'}`
    case 'toggle_bluetooth': return `Bluetooth ${paramVal(a.params, 'state', 'true') === 'true' ? 'on' : 'off'}`
    case 'toggle_dnd': return `DND ${paramVal(a.params, 'state', 'true') === 'true' ? 'on' : 'off'}`
    case 'speak': return `Speak: "${paramVal(a.params, 'text', '').slice(0, 30)}"`
    case 'notify': return `Notify: "${paramVal(a.params, 'title', '')}"`
    case 'send_webhook': return `${paramVal(a.params, 'method', 'POST')} ${paramVal(a.params, 'url', '')}`
    case 'http_get': return `GET ${paramVal(a.params, 'url', '')}`
    case 'delay': return `Wait ${paramVal(a.params, 'ms', '0')}ms`
    case 'vibrate': return `Vibrate ${paramVal(a.params, 'ms', '0')}ms`
    case 'flashlight': return `Torch ${paramVal(a.params, 'state', 'true') === 'true' ? 'on' : 'off'}`
    case 'lock_screen': return 'Lock screen'
    case 'take_photo': return `Photo ${paramVal(a.params, 'camera', '')}`
    case 'start_recording': return `Record ${paramVal(a.params, 'seconds', '0')}s`
    case 'set_variable': return `${paramVal(a.params, 'key', '?')} ← ${paramVal(a.params, 'value', '')}`
    default: return a.type
  }
}
function actionIcon(t: string) {
  const map: Record<string, string> = {
    send_sms: 'message-square', make_call: 'phone-call', notify: 'bell',
    launch_app: 'rocket', speak: 'volume-2', set_ringer: 'volume-x',
    set_volume: 'volume-1', toggle_wifi: 'wifi', toggle_bluetooth: 'bluetooth',
    toggle_dnd: 'moon', set_clipboard: 'clipboard', send_webhook: 'send',
    http_get: 'globe', flashlight: 'flashlight', lock_screen: 'lock',
    take_photo: 'camera', start_recording: 'mic', stop_recording: 'mic-off',
    delay: 'hourglass', vibrate: 'activity', set_variable: 'tag',
  }
  const name = map[t] || 'circle'
  return () => h('span', { class: 'lucide-mini', innerHTML: `<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/></svg>` })
}

// Time formatting --------------------------------------------------
function formatRelative(ms: number): string {
  if (!ms) return ''
  const diff = ms - Date.now()
  const abs = Math.abs(diff)
  const sign = diff < 0 ? 'ago' : 'in'
  const secs = Math.floor(abs / 1000)
  if (secs < 60) return diff < 0 ? `${secs}s ago` : `in ${secs}s`
  const mins = Math.floor(secs / 60)
  if (mins < 60) return `${sign === 'ago' ? '' : 'in '}${mins}m${sign === 'ago' ? ' ago' : ''}`
  const hrs = Math.floor(mins / 60)
  if (hrs < 24) return `${sign === 'ago' ? '' : 'in '}${hrs}h${sign === 'ago' ? ' ago' : ''}`
  return new Date(ms).toLocaleString()
}
function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  if (ms < 60_000) return `${Math.round(ms / 1000)}s`
  if (ms < 3_600_000) return `${Math.round(ms / 60_000)}m`
  return `${Math.round(ms / 3_600_000)}h`
}

</script>

<style scoped lang="scss">
.auto-root { padding: 18px 22px 32px; max-width: 1100px; margin: 0 auto; display: flex; flex-direction: column; gap: 18px; }
.auto-head { display: flex; flex-wrap: wrap; align-items: center; gap: 18px; }
.back-link { display: inline-flex; align-items: center; gap: 4px; text-decoration: none; color: var(--md-sys-color-on-surface-variant); font-size: 0.85rem; }
.back-link:hover { color: var(--md-sys-color-primary); }
.head-main { display: flex; align-items: center; gap: 12px; flex: 1 1 auto; }
.head-icon { width: 32px; height: 32px; color: #f59e0b; }
.head-title { margin: 0; font-size: 1.4rem; font-weight: 700; }
.head-sub { margin: 4px 0 0; color: var(--md-sys-color-on-surface-variant); font-size: 0.86rem; }
.head-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }

.toggle { display: inline-flex; align-items: center; gap: 8px; padding: 6px 12px; border-radius: 10px; background: var(--md-sys-color-surface-container); cursor: pointer; font-weight: 600; font-size: 0.85rem; }
.toggle input { accent-color: #f59e0b; }

.btn { display: inline-flex; align-items: center; gap: 6px; padding: 8px 14px; border-radius: 10px; border: 1px solid var(--md-sys-color-outline-variant); background: var(--md-sys-color-surface-container); color: inherit; cursor: pointer; font-weight: 600; font-size: 0.85rem; }
.btn:hover { background: var(--md-sys-color-surface-container-high); }
.btn.primary { background: linear-gradient(135deg, #f59e0b, #ef4444); color: white; border-color: transparent; }
.btn.primary:hover { filter: brightness(1.05); }
.btn.ghost { background: transparent; }
.btn.small { padding: 4px 10px; font-size: 0.78rem; }
.btn:disabled { opacity: 0.4; cursor: not-allowed; }

.stat-strip { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 10px; }
.stat { padding: 14px; background: var(--md-sys-color-surface-container); border-radius: 14px; border: 1px solid var(--md-sys-color-outline-variant); }
.stat-num { font-size: 1.4rem; font-weight: 700; color: #f59e0b; }
.stat-lbl { color: var(--md-sys-color-on-surface-variant); font-size: 0.78rem; margin-top: 2px; }

.tabs { display: flex; gap: 4px; border-bottom: 1px solid var(--md-sys-color-outline-variant); }
.tabs button { display: inline-flex; align-items: center; gap: 6px; padding: 10px 16px; background: transparent; border: 0; color: var(--md-sys-color-on-surface-variant); font-weight: 600; cursor: pointer; border-bottom: 2px solid transparent; font-size: 0.85rem; }
.tabs button.on { color: #f59e0b; border-color: #f59e0b; }

.rule-list { display: flex; flex-direction: column; gap: 10px; }
.rule-card { background: var(--md-sys-color-surface-container); border: 1px solid var(--md-sys-color-outline-variant); border-radius: 14px; padding: 14px; transition: border-color 0.2s; }
.rule-card:hover { border-color: #f59e0b; }
.rule-card.off { opacity: 0.55; }
.rule-top { display: flex; align-items: flex-start; gap: 12px; }
.enable-dot { flex-shrink: 0; width: 38px; height: 38px; border-radius: 10px; border: 1px solid var(--md-sys-color-outline-variant); background: var(--md-sys-color-surface-container-high); color: var(--md-sys-color-on-surface-variant); display: flex; align-items: center; justify-content: center; cursor: pointer; }
.enable-dot.on { background: rgba(245,158,11,0.18); color: #b45309; border-color: rgba(245,158,11,0.4); }
.enable-dot svg { width: 18px; height: 18px; }
.rule-name { flex: 1; min-width: 0; }
.rule-name h3 { margin: 0; font-size: 1rem; font-weight: 700; }
.rule-meta { margin-top: 4px; display: flex; gap: 6px; flex-wrap: wrap; }
.badge { display: inline-flex; align-items: center; gap: 4px; padding: 2px 8px; border-radius: 999px; font-size: 0.7rem; font-weight: 600; }
.badge.trig { background: rgba(245,158,11,0.16); color: #b45309; }
.badge.cond { background: rgba(99,102,241,0.16); color: #4338ca; }
.badge.neutral { background: var(--md-sys-color-surface-container-high); color: var(--md-sys-color-on-surface-variant); }
.badge.dim { color: var(--md-sys-color-on-surface-variant); }
.badge svg { width: 10px; height: 10px; }

.rule-actions { display: flex; gap: 4px; }
.icon-btn { width: 32px; height: 32px; border-radius: 8px; border: 0; background: transparent; color: var(--md-sys-color-on-surface-variant); cursor: pointer; display: inline-flex; align-items: center; justify-content: center; }
.icon-btn:hover { background: var(--md-sys-color-surface-container-high); color: inherit; }
.icon-btn.danger:hover { background: rgba(239,68,68,0.12); color: #dc2626; }
.icon-btn:disabled { opacity: 0.3; cursor: not-allowed; }
.icon-btn svg { width: 16px; height: 16px; }

.action-chain { list-style: none; margin: 12px 0 0 50px; padding: 0; display: flex; flex-direction: column; gap: 4px; }
.action-chain li { display: flex; align-items: center; gap: 8px; padding: 4px 8px; background: var(--md-sys-color-surface-container-high); border-radius: 8px; font-size: 0.82rem; color: var(--md-sys-color-on-surface-variant); }
.step { display: inline-flex; align-items: center; justify-content: center; width: 18px; height: 18px; border-radius: 50%; background: rgba(245,158,11,0.18); color: #b45309; font-size: 0.7rem; font-weight: 700; }
.act-name { color: var(--md-sys-color-on-surface); }

.empty { padding: 40px; text-align: center; color: var(--md-sys-color-on-surface-variant); display: flex; flex-direction: column; align-items: center; gap: 8px; }
.empty svg { width: 32px; height: 32px; opacity: 0.5; }

/* History */
.history { display: flex; flex-direction: column; gap: 8px; }
.hist-head { display: flex; justify-content: space-between; align-items: center; }
.hist-head h3 { margin: 0; font-size: 1rem; }
.run-row { padding: 12px; background: var(--md-sys-color-surface-container); border-radius: 12px; border-left: 4px solid #94a3b8; display: grid; grid-template-columns: 160px 1fr; gap: 8px 12px; }
.run-row.ok { border-color: #22c55e; }
.run-row.fail { border-color: #ef4444; }
.run-when { display: flex; align-items: center; gap: 6px; font-size: 0.82rem; color: var(--md-sys-color-on-surface-variant); }
.run-when svg { width: 14px; height: 14px; }
.run-row.ok .run-when svg { color: #22c55e; }
.run-row.fail .run-when svg { color: #ef4444; }
.run-name { display: flex; align-items: baseline; gap: 8px; }
.run-name .src { font-size: 0.7rem; color: var(--md-sys-color-on-surface-variant); padding: 1px 6px; border-radius: 4px; background: var(--md-sys-color-surface-container-high); text-transform: uppercase; }
.run-log { grid-column: 2; list-style: none; margin: 0; padding: 0; font-family: monospace; font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); }
.run-log li { padding: 1px 0; }

/* Modal */
.modal-back { position: fixed; inset: 0; background: rgba(0,0,0,0.5); display: flex; align-items: flex-start; justify-content: center; padding: 32px 16px; z-index: 100; overflow-y: auto; }
.modal { background: var(--md-sys-color-surface); border-radius: 16px; max-width: 720px; width: 100%; box-shadow: 0 30px 60px rgba(0,0,0,0.3); display: flex; flex-direction: column; max-height: calc(100vh - 64px); }
.modal-head { display: flex; justify-content: space-between; align-items: center; padding: 18px 20px; border-bottom: 1px solid var(--md-sys-color-outline-variant); }
.modal-head h3 { margin: 0; font-size: 1.05rem; font-weight: 700; }
.modal-body { padding: 18px 20px; overflow-y: auto; display: flex; flex-direction: column; gap: 18px; }
.modal-foot { padding: 14px 20px; border-top: 1px solid var(--md-sys-color-outline-variant); display: flex; justify-content: flex-end; gap: 8px; }

fieldset { border: 1px solid var(--md-sys-color-outline-variant); border-radius: 12px; padding: 14px; margin: 0; display: flex; flex-direction: column; gap: 10px; }
legend { padding: 0 6px; font-weight: 700; font-size: 0.85rem; display: inline-flex; align-items: center; gap: 6px; color: #f59e0b; }
.legend-sub { color: var(--md-sys-color-on-surface-variant); font-weight: 400; font-size: 0.75rem; margin-left: 4px; }

.field { display: flex; flex-direction: column; gap: 4px; }
.field span { font-size: 0.78rem; font-weight: 600; color: var(--md-sys-color-on-surface-variant); }
.field input, .field select, .field textarea { padding: 8px 10px; border: 1px solid var(--md-sys-color-outline-variant); border-radius: 8px; background: var(--md-sys-color-surface-container); color: inherit; font: inherit; font-size: 0.85rem; }
.field input:focus, .field select:focus, .field textarea:focus { outline: 2px solid rgba(245,158,11,0.4); border-color: #f59e0b; }
.param-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 10px; }

.dow-row { display: flex; gap: 4px; flex-wrap: wrap; }
.dow-row button { padding: 5px 10px; border-radius: 8px; border: 1px solid var(--md-sys-color-outline-variant); background: var(--md-sys-color-surface-container); color: var(--md-sys-color-on-surface-variant); cursor: pointer; font-weight: 600; font-size: 0.78rem; }
.dow-row button.on { background: rgba(245,158,11,0.18); color: #b45309; border-color: #f59e0b; }

.cond-row, .action-row { display: flex; align-items: flex-start; gap: 6px; flex-wrap: wrap; }
.cond-row select, .cond-row input, .action-row select, .action-row input, .action-row textarea {
  padding: 7px 10px; border: 1px solid var(--md-sys-color-outline-variant); border-radius: 8px;
  background: var(--md-sys-color-surface); color: inherit; font: inherit; font-size: 0.83rem;
}
.action-row { padding: 10px; background: var(--md-sys-color-surface-container); border-radius: 10px; }
.action-row > .step { margin-top: 6px; }
.action-params { display: flex; flex: 1 1 280px; flex-wrap: wrap; gap: 6px; min-width: 220px; }
.action-params input, .action-params textarea, .action-params select { flex: 1 1 140px; min-width: 0; }
.row-actions { display: flex; flex-direction: column; gap: 2px; }

.hint { font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); margin: 0; line-height: 1.4; }
</style>
