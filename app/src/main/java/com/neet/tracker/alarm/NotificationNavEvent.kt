package com.neet.tracker.alarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object NotificationNavEvent {
    private val _pendingEvent = MutableStateFlow<Pair<String, String>?>(null)
    val pendingEvent = _pendingEvent.asStateFlow()

    fun emit(eventId: String, eventType: String) {
        if (eventId.isNotBlank()) _pendingEvent.value = eventId to eventType
    }

    fun consume() {
        _pendingEvent.value = null
    }
}
