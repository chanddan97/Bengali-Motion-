package com.example.feature.audio

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.core.model.AudioAsset
import com.example.core.model.Project
import com.example.feature.subtitles.SrtEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AudioStepPanel(
    project: Project,
    audioEngine: StudioAudioEngine,
    onImportAudioUri: (Uri) -> Unit,
    onAudioAssetUpdated: (AudioAsset?) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isRecording by remember { mutableStateOf(false) }
    var recordingElapsedMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackPosMs by remember { mutableLongStateOf(0L) }
    var permissionDeniedMessage by remember { mutableStateOf<String?>(null) }

    val startVoiceRecording: () -> Unit = {
        audioEngine.stopPlayback()
        isPlaying = false
        val started = audioEngine.startRecording(project.id)
        started.onSuccess {
            permissionDeniedMessage = null
            isRecording = true
            recordingElapsedMs = 0L
        }.onFailure { err ->
            onShowMessage("Could not start recording: ${err.message}")
        }
    }

    // Request RECORD_AUDIO ONLY when user explicitly taps Record
    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startVoiceRecording()
        } else {
            permissionDeniedMessage =
                "Microphone permission denied. Grant Microphone access in Android Settings or import an existing audio file."
        }
    }

    val importAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            onImportAudioUri(uri)
        }
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(200L)
            recordingElapsedMs += 200L
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(150L)
            playbackPosMs = audioEngine.currentPlaybackPositionMs()
            if (!audioEngine.isPlaying()) {
                isPlaying = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            audioEngine.stopPlayback()
            if (isRecording) audioEngine.cancelRecording()
        }
    }

    val audio = project.audioAsset

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Import & Record Controls
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Voice & Audio Track (Offline Local)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Import an audio file from your phone (copied safely into local project storage) or record narration directly. Manual SRT works 100% offline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { importAudioLauncher.launch(arrayOf("audio/*")) },
                        enabled = !isRecording,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("import_audio_button"),
                    ) {
                        Icon(Icons.Outlined.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import Audio")
                    }

                    Button(
                        onClick = {
                            if (isRecording) {
                                scope.launch {
                                    isRecording = false
                                    audioEngine.stopRecordingAndBuildAsset()
                                        .onSuccess { recorded ->
                                            onAudioAssetUpdated(recorded)
                                            onShowMessage("Voice recorded (${SrtEngine.formatCompactDuration(recorded.durationMs)})")
                                        }
                                        .onFailure { err ->
                                            onShowMessage("Recording error: ${err.message}")
                                        }
                                }
                            } else {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    startVoiceRecording()
                                } else {
                                    recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            contentColor = if (isRecording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("record_audio_button"),
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Outlined.Stop else Icons.Outlined.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRecording) "Stop (${SrtEngine.formatCompactDuration(recordingElapsedMs)})" else "Record Voice",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (!permissionDeniedMessage.isNullOrBlank()) {
                    Text(
                        text = permissionDeniedMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        // Attached Audio Playback, Trim & SRT Sync Card
        if (audio != null) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("attached_audio_card"),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                            )
                            Column {
                                Text(
                                    text = audio.fileName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Duration: ${SrtEngine.formatTimecode(audio.durationMs)} · ${if (audio.isRecorded) "Recorded AAC" else "Imported"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                audioEngine.stopPlayback()
                                isPlaying = false
                                onAudioAssetUpdated(null)
                                onShowMessage("Audio removed from project.")
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("remove_audio_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Remove audio",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Button(
                            onClick = {
                                if (isPlaying) {
                                    audioEngine.stopPlayback()
                                    isPlaying = false
                                } else {
                                    audioEngine.playAudio(
                                        path = audio.localPath,
                                        startPositionMs = audio.trimStartMs,
                                        onCompletion = { isPlaying = false },
                                    ).onSuccess {
                                        isPlaying = true
                                    }.onFailure { err ->
                                        onShowMessage("Playback error: ${err.message}")
                                    }
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("play_audio_button"),
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isPlaying) "Stop" else "Play")
                        }

                        Text(
                            text = "${SrtEngine.formatCompactDuration(playbackPosMs)} / ${SrtEngine.formatCompactDuration(audio.durationMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Trim controls
                    val maxDur = audio.durationMs.coerceAtLeast(1_000L).toFloat()
                    Text(
                        text = "Trim Start: ${SrtEngine.formatTimecode(audio.trimStartMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Slider(
                        value = audio.trimStartMs.toFloat().coerceIn(0f, maxDur),
                        onValueChange = { value ->
                            val newStart = value.toLong().coerceAtMost((audio.effectiveEndMs - 500L).coerceAtLeast(0L))
                            onAudioAssetUpdated(audio.copy(trimStartMs = newStart))
                        },
                        valueRange = 0f..maxDur,
                    )

                    Text(
                        text = "Trim End: ${SrtEngine.formatTimecode(audio.effectiveEndMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Slider(
                        value = audio.effectiveEndMs.toFloat().coerceIn(0f, maxDur),
                        onValueChange = { value ->
                            val newEnd = value.toLong().coerceAtLeast((audio.trimStartMs + 500L).coerceAtMost(audio.durationMs))
                            onAudioAssetUpdated(audio.copy(trimEndMs = newEnd))
                        },
                        valueRange = 0f..maxDur,
                    )

                    // Audio-to-SRT sync offset
                    Text(
                        text = "Audio-to-SRT Sync Offset: ${if (audio.srtSyncOffsetMs >= 0) "+" else ""}${audio.srtSyncOffsetMs} ms",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Slider(
                        value = audio.srtSyncOffsetMs.toFloat().coerceIn(-5_000f, 5_000f),
                        onValueChange = { value ->
                            val snapped = ((value / 100f).toLong()) * 100L
                            onAudioAssetUpdated(audio.copy(srtSyncOffsetMs = snapped))
                        },
                        valueRange = -5_000f..5_000f,
                    )
                }
            }
        }
    }
}
