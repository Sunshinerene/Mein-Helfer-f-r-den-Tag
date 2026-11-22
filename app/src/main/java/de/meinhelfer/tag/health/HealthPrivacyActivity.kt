package de.meinhelfer.tag.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Wird von Health Connect geöffnet („Datenschutzerklärung“ bzw. „Warum braucht die App das?“).
 * Erklärt, welche Werte gelesen werden und was damit passiert.
 */
class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Mein Helfer für den Tag und Health Connect", style = MaterialTheme.typography.titleLarge)
                        HEALTH_PRIVACY_TEXT.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        Button(onClick = { finish() }) { Text("Schließen") }
                    }
                }
            }
        }
    }
}

/** Erklärung zur Nutzung von Health Connect (auch in der Anleitung). */
internal val HEALTH_PRIVACY_TEXT = listOf(
    "Die App liest auf deinen Wunsch Messwerte aus Health Connect: Blutdruck, Puls (Einzelmessungen und Ruhepuls), Gewicht, Blutzucker, Körpertemperatur, Sauerstoffsättigung und Trinkmenge. Andere Daten (z. B. Schritte, Schlaf, Standort) liest sie nicht.",
    "Wozu: Werte von Uhr, Fitnessband, Waage oder Blutdruckgerät müssen nicht abgetippt werden. Sie landen im Bereich „Trinken und Werte“ der betreuten Person, die du beim Übernehmen auswählst.",
    "Was passiert: Gelesen wird nur, wenn du in der App auf „Werte holen“ tippst. Du siehst vorher jeden Wert mit Quelle und wählst aus, was übernommen wird. Die Werte bleiben verschlüsselt auf dem Handy. Die App schreibt nichts in Health Connect und sendet die Werte an niemanden.",
    "Die Rechte kannst du jederzeit in Health Connect zurücknehmen (Android-Einstellungen → Sicherheit und Datenschutz → Datenschutzeinstellungen → Health Connect). Schon übernommene Werte löschst du in der App.",
    "Die App bewertet keine Werte und ersetzt keine ärztliche Beratung. Messwerte von Uhren und Bändern können ungenau sein.",
)
