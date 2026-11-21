package de.meinhelfer.tag.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.print.PrintAttributes
import android.print.PrintManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.data.CaregiverProfile
import de.meinhelfer.tag.data.CaregiverRole
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Document
import de.meinhelfer.tag.data.Medication
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.export.MailTemplates
import de.meinhelfer.tag.export.PdfPrintAdapter
import de.meinhelfer.tag.export.RecipientKind
import de.meinhelfer.tag.letters.Letter
import de.meinhelfer.tag.letters.LetterBody
import de.meinhelfer.tag.letters.LetterInput
import de.meinhelfer.tag.letters.LetterPdf
import de.meinhelfer.tag.letters.LetterTemplate
import de.meinhelfer.tag.letters.LetterTemplates
import de.meinhelfer.tag.letters.LetterTo
import de.meinhelfer.tag.media.DocumentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

private const val OTHER_RECIPIENT = "– anderer Empfänger –"

private fun startLetterIntent(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Dafür ist keine passende App installiert.", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Schreiben und Vollmachten fuer den Postweg: Vorlage waehlen, Angaben ergaenzen, Text pruefen,
 * als PDF erzeugen, drucken oder teilen und als Dokument ablegen. Alles auf dem Handy,
 * nichts wird gesendet. Die unterschriebene Fassung scannt man spaeter wie jedes Dokument ein.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LetterScreen(
    person: Person?,
    doctors: List<Doctor>,
    meds: List<Medication>,
    caregiver: CaregiverProfile,
    vm: MainViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var stage by remember { mutableIntStateOf(0) } // 0 Auswahl, 1 Angaben, 2 Text und PDF
    var template by remember { mutableStateOf<LetterTemplate?>(null) }
    val values = remember { mutableStateMapOf<String, String>() }
    var proxy by remember { mutableStateOf(caregiver.isSet && caregiver.roleValue != CaregiverRole.Selbst) }
    var withNumber by remember { mutableStateOf(true) }
    var recipientId by remember { mutableStateOf<Long?>(null) }
    var recipientText by remember { mutableStateOf("") }
    var senderText by remember { mutableStateOf("") }
    var place by remember { mutableStateOf(person?.city.orEmpty()) }

    var subject by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var enclosuresText by remember { mutableStateOf("") }
    var signatures by remember { mutableStateOf<List<String>>(emptyList()) }

    var pdf by remember { mutableStateOf<File?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var working by remember { mutableStateOf(false) }
    var savedToDocs by remember { mutableStateOf(false) }
    var taskAdded by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val file = pdf
        if (uri != null && file != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                Toast.makeText(context, "PDF gespeichert.", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, "Speichern hat nicht geklappt.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler {
        when {
            stage == 2 -> stage = 1
            stage == 1 -> stage = 0
            else -> onBack()
        }
    }

    fun startTemplate(t: LetterTemplate) {
        template = t
        values.clear()
        t.fields.forEach { if (it.default.isNotEmpty()) values[it.key] = it.default }
        if (t.id == "vollmacht" || t.id == "schweigepflicht") {
            values["vertreter"] = caregiver.name
            if (caregiver.address.isNotBlank()) values["vertreterAdresse"] = caregiver.address
        }
        recipientId = null
        recipientText = ""
        withNumber = t.to == LetterTo.Kasse
        val lines = buildList {
            person?.name?.takeIf { it.isNotBlank() }?.let { add(it) }
            person?.street?.takeIf { it.isNotBlank() }?.let { add(it) }
            val zc = listOf(person?.zip.orEmpty(), person?.city.orEmpty()).filter { it.isNotBlank() }.joinToString(" ")
            if (zc.isNotBlank()) add(zc)
            if (proxy && caregiver.name.isNotBlank()) add("Ansprechperson: " + caregiver.name)
            if (proxy && caregiver.address.isNotBlank()) add(caregiver.address)
            if (caregiver.phone.isNotBlank()) add("Tel.: " + caregiver.phone)
        }
        senderText = lines.joinToString("\n")
        pdf = null
        preview = null
        savedToDocs = false
        taskAdded = false
        stage = 1
    }

    fun buildText(t: LetterTemplate): LetterBody {
        val input = LetterInput(person, caregiver, proxy && t.to != LetterTo.Keiner, withNumber, values.toMap(), meds)
        return t.build(input)
    }

    fun currentLetter(t: LetterTemplate): Letter = Letter(
        senderLines = senderText.lines(),
        recipientLines = recipientText.lines(),
        placeDate = if (t.to == LetterTo.Keiner) "" else listOf(place.trim(), LetterTemplates.longDate()).filter { it.isNotEmpty() }.joinToString(", "),
        subject = subject,
        reference = reference,
        body = body,
        signatures = signatures,
        enclosures = enclosuresText.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() },
        form = t.to == LetterTo.Keiner,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schreiben und Vollmachten") },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            stage == 2 -> stage = 1
                            stage == 1 -> stage = 0
                            else -> onBack()
                        }
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val t = template
            when {
                stage == 0 || t == null -> {
                    Text(
                        "Wähle eine Vorlage. Du ergänzt die Angaben, prüfst den Text und bekommst eine PDF zum Ausdrucken. Nach dem Unterschreiben kannst du das Schreiben abschicken und die unterschriebene Fassung wie jedes Dokument einscannen und in der App ablegen.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LetterTemplates.ALL.groupBy { it.group }.forEach { (group, list) ->
                        Text(group, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                        list.forEach { tpl ->
                            Card(
                                Modifier.fillMaxWidth().clickable { startTemplate(tpl) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(tpl.title, style = MaterialTheme.typography.titleSmall)
                                    Text(tpl.description, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    Text(
                        "Vorsorgevollmacht und Patientenverfügung gibt es hier nicht als Vorlage: Dafür gelten besondere Formvorschriften (bei Grundstücken z. B. eine notarielle Beglaubigung). Amtliche Muster und Beratung bieten das Bundesministerium der Justiz, Betreuungsvereine und die Betreuungsbehörde.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Alle Texte sind Entwürfe und ersetzen keine Rechts- oder Pflegeberatung. Bitte vor dem Ausdrucken prüfen und die Stellen „(bitte ergänzen)“ ausfüllen.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                stage == 1 -> {
                    val t = checkNotNull(t)
                    Text(t.title, style = MaterialTheme.typography.titleMedium)
                    t.notice?.let {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                        }
                    }
                    if (person == null) {
                        Text("Lege zuerst eine Person an, dann werden Name und Anschrift eingesetzt.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (t.to != LetterTo.Keiner) {
                        Text("Wer schreibt?", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !proxy, onClick = { proxy = false }, label = { Text("Die Person selbst") })
                            FilterChip(selected = proxy, onClick = { proxy = true }, label = { Text("Ich, mit Vollmacht") })
                        }
                        if (proxy) {
                            Text("Dann wird die Vollmacht als Anlage genannt. Lege sie dem Brief bei (Vorlage „Vollmacht“).", style = MaterialTheme.typography.bodySmall)
                        }
                        if (t.to == LetterTo.Kasse && !person?.insuranceNumber.isNullOrBlank()) {
                            FilterChip(selected = withNumber, onClick = { withNumber = !withNumber }, label = { Text("Versichertennummer angeben") })
                        }

                        val sorted = doctors.sortedBy { d ->
                            val kind = MailTemplates.kindOf(d)
                            val want = when (t.to) {
                                LetterTo.Kasse -> kind == RecipientKind.Pflegekasse
                                LetterTo.Arzt -> kind == RecipientKind.Arzt
                                else -> false
                            }
                            if (want) 0 else 1
                        }
                        if (sorted.isNotEmpty()) {
                            DropdownField(
                                label = "Empfänger aus deinen Kontakten",
                                value = sorted.firstOrNull { it.id == recipientId }?.let { doctorLabel(it) } ?: "",
                                options = listOf(OTHER_RECIPIENT) + sorted.map { doctorLabel(it) },
                                editable = false,
                                onValueChange = { chosen ->
                                    val d = sorted.firstOrNull { doctorLabel(it) == chosen }
                                    recipientId = d?.id
                                    if (d != null) {
                                        recipientText = (listOf(d.name) + d.address.split(",").map { it.trim() }.filter { it.isNotEmpty() }).joinToString("\n")
                                    }
                                },
                            )
                        }
                        OutlinedTextField(
                            value = recipientText,
                            onValueChange = { recipientText = it },
                            label = { Text("Empfänger (Name und Anschrift)") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = senderText,
                            onValueChange = { senderText = it },
                            label = { Text("Absender") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = place,
                            onValueChange = { place = it },
                            label = { Text("Ort (für die Datumszeile)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    t.fields.forEach { f ->
                        OutlinedTextField(
                            value = values[f.key].orEmpty(),
                            onValueChange = { values[f.key] = it },
                            label = { Text(f.label) },
                            placeholder = { Text(f.hint) },
                            minLines = if (f.multiline) 3 else 1,
                            singleLine = !f.multiline,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Button(shape = buttonShape(), onClick = {
                        val built = buildText(t)
                        subject = built.subject
                        reference = built.reference
                        body = built.body
                        enclosuresText = built.enclosures.joinToString(", ")
                        signatures = built.signatures
                        pdf = null
                        preview = null
                        savedToDocs = false
                        taskAdded = false
                        stage = 2
                    }, modifier = Modifier.fillMaxWidth()) { Text("Weiter: Text prüfen") }
                }

                else -> {
                    val t = checkNotNull(t)
                    Text(t.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Prüfe den Text und ändere ihn, wie du möchtest. Stellen mit „(bitte ergänzen)“ musst du noch ausfüllen.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val form = t.to == LetterTo.Keiner
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text(if (form) "Überschrift" else "Betreff") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!form) {
                        OutlinedTextField(
                            value = reference,
                            onValueChange = { reference = it },
                            label = { Text("Zeile unter dem Betreff (z. B. Versichertennummer)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Text") },
                        minLines = 10,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = enclosuresText,
                        onValueChange = { enclosuresText = it },
                        label = { Text("Anlagen (mit Komma getrennt)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Unterschrift: " + signatures.joinToString("; "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(shape = buttonShape(), 
                        enabled = !working && body.isNotBlank(),
                        onClick = {
                            working = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val file = LetterPdf.create(context, currentLetter(t))
                                        file to LetterPdf.preview(file)
                                    }
                                }
                                working = false
                                result.onSuccess { (file, bmp) ->
                                    pdf = file
                                    preview = bmp
                                    savedToDocs = false
                                }.onFailure {
                                    Toast.makeText(context, "Die PDF konnte nicht erstellt werden.", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (working) "Erstelle PDF …" else if (pdf == null) "PDF erstellen" else "PDF neu erstellen") }

                    val file = pdf
                    if (file != null) {
                        preview?.let {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "Vorschau der ersten Seite",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        val fileName = DocumentStore.safeName(t.title + " " + LocalDate.now()) + ".pdf"
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(shape = buttonShape(), onClick = {
                                val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                                pm.print(fileName, PdfPrintAdapter(file, fileName), PrintAttributes.Builder().build())
                            }) { Text("Drucken") }
                            OutlinedButton(shape = buttonShape(), onClick = { saveLauncher.launch(fileName) }) { Text("Speichern unter …") }
                            OutlinedButton(shape = buttonShape(), onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, DocumentStore.uriFor(context, file))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startLetterIntent(context, Intent.createChooser(send, "PDF teilen"))
                            }) { Text("Teilen") }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(shape = buttonShape(), enabled = !savedToDocs, onClick = {
                                scope.launch {
                                    try {
                                        val stored = DocumentStore.store(context, file)
                                        vm.add(
                                            Document(
                                                title = t.title + " (Entwurf, " + LocalDate.now().toString() + ")",
                                                category = t.docCategory,
                                                docDate = LocalDate.now().toEpochDay(),
                                                fileName = stored,
                                                mimeType = "application/pdf",
                                                text = listOf(subject, reference, body).filter { it.isNotBlank() }.joinToString("\n\n"),
                                                notes = "Erstellt in der App zum Ausdrucken. Die unterschriebene Fassung später einscannen und als eigenes Dokument ablegen.",
                                                doctorId = recipientId,
                                            ),
                                        )
                                        savedToDocs = true
                                        Toast.makeText(context, "Unter Dokumente abgelegt.", Toast.LENGTH_SHORT).show()
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Ablegen hat nicht geklappt.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }) { Text(if (savedToDocs) "Abgelegt ✓" else "In Dokumente ablegen") }
                            OutlinedButton(shape = buttonShape(), enabled = !taskAdded, onClick = {
                                vm.addTodo("„" + t.title + "“ ausdrucken, unterschreiben und abschicken; unterschriebene Fassung einscannen")
                                taskAdded = true
                                Toast.makeText(context, "Aufgabe angelegt.", Toast.LENGTH_SHORT).show()
                            }) { Text(if (taskAdded) "Aufgabe angelegt ✓" else "Als Aufgabe merken") }
                        }
                        Text(
                            "Die PDF liegt nur auf deinem Handy. Gesendet wird nichts automatisch. Nach dem Unterschreiben: abschicken, die unterschriebene Fassung im Tab „Dokumente“ einscannen, gern mit der Kategorie dieses Entwurfs.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
