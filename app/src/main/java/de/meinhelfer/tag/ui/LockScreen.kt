package de.meinhelfer.tag.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Undurchsichtiger Sperrbildschirm ueber der App. Der Inhalt dahinter bleibt verdeckt. */
@Composable
fun LockScreen(dark: Boolean, onUnlock: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(if (dark) Color(0xFF121218) else Color(0xFFF7F5FA))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppLogoFull(220.dp)
            Text(
                "Die App ist gesperrt. Entsperre mit Fingerabdruck, Gesicht oder der PIN deines Handys.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Button(shape = buttonShape(), onClick = onUnlock) { Text("Entsperren") }
        }
    }
}
