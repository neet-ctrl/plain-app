package com.neet.tracker.ui.viewmodels

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.neet.tracker.alarm.AlarmScheduler
import com.neet.tracker.alarm.BootReceiver
import com.neet.tracker.alarm.SavedAlarm
import com.neet.tracker.data.database.NEETDao
import com.neet.tracker.data.models.Reminder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReminderViewModel @Inject constructor(
    application: Application,
    private val dao: NEETDao
) : AndroidViewModel(application) {

    private val prefs: SharedPreferences =
        application.getSharedPreferences("neet_alarms", Context.MODE_PRIVATE)

    val reminders = dao.getReminders().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val activeReminders = dao.getActiveReminders().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun scheduleReminder(reminder: Reminder) {
        viewModelScope.launch {
            dao.saveReminder(reminder)
            val ctx = getApplication<Application>()
            AlarmScheduler.scheduleAlarm(
                ctx,
                reminder.id.hashCode(),
                reminder.triggerAtMillis,
                reminder.title,
                reminder.message
            )
            persistAlarm(
                SavedAlarm(
                    id = reminder.id.hashCode(),
                    triggerAtMillis = reminder.triggerAtMillis,
                    title = reminder.title,
                    message = reminder.message
                )
            )
        }
    }

    fun cancelReminder(reminder: Reminder) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            AlarmScheduler.cancelAlarm(ctx, reminder.id.hashCode())
            dao.deleteReminder(reminder)
            removePersistedAlarm(reminder.id.hashCode())
        }
    }

    fun toggleReminder(reminder: Reminder) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            if (reminder.isActive) {
                AlarmScheduler.cancelAlarm(ctx, reminder.id.hashCode())
                dao.setReminderActive(reminder.id, false)
            } else {
                AlarmScheduler.scheduleAlarm(
                    ctx, reminder.id.hashCode(),
                    reminder.triggerAtMillis, reminder.title, reminder.message
                )
                dao.setReminderActive(reminder.id, true)
            }
        }
    }

    fun scheduleQuickAlarm(
        context: Context,
        alarmId: Int,
        triggerAtMillis: Long,
        title: String,
        message: String
    ) {
        AlarmScheduler.scheduleAlarm(context, alarmId, triggerAtMillis, title, message)
        persistAlarm(SavedAlarm(alarmId, triggerAtMillis, title, message))
    }

    fun cancelQuickAlarm(context: Context, alarmId: Int) {
        AlarmScheduler.cancelAlarm(context, alarmId)
        removePersistedAlarm(alarmId)
    }

    private fun persistAlarm(alarm: SavedAlarm) {
        val existing = loadSavedAlarms().toMutableList()
        existing.removeAll { it.id == alarm.id }
        existing.add(alarm)
        prefs.edit().putString("saved_alarms", Gson().toJson(existing)).apply()
    }

    private fun removePersistedAlarm(id: Int) {
        val existing = loadSavedAlarms().toMutableList()
        existing.removeAll { it.id == id }
        prefs.edit().putString("saved_alarms", Gson().toJson(existing)).apply()
    }

    private fun loadSavedAlarms(): List<SavedAlarm> {
        val json = prefs.getString("saved_alarms", null) ?: return emptyList()
        val type = object : TypeToken<List<SavedAlarm>>() {}.type
        return Gson().fromJson(json, type) ?: emptyList()
    }
}
