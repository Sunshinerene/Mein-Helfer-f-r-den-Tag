package de.meinhelfer.tag.ui

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.export.PdfPrintAdapter
import de.meinhelfer.tag.media.DocumentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Teilt eine fertige PDF-Datei (z. B. per Mail, Messenger oder zum Speichern). */
internal fun sharePdfFile(context: Context, file: File, title: String) {
    try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, DocumentStore.uriFor(context, file))
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension.replace('_', ' '))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title))
    } catch (_: Exception) {
        Toast.makeText(context, "Teilen ist nicht möglich.", Toast.LENGTH_SHORT).show()
    }
}

/** Gibt eine fertige PDF-Datei an den Druckdienst des Handys. */
internal fun printPdfFile(context: Context, file: File) {
    try {
        val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        pm.print(file.nameWithoutExtension, PdfPrintAdapter(file, file.name), PrintAttributes.Builder().build())
    } catch (_: Exception) {
        Toast.makeText(context, "Drucken ist nicht möglich.", Toast.LENGTH_SHORT).show()
    }
}

/** Zwei Knoepfe "Als PDF teilen" und "Drucken"; die PDF entsteht erst beim Antippen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfButtons(
    shareLabel: String,
    chooserTitle: String,
    modifier: Modifier = Modifier,
    make: (Context) -> File,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    fun run(action: (File) -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            val f = withContext(Dispatchers.IO) { runCatching { make(context.applicationContext) }.getOrNull() }
            busy = false
            if (f == null) Toast.makeText(context, "PDF konnte nicht erstellt werden.", Toast.LENGTH_SHORT).show() else action(f)
        }
    }
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(shape = buttonShape(), enabled = !busy, onClick = { run { sharePdfFile(context, it, chooserTitle) } }) { Text(shareLabel) }
        OutlinedButton(shape = buttonShape(), enabled = !busy, onClick = { run { printPdfFile(context, it) } }) { Text("Drucken") }
    }
}
