package com.ismartcoding.plain.helpers

import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import com.ismartcoding.lib.channel.sendEvent
import com.ismartcoding.lib.helpers.JsonHelper
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.events.EventType
import com.ismartcoding.plain.events.WebSocketEvent
import com.ismartcoding.plain.preferences.CallRecorderEnabledPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Tracks every phone / VoIP call and records the microphone for the entire
 * duration into the app's external-files dir (`<app>/files/CallRecordings/`).
 *
 * - Source: [MediaRecorder.AudioSource.MIC]. Capturing the actual VOICE_CALL
 *   stream requires a system signature permission on modern Android, so we
 *   capture the mic instead. To pick up the remote party as well, the user
 *   should turn on speakerphone for the call (the same hint the live-listen
 *   page already shows).
 * - Format: AAC inside MP4 container (`.m4a`), 96 kbps mono 44.1 kHz.
 * - Each recording has a sidecar `.json` with metadata.
 */
object CallRecorderHelper {

    @Serializable
    data class Meta(
        val id: String,
        val filename: String,
        val displayName: String,
        val source: String,        // phone | whatsapp | telegram | …
        val direction: String,     // incoming | outgoing
        val appId: String,
        val appName: String,
        val startedAt: Long,
        val endedAt: Long,
        val durationMs: Long,
        val sizeBytes: Long,
        /** "VOICE_RECOGNITION" | "VOICE_COMMUNICATION" | "MIC" — what actually opened. */
        val audioSource: String = "MIC",
        /** True when we forced speakerphone on for the duration of recording. */
        val speakerphoneForced: Boolean = false,
    )

    @Serializable
    data class State(
        val enabled: Boolean,
        val recording: Boolean,
        val currentDisplayName: String,
        val currentSource: String,
        val currentStartedAt: Long,
        val totalCount: Int,
        val totalSize: Long,
        val lastError: String,
        /** "VOICE_RECOGNITION" | "VOICE_COMMUNICATION" | "MIC" or "" when idle. */
        val activeAudioSource: String = "",
        /** True when speakerphone is currently forced on for this recording. */
        val speakerphoneForced: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    @Volatile private var recorder: MediaRecorder? = null
    @Volatile private var currentFile: File? = null
    @Volatile private var currentDisplayName: String = ""
    @Volatile private var currentSource: String = ""
    @Volatile private var currentDirection: String = ""
    @Volatile private var currentAppId: String = ""
    @Volatile private var currentAppName: String = ""
    @Volatile private var startedAt: Long = 0
    @Volatile private var lastError: String = ""
    @Volatile private var activeAudioSource: String = ""
    @Volatile private var speakerphoneForced: Boolean = false
    /** Saved AudioManager state we have to restore when recording stops. */
    @Volatile private var prevSpeakerphone: Boolean? = null
    @Volatile private var prevMicMuted: Boolean? = null

    fun isRecording(): Boolean = recorder != null

    /**
     * Storage is the app's *internal* `filesDir` (i.e. `/data/data/<pkg>/files/...`),
     * which is **not browsable by any file manager, gallery, or other app**
     * without root — only the running PlainApp process can read it. The
     * additional `.PlainPrivate` parent + `.nomedia` marker make sure media
     * scanners and shell `ls` ignore it as well.
     *
     * The web panel still has full access because the Ktor server runs inside
     * this same app process and reads the file by absolute path.
     */
    fun recordingsDir(context: Context = MainApp.instance): File {
        val privateRoot = File(context.filesDir, ".PlainPrivate").apply { if (!exists()) mkdirs() }
        runCatching { File(privateRoot, ".nomedia").apply { if (!exists()) createNewFile() } }
        val dir = File(privateRoot, "CallRecordings").apply { if (!exists()) mkdirs() }
        runCatching { File(dir, ".nomedia").apply { if (!exists()) createNewFile() } }
        migrateLegacyRecordingsIfNeeded(context, dir)
        return dir
    }

    /**
     * Older builds stored recordings under `getExternalFilesDir(null)/CallRecordings`,
     * which was visible in Android's per-app folder. Move any leftovers into
     * the new private location on first access.
     */
    @Volatile private var legacyMigrated = false
    private fun migrateLegacyRecordingsIfNeeded(context: Context, target: File) {
        if (legacyMigrated) return
        legacyMigrated = true
        runCatching {
            val legacyBase = context.getExternalFilesDir(null) ?: return@runCatching
            val legacyDir = File(legacyBase, "CallRecordings")
            if (!legacyDir.exists() || !legacyDir.isDirectory) return@runCatching
            legacyDir.listFiles()?.forEach { src ->
                val dst = File(target, src.name)
                if (!dst.exists()) {
                    runCatching { src.copyTo(dst, overwrite = false) }
                }
                runCatching { src.delete() }
            }
            runCatching { legacyDir.delete() }
        }
    }

    /** Called by [com.ismartcoding.plain.services.LiveCallTracker] when a call becomes active. */
    fun onCallActive(
        displayName: String,
        source: String,
        direction: String,
        appId: String,
        appName: String,
    ) {
        scope.launch {
            try {
                val enabled = CallRecorderEnabledPreference.getAsync(MainApp.instance)
                if (!enabled) return@launch
                startRecording(displayName, source, direction, appId, appName)
            } catch (t: Throwable) {
                LogCat.e("CallRecorder onCallActive failed: ${t.message}")
                lastError = t.message ?: t.javaClass.simpleName
                publishState()
            }
        }
    }

    /** Called by LiveCallTracker when a call ends. */
    fun onCallEnded() {
        scope.launch { stopRecording() }
    }

    private fun sanitize(s: String): String {
        return s.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").take(40).ifEmpty { "Unknown" }
    }

    /**
     * Audio sources we try in order. On modern Android (10+):
     *  - `VOICE_RECOGNITION` is the most reliable — it does not get muted by
     *    the in-call audio policy and pulls a clean mic stream with no AEC,
     *    which captures the loudspeaker very well when speakerphone is on.
     *  - `VOICE_COMMUNICATION` matches the call's audio mode (good for VoIP)
     *    but on some Samsung / Xiaomi ROMs the system blocks third-party
     *    apps from opening it during MODE_IN_COMMUNICATION.
     *  - `MIC` is the universal fallback.
     */
    private val sourceChain = intArrayOf(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        MediaRecorder.AudioSource.MIC,
    )

    private fun sourceName(s: Int): String = when (s) {
        MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
        MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
        MediaRecorder.AudioSource.MIC -> "MIC"
        else -> "OTHER($s)"
    }

    /**
     * Force the routing that gives us both sides of the call. Saves the
     * previous state so we can restore it cleanly when the call ends.
     *
     * Why this is safe to do here (unlike for the live-listen page): the
     * recording is fully local — it never touches the cloudflared edge —
     * so the brief radio/audio-routing reshuffle some OEMs do does not
     * affect anything the user notices.
     */
    private fun forceLoudspeakerCapture(ctx: Context) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            prevMicMuted = am.isMicrophoneMute
            if (am.isMicrophoneMute) am.isMicrophoneMute = false
        }
        runCatching {
            prevSpeakerphone = am.isSpeakerphoneOn
            if (!am.isSpeakerphoneOn) {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = true
                speakerphoneForced = true
            }
        }
    }

    private fun restoreLoudspeakerCapture(ctx: Context) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        prevSpeakerphone?.let { prev ->
            runCatching {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = prev
            }
        }
        prevMicMuted?.let { prev -> runCatching { am.isMicrophoneMute = prev } }
        prevSpeakerphone = null
        prevMicMuted = null
        speakerphoneForced = false
    }

    @Synchronized
    private fun startRecording(
        displayName: String,
        source: String,
        direction: String,
        appId: String,
        appName: String,
    ) {
        synchronized(lock) {
            if (recorder != null) return
            val ctx = MainApp.instance

            // 1) Force routing for both sides BEFORE we open the mic.
            forceLoudspeakerCapture(ctx)

            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val safeName = sanitize(displayName.ifEmpty { source })
            val file = File(recordingsDir(ctx), "${ts}_${sanitize(source)}_${safeName}.m4a")

            // 2) Try the source-fallback chain. If a source fails, log and
            //    fall through to the next one rather than giving up.
            var openedRec: MediaRecorder? = null
            var openedSourceName = ""
            var firstError: Throwable? = null
            for (src in sourceChain) {
                val candidate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(ctx)
                } else {
                    @Suppress("DEPRECATION") MediaRecorder()
                }
                try {
                    candidate.setAudioSource(src)
                    candidate.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    candidate.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    candidate.setAudioChannels(1)
                    candidate.setAudioSamplingRate(44100)
                    candidate.setAudioEncodingBitRate(96_000)
                    candidate.setOutputFile(file.absolutePath)
                    candidate.prepare()
                    candidate.start()
                    openedRec = candidate
                    openedSourceName = sourceName(src)
                    LogCat.d("CallRecorder opened source=$openedSourceName")
                    break
                } catch (t: Throwable) {
                    if (firstError == null) firstError = t
                    LogCat.e("CallRecorder source ${sourceName(src)} failed: ${t.message}")
                    runCatching { candidate.release() }
                }
            }

            if (openedRec == null) {
                LogCat.e("CallRecorder all sources failed: ${firstError?.message}")
                lastError = firstError?.message ?: "All audio sources failed"
                restoreLoudspeakerCapture(ctx)
                publishState()
                return
            }

            recorder = openedRec
            currentFile = file
            currentDisplayName = displayName
            currentSource = source
            currentDirection = direction
            currentAppId = appId
            currentAppName = appName
            startedAt = System.currentTimeMillis()
            lastError = ""
            activeAudioSource = openedSourceName
            LogCat.d("CallRecorder started → ${file.absolutePath} (source=$openedSourceName speakerphoneForced=$speakerphoneForced)")
        }
        publishState()
    }

    @Synchronized
    private fun stopRecording() {
        var savedFile: File? = null
        var meta: Meta? = null
        synchronized(lock) {
            val rec = recorder ?: return
            val file = currentFile
            val started = startedAt
            val usedSource = activeAudioSource
            val speakerForced = speakerphoneForced
            recorder = null
            currentFile = null
            startedAt = 0
            try {
                rec.stop()
            } catch (t: Throwable) {
                LogCat.e("CallRecorder stop failed: ${t.message}")
            } finally {
                runCatching { rec.release() }
            }
            // Always restore audio manager state — even if file write failed.
            restoreLoudspeakerCapture(MainApp.instance)
            activeAudioSource = ""

            if (file != null && file.exists() && file.length() > 1024) {
                val ended = System.currentTimeMillis()
                val m = Meta(
                    id = UUID.randomUUID().toString(),
                    filename = file.name,
                    displayName = currentDisplayName,
                    source = currentSource,
                    direction = currentDirection,
                    appId = currentAppId,
                    appName = currentAppName,
                    startedAt = started,
                    endedAt = ended,
                    durationMs = (ended - started).coerceAtLeast(0),
                    sizeBytes = file.length(),
                    audioSource = usedSource.ifEmpty { "MIC" },
                    speakerphoneForced = speakerForced,
                )
                runCatching {
                    File(file.parentFile, file.nameWithoutExtension + ".json")
                        .writeText(JsonHelper.jsonEncode(m))
                }
                savedFile = file
                meta = m
            } else {
                // Recording was too short / never wrote — drop the file.
                file?.delete()
            }
            currentDisplayName = ""
            currentSource = ""
            currentDirection = ""
            currentAppId = ""
            currentAppName = ""
        }
        if (savedFile != null) {
            LogCat.d("CallRecorder finished → ${savedFile?.absolutePath} (${meta?.durationMs}ms src=${meta?.audioSource} sp=${meta?.speakerphoneForced})")
            publishRecordingsChanged()
            val f = savedFile
            val m = meta
            if (f != null && m != null) {
                try { com.ismartcoding.plain.telegram.TelegramBotManager.forwardCallRecording(f, m) } catch (_: Throwable) {}
            }
        }
        publishState()
    }

    /** Returns all stored recordings, newest first. */
    fun list(): List<Meta> {
        val dir = recordingsDir()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".m4a") } ?: return emptyList()
        return files.mapNotNull { audioFile ->
            val sidecar = File(audioFile.parentFile, audioFile.nameWithoutExtension + ".json")
            val meta = if (sidecar.exists()) {
                runCatching { JsonHelper.jsonDecode<Meta>(sidecar.readText()) }.getOrNull()
            } else null
            meta ?: Meta(
                id = audioFile.name,
                filename = audioFile.name,
                displayName = audioFile.nameWithoutExtension,
                source = "unknown",
                direction = "unknown",
                appId = "",
                appName = "",
                startedAt = audioFile.lastModified(),
                endedAt = audioFile.lastModified(),
                durationMs = 0,
                sizeBytes = audioFile.length(),
            )
        }.sortedByDescending { it.startedAt }
    }

    fun getFileByFilename(filename: String): File? {
        if (filename.contains("..") || filename.contains("/")) return null
        val f = File(recordingsDir(), filename)
        return if (f.exists() && f.isFile) f else null
    }

    fun deleteByFilename(filename: String): Boolean {
        if (filename.contains("..") || filename.contains("/")) return false
        val f = File(recordingsDir(), filename)
        if (!f.exists()) return false
        val ok = f.delete()
        File(f.parentFile, f.nameWithoutExtension + ".json").delete()
        if (ok) publishRecordingsChanged()
        return ok
    }

    fun deleteAll(): Int {
        val dir = recordingsDir()
        val files = dir.listFiles() ?: return 0
        var n = 0
        files.forEach { if (it.delete()) n++ }
        if (n > 0) publishRecordingsChanged()
        return n
    }

    fun snapshotState(): State {
        val items = list()
        val enabled = runCatching {
            kotlinx.coroutines.runBlocking { CallRecorderEnabledPreference.getAsync(MainApp.instance) }
        }.getOrDefault(true)
        return State(
            enabled = enabled,
            recording = recorder != null,
            currentDisplayName = currentDisplayName,
            currentSource = currentSource,
            currentStartedAt = startedAt,
            totalCount = items.size,
            totalSize = items.sumOf { it.sizeBytes },
            lastError = lastError,
            activeAudioSource = activeAudioSource,
            speakerphoneForced = speakerphoneForced,
        )
    }

    private fun publishState() {
        try {
            sendEvent(WebSocketEvent(EventType.CALL_RECORDER_STATE, JsonHelper.jsonEncode(snapshotState())))
        } catch (_: Throwable) {}
    }

    private fun publishRecordingsChanged() {
        try {
            sendEvent(WebSocketEvent(EventType.CALL_RECORDINGS_CHANGED, ""))
        } catch (_: Throwable) {}
    }
}
