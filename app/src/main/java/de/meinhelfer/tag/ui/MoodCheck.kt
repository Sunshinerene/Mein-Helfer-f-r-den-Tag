package de.meinhelfer.tag.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.meinhelfer.tag.summary.Motivator
import de.meinhelfer.tag.summary.SupportLevel

/** Fuenf Smileys zur Auswahl (5 = sehr gut links, 1 = schlecht rechts). */
@Composable
fun MoodPicker(selected: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val animate = LocalEffects.current.animations
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        for (m in 5 downTo 1) {
            val isSel = selected == m
            val scale by animateFloatAsState(if (isSel && animate) 1.25f else if (isSel) 1.12f else 1f, label = "smiley")
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onSelect(m) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .background(
                            if (isSel) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                            MaterialTheme.shapes.large,
                        )
                        .padding(6.dp),
                ) {
                    Text(Motivator.emoji(m), fontSize = 34.sp)
                }
                Text(
                    Motivator.label(m),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Ganzer Bildschirm: Wie geht es dir gerade? */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoodCheckScreen(
    name: String,
    professional: Boolean,
    onSave: (mood: Int, tags: List<String>, note: String) -> Unit,
    onClose: () -> Unit,
) {
    var mood by remember { mutableStateOf<Int?>(null) }
    val tags = remember { mutableStateListOf<String>() }
    var note by remember { mutableStateOf("") }
    var savedLow by remember { mutableStateOf<Int?>(null) }
    val first = name.trim().split(" ").firstOrNull().orEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        val low = savedLow
        if (low != null) {
            Text("Danke, dass du es festhältst.", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Schwere Momente gehören dazu, und du musst sie nicht allein tragen. Vielleicht hilft dir etwas davon:",
                style = MaterialTheme.typography.bodyLarge,
            )
            SupportBox(if (low <= 1) SupportLevel.Strong else SupportLevel.Relief, professional)
            Button(shape = buttonShape(), onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Weiter") }
            return@Column
        }
        Text(if (first.isNotBlank()) "Wie geht es dir gerade, $first?" else "Wie geht es dir gerade?", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Ein Tipp genügt. Das bleibt verschlüsselt auf deinem Handy und hilft dem Tages-Motivator, passende Vorhaben und Tipps zu finden.",
            style = MaterialTheme.typography.bodyMedium,
        )
        HCard(Modifier.fillMaxWidth()) {
            MoodPicker(mood, { mood = it }, Modifier.padding(vertical = 12.dp, horizontal = 4.dp))
        }
        Text("Was passt dazu? (optional)", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Motivator.TAGS.forEach { t ->
                FilterChip(selected = t in tags, onClick = { if (t in tags) tags.remove(t) else tags.add(t) }, label = { Text(t) })
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(300) },
            label = { Text("Notiz (optional)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Button(
            shape = buttonShape(),
            enabled = mood != null,
            onClick = {
                val m = mood ?: return@Button
                onSave(m, tags.toList(), note)
                if (m <= 2) savedLow = m else onClose()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Speichern") }
        OutlinedButton(shape = buttonShape(), onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Später") }
        Text(
            "Wie oft gefragt wird, stellst du unter Einstellungen → Tages-Motivator ein.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun dial(context: Context, number: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.filter { it.isDigit() || it == '+' })))
    } catch (_: Exception) {
    }
}

/** Hinweise zur Entlastung; bei sehr schlechter Stimmung zusaetzlich die Telefonseelsorge. */
@Composable
fun SupportBox(level: SupportLevel, professional: Boolean) {
    if (level == SupportLevel.None) return
    val context = LocalContext.current
    HCard(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Unterstützung", style = MaterialTheme.typography.titleMedium)
            if (professional) {
                Text("• Sprich mit deinem Team oder deiner Leitung über die Belastung. Das ist kein Versagen, sondern Teil guter Pflege.", style = MaterialTheme.typography.bodyMedium)
                Text("• Nimm deine Pausen bewusst, auch wenn es eng ist.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("• Pflegetelefon des Bundes: Beratung rund um die Pflege, auch in belastenden Situationen. 030 20179131, Mo–Do 9–18 Uhr.", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { dial(context, "03020179131") }) { Text("Pflegetelefon anrufen") }
                Text("• Pflegeberatung bei der Pflegekasse oder im Pflegestützpunkt vor Ort.", style = MaterialTheme.typography.bodyMedium)
                Text("• Entlastung: Entlastungsbetrag für Hilfe im Alltag, Verhinderungspflege für eine Auszeit (siehe Wegweiser).", style = MaterialTheme.typography.bodyMedium)
            }
            if (level == SupportLevel.Strong) {
                Text(
                    "• Wenn es dir sehr schlecht geht, sprich mit jemandem: Telefonseelsorge 0800 111 0 111, 0800 111 0 222 oder 116 123, kostenfrei und rund um die Uhr.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { dial(context, "08001110111") }) { Text("Telefonseelsorge anrufen") }
                Text("• Im Notfall: 112.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
