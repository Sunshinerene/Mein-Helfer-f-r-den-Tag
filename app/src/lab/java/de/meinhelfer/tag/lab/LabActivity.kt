package de.meinhelfer.tag.lab

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class LabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == "de.meinhelfer.tag.lab")
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { MaterialTheme { LabHome() } }
    }
}

@Composable
private fun LabHome() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Sunny Test Lab", style = MaterialTheme.typography.headlineMedium)
            Text("TESTUMGEBUNG – keine echten Pflegedaten", color = MaterialTheme.colorScheme.error)
            Text("Separater App-Start. Produktive Dienste werden hier nicht gestartet.")
            listOf("Testdaten verwalten", "Datensicherung und Wiederherstellung",
                "Prozessabbruch simulieren", "Integritätsprüfung", "Testprotokolle", "Systemdiagnose"
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
