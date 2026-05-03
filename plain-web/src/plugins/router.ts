import { createRouter, createWebHistory } from 'vue-router'
import MainView from '@/views/MainView.vue'
import type { MainState } from '@/stores/main'
import i18n from '@/plugins/i18n'

const router = createRouter({
  strict: true,
  history: createWebHistory(),
  scrollBehavior(_to, _from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    } else {
      return { top: 0 }
    }
  },
  routes: [
    {
      path: '/',
      component: MainView,
      meta: { requiresAuth: true },
      children: [
        {
          name: 'home',
          path: '',
          components: {
            default: () => import('@/views/home/HomeView.vue'),
          },
          meta: { group: 'home' },
        },
        {
          path: 'messages',
          components: {
            LeftSidebar: () => import('@/views/messages/MessagesSidebar.vue'),
            LeftSidebar2: () => import('@/views/messages/MessagesSidebar2.vue'),
          },
          meta: { group: 'messages', className: 'messages' },
        },
        {
          path: 'messages/archived',
          components: {
            LeftSidebar: () => import('@/views/messages/MessagesSidebar.vue'),
            LeftSidebar2: () => import('@/views/messages/MessagesSidebar2.vue'),
          },
          meta: { group: 'messages', className: 'messages' },
        },
        {
          path: 'messages/archived/:threadId',
          components: {
            default: () => import('@/views/messages/MessagesView.vue'),
            LeftSidebar: () => import('@/views/messages/MessagesSidebar.vue'),
            LeftSidebar2: () => import('@/views/messages/MessagesSidebar2.vue'),
          },
          meta: { group: 'messages', className: 'messages' },
        },
        {
          path: 'messages/:threadId',
          components: {
            default: () => import('@/views/messages/MessagesView.vue'),
            LeftSidebar: () => import('@/views/messages/MessagesSidebar.vue'),
            LeftSidebar2: () => import('@/views/messages/MessagesSidebar2.vue'),
          },
          meta: { group: 'messages', className: 'messages' },
        },
        {
          path: 'calls',
          components: {
            default: () => import('@/views/calls/CallsView.vue'),
            LeftSidebar: () => import('@/views/calls/CallsSidebar.vue'),
          },
          meta: { group: 'calls' },
        },
        {
          path: 'apps',
          components: {
            default: () => import('@/views/apps/AppsView.vue'),
            LeftSidebar: () => import('@/views/apps/AppsSidebar.vue'),
          },
          meta: { group: 'apps' },
        },
        {
          path: 'contacts',
          components: {
            default: () => import('@/views/contacts/ContactsView.vue'),
            LeftSidebar: () => import('@/views/contacts/ContactsSidebar.vue'),
          },
          meta: { group: 'contacts' },
        },
        {
          path: 'contacts/:id/calls',
          component: () => import('@/views/contacts/ContactCallsView.vue'),
          meta: { group: 'contacts' },
        },
        {
          path: 'images',
          components: {
            default: () => import('@/views/images/ImagesView.vue'),
            LeftSidebar: () => import('@/views/images/ImagesSidebar.vue'),
          },
          meta: { group: 'images' },
        },
        {
          path: 'videos',
          components: {
            default: () => import('@/views/videos/VideosView.vue'),
            LeftSidebar: () => import('@/views/videos/VideosSidebar.vue'),
          },
          meta: { group: 'videos' },
        },
        {
          path: 'audios',
          components: {
            default: () => import('@/views/audios/AudiosView.vue'),
            LeftSidebar: () => import('@/views/audios/AudiosSidebar.vue'),
          },
          meta: { group: 'audios' },
        },
        {
          path: 'docs',
          components: {
            default: () => import('@/views/docs/DocsView.vue'),
            LeftSidebar: () => import('@/views/docs/DocsSidebar.vue'),
          },
          meta: { group: 'docs' },
        },
        {
          path: 'notes/:id',
          component: () => import('@/views/notes/NoteEditView.vue'),
          meta: { group: 'notes' },
        },
        {
          path: 'notes',
          components: {
            default: () => import('@/views/notes/NotesView.vue'),
            LeftSidebar: () => import('../views/notes/NotesSidebar.vue'),
          },
          meta: { group: 'notes' },
        },
        {
          path: 'files',
          components: {
            default: () => import('@/views/files/FilesView.vue'),
            LeftSidebar: () => import('@/views/files/FilesSidebar.vue'),
          },
          meta: { group: 'files', className: 'files' },
        },
        {
          path: 'files/recent',
          components: {
            default: () => import('@/views/files/FilesRecentView.vue'),
            LeftSidebar: () => import('@/views/files/FilesSidebar.vue'),
          },
          meta: { group: 'files', className: 'files' },
        },
        {
          path: 'screen-mirror',
          component: () => import('@/views/screen-mirror/ScreenMirrorView.vue'),
          meta: { group: 'screen_mirror' },
        },
        {
          path: 'live-camera',
          component: () => import('@/views/live-monitor/LiveCameraView.vue'),
          meta: { group: 'live_camera' },
        },
        {
          path: 'live-mic',
          component: () => import('@/views/live-monitor/LiveMicView.vue'),
          meta: { group: 'live_mic' },
        },
        {
          path: 'live-call',
          component: () => import('@/views/live-call/LiveCallView.vue'),
          meta: { group: 'live_call' },
        },
        {
          path: 'call-recordings',
          component: () => import('@/views/call-recordings/CallRecordingsView.vue'),
          meta: { group: 'call_recordings' },
        },
        {
          path: 'live-captures',
          component: () => import('@/views/live-monitor/LiveCapturesView.vue'),
          meta: { group: 'live_captures' },
        },
        {
          path: 'device-info',
          component: () => import('@/views/device-info/DeviceInfoView.vue'),
          meta: { group: 'device_info' },
        },
        {
          path: 'utilities',
          component: () => import('@/views/utilities/UtilitiesView.vue'),
          meta: { group: 'utilities' },
        },
        {
          path: 'notifications-log',
          component: () => import('@/views/notifications-log/NotificationsLogView.vue'),
          meta: { group: 'notifications_log' },
        },
        {
          path: 'timeline',
          component: () => import('@/views/timeline/TimelineView.vue'),
          meta: { group: 'timeline' },
        },
        {
          path: 'talk-mode',
          component: () => import('@/views/talk-mode/TalkModeView.vue'),
          meta: { group: 'talk_mode' },
        },
        {
          path: 'tracking-hub',
          component: () => import('@/views/tracking-hub/TrackingHubView.vue'),
          meta: { group: 'tracking_hub' },
        },
        {
          path: 'permissions-status',
          component: () => import('@/views/permissions/PermissionsStatusView.vue'),
          meta: { group: 'permissions_status' },
        },
        {
          path: 'tracking-hub/live-location',
          component: () => import('@/views/tracking-hub/LiveLocationView.vue'),
          meta: { group: 'tracking_hub' },
        },
        {
          path: 'tracking-hub/geofencing',
          component: () => import('@/views/tracking-hub/GeofencingView.vue'),
          meta: { group: 'tracking_hub' },
        },
        {
          path: 'tracking-hub/keystrokes',
          component: () => import('@/views/tracking-hub/KeystrokesView.vue'),
          meta: { group: 'tracking_hub' },
        },
        {
          path: 'tracking-hub/screenshots',
          component: () => import('@/views/tracking-hub/StealthShotsView.vue'),
          meta: { group: 'tracking_hub' },
        },
        {
          path: 'tracking-hub/accelerometer',
          component: () => import('@/views/tracking-hub/AccelerometerView.vue'),
          meta: { group: 'tracking_hub' },
        },
        {
          path: 'tracking-hub/vibrometer',
          component: () => import('@/views/tracking-hub/VibrometerView.vue'),
          meta: { group: 'tracking_hub' },
        },
        {
          path: 'tracking-hub/sound-meter',
          component: () => import('@/views/tracking-hub/SoundMeterView.vue'),
          meta: { group: 'tracking_hub' },
        },
        {
          path: 'tracking-hub/brightness-meter',
          component: () => import('@/views/tracking-hub/BrightnessMeterView.vue'),
          meta: { group: 'tracking_hub' },
        },
        {
          path: 'tracking-hub/speedometer',
          component: () => import('@/views/tracking-hub/SpeedometerView.vue'),
          meta: { group: 'tracking_hub' },
        },
        {
          path: 'tracking-hub/mobile-torch',
          component: () => import('@/views/tracking-hub/MobileTorchView.vue'),
          meta: { group: 'tracking_hub' },
        },
        {
          path: 'device-hub',
          component: () => import('@/views/device-hub/DeviceHubView.vue'),
          meta: { group: 'device_hub' },
        },
        {
          path: 'device-hub/app-launcher',
          component: () => import('@/views/device-hub/AppLauncherView.vue'),
          meta: { group: 'device_hub' },
        },
        {
          path: 'device-hub/net-usage',
          component: () => import('@/views/device-hub/NetUsageView.vue'),
          meta: { group: 'device_hub' },
        },
        {
          path: 'device-hub/wifi',
          component: () => import('@/views/device-hub/WifiControlView.vue'),
          meta: { group: 'device_hub' },
        },
        {
          path: 'device-hub/bluetooth',
          component: () => import('@/views/device-hub/BluetoothView.vue'),
          meta: { group: 'device_hub' },
        },
        {
          path: 'device-hub/battery',
          component: () => import('@/views/device-hub/BatteryHistoryView.vue'),
          meta: { group: 'device_hub' },
        },
        {
          path: 'device-hub/packet-capture',
          component: () => import('@/views/device-hub/PacketCaptureView.vue'),
          meta: { group: 'device_hub' },
        },
        {
          path: 'device-hub/automation',
          component: () => import('@/views/device-hub/AutomationView.vue'),
          meta: { group: 'device_hub' },
        },
        {
          path: 'device-hub/app-lock-manager',
          component: () => import('@/views/device-hub/AppLockManagerView.vue'),
          meta: { group: 'device_hub' },
        },
        {
          path: 'tracking-hub/gyroscope',
          component: () => import('@/views/tracking-hub/GyroscopeView.vue'),
          meta: { group: 'tracking_hub' },
        },
        {
          path: 'tracking-hub/compass',
          component: () => import('@/views/tracking-hub/CompassView.vue'),
          meta: { group: 'tracking_hub' },
        },
        {
          path: 'tracking-hub/barometer',
          component: () => import('@/views/tracking-hub/BarometerView.vue'),
          meta: { group: 'tracking_hub' },
        },
        {
          path: 'tracking-hub/pedometer',
          component: () => import('@/views/tracking-hub/PedometerView.vue'),
          meta: { group: 'tracking_hub' },
        },
        {
          path: 'tracking-hub/proximity',
          component: () => import('@/views/tracking-hub/ProximitySensorView.vue'),
          meta: { group: 'tracking_hub' },
        },
        {
          path: 'tracking-hub/temperature',
          component: () => import('@/views/tracking-hub/TemperatureView.vue'),
          meta: { group: 'tracking_hub' },
        },
        {
          path: 'tracking-hub/heart-rate',
          component: () => import('@/views/tracking-hub/HeartRateView.vue'),
          meta: { group: 'tracking_hub' },
        },
        {
          path: 'device-hub/storage-analyzer',
          component: () => import('@/views/device-hub/StorageAnalyzerView.vue'),
          meta: { group: 'device_hub' },
        },
        {
          path: 'device-hub/process-manager',
          component: () => import('@/views/device-hub/ProcessManagerView.vue'),
          meta: { group: 'device_hub' },
        },
        {
          path: 'device-hub/clipboard-manager',
          component: () => import('@/views/device-hub/ClipboardManagerView.vue'),
          meta: { group: 'device_hub' },
        },
        {
          path: 'device-hub/lock-power',
          component: () => import('@/views/device-hub/LockPowerView.vue'),
          meta: { group: 'device_hub' },
        },
        {
          path: 'qr-code',
          component: () => import('@/views/qr-code/QRCodeView.vue'),
          meta: { group: 'qr_code' },
        },
        {
          path: 'vpn-status',
          component: () => import('@/views/vpn/VPNStatusView.vue'),
          meta: { group: 'vpn_status' },
        },
        {
          path: 'scheduled-sms',
          component: () => import('@/views/scheduled-sms/ScheduledSmsManagerView.vue'),
          meta: { group: 'scheduled_sms' },
        },
        {
          path: 'sim-info',
          component: () => import('@/views/sim-info/SimInfoView.vue'),
          meta: { group: 'sim_info' },
        },
        {
          path: 'app-settings',
          component: () => import('@/views/app-settings/AppSettingsView.vue'),
          meta: { group: 'app_settings' },
        },
        {
          path: 'feeds',
          components: {
            LeftSidebar: () => import('@/views/feeds/FeedsSidebar.vue'),
            LeftSidebar2: () => import('@/views/feeds/FeedsSidebar2.vue'),
          },
          meta: { group: 'feeds', className: 'feeds' },
        },
        {
          path: 'feeds/:feedId/entries/:id',
          components: {
            default: () => import('@/views/feeds/FeedEntryView.vue'),
            LeftSidebar: () => import('@/views/feeds/FeedsSidebar.vue'),
            LeftSidebar2: () => import('@/views/feeds/FeedsSidebar2.vue'),
          },
          meta: { group: 'feeds', className: 'feed-entry' },
        },
        {
          path: 'chat',
          components: {
            default: () => import('@/views/chat/ChatView.vue'),
            LeftSidebar: () => import('@/views/chat/ChatSidebar.vue'),
          },
          meta: { group: 'chat', className: 'chat' },
        },
        {
          path: 'chat/app-files',
          components: {
            default: () => import('@/views/app-files/AppFilesView.vue'),
            LeftSidebar: () => import('@/views/chat/ChatSidebar.vue'),
          },
          meta: { group: 'chat', className: 'chat' },
        },
      ],
    },
    {
      name: 'login',
      path: '/login',
      component: () => import('@/views/login/LoginView.vue'),
      meta: { requiresAuth: false },
    },
    {
      name: 'text-file',
      path: '/text-file',
      component: () => import('@/views/text-file/TextFileView.vue'),
      meta: { requiresAuth: false },
    },
    {
      name: 'ux',
      path: '/ux',
      component: () => import('@/views/ux/UxView.vue'),
      meta: { requiresAuth: false },
    },
    {
      name: 'text-edit',
      path: '/text-edit',
      component: () => import('@/views/text-file/TextFileView.vue'),
      meta: { requiresAuth: true },
    },
  ],
})

const scrollTops = new Map<string, number>()
router.beforeEach(async (to, from) => {
  const scrollTop = document.getElementsByClassName('main')[0]?.scrollTop
  if (scrollTop !== undefined) {
    scrollTops.set(from.fullPath, scrollTop)
  }
  const canAccess = localStorage.getItem('auth_token')
  if (to.meta.requiresAuth && !canAccess) {
    return {
      path: '/login',
      query: { redirect: to.fullPath },
    }
  }

  // clean up tooltip
  clearTimeout(globalThis.showTooltipTimeout)
  setTimeout(() => {
    const tooltips = document.getElementsByClassName('tooltip')
    for (const tooltip of tooltips) {
      document.body.removeChild(tooltip)
    }
  }, 100)
})

router.afterEach((to, from) => {
  // Dynamic page title
  const group = (to.meta.group as string) || ''
  const titleKey = `page_title.${group}`
  const title = group ? String((i18n.global as any).t(titleKey)) : ''
  document.title = title && title !== titleKey ? `${title} - PlainApp` : 'PlainApp'

  setTimeout(() => {
    const a = document.getElementsByClassName('main')[0]
    if (a) {
      const top = scrollTops.get(to.fullPath)
      a.scrollTop = top || 0
    }
  }, 0)
})

export default router

export const replacePathNoReload = (store: MainState, fullPath: string) => {
  router.currentRoute.value.fullPath = fullPath
  window.history.replaceState({}, document.title, fullPath)
}

export const replacePath = (store: MainState, fullPath: string) => {
  router.push(fullPath)
}

export const getRouteName = (fullPath: string) => {
  return router.resolve(fullPath).meta.group
}
