package com.ismartcoding.plain.web.schemas

import com.ismartcoding.lib.kgraphql.schema.dsl.SchemaBuilder
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.helpers.AppLauncherHelper
import com.ismartcoding.plain.helpers.AutomationActionRunner
import com.ismartcoding.plain.helpers.AutomationHelper
import com.ismartcoding.plain.helpers.AutomationScheduler
import com.ismartcoding.plain.helpers.BatteryHistoryHelper
import com.ismartcoding.plain.helpers.BluetoothControlHelper
import com.ismartcoding.plain.helpers.NetworkUsageHelper
import com.ismartcoding.plain.helpers.PacketCaptureHelper
import com.ismartcoding.plain.helpers.WifiControlHelper
import com.ismartcoding.plain.services.PacketCaptureVpnService
import kotlinx.serialization.Serializable

// ---------- App launcher ----------

@Serializable
data class LaunchAppModel(
    val packageName: String,
    val label: String,
    val versionName: String,
    val isSystem: Boolean,
    val installedAt: Long,
    val updatedAt: Long,
    val launchable: Boolean,
)

// ---------- Network usage ----------

@Serializable
data class NetAppUsageModel(
    val packageName: String,
    val label: String,
    val rxBytes: Long,
    val txBytes: Long,
    val rxBytesWifi: Long,
    val txBytesWifi: Long,
    val rxBytesMobile: Long,
    val txBytesMobile: Long,
)

@Serializable
data class NetUsageWindowModel(
    val sinceMs: Long,
    val untilMs: Long,
    val totalRx: Long,
    val totalTx: Long,
    val activeNetwork: String,
    val usageAccessGranted: Boolean,
    val apps: List<NetAppUsageModel>,
)

// ---------- Wi-Fi ----------

@Serializable
data class WifiNetworkModel(
    val ssid: String,
    val bssid: String,
    val capabilities: String,
    val frequencyMhz: Int,
    val rssi: Int,
    val channelWidth: Int,
    val seenMs: Long,
    val isCurrent: Boolean,
)

@Serializable
data class WifiStateModel(
    val enabled: Boolean,
    val connectedSsid: String,
    val connectedBssid: String,
    val rssi: Int,
    val linkSpeedMbps: Int,
    val frequencyMhz: Int,
    val ipv4: String,
    val hotspotState: String,
    val savedListAccessible: Boolean,
    val canScan: Boolean,
)

// ---------- Bluetooth ----------

@Serializable
data class BtDeviceModel(
    val address: String,
    val name: String,
    val type: String,
    val bondState: String,
    val rssi: Int,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val nearby: Boolean,
)

@Serializable
data class BtStateModel(
    val supported: Boolean,
    val enabled: Boolean,
    val scanning: Boolean,
    val hasScanPermission: Boolean,
    val hasConnectPermission: Boolean,
    val pairedCount: Int,
    val nearbyCount: Int,
)

// ---------- Battery ----------

@Serializable
data class BatterySampleModel(
    val ts: Long,
    val level: Int,
    val plugged: Int,
    val temperatureC: Double,
    val voltageMv: Int,
    val status: Int,
)

@Serializable
data class BatteryWindowModel(
    val sinceMs: Long,
    val untilMs: Long,
    val charging: Boolean,
    val currentLevel: Int,
    val plugged: String,
    val samples: List<BatterySampleModel>,
)

// ---------- Packet capture ----------

@Serializable
data class PacketEntryModel(
    val id: String,
    val ts: Long,
    val host: String,
    val port: Int,
    val protocol: String,
    val appPackage: String,
    val appLabel: String,
    val sizeBytes: Int,
    val resolvedIp: String,
)

@Serializable
data class PacketStateModel(
    val supported: Boolean,
    val enabled: Boolean,
    val running: Boolean,
    val totalEntries: Int,
    val needsConsent: Boolean,
)

// ---------- Automation ----------

@Serializable
data class KvModel(val key: String, val value: String)

@Serializable
data class TriggerModel(val type: String, val params: List<KvModel>)

@Serializable
data class ConditionModel(val type: String, val params: List<KvModel>)

@Serializable
data class ActionModel(val type: String, val params: List<KvModel>)

@Serializable
data class AutomationRuleModel(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val kind: String,
    val trigger: TriggerModel,
    val conditions: List<ConditionModel>,
    val actions: List<ActionModel>,
    val cooldownMs: Long,
    val lastRunMs: Long,
    val createdMs: Long,
    val updatedMs: Long,
)

@Serializable
data class AutomationRunModel(
    val id: String,
    val ruleId: String,
    val ruleName: String,
    val ts: Long,
    val ok: Boolean,
    val source: String,
    val log: List<String>,
)

@Serializable
data class AutomationStateModel(
    val enabled: Boolean,
    val ruleCount: Int,
    val activeCount: Int,
    val nextScheduledMs: Long,
)

@Serializable
data class KvIn(val key: String = "", val value: String = "")

@Serializable
data class TriggerIn(val type: String = "manual", val params: List<KvIn> = emptyList())

@Serializable
data class ConditionIn(val type: String = "", val params: List<KvIn> = emptyList())

@Serializable
data class ActionIn(val type: String = "", val params: List<KvIn> = emptyList())

@Serializable
data class AutomationRuleInput(
    val id: String = "",
    val name: String = "Untitled",
    val enabled: Boolean = true,
    val kind: String = "rule",
    val trigger: TriggerIn = TriggerIn(),
    val conditions: List<ConditionIn> = emptyList(),
    val actions: List<ActionIn> = emptyList(),
    val cooldownMs: Long = 0L,
)

// ---------- Schema registration ----------

fun SchemaBuilder.addDeviceControlSchema() {

    // ---- App launcher ----
    query("launchApps") {
        resolver { query: String ->
            AppLauncherHelper.list(MainApp.instance, query).map {
                LaunchAppModel(
                    packageName = it.packageName,
                    label = it.label,
                    versionName = it.versionName,
                    isSystem = it.isSystem,
                    installedAt = it.installedAt,
                    updatedAt = it.updatedAt,
                    launchable = it.launchable,
                )
            }
        }
    }
    mutation("launchApp") {
        resolver { packageName: String ->
            AppLauncherHelper.launch(packageName, MainApp.instance)
        }
    }

    // ---- Network usage ----
    query("networkUsage") {
        resolver { windowDays: Int ->
            val w = NetworkUsageHelper.query(windowDays.coerceIn(1, 90), MainApp.instance)
            NetUsageWindowModel(
                sinceMs = w.sinceMs,
                untilMs = w.untilMs,
                totalRx = w.totalRx,
                totalTx = w.totalTx,
                activeNetwork = NetworkUsageHelper.activeNetworkType(MainApp.instance),
                usageAccessGranted = w.usageAccessGranted,
                apps = w.apps.map {
                    NetAppUsageModel(
                        packageName = it.packageName, label = it.label,
                        rxBytes = it.rxBytes, txBytes = it.txBytes,
                        rxBytesWifi = it.rxBytesWifi, txBytesWifi = it.txBytesWifi,
                        rxBytesMobile = it.rxBytesMobile, txBytesMobile = it.txBytesMobile,
                    )
                },
            )
        }
    }

    // ---- Wi-Fi ----
    query("wifiState") {
        resolver { ->
            val s = WifiControlHelper.state(MainApp.instance)
            WifiStateModel(
                enabled = s.enabled,
                connectedSsid = s.connectedSsid, connectedBssid = s.connectedBssid,
                rssi = s.rssi, linkSpeedMbps = s.linkSpeedMbps, frequencyMhz = s.frequencyMhz,
                ipv4 = s.ipv4, hotspotState = s.hotspotState,
                savedListAccessible = s.savedListAccessible, canScan = s.canScan,
            )
        }
    }
    query("wifiScan") {
        resolver { ->
            WifiControlHelper.scan(MainApp.instance).map {
                WifiNetworkModel(
                    ssid = it.ssid, bssid = it.bssid, capabilities = it.capabilities,
                    frequencyMhz = it.frequencyMhz, rssi = it.rssi,
                    channelWidth = it.channelWidth, seenMs = it.seenMs,
                    isCurrent = it.isCurrent,
                )
            }
        }
    }
    mutation("setWifiEnabled") {
        resolver { enabled: Boolean ->
            WifiControlHelper.setWifiEnabled(enabled, MainApp.instance)
        }
    }

    // ---- Bluetooth ----
    query("bluetoothState") {
        resolver { ->
            val s = BluetoothControlHelper.state(MainApp.instance)
            BtStateModel(
                supported = s.supported, enabled = s.enabled,
                scanning = s.scanning,
                hasScanPermission = s.hasScanPermission,
                hasConnectPermission = s.hasConnectPermission,
                pairedCount = s.pairedCount, nearbyCount = s.nearbyCount,
            )
        }
    }
    query("bluetoothPaired") {
        resolver { ->
            BluetoothControlHelper.pairedList(MainApp.instance).map { it.toModel() }
        }
    }
    query("bluetoothNearby") {
        resolver { ->
            BluetoothControlHelper.nearbyList().map { it.toModel() }
        }
    }
    mutation("bluetoothStartScan") {
        resolver { -> BluetoothControlHelper.startScan(MainApp.instance) }
    }
    mutation("bluetoothStopScan") {
        resolver { -> BluetoothControlHelper.stopScan(MainApp.instance) }
    }
    mutation("bluetoothPair") {
        resolver { address: String -> BluetoothControlHelper.pair(address, MainApp.instance) }
    }
    mutation("bluetoothUnpair") {
        resolver { address: String -> BluetoothControlHelper.unpair(address, MainApp.instance) }
    }
    mutation("setBluetoothEnabled") {
        resolver { enabled: Boolean -> BluetoothControlHelper.setEnabled(enabled, MainApp.instance) }
    }

    // ---- Battery ----
    query("batteryHistory") {
        resolver { days: Int ->
            val w = BatteryHistoryHelper.window(days.coerceIn(1, 60), MainApp.instance)
            BatteryWindowModel(
                sinceMs = w.sinceMs, untilMs = w.untilMs,
                charging = w.charging, currentLevel = w.currentLevel,
                plugged = w.plugged,
                samples = w.samples.map {
                    BatterySampleModel(
                        ts = it.ts, level = it.level, plugged = it.plugged,
                        temperatureC = it.temperatureC.toDouble(),
                        voltageMv = it.voltageMv, status = it.status,
                    )
                },
            )
        }
    }
    mutation("clearBatteryHistory") {
        resolver { ->
            BatteryHistoryHelper.clear(MainApp.instance)
            true
        }
    }
    mutation("setBatteryHistoryEnabled") {
        resolver { enabled: Boolean ->
            BatteryHistoryHelper.setEnabled(enabled, MainApp.instance)
            true
        }
    }

    // ---- Packet capture ----
    query("packetCaptureState") {
        resolver { ->
            val running = PacketCaptureHelper.isRunning(MainApp.instance)
            val enabled = PacketCaptureHelper.isEnabled(MainApp.instance)
            PacketStateModel(
                supported = true,
                enabled = enabled,
                running = running,
                totalEntries = PacketCaptureHelper.count(MainApp.instance),
                // The web UI uses this to surface "tap to grant VPN consent" because
                // VpnService.prepare() must be triggered from an Activity on-device.
                needsConsent = enabled && !running,
            )
        }
    }
    query("packetEntries") {
        resolver { offset: Int, limit: Int, host: String ->
            PacketCaptureHelper.list(offset, limit.coerceIn(1, 1000), host, MainApp.instance).map {
                PacketEntryModel(
                    id = it.id, ts = it.ts, host = it.host, port = it.port,
                    protocol = it.protocol, appPackage = it.appPackage,
                    appLabel = it.appLabel, sizeBytes = it.sizeBytes,
                    resolvedIp = it.resolvedIp,
                )
            }
        }
    }
    mutation("setPacketCaptureEnabled") {
        resolver { enabled: Boolean ->
            PacketCaptureHelper.setEnabled(enabled, MainApp.instance)
            if (!enabled) {
                PacketCaptureVpnService.stop(MainApp.instance)
            } else {
                // Best-effort start; if VPN consent has not been granted yet the
                // service will fail to establish() and report needsConsent=true on
                // the next state query.
                PacketCaptureVpnService.start(MainApp.instance)
            }
            true
        }
    }
    mutation("clearPacketEntries") {
        resolver { ->
            PacketCaptureHelper.clear(MainApp.instance)
            true
        }
    }

    // ---- Automation ----
    query("automationState") {
        resolver { ->
            val rules = AutomationHelper.list(MainApp.instance)
            val now = System.currentTimeMillis()
            val nextScheduled = rules
                .filter { it.enabled && (it.trigger.type == "time" || it.trigger.type == "scheduled_once") }
                .mapNotNull {
                    when (it.trigger.type) {
                        "scheduled_once" -> it.trigger.params["atMs"]?.toLongOrNull()?.takeIf { ms -> ms > now }
                        "time" -> nextDailyMs(it.trigger.params["hour"]?.toIntOrNull() ?: -1,
                            it.trigger.params["minute"]?.toIntOrNull() ?: 0)
                        else -> null
                    }
                }.minOrNull() ?: 0L
            AutomationStateModel(
                enabled = AutomationHelper.isEnabled(MainApp.instance),
                ruleCount = rules.size,
                activeCount = rules.count { it.enabled },
                nextScheduledMs = nextScheduled,
            )
        }
    }
    query("automationRules") {
        resolver { ->
            AutomationHelper.list(MainApp.instance).map { it.toModel() }
        }
    }
    query("automationRuns") {
        resolver { limit: Int ->
            AutomationHelper.runs(limit.coerceIn(1, 200), MainApp.instance).map {
                AutomationRunModel(
                    id = it.id, ruleId = it.ruleId, ruleName = it.ruleName,
                    ts = it.ts, ok = it.ok, source = it.source, log = it.log,
                )
            }
        }
    }
    mutation("setAutomationEnabled") {
        resolver { enabled: Boolean ->
            AutomationHelper.setEnabled(enabled, MainApp.instance)
            if (enabled) AutomationScheduler.scheduleAll(MainApp.instance)
            true
        }
    }
    // The web panel sends the rule as a single JSON string instead of a nested
    // GraphQL input object. The previous typed input (`AutomationRuleInput`) had
    // three levels of nested input lists (`AutomationRuleInput → ConditionIn →
    // List<KvIn>`) which kgraphql could not deserialize reliably, causing every
    // "Save rule" click to silently no-op. JSON keeps the wire format flat and
    // mirrors what the bot sends in /newrule, /newschedule, /editrule.
    mutation("upsertAutomationRuleJson") {
        resolver { ruleJson: String ->
            val saved = AutomationHelper.upsert(parseRuleJson(ruleJson), MainApp.instance)
            AutomationScheduler.scheduleRule(saved.id, MainApp.instance)
            saved.toModel()
        }
    }
    // Legacy typed-input mutation kept so older bundled web panels still work.
    mutation("upsertAutomationRule") {
        resolver { input: AutomationRuleInput ->
            val rule = AutomationHelper.Rule(
                id = input.id,
                name = input.name,
                enabled = input.enabled,
                kind = input.kind,
                trigger = AutomationHelper.Trigger(
                    input.trigger.type,
                    input.trigger.params.associate { it.key to it.value },
                ),
                conditions = input.conditions.map {
                    AutomationHelper.Condition(it.type, it.params.associate { p -> p.key to p.value })
                },
                actions = input.actions.map {
                    AutomationHelper.Action(it.type, it.params.associate { p -> p.key to p.value })
                },
                cooldownMs = input.cooldownMs,
                lastRunMs = 0L, createdMs = 0L, updatedMs = 0L,
            )
            val saved = AutomationHelper.upsert(rule, MainApp.instance)
            AutomationScheduler.scheduleRule(saved.id, MainApp.instance)
            saved.toModel()
        }
    }
    mutation("setAutomationRuleEnabled") {
        resolver { id: String, enabled: Boolean ->
            val ok = AutomationHelper.setEnabled(id, enabled, MainApp.instance)
            if (ok) AutomationScheduler.scheduleRule(id, MainApp.instance)
            ok
        }
    }
    mutation("deleteAutomationRule") {
        resolver { id: String ->
            AutomationScheduler.cancel(id, MainApp.instance)
            AutomationHelper.delete(id, MainApp.instance)
        }
    }
    mutation("runAutomationRule") {
        resolver { id: String ->
            AutomationActionRunner.trigger(id, "manual", emptyMap(), MainApp.instance)
        }
    }
    mutation("clearAutomationRuns") {
        resolver { ->
            AutomationHelper.clearRuns(MainApp.instance)
            true
        }
    }
}

/**
 * Parse a flat JSON payload sent by the web panel or the Telegram bot into an
 * [AutomationHelper.Rule] ready to be persisted by [AutomationHelper.upsert].
 *
 * Expected shape (all fields optional except trigger.type):
 * {
 *   "id": "",                      // blank for new rule
 *   "name": "Bedtime",
 *   "enabled": true,
 *   "kind": "rule",                // or "schedule"
 *   "cooldownMs": 0,
 *   "trigger": { "type": "time", "params": { "hour": "22", "minute": "30" } },
 *   "conditions": [{ "type": "wifi_ssid", "params": { "ssid": "Home" } }],
 *   "actions":    [{ "type": "notify", "params": { "title": "...", "body": "..." } }]
 * }
 */
fun parseRuleJson(json: String): AutomationHelper.Rule {
    val o = org.json.JSONObject(json)
    fun jsonToMap(j: org.json.JSONObject?): Map<String, String> {
        if (j == null) return emptyMap()
        return j.keys().asSequence().associateWith { j.optString(it, "") }
    }
    val tObj = o.optJSONObject("trigger") ?: org.json.JSONObject()
    val trigger = AutomationHelper.Trigger(
        tObj.optString("type", "manual"),
        jsonToMap(tObj.optJSONObject("params")),
    )
    val condArr = o.optJSONArray("conditions") ?: org.json.JSONArray()
    val conds = (0 until condArr.length()).map {
        val c = condArr.getJSONObject(it)
        AutomationHelper.Condition(c.optString("type"), jsonToMap(c.optJSONObject("params")))
    }
    val actArr = o.optJSONArray("actions") ?: org.json.JSONArray()
    val acts = (0 until actArr.length()).map {
        val a = actArr.getJSONObject(it)
        AutomationHelper.Action(a.optString("type"), jsonToMap(a.optJSONObject("params")))
    }
    return AutomationHelper.Rule(
        id = o.optString("id", ""),
        name = o.optString("name", "Untitled").ifBlank { "Untitled" },
        enabled = o.optBoolean("enabled", true),
        kind = o.optString("kind", "rule").ifBlank { "rule" },
        trigger = trigger,
        conditions = conds,
        actions = acts,
        cooldownMs = o.optLong("cooldownMs", 0L),
        lastRunMs = 0L, createdMs = 0L, updatedMs = 0L,
    )
}

private fun nextDailyMs(hour: Int, minute: Int): Long? {
    if (hour < 0) return null
    val cal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) {
            add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
    }
    return cal.timeInMillis
}

private fun AutomationHelper.Rule.toModel(): AutomationRuleModel = AutomationRuleModel(
    id = id, name = name, enabled = enabled, kind = kind,
    trigger = TriggerModel(trigger.type, trigger.params.map { KvModel(it.key, it.value) }),
    conditions = conditions.map { c -> ConditionModel(c.type, c.params.map { KvModel(it.key, it.value) }) },
    actions = actions.map { a -> ActionModel(a.type, a.params.map { KvModel(it.key, it.value) }) },
    cooldownMs = cooldownMs, lastRunMs = lastRunMs,
    createdMs = createdMs, updatedMs = updatedMs,
)

// ---------- Local conversion helpers ----------

private fun BluetoothControlHelper.Device.toModel(): BtDeviceModel = BtDeviceModel(
    address = address, name = name, type = type, bondState = bondState,
    rssi = rssi, firstSeenMs = firstSeenMs, lastSeenMs = lastSeenMs,
    nearby = nearby,
)
