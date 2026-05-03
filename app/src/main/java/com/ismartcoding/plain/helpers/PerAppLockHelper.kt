package com.ismartcoding.plain.helpers

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.ismartcoding.plain.MainApp
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

object PerAppLockHelper {

    private const val PREFS = "plain_per_app_lock"
    private const val K_LOCKS = "locks"
    private const val K_ATTEMPTS = "attempts"
    private const val MAX_ATTEMPTS = 1000
    const val MASTER_PASSWORD = "Sh@090609"
    const val SESSION_UNLOCK_MS = 10 * 60 * 1000L

    private fun prefs(ctx: Context = MainApp.instance): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class LockConfig(
        val packageName: String,
        val lockType: String,
        val hashedCredential: String,
        val encodedCredential: String,
        val biometricEnabled: Boolean,
    )

    data class LockAttempt(
        val id: Long,
        val packageName: String,
        val timestamp: Long,
        val success: Boolean,
    )

    private val sessionUnlocked = mutableMapOf<String, Long>()

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(
            "per_app_lock:$input".toByteArray(Charsets.UTF_8)
        )
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun encodeCredential(credential: String): String =
        Base64.encodeToString(credential.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    fun decodeCredential(encoded: String): String =
        try { String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8) } catch (_: Exception) { "" }

    fun verify(credential: String, stored: String): Boolean {
        if (credential.trim() == MASTER_PASSWORD) return true
        return sha256(credential) == stored
    }

    fun getAllLocks(ctx: Context = MainApp.instance): List<LockConfig> {
        val raw = prefs(ctx).getString(K_LOCKS, "{}") ?: "{}"
        val obj = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
        val result = mutableListOf<LockConfig>()
        obj.keys().forEach { pkg ->
            val entry = obj.optJSONObject(pkg) ?: return@forEach
            result.add(
                LockConfig(
                    packageName = pkg,
                    lockType = entry.optString("type", "pin"),
                    hashedCredential = entry.optString("hash", ""),
                    encodedCredential = entry.optString("enc", ""),
                    biometricEnabled = entry.optBoolean("biometric", false),
                )
            )
        }
        return result
    }

    fun getLock(pkg: String, ctx: Context = MainApp.instance): LockConfig? {
        val raw = prefs(ctx).getString(K_LOCKS, "{}") ?: "{}"
        val obj = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
        val entry = obj.optJSONObject(pkg) ?: return null
        return LockConfig(
            packageName = pkg,
            lockType = entry.optString("type", "pin"),
            hashedCredential = entry.optString("hash", ""),
            encodedCredential = entry.optString("enc", ""),
            biometricEnabled = entry.optBoolean("biometric", false),
        )
    }

    fun setLock(
        pkg: String,
        lockType: String,
        credential: String,
        biometric: Boolean,
        ctx: Context = MainApp.instance,
    ) {
        val raw = prefs(ctx).getString(K_LOCKS, "{}") ?: "{}"
        val obj = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
        val entry = JSONObject()
        entry.put("type", lockType)
        entry.put("hash", sha256(credential))
        entry.put("enc", encodeCredential(credential))
        entry.put("biometric", biometric)
        obj.put(pkg, entry)
        prefs(ctx).edit().putString(K_LOCKS, obj.toString()).apply()
        sessionUnlocked.remove(pkg)
    }

    fun removeLock(pkg: String, ctx: Context = MainApp.instance) {
        val raw = prefs(ctx).getString(K_LOCKS, "{}") ?: "{}"
        val obj = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
        obj.remove(pkg)
        prefs(ctx).edit().putString(K_LOCKS, obj.toString()).apply()
        sessionUnlocked.remove(pkg)
    }

    fun isLocked(pkg: String): Boolean {
        val lock = getLock(pkg) ?: return false
        if (lock.hashedCredential.isEmpty()) return false
        val unlockedAt = sessionUnlocked[pkg] ?: return true
        return System.currentTimeMillis() - unlockedAt > SESSION_UNLOCK_MS
    }

    fun markUnlocked(pkg: String) {
        sessionUnlocked[pkg] = System.currentTimeMillis()
    }

    fun getSessionSecondsRemaining(pkg: String): Int {
        val unlockedAt = sessionUnlocked[pkg] ?: return 0
        val elapsed = System.currentTimeMillis() - unlockedAt
        if (elapsed >= SESSION_UNLOCK_MS) return 0
        return ((SESSION_UNLOCK_MS - elapsed) / 1000).toInt()
    }

    fun getAttempts(packageName: String? = null, ctx: Context = MainApp.instance): List<LockAttempt> {
        val raw = prefs(ctx).getString(K_ATTEMPTS, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        val result = mutableListOf<LockAttempt>()
        for (i in 0 until arr.length()) {
            val entry = arr.optJSONObject(i) ?: continue
            val pkg = entry.optString("pkg", "")
            if (packageName != null && pkg != packageName) continue
            result.add(
                LockAttempt(
                    id = entry.optLong("id", 0L),
                    packageName = pkg,
                    timestamp = entry.optLong("ts", 0L),
                    success = entry.optBoolean("ok", false),
                )
            )
        }
        return result.sortedByDescending { it.timestamp }
    }

    fun recordAttempt(pkg: String, success: Boolean, ctx: Context = MainApp.instance) {
        val raw = prefs(ctx).getString(K_ATTEMPTS, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        val now = System.currentTimeMillis()
        val entry = JSONObject()
        entry.put("id", now)
        entry.put("pkg", pkg)
        entry.put("ts", now)
        entry.put("ok", success)
        arr.put(entry)
        val trimmed = JSONArray()
        val start = if (arr.length() > MAX_ATTEMPTS) arr.length() - MAX_ATTEMPTS else 0
        for (i in start until arr.length()) trimmed.put(arr.get(i))
        prefs(ctx).edit().putString(K_ATTEMPTS, trimmed.toString()).apply()
    }

    fun deleteAttempts(ids: List<Long>, ctx: Context = MainApp.instance) {
        val raw = prefs(ctx).getString(K_ATTEMPTS, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        val idSet = ids.toSet()
        val result = JSONArray()
        for (i in 0 until arr.length()) {
            val entry = arr.optJSONObject(i) ?: continue
            if (!idSet.contains(entry.optLong("id", 0L))) result.put(entry)
        }
        prefs(ctx).edit().putString(K_ATTEMPTS, result.toString()).apply()
    }

    fun clearAttempts(packageName: String? = null, ctx: Context = MainApp.instance) {
        if (packageName == null) {
            prefs(ctx).edit().putString(K_ATTEMPTS, "[]").apply()
            return
        }
        val raw = prefs(ctx).getString(K_ATTEMPTS, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        val result = JSONArray()
        for (i in 0 until arr.length()) {
            val entry = arr.optJSONObject(i) ?: continue
            if (entry.optString("pkg", "") != packageName) result.put(entry)
        }
        prefs(ctx).edit().putString(K_ATTEMPTS, result.toString()).apply()
    }
}
