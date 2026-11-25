package com.example

import com.example.core.i18n.stringsFor
import com.example.core.model.AppLanguage
import com.example.core.model.AspectRatio
import com.example.core.model.ExportConfig
import com.example.core.model.ExportResolution
import com.example.core.model.Project
import com.example.core.model.SrtIssueSeverity
import com.example.core.model.WatermarkSettings
import com.example.core.render.WatermarkLayoutCalculator
import com.example.core.storage.ProjectNameValidator
import com.example.core.storage.ProjectValidationError
import com.example.feature.subtitles.SrtEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun projectNameValidator_rejectsBlankAndLongNames() {
        assertEquals(ProjectValidationError.EmptyName, ProjectNameValidator.validate("   "))
        assertEquals(
            ProjectValidationError.NameTooLong,
            ProjectNameValidator.validate("ক".repeat(Project.MAX_NAME_LENGTH + 1)),
        )
        assertNull(ProjectValidationError::class.java.cast(ProjectNameValidator.validate("  বাংলা   গল্প ২০২৬  ")))
        assertEquals("বাংলা গল্প ২০২৬", ProjectNameValidator.sanitize("  বাংলা   গল্প ২০২৬  "))
    }

    @Test
    fun defaultWatermarks_areBothDisabledByDefault() {
        val settings = WatermarkSettings()
        assertFalse("User watermark must default to OFF", settings.user.enabled)
        assertFalse("Developer watermark must default to OFF", settings.developer.enabled)
        assertEquals("Made with BanglaMotion", settings.developer.text)
    }

    @Test
    fun srtEngine_roundTripsBengaliMultilineCuesWithoutMojibake() {
        val sampleSrt = """
            1
            00:00:01,000 --> 00:00:03,500
            আজকের গল্পে তোমাকে স্বাগতম।
            দ্বিতীয় লাইন বাংলায়।

            2
            00:00:03,600 --> 00:00:06,250
            চলো, BanglaMotion দিয়ে শুরু করি!
        """.trimIndent()

        val parsed = SrtEngine.parse(sampleSrt)
        assertFalse(parsed.hasErrors)
        assertEquals(2, parsed.cues.size)
        assertEquals(1_000L, parsed.cues[0].startMs)
        assertEquals(3_500L, parsed.cues[0].endMs)
        assertEquals("আজকের গল্পে তোমাকে স্বাগতম।\nদ্বিতীয় লাইন বাংলায়।", parsed.cues[0].text)

        val exported = SrtEngine.exportSrt(parsed.cues)
        val reParsed = SrtEngine.parse(exported)
        assertFalse(reParsed.hasErrors)
        assertEquals(2, reParsed.cues.size)
        assertEquals(parsed.cues[0].text, reParsed.cues[0].text)
        assertEquals(parsed.cues[1].text, reParsed.cues[1].text)
        assertEquals(parsed.cues[1].endMs, reParsed.cues[1].endMs)
    }

    @Test
    fun srtEngine_flagsMalformedTimestampsAndOverlapsWithoutCrashing() {
        val badSrt = """
            1
            00:00:01.000 --> 00:00:03.000
            ডট মিলিসেকেন্ড ভুল ফরম্যাট

            2
            00:00:05,000 --> 00:00:02,000
            শেষ সময় শুরুর আগে

            3
            00:00:01,000 --> 00:00:04,000
            বৈধ বাংলা কিউ ১

            4
            00:00:03,000 --> 00:00:06,000
            ওভারল্যাপিং কিউ ২
        """.trimIndent()

        val parsed = SrtEngine.parse(badSrt)
        assertTrue(parsed.hasErrors)
        assertEquals(2, parsed.errorCount)
        assertEquals(2, parsed.cues.size)
        assertTrue(parsed.issues.any { it.severity == SrtIssueSeverity.WARNING && it.message.contains("overlaps") })
    }

    @Test
    fun srtEngine_splitMergeShiftAndSearchReplaceWorkAccurately() {
        val cues = SrtEngine.generateCuesFromScript("আজকের গল্পে স্বাগতম।\nচলো শুরু করা যাক।")
        assertEquals(2, cues.size)

        val shifted = SrtEngine.shiftAll(cues, 500L)
        assertEquals(500L, shifted[0].startMs)

        val split = SrtEngine.splitCue(shifted, shifted[0].id)
        assertEquals(3, split.size)

        val merged = SrtEngine.mergeWithNext(split, split[0].id)
        assertEquals(2, merged.size)

        val (replaced, count) = SrtEngine.searchAndReplace(merged, "স্বাগতম", "অভিনন্দন")
        assertEquals(1, count)
        assertTrue(replaced[0].text.contains("অভিনন্দন"))
    }

    @Test
    fun watermarkLayoutCalculator_enforcesDisabledAndDetectsOverlap() {
        val bothOff = WatermarkSettings()
        val offEval = WatermarkLayoutCalculator.evaluateOverlap(
            settings = bothOff,
            canvasWidth = 1080f,
            canvasHeight = 1920f,
            userContentSize = 200f to 60f,
            devContentSize = 200f to 60f,
        )
        assertNull(offEval.userBox)
        assertNull(offEval.developerBox)
        assertFalse(offEval.overlaps)

        val overlapping = WatermarkSettings(
            user = WatermarkSettings.defaultUserLayer().copy(enabled = true, text = "MyChannel", position = "top-right"),
            developer = WatermarkSettings.defaultDeveloperLayer().copy(enabled = true, position = "top-right"),
        )
        val overlapEval = WatermarkLayoutCalculator.evaluateOverlap(
            settings = overlapping,
            canvasWidth = 1080f,
            canvasHeight = 1920f,
            userContentSize = 240f to 64f,
            devContentSize = 220f to 54f,
        )
        assertNotNull(overlapEval.userBox)
        assertNotNull(overlapEval.developerBox)
        assertTrue("Both watermarks at top-right must trigger overlap warning", overlapEval.overlaps)

        val fixedPos = WatermarkLayoutCalculator.suggestNonOverlappingDeveloperPosition(overlapping.user.position)
        val fixedEval = WatermarkLayoutCalculator.evaluateOverlap(
            settings = overlapping.copy(developer = overlapping.developer.copy(position = fixedPos)),
            canvasWidth = 1080f,
            canvasHeight = 1920f,
            userContentSize = 240f to 64f,
            devContentSize = 220f to 54f,
        )
        assertFalse("Auto-repositioned developer watermark must not overlap", fixedEval.overlaps)
    }

    @Test
    fun exportConfig_producesEvenH264DimensionsForAllAspectRatios() {
        ExportResolution.entries.forEach { res ->
            AspectRatio.entries.forEach { ratio ->
                val (w, h) = ExportConfig(ratio, res, 30).outputDimensions()
                assertTrue("Width $w must be divisible by 16", w % 16 == 0)
                assertTrue("Height $h must be divisible by 16", h % 16 == 0)
                assertTrue(w >= 320 && h >= 320)
            }
        }
    }

    @Test
    fun srtAutoMatch_generatesVariedMotionPresetsTransitionsAndHighlights() {
        val cues = SrtEngine.generateCuesFromScript(
            "আজকের গল্পে তোমাকে অনেক অনেক স্বাগতম জানাচ্ছি।\nচলো বাংলা-মোশন দিয়ে দুর্দান্ত ভিডিও বানাই।\nশেষ পর্যন্ত ভিডিওটি দেখতে থাকো!",
        )
        val scenes = SrtEngine.autoMatchScenesFromCues(
            cues = cues,
            script = "",
            templateId = "minimal-text",
            varyTemplates = true,
        )
        assertEquals(3, scenes.size)
        assertEquals("SCENE 01", scenes[0].badgeLabel)
        assertTrue(scenes.map { it.motionPreset }.toSet().size >= 2)
        assertTrue(scenes.map { it.transitionIn }.toSet().size >= 2)
        assertTrue(scenes.map { it.templateId }.toSet().size >= 2)
        assertTrue(scenes.any { it.highlightWord.isNotBlank() })
    }

    @Test
    fun localizationStrings_coverBengaliEnglishAndBilingual() {
        val bn = stringsFor(AppLanguage.BENGALI)
        val en = stringsFor(AppLanguage.ENGLISH)
        val bi = stringsFor(AppLanguage.BENGALI_ENGLISH)

        assertTrue(bn.newProjectCta.contains("প্রোজেক্ট"))
        assertEquals("New Project", en.newProjectCta)
        assertTrue(bi.newProjectCta.contains("প্রোজেক্ট") && bi.newProjectCta.contains("New"))
    }
}
