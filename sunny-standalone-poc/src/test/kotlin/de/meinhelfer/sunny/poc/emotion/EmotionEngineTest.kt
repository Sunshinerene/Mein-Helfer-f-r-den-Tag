package de.meinhelfer.sunny.poc.emotion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class EmotionEngineTest {
    private val engine = EmotionEngine()

    @Test
    fun allEightGroupsMatchReferenceIdsAndOrder() {
        val reference = mapOf(
            EmotionGroup.Friendly to listOf(5, 8, 15, 23, 28),
            EmotionGroup.Explaining to listOf(1, 2, 7, 11, 12, 22),
            EmotionGroup.Warm to listOf(6, 9, 19, 20),
            EmotionGroup.Thinking to listOf(17, 24, 25, 26),
            EmotionGroup.MotivatedReading to listOf(10, 33, 34),
            EmotionGroup.Calm to listOf(14, 18, 21, 27, 29),
            EmotionGroup.Active to listOf(3, 4, 13, 16, 35),
            EmotionGroup.Talking to listOf(30, 31, 32),
        )
        assertEquals(reference.keys, EmotionGroup.entries.toSet())
        val selected = reference.flatMap { (group, ids) ->
            ids.mapIndexed { index, expected ->
                val actual = engine.select(group, index)
                assertEquals(group, actual.group)
                assertEquals(expected, actual.id.number)
                assertEquals(String.format(Locale.ROOT, "su%02d", expected), actual.id.code)
                actual.id.number
            }
        }
        assertEquals((1..35).toList(), selected.sorted())
    }

    @Test
    fun repeatedSelectionCyclesPredictably() {
        val group = EmotionGroup.Thinking
        assertEquals(
            listOf(17, 24, 25, 26, 17, 24, 25, 26, 17),
            (0..8).map { engine.select(group, it).id.number },
        )
        assertEquals(engine.select(group, 0), engine.select(group, 0))
        assertEquals(26, engine.select(group, -1).id.number)
    }

    @Test
    fun additionsAppendWithoutRenumberingAndCanBindMedia() {
        val group = EmotionGroup.Talking
        val extended = EmotionEngine(mapOf(group to listOf(EmotionId(36), EmotionId(100))))
        val catalog = EmotionMediaCatalog(extended, listOf(MediaBinding("su36.webp", 1)))
        assertEquals(listOf("su30", "su31", "su32", "su36", "su100"), (0..4).map { extended.select(group, it).id.code })
        assertEquals(extended.select(group), extended.select(group, 5))
        assertEquals(MediaChoice(1), catalog.resolve(EmotionId(36), PresentationMode.Image, true))
        assertTrue(extended.contains(EmotionId(36)))
        assertFalse(engine.contains(EmotionId(36)))
    }

    @Test
    fun additionsRejectReservedAndDuplicateIds() {
        assertThrows(IllegalArgumentException::class.java) { EmotionId(0) }
        assertThrows(IllegalArgumentException::class.java) {
            EmotionEngine(mapOf(EmotionGroup.Warm to listOf(EmotionId(35))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            EmotionEngine(
                mapOf(
                    EmotionGroup.Warm to listOf(EmotionId(36)),
                    EmotionGroup.Calm to listOf(EmotionId(36)),
                ),
            )
        }
    }

    @Test
    fun emptyCatalogAlwaysChoosesPlaceholder() {
        val empty = EmotionMediaCatalog(engine, emptyList())
        for (number in 1..35) {
            for (mode in PresentationMode.entries) {
                assertEquals(MediaChoice(null), empty.resolve(EmotionId(number), mode, true))
            }
        }
    }

    @Test
    fun imageModeAndReducedMotionAutoUseStill() {
        val catalog = EmotionMediaCatalog(engine, listOf(MediaBinding("SU05.WebP", 10), MediaBinding("su05.mp4", 11)))
        val id = EmotionId(5)
        assertEquals(MediaChoice(10), catalog.resolve(id, PresentationMode.Image, true))
        assertEquals(MediaChoice(10), catalog.resolve(id, PresentationMode.Auto, false))
        assertEquals(MediaChoice(10, 11), catalog.resolve(id, PresentationMode.Auto, true))
        assertEquals(MediaChoice(10, 11), catalog.resolve(id, PresentationMode.Video, false))
    }

    @Test
    fun missingVideoFallsBackToSameImageAndMissingImageStaysNeutral() {
        val catalog = EmotionMediaCatalog(engine, listOf(MediaBinding("su05.png", 10), MediaBinding("su08.mp4", 11)))
        assertEquals(MediaChoice(10), catalog.resolve(EmotionId(5), PresentationMode.Video, true))
        assertEquals(MediaChoice(null, 11), catalog.resolve(EmotionId(8), PresentationMode.Video, true))
        assertEquals(MediaChoice(null), catalog.resolve(EmotionId(8), PresentationMode.Image, true))
        assertEquals(MediaChoice(null), catalog.resolve(EmotionId(15), PresentationMode.Auto, true))
    }

    @Test
    fun malformedBindingsFailClosed() {
        val malformed = listOf(
            "../su05.webp", "file:///su05.webp", "https://example.invalid/su05.mp4", "su5.png",
            "su005.webp", "su05.gif", "su05-description.webp", "su00.jpg", "su36.webp", "su2147483648.webp",
        ).map { MediaBinding(it, 42) } + listOf(MediaBinding("su05.webp", 0), MediaBinding("su05.mp4", -1))
        val catalog = EmotionMediaCatalog(engine, malformed)
        for (id in listOf(EmotionId(5), EmotionId(36), EmotionId(Int.MAX_VALUE))) {
            assertEquals(MediaChoice(null), catalog.resolve(id, PresentationMode.Video, true))
        }
    }

    @Test
    fun duplicateBindingsInvalidateOnlyTheAmbiguousSlotInEitherOrder() {
        val bindings = listOf(MediaBinding("su05.png", 10), MediaBinding("su05.webp", 20), MediaBinding("su05.mp4", 30))
        val desired = MediaChoice(null, 30)
        assertEquals(desired, EmotionMediaCatalog(engine, bindings).resolve(EmotionId(5), PresentationMode.Video, true))
        assertEquals(desired, EmotionMediaCatalog(engine, bindings.reversed()).resolve(EmotionId(5), PresentationMode.Video, true))
    }
}
