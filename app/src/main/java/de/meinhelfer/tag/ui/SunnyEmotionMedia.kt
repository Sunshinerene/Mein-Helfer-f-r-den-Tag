package de.meinhelfer.tag.ui

import android.content.Context

/** Stable emotion keys, independent of the language model and host app. */
enum class SunnyEmotion(val ids: List<Int>) {
    Friendly(listOf(5,8,15,23,28)),
    Explaining(listOf(1,2,7,11,12,22)),
    Warm(listOf(6,9,19,20)),
    Thinking(listOf(17,24,25,26)),
    MotivatedReading(listOf(10,33,34)),
    Calm(listOf(14,18,21,27,29)),
    Active(listOf(3,4,13,16,35)),
    Talking(listOf(30,31,32))
}

object SunnyEmotionMedia {
    private fun name(id: Int) = "sunny_su" + id.toString().padStart(2, '0')
    private fun resource(context: Context, name: String, type: String): Int =
        context.resources.getIdentifier(name, type, context.packageName)

    fun image(context: Context, emotion: SunnyEmotion, variant: Int = 0): Int {
        val available = emotion.ids.map { resource(context, name(it), "drawable") }.filter { it != 0 }
        if (available.isNotEmpty()) return available[variant.coerceAtLeast(0) % available.size]
        return resource(context, "sunny_beta", "drawable")
            .takeIf { it != 0 } ?: de.meinhelfer.tag.R.drawable.sunny_portrait
    }

    /** Missing videos return zero; the caller then displays the emotion image. */
    fun video(context: Context, emotion: SunnyEmotion, variant: Int = 0): Int {
        val names = if (emotion == SunnyEmotion.Talking)
            listOf("sunny_talk1", "sunny_talk2") + emotion.ids.map(::name)
        else emotion.ids.map(::name)
        val available = names.map { resource(context, it, "raw") }.filter { it != 0 }
        return if (available.isEmpty()) 0 else available[variant.coerceAtLeast(0) % available.size]
    }
}
