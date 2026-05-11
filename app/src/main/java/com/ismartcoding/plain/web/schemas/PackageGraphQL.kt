package com.ismartcoding.plain.web.schemas

import com.ismartcoding.lib.kgraphql.GraphQLError
import com.ismartcoding.lib.kgraphql.schema.dsl.SchemaBuilder
import com.ismartcoding.lib.apk.ApkParsers
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.features.PackageHelper
import com.ismartcoding.plain.features.Permission
import com.ismartcoding.plain.features.Permissions
import com.ismartcoding.plain.features.file.FileSortBy
import com.ismartcoding.plain.helpers.ApkUpdateHelper
import com.ismartcoding.plain.packageManager
import com.ismartcoding.plain.ui.MainActivity
import com.ismartcoding.plain.web.models.ID
import com.ismartcoding.plain.web.models.PackageInstallPending
import com.ismartcoding.plain.web.models.PackageStatus
import com.ismartcoding.plain.web.models.toModel
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.time.Instant
import java.io.File
import java.util.concurrent.TimeUnit

fun SchemaBuilder.addPackageSchema() {
    query("packages") {
        resolver { offset: Int, limit: Int, query: String, sortBy: FileSortBy ->
            Permissions.checkAsync(MainApp.instance, setOf(Permission.QUERY_ALL_PACKAGES))
            PackageHelper.searchAsync(query, limit, offset, sortBy).map { it.toModel() }
        }
    }
    query("packageStatuses") {
        resolver { ids: List<ID> ->
            Permissions.checkAsync(MainApp.instance, setOf(Permission.QUERY_ALL_PACKAGES))
            PackageHelper.getPackageInfoMap(ids.map { it.value }).map {
                val pkg = it.value
                val updatedAt = if (pkg != null) Instant.fromEpochMilliseconds(pkg.lastUpdateTime) else null
                PackageStatus(ID(it.key), pkg != null, updatedAt)
            }
        }
    }
    query("packageCount") {
        resolver { query: String ->
            if (Permission.QUERY_ALL_PACKAGES.enabledAndCanAsync(MainApp.instance)) {
                PackageHelper.count(query)
            } else {
                0
            }
        }
    }
    mutation("uninstallPackages") {
        resolver { ids: List<ID> ->
            Permissions.checkAsync(MainApp.instance, setOf(Permission.QUERY_ALL_PACKAGES))
            val activity = MainActivity.instance.get()
                ?: throw GraphQLError("PlainApp is not open on the device. Open the app once after reboot to uninstall packages.")
            ids.forEach {
                PackageHelper.uninstall(activity, it.value)
            }
            true
        }
    }
    mutation("installPackage") {
        resolver { path: String ->
            Permissions.checkAsync(MainApp.instance, setOf(Permission.QUERY_ALL_PACKAGES))
            val file = File(path)
            if (!file.exists()) {
                throw GraphQLError("File does not exist")
            }

            try {
                // FLAG_ACTIVITY_NEW_TASK is already set in PackageHelper.install(), so
                // applicationContext works here — MainActivity does not need to be open.
                val context = MainApp.instance
                if (file.name.endsWith(".apk", ignoreCase = true)) {
                    LogCat.d("Installing APK file: ${file.name}")
                    val apkMeta = ApkParsers.getMetaInfo(file)
                        ?: throw GraphQLError("Failed to parse APK package ID")

                    PackageHelper.install(context, file)
                    val packageName = apkMeta.packageName ?: ""
                    try {
                        val pkg = packageManager.getPackageInfo(packageName, 0)
                        PackageInstallPending(packageName, Instant.fromEpochMilliseconds(pkg.lastUpdateTime), isNew = false)
                    } catch (e: Exception) {
                        PackageInstallPending(packageName, null, isNew = true)
                    }
                } else {
                    throw GraphQLError("Unsupported file format. Only APK files are supported.")
                }
            } catch (e: Exception) {
                LogCat.e("Installation failed: ${e.message}", e)
                throw GraphQLError("Installation failed: ${e.message}")
            }
        }
    }
    mutation("installPackageFromUrl") {
        resolver { url: String ->
            Permissions.checkAsync(MainApp.instance, setOf(Permission.QUERY_ALL_PACKAGES))
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                throw GraphQLError("Invalid URL: must start with http:// or https://")
            }
            val ctx = MainApp.instance
            val destDir = File(ctx.cacheDir, "apk_updates")
            destDir.mkdirs()
            val destFile = File(destDir, "apk_url_${System.currentTimeMillis()}.apk")
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder().url(url).header("User-Agent", "PlainApp/1.0").get().build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw GraphQLError("Download failed: HTTP ${resp.code}")
                    resp.body?.byteStream()?.use { input ->
                        destFile.outputStream().use { out -> input.copyTo(out) }
                    } ?: throw GraphQLError("Download failed: empty response body")
                }
            } catch (e: GraphQLError) {
                throw e
            } catch (e: Exception) {
                throw GraphQLError("Download failed: ${e.message}")
            }
            if (!destFile.exists() || destFile.length() == 0L) {
                throw GraphQLError("Download failed: file is empty")
            }
            val isOwner = ApkUpdateHelper.isDeviceOwner(ctx)
            ApkUpdateHelper.install(ctx, destFile) { _, _ -> }
            isOwner
        }
    }
}
