package de.meinhelfer.sunny.poc.emotion

import java.util.Locale

@JvmInline
internal value class EmotionId(val number: Int) {
    init {
        require(number > 0)
    }

    val code: String get() = "su" + number.toString().padStart(2, '0')
}

internal enum class EmotionGroup(val label: String) {
    Friendly("Freundlich"),
    Explaining("Erklärend"),
    Warm("Herzlich"),
    Thinking("Nachdenklich"),
    MotivatedReading("Motiviertes Vorlesen"),
    Calm("Ruhig"),
    Active("Aktiv"),
    Talking("Sprechend"),
}

internal data class EmotionFrame(val group: EmotionGroup, val id: EmotionId)

internal class EmotionEngine(additions: Map<EmotionGroup, List<EmotionId>> = emptyMap()) {
    private val variants = EmotionGroup.entries.associateWith { group ->
        initial.getValue(group).map(::EmotionId) + additions[group].orEmpty()
    }
    private val ids = variants.values.flatten().toSet()

    init {
        val extra = additions.values.flatten()
        require(extra.all { it.number > 35 } && extra.size == extra.toSet().size) {
            "Additions require distinct emotion IDs from su36 onwards"
        }
    }

    fun contains(id: EmotionId): Boolean = id in ids

    fun select(group: EmotionGroup, variant: Int = 0): EmotionFrame {
        val groupVariants = variants.getValue(group)
        return EmotionFrame(group, groupVariants[Math.floorMod(variant, groupVariants.size)])
    }

    fun displayed(idle: EmotionFrame, speaking: Boolean): EmotionFrame =
        if (speaking) select(EmotionGroup.Talking) else idle

    private companion object {
        // Reference: MeinHelfer's SunnyEmotionMedia.kt at the accepted S1 baseline.
        val initial = mapOf(
            EmotionGroup.Friendly to listOf(5, 8, 15, 23, 28),
            EmotionGroup.Explaining to listOf(1, 2, 7, 11, 12, 22),
            EmotionGroup.Warm to listOf(6, 9, 19, 20),
            EmotionGroup.Thinking to listOf(17, 24, 25, 26),
            EmotionGroup.MotivatedReading to listOf(10, 33, 34),
            EmotionGroup.Calm to listOf(14, 18, 21, 27, 29),
            EmotionGroup.Active to listOf(3, 4, 13, 16, 35),
            EmotionGroup.Talking to listOf(30, 31, 32),
        )
    }
}

internal enum class PresentationMode(val label: String) {
    Image("Bild"), Video("Video"), Auto("Automatik"),
}

internal data class MediaBinding(val filename: String, val resourceId: Int)

/** A missing still renders the neutral placeholder. A video is an optional, muted layer over it. */
internal data class MediaChoice(val still: Int?, val video: Int? = null)

internal class EmotionMediaCatalog(engine: EmotionEngine, bindings: List<MediaBinding>) {
    private enum class Kind { Image, Video }

    private val resources = bindings.mapNotNull { binding ->
        if (binding.resourceId <= 0) return@mapNotNull null
        val match = filename.matchEntire(binding.filename.lowercase(Locale.ROOT)) ?: return@mapNotNull null
        val number = match.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        val id = EmotionId(number)
        if (match.groupValues[1] != id.code.drop(2) || !engine.contains(id)) return@mapNotNull null
        val kind = if (match.groupValues[2] == "mp4") Kind.Video else Kind.Image
        (id to kind) to binding.resourceId
    }.groupBy({ it.first }, { it.second })
        .mapNotNull { (slot, entries) -> entries.singleOrNull()?.let { slot to it } }
        .toMap()

    fun resolve(id: EmotionId, mode: PresentationMode, motionAllowed: Boolean): MediaChoice {
        val video = when (mode) {
            PresentationMode.Image -> null
            PresentationMode.Video -> resources[id to Kind.Video]
            PresentationMode.Auto -> if (motionAllowed) resources[id to Kind.Video] else null
        }
        return MediaChoice(resources[id to Kind.Image], video)
    }

    private companion object {
        val filename = Regex("su([0-9]{2,10})\\.(webp|png|jpg|jpeg|mp4)")
    }
}
