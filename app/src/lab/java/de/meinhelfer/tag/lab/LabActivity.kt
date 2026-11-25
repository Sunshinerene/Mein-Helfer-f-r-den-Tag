package de.meinhelfer.tag.lab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class LabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == "de.meinhelfer.tag.lab")
        val database = LabDatabase(this)
        val abort = LabAbort(this, database)
        setContent { MaterialTheme { LabHome(LabFixtures(this@LabActivity), database, abort, LabRestore(this@LabActivity)) } }
    }
}

@Composable
private fun LabHome(fixtures: LabFixtures, database: LabDatabase, abort: LabAbort, restore: LabRestore) {
    var result by remember { mutableStateOf("Noch kein Test ausgeführt") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun runTest(action: () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                result = withContext(Dispatchers.IO) { runCatching(action).getOrElse { "FEHLER: ${it.message}" } }
            } finally { busy = false }
        }
    }
    LaunchedEffect(Unit) { runTest { if (restore.pending()) restore.evaluate() else abort.evaluate() } }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Sunny Test Lab", style = MaterialTheme.typography.headlineMedium)
            Text("TESTUMGEBUNG – keine echten Pflegedaten", color = MaterialTheme.colorScheme.error)
            Text("Separater App-Start. Produktive Dienste werden hier nicht gestartet.")
            Button(onClick = { runTest(fixtures::create) }, enabled = !busy) { Text("Künstliche Testdaten erzeugen") }
            OutlinedButton(onClick = { runTest(fixtures::verify) }, enabled = !busy) { Text("Testdaten prüfen") }
            Button(onClick = { runTest(database::prepare) }, enabled = !busy && !abort.pending()) { Text("Testdatenbanken A/B vorbereiten") }
            OutlinedButton(onClick = { runTest(database::verify) }, enabled = !busy) { Text("Aktive Testdatenbank prüfen") }
            listOf("A_PREPARED" to "Vor Datenaustausch", "ASIDE" to "Nach Weglegen von A",
                "PLACED" to "Nach Einsetzen von B", "COMMITTED" to "Nach Bestätigung",
                "CLEANED" to "Nach Bereinigung").forEach { (point, label) ->
                Button(onClick = { runTest { abort.startAt(point) } }, enabled = !busy && !abort.pending()) { Text("Abbruch: $label") }
            }
            Text("Die App beendet sich absichtlich. Danach erneut öffnen.")
            OutlinedButton(onClick = { runTest(abort::evaluate) }, enabled = !busy) { Text("Letzten Abbruchtest auswerten") }
            Text(result)
            val context = androidx.compose.ui.platform.LocalContext.current
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sunny Test Lab", abort.report()))
            }, enabled = !busy) { Text("Testergebnisse kopieren") }
            OutlinedButton(onClick = { runTest(abort::report) }, enabled = !busy) { Text("Ergebnisübersicht aller Abbruchtests") }
            listOf("Datensicherung und Wiederherstellung",
                "Systemdiagnose"
            ).forEach { name ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        Text("Noch nicht verfügbar")
                    }
                }
            }
            Text("Echte Wiederherstellung (synthetisch)", style = MaterialTheme.typography.titleLarge)
            restore.points.forEach { point ->
                Button(onClick = { runTest { restore.start(point) } }, enabled = !busy && !restore.pending()) { Text(point) }
            }
            OutlinedButton(onClick = { runTest(restore::evaluate) }, enabled = !busy) { Text("Echte Wiederherstellung auswerten") }
            OutlinedButton(onClick = { runTest(restore::report) }, enabled = !busy) { Text("Ergebnisse echte Wiederherstellung") }
            Text("Paket: de.meinhelfer.tag.lab")
        }
    }
}
