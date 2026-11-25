package de.meinhelfer.tag.lab

import android.os.Bundle
import android.view.WindowManager
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
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { MaterialTheme { LabHome(LabFixtures(this@LabActivity), LabDatabase(this@LabActivity)) } }
    }
}

@Composable
private fun LabHome(fixtures: LabFixtures, database: LabDatabase) {
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
            Button(onClick = { runTest(database::prepare) }, enabled = !busy) { Text("Testdatenbanken A/B vorbereiten") }
            OutlinedButton(onClick = { runTest(database::verify) }, enabled = !busy) { Text("Aktive Testdatenbank prüfen") }
            Text(result)
            listOf("Datensicherung und Wiederherstellung",
                "Prozessabbruch simulieren", "Testprotokolle", "Systemdiagnose"
            ).forEach { name ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        Text("Noch nicht verfügbar")
                    }
                }
            }
            Text("Paket: de.meinhelfer.tag.lab")
        }
    }
}
