package com.ismartcoding.plain.helpers

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.ismartcoding.lib.helpers.StringHelper
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp
import java.io.File

/**
 * Lightweight one-shot audio recorder used when a geofence trigger fires.
 * Records to GeofencingHelper.audiosDir() as a private MP4/AAC file, then
 * appends a GeofenceAudio entry to the helper.
 *
 * This is independent of CallRecorderHelper so a geofence trigger during a
 * call cannot disturb the call recording.
 */
object GeofenceAudioRecorderHelper {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var currentEventId: String = ""
    private var currentGeofenceId: String = ""
    private var currentGeofenceName: String = ""
    private var startedAt: Long = 0L

    @Synchronized
    fun start(geofenceId: String, geofenceName: String, eventId: String, ctx: Context = MainApp.instance): Boolean {
        try {
            stop()
            val dir = GeofencingHelper.audiosDir(ctx)
            val file = File(dir, "geofence_${StringHelper.shortUUID()}.m4a")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
            // VOICE_RECOGNITION matches the call recorder's pattern - clean stream
            // that survives different audio modes.
            try { r.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION) }
            catch (_: Throwable) { r.setAudioSource(MediaRecorder.AudioSource.MIC) }
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(96_000)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            currentFile = file
            currentEventId = eventId
            currentGeofenceId = geofenceId
            currentGeofenceName = geofenceName
            startedAt = System.currentTimeMillis()
            return true
        } catch (e: Throwable) {
            LogCat.e("GeofenceAudioRecorderHelper.start failed: ${e.message}", e)
            recorder = null
            currentFile?.delete()
            currentFile = null
            return false
        }
    }

    @Synchronized
    fun stop(): GeofencingHelper.GeofenceAudio? {
        val r = recorder ?: return null
        val f = currentFile
        recorder = null
        try {
            try { r.stop() } catch (_: Throwable) {}
            try { r.release() } catch (_: Throwable) {}
        } catch (_: Throwable) {}
        if (f != null && f.exists() && f.length() > 0) {
            val audio = GeofencingHelper.GeofenceAudio(
                id = StringHelper.shortUUID(),
                geofenceId = currentGeofenceId,
                geofenceName = currentGeofenceName,
                eventId = currentEventId,
                ts = startedAt,
                durationMs = System.currentTimeMillis() - startedAt,
                sizeBytes = f.length(),
                absPath = f.absolutePath,
            )
            GeofencingHelper.appendAudio(audio)
            currentFile = null
            return audio
        }
        currentFile = null
        return null
    }

    fun isRecording(): Boolean = recorder != null
}
