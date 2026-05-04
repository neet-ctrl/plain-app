<template>
  <div v-if="wsStatus" class="top-error">
    {{ $t('fix_disconnect_tips') }}
  </div>
  <router-view />
  <Teleport to="body">
    <MatrixRain v-if="theme.isMatrix" />
    <PanelThemeToggle />
    <modal-container />
    <div v-if="tapPhoneMessage" v-click-away="closeTapPhone" class="tap-phone-container" @click="closeTapPhone">
      <div>
        {{ tapPhoneMessage }}
      </div>
      <TouchPhone />
    </div>
  </Teleport>
</template>
<script setup lang="ts">
import { onMounted } from 'vue'
import { useAppSocket } from '@/hooks/app-socket'
import { usePanelThemeStore } from '@/stores/theme'
import MatrixRain from '@/components/MatrixRain.vue'
import PanelThemeToggle from '@/components/PanelThemeToggle.vue'
import { useRouter } from 'vue-router'

const { wsStatus, tapPhoneMessage, closeTapPhone } = useAppSocket()
const theme = usePanelThemeStore()
const router = useRouter()

function initTelegramMiniApp() {
  const tg = (window as any).Telegram?.WebApp
  if (!tg) return
  tg.ready()
  tg.expand()
  tg.enableClosingConfirmation()

  const root = document.documentElement
  const tc = tg.themeParams ?? {}
  if (tc.bg_color) root.style.setProperty('--tg-theme-bg-color', tc.bg_color)
  if (tc.text_color) root.style.setProperty('--tg-theme-text-color', tc.text_color)
  if (tc.hint_color) root.style.setProperty('--tg-theme-hint-color', tc.hint_color)
  if (tc.link_color) root.style.setProperty('--tg-theme-link-color', tc.link_color)
  if (tc.button_color) root.style.setProperty('--tg-theme-button-color', tc.button_color)
  if (tc.button_text_color) root.style.setProperty('--tg-theme-button-text-color', tc.button_text_color)
  if (tc.secondary_bg_color) root.style.setProperty('--tg-theme-secondary-bg-color', tc.secondary_bg_color)

  tg.BackButton.show()
  tg.BackButton.onClick(() => {
    if (window.history.length > 1) {
      router.back()
    } else {
      tg.close()
    }
  })

  tg.onEvent('themeChanged', () => {
    const t = tg.themeParams ?? {}
    if (t.bg_color) root.style.setProperty('--tg-theme-bg-color', t.bg_color)
    if (t.text_color) root.style.setProperty('--tg-theme-text-color', t.text_color)
    if (t.button_color) root.style.setProperty('--tg-theme-button-color', t.button_color)
    if (t.secondary_bg_color) root.style.setProperty('--tg-theme-secondary-bg-color', t.secondary_bg_color)
  })
}

onMounted(() => {
  theme.init()
  initTelegramMiniApp()
})
</script>

<style scoped>
.top-error {
  background-color: var(--md-sys-color-error);
  color: var(--md-sys-color-on-error);
  padding: 8px;
  font-size: 0.8rem;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
