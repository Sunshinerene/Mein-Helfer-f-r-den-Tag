package de.meinhelfer.tag.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import de.meinhelfer.tag.update.AppPrefs

/**
 * Auf- und Zuklappen der Karten einer Liste (Medikamente, Kontakte, Dokumente).
 * Antippen einer Karte klappt sie auf oder zu; „Alle aufklappen“ bzw. „Alle zuklappen“ gilt fuer die ganze Liste
 * und wird gemerkt. Voreingestellt ist zugeklappt, damit die Liste uebersichtlich bleibt.
 */
@Stable
class CardFoldState internal constructor(private val context: Context, private val prefKey: String) {
    var allOpen by mutableStateOf(AppPrefs.listOpen(context, prefKey))
        private set
    private val overrides = mutableStateMapOf<Any, Boolean>()

    fun isOpen(key: Any): Boolean = overrides[key] ?: allOpen

    fun toggle(key: Any) {
        overrides[key] = !isOpen(key)
    }

    fun setAll(open: Boolean) {
        allOpen = open
        overrides.clear()
        AppPrefs.setListOpen(context, prefKey, open)
    }

    /** Sind gerade (fast) alle offen? Dann bietet die Zeile „Alle zuklappen“ an. */
    fun mostlyOpen(keys: List<Any>): Boolean = keys.isNotEmpty() && keys.count { isOpen(it) } * 2 >= keys.size
}

@Composable
fun rememberCardFoldState(prefKey: String): CardFoldState {
    val context = LocalContext.current
    return remember(prefKey) { CardFoldState(context.applicationContext, prefKey) }
}

/** Zeile ueber der Liste: „Alle aufklappen“ bzw. „Alle zuklappen“. */
@Composable
fun FoldAllRow(state: CardFoldState, keys: List<Any>, modifier: Modifier = Modifier) {
    val open = state.mostlyOpen(keys)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { state.setAll(!open) }) {
            Text(if (open) "Alle zuklappen ▴" else "Alle aufklappen ▾", style = MaterialTheme.typography.labelLarge)
        }
    }
}
