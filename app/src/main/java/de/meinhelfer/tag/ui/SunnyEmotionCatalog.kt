package de.meinhelfer.tag.ui

import java.util.Locale

/** Stable media identity, independent of display text and file format. */
object SunnyEmotionCatalog {
    val initialIds = (1..35).map { id(it) }
    fun id(number: Int): String {
        require(number > 0)
        return "su" + number.toString().padStart(2, '0')
    }

    private val filenamePattern = Regex("^su([0-9]+)(?:[ _-].*)?$", RegexOption.IGNORE_CASE)
    private val imageExtensions = setOf("webp", "png", "jpg", "jpeg")
    private val videoExtensions = setOf("mp4")

    data class Media(val emotionId: String, val extension: String, val video: Boolean) {
        val canonicalName: String get() = "$emotionId.$extension"
    }

    fun parse(filename: String): Media? {
        val extension = filename.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension !in imageExtensions && extension !in videoExtensions) return null
        val stem = filename.substringBeforeLast('.')
        val number = filenamePattern.matchEntire(stem)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        if (number <= 0) return null
        return Media(id(number), extension, extension in videoExtensions)
    }
}
