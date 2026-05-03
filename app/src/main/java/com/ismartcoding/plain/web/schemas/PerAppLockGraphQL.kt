package com.ismartcoding.plain.web.schemas

import com.ismartcoding.lib.kgraphql.GraphQLError
import com.ismartcoding.lib.kgraphql.schema.dsl.SchemaBuilder
import com.ismartcoding.plain.helpers.PerAppLockHelper
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.preferences.TelegramBotPasswordPreference
import com.ismartcoding.plain.preferences.TelegramBotPasswordEnabledPreference
import com.ismartcoding.plain.telegram.TelegramBotManager

data class PerAppLock(
    val packageName: String,
    val lockType: String,
    val biometricEnabled: Boolean,
    val totalAttempts: Int,
    val wrongAttempts: Int,
)

data class PerAppLockAttempt(
    val id: Long,
    val packageName: String,
    val timestamp: Long,
    val success: Boolean,
)

data class TelegramBotPasswordSettings(
    val enabled: Boolean,
    val hasPassword: Boolean,
)

fun SchemaBuilder.addPerAppLockSchema() {

    type<PerAppLock> {}
    type<PerAppLockAttempt> {}
    type<TelegramBotPasswordSettings> {}

    query("perAppLocks") {
        resolver { ->
            PerAppLockHelper.getAllLocks().map { config ->
                val attempts = PerAppLockHelper.getAttempts(config.packageName)
                PerAppLock(
                    packageName = config.packageName,
                    lockType = config.lockType,
                    biometricEnabled = config.biometricEnabled,
                    totalAttempts = attempts.size,
                    wrongAttempts = attempts.count { !it.success },
                )
            }
        }
    }

    query("perAppLockAttempts") {
        resolver { packageName: String ->
            PerAppLockHelper.getAttempts(packageName.ifBlank { null }).map { a ->
                PerAppLockAttempt(
                    id = a.id,
                    packageName = a.packageName,
                    timestamp = a.timestamp,
                    success = a.success,
                )
            }
        }
    }

    query("telegramBotPasswordSettings") {
        resolver { ->
            val ctx = MainApp.instance
            TelegramBotPasswordSettings(
                enabled = TelegramBotPasswordEnabledPreference.getAsync(ctx),
                hasPassword = TelegramBotPasswordPreference.getAsync(ctx).isNotBlank(),
            )
        }
    }

    mutation("setPerAppLock") {
        resolver { packageName: String, lockType: String, credential: String, biometricEnabled: Boolean ->
            if (credential.isBlank()) throw GraphQLError("Credential cannot be empty")
            if (lockType !in listOf("pin", "pattern")) throw GraphQLError("lockType must be 'pin' or 'pattern'")
            if (lockType == "pin" && (credential.any { !it.isDigit() } || credential.length < 4)) {
                throw GraphQLError("PIN must be at least 4 digits")
            }
            PerAppLockHelper.setLock(packageName, lockType, credential, biometricEnabled)
            true
        }
    }

    mutation("removePerAppLock") {
        resolver { packageName: String ->
            PerAppLockHelper.removeLock(packageName)
            true
        }
    }

    mutation("verifyPerAppLock") {
        resolver { packageName: String, credential: String ->
            val config = PerAppLockHelper.getLock(packageName) ?: return@resolver true
            val ok = PerAppLockHelper.verify(credential, config.hashedCredential)
            PerAppLockHelper.recordAttempt(packageName, ok)
            if (ok) PerAppLockHelper.markUnlocked(packageName)
            ok
        }
    }

    mutation("revealPerAppLockCredential") {
        resolver { packageName: String, masterPassword: String ->
            if (masterPassword.trim() != PerAppLockHelper.MASTER_PASSWORD) {
                throw GraphQLError("Master password is required to reveal credentials")
            }
            val config = PerAppLockHelper.getLock(packageName)
                ?: throw GraphQLError("No lock configured for $packageName")
            PerAppLockHelper.decodeCredential(config.encodedCredential)
        }
    }

    mutation("deletePerAppLockAttempts") {
        resolver { packageName: String, ids: List<Long> ->
            if (ids.isEmpty()) {
                PerAppLockHelper.clearAttempts(packageName.ifBlank { null })
            } else {
                PerAppLockHelper.deleteAttempts(ids)
            }
            true
        }
    }

    mutation("setTelegramBotPassword") {
        resolver { enabled: Boolean, password: String ->
            val ctx = MainApp.instance
            TelegramBotPasswordEnabledPreference.putAsync(ctx, enabled)
            if (password.isNotBlank()) {
                TelegramBotPasswordPreference.putAsync(ctx, password)
            }
            TelegramBotManager.botPasswordEnabled = enabled
            if (password.isNotBlank()) TelegramBotManager.botPassword = password
            true
        }
    }
}
