package com.ismartcoding.plain.workers

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.ismartcoding.plain.telegram.FileForwardService
import java.util.concurrent.TimeUnit

class FileForwardScanWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        FileForwardService.scanAllDirs(ctx)
        FileForwardService.startIfEnabled(ctx)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "file_forward_scan_periodic"

        fun enqueue(ctx: Context) {
            try {
                val req = PeriodicWorkRequest.Builder(
                    FileForwardScanWorker::class.java,
                    15,
                    TimeUnit.MINUTES,
                ).build()
                WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    req,
                )
            } catch (ignored: Exception) {}
        }
    }
}
