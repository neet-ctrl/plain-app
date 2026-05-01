package com.ismartcoding.plain.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.ismartcoding.plain.telegram.TelegramBotManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsForwardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return
        val sender = messages.first().originatingAddress ?: "Unknown"
        val body = messages.joinToString("") { it.messageBody ?: "" }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TelegramBotManager.forwardSmsStandalone(context, sender, body)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
