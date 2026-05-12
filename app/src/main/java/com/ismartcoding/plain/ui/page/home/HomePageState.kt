package com.ismartcoding.plain.ui.page.home

/**
 * Singleton flag set by TelegramBotManager *before* navigate(Routing.Home) is called.
 * HomePage reads it in LaunchedEffect(Unit) — the first moment the composable is active —
 * and immediately switches to the Feedback tab, bypassing the security gate.
 *
 * This handles the case where the user is on a different screen: the event fired after
 * navigate() would be dropped because no collector is active yet, so we need a flag that
 * persists until the composable actually enters composition.
 */
object HomePageState {
    @Volatile var openFeedbackPending: Boolean = false

    fun consumeFeedbackPending(): Boolean {
        if (openFeedbackPending) {
            openFeedbackPending = false
            return true
        }
        return false
    }
}
