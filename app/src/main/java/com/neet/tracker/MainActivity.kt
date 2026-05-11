package com.neet.tracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.neet.tracker.alarm.NotificationNavEvent
import com.neet.tracker.navigation.NEETNavHost
import com.neet.tracker.ui.theme.NEETTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_EVENT_ID   = "open_event_id"
        const val EXTRA_EVENT_TYPE = "open_event_type"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleNotificationIntent(intent)
        setContent {
            NEETTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color.Transparent
                ) {
                    NEETNavHost()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val eventId = intent?.getStringExtra(EXTRA_EVENT_ID) ?: return
        if (eventId.isNotBlank()) {
            val eventType = intent.getStringExtra(EXTRA_EVENT_TYPE) ?: ""
            NotificationNavEvent.emit(eventId, eventType)
        }
    }
}
