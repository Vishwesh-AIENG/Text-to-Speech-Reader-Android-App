package com.app.ttsreader.ai

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

// ── Model download state ───────────────────────────────────────────────────────

sealed class GemmaModelState {
    /** Model .bin file not present on device. */
    object NotDownloaded : GemmaModelState()
    /** Download in progress — [progressPercent] is 0–100, or -1 if size unknown. */
    data class Downloading(val progressPercent: Int) : GemmaModelState()
    /** Model file present and ready for inference. */
    object Downloaded : GemmaModelState()
    /** Download or verification failed. */
    data class Error(val message: String) : GemmaModelState()
}

/**
 * Singleton that manages the on-device Gemma model lifecycle:
 * - checks whether the model file is present
 * - triggers a DownloadManager download with live progress
 * - exposes a [StateFlow] the ViewModel can collect
 *
 * The model is stored at `context.filesDir/models/<MODEL_FILENAME>` — internal
 * app storage, no WRITE_EXTERNAL_STORAGE permission required.
 */
object GemmaModelManager {

    const val MODEL_FILENAME = "gemma-1.1-2b-it-cpu-int4.bin"
    /** Approximate size shown to the user before download. */
    const val MODEL_SIZE_MB  = 1340

    // Public Google Cloud Storage URL provided by the MediaPipe team — no auth required.
    private const val MODEL_URL =
        "https://storage.googleapis.com/mediapipe-models/llm_inference/" +
        "gemma-1.1-2b-it-cpu-int4/float16/1/gemma-1.1-2b-it-cpu-int4.bin"

    private val _state = MutableStateFlow<GemmaModelState>(GemmaModelState.NotDownloaded)
    val state: StateFlow<GemmaModelState> = _state.asStateFlow()

    private var activeDownloadId: Long = -1L
    private var completionReceiver: BroadcastReceiver? = null

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Call once on ViewModel init to reflect existing model on disk. */
    fun init(context: Context) {
        if (modelExists(context)) _state.value = GemmaModelState.Downloaded
    }

    fun modelPath(context: Context): String =
        File(modelsDir(context), MODEL_FILENAME).absolutePath

    fun modelExists(context: Context): Boolean {
        val f = File(modelPath(context))
        return f.exists() && f.length() > 100_000_000L   // sanity: >100 MB
    }

    /** Enqueues the model download via DownloadManager. No-op if already downloading/downloaded. */
    fun startDownload(context: Context) {
        if (_state.value is GemmaModelState.Downloading ||
            _state.value is GemmaModelState.Downloaded) return

        modelsDir(context).mkdirs()

        _state.value = GemmaModelState.Downloading(0)

        val destFile = File(modelPath(context))
        // Remove any partial file from a previous failed attempt
        if (destFile.exists()) destFile.delete()

        val request = DownloadManager.Request(Uri.parse(MODEL_URL))
            .setTitle("OmniLingo — Gemma AI Model")
            .setDescription("Downloading on-device AI model (~${MODEL_SIZE_MB} MB)…")
            .setDestinationUri(Uri.fromFile(destFile))
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI or
                DownloadManager.Request.NETWORK_MOBILE
            )

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        activeDownloadId = dm.enqueue(request)

        registerCompletionReceiver(context, dm)
    }

    /**
     * Poll download progress from DownloadManager. Call this periodically
     * (e.g. every second) while state is [GemmaModelState.Downloading].
     */
    fun pollProgress(context: Context) {
        if (activeDownloadId < 0) return
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = dm.query(DownloadManager.Query().setFilterById(activeDownloadId))
            ?: return
        if (cursor.moveToFirst()) {
            val downloaded = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            )
            val total = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            )
            if (total > 0 && _state.value is GemmaModelState.Downloading) {
                _state.value = GemmaModelState.Downloading(((downloaded * 100) / total).toInt())
            }
        }
        cursor.close()
    }

    /** Deletes the model file and resets to [GemmaModelState.NotDownloaded]. */
    fun deleteModel(context: Context) {
        File(modelPath(context)).takeIf { it.exists() }?.delete()
        _state.value = GemmaModelState.NotDownloaded
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun modelsDir(context: Context) = File(context.filesDir, "models")

    private fun registerCompletionReceiver(context: Context, dm: DownloadManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != activeDownloadId) return

                val cursor = dm.query(DownloadManager.Query().setFilterById(id))
                if (cursor != null && cursor.moveToFirst()) {
                    val status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    )
                    _state.value = when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            if (modelExists(ctx)) GemmaModelState.Downloaded
                            else GemmaModelState.Error("File not found after download")
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reason = cursor.getInt(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                            )
                            GemmaModelState.Error("Download failed (code $reason)")
                        }
                        else -> _state.value   // still running — ignore
                    }
                    cursor.close()
                }

                try { ctx.unregisterReceiver(this) } catch (_: Exception) {}
                completionReceiver = null
                activeDownloadId = -1L
            }
        }
        completionReceiver = receiver

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API 33+: must declare RECEIVER_EXPORTED for system broadcasts
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }
}
