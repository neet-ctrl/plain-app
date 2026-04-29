package com.ismartcoding.plain.helpers

import android.content.Context
import com.ismartcoding.plain.MainApp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.max

/**
 * Tasker-style "if this then that" engine + scheduled actions.
 *
 * Stores Rules and ActionRuns in SharedPreferences as JSON. Triggers are
 * evaluated by [AutomationActionRunner] which knows how to perform every action
 * type, and by [com.ismartcoding.plain.receivers.AutomationAlarmReceiver] for
 * time-of-day triggers. The action runner also surfaces a manual "run now"
 * verb so every rule can be tested even if its real-world trigger source is
 * not wired up on this device.
 */
object AutomationHelper {
    private const val PREFS = "plain_automation"
    private const val K_RULES = "rules"
    private const val K_RUNS = "runs"
    private const val K_ENABLED = "enabled"
    private const val K_VARS = "vars"
    const val MAX_RUNS = 200
    const val MAX_RULES = 200

    // ---------------- Models ----------------

    data class Rule(
        val id: String,
        val name: String,
        val enabled: Boolean,
        val trigger: Trigger,
        val conditions: List<Condition>,
        val actions: List<Action>,
        /** Minimum gap between two runs of this rule, in ms. 0 = no debounce. */
        val cooldownMs: Long,
        val lastRunMs: Long,
        val createdMs: Long,
        val updatedMs: Long,
        /** "rule" = if-this-then-that. "schedule" = pure scheduled action. */
        val kind: String,
    )

    /** Trigger.type ∈ {time, manual, battery_level, battery_charging, incoming_call,
     *  sms_received, wifi_connected, bluetooth_connected, app_launched, headphones,
     *  boot_completed, scheduled_once} */
    data class Trigger(
        val type: String,
        /** Free-form params, e.g. {hour:21, minute:0, days:"1,2,3,4,5"} or {threshold:20, op:"<"} */
        val params: Map<String, String>,
    )

    /** Condition.type ∈ {time_window, day_of_week, battery_level, charging, wifi_ssid, silent_mode} */
    data class Condition(
        val type: String,
        val params: Map<String, String>,
    )

    /** Action.type ∈ {send_sms, make_call, take_photo, start_recording, stop_recording,
     *  launch_app, set_volume, set_ringer, toggle_wifi, toggle_bluetooth, toggle_dnd,
     *  set_clipboard, speak, notify, send_webhook, http_get, delay, vibrate,
     *  flashlight, lock_screen} */
    data class Action(
        val type: String,
        val params: Map<String, String>,
    )

    data class ActionRun(
        val id: String,
        val ruleId: String,
        val ruleName: String,
        val ts: Long,
        val ok: Boolean,
        /** Per-action result lines, e.g. ["notify ✓", "send_sms ✗ permission"] */
        val log: List<String>,
        /** "trigger" | "manual" | "scheduled" | "boot" */
        val source: String,
    )

    // ---------------- Persistence ----------------

    private fun prefs(ctx: Context = MainApp.instance) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context = MainApp.instance): Boolean =
        prefs(ctx).getBoolean(K_ENABLED, true)

    fun setEnabled(enabled: Boolean, ctx: Context = MainApp.instance) {
        prefs(ctx).edit().putBoolean(K_ENABLED, enabled).apply()
    }

    @Synchronized
    fun list(ctx: Context = MainApp.instance): List<Rule> {
        val raw = prefs(ctx).getString(K_RULES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { ruleFromJson(arr.getJSONObject(it)) }
        } catch (_: Throwable) { emptyList() }
    }

    fun byId(id: String, ctx: Context = MainApp.instance): Rule? =
        list(ctx).firstOrNull { it.id == id }

    @Synchronized
    fun upsert(rule: Rule, ctx: Context = MainApp.instance): Rule {
        val all = list(ctx).toMutableList()
        val now = System.currentTimeMillis()
        val final = if (rule.id.isBlank()) {
            rule.copy(id = UUID.randomUUID().toString(), createdMs = now, updatedMs = now)
        } else {
            val idx = all.indexOfFirst { it.id == rule.id }
            if (idx >= 0) {
                val ex = all[idx]
                val updated = rule.copy(createdMs = ex.createdMs, updatedMs = now,
                    lastRunMs = ex.lastRunMs)
                all[idx] = updated
                save(all, ctx)
                return updated
            }
            rule.copy(createdMs = now, updatedMs = now)
        }
        if (all.size >= MAX_RULES) all.removeAt(all.size - 1)
        all.add(0, final)
        save(all, ctx)
        return final
    }

    @Synchronized
    fun delete(id: String, ctx: Context = MainApp.instance): Boolean {
        val all = list(ctx).toMutableList()
        val removed = all.removeAll { it.id == id }
        if (removed) save(all, ctx)
        return removed
    }

    @Synchronized
    fun setEnabled(id: String, enabled: Boolean, ctx: Context = MainApp.instance): Boolean {
        val all = list(ctx).toMutableList()
        val idx = all.indexOfFirst { it.id == id }
        if (idx < 0) return false
        all[idx] = all[idx].copy(enabled = enabled, updatedMs = System.currentTimeMillis())
        save(all, ctx)
        return true
    }

    @Synchronized
    fun markRan(id: String, ctx: Context = MainApp.instance) {
        val all = list(ctx).toMutableList()
        val idx = all.indexOfFirst { it.id == id }
        if (idx < 0) return
        all[idx] = all[idx].copy(lastRunMs = System.currentTimeMillis())
        save(all, ctx)
    }

    private fun save(rules: List<Rule>, ctx: Context) {
        val arr = JSONArray()
        rules.forEach { arr.put(ruleToJson(it)) }
        prefs(ctx).edit().putString(K_RULES, arr.toString()).apply()
    }

    @Synchronized
    fun runs(limit: Int = 50, ctx: Context = MainApp.instance): List<ActionRun> {
        val raw = prefs(ctx).getString(K_RUNS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).take(max(1, limit)).map { runFromJson(arr.getJSONObject(it)) }
        } catch (_: Throwable) { emptyList() }
    }

    @Synchronized
    fun appendRun(run: ActionRun, ctx: Context = MainApp.instance) {
        val list = runs(MAX_RUNS, ctx).toMutableList()
        list.add(0, run)
        if (list.size > MAX_RUNS) list.subList(MAX_RUNS, list.size).clear()
        val arr = JSONArray()
        list.forEach { arr.put(runToJson(it)) }
        prefs(ctx).edit().putString(K_RUNS, arr.toString()).apply()
    }

    @Synchronized
    fun clearRuns(ctx: Context = MainApp.instance) {
        prefs(ctx).edit().remove(K_RUNS).apply()
    }

    // ---------------- Variables (used by templating in actions) ----------------

    @Synchronized
    fun setVariable(key: String, value: String, ctx: Context = MainApp.instance) {
        val raw = prefs(ctx).getString(K_VARS, "{}")
        val obj = try { JSONObject(raw ?: "{}") } catch (_: Throwable) { JSONObject() }
        obj.put(key, value)
        prefs(ctx).edit().putString(K_VARS, obj.toString()).apply()
    }

    @Synchronized
    fun variables(ctx: Context = MainApp.instance): Map<String, String> {
        val raw = prefs(ctx).getString(K_VARS, "{}") ?: "{}"
        return try {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { o.optString(it, "") }
        } catch (_: Throwable) { emptyMap() }
    }

    // ---------------- JSON ----------------

    private fun mapToJson(m: Map<String, String>): JSONObject {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, v) }
        return o
    }

    private fun jsonToMap(o: JSONObject?): Map<String, String> {
        if (o == null) return emptyMap()
        return o.keys().asSequence().associateWith { o.optString(it, "") }
    }

    private fun ruleToJson(r: Rule): JSONObject = JSONObject().apply {
        put("id", r.id); put("name", r.name); put("enabled", r.enabled)
        put("kind", r.kind)
        put("trigger", JSONObject().apply {
            put("type", r.trigger.type); put("params", mapToJson(r.trigger.params))
        })
        val condArr = JSONArray()
        r.conditions.forEach { condArr.put(JSONObject().apply {
            put("type", it.type); put("params", mapToJson(it.params))
        }) }
        put("conditions", condArr)
        val actArr = JSONArray()
        r.actions.forEach { actArr.put(JSONObject().apply {
            put("type", it.type); put("params", mapToJson(it.params))
        }) }
        put("actions", actArr)
        put("cooldownMs", r.cooldownMs)
        put("lastRunMs", r.lastRunMs)
        put("createdMs", r.createdMs)
        put("updatedMs", r.updatedMs)
    }

    private fun ruleFromJson(o: JSONObject): Rule {
        val tObj = o.optJSONObject("trigger") ?: JSONObject()
        val trigger = Trigger(
            tObj.optString("type", "manual"),
            jsonToMap(tObj.optJSONObject("params")),
        )
        val condArr = o.optJSONArray("conditions") ?: JSONArray()
        val conds = (0 until condArr.length()).map {
            val c = condArr.getJSONObject(it)
            Condition(c.optString("type"), jsonToMap(c.optJSONObject("params")))
        }
        val actArr = o.optJSONArray("actions") ?: JSONArray()
        val acts = (0 until actArr.length()).map {
            val a = actArr.getJSONObject(it)
            Action(a.optString("type"), jsonToMap(a.optJSONObject("params")))
        }
        return Rule(
            id = o.optString("id"),
            name = o.optString("name", "Untitled"),
            enabled = o.optBoolean("enabled", true),
            trigger = trigger,
            conditions = conds,
            actions = acts,
            cooldownMs = o.optLong("cooldownMs", 0L),
            lastRunMs = o.optLong("lastRunMs", 0L),
            createdMs = o.optLong("createdMs", System.currentTimeMillis()),
            updatedMs = o.optLong("updatedMs", System.currentTimeMillis()),
            kind = o.optString("kind", "rule"),
        )
    }

    private fun runToJson(r: ActionRun): JSONObject = JSONObject().apply {
        put("id", r.id); put("ruleId", r.ruleId); put("ruleName", r.ruleName)
        put("ts", r.ts); put("ok", r.ok); put("source", r.source)
        val arr = JSONArray()
        r.log.forEach { arr.put(it) }
        put("log", arr)
    }

    private fun runFromJson(o: JSONObject): ActionRun {
        val arr = o.optJSONArray("log") ?: JSONArray()
        val log = (0 until arr.length()).map { arr.optString(it, "") }
        return ActionRun(
            id = o.optString("id"),
            ruleId = o.optString("ruleId"),
            ruleName = o.optString("ruleName"),
            ts = o.optLong("ts"),
            ok = o.optBoolean("ok"),
            log = log,
            source = o.optString("source", "manual"),
        )
    }
}
