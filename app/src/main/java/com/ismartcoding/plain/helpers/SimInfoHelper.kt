package com.ismartcoding.plain.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.ismartcoding.plain.MainApp
import kotlinx.serialization.Serializable

@Serializable
data class SimInfoItem(
    val slotIndex: Int,
    val carrierName: String,
    val operatorName: String,
    val phoneNumber: String,
    val networkTypeName: String,
    val mcc: String,
    val mnc: String,
    val isRoaming: Boolean,
    val isDataActive: Boolean,
    val signalBars: Int,
    val simState: String,
    val iccid: String,
)

object SimInfoHelper {

    @SuppressLint("MissingPermission", "HardwareIds")
    fun getAll(): List<SimInfoItem> {
        val ctx = MainApp.instance
        val result = mutableListOf<SimInfoItem>()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            result.add(legacySimInfo(ctx))
            return result
        }

        val sm = ctx.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            ?: return result
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return result

        val subs = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) sm.completeActiveSubscriptionInfoList
            else @Suppress("DEPRECATION") sm.activeSubscriptionInfoList
        } catch (_: Exception) { null }

        if (subs.isNullOrEmpty()) {
            result.add(legacySimInfo(ctx))
            return result
        }

        val activeDataSubId = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) SubscriptionManager.getDefaultDataSubscriptionId() else -1
        } catch (_: Exception) { -1 }

        for (sub in subs) {
            val subId = sub.subscriptionId
            val tmForSub: TelephonyManager = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) tm.createForSubscriptionId(subId) else tm
            } catch (_: Exception) { tm }

            val networkType = try { tmForSub.dataNetworkType } catch (_: Exception) { TelephonyManager.NETWORK_TYPE_UNKNOWN }
            val mcc = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tmForSub.networkOperator.take(3)
                else sub.mcc.toString()
            } catch (_: Exception) { "" }
            val mnc = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tmForSub.networkOperator.drop(3)
                else sub.mnc.toString()
            } catch (_: Exception) { "" }
            val simState = try {
                simStateToString(tmForSub.simState)
            } catch (_: Exception) { "UNKNOWN" }
            val signalBars = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tmForSub.signalStrength?.level ?: 0 else 0
            } catch (_: Exception) { 0 }

            result.add(SimInfoItem(
                slotIndex = sub.simSlotIndex,
                carrierName = sub.carrierName?.toString() ?: "",
                operatorName = sub.displayName?.toString() ?: "",
                phoneNumber = try { sub.number ?: "" } catch (_: Exception) { "" },
                networkTypeName = networkTypeToName(networkType),
                mcc = mcc,
                mnc = mnc,
                isRoaming = try { tmForSub.isNetworkRoaming } catch (_: Exception) { false },
                isDataActive = subId == activeDataSubId,
                signalBars = signalBars.coerceIn(0, 5),
                simState = simState,
                iccid = try { sub.iccId ?: "" } catch (_: Exception) { "" },
            ))
        }
        return result
    }

    @SuppressLint("MissingPermission")
    private fun legacySimInfo(ctx: Context): SimInfoItem {
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return SimInfoItem(
            slotIndex = 0,
            carrierName = tm.networkOperatorName ?: "",
            operatorName = tm.simOperatorName ?: "",
            phoneNumber = try { tm.line1Number ?: "" } catch (_: Exception) { "" },
            networkTypeName = networkTypeToName(tm.dataNetworkType),
            mcc = tm.networkOperator?.take(3) ?: "",
            mnc = tm.networkOperator?.drop(3) ?: "",
            isRoaming = tm.isNetworkRoaming,
            isDataActive = true,
            signalBars = 0,
            simState = simStateToString(tm.simState),
            iccid = try { tm.simSerialNumber ?: "" } catch (_: Exception) { "" },
        )
    }

    private fun networkTypeToName(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
        TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
        TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
        TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT"
        TelephonyManager.NETWORK_TYPE_IDEN -> "iDEN"
        TelephonyManager.NETWORK_TYPE_UMTS -> "3G (UMTS)"
        TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO rev.0"
        TelephonyManager.NETWORK_TYPE_EVDO_A -> "EVDO rev.A"
        TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
        TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
        TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
        TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO rev.B"
        TelephonyManager.NETWORK_TYPE_EHRPD -> "eHRPD"
        TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
        TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
        TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "TD-SCDMA"
        TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
        TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
        TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
        else -> "Unknown"
    }

    private fun simStateToString(state: Int): String = when (state) {
        TelephonyManager.SIM_STATE_ABSENT -> "ABSENT"
        TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN_REQUIRED"
        TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK_REQUIRED"
        TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "NETWORK_LOCKED"
        TelephonyManager.SIM_STATE_READY -> "READY"
        TelephonyManager.SIM_STATE_NOT_READY -> "NOT_READY"
        TelephonyManager.SIM_STATE_PERM_DISABLED -> "PERM_DISABLED"
        TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "IO_ERROR"
        TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "RESTRICTED"
        else -> "UNKNOWN"
    }
}
