package de.meinhelfer.tag.update

import android.content.Context

/** Kleine App-Einstellungen (lokal auf dem Geraet). */
object AppPrefs {
    internal const val FILE = "einstellungen"
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

    /** Schriftgroesse der App als Faktor (1.0 = wie vom Handy eingestellt). */
    fun fontFactor(context: Context): Float = prefs(context).getFloat("schrift_faktor", 1f).coerceIn(0.8f, 1.8f)

    fun setFontFactor(context: Context, factor: Float) {
        prefs(context).edit().putFloat("schrift_faktor", factor).apply()
    }

    /** Eingeklappte Karten auf der Heute-Seite. */
    fun folded(context: Context, key: String): Boolean = prefs(context).getBoolean("eingeklappt_$key", false)

    fun setFolded(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean("eingeklappt_$key", value).apply()
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

    /** Eigene Farben je Design: "a;b;c" als ARGB-Zahlen, leer = Standard. */
    fun designTweak(context: Context, styleKey: String): de.meinhelfer.tag.ui.DesignTweak {
        val parts = (prefs(context).getString("farben_$styleKey", "") ?: "").split(";")
        fun at(i: Int): Long? = parts.getOrNull(i)?.trim()?.toLongOrNull()
        return de.meinhelfer.tag.ui.DesignTweak(at(0), at(1), at(2))
    }

    fun setDesignTweak(context: Context, styleKey: String, tweak: de.meinhelfer.tag.ui.DesignTweak) {
        val text = if (tweak.isEmpty) "" else listOf(tweak.a, tweak.b, tweak.c).joinToString(";") { it?.toString() ?: "" }
        prefs(context).edit().putString("farben_$styleKey", text).apply()
    }

    /** Beta: Notfallkarte auf der Heute-Seite. */
    fun betaEmergency(context: Context): Boolean = prefs(context).getBoolean("beta_notfallkarte", false)

    fun setBetaEmergency(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("beta_notfallkarte", value).apply()
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
    private const val KEY_INFO_FAV_CARDS = "info_favoriten_karten"
    private const val KEY_INFO_FAV_NEWS = "info_favoriten_meldungen"
    private const val KEY_INFO_PERSONAL = "info_vorschlaege"

    /** Eigene Themen der Infoseite (Suchwoerter). */
    /** Als Favorit gemerkte Info-Karten (Titel). */
    fun infoFavoriteCards(context: Context): Set<String> =
        (prefs(context).getString(KEY_INFO_FAV_CARDS, "") ?: "").split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun setInfoFavoriteCards(context: Context, titles: Set<String>) {
        prefs(context).edit().putString(KEY_INFO_FAV_CARDS, titles.joinToString("\n")).apply()
    }

    /** Gemerkte Meldungen: je Zeile Titel, Quelle, Link, Datum (Tab-getrennt). */
    fun infoFavoriteNews(context: Context): List<de.meinhelfer.tag.info.NewsItem> =
        (prefs(context).getString(KEY_INFO_FAV_NEWS, "") ?: "").split("\n").mapNotNull { line ->
            val f = line.split("\t")
            if (f.size < 4 || f[2].isBlank()) null
            else de.meinhelfer.tag.info.NewsItem(f[0], f[1], f[2], f[3].toLongOrNull())
        }

    fun setInfoFavoriteNews(context: Context, items: List<de.meinhelfer.tag.info.NewsItem>) {
        fun clean(t: String) = t.replace('\t', ' ').replace('\n', ' ')
        prefs(context).edit().putString(
            KEY_INFO_FAV_NEWS,
            items.take(100).joinToString("\n") { "${clean(it.title)}\t${clean(it.source)}\t${clean(it.link)}\t${it.published ?: ""}" },
        ).apply()
    }

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

    /** Benachrichtigung, wenn nach dem Zeitfenster noch eine Rueckfrage zur Einnahme offen ist (Standard an). */
    fun followupNotify(context: Context): Boolean = prefs(context).getBoolean("rueckfrage_benachrichtigung", true)

    fun setFollowupNotify(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("rueckfrage_benachrichtigung", value).apply()
    }

    fun setIntakeWindow(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_INTAKE_WINDOW, minutes).apply()
    }

    private const val KEY_CG_ROLE = "pflegende_rolle"
    private const val KEY_CG_NAME = "pflegende_name"
    private const val KEY_CG_REL = "pflegende_beziehung"
    private const val KEY_CG_ORG = "pflegende_organisation"
    private const val KEY_CG_PHONE = "pflegende_telefon"
    private const val KEY_CG_EMAIL = "pflegende_email"
    private const val KEY_CG_ASKED = "pflegende_gefragt"

    /** Eigene Daten der pflegenden Person (nur lokal). */
    fun caregiver(context: Context): de.meinhelfer.tag.data.CaregiverProfile {
        val p = prefs(context)
        return de.meinhelfer.tag.data.CaregiverProfile(
            role = p.getString(KEY_CG_ROLE, "").orEmpty(),
            name = p.getString(KEY_CG_NAME, "").orEmpty(),
            relation = p.getString(KEY_CG_REL, "").orEmpty(),
            organisation = p.getString(KEY_CG_ORG, "").orEmpty(),
            phone = p.getString(KEY_CG_PHONE, "").orEmpty(),
            email = p.getString(KEY_CG_EMAIL, "").orEmpty(),
        )
    }

    fun setCaregiver(context: Context, c: de.meinhelfer.tag.data.CaregiverProfile) {
        prefs(context).edit()
            .putString(KEY_CG_ROLE, c.role)
            .putString(KEY_CG_NAME, c.name.trim())
            .putString(KEY_CG_REL, c.relation.trim())
            .putString(KEY_CG_ORG, c.organisation.trim())
            .putString(KEY_CG_PHONE, c.phone.trim())
            .putString(KEY_CG_EMAIL, c.email.trim())
            .apply()
    }

    fun caregiverAsked(context: Context): Boolean = prefs(context).getBoolean(KEY_CG_ASKED, false)

    fun setCaregiverAsked(context: Context) {
        prefs(context).edit().putBoolean(KEY_CG_ASKED, true).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Zeitpunkt der letzten Sicherung (Millisekunden), 0 = noch nie. */
    fun lastBackup(context: Context): Long = prefs(context).getLong("letzte_sicherung", 0L)

    fun setLastBackup(context: Context, millis: Long) {
        prefs(context).edit().putLong("letzte_sicherung", millis).apply()
    }

    /** Ergebnis der letzten Wiederherstellung: "ok:<millis>" oder "fehler:<millis>", leer = keine. */
    fun restoreResult(context: Context): String = prefs(context).getString("wiederherstellung", "") ?: ""

    fun setRestoreResult(context: Context, value: String) {
        prefs(context).edit().putString("wiederherstellung", value).apply()
    }

    fun autoUpdateCheck(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_UPDATE, true)

    fun setAutoUpdateCheck(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_UPDATE, value).apply()
    }

    fun lastNotifiedVersion(context: Context): String? = prefs(context).getString(KEY_LAST_NOTIFIED, null)

    fun setLastNotifiedVersion(context: Context, version: String) {
        prefs(context).edit().putString(KEY_LAST_NOTIFIED, version).apply()
    }
}
