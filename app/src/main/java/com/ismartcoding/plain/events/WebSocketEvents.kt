package com.ismartcoding.plain.events

import com.ismartcoding.lib.channel.ChannelEvent
import com.ismartcoding.plain.ui.page.pomodoro.PomodoroState
import kotlinx.serialization.Serializable

sealed class WebSocketData {
    data class Text(val value: String) : WebSocketData()
    data class Binary(val value: ByteArray) : WebSocketData() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Binary

            return value.contentEquals(other.value)
        }

        override fun hashCode(): Int {
            return value.contentHashCode()
        }
    }
}

// The events sent to the web client via WebSocket
class WebSocketEvent(
    val type: EventType,
    val data: WebSocketData,
) : ChannelEvent() // Event will be sent to web client
{
    constructor(type: EventType, data: String) : this(type, WebSocketData.Text(data))
    constructor(type: EventType, data: ByteArray) : this(type, WebSocketData.Binary(data))
}

enum class EventType(val value: Int) {
    MESSAGE_CREATED(1),
    MESSAGE_DELETED(2),
    MESSAGE_UPDATED(3),
    FEEDS_FETCHED(4),
    SCREEN_MIRRORING(5),
    WEBRTC_SIGNALING(6),
    NOTIFICATION_CREATED(7),
    NOTIFICATION_UPDATED(8),
    NOTIFICATION_DELETED(9),
    NOTIFICATION_REFRESHED(10),
    POMODORO_ACTION(11),
    POMODORO_SETTINGS_UPDATE(12),
    MESSAGE_CLEARED(13),
    SCREEN_MIRROR_AUDIO_GRANTED(14),
    BOOKMARK_UPDATED(15),
    DOWNLOAD_PROGRESS(16),
    MMS_SENT(17),
    CHANNELS_UPDATED(18),
    IMAGE_SEARCH_UPDATED(19),
    LIVE_CAMERA_STREAMING(20),
    LIVE_MIC_STREAMING(21),
    TIMELINE_EVENT(22),
    LIVE_CALL_STATE(23),
    NOTIFICATION_LOG_APPENDED(24),
    CALL_RECORDER_STATE(25),
    CALL_RECORDINGS_CHANGED(26),
    LIVE_CAPTURES_CHANGED(27),
    LOCATION_UPDATE(28),
    GEOFENCE_EVENT(29),
    GEOFENCE_AUDIO_CHANGED(30),
    GEOFENCES_CHANGED(31),
}


@Serializable
data class PomodoroActionData(
    val action: String, val timeLeft: Int,
    val totalTime: Int, val completedCount: Int,
    val round: Int, val state: PomodoroState
) // action: "start", "pause",  "stop"