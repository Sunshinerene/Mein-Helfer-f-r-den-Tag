package de.meinhelfer.tag.update

import android.content.Context

/** Kleine App-Einstellungen (lokal auf dem Geraet). */
object AppPrefs {
    private const val FILE = "einstellungen"
    private const val KEY_AUTO_UPDATE = "auto_update_check"
    private const val KEY_LAST_NOTIFIED = "last_notified_version"

    private const val KEY_PERSON = "aktuelle_person"

    /** ID der gerade ausgewaehlten betreuten Person (0 = noch keine Auswahl). */
    fun currentPerson(context: Context): Long = prefs(context).getLong(KEY_PERSON, 0L)

    fun setCurrentPerson(context: Context, id: Long) {
        prefs(context).edit().putLong(KEY_PERSON, id).apply()
    }

    private const val KEY_THEME = "design"

    /** "system", "light" oder "dark" */
    fun themeMode(context: Context): String = prefs(context).getString(KEY_THEME, "system") ?: "system"

    fun setThemeMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_THEME, mode).apply()
    }

    private const val KEY_TOUR_SEEN = "rundgang_gesehen"

    fun tourSeen(context: Context): Boolean = prefs(context).getBoolean(KEY_TOUR_SEEN, false)

    fun setTourSeen(context: Context) {
        prefs(context).edit().putBoolean(KEY_TOUR_SEEN, true).apply()
    }

    private const val KEY_STYLE = "layout"

    /** Schluessel des gewaehlten Designs, siehe DesignStyle. */
    fun designStyle(context: Context): String = prefs(context).getString(KEY_STYLE, "schlicht") ?: "schlicht"

    fun setDesignStyle(context: Context, key: String) {
        prefs(context).edit().putString(KEY_STYLE, key).apply()
    }

    private const val KEY_INFO_AUTO = "beschreibung_automatisch"

    /** true = Medikamenten-Beschreibung wird automatisch geladen, false = nur auf Knopfdruck (Standard). */
    fun medInfoAuto(context: Context): Boolean = prefs(context).getBoolean(KEY_INFO_AUTO, false)

    fun setMedInfoAuto(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_INFO_AUTO, value).apply()
    }

    private const val KEY_LOCK = "app_sperre"
    private const val KEY_LOCK_GRACE = "app_sperre_sekunden"
    private const val KEY_SECURE = "bildschirm_geschuetzt"

    /** App-Sperre (Fingerabdruck, Gesicht oder Handy-PIN) eingeschaltet? */
    fun lockEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_LOCK, false)

    fun setLockEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCK, value).apply()
    }

    /** Nach wie vielen Sekunden im Hintergrund die App wieder gesperrt wird (0 = sofort). */
    fun lockGraceSeconds(context: Context): Int = prefs(context).getInt(KEY_LOCK_GRACE, 60)

    fun setLockGraceSeconds(context: Context, seconds: Int) {
        prefs(context).edit().putInt(KEY_LOCK_GRACE, seconds).apply()
    }

    /** Bildschirmfotos und App-Vorschau (zuletzt benutzte Apps) sperren. */
    fun screenProtected(context: Context): Boolean = prefs(context).getBoolean(KEY_SECURE, false)

    fun setScreenProtected(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SECURE, value).apply()
    }

    private const val KEY_INFO_TOPICS = "info_themen"
    private const val KEY_INFO_PERSONAL = "info_vorschlaege"

    /** Eigene Themen der Infoseite (Suchwoerter). */
    fun infoTopics(context: Context): List<String> =
        (prefs(context).getString(KEY_INFO_TOPICS, "") ?: "").split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    fun setInfoTopics(context: Context, topics: List<String>) {
        prefs(context).edit().putString(KEY_INFO_TOPICS, topics.take(20).joinToString("\n")).apply()
    }

    /** Themen-Vorschlaege aus den eigenen Daten (nur auf dem Handy ausgewertet). */
    fun infoPersonalized(context: Context): Boolean = prefs(context).getBoolean(KEY_INFO_PERSONAL, true)

    fun setInfoPersonalized(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_INFO_PERSONAL, value).apply()
    }

    private const val KEY_INTAKE_WINDOW = "einnahme_zeitfenster"

    /** Minuten vor und nach der Einnahmezeit, in denen normal abgehakt werden kann (Standard 60). */
    fun intakeWindow(context: Context): Int = prefs(context).getInt(KEY_INTAKE_WINDOW, 60)

    fun setIntakeWindow(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_INTAKE_WINDOW, minutes).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun autoUpdateCheck(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_UPDATE, true)

    fun setAutoUpdateCheck(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_UPDATE, value).apply()
    }

    fun lastNotifiedVersion(context: Context): String? = prefs(context).getString(KEY_LAST_NOTIFIED, null)

    fun setLastNotifiedVersion(context: Context, version: String) {
        prefs(context).edit().putString(KEY_LAST_NOTIFIED, version).apply()
    }
}
