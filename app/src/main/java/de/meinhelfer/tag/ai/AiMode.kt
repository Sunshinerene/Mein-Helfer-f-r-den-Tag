package de.meinhelfer.tag.ai

import android.content.Context

/**
 * Was Sunny mit KI darf – nach Sicherheitsstufe. Personenbezogene Angaben (Namen, Adressen, Telefon, Geburtsdaten,
 * Versicherten- und Kontonummern) werden in jedem Modus vor dem Senden entfernt.
 */
enum class AiMode(val key: Int, val title: String, val level: String, val short: String, val risks: List<String>) {
    Off(
        0, "Ohne KI", "Stufe 1 · höchster Schutz",
        "Nichts geht an KI-Dienste. Alle KI-Knöpfe sind aus, Sunny arbeitet nur mit Regeln auf dem Handy.",
        listOf(
            "Keine Zusammenfassungen, Medikamenten-Hinweise oder Auswertungen per KI.",
            "Andere Internet-Funktionen (Updates, Nachrichten, Wetter, Beträge prüfen) haben eigene Schalter.",
        ),
    ),
    Confirm(
        1, "KI mit Bestätigung", "Stufe 2 · du entscheidest jedes Mal",
        "Vor jedem Senden zeigt Sunny genau den Text, der rausgeht (ohne Namen, Adressen, Telefon, Geburtsdaten), und du bestätigst.",
        listOf(
            "Gesundheitsangaben wie Medikamente oder Werte gehen an den KI-Dienst – ohne Namen, aber es sind deine Daten.",
            "Es gilt der Datenschutz des Dienstes; kostenlose Dienste können Eingaben zur Verbesserung nutzen.",
            "KI-Antworten können falsch sein: nur ein Anhaltspunkt, keine ärztliche Empfehlung.",
        ),
    ),
    Auto(
        2, "KI vollumfänglich", "Stufe 3 · ohne Nachfrage",
        "Sunny nutzt KI ohne zu fragen. Vor jedem Senden entfernt sie automatisch Namen, Adressen, Telefonnummern, E-Mails, Geburtsdaten, Versicherten- und Kontonummern.",
        listOf(
            "Du siehst nicht mehr vorher, was gesendet wird. Gesundheitsangaben ohne Namen gehen weiterhin raus.",
            "Die automatische Entfernung kann etwas übersehen, z. B. einen Namen in ungewöhnlicher Schreibweise oder in einer Notiz.",
            "Bei bezahlten Diensten können Kosten entstehen; bei kostenlosen ist das Tageskontingent schneller aufgebraucht.",
            "Es gilt der Datenschutz des Dienstes. Am sichersten ist ein eigener KI-Server im Heimnetz.",
            "KI-Antworten bleiben ein Anhaltspunkt und ersetzen weder Arzt noch Apotheke; gesendet oder gespeichert wird nichts ohne dich.",
        ),
    ),
    ;

    companion object {
        private fun prefs(context: Context) = context.getSharedPreferences("app_einstellungen_ki_modus", Context.MODE_PRIVATE)

        fun of(context: Context): AiMode = prefs(context).getInt("modus", 1).let { k -> entries.firstOrNull { it.key == k } ?: Confirm }

        fun set(context: Context, mode: AiMode) {
            prefs(context).edit().putInt("modus", mode.key).apply()
        }

        fun isOff(context: Context) = of(context) == Off

        /** Ohne Nachfrage senden? */
        fun skipConfirm(context: Context) = of(context) == Auto

        const val OFF_TEXT = "KI ist aus (Modus „Ohne KI“). Ändern unter Einstellungen → Persönlicher Assistent → „KI-Modus“."
    }
}
