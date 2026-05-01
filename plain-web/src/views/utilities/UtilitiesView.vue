<template>
  <div class="utilities-page">
    <div class="page-header">
      <h2>{{ $t('page_title.utilities') }}</h2>
      <p class="muted">{{ $t('utilities_intro') }}</p>
    </div>

    <!-- System Controls Section -->
    <div class="page-header" style="margin-top: 8px">
      <h2>System Controls</h2>
      <p class="muted">Toggle system settings and trigger device actions remotely.</p>
    </div>
    <div class="sys-controls-grid">
      <!-- DND -->
      <div class="sys-card">
        <div class="sys-card-top">
          <div class="sys-icon-wrap dnd"><i-lucide:bell-off /></div>
          <div class="sys-info">
            <div class="sys-name">Do Not Disturb</div>
            <div class="sys-desc">{{ dndOn ? dndModeLabel : 'All notifications allowed' }}</div>
          </div>
          <label class="switch">
            <input type="checkbox" :checked="dndOn" @change="toggleDnd(($event.target as HTMLInputElement).checked)" />
            <span class="slider-knob"></span>
          </label>
        </div>
        <div v-if="dndOn" class="sys-extra">
          <div class="mode-btns">
            <button v-for="m in dndModes" :key="m.value" class="mode-btn" :class="{ active: dndMode === m.value }" @click="setDndMode(m.value)">{{ m.label }}</button>
          </div>
        </div>
      </div>

      <!-- Airplane Mode -->
      <div class="sys-card">
        <div class="sys-card-top">
          <div class="sys-icon-wrap airplane"><i-lucide:plane /></div>
          <div class="sys-info">
            <div class="sys-name">Airplane Mode</div>
            <div class="sys-desc">{{ airplaneOn ? 'All radios disabled' : 'Normal connectivity' }}</div>
          </div>
          <label class="switch">
            <input type="checkbox" :checked="airplaneOn" @change="toggleAirplane(($event.target as HTMLInputElement).checked)" />
            <span class="slider-knob"></span>
          </label>
        </div>
        <div class="sys-note">⚠ Requires Settings.Global write permission (WRITE_SECURE_SETTINGS)</div>
      </div>

      <!-- Hotspot -->
      <div class="sys-card">
        <div class="sys-card-top">
          <div class="sys-icon-wrap hotspot"><i-lucide:wifi /></div>
          <div class="sys-info">
            <div class="sys-name">Mobile Hotspot</div>
            <div class="sys-desc">{{ hotspotOn ? 'Hotspot is active' : 'Hotspot is off' }}</div>
          </div>
          <label class="switch">
            <input type="checkbox" :checked="hotspotOn" @change="toggleHotspot(($event.target as HTMLInputElement).checked)" />
            <span class="slider-knob"></span>
          </label>
        </div>
        <div class="sys-note">⚠ Android 8+ may require CHANGE_NETWORK_STATE permission</div>
      </div>

      <!-- Lock Screen -->
      <div class="sys-card">
        <div class="sys-card-top">
          <div class="sys-icon-wrap lock"><i-lucide:lock /></div>
          <div class="sys-info">
            <div class="sys-name">Lock Screen</div>
            <div class="sys-desc">Immediately lock the device screen</div>
          </div>
        </div>
        <div class="sys-note">Requires Device Admin permission. Grant in app settings.</div>
        <button class="sys-btn danger" @click="doLock">Lock Now</button>
      </div>

      <!-- Reboot -->
      <div class="sys-card">
        <div class="sys-card-top">
          <div class="sys-icon-wrap reboot"><i-lucide:power /></div>
          <div class="sys-info">
            <div class="sys-name">Reboot Device</div>
            <div class="sys-desc">Restart the device immediately</div>
          </div>
        </div>
        <div class="sys-note">Requires root or ADB shell access.</div>
        <button class="sys-btn danger" @click="doReboot">Reboot</button>
      </div>
    </div>

    <div class="utilities-grid">
      <!-- Speak Message -->
      <section class="util-card">
        <header>
          <i-lucide:mic class="icon" />
          <div>
            <h3>{{ $t('util_speak_title') }}</h3>
            <p class="muted">{{ $t('util_speak_desc') }}</p>
          </div>
        </header>
        <v-text-field v-model="speakText" :label="$t('util_speak_placeholder')" />
        <div class="row gap">
          <v-filled-button @click="doSpeak">{{ $t('speak') }}</v-filled-button>
          <v-outlined-button @click="doStopSpeak">{{ $t('stop') }}</v-outlined-button>
        </div>
      </section>

      <!-- Show Message -->
      <section class="util-card">
        <header>
          <i-lucide:message-square-text class="icon" />
          <div>
            <h3>{{ $t('util_show_title') }}</h3>
            <p class="muted">{{ $t('util_show_desc') }}</p>
          </div>
        </header>
        <v-text-field v-model="showTitle" :label="$t('title')" />
        <v-text-field v-model="showMessageBody" :label="$t('message')" />
        <div class="row gap" style="align-items: center">
          <label class="checkbox-row">
            <v-checkbox touch-target="wrapper" :checked="showBlocking" @change="showBlocking = !showBlocking" />
            <span>{{ $t('util_show_blocking') }}</span>
          </label>
          <v-text-field v-model.number="showDuration" type="number" min="1" max="60" :label="$t('seconds')" style="width: 110px" />
        </div>
        <div class="row">
          <v-filled-button @click="doShow">{{ $t('show') }}</v-filled-button>
        </div>
      </section>

      <!-- Vibrate -->
      <section class="util-card">
        <header>
          <i-lucide:vibrate class="icon" />
          <div>
            <h3>{{ $t('util_vibrate_title') }}</h3>
            <p class="muted">{{ $t('util_vibrate_desc') }}</p>
          </div>
        </header>
        <div class="row gap" style="align-items: center">
          <input v-model.number="vibrateMs" type="range" min="100" max="5000" step="100" class="slider" />
          <span class="value">{{ vibrateMs }} ms</span>
        </div>
        <div class="row gap chips">
          <button v-for="p in vibratePresets" :key="p" class="chip" @click="vibrateMs = p">{{ p }} ms</button>
        </div>
        <div class="row">
          <v-filled-button @click="doVibrate">{{ $t('vibrate') }}</v-filled-button>
        </div>
      </section>

      <!-- Locate Phone -->
      <section class="util-card">
        <header>
          <i-lucide:bell-ring class="icon" />
          <div>
            <h3>{{ $t('util_locate_title') }}</h3>
            <p class="muted">{{ $t('util_locate_desc') }}</p>
          </div>
        </header>
        <div class="row gap">
          <v-filled-button v-if="!locateOn" @click="doLocate(true)">{{ $t('start_ringing') }}</v-filled-button>
          <v-filled-button v-else class="danger" @click="doLocate(false)">{{ $t('stop_ringing') }}</v-filled-button>
        </div>
      </section>

      <!-- Wake Screen -->
      <section class="util-card">
        <header>
          <i-lucide:sun class="icon" />
          <div>
            <h3>{{ $t('util_wake_title') }}</h3>
            <p class="muted">{{ $t('util_wake_desc') }}</p>
          </div>
        </header>
        <div class="row">
          <v-filled-button @click="doWake">{{ $t('wake_screen') }}</v-filled-button>
        </div>
      </section>

      <!-- Torch -->
      <section class="util-card">
        <header>
          <i-lucide:flashlight class="icon" />
          <div>
            <h3>{{ $t('util_torch_title') }}</h3>
            <p class="muted">{{ $t('util_torch_desc') }}</p>
          </div>
        </header>
        <div class="row gap" style="align-items: center">
          <label class="switch">
            <input type="checkbox" :checked="torchOn" @change="doTorch(($event.target as HTMLInputElement).checked)" />
            <span class="slider-knob"></span>
          </label>
          <span>{{ torchOn ? $t('on') : $t('off') }}</span>
        </div>
      </section>

      <!-- Volume -->
      <section class="util-card span-2">
        <header>
          <i-lucide:volume-2 class="icon" />
          <div>
            <h3>{{ $t('util_volume_title') }}</h3>
            <p class="muted">{{ $t('util_volume_desc') }}</p>
          </div>
        </header>
        <div v-for="v in volumes" :key="v.stream" class="slider-row">
          <span class="slider-label">{{ $t('stream_' + v.stream) }}</span>
          <input
            type="range" min="0" max="100" :value="v.percent"
            class="slider" @change="onVolume(v.stream, ($event.target as HTMLInputElement).valueAsNumber)"
          />
          <span class="value">{{ v.percent }}%</span>
        </div>
      </section>

      <!-- Brightness -->
      <section class="util-card">
        <header>
          <i-lucide:sun-medium class="icon" />
          <div>
            <h3>{{ $t('util_brightness_title') }}</h3>
            <p class="muted">{{ $t('util_brightness_desc') }}</p>
          </div>
        </header>
        <div class="slider-row">
          <input
            type="range" min="0" max="100" :value="brightness"
            class="slider" @change="onBrightness(($event.target as HTMLInputElement).valueAsNumber)"
          />
          <span class="value">{{ brightness }}%</span>
        </div>
      </section>

      <!-- Mobile data deep-link -->
      <section class="util-card">
        <header>
          <i-lucide:signal class="icon" />
          <div>
            <h3>{{ $t('util_data_title') }}</h3>
            <p class="muted">{{ $t('util_data_desc') }}</p>
          </div>
        </header>
        <div class="row">
          <v-outlined-button @click="doOpenData">{{ $t('open_data_settings') }}</v-outlined-button>
        </div>
      </section>

      <!-- Location -->
      <section class="util-card span-2">
        <header>
          <i-lucide:map-pin class="icon" />
          <div>
            <h3>{{ $t('util_location_title') }}</h3>
            <p class="muted">{{ $t('util_location_desc') }}</p>
          </div>
        </header>
        <div class="row gap">
          <v-filled-button :loading="loadingLocation" @click="loadLocation">{{ $t('refresh') }}</v-filled-button>
        </div>
        <div v-if="location" class="loc-grid">
          <div class="loc-row">
            <span class="loc-label">{{ $t('plus_code') }}</span>
            <code>{{ location.plusCode }}</code>
            <v-icon-button v-tooltip="$t('copy')" class="sm" @click="copy(location.plusCode)">
              <i-lucide:copy />
            </v-icon-button>
          </div>
          <div class="loc-row">
            <span class="loc-label">{{ $t('latitude') }}</span>
            <code>{{ location.latitude.toFixed(6) }}</code>
            <v-icon-button v-tooltip="$t('copy')" class="sm" @click="copy(location.latitude.toFixed(6))">
              <i-lucide:copy />
            </v-icon-button>
          </div>
          <div class="loc-row">
            <span class="loc-label">{{ $t('longitude') }}</span>
            <code>{{ location.longitude.toFixed(6) }}</code>
            <v-icon-button v-tooltip="$t('copy')" class="sm" @click="copy(location.longitude.toFixed(6))">
              <i-lucide:copy />
            </v-icon-button>
          </div>
          <div class="loc-row">
            <span class="loc-label">{{ $t('accuracy') }}</span>
            <code>± {{ location.accuracyMeters.toFixed(0) }} m</code>
            <span class="muted">{{ location.provider }}</span>
          </div>
          <div class="loc-row">
            <a class="map-link" :href="location.googleMapsUrl" target="_blank">
              <i-lucide:external-link /> {{ $t('open_in_google_maps') }}
            </a>
            <v-icon-button v-tooltip="$t('copy')" class="sm" @click="copy(location.googleMapsUrl)">
              <i-lucide:copy />
            </v-icon-button>
          </div>
        </div>
        <div v-else class="muted">{{ $t('no_location_yet') }}</div>
      </section>
    </div>

    <!-- Parental Controls -->
    <div class="page-header" style="margin-top: 32px">
      <h2>{{ $t('page_title.parental') }}</h2>
      <p class="muted">{{ $t('parental_intro') }}</p>
      <div v-if="state && !state.accessibilityServiceEnabled" class="warning">
        {{ $t('parental_needs_accessibility') }}
      </div>
    </div>

    <div class="utilities-grid">
      <!-- Bedtime mode -->
      <section class="util-card span-2">
        <header>
          <i-lucide:moon class="icon" />
          <div>
            <h3>{{ $t('bedtime_title') }}</h3>
            <p class="muted">{{ $t('bedtime_desc') }}</p>
          </div>
          <div class="header-actions">
            <span v-if="bedtimeActive" class="badge badge-active">
              <i-lucide:moon /> {{ $t('currently_active') }}
            </span>
            <span v-else-if="bedtime.enabled" class="badge badge-set">{{ $t('set') }}</span>
            <span v-else class="badge badge-off">{{ $t('off') }}</span>
          </div>
        </header>
        <div class="row gap" style="align-items: center; flex-wrap: wrap">
          <label>{{ $t('start') }}: <input type="time" v-model="bedtimeStart" class="time-input" /></label>
          <label>{{ $t('end') }}: <input type="time" v-model="bedtimeEnd" class="time-input" /></label>
        </div>
        <div class="bedtime-apps">
          <div class="bedtime-apps-header">
            <span class="muted">{{ $t('bedtime_packages') }} ({{ bedtime.packages.length }})</span>
            <v-outlined-button class="btn-sm" @click="pickBedtimeApps">
              <i-lucide:plus /> {{ $t('add_apps') }}
            </v-outlined-button>
          </div>
          <div v-if="bedtime.packages.length" class="bedtime-icons">
            <div v-for="pkg in bedtime.packages" :key="pkg" class="bedtime-icon" v-tooltip="pkg">
              <img :src="iconUrl(pkg)" width="32" height="32" />
              <button class="remove" @click="removeBedtimePkg(pkg)" v-tooltip="$t('remove')">
                <i-lucide:x />
              </button>
            </div>
          </div>
          <div v-else class="muted small">{{ $t('no_apps_picked') }}</div>
        </div>
        <div class="row gap">
          <v-filled-button v-if="!bedtime.enabled" @click="enableBedtime">{{ $t('enable') }}</v-filled-button>
          <v-outlined-button v-else class="danger-text" @click="disableBedtime">{{ $t('disable') }}</v-outlined-button>
          <v-outlined-button @click="saveBedtime">{{ $t('save') }}</v-outlined-button>
        </div>
      </section>

      <!-- Time limits / blocked summary -->
      <section class="util-card span-2">
        <header>
          <i-lucide:clock class="icon" />
          <div>
            <h3>{{ $t('time_limits_title') }}</h3>
            <p class="muted">{{ $t('time_limits_desc') }}</p>
          </div>
        </header>
        <table v-if="state && state.timeLimits.length" class="data-table">
          <thead><tr><th>{{ $t('app') }}</th><th>{{ $t('daily_limit') }}</th><th>{{ $t('used_today') }}</th><th>{{ $t('remaining') }}</th><th></th></tr></thead>
          <tbody>
            <tr v-for="t in state.timeLimits" :key="t.packageId" :class="{ 'over-limit': liveUsed(t) >= t.dailyMs }">
              <td>
                <div class="app-cell">
                  <img :src="iconUrl(t.packageId)" width="24" height="24" />
                  <div>
                    <div class="app-name">{{ t.appName || t.packageId }}</div>
                    <div class="app-pkg muted small">{{ t.packageId }}</div>
                  </div>
                </div>
              </td>
              <td>{{ formatMs(t.dailyMs) }}</td>
              <td>{{ formatMs(liveUsed(t)) }}</td>
              <td>
                <span :class="{ 'danger-text': liveUsed(t) >= t.dailyMs }">
                  {{ liveUsed(t) >= t.dailyMs ? $t('blocked_now') : formatMs(t.dailyMs - liveUsed(t)) }}
                </span>
              </td>
              <td>
                <v-icon-button class="sm" v-tooltip="$t('delete')" @click="removeLimit(t.packageId)">
                  <i-lucide:trash-2 />
                </v-icon-button>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else class="muted">{{ $t('no_time_limits') }}</div>
      </section>

      <!-- Top apps donut + Launch history -->
      <section class="util-card span-2">
        <header>
          <i-lucide:pie-chart class="icon" />
          <div>
            <h3>{{ $t('most_used_apps') }}</h3>
            <p class="muted">{{ $t('most_used_apps_desc') }}</p>
          </div>
        </header>
        <DonutChart v-if="topApps.length" :data="topApps" :sub-label="$t('apps')" />
        <div v-else class="muted">{{ $t('no_launch_history') }}</div>
      </section>

      <section class="util-card span-2">
        <header @click="historyOpen = !historyOpen" style="cursor: pointer">
          <i-lucide:history class="icon" />
          <div>
            <h3>{{ $t('launch_history_title') }}</h3>
            <p class="muted">{{ $t('launch_history_desc') }}</p>
          </div>
          <div class="header-actions">
            <v-outlined-button class="btn-sm" @click.stop="loadHistory">{{ $t('refresh') }}</v-outlined-button>
            <v-outlined-button class="btn-sm" @click.stop="clearHistory">{{ $t('clear') }}</v-outlined-button>
            <v-icon-button class="sm" :class="{ rotated: historyOpen }" v-tooltip="historyOpen ? $t('collapse') : $t('expand')">
              <i-lucide:chevron-down />
            </v-icon-button>
          </div>
        </header>
        <transition name="collapse">
          <div v-if="historyOpen">
            <table v-if="history.length" class="data-table">
              <thead><tr><th>{{ $t('app') }}</th><th>{{ $t('time') }}</th></tr></thead>
              <tbody>
                <tr v-for="(h, i) in history" :key="i">
                  <td>
                    <div class="app-cell">
                      <img :src="iconUrl(h.packageId)" width="24" height="24" />
                      <div>
                        <div class="app-name">{{ h.appName || h.packageId }}</div>
                        <div class="app-pkg muted small">{{ h.packageId }}</div>
                      </div>
                    </div>
                  </td>
                  <td>{{ new Date(h.timestamp).toLocaleString() }}</td>
                </tr>
              </tbody>
            </table>
            <div v-else class="muted">{{ $t('no_launch_history') }}</div>
          </div>
        </transition>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import toast from '@/components/toaster'
import { useI18n } from 'vue-i18n'
import DonutChart from '@/components/DonutChart.vue'
import { openModal } from '@/components/modal'
import AppPickerModal from '@/components/AppPickerModal.vue'
import { useTempStore } from '@/stores/temp'
import { storeToRefs } from 'pinia'
import { getFileUrlByPath } from '@/lib/api/file'
import { initMutation } from '@/lib/api/mutation'
import {
  speakMessageGQL, stopSpeakingGQL, showMessageGQL, vibrateGQL,
  locatePhoneGQL, wakeScreenGQL, setTorchGQL, setVolumeGQL, setBrightnessGQL,
  openDataSettingsGQL, setBedtimeGQL, clearLaunchHistoryGQL, removeAppTimeLimitGQL,
} from '@/lib/api/mutation'
import {
  volumesGQL, brightnessGQL, torchOnGQL, locateRunningGQL,
  deviceLocationGQL, blockedAppsStateGQL, launchHistoryGQL,
} from '@/lib/api/query'
import { gqlFetch } from '@/lib/api/gql-client'
import type { IDeviceLocation, IBlockedAppsState, IVolumeLevel, ILaunchHistoryEntry } from '@/lib/interfaces'
import {
  setDndGQL, setAirplaneModeGQL, setHotspotGQL, lockScreenGQL, rebootDeviceGQL,
} from '@/lib/api/mutation'
import { dndStatusGQL, airplaneModeGQL as airplaneModeStatusGQL, hotspotStatusGQL } from '@/lib/api/query'

const { t } = useI18n()

// --- System Controls ---
const dndOn = ref(false)
const dndMode = ref(1)
const airplaneOn = ref(false)
const hotspotOn = ref(false)

const dndModes = [
  { label: 'All', value: 1 },
  { label: 'Priority', value: 2 },
  { label: 'Silent', value: 3 },
  { label: 'Alarms', value: 4 },
]
const dndModeLabel = computed(() => dndModes.find(m => m.value === dndMode.value)?.label ?? 'On')

const { mutate: mDnd } = initMutation({ document: setDndGQL })
const { mutate: mAirplane } = initMutation({ document: setAirplaneModeGQL })
const { mutate: mHotspot } = initMutation({ document: setHotspotGQL })
const { mutate: mLock } = initMutation({ document: lockScreenGQL })
const { mutate: mReboot } = initMutation({ document: rebootDeviceGQL })

function toggleDnd(on: boolean) { mDnd({ enabled: on }).then(() => { dndOn.value = on }) }
function setDndMode(mode: number) { dndMode.value = mode; mDnd({ enabled: dndOn.value }) }
function toggleAirplane(on: boolean) { mAirplane({ enabled: on }).then(() => { airplaneOn.value = on }) }
function toggleHotspot(on: boolean) { mHotspot({ enabled: on }).then(() => { hotspotOn.value = on }) }
function doLock() { if (confirm('Lock the device screen now?')) mLock() }
function doReboot() { if (confirm('Reboot the device now?')) mReboot() }

async function loadSysState() {
  try {
    const d = await gqlFetch<{ dndStatus: { mode: number } }>(dndStatusGQL)
    if (!d.errors) { dndMode.value = d.data.dndStatus.mode; dndOn.value = d.data.dndStatus.mode !== 1 }
  } catch {}
  try {
    const a = await gqlFetch<{ airplaneMode: boolean }>(airplaneModeStatusGQL)
    if (!a.errors) airplaneOn.value = a.data.airplaneMode
  } catch {}
  try {
    const h = await gqlFetch<{ hotspotStatus: { enabled: boolean } }>(hotspotStatusGQL)
    if (!h.errors) hotspotOn.value = h.data.hotspotStatus.enabled
  } catch {}
}

// --- Speak ---
const speakText = ref('')
const { mutate: mSpeak } = initMutation({ document: speakMessageGQL })
const { mutate: mStopSpeak } = initMutation({ document: stopSpeakingGQL })
function doSpeak() { if (speakText.value.trim()) mSpeak({ text: speakText.value }) }
function doStopSpeak() { mStopSpeak() }

// --- Show ---
const showTitle = ref('PlainApp')
const showMessageBody = ref('')
const showDuration = ref(5)
const showBlocking = ref(false)
const { mutate: mShow } = initMutation({ document: showMessageGQL })
function doShow() {
  if (!showMessageBody.value.trim()) return
  mShow({
    title: showTitle.value || 'PlainApp',
    message: showMessageBody.value,
    durationMs: Math.round((showDuration.value || 5) * 1000),
    blocking: showBlocking.value,
  }).then((r) => { if (r) toast(t('sent')) })
}

// --- Vibrate ---
const vibrateMs = ref(500)
const vibratePresets = [200, 500, 1000, 2000, 5000]
const { mutate: mVibrate } = initMutation({ document: vibrateGQL })
function doVibrate() { mVibrate({ durationMs: vibrateMs.value }) }

// --- Locate ---
const locateOn = ref(false)
const { mutate: mLocate } = initMutation({ document: locatePhoneGQL })
function doLocate(start: boolean) {
  mLocate({ start }).then((r) => { if (r) locateOn.value = start })
}

// --- Wake ---
const { mutate: mWake } = initMutation({ document: wakeScreenGQL })
function doWake() { mWake({ durationMs: 10000 }).then((r) => { if (r) toast(t('sent')) }) }

// --- Torch ---
const torchOn = ref(false)
const { mutate: mTorch } = initMutation({ document: setTorchGQL })
function doTorch(on: boolean) {
  mTorch({ on }).then((r) => {
    if (r != null && r.data?.setTorch !== false) torchOn.value = on
    else toast(t('failed'), 'error')
  })
}

// --- Volume / Brightness ---
const volumes = ref<IVolumeLevel[]>([])
const brightness = ref(0)
const { mutate: mVolume } = initMutation({ document: setVolumeGQL })
const { mutate: mBrightness } = initMutation({ document: setBrightnessGQL })
function onVolume(stream: string, percent: number) { mVolume({ stream, percent }) }
function onBrightness(percent: number) {
  mBrightness({ percent }).then((r) => {
    if (r) brightness.value = percent
    else toast(t('write_settings_required'), 'error')
  })
}

// --- Data settings ---
const { mutate: mOpenData } = initMutation({ document: openDataSettingsGQL })
function doOpenData() { mOpenData().then(() => toast(t('opened_on_phone'))) }

// --- Location ---
const location = ref<IDeviceLocation | null>(null)
const loadingLocation = ref(false)
async function loadLocation() {
  loadingLocation.value = true
  try {
    const r = await gqlFetch<{ deviceLocation: IDeviceLocation }>(deviceLocationGQL)
    if (r.errors?.length) toast(r.errors[0].message, 'error')
    else location.value = r.data.deviceLocation
  } finally { loadingLocation.value = false }
}
function copy(s: string) {
  navigator.clipboard?.writeText(s).then(() => toast(t('copied'))).catch(() => toast(t('copy_failed'), 'error'))
}

// --- Parental: state ---
const state = ref<IBlockedAppsState | null>(null)
const bedtime = reactive({ enabled: false, packages: [] as string[] })
const bedtimeStart = ref('22:00')
const bedtimeEnd = ref('07:00')
const history = ref<ILaunchHistoryEntry[]>([])

async function loadState() {
  const r = await gqlFetch<{ blockedAppsState: IBlockedAppsState }>(blockedAppsStateGQL)
  if (r.errors?.length) return
  state.value = r.data.blockedAppsState
  bedtime.enabled = state.value.bedtime.enabled
  bedtime.packages = state.value.bedtime.packages.slice()
  bedtimeStart.value = minutesToTime(state.value.bedtime.startMinutes)
  bedtimeEnd.value = minutesToTime(state.value.bedtime.endMinutes)
}
function minutesToTime(m: number): string {
  const h = Math.floor(m / 60).toString().padStart(2, '0')
  const mm = (m % 60).toString().padStart(2, '0')
  return `${h}:${mm}`
}
function timeToMinutes(s: string): number {
  const [h, m] = s.split(':').map(Number)
  return (h || 0) * 60 + (m || 0)
}
const { urlTokenKey } = storeToRefs(useTempStore())
function iconUrl(pkgId: string) { return getFileUrlByPath(urlTokenKey.value, 'pkgicon://' + pkgId) }
const historyOpen = ref(false)
const bedtimeActive = computed(() => {
  if (!bedtime.enabled) return false
  const now = new Date()
  const m = now.getHours() * 60 + now.getMinutes()
  const s = state.value?.bedtime.startMinutes ?? timeToMinutes(bedtimeStart.value)
  const e = state.value?.bedtime.endMinutes ?? timeToMinutes(bedtimeEnd.value)
  return s <= e ? (m >= s && m < e) : (m >= s || m < e)
})
const topApps = computed(() => {
  const m = new Map<string, number>()
  for (const h of history.value) m.set(h.packageId, (m.get(h.packageId) || 0) + 1)
  return Array.from(m.entries())
    .sort((a, b) => b[1] - a[1])
    .slice(0, 6)
    .map(([label, value]) => ({ label, value }))
})
function pickBedtimeApps() {
  openModal(AppPickerModal, {
    title: t('select_bedtime_apps'),
    initial: bedtime.packages,
    done: (ids: string[]) => { bedtime.packages = ids },
  })
}
function removeBedtimePkg(p: string) { bedtime.packages = bedtime.packages.filter((x) => x !== p) }
function enableBedtime() { bedtime.enabled = true; saveBedtime() }
function disableBedtime() { bedtime.enabled = false; saveBedtime() }

const { mutate: mBedtime } = initMutation({ document: setBedtimeGQL })
function saveBedtime() {
  mBedtime({
    enabled: bedtime.enabled,
    startMinutes: timeToMinutes(bedtimeStart.value),
    endMinutes: timeToMinutes(bedtimeEnd.value),
    packages: bedtime.packages,
  }).then((r) => { if (r) toast(t('saved')) })
}
async function loadHistory() {
  const r = await gqlFetch<{ launchHistory: ILaunchHistoryEntry[] }>(launchHistoryGQL, { limit: 100 })
  if (!r.errors) history.value = r.data.launchHistory
}
const { mutate: mClearHistory } = initMutation({ document: clearLaunchHistoryGQL })
function clearHistory() {
  mClearHistory().then(() => { history.value = [] })
}
function formatMs(ms: number): string {
  if (ms < 0) ms = 0
  const totalSec = Math.floor(ms / 1000)
  const h = Math.floor(totalSec / 3600)
  const m = Math.floor((totalSec % 3600) / 60)
  const s = totalSec % 60
  if (h > 0) return `${h}h ${m}m`
  if (m > 0) return `${m}m ${s.toString().padStart(2, '0')}s`
  return `${s}s`
}

const tickNow = ref(Date.now())
let tickHandle: any
let liveStateHandle: any
function liveUsed(t: any): number {
  void tickNow.value
  return t.usedMs
}
const { mutate: mRemoveLimit } = initMutation({ document: removeAppTimeLimitGQL })
async function removeLimit(packageId: string) {
  await mRemoveLimit({ packageId })
  loadState()
}

onMounted(async () => {
  // initial loads — best-effort, ignore errors
  try {
    const v = await gqlFetch<{ volumes: IVolumeLevel[] }>(volumesGQL)
    if (!v.errors) volumes.value = v.data.volumes
  } catch {}
  try {
    const b = await gqlFetch<{ brightness: number }>(brightnessGQL)
    if (!b.errors) brightness.value = b.data.brightness
  } catch {}
  try {
    const t = await gqlFetch<{ torchOn: boolean }>(torchOnGQL)
    if (!t.errors) torchOn.value = t.data.torchOn
  } catch {}
  try {
    const l = await gqlFetch<{ locateRunning: boolean }>(locateRunningGQL)
    if (!l.errors) locateOn.value = l.data.locateRunning
  } catch {}
  loadState()
  loadHistory()
  loadSysState()
  tickHandle = setInterval(() => { tickNow.value = Date.now() }, 1000)
  liveStateHandle = setInterval(() => { loadState() }, 5000)
})
onUnmounted(() => {
  if (tickHandle) clearInterval(tickHandle)
  if (liveStateHandle) clearInterval(liveStateHandle)
})
</script>

<style scoped lang="scss">
.utilities-page { padding: 16px; overflow-y: auto; }
.page-header { margin-bottom: 16px; h2 { margin: 0 0 4px; } .muted { color: var(--md-sys-color-on-surface-variant); font-size: 0.875rem; } }
.sys-controls-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
  margin-bottom: 24px;
}
.sys-card {
  background: var(--md-sys-color-surface-container);
  border-radius: 18px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.sys-card-top {
  display: flex;
  align-items: center;
  gap: 14px;
}
.sys-icon-wrap {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  svg { width: 22px; height: 22px; }
  &.dnd { background: rgba(99,102,241,0.12); color: #6366f1; }
  &.airplane { background: rgba(56,189,248,0.12); color: #0ea5e9; }
  &.hotspot { background: rgba(34,197,94,0.12); color: #22c55e; }
  &.lock { background: rgba(245,158,11,0.12); color: #d97706; }
  &.reboot { background: rgba(239,68,68,0.12); color: #ef4444; }
}
.sys-info { flex: 1; }
.sys-name { font-weight: 700; font-size: 0.95rem; }
.sys-desc { font-size: 0.78rem; color: var(--md-sys-color-on-surface-variant); }
.sys-note { font-size: 0.75rem; color: var(--md-sys-color-on-surface-variant); padding: 6px 10px; background: rgba(0,0,0,0.04); border-radius: 8px; }
.sys-extra { display: flex; flex-direction: column; gap: 6px; }
.mode-btns { display: flex; gap: 6px; flex-wrap: wrap; }
.mode-btn {
  padding: 5px 12px;
  border-radius: 999px;
  border: 1px solid var(--md-sys-color-outline-variant);
  background: none;
  cursor: pointer;
  font-size: 0.8rem;
  color: var(--md-sys-color-on-surface-variant);
  transition: all 0.18s;
  &.active {
    background: #6366f1;
    color: #fff;
    border-color: #6366f1;
  }
}
.sys-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  font-size: 0.88rem;
  font-weight: 600;
  align-self: flex-start;
  &.danger { background: #ef4444; color: #fff; &:hover { background: #dc2626; } }
}

.utilities-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}
.util-card {
  background: var(--md-sys-color-surface-container);
  border-radius: 16px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  &.span-2 { grid-column: span 2; }
  &.span-4 { grid-column: span 4; }
  header {
    display: flex; gap: 12px; align-items: flex-start;
    .icon { width: 28px; height: 28px; flex-shrink: 0; color: var(--md-sys-color-primary); }
    h3 { margin: 0; font-size: 1rem; font-weight: 600; }
    p.muted { font-size: 0.8125rem; color: var(--md-sys-color-on-surface-variant); margin: 2px 0 0; }
    .header-actions { margin-left: auto; display: flex; gap: 8px; }
  }
  .row { display: flex; }
  .gap { gap: 12px; }
  .danger { --md-filled-button-container-color: #c62828; }
  .checkbox-row { display: inline-flex; align-items: center; gap: 6px; cursor: pointer; }
}
.slider-row {
  display: grid;
  grid-template-columns: 100px 1fr 56px;
  align-items: center;
  gap: 12px;
  .slider-label { font-size: 0.875rem; color: var(--md-sys-color-on-surface-variant); }
  .value { font-variant-numeric: tabular-nums; text-align: right; font-size: 0.875rem; }
}
.slider {
  appearance: none; -webkit-appearance: none;
  width: 100%; height: 4px; background: var(--md-sys-color-surface-variant); border-radius: 2px;
  outline: none;
}
.slider::-webkit-slider-thumb {
  -webkit-appearance: none; width: 18px; height: 18px;
  background: var(--md-sys-color-primary); border-radius: 50%; cursor: pointer; border: none;
}
.slider::-moz-range-thumb {
  width: 18px; height: 18px;
  background: var(--md-sys-color-primary); border-radius: 50%; cursor: pointer; border: none;
}
.chips { flex-wrap: wrap; }
.chip {
  border: 1px solid var(--md-sys-color-outline-variant);
  background: transparent; color: var(--md-sys-color-on-surface);
  padding: 4px 10px; border-radius: 999px; font-size: 0.8125rem; cursor: pointer;
}
.chip:hover { background: var(--md-sys-color-surface-variant); }
.value { font-variant-numeric: tabular-nums; min-width: 60px; }

.switch { position: relative; display: inline-block; width: 44px; height: 24px; cursor: pointer; }
.switch input { opacity: 0; width: 0; height: 0; }
.slider-knob {
  position: absolute; cursor: pointer; inset: 0;
  background: var(--md-sys-color-surface-variant); border-radius: 999px;
  transition: 0.2s;
}
.slider-knob:before {
  position: absolute; content: ''; height: 18px; width: 18px; left: 3px; top: 3px;
  background: var(--md-sys-color-on-surface-variant); border-radius: 50%; transition: 0.2s;
}
.switch input:checked + .slider-knob { background: var(--md-sys-color-primary); }
.switch input:checked + .slider-knob:before { transform: translateX(20px); background: white; }

.loc-grid { display: flex; flex-direction: column; gap: 8px; }
.loc-row {
  display: flex; gap: 12px; align-items: center; padding: 8px 12px;
  background: var(--md-sys-color-surface); border-radius: 12px;
  .loc-label { width: 100px; font-size: 0.8125rem; color: var(--md-sys-color-on-surface-variant); }
  code { flex: 1; font-family: var(--md-ref-typeface-monospace, monospace); font-size: 0.875rem; }
  .map-link {
    flex: 1; display: inline-flex; gap: 6px; align-items: center;
    color: var(--md-sys-color-primary); text-decoration: none; font-weight: 500;
  }
}
.warning {
  margin-top: 8px; padding: 10px 14px; border-radius: 12px;
  background: #FFE082; color: #332600; font-size: 0.875rem;
}
.data-table {
  width: 100%; border-collapse: collapse; font-size: 0.875rem;
  th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--md-sys-color-outline-variant); }
  th { font-weight: 500; color: var(--md-sys-color-on-surface-variant); }
}
.time-input {
  border: 1px solid var(--md-sys-color-outline-variant); background: transparent;
  padding: 4px 8px; border-radius: 8px; color: inherit;
}
@media (max-width: 720px) {
  .util-card.span-2, .util-card.span-4 { grid-column: span 1; }
}
.badge {
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 0.75rem; padding: 4px 10px; border-radius: 999px; font-weight: 500;
}
.badge-active { background: #2e7d32; color: white; }
.badge-set { background: var(--md-sys-color-primary-container); color: var(--md-sys-color-on-primary-container); }
.badge-off { background: var(--md-sys-color-surface-variant); color: var(--md-sys-color-on-surface-variant); }
.danger-text { --md-outlined-button-label-text-color: #c62828; }
.bedtime-apps { background: var(--md-sys-color-surface); border-radius: 12px; padding: 10px 12px; }
.bedtime-apps-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.bedtime-icons { display: flex; flex-wrap: wrap; gap: 6px; }
.bedtime-icon {
  position: relative; width: 36px; height: 36px;
  img { border-radius: 6px; width: 32px; height: 32px; }
  .remove {
    position: absolute; top: -4px; right: -4px;
    background: var(--md-sys-color-error); color: var(--md-sys-color-on-error);
    border: none; border-radius: 50%; width: 16px; height: 16px;
    display: flex; align-items: center; justify-content: center; cursor: pointer;
    svg { width: 10px; height: 10px; }
  }
}
.muted.small { font-size: 0.8125rem; }
.rotated { transform: rotate(180deg); transition: transform 0.2s; }
.collapse-enter-active, .collapse-leave-active { transition: opacity 0.2s; }
.collapse-enter-from, .collapse-leave-to { opacity: 0; }
</style>
