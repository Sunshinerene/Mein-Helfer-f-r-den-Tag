package de.meinhelfer.tag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

/** Fragen und Antworten fuer Einsteiger: was ein KI-Dienst und ein Schluessel ist, Kosten, Daten, Schritt fuer Schritt. */
internal val AI_BEGINNER_HELP: List<Pair<String, String>> = listOf(
    "Was ist ein KI-Dienst?" to
        "Ein Computer im Internet, z. B. von Google, der Texte liest und beantwortet. Die App schickt ihm nur auf deinen Wunsch eine Frage (ohne Namen) und zeigt dir die Antwort. Ohne KI funktioniert die App genauso, nur ohne diese Hinweise.",
    "Was ist ein Schlüssel (API-Schlüssel)?" to
        "Eine lange Folge aus Buchstaben und Zahlen, z. B. „AIza…“. Er ist wie ein Ausweis für diese App bei dem Dienst: Der Dienst erkennt daran, dass die Anfrage von dir kommt, und zählt dein kostenloses Kontingent mit. Er ist nicht das Passwort deines Google-Kontos; mit ihm kommt niemand an deine E-Mails oder Fotos.",
    "Was kostet das?" to
        "Bei den Diensten unter „Kostenlos nutzbar“ nichts, aber mit Grenzen, z. B. nur eine bestimmte Zahl an Anfragen pro Minute oder Tag. Eine Kreditkarte ist dafür nicht nötig. Kosten entstehen nur, wenn du selbst beim Anbieter eine Zahlungsart hinterlegst und auf einen bezahlten Tarif wechselst. Die App bucht nie etwas.",
    "Was passiert mit den Daten?" to
        "Bevor etwas gesendet wird, siehst du den genauen Text. Namen, Adressen und Nummern der Person werden nie gesendet. Bei kostenlosen Angeboten dürfen manche Anbieter die Eingaben zur Verbesserung ihrer KI nutzen (Google zum Beispiel im kostenlosen Tarif). Wer das nicht möchte, nimmt einen Dienst mit Servern in der EU, eine eigene KI im Heimnetz oder verzichtet auf die KI.",
    "Schritt für Schritt mit Google Gemini" to
        "1. Oben „Google Gemini“ antippen.\n" +
        "2. „Schlüssel besorgen“ antippen. Es öffnet sich Google AI Studio im Browser.\n" +
        "3. Mit deinem Google-Konto anmelden (das vom Handy) und die Bedingungen bestätigen (Nutzung ab 18 Jahren).\n" +
        "4. „API-Schlüssel erstellen“ bzw. „Create API key“ antippen. Fragt die Seite nach einem Projekt, das vorgeschlagene nehmen oder neu erstellen lassen.\n" +
        "5. Den Schlüssel mit dem Kopieren-Symbol kopieren.\n" +
        "6. Zurück in diese App wechseln, lange in das Feld „API-Schlüssel“ tippen und „Einfügen“ wählen.\n" +
        "7. „Testen“ antippen. Steht dort „Verbindung klappt.“, ist alles fertig.",
    "Wo wird der Schlüssel gespeichert?" to
        "Verschlüsselt nur auf diesem Handy. Er ist nicht in der Sicherung enthalten; nach einer Wiederherstellung oder auf einem neuen Handy trägst du ihn neu ein (oder erstellst einen neuen). Gib ihn nicht weiter. Ist er doch in falsche Hände geraten, lösche ihn beim Anbieter (bei Gemini in AI Studio) und erstelle einen neuen.",
    "Ganz ohne Schlüssel?" to
        "Zwei Wege: „OVHcloud“ unter „Kostenlos nutzbar“ funktioniert auch ohne Schlüssel, allerdings nur mit 2 Anfragen pro Minute. Oder du nutzt bei jedem KI-Hinweis „An KI-App teilen“: Dann geht der vorbereitete Text an eine installierte KI-App wie Gemini oder ChatGPT, und die Antwort steht dort.",
    "Es klappt nicht?" to
        "„Schlüssel ungültig“: Schlüssel noch einmal kopieren und einfügen, ohne Leerzeichen davor oder dahinter. „Limit erreicht“ oder „überlastet“: etwas später erneut versuchen oder einen zweiten Dienst einrichten (z. B. Groq oder Mistral) und bei den KI-Hinweisen wechseln. „Modell nicht gefunden“: „Verfügbare Modelle laden“ antippen und ein anderes Modell wählen. Ohne Internet geht es nicht.",
)

/** Ausklappbare Erklaerung in den KI-Einstellungen. */
@Composable
fun AiBeginnerHelp() {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) { Text(if (open) "Erklärung für Einsteiger ausblenden" else "Neu hier? Was ist ein Schlüssel und wie komme ich dran?") }
    if (open) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AI_BEGINNER_HELP.forEach { (q, a) ->
                Text(q, style = MaterialTheme.typography.titleSmall)
                Text(a, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
