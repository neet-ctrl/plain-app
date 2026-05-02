<template>
  <div class="ar">

    <!-- ── Header ── -->
    <header class="ar-head">
      <router-link to="/device-hub" class="back-link">
        <i-lucide:arrow-left /> Device Hub
      </router-link>
      <div class="head-main">
        <i-lucide:zap class="hd-icon" />
        <div>
          <h2 class="hd-title">Automation</h2>
          <p class="hd-sub">Build powerful if-this-then-that workflows on your device</p>
        </div>
      </div>
      <div class="head-acts">
        <label class="engine-toggle" :class="{ on: state?.enabled }">
          <input type="checkbox" :checked="state?.enabled" @change="onEnabledChange" />
          <span class="et-track"><span class="et-thumb" /></span>
          <span class="et-lbl">{{ state?.enabled ? 'Engine ON' : 'Engine OFF' }}</span>
        </label>
        <button class="btn ghost" @click="showTemplates = !showTemplates">
          <i-lucide:layout-template /> Templates
        </button>
        <button class="btn primary" @click="openNew('rule')">
          <i-lucide:plus /> New Rule
        </button>
        <button class="btn accent" @click="openNew('schedule')">
          <i-lucide:calendar-plus /> New Schedule
        </button>
      </div>
    </header>

    <!-- ── Engine warning ── -->
    <div v-if="state && !state.enabled" class="engine-warn">
      <i-lucide:power-off class="ew-icon" />
      <div>
        <strong>Automation engine is off.</strong>
        Rules will not fire until you turn it on.
      </div>
      <button class="btn primary sm" @click="enableEngine">Turn On</button>
    </div>

    <!-- ── Stats ── -->
    <div class="stat-strip" v-if="state">
      <div class="stat">
        <div class="stat-n">{{ state.activeCount }}<span class="stat-d">/{{ state.ruleCount }}</span></div>
        <div class="stat-l">Active rules</div>
      </div>
      <div class="stat">
        <div class="stat-n">{{ runs.length }}</div>
        <div class="stat-l">Recent runs</div>
      </div>
      <div class="stat">
        <div class="stat-n">{{ state.nextScheduledMs ? fmtRel(state.nextScheduledMs) : '—' }}</div>
        <div class="stat-l">Next scheduled</div>
      </div>
    </div>

    <!-- ── Template gallery ── -->
    <section v-if="showTemplates" class="tpl-gallery">
      <div class="tpl-head">
        <h3><i-lucide:layout-template /> Quick Start Templates</h3>
        <button class="icon-btn" @click="showTemplates = false"><i-lucide:x /></button>
      </div>
      <div class="tpl-grid">
        <button v-for="t in templates" :key="t.name" class="tpl-card" @click="applyTemplate(t)">
          <span class="tpl-emoji">{{ t.emoji }}</span>
          <span class="tpl-name">{{ t.name }}</span>
          <span class="tpl-desc">{{ t.desc }}</span>
        </button>
      </div>
    </section>

    <!-- ── Tabs ── -->
    <div class="tabs">
      <button :class="{ on: tab==='rules' }" @click="tab='rules'">
        <i-lucide:list-checks /> Rules
        <span class="tab-badge">{{ rules.filter(r=>r.kind!=='schedule').length }}</span>
      </button>
      <button :class="{ on: tab==='schedules' }" @click="tab='schedules'">
        <i-lucide:calendar-clock /> Schedules
        <span class="tab-badge">{{ rules.filter(r=>r.kind==='schedule').length }}</span>
      </button>
      <button :class="{ on: tab==='history' }" @click="tab='history'">
        <i-lucide:history /> History
        <span class="tab-badge">{{ runs.length }}</span>
      </button>
    </div>

    <!-- ── Rule list ── -->
    <section v-if="tab!=='history'" class="rule-list">
      <div v-if="filteredRules.length === 0" class="empty-state">
        <i-lucide:inbox class="es-icon" />
        <p>No {{ tab === 'schedules' ? 'schedules' : 'rules' }} yet</p>
        <div class="es-btns">
          <button class="btn primary" @click="openNew(tab==='schedules'?'schedule':'rule')">
            <i-lucide:plus /> Create {{ tab==='schedules'?'Schedule':'Rule' }}
          </button>
          <button class="btn ghost" @click="showTemplates=true">
            <i-lucide:layout-template /> Browse Templates
          </button>
        </div>
      </div>
      <article v-for="r in filteredRules" :key="r.id" class="rule-card" :class="{ off: !r.enabled }">
        <div class="rc-left">
          <button class="pwr-btn" :class="{ on: r.enabled }" @click="toggleEnabled(r)">
            <i-lucide:zap v-if="r.enabled" /><i-lucide:zap-off v-else />
          </button>
        </div>
        <div class="rc-body">
          <div class="rc-top">
            <h3 class="rc-name">{{ r.name }}</h3>
            <div class="rc-badges">
              <span class="badge trig">{{ TRIG_META[r.trigger.type]?.emoji || '⚡' }} {{ TRIG_META[r.trigger.type]?.short || r.trigger.type }}</span>
              <span v-for="(c,i) in r.conditions" :key="i" class="badge cond">{{ COND_META[c.type]?.emoji || '🔍' }} {{ c.type }}</span>
              <span v-if="r.cooldownMs>0" class="badge neutral"><i-lucide:hourglass /> {{ fmtDur(r.cooldownMs) }}</span>
              <span v-if="r.lastRunMs>0" class="badge dim"><i-lucide:clock /> {{ fmtRel(r.lastRunMs) }}</span>
            </div>
          </div>
          <ol class="act-chain">
            <li v-for="(a,i) in r.actions" :key="i">
              <span class="step">{{ i+1 }}</span>
              <span class="act-icon">{{ ACT_META[a.type]?.emoji || '⚙️' }}</span>
              <span class="act-lbl">{{ actLabel(a) }}</span>
            </li>
          </ol>
        </div>
        <div class="rc-actions">
          <button class="icon-btn green" @click="runNow(r)" title="Run now"><i-lucide:play /></button>
          <button class="icon-btn" @click="openEdit(r)" title="Edit"><i-lucide:pencil /></button>
          <button class="icon-btn red" @click="confirmDelete(r)" title="Delete"><i-lucide:trash-2 /></button>
        </div>
      </article>
    </section>

    <!-- ── History ── -->
    <section v-if="tab==='history'" class="history">
      <div class="hist-head">
        <h3>Run History</h3>
        <button class="btn ghost sm" @click="onClearRuns"><i-lucide:eraser /> Clear</button>
      </div>
      <div v-if="runs.length===0" class="empty-state"><i-lucide:inbox class="es-icon" /><p>No runs yet</p></div>
      <div v-for="r in runs" :key="r.id" class="run-row" :class="{ ok: r.ok, fail: !r.ok }">
        <div class="run-icon"><i-lucide:circle-check v-if="r.ok" /><i-lucide:circle-x v-else /></div>
        <div class="run-info">
          <div class="run-name"><strong>{{ r.ruleName || 'Untitled' }}</strong><span class="src">{{ r.source }}</span><span class="run-ts">{{ fmtRel(r.ts) }}</span></div>
          <ul class="run-log"><li v-for="(l,i) in r.log" :key="i">{{ l }}</li></ul>
        </div>
      </div>
    </section>

    <!-- ═══════════════════════════════════════════════════════ -->
    <!-- EDITOR DRAWER (teleported to body so it's never clipped) -->
    <!-- ═══════════════════════════════════════════════════════ -->
    <Teleport to="body">
      <template v-if="editor">
        <div class="backdrop" @click="editor=null" />
        <aside class="drawer">

          <!-- Drawer header -->
          <div class="drw-head">
            <div class="drw-kind-badge" :class="editor.kind">
              {{ editor.kind === 'schedule' ? '📅 Schedule' : '⚡ Rule' }}
            </div>
            <input class="drw-name" v-model="editor.name" placeholder="Rule name…" />
            <button class="icon-btn" @click="editor=null"><i-lucide:x /></button>
          </div>

          <!-- Drawer body -->
          <div class="drw-body">

            <!-- ① TRIGGER -->
            <div class="sec">
              <div class="sec-hdr">
                <span class="sec-num">1</span>
                <span class="sec-title">When this happens…</span>
                <span class="sec-sub">Trigger</span>
              </div>
              <div class="trig-grid">
                <button
                  v-for="tt in triggerTypesFor(editor.kind)" :key="tt"
                  class="trig-chip" :class="{ on: editor.trigger.type === tt }"
                  @click="setTriggerType(tt)"
                >
                  <span>{{ TRIG_META[tt]?.emoji }}</span>
                  <span>{{ TRIG_META[tt]?.label }}</span>
                </button>
              </div>
              <!-- Trigger params -->
              <div class="param-box" v-if="editor.trigger.type === 'time'">
                <label class="pf"><span>Time</span><input type="time" :value="timeVal(editor.trigger.params)" @input="setTimeVal($event, editor.trigger.params)" /></label>
                <label class="pf"><span>Days</span>
                  <div class="dow-row">
                    <button type="button" v-for="d in DAYS" :key="d.v" :class="{ on: dowHas(editor.trigger.params,d.v) }" @click="dowToggle(editor.trigger.params,d.v)">{{ d.l }}</button>
                  </div>
                </label>
              </div>
              <div class="param-box" v-else-if="editor.trigger.type === 'scheduled_once'">
                <label class="pf"><span>Run at</span><input type="datetime-local" :value="dtVal(editor.trigger.params)" @input="setDtVal($event,editor.trigger.params)" /></label>
              </div>
              <div class="param-box" v-else-if="editor.trigger.type === 'battery_level'">
                <label class="pf"><span>Operator</span>
                  <select :value="pv(editor.trigger.params,'op','<')" @change="sp($event,editor.trigger.params,'op')">
                    <option value="<">Less than (&lt;)</option><option value="<=">At most (≤)</option>
                    <option value=">">Greater than (&gt;)</option><option value=">=">At least (≥)</option>
                    <option value="==">Equal to (=)</option>
                  </select>
                </label>
                <label class="pf"><span>Threshold (%)</span><input type="number" min="0" max="100" :value="pv(editor.trigger.params,'threshold','20')" @input="sp($event,editor.trigger.params,'threshold')" /></label>
              </div>
              <div class="param-box" v-else-if="editor.trigger.type === 'battery_charging'">
                <label class="pf"><span>State</span>
                  <select :value="pv(editor.trigger.params,'state','true')" @change="sp($event,editor.trigger.params,'state')">
                    <option value="true">⚡ Plugged in / Charging</option>
                    <option value="false">🔌 Unplugged / Discharging</option>
                  </select>
                </label>
              </div>
              <div class="param-box" v-else-if="editor.trigger.type === 'incoming_call'">
                <label class="pf"><span>From number (blank = any)</span><input type="tel" :value="pv(editor.trigger.params,'from','')" @input="sp($event,editor.trigger.params,'from')" placeholder="+15551234567" /></label>
              </div>
              <div class="param-box" v-else-if="editor.trigger.type === 'sms_received'">
                <label class="pf"><span>From number (blank = any)</span><input type="tel" :value="pv(editor.trigger.params,'from','')" @input="sp($event,editor.trigger.params,'from')" placeholder="+15551234567" /></label>
                <label class="pf"><span>Body contains (blank = any)</span><input type="text" :value="pv(editor.trigger.params,'contains','')" @input="sp($event,editor.trigger.params,'contains')" /></label>
              </div>
              <div class="param-box" v-else-if="editor.trigger.type === 'wifi_connected'">
                <label class="pf"><span>SSID (blank = any)</span><input type="text" :value="pv(editor.trigger.params,'ssid','')" @input="sp($event,editor.trigger.params,'ssid')" placeholder="My Home WiFi" /></label>
              </div>
              <div class="param-box" v-else-if="editor.trigger.type === 'bluetooth_connected'">
                <label class="pf"><span>Device name (blank = any)</span><input type="text" :value="pv(editor.trigger.params,'name','')" @input="sp($event,editor.trigger.params,'name')" placeholder="My Headphones" /></label>
              </div>
              <div class="param-box" v-else-if="editor.trigger.type === 'app_launched'">
                <label class="pf"><span>Package name</span><input type="text" :value="pv(editor.trigger.params,'package','')" @input="sp($event,editor.trigger.params,'package')" placeholder="com.example.app" /></label>
              </div>
              <div class="param-box hint" v-else-if="editor.trigger.type === 'manual'">
                💡 This rule runs only when you tap <strong>Run now</strong> or call it from the Telegram bot.
              </div>
              <div class="param-box hint" v-else-if="editor.trigger.type === 'headphones'">
                🎧 Fires when headphones / earbuds are plugged in or removed.
              </div>
              <div class="param-box hint" v-else-if="editor.trigger.type === 'boot_completed'">
                🔄 Fires once every time the device finishes booting.
              </div>
            </div>

            <!-- ② CONDITIONS (rules only) -->
            <div class="sec" v-if="editor.kind !== 'schedule'">
              <div class="sec-hdr">
                <span class="sec-num">2</span>
                <span class="sec-title">Only if…</span>
                <span class="sec-sub">Conditions (optional — all must be true)</span>
              </div>
              <div v-if="editor.conditions.length === 0" class="no-cond">No conditions — rule always fires when triggered.</div>
              <div v-for="(c,i) in editor.conditions" :key="i" class="cond-card">
                <div class="cond-top">
                  <span class="cond-emoji">{{ COND_META[c.type]?.emoji }}</span>
                  <select v-model="c.type" @change="c.params=[]">
                    <option v-for="ct in COND_TYPES" :key="ct" :value="ct">{{ COND_META[ct]?.label }}</option>
                  </select>
                  <button class="icon-btn red sm" @click="editor.conditions.splice(i,1)"><i-lucide:trash-2 /></button>
                </div>
                <div class="cond-params">
                  <template v-if="c.type==='time_window'">
                    <label class="pf"><span>From</span><input type="time" :value="pv(c.params,'from','09:00')" @input="sp($event,c.params,'from')" /></label>
                    <label class="pf"><span>To</span><input type="time" :value="pv(c.params,'to','17:00')" @input="sp($event,c.params,'to')" /></label>
                  </template>
                  <template v-else-if="c.type==='day_of_week'">
                    <label class="pf"><span>Days</span>
                      <div class="dow-row">
                        <button type="button" v-for="d in DAYS" :key="d.v" :class="{ on: dowHas(c.params,d.v) }" @click="dowToggle(c.params,d.v)">{{ d.l }}</button>
                      </div>
                    </label>
                  </template>
                  <template v-else-if="c.type==='battery_level'">
                    <label class="pf"><span>Op</span>
                      <select :value="pv(c.params,'op','<')" @change="sp($event,c.params,'op')">
                        <option value="<">&lt;</option><option value=">">&gt;</option><option value="<=">&le;</option><option value=">=">&ge;</option>
                      </select>
                    </label>
                    <label class="pf"><span>Level %</span><input type="number" min="0" max="100" :value="pv(c.params,'threshold','20')" @input="sp($event,c.params,'threshold')" /></label>
                  </template>
                  <template v-else-if="c.type==='charging' || c.type==='silent_mode'">
                    <label class="pf"><span>State</span>
                      <select :value="pv(c.params,'state','true')" @change="sp($event,c.params,'state')">
                        <option value="true">ON / Active</option><option value="false">OFF / Inactive</option>
                      </select>
                    </label>
                  </template>
                  <template v-else-if="c.type==='wifi_ssid'">
                    <label class="pf"><span>SSID</span><input type="text" :value="pv(c.params,'ssid','')" @input="sp($event,c.params,'ssid')" placeholder="My Network" /></label>
                  </template>
                </div>
              </div>
              <button class="btn ghost sm add-btn" @click="editor.conditions.push({type:'time_window',params:[]})">
                <i-lucide:plus /> Add Condition
              </button>
            </div>

            <!-- ③ ACTIONS -->
            <div class="sec">
              <div class="sec-hdr">
                <span class="sec-num">{{ editor.kind==='schedule'?'2':'3' }}</span>
                <span class="sec-title">Do this…</span>
                <span class="sec-sub">Actions (run in order)</span>
              </div>

              <div v-if="editor.actions.length===0" class="no-cond">No actions yet — add one below.</div>

              <div v-for="(a,i) in editor.actions" :key="i" class="act-card">
                <div class="act-card-head">
                  <span class="step">{{ i+1 }}</span>
                  <span class="act-chip-icon">{{ ACT_META[a.type]?.emoji }}</span>
                  <span class="act-chip-name">{{ ACT_META[a.type]?.label || a.type }}</span>
                  <div class="act-card-btns">
                    <button class="icon-btn sm" :disabled="i===0" @click="moveAct(i,-1)"><i-lucide:chevron-up /></button>
                    <button class="icon-btn sm" :disabled="i===editor.actions.length-1" @click="moveAct(i,1)"><i-lucide:chevron-down /></button>
                    <button class="icon-btn red sm" @click="editor.actions.splice(i,1)"><i-lucide:trash-2 /></button>
                  </div>
                </div>
                <div class="act-params">

                  <!-- notify -->
                  <template v-if="a.type==='notify'">
                    <label class="pf"><span>Title</span><input type="text" :value="pv(a.params,'title','Automation')" @input="sp($event,a.params,'title')" placeholder="Notification title" /></label>
                    <label class="pf"><span>Body</span><textarea rows="2" :value="pv(a.params,'body','')" @input="sp($event,a.params,'body')" placeholder="Notification message… supports {{tokens}}"></textarea></label>
                  </template>

                  <!-- send_sms -->
                  <template v-else-if="a.type==='send_sms'">
                    <label class="pf"><span>To number</span><input type="tel" :value="pv(a.params,'to','')" @input="sp($event,a.params,'to')" placeholder="+15551234567" /></label>
                    <label class="pf"><span>Message</span><textarea rows="2" :value="pv(a.params,'body','')" @input="sp($event,a.params,'body')" placeholder="Hi! supports {{from_number}} {{now}}"></textarea></label>
                  </template>

                  <!-- make_call -->
                  <template v-else-if="a.type==='make_call'">
                    <label class="pf"><span>Phone number</span><input type="tel" :value="pv(a.params,'to','')" @input="sp($event,a.params,'to')" placeholder="+15551234567" /></label>
                  </template>

                  <!-- launch_app -->
                  <template v-else-if="a.type==='launch_app'">
                    <label class="pf"><span>Package name</span><input type="text" :value="pv(a.params,'package','')" @input="sp($event,a.params,'package')" placeholder="com.example.app" /></label>
                    <p class="hint">Example: com.whatsapp, com.google.android.youtube</p>
                  </template>

                  <!-- speak -->
                  <template v-else-if="a.type==='speak'">
                    <label class="pf"><span>Text to speak</span><textarea rows="2" :value="pv(a.params,'text','')" @input="sp($event,a.params,'text')" placeholder="Good morning! Battery is {{battery_level}}%"></textarea></label>
                  </template>

                  <!-- vibrate -->
                  <template v-else-if="a.type==='vibrate'">
                    <label class="pf">
                      <span>Duration — {{ pv(a.params,'ms','250') }}ms</span>
                      <input type="range" min="50" max="5000" step="50" :value="pv(a.params,'ms','250')" @input="sp($event,a.params,'ms')" />
                    </label>
                  </template>

                  <!-- set_ringer -->
                  <template v-else-if="a.type==='set_ringer'">
                    <label class="pf"><span>Mode</span></label>
                    <div class="seg-btns">
                      <button type="button" v-for="m in ['silent','vibrate','normal']" :key="m"
                        :class="{ on: pv(a.params,'mode','silent')===m }"
                        @click="spv(a.params,'mode',m)">
                        {{ m==='silent'?'🔇 Silent':m==='vibrate'?'📳 Vibrate':'🔔 Normal' }}
                      </button>
                    </div>
                  </template>

                  <!-- set_volume -->
                  <template v-else-if="a.type==='set_volume'">
                    <label class="pf"><span>Stream</span>
                      <select :value="pv(a.params,'stream','music')" @change="sp($event,a.params,'stream')">
                        <option value="music">🎵 Music</option>
                        <option value="ring">🔔 Ringtone</option>
                        <option value="alarm">⏰ Alarm</option>
                        <option value="notification">🔔 Notification</option>
                        <option value="voice">📞 Voice Call</option>
                      </select>
                    </label>
                    <label class="pf">
                      <span>Level — {{ pv(a.params,'percent','50') }}%</span>
                      <input type="range" min="0" max="100" :value="pv(a.params,'percent','50')" @input="sp($event,a.params,'percent')" />
                    </label>
                  </template>

                  <!-- toggle_wifi / toggle_bluetooth / toggle_dnd / flashlight -->
                  <template v-else-if="['toggle_wifi','toggle_bluetooth','toggle_dnd','flashlight'].includes(a.type)">
                    <label class="pf"><span>State</span></label>
                    <div class="seg-btns">
                      <button type="button" :class="{ on: pv(a.params,'state','true')==='true' }" @click="spv(a.params,'state','true')">✅ ON</button>
                      <button type="button" :class="{ on: pv(a.params,'state','true')==='false' }" @click="spv(a.params,'state','false')">❌ OFF</button>
                    </div>
                  </template>

                  <!-- set_clipboard -->
                  <template v-else-if="a.type==='set_clipboard'">
                    <label class="pf"><span>Text</span><textarea rows="2" :value="pv(a.params,'text','')" @input="sp($event,a.params,'text')" placeholder="Text to copy… supports {{tokens}}"></textarea></label>
                  </template>

                  <!-- send_webhook -->
                  <template v-else-if="a.type==='send_webhook'">
                    <div class="row2">
                      <label class="pf" style="flex:0 0 90px"><span>Method</span>
                        <select :value="pv(a.params,'method','POST')" @change="sp($event,a.params,'method')">
                          <option>POST</option><option>PUT</option><option>PATCH</option><option>GET</option><option>DELETE</option>
                        </select>
                      </label>
                      <label class="pf" style="flex:1"><span>URL</span><input type="url" :value="pv(a.params,'url','')" @input="sp($event,a.params,'url')" placeholder="https://example.com/webhook" /></label>
                    </div>
                    <label class="pf"><span>Body (JSON)</span><textarea rows="3" :value="pv(a.params,'body','')" @input="sp($event,a.params,'body')" placeholder='{"event":"triggered","ts":"{{ts"}}'></textarea></label>
                    <label class="pf"><span>Headers (one per line: Key: Value)</span><textarea rows="2" :value="pv(a.params,'headers','')" @input="sp($event,a.params,'headers')" placeholder="Authorization: Bearer token123"></textarea></label>
                    <label class="pf"><span>Save response as variable (optional)</span><input type="text" :value="pv(a.params,'captureAs','')" @input="sp($event,a.params,'captureAs')" placeholder="webhook_response" /></label>
                  </template>

                  <!-- http_get -->
                  <template v-else-if="a.type==='http_get'">
                    <label class="pf"><span>URL</span><input type="url" :value="pv(a.params,'url','')" @input="sp($event,a.params,'url')" placeholder="https://api.example.com/endpoint" /></label>
                    <label class="pf"><span>Save response as variable (optional)</span><input type="text" :value="pv(a.params,'captureAs','')" @input="sp($event,a.params,'captureAs')" placeholder="api_response" /></label>
                  </template>

                  <!-- delay -->
                  <template v-else-if="a.type==='delay'">
                    <label class="pf"><span>Wait duration</span>
                      <div class="row2">
                        <input type="number" min="100" max="600000" :value="pv(a.params,'ms','1000')" @input="sp($event,a.params,'ms')" style="flex:1" />
                        <span class="unit">ms ({{ Math.round(+pv(a.params,'ms','1000')/1000) }}s)</span>
                      </div>
                    </label>
                    <p class="hint">Use between actions to add a gap. Max 10 minutes (600000ms).</p>
                  </template>

                  <!-- take_photo -->
                  <template v-else-if="a.type==='take_photo'">
                    <label class="pf"><span>Camera</span>
                      <div class="seg-btns">
                        <button type="button" :class="{ on: pv(a.params,'camera','back')==='back' }" @click="spv(a.params,'camera','back')">📷 Back</button>
                        <button type="button" :class="{ on: pv(a.params,'camera','back')==='front' }" @click="spv(a.params,'camera','front')">🤳 Front</button>
                      </div>
                    </label>
                    <label class="pf"><span>Flash</span>
                      <div class="seg-btns">
                        <button type="button" :class="{ on: pv(a.params,'flash','off')==='off' }" @click="spv(a.params,'flash','off')">🌑 Off</button>
                        <button type="button" :class="{ on: pv(a.params,'flash','off')==='on' }" @click="spv(a.params,'flash','on')">⚡ On</button>
                        <button type="button" :class="{ on: pv(a.params,'flash','off')==='auto' }" @click="spv(a.params,'flash','auto')">🔄 Auto</button>
                      </div>
                    </label>
                  </template>

                  <!-- start_recording -->
                  <template v-else-if="a.type==='start_recording'">
                    <label class="pf">
                      <span>Duration — {{ pv(a.params,'seconds','30') }}s</span>
                      <input type="range" min="1" max="600" :value="pv(a.params,'seconds','30')" @input="sp($event,a.params,'seconds')" />
                    </label>
                    <label class="pf"><span>Source</span>
                      <div class="seg-btns">
                        <button type="button" :class="{ on: pv(a.params,'source','mic')==='mic' }" @click="spv(a.params,'source','mic')">🎙 Microphone</button>
                        <button type="button" :class="{ on: pv(a.params,'source','mic')==='cam' }" @click="spv(a.params,'source','cam')">📷 Camera</button>
                      </div>
                    </label>
                  </template>

                  <!-- stop_recording -->
                  <template v-else-if="a.type==='stop_recording'">
                    <p class="hint">Stops any active audio/video recording.</p>
                  </template>

                  <!-- lock_screen -->
                  <template v-else-if="a.type==='lock_screen'">
                    <p class="hint">Immediately locks the screen. Requires Device Admin permission (grant via ADB).</p>
                  </template>

                  <!-- set_variable -->
                  <template v-else-if="a.type==='set_variable'">
                    <label class="pf"><span>Variable name</span><input type="text" :value="pv(a.params,'key','')" @input="sp($event,a.params,'key')" placeholder="my_var" /></label>
                    <label class="pf"><span>Value (supports {{tokens}})</span><input type="text" :value="pv(a.params,'value','')" @input="sp($event,a.params,'value')" placeholder="{{battery_level}}" /></label>
                    <p class="hint">Variables can be referenced in later actions as <code>{{my_var}}</code>.</p>
                  </template>

                </div>
              </div>

              <!-- Add action button -->
              <button class="btn ghost add-btn" @click="actionPicker=true">
                <i-lucide:plus /> Add Action
              </button>
            </div>

            <!-- ④ ADVANCED -->
            <div class="sec sec-adv">
              <div class="sec-hdr" style="cursor:pointer" @click="advOpen=!advOpen">
                <span class="sec-num">⚙</span>
                <span class="sec-title">Advanced</span>
                <i-lucide:chevron-down :class="{ rotated: advOpen }" class="adv-chev" />
              </div>
              <div v-if="advOpen" class="adv-body">
                <label class="pf">
                  <span>Cooldown (ms) — minimum gap between runs</span>
                  <input type="number" min="0" v-model.number="editor.cooldownMs" placeholder="0 = no limit" />
                </label>
                <div class="preset-row">
                  <span class="hint">Quick set:</span>
                  <button type="button" class="pill" @click="editor.cooldownMs=0">None</button>
                  <button type="button" class="pill" @click="editor.cooldownMs=60000">1 min</button>
                  <button type="button" class="pill" @click="editor.cooldownMs=300000">5 min</button>
                  <button type="button" class="pill" @click="editor.cooldownMs=3600000">1 hr</button>
                  <button type="button" class="pill" @click="editor.cooldownMs=86400000">1 day</button>
                </div>
                <p class="hint">
                  Available tokens in action fields:
                  <code>{{now}}</code> <code>{{date}}</code> <code>{{time}}</code> <code>{{ts}}</code>
                  <code>{{battery_level}}</code> <code>{{from_number}}</code> <code>{{wifi_ssid}}</code>
                  <code>{{sms_body}}</code> <code>{{my_variable}}</code>
                </p>
              </div>
            </div>

          </div><!-- /drw-body -->

          <!-- Drawer footer -->
          <div class="drw-foot">
            <button class="btn ghost" @click="editor=null">Cancel</button>
            <label class="toggle-row">
              <input type="checkbox" v-model="editor.enabled" />
              <span>Enabled</span>
            </label>
            <button class="btn primary" @click="onSave" :disabled="saving">
              <i-lucide:loader-2 v-if="saving" class="spin" />
              <i-lucide:save v-else />
              {{ saving ? 'Saving…' : 'Save' }}
            </button>
          </div>
        </aside>

        <!-- ── Action Picker ── -->
        <div v-if="actionPicker" class="ap-back" @click.self="actionPicker=false">
          <div class="ap-panel">
            <div class="ap-head">
              <h4>Choose an action</h4>
              <button class="icon-btn" @click="actionPicker=false"><i-lucide:x /></button>
            </div>
            <div class="ap-cats">
              <div v-for="cat in ACT_CATS" :key="cat.label" class="ap-cat">
                <div class="ap-cat-lbl">{{ cat.label }}</div>
                <div class="ap-grid">
                  <button v-for="t in cat.types" :key="t" class="ap-card" @click="addAction(t)">
                    <span class="ap-emoji">{{ ACT_META[t]?.emoji }}</span>
                    <span class="ap-label">{{ ACT_META[t]?.label }}</span>
                    <span class="ap-desc">{{ ACT_META[t]?.desc }}</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

      </template>
    </Teleport>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { gqlFetch } from '@/lib/api/gql-client'
import {
  automationStateGQL, automationRulesGQL, automationRunsGQL,
  upsertAutomationRuleJsonGQL, setAutomationRuleEnabledGQL, deleteAutomationRuleGQL,
  runAutomationRuleGQL, setAutomationEnabledGQL, clearAutomationRunsGQL,
} from '@/lib/api/query'

// ─── Types ───────────────────────────────────────────────────────
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

// ─── Metadata ────────────────────────────────────────────────────
const TRIG_META: Record<string, { emoji: string; label: string; short: string }> = {
  manual:             { emoji: '👆', label: 'Manual trigger',        short: 'Manual' },
  time:               { emoji: '🕐', label: 'Daily schedule',         short: 'Daily' },
  scheduled_once:     { emoji: '📅', label: 'Run once at date/time', short: 'Once' },
  battery_level:      { emoji: '🔋', label: 'Battery level',          short: 'Battery' },
  battery_charging:   { emoji: '⚡', label: 'Charger plugged/unplugged', short: 'Charger' },
  incoming_call:      { emoji: '📞', label: 'Incoming call',          short: 'Incoming call' },
  sms_received:       { emoji: '💬', label: 'SMS received',           short: 'SMS' },
  wifi_connected:     { emoji: '📶', label: 'Wi-Fi connected',        short: 'Wi-Fi' },
  bluetooth_connected:{ emoji: '🔵', label: 'Bluetooth connected',    short: 'Bluetooth' },
  app_launched:       { emoji: '🚀', label: 'App launched',           short: 'App launch' },
  headphones:         { emoji: '🎧', label: 'Headphones plugged in',  short: 'Headphones' },
  boot_completed:     { emoji: '🔄', label: 'Device boot',            short: 'Boot' },
}

const ACT_META: Record<string, { emoji: string; label: string; desc: string }> = {
  notify:          { emoji: '🔔', label: 'Push Notification',    desc: 'Show a device notification' },
  send_sms:        { emoji: '💬', label: 'Send SMS',             desc: 'Send a text message' },
  make_call:       { emoji: '📞', label: 'Make Call',            desc: 'Initiate a phone call' },
  launch_app:      { emoji: '🚀', label: 'Launch App',           desc: 'Open an installed app' },
  speak:           { emoji: '🔊', label: 'Text to Speech',       desc: 'Speak text aloud via TTS' },
  vibrate:         { emoji: '📳', label: 'Vibrate',              desc: 'Vibrate the device' },
  set_ringer:      { emoji: '🔇', label: 'Set Ringer Mode',      desc: 'Silent / Vibrate / Normal' },
  set_volume:      { emoji: '🔉', label: 'Set Volume',           desc: 'Adjust a volume stream' },
  toggle_wifi:     { emoji: '📶', label: 'Toggle Wi-Fi',         desc: 'Turn Wi-Fi on or off' },
  toggle_bluetooth:{ emoji: '🔵', label: 'Toggle Bluetooth',     desc: 'Turn Bluetooth on or off' },
  toggle_dnd:      { emoji: '🌙', label: 'Toggle Do Not Disturb',desc: 'Enable or disable DND mode' },
  flashlight:      { emoji: '🔦', label: 'Flashlight',           desc: 'Turn torch on or off' },
  set_clipboard:   { emoji: '📋', label: 'Set Clipboard',        desc: 'Copy text to clipboard' },
  send_webhook:    { emoji: '🌐', label: 'HTTP Webhook',         desc: 'POST/PUT data to a URL' },
  http_get:        { emoji: '🔗', label: 'HTTP GET',             desc: 'Fetch a URL (GET request)' },
  lock_screen:     { emoji: '🔒', label: 'Lock Screen',          desc: 'Immediately lock the device' },
  take_photo:      { emoji: '📷', label: 'Take Photo',           desc: 'Capture with front/back camera' },
  start_recording: { emoji: '🎙', label: 'Start Recording',      desc: 'Record audio or video' },
  stop_recording:  { emoji: '⏹', label: 'Stop Recording',       desc: 'Stop any active recording' },
  delay:           { emoji: '⏳', label: 'Wait / Delay',         desc: 'Pause before next action' },
  set_variable:    { emoji: '🏷', label: 'Set Variable',         desc: 'Store a value for later use' },
}

const ACT_CATS = [
  { label: '📣 Alerts & Comms', types: ['notify', 'send_sms', 'make_call', 'speak'] },
  { label: '📱 Device Control', types: ['set_ringer', 'set_volume', 'toggle_wifi', 'toggle_bluetooth', 'toggle_dnd', 'flashlight', 'lock_screen'] },
  { label: '📷 Media & Input', types: ['take_photo', 'start_recording', 'stop_recording', 'set_clipboard', 'vibrate'] },
  { label: '🌐 Network & Web', types: ['send_webhook', 'http_get'] },
  { label: '🤖 Apps & Logic', types: ['launch_app', 'delay', 'set_variable'] },
]

const COND_META: Record<string, { emoji: string; label: string }> = {
  time_window:  { emoji: '🕐', label: 'Time window' },
  day_of_week:  { emoji: '📅', label: 'Day of week' },
  battery_level:{ emoji: '🔋', label: 'Battery level' },
  charging:     { emoji: '⚡', label: 'Charging state' },
  wifi_ssid:    { emoji: '📶', label: 'Wi-Fi SSID' },
  silent_mode:  { emoji: '🔇', label: 'Silent mode' },
}
const COND_TYPES = Object.keys(COND_META)

const DAYS = [
  { v: 1, l: 'Mon' }, { v: 2, l: 'Tue' }, { v: 3, l: 'Wed' }, { v: 4, l: 'Thu' },
  { v: 5, l: 'Fri' }, { v: 6, l: 'Sat' }, { v: 7, l: 'Sun' },
]

// ─── State ────────────────────────────────────────────────────────
const state  = ref<any>(null)
const rules  = ref<Rule[]>([])
const runs   = ref<Run[]>([])
const tab    = ref<'rules'|'schedules'|'history'>('rules')
const editor = ref<Rule | null>(null)
const saving = ref(false)
const actionPicker = ref(false)
const showTemplates = ref(false)
const advOpen = ref(false)

const filteredRules = computed(() =>
  (rules.value ?? []).filter(r => tab.value === 'schedules' ? r.kind === 'schedule' : r.kind !== 'schedule')
)

// ─── Templates ────────────────────────────────────────────────────
const templates = [
  { emoji:'🔋', name:'Low Battery Alert',    desc:'Notify when battery drops below 20%',
    kind:'rule', trigger:{type:'battery_level',params:{op:'<',threshold:'20'}},
    conditions:[], actions:[{type:'notify',params:{title:'Low Battery! 🔋',body:'Battery is at {{battery_level}}% — please charge your device.'}}] },
  { emoji:'🌙', name:'Bedtime Silence',      desc:'Go silent and enable DND at 10:30 PM',
    kind:'schedule', trigger:{type:'time',params:{hour:'22',minute:'30',days:'1,2,3,4,5,6,7'}},
    conditions:[], actions:[{type:'set_ringer',params:{mode:'silent'}},{type:'toggle_dnd',params:{state:'true'}}] },
  { emoji:'☀️', name:'Morning Routine',      desc:'Volume up and Wi-Fi on at 7 AM',
    kind:'schedule', trigger:{type:'time',params:{hour:'7',minute:'0',days:'1,2,3,4,5'}},
    conditions:[], actions:[{type:'set_volume',params:{stream:'music',percent:'70'}},{type:'toggle_wifi',params:{state:'true'}}] },
  { emoji:'📱', name:'Find My Phone',        desc:'Vibrate, flash, and speak to locate your phone',
    kind:'rule', trigger:{type:'manual',params:{}},
    conditions:[], actions:[{type:'vibrate',params:{ms:'3000'}},{type:'flashlight',params:{state:'true'}},{type:'speak',params:{text:"I'm here! I'm here! Please pick me up!"}}] },
  { emoji:'⚡', name:'Charging Started',     desc:'Speak confirmation when charger is plugged in',
    kind:'rule', trigger:{type:'battery_charging',params:{state:'true'}},
    conditions:[], actions:[{type:'speak',params:{text:'Charging started. Battery is at {{battery_level}} percent.'}}] },
  { emoji:'🏠', name:'Home Wi-Fi Arrival',   desc:'Set normal ringer when connecting to home network',
    kind:'rule', trigger:{type:'wifi_connected',params:{ssid:'My Home WiFi'}},
    conditions:[], actions:[{type:'set_ringer',params:{mode:'normal'}},{type:'toggle_dnd',params:{state:'false'}}] },
  { emoji:'📩', name:'SMS Auto-Reply',       desc:'Auto-reply to any SMS with your status',
    kind:'rule', trigger:{type:'sms_received',params:{from:'',contains:''}},
    conditions:[], actions:[{type:'send_sms',params:{to:'{{from_number}}',body:"I'm busy right now. I'll get back to you soon!"}}] },
  { emoji:'🔗', name:'Daily API Call',       desc:'Hit a webhook URL every morning at 6 AM',
    kind:'schedule', trigger:{type:'time',params:{hour:'6',minute:'0',days:'1,2,3,4,5,6,7'}},
    conditions:[], actions:[{type:'http_get',params:{url:'https://example.com/daily-trigger',captureAs:''}}] },
  { emoji:'🔒', name:'Night Lock & Mute',    desc:'Lock screen and mute at 11 PM',
    kind:'schedule', trigger:{type:'time',params:{hour:'23',minute:'0',days:'1,2,3,4,5,6,7'}},
    conditions:[], actions:[{type:'set_ringer',params:{mode:'silent'}},{type:'lock_screen',params:{}}] },
  { emoji:'📊', name:'Battery Webhook',      desc:'POST battery status to a server when below 30%',
    kind:'rule', trigger:{type:'battery_level',params:{op:'<',threshold:'30'}},
    conditions:[], actions:[{type:'send_webhook',params:{method:'POST',url:'https://example.com/battery',body:'{"level":"{{battery_level}}","ts":"{{ts}}"}'}}] },
]

function applyTemplate(t: any) {
  const toKv = (o: Record<string,string>) => Object.entries(o).map(([key,value])=>({key,value}))
  editor.value = {
    id:'', name: t.name, enabled: true, kind: t.kind,
    cooldownMs: 0, lastRunMs: 0, createdMs: 0, updatedMs: 0,
    trigger: { type: t.trigger.type, params: toKv(t.trigger.params) },
    conditions: t.conditions.map((c:any) => ({ type: c.type, params: toKv(c.params||{}) })),
    actions: t.actions.map((a:any) => ({ type: a.type, params: toKv(a.params||{}) })),
  }
  if (t.trigger.type === 'time') {
    const params = editor.value.trigger.params
    if (!params.find(p=>p.key==='days')) params.push({key:'days',value:'1,2,3,4,5,6,7'})
  }
  showTemplates.value = false
  tab.value = t.kind === 'schedule' ? 'schedules' : 'rules'
  advOpen.value = false
}

// ─── Loaders ──────────────────────────────────────────────────────
async function load() {
  const [s, r, h] = await Promise.allSettled([
    gqlFetch(automationStateGQL, {}),
    gqlFetch(automationRulesGQL, {}),
    gqlFetch(automationRunsGQL, { limit: 100 }),
  ])
  if (s.status==='fulfilled' && !(s.value as any).errors) state.value = (s.value as any).data?.automationState ?? null
  if (r.status==='fulfilled' && !(r.value as any).errors) rules.value = (r.value as any).data?.automationRules ?? []
  if (h.status==='fulfilled' && !(h.value as any).errors) runs.value = (h.value as any).data?.automationRuns ?? []
}
onMounted(load)

// ─── Param helpers ────────────────────────────────────────────────
function pv(arr: Kv[], k: string, def=''): string { return arr.find(p=>p.key===k)?.value ?? def }
function sp(e: Event, arr: Kv[], k: string) { spv(arr, k, (e.target as HTMLInputElement).value) }
function spv(arr: Kv[], k: string, v: string) {
  const i = arr.findIndex(p=>p.key===k)
  if (i>=0) arr[i].value = v; else arr.push({key:k,value:v})
}
function timeVal(arr: Kv[]): string {
  return `${String(pv(arr,'hour','9')).padStart(2,'0')}:${String(pv(arr,'minute','0')).padStart(2,'0')}`
}
function setTimeVal(e: Event, arr: Kv[]) {
  const [h,m] = (e.target as HTMLInputElement).value.split(':')
  spv(arr,'hour',h); spv(arr,'minute',m)
}
function dtVal(arr: Kv[]): string {
  const ms = parseInt(pv(arr,'atMs','0')) || (Date.now()+60_000)
  const d = new Date(ms), pad=(n:number)=>String(n).padStart(2,'0')
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}
function setDtVal(e: Event, arr: Kv[]) {
  const v = (e.target as HTMLInputElement).value
  if (!v) return
  spv(arr,'atMs',String(new Date(v).getTime()))
}
function dowHas(arr: Kv[], d: number): boolean {
  return (pv(arr,'days','1,2,3,4,5,6,7')||'').split(',').map(Number).includes(d)
}
function dowToggle(arr: Kv[], d: number) {
  let days = (pv(arr,'days','1,2,3,4,5,6,7')||'').split(',').map(Number).filter(Boolean)
  days = days.includes(d) ? days.filter(x=>x!==d) : [...days,d].sort()
  spv(arr,'days',days.join(','))
}
function kvToObj(arr: Kv[]): Record<string,string> {
  const o: Record<string,string>={}
  for (const p of arr||[]) if(p.key) o[p.key]=p.value
  return o
}
function triggerTypesFor(kind: string) {
  return kind==='schedule'
    ? ['scheduled_once','time']
    : ['manual','time','battery_level','battery_charging','incoming_call','sms_received','wifi_connected','bluetooth_connected','app_launched','headphones','boot_completed']
}

// ─── Trigger type change ───────────────────────────────────────────
function setTriggerType(tt: string) {
  if (!editor.value) return
  editor.value.trigger.type = tt
  editor.value.trigger.params = []
  if (tt==='time') { spv(editor.value.trigger.params,'hour','21'); spv(editor.value.trigger.params,'minute','0'); spv(editor.value.trigger.params,'days','1,2,3,4,5,6,7') }
  if (tt==='scheduled_once') spv(editor.value.trigger.params,'atMs',String(Date.now()+3_600_000))
}

// ─── Editor ───────────────────────────────────────────────────────
function blank(kind: string): Rule {
  const trig = kind==='schedule' ? 'scheduled_once' : 'manual'
  const r: Rule = {
    id:'', name: kind==='schedule'?'New Schedule':'New Rule',
    enabled:true, kind, cooldownMs:0, lastRunMs:0, createdMs:0, updatedMs:0,
    trigger:{type:trig,params:[]},
    conditions:[],
    actions:[{type:'notify',params:[{key:'title',value:'Hello!'},{key:'body',value:'Your automation ran ✅'}]}],
  }
  if (trig==='scheduled_once') spv(r.trigger.params,'atMs',String(Date.now()+3_600_000))
  return r
}
function openNew(kind: string) {
  editor.value = blank(kind)
  actionPicker.value = false
  advOpen.value = false
  tab.value = kind==='schedule'?'schedules':'rules'
}
function openEdit(r: Rule) {
  editor.value = JSON.parse(JSON.stringify(r))
  actionPicker.value = false
  advOpen.value = false
}
function addAction(type: string) {
  if (!editor.value) return
  editor.value.actions.push({type, params:[]})
  actionPicker.value = false
}
function moveAct(i: number, delta: number) {
  if (!editor.value) return
  const arr = editor.value.actions
  const j = i+delta
  if (j<0||j>=arr.length) return
  const [it]=arr.splice(i,1); arr.splice(j,0,it)
}

async function onSave() {
  if (!editor.value) return
  saving.value = true
  try {
    const e = editor.value
    const payload = {
      id:e.id, name:e.name||'Untitled', enabled:e.enabled,
      kind:e.kind, cooldownMs:e.cooldownMs||0,
      trigger:{type:e.trigger.type,params:kvToObj(e.trigger.params)},
      conditions:e.conditions.map(c=>({type:c.type,params:kvToObj(c.params)})),
      actions:e.actions.map(a=>({type:a.type,params:kvToObj(a.params)})),
    }
    const res: any = await gqlFetch(upsertAutomationRuleJsonGQL,{ruleJson:JSON.stringify(payload)})
    if (res?.errors?.length) { alert(res.errors.map((x:any)=>x.message).join('\n')); return }
    editor.value = null
    await load()
  } finally { saving.value = false }
}

function errCheck(res: any): boolean {
  if (res?.errors?.length) { alert(res.errors.map((x:any)=>x.message).join('\n')); return true }
  return false
}
async function toggleEnabled(r: Rule) {
  const res:any = await gqlFetch(setAutomationRuleEnabledGQL,{id:r.id,enabled:!r.enabled})
  if (!errCheck(res)) await load()
}
async function runNow(r: Rule) {
  const res:any = await gqlFetch(runAutomationRuleGQL,{id:r.id})
  if (!errCheck(res)) setTimeout(load,800)
}
async function confirmDelete(r: Rule) {
  if (!confirm(`Delete "${r.name}"?`)) return
  const res:any = await gqlFetch(deleteAutomationRuleGQL,{id:r.id})
  if (!errCheck(res)) await load()
}
async function onEnabledChange(e: Event) {
  const res:any = await gqlFetch(setAutomationEnabledGQL,{enabled:(e.target as HTMLInputElement).checked})
  if (!errCheck(res)) await load()
}
async function enableEngine() {
  const res:any = await gqlFetch(setAutomationEnabledGQL,{enabled:true})
  if (!errCheck(res)) await load()
}
async function onClearRuns() {
  if (!confirm('Clear all run history?')) return
  const res:any = await gqlFetch(clearAutomationRunsGQL,{})
  if (!errCheck(res)) await load()
}

// ─── Labels ───────────────────────────────────────────────────────
function actLabel(a: Action): string {
  switch(a.type) {
    case 'send_sms':        return `SMS → ${pv(a.params,'to','?')}`
    case 'make_call':       return `Call ${pv(a.params,'to','?')}`
    case 'launch_app':      return `Open ${pv(a.params,'package','?')}`
    case 'set_ringer':      return `Ringer: ${pv(a.params,'mode','')}`
    case 'set_volume':      return `${pv(a.params,'stream','')} volume = ${pv(a.params,'percent','')}%`
    case 'toggle_wifi':     return `Wi-Fi ${pv(a.params,'state','true')==='true'?'ON':'OFF'}`
    case 'toggle_bluetooth':return `Bluetooth ${pv(a.params,'state','true')==='true'?'ON':'OFF'}`
    case 'toggle_dnd':      return `DND ${pv(a.params,'state','true')==='true'?'ON':'OFF'}`
    case 'flashlight':      return `Torch ${pv(a.params,'state','true')==='true'?'ON':'OFF'}`
    case 'speak':           return `"${pv(a.params,'text','').slice(0,40)}"`
    case 'notify':          return `"${pv(a.params,'title','')}"`
    case 'send_webhook':    return `${pv(a.params,'method','POST')} ${pv(a.params,'url','').slice(0,40)}`
    case 'http_get':        return `GET ${pv(a.params,'url','').slice(0,40)}`
    case 'delay':           return `Wait ${pv(a.params,'ms','0')}ms`
    case 'vibrate':         return `Vibrate ${pv(a.params,'ms','0')}ms`
    case 'set_variable':    return `${pv(a.params,'key','?')} = ${pv(a.params,'value','')}`
    case 'take_photo':      return `Photo (${pv(a.params,'camera','back')})`
    case 'start_recording': return `Record ${pv(a.params,'seconds','30')}s`
    default: return ACT_META[a.type]?.label || a.type
  }
}

// ─── Formatting ───────────────────────────────────────────────────
function fmtRel(ms: number): string {
  if (!ms) return ''
  const diff = ms - Date.now(), abs = Math.abs(diff), sign = diff < 0
  const s = Math.floor(abs/1000)
  if (s<60) return sign?`${s}s ago`:`in ${s}s`
  const m = Math.floor(s/60)
  if (m<60) return sign?`${m}m ago`:`in ${m}m`
  const h = Math.floor(m/60)
  if (h<24) return sign?`${h}h ago`:`in ${h}h`
  return new Date(ms).toLocaleString()
}
function fmtDur(ms: number): string {
  if (ms<1000) return `${ms}ms`
  if (ms<60_000) return `${Math.round(ms/1000)}s`
  if (ms<3_600_000) return `${Math.round(ms/60_000)}m`
  return `${Math.round(ms/3_600_000)}h`
}
</script>

<style scoped>
/* ── Root ── */
.ar { padding: 18px 22px 60px; max-width: 1100px; margin: 0 auto; display: flex; flex-direction: column; gap: 18px; }

/* ── Header ── */
.ar-head { display: flex; flex-wrap: wrap; align-items: center; gap: 14px; }
.back-link { display: inline-flex; align-items: center; gap: 4px; text-decoration: none; color: var(--md-sys-color-on-surface-variant); font-size: 0.83rem; }
.back-link:hover { color: var(--md-sys-color-primary); }
.head-main { display: flex; align-items: center; gap: 12px; flex: 1 1 200px; }
.hd-icon { width: 32px; height: 32px; color: #f59e0b; }
.hd-title { margin: 0; font-size: 1.4rem; font-weight: 700; }
.hd-sub { margin: 3px 0 0; color: var(--md-sys-color-on-surface-variant); font-size: 0.83rem; }
.head-acts { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }

/* ── Engine toggle ── */
.engine-toggle { display: inline-flex; align-items: center; gap: 8px; cursor: pointer; font-weight: 600; font-size: 0.82rem; }
.engine-toggle input { display: none; }
.et-track { width: 40px; height: 22px; border-radius: 11px; background: var(--md-sys-color-outline); position: relative; transition: background 0.2s; }
.engine-toggle.on .et-track { background: #f59e0b; }
.et-thumb { position: absolute; width: 16px; height: 16px; border-radius: 50%; background: white; top: 3px; left: 3px; transition: transform 0.2s; }
.engine-toggle.on .et-thumb { transform: translateX(18px); }
.et-lbl { color: var(--md-sys-color-on-surface-variant); }
.engine-toggle.on .et-lbl { color: #f59e0b; }

/* ── Buttons ── */
.btn { display: inline-flex; align-items: center; gap: 6px; padding: 8px 14px; border-radius: 10px; border: 1px solid var(--md-sys-color-outline-variant); background: var(--md-sys-color-surface-container); color: inherit; cursor: pointer; font-weight: 600; font-size: 0.85rem; transition: all 0.15s; }
.btn svg { width: 16px; height: 16px; }
.btn:hover { background: var(--md-sys-color-surface-container-high); }
.btn.primary { background: linear-gradient(135deg,#f59e0b,#ef4444); color: white; border: none; }
.btn.primary:hover { filter: brightness(1.06); }
.btn.accent { background: linear-gradient(135deg,#6366f1,#8b5cf6); color: white; border: none; }
.btn.accent:hover { filter: brightness(1.06); }
.btn.ghost { background: transparent; }
.btn.sm { padding: 5px 10px; font-size: 0.78rem; }
.btn:disabled { opacity: 0.4; cursor: not-allowed; }

.icon-btn { width: 32px; height: 32px; border-radius: 8px; border: none; background: transparent; color: var(--md-sys-color-on-surface-variant); cursor: pointer; display: inline-flex; align-items: center; justify-content: center; transition: background 0.15s; }
.icon-btn:hover { background: var(--md-sys-color-surface-container-high); color: inherit; }
.icon-btn.red:hover { background: rgba(239,68,68,0.12); color: #dc2626; }
.icon-btn.green:hover { background: rgba(34,197,94,0.12); color: #16a34a; }
.icon-btn:disabled { opacity: 0.3; cursor: not-allowed; }
.icon-btn svg { width: 15px; height: 15px; }
.icon-btn.sm { width: 26px; height: 26px; }

/* ── Engine warning ── */
.engine-warn { display: flex; align-items: center; gap: 12px; padding: 14px 18px; background: rgba(245,158,11,0.1); border: 1px solid rgba(245,158,11,0.35); border-radius: 12px; font-size: 0.88rem; }
.ew-icon { width: 22px; height: 22px; color: #f59e0b; flex-shrink: 0; }
.engine-warn > div { flex: 1; }

/* ── Stats ── */
.stat-strip { display: grid; grid-template-columns: repeat(auto-fit,minmax(160px,1fr)); gap: 10px; }
.stat { padding: 14px 18px; background: var(--md-sys-color-surface-container); border-radius: 14px; border: 1px solid var(--md-sys-color-outline-variant); }
.stat-n { font-size: 1.6rem; font-weight: 800; color: #f59e0b; line-height: 1; }
.stat-d { font-size: 1rem; font-weight: 500; color: var(--md-sys-color-on-surface-variant); }
.stat-l { font-size: 0.75rem; color: var(--md-sys-color-on-surface-variant); margin-top: 4px; }

/* ── Template gallery ── */
.tpl-gallery { background: var(--md-sys-color-surface-container); border-radius: 18px; border: 1px solid var(--md-sys-color-outline-variant); padding: 18px; }
.tpl-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.tpl-head h3 { margin: 0; font-size: 1rem; font-weight: 700; display: inline-flex; align-items: center; gap: 8px; }
.tpl-grid { display: grid; grid-template-columns: repeat(auto-fill,minmax(200px,1fr)); gap: 10px; }
.tpl-card { display: flex; flex-direction: column; align-items: flex-start; gap: 4px; padding: 14px; border-radius: 12px; border: 1px solid var(--md-sys-color-outline-variant); background: var(--md-sys-color-surface); cursor: pointer; text-align: left; transition: all 0.15s; }
.tpl-card:hover { border-color: #f59e0b; background: rgba(245,158,11,0.05); transform: translateY(-1px); }
.tpl-emoji { font-size: 1.5rem; }
.tpl-name { font-weight: 700; font-size: 0.88rem; }
.tpl-desc { font-size: 0.74rem; color: var(--md-sys-color-on-surface-variant); }

/* ── Tabs ── */
.tabs { display: flex; gap: 2px; border-bottom: 2px solid var(--md-sys-color-outline-variant); }
.tabs button { display: inline-flex; align-items: center; gap: 6px; padding: 10px 16px; background: transparent; border: none; color: var(--md-sys-color-on-surface-variant); font-weight: 600; cursor: pointer; border-bottom: 2px solid transparent; margin-bottom: -2px; font-size: 0.85rem; transition: color 0.15s; }
.tabs button.on { color: #f59e0b; border-color: #f59e0b; }
.tabs button svg { width: 16px; height: 16px; }
.tab-badge { background: var(--md-sys-color-surface-container-high); padding: 1px 7px; border-radius: 999px; font-size: 0.7rem; }
.tabs button.on .tab-badge { background: rgba(245,158,11,0.2); color: #b45309; }

/* ── Rule list ── */
.rule-list { display: flex; flex-direction: column; gap: 10px; }
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 10px; padding: 48px 24px; color: var(--md-sys-color-on-surface-variant); text-align: center; }
.es-icon { width: 40px; height: 40px; opacity: 0.4; }
.es-btns { display: flex; gap: 8px; }

.rule-card { display: flex; align-items: flex-start; gap: 12px; padding: 14px 16px; background: var(--md-sys-color-surface-container); border: 1px solid var(--md-sys-color-outline-variant); border-radius: 14px; transition: border-color 0.2s; }
.rule-card:hover { border-color: #f59e0b; }
.rule-card.off { opacity: 0.5; }
.rc-left { flex-shrink: 0; }
.pwr-btn { width: 40px; height: 40px; border-radius: 10px; border: 1px solid var(--md-sys-color-outline-variant); background: var(--md-sys-color-surface-container-high); color: var(--md-sys-color-on-surface-variant); display: flex; align-items: center; justify-content: center; cursor: pointer; transition: all 0.2s; }
.pwr-btn.on { background: rgba(245,158,11,0.15); color: #d97706; border-color: rgba(245,158,11,0.4); }
.pwr-btn svg { width: 18px; height: 18px; }
.rc-body { flex: 1; min-width: 0; }
.rc-top { display: flex; align-items: flex-start; gap: 10px; flex-wrap: wrap; }
.rc-name { margin: 0; font-size: 1rem; font-weight: 700; }
.rc-badges { display: flex; gap: 5px; flex-wrap: wrap; margin-top: 5px; }
.badge { display: inline-flex; align-items: center; gap: 3px; padding: 2px 8px; border-radius: 999px; font-size: 0.68rem; font-weight: 600; }
.badge.trig { background: rgba(245,158,11,0.15); color: #b45309; }
.badge.cond { background: rgba(99,102,241,0.15); color: #4338ca; }
.badge.neutral { background: var(--md-sys-color-surface-container-high); color: var(--md-sys-color-on-surface-variant); }
.badge.dim { color: var(--md-sys-color-on-surface-variant); }
.badge svg { width: 10px; height: 10px; }
.act-chain { list-style: none; margin: 8px 0 0; padding: 0; display: flex; flex-direction: column; gap: 4px; }
.act-chain li { display: inline-flex; align-items: center; gap: 6px; padding: 3px 8px; background: var(--md-sys-color-surface-container-high); border-radius: 6px; font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); }
.step { display: inline-flex; align-items: center; justify-content: center; width: 18px; height: 18px; border-radius: 50%; background: rgba(245,158,11,0.2); color: #d97706; font-size: 0.65rem; font-weight: 800; flex-shrink: 0; }
.act-icon { font-size: 0.85rem; }
.act-lbl { color: var(--md-sys-color-on-surface); }
.rc-actions { display: flex; gap: 3px; }

/* ── History ── */
.history { display: flex; flex-direction: column; gap: 8px; }
.hist-head { display: flex; justify-content: space-between; align-items: center; }
.hist-head h3 { margin: 0; }
.run-row { display: flex; gap: 12px; padding: 12px 14px; background: var(--md-sys-color-surface-container); border-radius: 12px; border-left: 3px solid #94a3b8; }
.run-row.ok { border-color: #22c55e; }
.run-row.fail { border-color: #ef4444; }
.run-icon { flex-shrink: 0; width: 20px; }
.run-icon svg { width: 20px; height: 20px; }
.run-row.ok .run-icon { color: #22c55e; }
.run-row.fail .run-icon { color: #ef4444; }
.run-info { flex: 1; min-width: 0; }
.run-name { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; font-size: 0.88rem; }
.src { font-size: 0.68rem; color: var(--md-sys-color-on-surface-variant); padding: 1px 6px; border-radius: 4px; background: var(--md-sys-color-surface-container-high); text-transform: uppercase; }
.run-ts { font-size: 0.72rem; color: var(--md-sys-color-on-surface-variant); margin-left: auto; }
.run-log { list-style: none; margin: 4px 0 0; padding: 0; font-family: ui-monospace,monospace; font-size: 0.75rem; color: var(--md-sys-color-on-surface-variant); }
.run-log li { padding: 1px 0; }

/* ══════════════════════════════════════════ */
/* DRAWER — Teleported to body               */
/* ══════════════════════════════════════════ */
.backdrop { position: fixed; inset: 0; background: rgba(0,0,0,0.45); z-index: 1000; }

.drawer {
  position: fixed; right: 0; top: 0; height: 100dvh; width: min(720px, 100vw);
  background: var(--md-sys-color-surface); z-index: 1001;
  display: flex; flex-direction: column;
  box-shadow: -8px 0 40px rgba(0,0,0,0.25);
  animation: slideIn 0.22s cubic-bezier(0.16,1,0.3,1);
}
@keyframes slideIn { from { transform: translateX(100%); } to { transform: translateX(0); } }

.drw-head {
  display: flex; align-items: center; gap: 10px;
  padding: 14px 18px; border-bottom: 1px solid var(--md-sys-color-outline-variant);
  flex-shrink: 0;
}
.drw-kind-badge { padding: 3px 10px; border-radius: 6px; font-size: 0.75rem; font-weight: 700; background: rgba(245,158,11,0.15); color: #b45309; white-space: nowrap; }
.drw-kind-badge.schedule { background: rgba(99,102,241,0.15); color: #4338ca; }
.drw-name { flex: 1; font-size: 1rem; font-weight: 700; border: none; background: transparent; color: inherit; outline: none; min-width: 0; padding: 4px 8px; border-radius: 6px; }
.drw-name:focus { background: var(--md-sys-color-surface-container); }

.drw-body { flex: 1; overflow-y: auto; padding: 18px; display: flex; flex-direction: column; gap: 16px; }

.drw-foot {
  display: flex; align-items: center; gap: 10px; justify-content: flex-end;
  padding: 14px 18px; border-top: 1px solid var(--md-sys-color-outline-variant);
  flex-shrink: 0;
}
.toggle-row { display: inline-flex; align-items: center; gap: 6px; font-size: 0.85rem; font-weight: 600; cursor: pointer; margin-right: auto; }
.toggle-row input { accent-color: #f59e0b; width: 16px; height: 16px; }

/* ── Sections ── */
.sec { display: flex; flex-direction: column; gap: 12px; padding: 16px; border-radius: 14px; background: var(--md-sys-color-surface-container); border: 1px solid var(--md-sys-color-outline-variant); }
.sec-hdr { display: flex; align-items: center; gap: 10px; }
.sec-num { width: 26px; height: 26px; border-radius: 50%; background: #f59e0b; color: white; display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 0.8rem; flex-shrink: 0; }
.sec-title { font-weight: 700; font-size: 1rem; }
.sec-sub { color: var(--md-sys-color-on-surface-variant); font-size: 0.75rem; margin-left: auto; }
.sec-adv .sec-num { background: var(--md-sys-color-surface-container-high); color: var(--md-sys-color-on-surface-variant); }

/* ── Trigger grid ── */
.trig-grid { display: flex; flex-wrap: wrap; gap: 6px; }
.trig-chip { display: inline-flex; flex-direction: column; align-items: center; gap: 2px; padding: 8px 12px; border-radius: 10px; border: 1px solid var(--md-sys-color-outline-variant); background: var(--md-sys-color-surface); cursor: pointer; font-size: 0.72rem; font-weight: 600; color: var(--md-sys-color-on-surface-variant); transition: all 0.15s; min-width: 72px; }
.trig-chip > span:first-child { font-size: 1.3rem; }
.trig-chip:hover { border-color: #f59e0b; background: rgba(245,158,11,0.06); }
.trig-chip.on { border-color: #f59e0b; background: rgba(245,158,11,0.12); color: #b45309; }

/* ── Param box ── */
.param-box { display: flex; flex-direction: column; gap: 10px; padding: 12px; background: var(--md-sys-color-surface); border-radius: 10px; border: 1px solid var(--md-sys-color-outline-variant); }
.param-box.hint { font-size: 0.83rem; color: var(--md-sys-color-on-surface-variant); line-height: 1.55; }
.pf { display: flex; flex-direction: column; gap: 4px; }
.pf > span { font-size: 0.73rem; font-weight: 700; color: var(--md-sys-color-on-surface-variant); text-transform: uppercase; letter-spacing: 0.03em; }
.pf input, .pf select, .pf textarea { padding: 9px 11px; border: 1px solid var(--md-sys-color-outline-variant); border-radius: 8px; background: var(--md-sys-color-surface-container); color: inherit; font: inherit; font-size: 0.85rem; width: 100%; box-sizing: border-box; }
.pf input:focus, .pf select:focus, .pf textarea:focus { outline: 2px solid rgba(245,158,11,0.4); border-color: #f59e0b; }
.pf input[type=range] { border: none; padding: 4px 0; background: transparent; }
.row2 { display: flex; gap: 8px; align-items: center; }
.unit { font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); white-space: nowrap; }

/* ── Day of week ── */
.dow-row { display: flex; gap: 5px; flex-wrap: wrap; }
.dow-row button { padding: 5px 10px; border-radius: 7px; border: 1px solid var(--md-sys-color-outline-variant); background: var(--md-sys-color-surface-container); color: var(--md-sys-color-on-surface-variant); cursor: pointer; font-weight: 600; font-size: 0.75rem; }
.dow-row button.on { background: rgba(245,158,11,0.18); color: #d97706; border-color: #f59e0b; }

/* ── Segment buttons ── */
.seg-btns { display: flex; gap: 4px; flex-wrap: wrap; margin-top: 4px; }
.seg-btns button { padding: 6px 14px; border-radius: 8px; border: 1px solid var(--md-sys-color-outline-variant); background: var(--md-sys-color-surface-container); color: var(--md-sys-color-on-surface-variant); cursor: pointer; font-weight: 600; font-size: 0.8rem; transition: all 0.15s; }
.seg-btns button.on { background: rgba(245,158,11,0.18); color: #d97706; border-color: #f59e0b; }

/* ── Conditions ── */
.no-cond { font-size: 0.82rem; color: var(--md-sys-color-on-surface-variant); padding: 8px 4px; }
.cond-card { background: var(--md-sys-color-surface); border-radius: 10px; border: 1px solid var(--md-sys-color-outline-variant); padding: 12px; display: flex; flex-direction: column; gap: 10px; }
.cond-top { display: flex; align-items: center; gap: 8px; }
.cond-emoji { font-size: 1.1rem; }
.cond-top select { flex: 1; padding: 7px 10px; border: 1px solid var(--md-sys-color-outline-variant); border-radius: 8px; background: var(--md-sys-color-surface-container); color: inherit; font: inherit; font-size: 0.85rem; }
.cond-params { display: flex; gap: 10px; flex-wrap: wrap; }
.cond-params .pf { flex: 1; min-width: 120px; }
.add-btn { align-self: flex-start; }

/* ── Action cards ── */
.act-card { background: var(--md-sys-color-surface); border-radius: 12px; border: 1px solid var(--md-sys-color-outline-variant); padding: 14px; display: flex; flex-direction: column; gap: 12px; }
.act-card-head { display: flex; align-items: center; gap: 8px; }
.act-chip-icon { font-size: 1.2rem; }
.act-chip-name { font-weight: 700; font-size: 0.9rem; flex: 1; }
.act-card-btns { display: flex; gap: 2px; }
.act-params { display: flex; flex-direction: column; gap: 10px; }
.hint { font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); margin: 0; line-height: 1.5; }
.hint code { background: var(--md-sys-color-surface-container-highest); padding: 1px 5px; border-radius: 4px; font-family: ui-monospace,monospace; }

/* ── Advanced ── */
.adv-chev { width: 18px; height: 18px; transition: transform 0.2s; margin-left: auto; }
.adv-chev.rotated { transform: rotate(180deg); }
.adv-body { display: flex; flex-direction: column; gap: 12px; }
.preset-row { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.pill { padding: 4px 12px; border-radius: 999px; border: 1px solid var(--md-sys-color-outline-variant); background: var(--md-sys-color-surface-container-high); font-size: 0.75rem; font-weight: 600; cursor: pointer; color: inherit; }
.pill:hover { border-color: #f59e0b; color: #b45309; }

/* ── Action picker ── */
.ap-back { position: fixed; inset: 0; background: rgba(0,0,0,0.5); z-index: 1002; display: flex; align-items: flex-end; justify-content: flex-end; }
.ap-panel { width: min(700px,100vw); height: min(85dvh,600px); background: var(--md-sys-color-surface); border-radius: 18px 0 0 0; display: flex; flex-direction: column; animation: slideUp 0.2s ease; }
@keyframes slideUp { from { transform: translateY(40px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }
.ap-head { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; border-bottom: 1px solid var(--md-sys-color-outline-variant); flex-shrink: 0; }
.ap-head h4 { margin: 0; font-size: 1rem; font-weight: 700; }
.ap-cats { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; gap: 16px; }
.ap-cat-lbl { font-size: 0.75rem; font-weight: 700; color: var(--md-sys-color-on-surface-variant); text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 6px; }
.ap-grid { display: grid; grid-template-columns: repeat(auto-fill,minmax(140px,1fr)); gap: 8px; }
.ap-card { display: flex; flex-direction: column; align-items: flex-start; gap: 3px; padding: 12px; border-radius: 12px; border: 1px solid var(--md-sys-color-outline-variant); background: var(--md-sys-color-surface-container); cursor: pointer; text-align: left; transition: all 0.15s; }
.ap-card:hover { border-color: #f59e0b; background: rgba(245,158,11,0.06); transform: translateY(-1px); }
.ap-emoji { font-size: 1.4rem; }
.ap-label { font-weight: 700; font-size: 0.82rem; }
.ap-desc { font-size: 0.68rem; color: var(--md-sys-color-on-surface-variant); line-height: 1.3; }

/* ── Spin animation ── */
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
