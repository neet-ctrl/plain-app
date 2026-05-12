package com.ismartcoding.plain.ui.models

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ismartcoding.lib.channel.sendEvent
import com.ismartcoding.lib.extensions.queryOpenableFileName
import com.ismartcoding.lib.helpers.ZipHelper
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.R
import com.ismartcoding.plain.contentResolver
import com.ismartcoding.plain.events.RestartAppEvent
import com.ismartcoding.plain.features.locale.LocaleHelper
import com.ismartcoding.plain.features.locale.LocaleHelper.getString
import com.ismartcoding.plain.helpers.BackupManager
import com.ismartcoding.plain.helpers.FileHelper
import com.ismartcoding.plain.ui.helpers.DialogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipOutputStream

class BackupRestoreViewModel : ViewModel() {

    /**
     * Legacy path — on Android 9 where ACTION_CREATE_DOCUMENT is broken on many OEM devices,
     * write the backup directly to app-specific external storage (no permission required).
     */
    fun backupToFile(context: Context, fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            DialogHelper.showLoading()
            try {
                val tmpFile = File(context.cacheDir, fileName)
                ZipOutputStream(FileOutputStream(tmpFile)).use { BackupManager.writeBackup(it, context) }
                val destFile = FileHelper.createPublicFile(fileName, Environment.DIRECTORY_DOWNLOADS)
                tmpFile.copyTo(destFile, overwrite = true)
                tmpFile.delete()
                DialogHelper.hideLoading()
                DialogHelper.showConfirmDialog("", LocaleHelper.getStringF(R.string.exported_to, "name", destFile.absolutePath))
            } catch (e: Throwable) {
                LogCat.e("Backup failed: ${e.message}")
                DialogHelper.hideLoading()
                DialogHelper.showMessage(e.message ?: getString(R.string.error))
            }
        }
    }

    fun backup(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            DialogHelper.showLoading()
            try {
                val stream = contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Failed to open output stream")
                ZipOutputStream(stream).use { BackupManager.writeBackup(it, context) }
                val fileName = contentResolver.queryOpenableFileName(uri)
                DialogHelper.hideLoading()
                DialogHelper.showConfirmDialog("", LocaleHelper.getStringF(R.string.exported_to, "name", fileName))
            } catch (e: Throwable) {
                LogCat.e("Backup failed: ${e.message}")
                DialogHelper.hideLoading()
                DialogHelper.showMessage(e.message ?: getString(R.string.error))
            }
        }
    }

    fun restore(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            DialogHelper.showLoading()
            try {
                val fileName = contentResolver.queryOpenableFileName(uri)
                if (!fileName.endsWith(".plain") && !fileName.endsWith(".zip")) {
                    DialogHelper.hideLoading()
                    DialogHelper.showMessage(R.string.invalid_file)
                    return@launch
                }
                contentResolver.openInputStream(uri)?.use { stream ->
                    val destDir = File(context.cacheDir, "restore")
                    if (destDir.exists()) destDir.deleteRecursively()
                    if (!ZipHelper.unzip(stream, destDir)) throw IllegalStateException("Failed to unzip backup file")
                    BackupManager.restoreFrom(destDir, context)
                    destDir.deleteRecursively()
                }
                DialogHelper.hideLoading()
                DialogHelper.showConfirmDialog("", getString(R.string.app_restored)) {
                    sendEvent(RestartAppEvent())
                }
            } catch (e: Throwable) {
                LogCat.e("Restore failed: ${e.message}")
                DialogHelper.hideLoading()
                DialogHelper.showMessage(e.message ?: getString(R.string.error))
            }
        }
    }
}
