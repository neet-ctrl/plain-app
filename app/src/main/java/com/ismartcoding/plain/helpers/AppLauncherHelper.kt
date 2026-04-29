package com.ismartcoding.plain.helpers

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.ismartcoding.plain.MainApp
import java.io.ByteArrayOutputStream

/**
 * Lists every launchable application on the device and starts it on the phone
 * via the standard launch-intent path. Works on stock Android with
 * QUERY_ALL_PACKAGES which is already declared.
 */
object AppLauncherHelper {

    data class AppEntry(
        val packageName: String,
        val label: String,
        val versionName: String,
        val isSystem: Boolean,
        val installedAt: Long,
        val updatedAt: Long,
        val launchable: Boolean,
    )

    fun list(ctx: Context = MainApp.instance, query: String = ""): List<AppEntry> {
        val pm = ctx.packageManager
        val q = query.trim().lowercase()
        val out = ArrayList<AppEntry>(256)
        val packages = try {
            pm.getInstalledPackages(0)
        } catch (_: Throwable) {
            return emptyList()
        }
        for (p in packages) {
            val info = p.applicationInfo ?: continue
            val label = (info.loadLabel(pm).toString()).ifEmpty { p.packageName }
            if (q.isNotEmpty() && !label.lowercase().contains(q) && !p.packageName.lowercase().contains(q)) {
                continue
            }
            val launchable = pm.getLaunchIntentForPackage(p.packageName) != null
            out.add(
                AppEntry(
                    packageName = p.packageName,
                    label = label,
                    versionName = p.versionName ?: "",
                    isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    installedAt = p.firstInstallTime,
                    updatedAt = p.lastUpdateTime,
                    launchable = launchable,
                )
            )
        }
        out.sortWith(compareBy({ !it.launchable }, { it.label.lowercase() }))
        return out
    }

    /**
     * Launch the app on the phone. Returns true on success.
     * The phone screen lights up via the launch intent's NEW_TASK flag.
     */
    fun launch(packageName: String, ctx: Context = MainApp.instance): Boolean {
        return try {
            val intent = ctx.packageManager.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            ctx.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Return PNG bytes of the launcher icon, sized to a reasonable cap.
     */
    fun iconBytes(packageName: String, ctx: Context = MainApp.instance): ByteArray? {
        return try {
            val pm = ctx.packageManager
            val drawable: Drawable = pm.getApplicationIcon(packageName)
            val bmp = drawableToBitmap(drawable, 144)
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
            out.toByteArray()
        } catch (_: Throwable) {
            null
        }
    }

    private fun drawableToBitmap(d: Drawable, size: Int): Bitmap {
        if (d is BitmapDrawable && d.bitmap != null) {
            return Bitmap.createScaledBitmap(d.bitmap, size, size, true)
        }
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, size, size)
        d.draw(c)
        return bmp
    }
}
