package de.meinhelfer.tag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.update.AppPrefs

/**
 * Karte, die sich einklappen laesst. Eingeklappt bleiben nur Titel und eine kurze Info
 * (z. B. "2 offen"); der Zustand wird gemerkt.
 */
@Composable
internal fun Foldable(
    key: String,
    title: String,
    summary: String,
    colors: CardColors? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var folded by remember(key) { mutableStateOf(AppPrefs.folded(context, key)) }
    fun set(value: Boolean) {
        folded = value
        AppPrefs.setFolded(context, key, value)
    }
    if (folded) {
        HCard(Modifier.fillMaxWidth(), onClick = { set(false) }, colors = colors) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (summary.isNotBlank()) Text(summary, style = MaterialTheme.typography.bodyMedium)
                }
                Text("Aufklappen ▾", style = MaterialTheme.typography.labelMedium)
            }
        }
    } else {
        Column {
            content()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { set(true) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Text("Einklappen ▴", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
