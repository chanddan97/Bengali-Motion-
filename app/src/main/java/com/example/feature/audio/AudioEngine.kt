package com.example.feature.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.example.core.model.AudioAsset
import com.example.core.storage.LocalAssetStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class StudioAudioEngine(
    private val context: Context,
    private val assetStorage: LocalAssetStorage,
) {
    private var mediaRecorder: MediaRecorder? = null
    private var activeRecordingFile: File? = null
    private var mediaPlayer: MediaPlayer? = null

    suspend fun importAudioFromUri(projectId: String, uri: Uri): Result<AudioAsset> =
        withContext(Dispatchers.IO) {
            runCatching {
                val projectDir = assetStorage.ensureProjectDirectory(projectId)
                val audioDir = File(projectDir, "audio").apply { mkdirs() }
                val displayName = resolveDisplayName(uri) ?: "imported_audio.m4a"
                val safeExt = displayName.substringAfterLast('.', "m4a")
                    .replace(Regex("[^a-zA-Z0-9]"), "")
                    .ifBlank { "m4a" }
                val assetId = UUID.randomUUID().toString()
                val targetFile = File(audioDir, "audio_${assetId}.$safeExt")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Cannot open selected audio file. Permission may have been revoked.")

                val durationMs = extractDurationMs(targetFile.absolutePath)
                val mimeType = context.contentResolver.getType(uri) ?: "audio/mp4"

                AudioAsset(
                    id = assetId,
                    fileName = displayName,
                    localPath = targetFile.absolutePath,
                    mimeType = mimeType,
                    durationMs = durationMs,
                    trimStartMs = 0L,
                    trimEndMs = durationMs,
                    srtSyncOffsetMs = 0L,
                    isRecorded = false,
                )
            }
        }

    fun startRecording(projectId: String): Result<File> = runCatching {
        stopRecordingInternal(discard = true)
        val projectDir = assetStorage.ensureProjectDirectory(projectId)
        val audioDir = File(projectDir, "audio").apply { mkdirs() }
        val file = File(audioDir, "voice_${System.currentTimeMillis()}.m4a")

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        mediaRecorder = recorder
        activeRecordingFile = file
        file
    }

    suspend fun stopRecordingAndBuildAsset(): Result<AudioAsset> = withContext(Dispatchers.IO) {
        runCatching {
            val outputFile = activeRecordingFile ?: error("No active recording in progress.")
            stopRecordingInternal(discard = false)
            if (!outputFile.exists() || outputFile.length() == 0L) {
                error("Recording file was empty.")
            }
            val durationMs = extractDurationMs(outputFile.absolutePath)
            AudioAsset(
                id = UUID.randomUUID().toString(),
                fileName = outputFile.name,
                localPath = outputFile.absolutePath,
                mimeType = "audio/mp4",
                durationMs = durationMs,
                trimStartMs = 0L,
                trimEndMs = durationMs,
                srtSyncOffsetMs = 0L,
                isRecorded = true,
            )
        }
    }

    fun cancelRecording() {
        stopRecordingInternal(discard = true)
    }

    fun playAudio(
        path: String,
        startPositionMs: Long = 0L,
        onCompletion: () -> Unit = {},
    ): Result<Unit> = runCatching {
        stopPlayback()
        val file = File(path)
        require(file.exists() && file.length() > 0L) { "Audio file missing on device." }
        val player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            if (startPositionMs > 0L && startPositionMs < duration) {
                seekTo(startPositionMs.toInt())
            }
            setOnCompletionListener { onCompletion() }
            start()
        }
        mediaPlayer = player
    }

    fun currentPlaybackPositionMs(): Long = runCatching {
        mediaPlayer?.currentPosition?.toLong() ?: 0L
    }.getOrDefault(0L)

    fun isPlaying(): Boolean = runCatching {
        mediaPlayer?.isPlaying == true
    }.getOrDefault(false)

    fun seekTo(ms: Long) {
        runCatching {
            mediaPlayer?.seekTo(ms.coerceAtLeast(0L).toInt())
        }
    }

    fun stopPlayback() {
        runCatching {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        }
        mediaPlayer = null
    }

    fun extractDurationMs(filePath: String): Long = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(filePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }.getOrDefault(0L)

    private fun stopRecordingInternal(discard: Boolean) {
        val rec = mediaRecorder
        mediaRecorder = null
        val file = activeRecordingFile
        activeRecordingFile = null
        if (rec != null) {
            runCatching { rec.stop() }
            runCatching { rec.release() }
        }
        if (discard && file != null && file.exists()) {
            file.delete()
        }
    }

    private fun resolveDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()
}
