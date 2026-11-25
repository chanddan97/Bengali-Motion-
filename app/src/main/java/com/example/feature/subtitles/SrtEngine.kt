package com.example.feature.subtitles

import com.example.core.model.MotionPreset
import com.example.core.model.SceneItem
import com.example.core.model.SrtIssueSeverity
import com.example.core.model.SrtParseResult
import com.example.core.model.SrtValidationIssue
import com.example.core.model.SubtitleCue
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

object SrtEngine {
    private val TIMECODE_LINE_REGEX = Regex(
        """^\s*(\d{2}):(\d{2}):(\d{2}),(\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2}),(\d{3})\s*$""",
    )
    private val SINGLE_TIMECODE_REGEX = Regex("""^\s*(\d{2}):(\d{2}):(\d{2}),(\d{3})\s*$""")

    fun decodeUtf8Safely(bytes: ByteArray): Pair<String, Boolean> {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            val decoded = decoder.decode(ByteBuffer.wrap(bytes)).toString()
            stripBom(decoded) to false
        } catch (_: CharacterCodingException) {
            val fallback = String(bytes, StandardCharsets.ISO_8859_1)
            stripBom(fallback) to true
        }
    }

    fun parse(
        rawText: String,
        mediaDurationMs: Long? = null,
        usedEncodingFallback: Boolean = false,
    ): SrtParseResult {
        val normalized = stripBom(rawText)
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        val issues = mutableListOf<SrtValidationIssue>()
        if (usedEncodingFallback) {
            issues += SrtValidationIssue(
                blockNumber = 0,
                lineNumber = 1,
                severity = SrtIssueSeverity.WARNING,
                message = "File was not strictly valid UTF-8; loaded with fallback encoding. Verify Bengali characters before confirming.",
            )
        }

        if (normalized.isBlank()) {
            return SrtParseResult(
                cues = emptyList(),
                issues = issues,
                totalDurationMs = 0L,
                usedEncodingFallback = usedEncodingFallback,
            )
        }

        val lines = normalized.split("\n")
        val cues = mutableListOf<SubtitleCue>()
        var lineIndex = 0
        var blockNumber = 0

        while (lineIndex < lines.size) {
            while (lineIndex < lines.size && lines[lineIndex].isBlank()) {
                lineIndex++
            }
            if (lineIndex >= lines.size) break

            blockNumber++
            val blockStartLine = lineIndex + 1
            val blockLines = mutableListOf<String>()
            while (lineIndex < lines.size && lines[lineIndex].isNotBlank()) {
                blockLines += lines[lineIndex]
                lineIndex++
            }

            if (blockLines.size < 2) {
                issues += SrtValidationIssue(
                    blockNumber = blockNumber,
                    lineNumber = blockStartLine,
                    severity = SrtIssueSeverity.ERROR,
                    message = "Incomplete SRT block (missing timecode or text).",
                )
                continue
            }

            // Support optional missing cue index line if timecode is on line 0
            val firstLineMatch = TIMECODE_LINE_REGEX.matchEntire(blockLines[0])
            val timeLineIndex: Int
            val timeMatch: MatchResult?
            val parsedIndex: Int

            if (firstLineMatch != null) {
                timeLineIndex = 0
                timeMatch = firstLineMatch
                parsedIndex = cues.size + 1
                issues += SrtValidationIssue(
                    blockNumber = blockNumber,
                    lineNumber = blockStartLine,
                    severity = SrtIssueSeverity.WARNING,
                    message = "Missing cue index number; assigned #$parsedIndex automatically.",
                )
            } else {
                val idxVal = blockLines[0].trim().toIntOrNull()
                if (idxVal == null || idxVal <= 0) {
                    issues += SrtValidationIssue(
                        blockNumber = blockNumber,
                        lineNumber = blockStartLine,
                        severity = SrtIssueSeverity.WARNING,
                        message = "Non-numeric cue index '${blockLines[0].trim()}'; will be renumbered sequentially.",
                    )
                    parsedIndex = cues.size + 1
                } else {
                    parsedIndex = idxVal
                }
                timeLineIndex = 1
                timeMatch = TIMECODE_LINE_REGEX.matchEntire(blockLines[1])
            }

            if (timeMatch == null) {
                issues += SrtValidationIssue(
                    blockNumber = blockNumber,
                    lineNumber = blockStartLine + timeLineIndex,
                    severity = SrtIssueSeverity.ERROR,
                    message = "Invalid timecode format. Expected HH:MM:SS,mmm --> HH:MM:SS,mmm.",
                )
                continue
            }

            val startMs = parseTimeGroups(
                timeMatch.groupValues[1],
                timeMatch.groupValues[2],
                timeMatch.groupValues[3],
                timeMatch.groupValues[4],
            )
            val endMs = parseTimeGroups(
                timeMatch.groupValues[5],
                timeMatch.groupValues[6],
                timeMatch.groupValues[7],
                timeMatch.groupValues[8],
            )

            if (startMs == null || endMs == null) {
                issues += SrtValidationIssue(
                    blockNumber = blockNumber,
                    lineNumber = blockStartLine + timeLineIndex,
                    severity = SrtIssueSeverity.ERROR,
                    message = "Out-of-range minutes or seconds in timecode.",
                )
                continue
            }

            if (endMs <= startMs) {
                issues += SrtValidationIssue(
                    blockNumber = blockNumber,
                    lineNumber = blockStartLine + timeLineIndex,
                    severity = SrtIssueSeverity.ERROR,
                    message = "End time must be strictly greater than start time.",
                )
                continue
            }

            val textLines = blockLines.drop(timeLineIndex + 1)
            val cueText = textLines.joinToString("\n").trimEnd()
            if (cueText.isBlank()) {
                issues += SrtValidationIssue(
                    blockNumber = blockNumber,
                    lineNumber = blockStartLine + timeLineIndex + 1,
                    severity = SrtIssueSeverity.WARNING,
                    message = "Empty subtitle text in block #$blockNumber.",
                )
            }

            cues += SubtitleCue(
                id = UUID.randomUUID().toString(),
                index = parsedIndex,
                startMs = startMs,
                endMs = endMs,
                text = cueText,
            )
        }

        issues += validateCues(cues, mediaDurationMs)
        val totalDuration = cues.maxOfOrNull { it.endMs } ?: 0L
        return SrtParseResult(
            cues = renumber(cues),
            issues = issues,
            totalDurationMs = totalDuration,
            usedEncodingFallback = usedEncodingFallback,
        )
    }

    fun validateCues(
        cues: List<SubtitleCue>,
        mediaDurationMs: Long? = null,
    ): List<SrtValidationIssue> {
        val issues = mutableListOf<SrtValidationIssue>()
        cues.forEachIndexed { idx, cue ->
            val block = idx + 1
            if (cue.startMs < 0L) {
                issues += SrtValidationIssue(block, 0, SrtIssueSeverity.ERROR, "Cue #$block has negative start time.")
            }
            if (cue.endMs <= cue.startMs) {
                issues += SrtValidationIssue(block, 0, SrtIssueSeverity.ERROR, "Cue #$block has zero or negative duration.")
            }
            if (cue.text.isBlank()) {
                issues += SrtValidationIssue(block, 0, SrtIssueSeverity.WARNING, "Cue #$block text is empty.")
            }
            if (idx > 0) {
                val prev = cues[idx - 1]
                if (cue.startMs < prev.endMs) {
                    issues += SrtValidationIssue(
                        block,
                        0,
                        SrtIssueSeverity.WARNING,
                        "Cue #$block overlaps with Cue #${idx} (${formatTimecode(cue.startMs)} < ${formatTimecode(prev.endMs)}).",
                    )
                }
            }
            if (mediaDurationMs != null && mediaDurationMs > 0L && cue.endMs > mediaDurationMs) {
                issues += SrtValidationIssue(
                    block,
                    0,
                    SrtIssueSeverity.WARNING,
                    "Cue #$block ends after audio duration (${formatTimecode(cue.endMs)} > ${formatTimecode(mediaDurationMs)}).",
                )
            }
        }
        return issues
    }

    fun exportSrt(cues: List<SubtitleCue>): String {
        val cleaned = renumber(cues)
        if (cleaned.isEmpty()) return ""
        return buildString {
            cleaned.forEachIndexed { idx, cue ->
                append(idx + 1).append('\n')
                append(formatTimecode(cue.startMs.coerceAtLeast(0L)))
                append(" --> ")
                val safeEnd = maxOf(cue.endMs, cue.startMs + 100L)
                append(formatTimecode(safeEnd)).append('\n')
                append(cue.text.replace("\r\n", "\n").replace("\r", "\n"))
                append("\n\n")
            }
        }.trimEnd() + "\n"
    }

    fun parseSingleTimecode(raw: String): Long? {
        val match = SINGLE_TIMECODE_REGEX.matchEntire(raw) ?: return null
        return parseTimeGroups(
            match.groupValues[1],
            match.groupValues[2],
            match.groupValues[3],
            match.groupValues[4],
        )
    }

    fun formatTimecode(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val hours = safe / 3_600_000L
        val minutes = (safe % 3_600_000L) / 60_000L
        val seconds = (safe % 60_000L) / 1_000L
        val millis = safe % 1_000L
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    fun formatCompactDuration(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val totalSeconds = safe / 1_000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    fun renumber(cues: List<SubtitleCue>): List<SubtitleCue> =
        cues.mapIndexed { idx, cue -> cue.copy(index = idx + 1) }

    fun shiftAll(cues: List<SubtitleCue>, offsetMs: Long): List<SubtitleCue> =
        renumber(
            cues.map { cue ->
                val duration = (cue.endMs - cue.startMs).coerceAtLeast(200L)
                val newStart = (cue.startMs + offsetMs).coerceAtLeast(0L)
                val newEnd = (cue.endMs + offsetMs).coerceAtLeast(newStart + duration)
                cue.copy(startMs = newStart, endMs = newEnd)
            },
        )

    fun splitCue(cues: List<SubtitleCue>, cueId: String): List<SubtitleCue> {
        val idx = cues.indexOfFirst { it.id == cueId }
        if (idx < 0) return cues
        val target = cues[idx]
        val midTime = target.startMs + ((target.endMs - target.startMs) / 2L).coerceAtLeast(250L)
        val safeMid = midTime.coerceAtMost(target.endMs - 100L).coerceAtLeast(target.startMs + 100L)

        val lines = target.text.split("\n")
        val (firstText, secondText) = if (lines.size > 1) {
            val splitLine = lines.size / 2
            lines.take(splitLine).joinToString("\n") to lines.drop(splitLine).joinToString("\n")
        } else {
            val words = target.text.trim().split(Regex("\\s+"))
            if (words.size > 1) {
                val half = words.size / 2
                words.take(half).joinToString(" ") to words.drop(half).joinToString(" ")
            } else {
                target.text to target.text
            }
        }

        val first = target.copy(endMs = safeMid, text = firstText)
        val second = SubtitleCue(
            id = UUID.randomUUID().toString(),
            index = target.index + 1,
            startMs = safeMid,
            endMs = maxOf(target.endMs, safeMid + 500L),
            text = secondText,
        )
        val mutable = cues.toMutableList()
        mutable[idx] = first
        mutable.add(idx + 1, second)
        return renumber(mutable)
    }

    fun mergeWithNext(cues: List<SubtitleCue>, cueId: String): List<SubtitleCue> {
        val idx = cues.indexOfFirst { it.id == cueId }
        if (idx < 0 || idx >= cues.lastIndex) return cues
        val first = cues[idx]
        val second = cues[idx + 1]
        val mergedText = listOf(first.text.trim(), second.text.trim())
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        val merged = first.copy(
            endMs = maxOf(first.endMs, second.endMs),
            text = mergedText,
        )
        val mutable = cues.toMutableList()
        mutable[idx] = merged
        mutable.removeAt(idx + 1)
        return renumber(mutable)
    }

    fun duplicateCue(cues: List<SubtitleCue>, cueId: String): List<SubtitleCue> {
        val idx = cues.indexOfFirst { it.id == cueId }
        if (idx < 0) return cues
        val target = cues[idx]
        val duration = (target.endMs - target.startMs).coerceAtLeast(1_500L)
        val copy = SubtitleCue(
            id = UUID.randomUUID().toString(),
            index = target.index + 1,
            startMs = target.endMs + 100L,
            endMs = target.endMs + 100L + duration,
            text = target.text,
        )
        val mutable = cues.toMutableList()
        mutable.add(idx + 1, copy)
        return renumber(mutable)
    }

    fun searchAndReplace(
        cues: List<SubtitleCue>,
        query: String,
        replacement: String,
    ): Pair<List<SubtitleCue>, Int> {
        if (query.isEmpty()) return cues to 0
        var count = 0
        val updated = cues.map { cue ->
            if (cue.text.contains(query, ignoreCase = false)) {
                val occurrences = cue.text.split(query).size - 1
                count += occurrences
                cue.copy(text = cue.text.replace(query, replacement))
            } else {
                cue
            }
        }
        return renumber(updated) to count
    }

    fun generateCuesFromScript(script: String, defaultCueDurationMs: Long = 3_000L): List<SubtitleCue> {
        if (script.isBlank()) return emptyList()
        val segments = script
            .split(Regex("(?<=[।.?!])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        var cursorMs = 0L
        return segments.mapIndexed { idx, line ->
            val wordCount = line.split(Regex("\\s+")).size
            val durationMs = (wordCount * 420L).coerceIn(1_800L, 6_000L).let {
                if (wordCount <= 4) defaultCueDurationMs else it
            }
            val cue = SubtitleCue(
                id = UUID.randomUUID().toString(),
                index = idx + 1,
                startMs = cursorMs,
                endMs = cursorMs + durationMs,
                text = line,
            )
            cursorMs += durationMs + 120L
            cue
        }
    }

    fun autoMatchScenesFromCues(
        cues: List<SubtitleCue>,
        script: String,
        templateId: String,
    ): List<SceneItem> {
        val sourceCues = if (cues.isNotEmpty()) cues else generateCuesFromScript(script)
        if (sourceCues.isEmpty()) return emptyList()
        return sourceCues.mapIndexed { idx, cue ->
            val lines = cue.text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            val headline = lines.firstOrNull().orEmpty()
            val caption = if (lines.size > 1) lines.drop(1).joinToString(" ") else ""
            SceneItem(
                id = UUID.randomUUID().toString(),
                orderIndex = idx,
                headline = headline,
                caption = caption,
                startMs = cue.startMs,
                endMs = maxOf(cue.endMs, cue.startMs + 800L),
                templateId = templateId,
                motionPreset = if (idx % 2 == 0) MotionPreset.SLIDE_UP else MotionPreset.FADE,
            )
        }
    }

    fun activeCueAt(cues: List<SubtitleCue>, timeMs: Long, syncOffsetMs: Long = 0L): SubtitleCue? {
        val adjusted = timeMs - syncOffsetMs
        return cues.firstOrNull { adjusted in it.startMs..it.endMs }
    }

    private fun parseTimeGroups(hh: String, mm: String, ss: String, ms: String): Long? {
        val h = hh.toLongOrNull() ?: return null
        val m = mm.toLongOrNull() ?: return null
        val s = ss.toLongOrNull() ?: return null
        val millis = ms.toLongOrNull() ?: return null
        if (m !in 0..59 || s !in 0..59 || millis !in 0..999) return null
        return h * 3_600_000L + m * 60_000L + s * 1_000L + millis
    }

    private fun stripBom(input: String): String =
        if (input.startsWith("\uFEFF")) input.substring(1) else input
}
