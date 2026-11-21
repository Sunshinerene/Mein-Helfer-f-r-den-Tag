package de.meinhelfer.tag.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SWATCHES: List<Long> = listOf(
    0xFFC75A00, 0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF5E35B1, 0xFF3B5BDB, 0xFF1E88E5, 0xFF00ACC1,
    0xFF00897B, 0xFF2F6F4F, 0xFF7CB342, 0xFFF9A825, 0xFF6D4C41, 0xFF546E7A, 0xFF111111, 0xFFFFFFFF,
)

private fun parseHex(text: String): Long? {
    val t = text.trim().removePrefix("#")
    if (t.length != 6) return null
    return t.toLongOrNull(16)?.let { 0xFF000000 or it }
}

private fun hexOf(value: Long?): String = value?.let { "#%06X".format(it and 0xFFFFFF) }.orEmpty()

/** Eigene Farben fuer das gewaehlte Design. Aenderungen wirken sofort, "Zuruecksetzen" stellt das Standarddesign her. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorTweakDialog(style: DesignStyle, tweak: DesignTweak, onChange: (DesignTweak) -> Unit, onClose: () -> Unit) {
    val labels = if (style.hasBackdrop()) {
        listOf("Hauptfarbe (Knöpfe, Auswahl)", "Hintergrund oben links", "Hintergrund unten rechts")
    } else {
        listOf("Hauptfarbe (Knöpfe, Auswahl)", "Zweitfarbe (Flächen)", "Hintergrund")
    }
    val values = listOf(tweak.a, tweak.b, tweak.c)

    fun update(i: Int, v: Long?) = onChange(
        when (i) {
            0 -> tweak.copy(a = v)
            1 -> tweak.copy(b = v)
            else -> tweak.copy(c = v)
        },
    )

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Farben: ${style.label}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Tippe eine Farbe an, die Änderung siehst du sofort. Für jede Farbe kannst du auch einen eigenen Wert eingeben (#RRGGBB). Hell und Dunkel nutzen dieselben Farben.",
                    style = MaterialTheme.typography.bodySmall,
                )
                labels.forEachIndexed { i, label ->
                    var hex by remember(values[i]) { mutableStateOf(hexOf(values[i])) }
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SWATCHES.forEach { color ->
                            val selected = values[i] == color
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .background(Color(color), CircleShape)
                                    .border(
                                        if (selected) 3.dp else 1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        CircleShape,
                                    )
                                    .clickable { update(i, color) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = hex,
                            onValueChange = {
                                hex = it
                                parseHex(it)?.let { v -> update(i, v) }
                            },
                            label = { Text("#RRGGBB") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { update(i, null) }, enabled = values[i] != null) { Text("Standard") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Fertig") } },
        dismissButton = { TextButton(onClick = { onChange(DesignTweak()) }, enabled = !tweak.isEmpty) { Text("Alles zurücksetzen") } },
    )
}
