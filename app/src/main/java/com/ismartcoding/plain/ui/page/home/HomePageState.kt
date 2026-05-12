package com.ismartcoding.plain.ui.page.home

/**
 * Singleton flag set by TelegramBotManager *before* navigate(Routing.Home) is called.
 * HomePage reads it synchronously inside `remember { }` — the very first composition frame —
 * so selectedTab and feedbackUnlocked are initialised to "feedback"/true before any
 * LaunchedEffect or WindowFocusChangedEvent can race against them and snap the tab back.
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
