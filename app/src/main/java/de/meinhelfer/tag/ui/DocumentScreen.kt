package de.meinhelfer.tag.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.meinhelfer.tag.summary.Discharge
import de.meinhelfer.tag.summary.MedPlans
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.meinhelfer.tag.data.Doctor
import de.meinhelfer.tag.data.Document
import de.meinhelfer.tag.letters.SignedCopies
import de.meinhelfer.tag.data.Person
import de.meinhelfer.tag.media.DOC_CATEGORIES
import de.meinhelfer.tag.media.DocKind
import de.meinhelfer.tag.media.DocumentAnalyzer
import de.meinhelfer.tag.media.DocumentReader
import de.meinhelfer.tag.media.DocumentStore
import de.meinhelfer.tag.media.PendingDoc
import de.meinhelfer.tag.media.BatchSource
import de.meinhelfer.tag.media.PickedFile
import de.meinhelfer.tag.summary.BatchFile
import de.meinhelfer.tag.summary.BatchPlan
import de.meinhelfer.tag.summary.DocGrouping
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import de.meinhelfer.tag.update.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DOC_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)
private const val NO_CONTACT = "Keine Zuordnung"

/** Symbol "Dokument" fuer die Navigationsleiste. */
val DocumentIcon: ImageVector = ImageVector.Builder(
    name = "Dokument",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(
    pathData = addPathNodes(
        "M14,2H6c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8l-6,-6zM16,18H8v-2h8v2zM16,14H8v-2h8v2zM13,9V3.5L18.5,9H13z",
    ),
    fill = SolidColor(Color.Black),
).build()

internal fun Document.dateText(): String? = docDate?.let { LocalDate.ofEpochDay(it).format(DOC_DATE) }

/** Dokumente: Berichte, Befunde, Bescheide. Fotografieren, aus der Galerie oder als Datei laden. */
@Composable
fun DocumentScreen(items: List<Document>, doctors: List<Doctor>, person: Person?, vm: MainViewModel, onOpenLetters: () -> Unit = {}) {
    val context = LocalContext.current
    val meds by vm.medications.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var doctorFilter by remember { mutableStateOf<Long?>(null) }
    var yearFilter by remember { mutableStateOf<Int?>(null) }
    var sort by remember { mutableStateOf(DocSort.NEWEST) }
    var showSource by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<PendingDoc?>(null) }
    var working by remember { mutableStateOf(false) }
    var pendingCamera by remember { mutableStateOf<File?>(null) }
    var addingPage by remember { mutableStateOf(false) }
    var appendMode by remember { mutableStateOf(false) }
    var showAddPage by remember { mutableStateOf(false) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var editItem by remember { mutableStateOf<Document?>(null) }
    // Neuer Entwurf = neuer Dialog (sonst behielte er Titel und Vorschläge des vorigen)
    var draftSerial by remember { mutableIntStateOf(0) }
    var progress by remember { mutableStateOf<String?>(null) }
    val assistantOn by vm.assistantOn.collectAsStateWithLifecycle()
    val assistantName by vm.assistantName.collectAsStateWithLifecycle()

    // Sammel-Import: mehrere Dateien oder ein Ordner. Erst alle lesen, dann fragen, was zusammengehört, dann nacheinander speichern.
    var batchDocs by remember { mutableStateOf<List<PendingDoc>>(emptyList()) }
    var batchFiles by remember { mutableStateOf<List<BatchFile>>(emptyList()) }
    var batchPlan by remember { mutableStateOf<BatchPlan?>(null) }
    var batchSkipped by remember { mutableIntStateOf(0) }
    var queue by remember { mutableStateOf<List<List<PendingDoc>>>(emptyList()) }
    var batchTotal by remember { mutableIntStateOf(0) }
    var batchPos by remember { mutableIntStateOf(0) }
    var batchSaved by remember { mutableIntStateOf(0) }
    var batchOpenLater by remember { mutableStateOf<Long?>(null) }

    fun setDraft(next: PendingDoc?) {
        draft = next
        if (next != null) draftSerial++
    }

    fun startDraft(block: suspend () -> PendingDoc?) {
        scope.launch {
            working = true
            val result = runCatching { block() }.getOrNull()
            working = false
            if (result == null) {
                Toast.makeText(context, "Das Dokument konnte nicht gelesen werden.", Toast.LENGTH_SHORT).show()
            } else {
                setDraft(result)
            }
        }
    }

    fun finishBatch() {
        if (batchTotal > 0) {
            Toast.makeText(context, "Fertig: $batchSaved von $batchTotal Dokument(en) gespeichert.", Toast.LENGTH_LONG).show()
        }
        batchTotal = 0
        batchPos = 0
        batchSaved = 0
        batchOpenLater?.let { detailId = it }
        batchOpenLater = null
    }

    // Nächstes Dokument aus der Warteschlange; zusammengehörige Dateien werden vorher zu einem Dokument
    fun nextFromQueue() {
        val q = queue
        if (q.isEmpty()) {
            setDraft(null)
            finishBatch()
            return
        }
        val group = q.first()
        queue = q.drop(1)
        batchPos += 1
        if (group.size == 1) {
            setDraft(group.first())
            return
        }
        scope.launch {
            working = true
            progress = "Seiten werden zu einem Dokument zusammengefügt …"
            val name = DocGrouping.baseTitle(group.first().displayName)
            val merged = runCatching { DocumentReader.merge(context, group, name) }.getOrNull()
            working = false
            progress = null
            if (merged == null) {
                Toast.makeText(context, "Zusammenfügen hat nicht geklappt. Die Dateien kommen einzeln.", Toast.LENGTH_LONG).show()
                queue = group.drop(1).map { listOf(it) } + queue
                batchTotal += group.size - 1
                setDraft(group.first())
            } else {
                setDraft(merged)
            }
        }
    }

    fun cancelBatch() {
        draft?.let { DocumentStore.discard(it) }
        queue.flatten().forEach { DocumentStore.discard(it) }
        queue = emptyList()
        setDraft(null)
        finishBatch()
    }

    // Alle ausgewählten Dateien lesen (Text erkennen), dann den Dialog „einzeln oder zusammen?“ zeigen
    fun startBatch(picked: List<PickedFile>, skipped: Int) {
        val chosen = picked.sortedWith { a, b -> DocGrouping.naturalOrder.compare(a.name, b.name) }.take(DocGrouping.MAX_FILES)
        if (picked.size > chosen.size) {
            Toast.makeText(
                context,
                "Es werden die ersten ${DocGrouping.MAX_FILES} Dateien geladen, den Rest bitte in einem zweiten Durchgang.",
                Toast.LENGTH_LONG,
            ).show()
        }
        scope.launch {
            working = true
            val docs = mutableListOf<PendingDoc>()
            val files = mutableListOf<BatchFile>()
            var failed = 0
            chosen.forEachIndexed { i, p ->
                progress = "Datei ${i + 1} von ${chosen.size}: ${p.name}"
                val doc = runCatching { DocumentReader.fromUri(context, p.uri) }.getOrNull()
                if (doc == null) {
                    failed++
                } else {
                    docs.add(doc)
                    files.add(
                        BatchFile(
                            name = if (p.name == "Dokument") "${doc.displayName}.${doc.ext}" else p.name,
                            mergeable = doc.kind == DocKind.IMAGES || doc.kind == DocKind.PDF,
                            isPhoto = doc.kind == DocKind.IMAGES,
                            time = p.time,
                            text = doc.text,
                        ),
                    )
                }
            }
            working = false
            progress = null
            if (failed > 0) {
                Toast.makeText(context, "$failed Datei(en) konnten nicht gelesen werden.", Toast.LENGTH_LONG).show()
            }
            when (docs.size) {
                0 -> Unit
                1 -> setDraft(docs.first())
                else -> {
                    batchDocs = docs
                    batchFiles = files
                    batchSkipped = skipped
                    batchPlan = DocGrouping.suggest(files)
                }
            }
        }
    }

    fun startFolder(tree: Uri) {
        scope.launch {
            working = true
            progress = "Ordner wird durchgesehen …"
            val listed = runCatching { BatchSource.listFolder(context, tree) }.getOrNull()
            working = false
            progress = null
            when {
                listed == null -> Toast.makeText(context, "Der Ordner konnte nicht gelesen werden.", Toast.LENGTH_LONG).show()
                listed.first.isEmpty() -> Toast.makeText(
                    context,
                    "Im Ordner sind keine passenden Dateien (PDF, Foto, Text, Word).",
                    Toast.LENGTH_LONG,
                ).show()
                else -> startBatch(listed.first, listed.second)
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingCamera
        pendingCamera = null
        if (!ok || file == null) {
            file?.delete()
        } else if (addingPage && draft != null) {
            val current = draft!!
            scope.launch {
                working = true
                draft = runCatching { DocumentReader.appendFiles(context, current, listOf(file)) }.getOrDefault(current)
                working = false
            }
        } else {
            startDraft { DocumentReader.fromImages(listOf(file)) }
        }
        addingPage = false
    }
    // Weitere Seiten: Fotos oder Dateien (PDF, Bild) werden an den offenen Entwurf angehaengt.
    // Neue Dokumente: eine Datei wie bisher, mehrere als Sammel-Import (einzeln oder zusammen?).
    fun handleUris(uris: List<Uri>) {
        val base = if (appendMode) draft else null
        appendMode = false
        if (base == null && uris.size > 1) {
            scope.launch { startBatch(BatchSource.describe(context, uris), 0) }
            return
        }
        scope.launch {
            working = true
            val result = runCatching { DocumentReader.fromUris(context, uris, base) }.getOrNull()
            working = false
            val pending = result?.first
            if (pending == null) {
                Toast.makeText(context, "Das Dokument konnte nicht gelesen werden.", Toast.LENGTH_SHORT).show()
            } else {
                if (base == null) setDraft(pending) else draft = pending
                if (result.second > 0) {
                    Toast.makeText(
                        context,
                        "${result.second} Datei(en) übersprungen. Zusammengefügt werden nur Fotos und PDFs, andere Dateien bitte einzeln laden.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) handleUris(uris) else appendMode = false
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) handleUris(uris) else appendMode = false
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) startFolder(tree)
    }

    fun launchCamera(forNewPage: Boolean) {
        val file = DocumentStore.newTempFile(context, "jpg")
        pendingCamera = file
        addingPage = forNewPage
        try {
            cameraLauncher.launch(DocumentStore.uriFor(context, file))
        } catch (_: ActivityNotFoundException) {
            pendingCamera = null
            addingPage = false
            Toast.makeText(context, "Keine Kamera-App gefunden.", Toast.LENGTH_SHORT).show()
        }
    }

    val activeCategory = category?.takeIf { c -> items.any { it.category == c } }
    val activeDoctor = doctorFilter?.takeIf { id -> items.any { it.doctorId == id } }
    val years = items.map { docYear(it) }.distinct().sortedDescending()
    val activeYear = yearFilter?.takeIf { it in years }
    val counts = items.groupingBy { it.category }.eachCount()
    val categories = DOC_CATEGORIES.filter { it in counts } + counts.keys.filter { it !in DOC_CATEGORIES }.sorted()
    val doctorsUsed = doctors.filter { d -> items.any { it.doctorId == d.id } }
    val categoryOptions = allDocCategories(items)

    val filtered = items.filter { d ->
        (activeCategory == null || d.category == activeCategory) &&
            (activeDoctor == null || d.doctorId == activeDoctor) &&
            (activeYear == null || docYear(d) == activeYear)
    }
    val hits = searchDocuments(filtered, doctors, query)
    val ordered = if (query.isNotBlank()) {
        hits.sortedWith(compareByDescending<DocHit> { it.score }.thenByDescending { docEpochDay(it.doc) })
    } else {
        when (sort) {
            DocSort.NEWEST -> hits.sortedByDescending { docEpochDay(it.doc) }
            DocSort.OLDEST -> hits.sortedBy { docEpochDay(it.doc) }
            DocSort.TITLE -> hits.sortedBy { it.doc.title.lowercase() }
            DocSort.CATEGORY -> hits.sortedWith(
                compareBy<DocHit> { categories.indexOf(it.doc.category) }.thenByDescending { docEpochDay(it.doc) },
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (items.isNotEmpty()) {
            DocumentFilterBar(
                query = query,
                onQuery = { query = it },
                categories = categories,
                counts = counts,
                total = items.size,
                activeCategory = activeCategory,
                onCategory = { category = it },
                years = years,
                activeYear = activeYear,
                onYear = { yearFilter = it },
                doctorsUsed = doctorsUsed,
                activeDoctor = activeDoctor,
                onDoctor = { doctorFilter = it },
                sort = sort,
                onSort = { sort = it },
                shown = ordered.size,
                onReset = {
                    query = ""
                    category = null
                    yearFilter = null
                    doctorFilter = null
                },
                onLetters = onOpenLetters,
            )
        } else {
            OutlinedButton(shape = buttonShape(), onClick = onOpenLetters, modifier = Modifier.padding(start = 16.dp, top = 8.dp)) {
                Text("Schreiben / Vollmacht erstellen")
            }
        }
        val docFold = rememberCardFoldState("dokumente")
        ListScreen(
            fold = docFold,
            foldKey = { it.doc.id },
            items = ordered,
            emptyText = if (items.isEmpty()) {
                "Noch keine Dokumente. Mit + Berichte, Befunde oder Bescheide fotografieren oder als Datei laden."
            } else {
                "Nichts gefunden. Ändere die Suche oder setze die Filter zurück."
            },
            onAdd = { showSource = true },
            modifier = Modifier.weight(1f),
        ) { hit ->
            val d = hit.doc
            ItemCard(
                title = d.title,
                lines = listOfNotNull(
                    listOfNotNull(d.category, d.dateText()).joinToString(" · "),
                    doctors.firstOrNull { it.id == d.doctorId }?.let { doctorLabel(it) },
                    d.summary.takeIf { it.isNotBlank() }?.let { sm -> sm.replace(Regex("\\s+"), " ").let { if (it.length > 140) it.take(140).trimEnd() + " …" else it } },
                    hit.snippet ?: d.text.takeIf { d.summary.isBlank() }?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }?.let { if (it.length > 100) it.take(100).trimEnd() + " …" else it },
                ),
                onClick = { detailId = d.id },
                onDelete = { vm.delete(d) },
                fold = docFold,
                foldKey = d.id,
                openLabel = "Öffnen",
            )
        }
    }

    if (showSource) {
        AlertDialog(
            onDismissRequest = { showSource = false },
            title = { Text("Dokument hinzufügen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(shape = buttonShape(), onClick = {
                        showSource = false
                        launchCamera(false)
                    }, modifier = Modifier.fillMaxWidth()) { Text("Dokument fotografieren") }
                    OutlinedButton(shape = buttonShape(), onClick = {
                        showSource = false
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, modifier = Modifier.fillMaxWidth()) { Text("Foto(s) aus der Galerie") }
                    OutlinedButton(shape = buttonShape(), onClick = {
                        showSource = false
                        fileLauncher.launch(arrayOf("application/pdf", "image/*", "text/plain", "*/*"))
                    }, modifier = Modifier.fillMaxWidth()) { Text("Datei(en) laden (PDF, Bild, Word, Text …)") }
                    OutlinedButton(shape = buttonShape(), onClick = {
                        showSource = false
                        try {
                            folderLauncher.launch(null)
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, "Auf diesem Handy lässt sich kein Ordner auswählen.", Toast.LENGTH_SHORT).show()
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Ganzen Ordner laden") }
                    Text(
                        "Mehrere Dateien oder ein Ordner: Die App liest alle, schlägt vor, welche zusammengehören (z. B. Seite 1 und 2), " +
                            "und du entscheidest: einzeln oder zusammen. Seiten lassen sich auch nach dem Einlesen hinzufügen. " +
                            "Der Text wird direkt auf dem Handy gelesen. Dokumente werden verschlüsselt in der App gespeichert und nirgends hin gesendet.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSource = false }) { Text("Abbrechen") } },
        )
    }

    if (showAddPage) {
        AlertDialog(
            onDismissRequest = { showAddPage = false },
            title = { Text("Weitere Seiten hinzufügen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(shape = buttonShape(), onClick = {
                        showAddPage = false
                        launchCamera(true)
                    }, modifier = Modifier.fillMaxWidth()) { Text("Seite fotografieren") }
                    OutlinedButton(shape = buttonShape(), onClick = {
                        showAddPage = false
                        appendMode = true
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, modifier = Modifier.fillMaxWidth()) { Text("Foto(s) aus der Galerie") }
                    OutlinedButton(shape = buttonShape(), onClick = {
                        showAddPage = false
                        appendMode = true
                        fileLauncher.launch(arrayOf("application/pdf", "image/*"))
                    }, modifier = Modifier.fillMaxWidth()) { Text("PDF oder Bild(er) laden") }
                    Text("Die Seiten werden hinten angefügt, der Text wird mitgelesen.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAddPage = false }) { Text("Abbrechen") } },
        )
    }

    if (working) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(if (progress != null) "Bitte einen Moment" else "Dokument wird gelesen") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(progress ?: "Erkenne Text …")
                }
            },
            confirmButton = {},
        )
    }

    batchPlan?.let { plan ->
        BatchImportDialog(
            files = batchFiles,
            plan = plan,
            assistantName = if (assistantOn) assistantName else null,
            skipped = batchSkipped,
            onConfirm = { groups ->
                val docs = batchDocs
                queue = groups.map { g -> g.map { docs[it] } }
                batchTotal = groups.size
                batchPos = 0
                batchSaved = 0
                batchPlan = null
                batchDocs = emptyList()
                batchFiles = emptyList()
                nextFromQueue()
            },
            onCancel = {
                batchDocs.forEach { DocumentStore.discard(it) }
                batchPlan = null
                batchDocs = emptyList()
                batchFiles = emptyList()
            },
        )
    }

    draft?.let { pending ->
        key(draftSerial) {
            DocumentDialog(
                pending = pending,
                batchInfo = if (batchTotal > 0) "Dokument $batchPos von $batchTotal" else null,
                onStopBatch = if (batchTotal > 0 && queue.isNotEmpty()) ({ cancelBatch() }) else null,
                initial = null,
                doctors = doctors,
                categoryOptions = categoryOptions,
                person = person,
                onAddTasks = { vm.addTasks(it) },
                onAddPage = { showAddPage = true },
                onRemoveLast = {
                    scope.launch {
                        working = true
                        draft = runCatching { DocumentReader.removeLastPage(pending) }.getOrDefault(pending)
                        working = false
                    }
                },
                onDismiss = {
                    DocumentStore.discard(pending)
                    if (batchTotal > 0) nextFromQueue() else draft = null
                },
                drafts = items,
                onLinkDraft = { vm.markDraftSigned(it) },
                onSave = { doc ->
                    scope.launch {
                        try {
                            val (name, mime) = DocumentStore.commit(context, pending)
                            val saved = doc.copy(fileName = name, mimeType = mime)
                            val newId = vm.addDocumentNow(saved)
                            // Entlassbericht oder Medikamentenplan: gleich öffnen, damit die Vorschläge zu sehen sind
                            // (beim Sammel-Import erst am Ende, sonst lägen zwei Fenster übereinander)
                            val openIt = isDischargeDoc(saved) || MedPlans.isPlanDocument(saved)
                            if (batchTotal > 0) {
                                batchSaved += 1
                                if (openIt && batchOpenLater == null) batchOpenLater = newId
                                nextFromQueue()
                            } else {
                                draft = null
                                if (openIt) detailId = newId
                            }
                        } catch (_: Exception) {
                            Toast.makeText(context, "Speichern hat nicht geklappt.", Toast.LENGTH_LONG).show()
                        }
                    }
                },
            )
        }
    }

    items.firstOrNull { it.id == detailId }?.let { doc ->
        DocumentDetailDialog(
            doc = doc,
            doctors = doctors,
            onEdit = {
                editItem = doc
                detailId = null
            },
            onClose = { detailId = null },
            extra = {
                if (isDischargeDoc(doc)) {
                    DischargeBox(doc, person, meds, doctors, vm)
                } else if (MedPlans.isPlanDocument(doc)) {
                    MedPlanImportBox(doc, person, meds, vm)
                }
                if (isLetterDraft(doc)) {
                    OutlinedButton(shape = buttonShape(), onClick = {
                        scope.launch {
                            mailLetterDraft(context, doc, doctors.firstOrNull { it.id == doc.doctorId }?.email, de.meinhelfer.tag.update.AppPrefs.caregiver(context).name)
                        }
                    }) { Text("✉ Schreiben per E-Mail senden") }
                }
            },
        )
    }
    editItem?.let { doc ->
        DocumentDialog(
            pending = null,
            initial = doc,
            doctors = doctors,
            categoryOptions = categoryOptions,
            person = person,
            onAddTasks = { vm.addTasks(it) },
            onAddPage = {},
            onRemoveLast = {},
            onDismiss = { editItem = null },
            onSave = {
                vm.update(it)
                editItem = null
            },
        )
    }
}

/** Formular fuer neues oder bestehendes Dokument. Titel, Kategorie, Datum und Kontakt werden aus dem Text vorgeschlagen. */
@Composable
private fun DocumentDialog(
    pending: PendingDoc?,
    initial: Document?,
    batchInfo: String? = null,
    onStopBatch: (() -> Unit)? = null,
    doctors: List<Doctor>,
    categoryOptions: List<String>,
    person: Person?,
    onAddTasks: (List<TaskDraft>) -> Unit,
    onAddPage: () -> Unit,
    onRemoveLast: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (Document) -> Unit,
    drafts: List<Document> = emptyList(),
    onLinkDraft: (Document) -> Unit = {},
) {
    val suggestion = remember { pending?.let { DocumentAnalyzer.analyze(it.text, doctors) } }
    var title by remember { mutableStateOf(initial?.title ?: suggestion?.title ?: "Dokument") }
    var category by remember { mutableStateOf(initial?.category ?: suggestion?.category ?: "Sonstiges") }
    var date by remember {
        mutableStateOf(initial?.docDate?.let { LocalDate.ofEpochDay(it) } ?: suggestion?.date)
    }
    var doctorId by remember { mutableStateOf(initial?.doctorId ?: suggestion?.doctorId) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var summary by remember { mutableStateOf(initial?.summary ?: "") }
    var text by remember { mutableStateOf(initial?.text ?: pending?.text ?: "") }
    var planCode by remember { mutableStateOf(initial?.planCode ?: pending?.planCode ?: "") }
    // Unterschriebene Fassung eines Entwurfs aus „Schreiben und Vollmachten“?
    val draftMatch = remember(text, drafts) { if (pending != null && initial == null) SignedCopies.bestMatch(text, drafts) else null }
    var linked by remember { mutableStateOf<Document?>(null) }
    var matchDismissed by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rereading by remember { mutableStateOf(false) }
    val knownSuggestion = remember(text) { DocumentAnalyzer.suggestKnownCategory(title, text) }
    val newSuggestion = remember(title, text) { DocumentAnalyzer.suggestNewCategory(title, text) }

    // Weitere Seite hinzugefuegt: erkannten Text uebernehmen
    LaunchedEffect(pending?.text) {
        if (pending != null) {
            text = pending.text
            planCode = pending.planCode
        }
    }

    AlertDialog(
        // Beim Sammel-Import nicht versehentlich durch Tippen daneben überspringen
        onDismissRequest = { if (batchInfo == null) onDismiss() },
        title = { Text(if (initial == null) "Dokument speichern" else "Dokument bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (batchInfo != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(batchInfo, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        if (onStopBatch != null) TextButton(onClick = onStopBatch) { Text("Restliche verwerfen") }
                    }
                }
                if (pending != null) {
                    val pages = if (pending.kind == DocKind.IMAGES) " · ${pending.files.size} Seite(n)" else ""
                    Text(
                        "Datei: ${pending.displayName}$pages",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (pending.kind == DocKind.IMAGES || pending.kind == DocKind.PDF) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(shape = buttonShape(), onClick = onAddPage) { Text("Weitere Seiten hinzufügen") }
                            if (pending.kind == DocKind.IMAGES && pending.files.size > 1) {
                                TextButton(onClick = onRemoveLast) { Text("Letzte Seite entfernen") }
                            }
                        }
                    }
                    if (pending.text.isBlank()) {
                        Text(
                            if (pending.kind == DocKind.OTHER) {
                                "Für diesen Dateityp wird kein Text gelesen. Das Dokument wird trotzdem gespeichert."
                            } else {
                                "Es wurde kein Text erkannt. Du kannst Titel und Notizen selbst eintragen."
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text(
                            "Titel, Kategorie, Datum und Kontakt wurden aus dem Text vorgeschlagen. Bitte prüfen.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                val match = draftMatch
                val link = linked
                if (link != null) {
                    Text("✓ Zugeordnet: unterschriebene Fassung von „${link.title}“", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { linked = null }) { Text("Zuordnung aufheben") }
                } else if (match != null && !matchDismissed) {
                    HCard(Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Unterschriebene Fassung?", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Der Text passt zum Entwurf „${match.title}“. Soll dieses Dokument als unterschriebene Fassung dazu abgelegt werden?",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(shape = buttonShape(), onClick = {
                                    linked = match
                                    title = SignedCopies.baseTitle(match) + " (unterschrieben)"
                                    category = match.category
                                    if (match.doctorId != null) doctorId = match.doctorId
                                    if (date == null) date = LocalDate.now()
                                    val note = "Unterschriebene Fassung von „${match.title}“."
                                    if (!notes.contains(note)) notes = listOf(note, notes).filter { it.isNotBlank() }.joinToString("\n")
                                }) { Text("Ja, zuordnen") }
                                TextButton(onClick = { matchDismissed = true }) { Text("Nein") }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownField(label = "Kategorie", value = category, options = categoryOptions, onValueChange = { category = it })
                if (category.isBlank() || category == "Sonstiges") {
                    val chips = listOfNotNull(
                        knownSuggestion?.takeIf { it != category }?.let { it to "Vorschlag: $it" },
                        newSuggestion?.takeIf { it != category && it != knownSuggestion }?.let {
                            it to (if (it in categoryOptions) "Vorschlag: $it" else "Neue Kategorie: $it")
                        },
                    )
                    if (chips.isNotEmpty()) {
                        Text("Passt das besser?", style = MaterialTheme.typography.labelMedium)
                        chips.forEach { (name, label) ->
                            AssistChip(onClick = { category = name }, label = { Text(label) })
                        }
                        Text(
                            "Antippen übernimmt den Namen. Du kannst oben auch eine bestehende Kategorie wählen oder selbst einen Namen eintippen.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                DatePickerField(label = "Datum des Dokuments", date = date, onChange = { date = it })
                if (date != null) TextButton(onClick = { date = null }) { Text("Datum entfernen") }
                if (doctors.isNotEmpty()) {
                    DropdownField(
                        label = "Arzt / Stelle",
                        value = doctors.firstOrNull { it.id == doctorId }?.let { doctorLabel(it) } ?: "",
                        options = listOf(NO_CONTACT) + doctors.map { doctorLabel(it) },
                        editable = false,
                        onValueChange = { chosen -> doctorId = doctors.firstOrNull { doctorLabel(it) == chosen }?.id },
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notizen") },
                    modifier = Modifier.fillMaxWidth(),
                )
                DocSummaryBox(
                    text = text,
                    person = person,
                    summary = summary,
                    onSummary = { summary = it },
                    doctors = doctors,
                    docDay = date?.toEpochDay(),
                    onAddTasks = onAddTasks,
                )
                if (initial != null && DocumentReader.canExtract(initial.mimeType)) {
                    OutlinedButton(shape = buttonShape(), 
                        enabled = !rereading,
                        onClick = {
                            scope.launch {
                                rereading = true
                                val result = runCatching {
                                    val file = DocumentStore.decryptToCache(
                                        context,
                                        initial.fileName,
                                        DocumentStore.safeName(initial.title) + "." + DocumentStore.extensionFor(initial.mimeType),
                                    )
                                    val found = DocumentReader.extractText(file, initial.mimeType)
                                    // Medikationsplan: auch den Code auf dem Plan neu suchen
                                    if (MedPlans.looksLikePlan(found) || initial.category == "Medikamentenplan") {
                                        DocumentReader.planCodeInFile(file, initial.mimeType).takeIf { it.isNotBlank() }?.let { planCode = it }
                                    }
                                    found
                                }.getOrDefault("")
                                rereading = false
                                if (result.isBlank()) {
                                    Toast.makeText(context, "Es wurde kein Text erkannt.", Toast.LENGTH_SHORT).show()
                                } else {
                                    text = result.trim()
                                }
                            }
                        },
                    ) { Text(if (rereading) "Lese Text …" else "Text neu auslesen") }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Erkannter Text (durchsuchbar, korrigierbar)") },
                    minLines = 3,
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    linked?.let(onLinkDraft)
                    onSave(
                        (initial ?: Document(title = title, fileName = "")).copy(
                            title = title.trim(),
                            category = category.ifBlank { "Sonstiges" },
                            docDate = date?.toEpochDay(),
                            doctorId = doctorId?.takeIf { id -> doctors.any { it.id == id } },
                            notes = notes.trim(),
                            summary = summary.trim(),
                            text = text.trim(),
                            planCode = planCode,
                            mimeType = initial?.mimeType ?: pending?.mime ?: "application/pdf",
                        ),
                    )
                },
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (batchInfo != null) "Überspringen" else "Abbrechen") } },
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun DocumentDetailDialog(
    doc: Document,
    doctors: List<Doctor>,
    onEdit: (() -> Unit)?,
    onClose: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showFullText by remember { mutableStateOf(false) }

    var showViewer by remember { mutableStateOf(false) }
    if (showViewer) DocumentViewer(doc) { showViewer = false }

    fun withDecryptedSuspend(action: suspend (File) -> Unit) {
        scope.launch {
            try {
                val ext = DocumentStore.extensionFor(doc.mimeType)
                action(DocumentStore.decryptToCache(context, doc.fileName, DocumentStore.safeName(doc.title) + "." + ext))
            } catch (_: Exception) {
                Toast.makeText(context, "Die Datei konnte nicht geöffnet werden.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun withDecrypted(action: (File) -> Unit) {
        scope.launch {
            try {
                val ext = DocumentStore.extensionFor(doc.mimeType)
                action(DocumentStore.decryptToCache(context, doc.fileName, DocumentStore.safeName(doc.title) + "." + ext))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "Dafür ist keine passende App installiert.", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, "Die Datei konnte nicht geöffnet werden.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(doc.title) },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val facts = listOfNotNull(
                    "Kategorie: ${doc.category}",
                    doc.dateText()?.let { "Datum: $it" },
                    doctors.firstOrNull { it.id == doc.doctorId }?.let { "Arzt / Stelle: ${doctorLabel(it)}" },
                    doc.notes.takeIf { it.isNotBlank() }?.let { "Notizen: $it" },
                )
                facts.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                extra?.invoke()
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (doc.summary.isNotBlank() || doc.text.isNotBlank()) {
                        SpeakButton(owner = "dokument_${doc.id}") {
                            listOfNotNull(
                                doc.title,
                                doc.summary.takeIf { it.isNotBlank() }?.let { "Zusammenfassung: $it" },
                                doc.text.takeIf { it.isNotBlank() }?.let { "Text des Dokuments: $it" },
                            ).joinToString(". ")
                        }
                    }
                    OutlinedButton(shape = buttonShape(), onClick = {
                        if (AppPrefs.internalViewer(context) && canViewInside(doc)) {
                            showViewer = true
                        } else {
                            withDecrypted { file ->
                                val view = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(DocumentStore.uriFor(context, file), doc.mimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(view)
                            }
                        }
                    }) { Text("Öffnen") }
                    if (canPrint(doc)) {
                        OutlinedButton(shape = buttonShape(), onClick = {
                            withDecryptedSuspend { file ->
                                if (!printDocument(context, doc, file)) {
                                    Toast.makeText(context, "Drucken ist nicht möglich.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) { Text("Drucken") }
                    }
                    OutlinedButton(shape = buttonShape(), onClick = {
                        withDecrypted { file ->
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = doc.mimeType
                                putExtra(Intent.EXTRA_STREAM, DocumentStore.uriFor(context, file))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "Dokument teilen"))
                        }
                    }) { Text("Teilen") }
                }
                Text(
                    "Öffnen zeigt das Dokument in der App (einstellbar). Teilen und „Andere App“ geben eine Kopie an die gewählte App, nur nach deinem Tipp.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (doc.summary.isNotBlank()) {
                    Text("Zusammenfassung (Anhaltspunkt)", style = MaterialTheme.typography.labelLarge)
                    Text(doc.summary, style = MaterialTheme.typography.bodyMedium)
                }
                if (doc.text.isNotBlank()) {
                    Text("Erkannter Text", style = MaterialTheme.typography.labelLarge)
                    val shown = if (showFullText || doc.text.length <= 1200) doc.text else doc.text.take(1200).trimEnd() + " …"
                    Text(shown, style = MaterialTheme.typography.bodyMedium)
                    if (doc.text.length > 1200) {
                        TextButton(onClick = { showFullText = !showFullText }) {
                            Text(if (showFullText) "Weniger anzeigen" else "Alles anzeigen")
                        }
                    }
                }
            }
        },
        confirmButton = { if (onEdit != null) TextButton(onClick = onEdit) { Text("Bearbeiten") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Schließen") } },
    )
}

/** Entlassbericht (nicht der Medikationsplan selbst): eigene Vorschläge für Medikation bei Entlassung und Kontrollen. */
internal fun isDischargeDoc(doc: Document): Boolean =
    doc.category != "Medikamentenplan" && doc.planCode.isBlank() && Discharge.looksLikeReport(doc.category, doc.text)
