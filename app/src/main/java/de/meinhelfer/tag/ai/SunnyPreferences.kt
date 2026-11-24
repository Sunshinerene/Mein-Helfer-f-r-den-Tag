package de.meinhelfer.tag.ai

import android.content.Context

/** Explicitly selected non-medical preferences; never sent to an AI provider. */
object SunnyPreferences {
    enum class Tone { Warm, Direct, Quiet }
    data class Profile(val enabled: Boolean = false, val tone: Tone = Tone.Warm,
                       val encouragement: Boolean = true, val relief: Boolean = true)
    private fun prefs(context: Context) =
        context.getSharedPreferences("sunny_personality", Context.MODE_PRIVATE)

    fun load(context: Context): Profile {
        val p = prefs(context)
        if (!p.getBoolean("enabled", false)) return Profile()
        return Profile(true, runCatching { Tone.valueOf(p.getString("tone", "Warm") ?: "Warm") }
            .getOrDefault(Tone.Warm), p.getBoolean("encouragement", true), p.getBoolean("relief", true))
    }

    fun save(context: Context, profile: Profile) {
        if (!profile.enabled) { clear(context); return }
        prefs(context).edit().putBoolean("enabled", true).putString("tone", profile.tone.name)
            .putBoolean("encouragement", profile.encouragement).putBoolean("relief", profile.relief).apply()
    }

    fun clear(context: Context) { prefs(context).edit().clear().apply() }
}
