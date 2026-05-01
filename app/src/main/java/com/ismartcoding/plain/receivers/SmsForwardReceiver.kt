package com.ismartcoding.plain.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.ismartcoding.plain.telegram.TelegramBotManager

class SmsForwardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!TelegramBotManager.isRunning || !TelegramBotManager.forwardSmsEnabled) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return
        val sender = messages.first().originatingAddress ?: "Unknown"
        val body = messages.joinToString("") { it.messageBody ?: "" }
        TelegramBotManager.forwardSms(sender, body)
    }
}
