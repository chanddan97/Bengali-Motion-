package com.example.feature.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.core.model.ExportConfig
import com.example.core.model.ExportState
import com.example.core.model.Project
import com.example.core.render.RenderFrameInput
import com.example.core.render.SceneFrameRenderer
import com.example.core.storage.LocalAssetStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Real on-device MP4 (H.264 + optional AAC) video renderer and muxer.
 * Never renames a placeholder file; renders every frame deterministically through SceneFrameRenderer
 * into MediaCodec (video/avc) and verifies the resulting MP4 with MediaMetadataRetriever.
 */
class Mp4VideoExporter(
    private val context: Context,
    private val renderer: SceneFrameRenderer,
    private val assetStorage: LocalAssetStorage,
) {
    companion object {
        private const val VIDEO_MIME = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val MIN_REQUIRED_FREE_BYTES = 25L * 1024L * 1024L // 25 MB safety floor
        private const val TIMEOUT_US = 10_000L
    }

    suspend fun exportProjectToMp4(
        project: Project,
        config: ExportConfig,
        onProgress: (ExportState.Running) -> Unit,
    ): ExportState = withContext(Dispatchers.Default) {
        val (width, height) = config.outputDimensions()
        val fps = config.fps.coerceIn(24, 30)
        val totalDurationMs = project.totalDurationMs.coerceIn(1_000L, 180_000L)
        val totalFrames = ((totalDurationMs * fps) / 1_000L).toInt().coerceAtLeast(fps)

        // 1. Low-storage pre-check
        val availableBytes = assetStorage.calculateAvailableBytes()
        val estimatedBytes = (totalDurationMs / 1000L) * 800_000L + MIN_REQUIRED_FREE_BYTES
        if (availableBytes in 1L until estimatedBytes) {
            return@withContext ExportState.Failed(
                reason = "Not enough free storage on device. Please free at least ${(estimatedBytes / (1024 * 1024))} MB and retry.",
                isLowStorage = true,
            )
        }

        // 2. Decode scene backgrounds & user watermark image (fail explicitly if user image is corrupt!)
        onProgress(ExportState.Running("Preparing assets...", 0.02f, 0, totalFrames))
        val bgBitmaps = mutableMapOf<String, Bitmap>()
        var userWatermarkBitmap: Bitmap? = null

        val exportDir = File(assetStorage.ensureProjectDirectory(project.id), "exports").apply { mkdirs() }
        val safeName = project.name.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").take(32).ifBlank { "banglamotion" }
        val outputFile = File(exportDir, "${safeName}_${System.currentTimeMillis()}.mp4")

        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var audioExtractor: MediaExtractor? = null

        try {
            if (project.watermarkSettings.user.enabled && project.watermarkSettings.user.kind == "image") {
                val wmPath = project.watermarkSettings.user.assetId
                val decodedWm = SceneFrameRenderer.decodeBitmapSafely(wmPath, maxDimension = 1024)
                if (decodedWm.isFailure || decodedWm.getOrNull() == null) {
                    return@withContext ExportState.Failed(
                        reason = "User watermark image could not be decoded. Re-select a valid PNG/WebP or switch to Text watermark.",
                    )
                }
                userWatermarkBitmap = decodedWm.getOrNull()
            }

            project.scenes.mapNotNull { it.backgroundImagePath }.distinct().forEach { path ->
                SceneFrameRenderer.decodeBitmapSafely(path, maxDimension = maxOf(width, height))
                    .getOrNull()
                    ?.let { bgBitmaps[path] = it }
            }

            // 3. Configure H.264 MediaCodec (COLOR_FormatYUV420Flexible for deterministic PTS on all devices)
            val bitrate = if (width >= 1080 || height >= 1080) 4_500_000 else 2_500_000
            val format = MediaFormat.createVideoFormat(VIDEO_MIME, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            codec = MediaCodec.createEncoderByType(VIDEO_MIME)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Prepare optional AAC audio track if available and compatible
            var audioTrackIndex = -1
            var selectedAudioSourceTrack = -1
            val audioAsset = project.audioAsset
            if (config.includeAudio && audioAsset != null && File(audioAsset.localPath).exists()) {
                val extractor = MediaExtractor()
                val audioReady = runCatching {
                    extractor.setDataSource(audioAsset.localPath)
                    for (t in 0 until extractor.trackCount) {
                        val trackFormat = extractor.getTrackFormat(t)
                        val mime = trackFormat.getString(MediaFormat.KEY_MIME).orEmpty()
                        if (mime.startsWith("audio/mp4a") || mime == MediaFormat.MIMETYPE_AUDIO_AAC) {
                            selectedAudioSourceTrack = t
                            extractor.selectTrack(t)
                            break
                        }
                    }
                    selectedAudioSourceTrack >= 0
                }.getOrDefault(false)
                if (audioReady) {
                    audioExtractor = extractor
                } else {
                    runCatching { extractor.release() }
                }
            }

            var videoTrackIndex = -1
            val bufferInfo = MediaCodec.BufferInfo()
            val argbPixels = IntArray(width * height)
            var inputDone = false
            var outputDone = false
            var frameIndex = 0

            while (!outputDone) {
                ensureActive()

                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        if (frameIndex >= totalFrames) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                (frameIndex.toLong() * 1_000_000L) / fps,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            val timeMs = (frameIndex.toLong() * 1_000L) / fps
                            val frameBitmap = renderer.renderToBitmap(
                                RenderFrameInput(
                                    project = project,
                                    timeMs = timeMs,
                                    width = width,
                                    height = height,
                                    backgroundBitmaps = bgBitmaps,
                                    userWatermarkBitmap = userWatermarkBitmap,
                                ),
                            )
                            frameBitmap.getPixels(argbPixels, 0, width, 0, 0, width, height)
                            frameBitmap.recycle()

                            val inputImage = codec.getInputImage(inputIndex)
                            val sampleSize = if (inputImage != null) {
                                fillYuv420ImageFromArgb(inputImage, argbPixels, width, height)
                                inputImage.close()
                                width * height * 3 / 2
                            } else {
                                val inputBuffer = codec.getInputBuffer(inputIndex)
                                if (inputBuffer != null) {
                                    writeNv12BufferFromArgb(inputBuffer, argbPixels, width, height)
                                } else {
                                    0
                                }
                            }

                            val ptsUs = (frameIndex.toLong() * 1_000_000L) / fps
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, ptsUs, 0)
                            frameIndex++

                            if (frameIndex == 1 || frameIndex % 3 == 0 || frameIndex == totalFrames) {
                                val progress = 0.05f + (frameIndex.toFloat() / totalFrames.toFloat()) * 0.85f
                                onProgress(
                                    ExportState.Running(
                                        phase = "Rendering frames ($frameIndex / $totalFrames)",
                                        progress = progress.coerceIn(0f, 0.92f),
                                        currentFrame = frameIndex,
                                        totalFrames = totalFrames,
                                    ),
                                )
                            }
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            videoTrackIndex = muxer.addTrack(codec.outputFormat)
                            if (audioExtractor != null && selectedAudioSourceTrack >= 0) {
                                audioTrackIndex = muxer.addTrack(audioExtractor.getTrackFormat(selectedAudioSourceTrack))
                            }
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    outputIndex >= 0 -> {
                        val encodedData = codec.getOutputBuffer(outputIndex)
                        if (encodedData != null && bufferInfo.size > 0 && muxerStarted && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            // 4. Mux trimmed AAC audio samples if attached
            var hasMuxedAudio = false
            if (muxerStarted && audioExtractor != null && audioTrackIndex >= 0 && audioAsset != null) {
                onProgress(ExportState.Running("Muxing audio track...", 0.94f, totalFrames, totalFrames))
                val trimStartUs = audioAsset.trimStartMs.coerceAtLeast(0L) * 1_000L
                val maxEndUs = minOf(
                    totalDurationMs * 1_000L,
                    if (audioAsset.effectiveEndMs > audioAsset.trimStartMs) {
                        (audioAsset.effectiveEndMs - audioAsset.trimStartMs) * 1_000L
                    } else {
                        totalDurationMs * 1_000L
                    },
                )
                audioExtractor.seekTo(trimStartUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                val audioBuf = ByteBuffer.allocate(256 * 1024)
                val audioInfo = MediaCodec.BufferInfo()
                while (true) {
                    ensureActive()
                    val sampleSize = audioExtractor.readSampleData(audioBuf, 0)
                    if (sampleSize < 0) break
                    val rawPtsUs = audioExtractor.sampleTime
                    val shiftedPtsUs = rawPtsUs - trimStartUs
                    if (shiftedPtsUs > maxEndUs) break
                    if (shiftedPtsUs >= 0L) {
                        audioInfo.offset = 0
                        audioInfo.size = sampleSize
                        audioInfo.presentationTimeUs = shiftedPtsUs
                        audioInfo.flags = if ((audioExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                            MediaCodec.BUFFER_FLAG_KEY_FRAME
                        } else {
                            0
                        }
                        muxer.writeSampleData(audioTrackIndex, audioBuf, audioInfo)
                        hasMuxedAudio = true
                    }
                    audioExtractor.advance()
                }
            }

            // Release encoder and muxer before verification
            codec.stop()
            codec.release()
            codec = null
            if (muxerStarted) {
                muxer.stop()
            }
            muxer.release()
            muxer = null
            audioExtractor?.release()
            audioExtractor = null

            // 5. Real post-export MP4 verification via MediaMetadataRetriever
            onProgress(ExportState.Running("Verifying exported MP4...", 0.98f, totalFrames, totalFrames))
            val verified = verifyMp4File(outputFile)
            if (verified == null) {
                return@withContext ExportState.Failed("Exported MP4 failed playback metadata verification.")
            }

            ExportState.Success(
                outputPath = outputFile.absolutePath,
                fileSizeBytes = outputFile.length(),
                width = verified.first,
                height = verified.second,
                durationMs = verified.third,
                hasAudioTrack = hasMuxedAudio,
            )
        } catch (cancel: CancellationException) {
            runCatching { outputFile.delete() }
            ExportState.Cancelled
        } catch (t: Throwable) {
            runCatching { outputFile.delete() }
            ExportState.Failed(reason = t.message ?: "Device media encoder failed during MP4 export.")
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { audioExtractor?.release() }
            bgBitmaps.values.forEach { if (!it.isRecycled) it.recycle() }
            userWatermarkBitmap?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    suspend fun saveToDeviceMoviesGallery(sourceFilePath: String, displayName: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val src = File(sourceFilePath)
                require(src.exists() && src.length() > 0L) { "Exported MP4 not found." }
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "$displayName.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/BanglaMotion")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }
                val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create MediaStore video entry.")
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    src.inputStream().use { input -> input.copyTo(out) }
                } ?: error("Failed writing MP4 to Movies gallery.")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                    context.contentResolver.update(uri, done, null, null)
                }
                uri.toString()
            }
        }

    private fun verifyMp4File(file: File): Triple<Int, Int, Long>? {
        if (!file.exists() || file.length() < 1024L) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (hasVideo == "yes" && w > 0 && h > 0 && duration > 0L) {
                Triple(w, h, duration)
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun fillYuv420ImageFromArgb(
        image: android.media.Image,
        argb: IntArray,
        width: Int,
        height: Int,
    ) {
        val planes = image.planes
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        for (j in 0 until height) {
            val yRowOffset = j * yRowStride
            val uvRow = j shr 1
            val uRowOffset = uvRow * uRowStride
            val vRowOffset = uvRow * vRowStride
            for (i in 0 until width) {
                val px = argb[j * width + i]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yBuf.put(yRowOffset + i * yPixelStride, y.coerceIn(0, 255).toByte())

                if ((j and 1) == 0 && (i and 1) == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    val uvCol = i shr 1
                    uBuf.put(uRowOffset + uvCol * uPixelStride, u.coerceIn(0, 255).toByte())
                    vBuf.put(vRowOffset + uvCol * vPixelStride, v.coerceIn(0, 255).toByte())
                }
            }
        }
    }

    private fun writeNv12BufferFromArgb(
        buffer: ByteBuffer,
        argb: IntArray,
        width: Int,
        height: Int,
    ): Int {
        buffer.clear()
        val frameSize = width * height
        val uvOffset = frameSize
        val total = frameSize * 3 / 2
        if (buffer.capacity() < total) return 0

        var yIndex = 0
        var uvIndex = uvOffset
        for (j in 0 until height) {
            for (i in 0 until width) {
                val px = argb[j * width + i]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                val y = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255)
                buffer.put(yIndex++, y.toByte())
                if ((j and 1) == 0 && (i and 1) == 0 && uvIndex + 1 < total) {
                    val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    buffer.put(uvIndex++, u.toByte())
                    buffer.put(uvIndex++, v.toByte())
                }
            }
        }
        return total
    }
}
