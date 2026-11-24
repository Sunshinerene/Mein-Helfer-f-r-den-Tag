package de.meinhelfer.tag.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.meinhelfer.tag.summary.Assistant
import de.meinhelfer.tag.summary.Expression
import de.meinhelfer.tag.summary.NextKind
import de.meinhelfer.tag.summary.NextUp
import de.meinhelfer.tag.summary.Proposal
import de.meinhelfer.tag.summary.SupportLevel
import de.meinhelfer.tag.summary.Tageslage
import de.meinhelfer.tag.summary.VitalKind
import de.meinhelfer.tag.summary.AssistantBrief
import de.meinhelfer.tag.update.AppPrefs
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Alles, was der Assistent gerade weiß (über alle betreuten Personen). */
private class AssistantState(
    val name: String,
    val line: List<NextUp>,
    val proposals: List<Proposal>,
    val preps: List<de.meinhelfer.tag.summary.AppointmentPrep>,
    val expression: Expression,
    val headline: String,
    val multi: Boolean,
)

@Composable
private fun rememberAssistantState(vm: MainViewModel): AssistantState {
    val name by vm.assistantName.collectAsStateWithLifecycle()
    val persons by vm.persons.collectAsStateWithLifecycle()
    val meds by vm.allMedications.collectAsStateWithLifecycle()
    val appointments by vm.allAppointments.collectAsStateWithLifecycle()
    val todos by vm.allTodos.collectAsStateWithLifecycle()
    val checkins by vm.checkins.collectAsStateWithLifecycle()
    val duties by vm.duties.collectAsStateWithLifecycle()
    val doctors by vm.doctors.collectAsStateWithLifecycle()
    val dismissed by vm.assistantDismissed.collectAsStateWithLifecycle()
    val vitals by vm.allVitals.collectAsStateWithLifecycle()
    val plan by vm.motivator.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val now = LocalDateTime.now()
    val zone = ZoneId.systemDefault()
    val window = AppPrefs.intakeWindow(androidx.compose.ui.platform.LocalContext.current).toLong()
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val planTick by vm.measurePlanTick.collectAsStateWithLifecycle()
    val plans = remember(persons, planTick) {
        persons.associate { p -> p.id to de.meinhelfer.tag.summary.MeasurePlans.parse(AppPrefs.measurePlan(appContext, p.id)) }
    }
    val measures = de.meinhelfer.tag.summary.MeasurePlans.openToday(plans, vitals, now, zone)
    val line = Tageslage.dayLine(persons, meds, checkins, appointments, todos, now, zone, window, measures)
    val recent by vm.twoWeekCheckins.collectAsStateWithLifecycle()
    val ratesText by vm.ratesText.collectAsStateWithLifecycle()
    val ratesTick by vm.ratesTick.collectAsStateWithLifecycle()
    val rates = remember(ratesText, ratesTick, today) { vm.rateChanges() }
    val wake = if (AppPrefs.wakeOn(appContext)) {
        de.meinhelfer.tag.summary.WakeUp.plans(persons, appointments, checkins, now, zone, AppPrefs.wakeEarly(appContext), AppPrefs.wakeLead(appContext))
            .filter { it.key !in dismissed }
            .map { de.meinhelfer.tag.summary.WakeUp.proposal(it, persons.size > 1, name) }
    } else {
        emptyList()
    }
    val birthdays = if (AppPrefs.birthdaysOn(appContext)) {
        val entries = de.meinhelfer.tag.reminder.birthdayEntries(appContext, persons)
        de.meinhelfer.tag.summary.Birthdays.upcoming(entries, now.toLocalDate(), 7).mapNotNull { u ->
            val key = "geburtstag:${u.entry.name}:${u.date}:" + if (u.daysUntil <= 1) "tag" else "vorher"
            if (key in dismissed) return@mapNotNull null
            if (u.daysUntil <= 1) {
                de.meinhelfer.tag.summary.Proposal(key, "🎂 " + de.meinhelfer.tag.summary.Birthdays.sentence(u), "Danke", de.meinhelfer.tag.summary.ProposalAction.Acknowledge)
            } else {
                val forPerson = persons.firstOrNull { it.name == u.entry.name }?.id ?: persons.firstOrNull()?.id ?: return@mapNotNull null
                val text = "Karte oder Geschenk für ${u.entry.name} besorgen"
                de.meinhelfer.tag.summary.Proposal(
                    key,
                    "🎂 " + de.meinhelfer.tag.summary.Birthdays.sentence(u) + " Aufgabe „$text“ anlegen?",
                    "Aufgabe anlegen",
                    de.meinhelfer.tag.summary.ProposalAction.ReorderTask(forPerson, text, null, u.date.toEpochDay() - 2),
                )
            }
        }
    } else {
        emptyList()
    }
    // Haushalt: Termine aller Personen abgleichen, Werkstattzeiten, Ausweis-Ablauf
    val householdHints = run {
        val members = persons.map { p ->
            de.meinhelfer.tag.summary.HouseholdPlanner.Member(
                p.id, p.name,
                de.meinhelfer.tag.summary.Household.decode(p.household),
                de.meinhelfer.tag.summary.Disability.decode(p.disability),
            )
        }
        val appts = appointments.filter { !Tageslage.skipped(it, checkins, zone) }.map { a ->
            de.meinhelfer.tag.summary.HouseholdPlanner.Appt(a.id, a.personId, a.title, a.whenMillis, a.doctorId, doctors.firstOrNull { it.id == a.doctorId }?.name.orEmpty())
        }
        de.meinhelfer.tag.summary.HouseholdPlanner.hints(members, appts, System.currentTimeMillis(), zone, travelFor = { a ->
            a.doctorId?.let { AppPrefs.travelTo(appContext, it) }?.takeIf { it >= 0 } ?: AppPrefs.leaveMinutes(appContext)
        }).filter { it.key !in dismissed }.map { h ->
            if (h.task != null) {
                de.meinhelfer.tag.summary.Proposal(h.key, "🏠 " + h.text, "Aufgabe anlegen", de.meinhelfer.tag.summary.ProposalAction.ReorderTask(h.personId, h.task, h.doctorId, h.due ?: today))
            } else {
                de.meinhelfer.tag.summary.Proposal(h.key, "🏠 " + h.text, "Verstanden", de.meinhelfer.tag.summary.ProposalAction.Acknowledge)
            }
        }
    }
    // Neue App-Version: Sunny fragt, ob sie laden und installieren soll
    val upd by vm.updateState.collectAsStateWithLifecycle()
    val sunnyBusy by vm.sunnyInstall.collectAsStateWithLifecycle()
    val autoTick by vm.autoTick.collectAsStateWithLifecycle()
    val cleanupCount by vm.cleanupCount.collectAsStateWithLifecycle()
    val updateProposal = if (!AppPrefs.sunnyUpdates(appContext) || sunnyBusy) emptyList() else when (val u = upd) {
        is de.meinhelfer.tag.update.UpdateState.Available -> listOf(
            de.meinhelfer.tag.summary.Proposal(
                "update:${u.info.version}",
                "📲 Es gibt eine neue Version (${u.info.version}). Soll ich sie herunterladen und installieren? Android fragt am Ende noch einmal nach – dann einfach „Installieren“ tippen. Deine Daten bleiben erhalten.",
                "Ja, aktualisieren",
                de.meinhelfer.tag.summary.ProposalAction.StartUpdate(u.info.version),
            ),
        )
        is de.meinhelfer.tag.update.UpdateState.Ready -> listOf(
            de.meinhelfer.tag.summary.Proposal(
                "update:bereit:${u.info.version}",
                "📲 Version ${u.info.version} ist schon geladen. Jetzt installieren? Android fragt noch einmal nach – deine Daten bleiben erhalten.",
                "Jetzt installieren",
                de.meinhelfer.tag.summary.ProposalAction.StartUpdate(u.info.version),
            ),
        )
        else -> emptyList()
    }.filter { it.key !in dismissed }
    // Steuerwerk: Aufräumen anbieten (nur bei „Nur fragen“; sonst erledigt Sunny es selbst)
    val cleanupProposal = if (cleanupCount > 0 && autoTick >= 0 &&
        AppPrefs.autoLevel(appContext, de.meinhelfer.tag.summary.AutoArea.Cleanup) == de.meinhelfer.tag.summary.AutoLevel.Ask
    ) {
        listOf(
            de.meinhelfer.tag.summary.Proposal(
                "aufraeumen:${today / 7}",
                "🧹 $cleanupCount erledigte Aufgabe" + (if (cleanupCount == 1) " ist" else "n sind") + " älter als 14 Tage. Soll ich sie aus der Liste nehmen? Im Verlauf bleiben sie.",
                "Ja, aufräumen",
                de.meinhelfer.tag.summary.ProposalAction.Cleanup,
            ),
        ).filter { it.key !in dismissed }
    } else {
        emptyList()
    }
    // Steuerwerk: immer „Ja“ → mehr Selbstständigkeit anbieten
    val upgrades = listOf(
        de.meinhelfer.tag.summary.AutoArea.Updates to "update",
        de.meinhelfer.tag.summary.AutoArea.Cleanup to "aufraeumen",
        de.meinhelfer.tag.summary.AutoArea.Reorder to "bestellen",
    ).mapNotNull { (area, cat) ->
        val level = AppPrefs.autoLevel(appContext, area)
        if (!de.meinhelfer.tag.summary.Steuerwerk.offerUpgrade(area, level, AppPrefs.autoStat(appContext, cat))) return@mapNotNull null
        de.meinhelfer.tag.summary.Proposal(
            "stufe:${area.key}",
            "⚙️ Bei „${area.title}“ sagst du immer Ja. Soll ich das künftig ohne Frage machen und dir danach Bescheid sagen? Ändern kannst du das jederzeit unter Persönlicher Assistent → Steuerwerk.",
            "Ja, gern",
            de.meinhelfer.tag.summary.ProposalAction.SetAutonomy(area.key, de.meinhelfer.tag.summary.AutoLevel.Report.key),
        ).takeIf { it.key !in dismissed }
    }
    // Steuerwerk: Sicherung, Freigaben, Daten-Check (nach jeder Rückkehr in die App neu prüfen)
    var resumeTick by remember { mutableStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    val backupProposal = remember(resumeTick, autoTick, today) {
        if (!AppPrefs.backupDue(appContext)) return@remember emptyList<de.meinhelfer.tag.summary.Proposal>()
        val last = AppPrefs.lastBackup(appContext)
        val days = ((System.currentTimeMillis() - last) / (24L * 3600 * 1000)).toInt()
        listOf(
            de.meinhelfer.tag.summary.Proposal(
                "sicherung:${today / 7}",
                "💾 " + (if (last > 0) "Die letzte Sicherung ist $days Tage her." else "Es gibt noch keine Sicherung.") +
                    " Soll ich die Sicherung öffnen? Passwort und Speicherort wählst du selbst.",
                "Sicherung öffnen",
                de.meinhelfer.tag.summary.ProposalAction.OpenSettings("Sicherheit"),
            ),
        )
    }.filter { it.key !in dismissed }
    val permissionProposal = remember(resumeTick, today) {
        val missing = listOfNotNull(
            ("benachrichtigung" to "Benachrichtigungen sind aus – ohne sie kommen keine Erinnerungen.").takeIf { !AppPermissions.notificationsOn(appContext) },
            ("ton" to "Erinnerungen sind stumm geschaltet – du hörst sie nicht.").takeIf { AppPermissions.notificationsOn(appContext) && !AppPermissions.reminderChannelOk(appContext) },
            ("wecker" to "Pünktliche Wecker sind nicht erlaubt – Erinnerungen können sich verspäten.").takeIf { !AppPermissions.exactAlarms(appContext) },
            ("akku" to "Die Akku-Optimierung ist an – Android hält Erinnerungen manchmal zurück.").takeIf { !AppPermissions.batteryUnrestricted(appContext) },
        )
        missing.firstOrNull()?.let { (kind, text) ->
            listOf(
                de.meinhelfer.tag.summary.Proposal(
                    "freigabe:$kind:${today / 7}",
                    "🔔 " + (if (missing.size > 1) "Schritt 1 von ${missing.size}: " else "") + text + " Soll ich dich zur richtigen Stelle bringen? Dort einschalten und zurückkommen.",
                    "Hinführen",
                    de.meinhelfer.tag.summary.ProposalAction.FixPermission(kind),
                ),
            )
        }.orEmpty()
    }.filter { it.key !in dismissed }
    val dataCheckProposal = remember(persons, meds, doctors, appointments, today) {
        val found = de.meinhelfer.tag.summary.DataCheck.findings(persons, meds, doctors, appointments, System.currentTimeMillis())
        if (found.isEmpty()) emptyList() else listOf(
            de.meinhelfer.tag.summary.Proposal(
                "datencheck:${today / 7}",
                "🔎 " + de.meinhelfer.tag.summary.DataCheck.sentence(found) + " Magst du das bei Gelegenheit ergänzen?",
                "Verstanden",
                de.meinhelfer.tag.summary.ProposalAction.Acknowledge,
            ),
        )
    }.filter { it.key !in dismissed }
    val now0 = System.currentTimeMillis()
    val allProposals = (permissionProposal + updateProposal + backupProposal + cleanupProposal + upgrades + wake + birthdays + householdHints + Assistant.proposals(persons, todos, meds, duties, doctors, today, dismissed, recent, rates.first, rates.second))
        .plus(dataCheckProposal)
        .filter { !de.meinhelfer.tag.summary.Steuerwerk.paused(AppPrefs.autoStat(appContext, de.meinhelfer.tag.summary.Steuerwerk.category(it.key)), now0) }
    // Steuerwerk: Nachbestellen ohne Frage, wenn die Stufe es erlaubt
    val reorderLevel = AppPrefs.autoLevel(appContext, de.meinhelfer.tag.summary.AutoArea.Reorder)
    val autoReorder = if (reorderLevel == de.meinhelfer.tag.summary.AutoLevel.Ask) emptyList() else allProposals.filter {
        de.meinhelfer.tag.summary.Steuerwerk.category(it.key) == "bestellen" && it.action is de.meinhelfer.tag.summary.ProposalAction.ReorderTask
    }
    androidx.compose.runtime.LaunchedEffect(autoReorder.map { it.key }) {
        autoReorder.forEach { vm.autoApply(it, report = reorderLevel == de.meinhelfer.tag.summary.AutoLevel.Report) }
    }
    val proposals = allProposals - autoReorder.toSet()
    val measured = persons.associate { p ->
        p.id to vitals.any { it.personId == p.id && it.kind != VitalKind.Trinken.key && it.day >= today - 14 }
    }
    // Wetter zum Termin (nur wenn eingeschaltet; höchstens alle 3 Stunden neu geholt)
    var weatherTick by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val before = AppPrefs.weatherFetched(appContext)
        de.meinhelfer.tag.info.Weather.refresh(appContext)
        if (AppPrefs.weatherFetched(appContext) != before) weatherTick++
    }
    val preps = Assistant.preps(persons, appointments, checkins, meds, doctors, measured, now, zone).map { p ->
        val w = if (weatherTick >= 0) de.meinhelfer.tag.info.Weather.hintAt(appContext, p.appointment.whenMillis, zone) else ""
        if (w.isBlank()) p else p.copy(hints = listOf(w) + p.hints)
    }
    val doneToday = checkins.count { it.day == today && it.done } +
        todos.count { it.done && it.doneMillis != null && Instant.ofEpochMilli(it.doneMillis).atZone(zone).toLocalDate().toEpochDay() == today }
    val expression = Assistant.expression(line, plan?.support ?: SupportLevel.None, doneToday, now)
    return AssistantState(name, line, proposals, preps, expression, Assistant.headline(line, persons.size > 1), persons.size > 1)
}

private fun icon(n: NextUp) = when (n.kind) {
    NextKind.Intake -> "💊"
    NextKind.Appointment -> "📅"
    NextKind.Task -> "✅"
    NextKind.Measure -> "🩺"
}

@Composable
private fun NextUpRow(n: NextUp, multi: Boolean) {
    Text(
        icon(n) + " " + n.title + (if (multi && n.personName.isNotBlank()) " · ${n.personName}" else "") +
            (if (n.overdue) (if (n.kind == NextKind.Task) " · überfällig" else " · noch offen") else ""),
        style = MaterialTheme.typography.bodyMedium,
        color = if (n.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ProposalBox(p: Proposal, vm: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val alarm = p.action as? de.meinhelfer.tag.summary.ProposalAction.SetAlarm
    var alarmFailed by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text((if (alarm != null) "⏰ " else "💡 ") + p.text, style = MaterialTheme.typography.bodyMedium)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(shape = buttonShape(), onClick = {
                val perm = p.action as? de.meinhelfer.tag.summary.ProposalAction.FixPermission
                if (alarm != null) {
                    if (setAlarm(context, alarm, skipUi = true)) vm.applyProposal(p) else alarmFailed = true
                } else if (perm != null) {
                    when (perm.kind) {
                        "benachrichtigung" -> AppPermissions.openNotificationSettings(context)
                        "ton" -> AppPermissions.openChannelSettings(context)
                        "wecker" -> AppPermissions.openExactAlarmSettings(context)
                        "akku" -> AppPermissions.requestBattery(context)
                    }
                    // nicht als „erledigt“ ausblenden: nach der Rückkehr wird neu geprüft
                    val cat = de.meinhelfer.tag.summary.Steuerwerk.category(p.key)
                    AppPrefs.setAutoStat(context, cat, de.meinhelfer.tag.summary.Steuerwerk.accepted(AppPrefs.autoStat(context, cat)))
                    AppPrefs.addJournal(context, cat, "Zur Freigabe geführt: " + p.text.substringAfter(": ").substringBefore(" Soll ich").take(120))
                    vm.bumpAuto()
                } else {
                    vm.applyProposal(p)
                }
            }) { Text(p.confirmLabel) }
            if (alarm != null) {
                TextButton(onClick = { if (setAlarm(context, alarm, skipUi = false)) vm.applyProposal(p) else alarmFailed = true }) { Text("Andere Zeit …") }
            }
            TextButton(onClick = { vm.dismissProposal(p.key) }) { Text("Nein, danke") }
        }
        if (alarmFailed) {
            Text("Ich konnte die Uhr-App nicht öffnen. Bitte stell den Wecker selbst.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Wecker über die Uhr-App des Handys stellen ([skipUi] = ohne die Uhr-App zu zeigen). */
private fun setAlarm(context: android.content.Context, a: de.meinhelfer.tag.summary.ProposalAction.SetAlarm, skipUi: Boolean): Boolean = runCatching {
    val intent = android.content.Intent(android.provider.AlarmClock.ACTION_SET_ALARM)
        .putExtra(android.provider.AlarmClock.EXTRA_HOUR, a.hour)
        .putExtra(android.provider.AlarmClock.EXTRA_MINUTES, a.minute)
        .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, a.label.take(60))
        .putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, skipUi)
    context.startActivity(intent)
    if (skipUi) android.widget.Toast.makeText(context, "Wecker für %d:%02d gestellt".format(a.hour, a.minute), android.widget.Toast.LENGTH_LONG).show()
    true
}.getOrDefault(false)

/** Vorlese-Text des Assistenten. */
private fun speech(st: AssistantState, greeting: String?, sentence: String?): String = buildString {
    greeting?.let { append(it).append(". ") }
    append(st.headline).append(" ")
    st.preps.forEach { p ->
        append(p.whenText).append(" Uhr: ").append(p.appointment.title).append(". ")
        p.hints.forEach { append(it).append(". ") }
    }
    st.proposals.forEach { append(it.text).append(" ") }
    sentence?.let { append("Und für dich: ").append(it) }
}

/** Karte auf der Heute-Seite (ersetzt den Tages-Motivator, solange der Assistent an ist). */
@Composable
fun AssistantCard(vm: MainViewModel, onOpen: (tab: Int) -> Unit, onMood: () -> Unit, onOpenDailyStart: (planOnly: Boolean) -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val personId by vm.personId.collectAsStateWithLifecycle()
    val todayDay by vm.today.collectAsStateWithLifecycle()
    var startTick by remember { mutableStateOf(0) }
    // Tagesstart über den Assistenten: einmal am Tag anbieten statt automatisch zu öffnen
    val offerStart = remember(personId, todayDay, startTick) {
        AppPrefs.dailyStartEnabled(context) && personId > 0 && AppPrefs.lastDailyStart(context, personId) < todayDay
    }
    val st = rememberAssistantState(vm)
    val plan by vm.motivator.collectAsStateWithLifecycle()
    val speaking by Speech.speaking.collectAsState()
    val ownHealth by vm.ownHealth.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshOwnHealth() }
    val selfHints = AssistantBrief.selfHints(ownHealth, java.time.LocalTime.now().hour)
    val p = plan
    // Von sich aus erzählen: beim Öffnen höchstens alle 3 Stunden, nie in der Ruhezeit oder bei stummem Handy
    val talkScope = androidx.compose.runtime.rememberCoroutineScope()
    var news by remember { mutableStateOf<de.meinhelfer.tag.info.NewsItem?>(null) }
    var newsLoading by remember { mutableStateOf(false) }
    val talkPersons by vm.persons.collectAsStateWithLifecycle()
    val talkMeds by vm.medications.collectAsStateWithLifecycle()
    val talkDocs by vm.documents.collectAsStateWithLifecycle()
    fun talkText(item: de.meinhelfer.tag.info.NewsItem?): String {
        val todayPreps = st.preps.filter { it.whenText.startsWith("Heute") }
        return AssistantBrief.talk(
            st.name, java.time.LocalTime.now().hour, st.line, todayPreps,
            st.proposals.count { it.action !is de.meinhelfer.tag.summary.ProposalAction.SetAlarm && !it.text.startsWith("🎂") },
            st.proposals.filter { it.action is de.meinhelfer.tag.summary.ProposalAction.SetAlarm || it.text.startsWith("🎂") }
                .map { it.text.removePrefix("🎂 ") } + selfHints,
            item?.let { it.title to it.source }, st.multi,
            say = { t, d, n -> de.meinhelfer.tag.ai.SunnyMemory.say(context, t, d, n) },
        )
    }
    suspend fun fetchNews(): de.meinhelfer.tag.info.NewsItem? {
        if (!AppPrefs.assistantTalkNews(context)) return null
        newsLoading = true
        try {
            val person = talkPersons.firstOrNull { it.id == personId }
            val topics = (
                listOf("Pflegeversicherung", "Pflegereform", "pflegende Angehörige") +
                    de.meinhelfer.tag.info.PersonalTopics.suggest(person, talkMeds, talkDocs, AppPrefs.caregiver(context).roleValue).map { it.query }
                ).distinct()
            val told = AppPrefs.newsTold(context).toSet()
            val cutoff = System.currentTimeMillis() - 14L * 24 * 3600 * 1000
            repeat(3) {
                val q = topics[AppPrefs.nextNewsTopic(context) % topics.size]
                val item = de.meinhelfer.tag.info.NewsFeed.search(q).getOrNull()
                    ?.firstOrNull { it.link !in told && (it.published ?: 0L) >= cutoff }
                if (item != null) {
                    AppPrefs.addNewsTold(context, item.link)
                    return item
                }
            }
            return null
        } finally {
            newsLoading = false
        }
    }
    fun voluntaryTalkAllowed(): Boolean = de.meinhelfer.tag.summary.SunnyMoodPolicy.mayPrompt(
        AppPrefs.assistantTalk(context), AppPrefs.isQuietNow(context), false,
        System.currentTimeMillis(), context.getSharedPreferences("sunny_quiet", android.content.Context.MODE_PRIVATE).getLong("until", 0L),
    )
    fun tell(automatic: Boolean = false) {
        talkScope.launch {
            if (automatic && !voluntaryTalkAllowed()) return@launch
            val item = fetchNews() ?: news
            if (automatic && (!voluntaryTalkAllowed() || Speech.speaking.value != null)) return@launch
            news = item
            AppPrefs.setAssistantLastTalk(context, System.currentTimeMillis())
            Speech.speak(context, "assistent", talkText(item))
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!voluntaryTalkAllowed()) return@LaunchedEffect
        if (System.currentTimeMillis() - AppPrefs.assistantLastTalk(context) < 3L * 3600 * 1000) return@LaunchedEffect
        val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
        if (audio != null && audio.ringerMode != android.media.AudioManager.RINGER_MODE_NORMAL) return@LaunchedEffect
        kotlinx.coroutines.delay(1200)
        if (Speech.speaking.value == null && voluntaryTalkAllowed()) tell(automatic = true)
    }
    Foldable(
        key = "assistent",
        title = st.name + " · dein Assistent",
        summary = st.headline,
    ) {
        HCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AssistantFace(st.expression, size = 60.dp, speaking = speaking?.startsWith("assistent") == true, description = "Gesicht von ${st.name}")
                    Column(Modifier.weight(1f)) {
                        Text(p?.greeting ?: "Hallo", style = MaterialTheme.typography.titleMedium)
                        Text("Ich bin ${st.name}, dein persönlicher Assistent.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                // Alltagshelfer (Beta): angeheftete Werkzeuge als Knöpfe
                HelperChips(vm)
                if (offerStart) {
                    HCard(Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Wollen wir kurz auf gestern schauen und den Tag planen?", style = MaterialTheme.typography.bodyLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(shape = buttonShape(), onClick = {
                                    AppPrefs.setLastDailyStart(context, personId, todayDay)
                                    startTick++
                                    onOpenDailyStart(false)
                                }) { Text("Tagesstart") }
                                TextButton(onClick = {
                                    AppPrefs.setLastDailyStart(context, personId, todayDay)
                                    startTick++
                                }) { Text("Heute nicht") }
                            }
                        }
                    }
                }
                Text(st.headline, style = MaterialTheme.typography.bodyLarge)
                if (st.line.size > 1) {
                    st.line.take(5).forEach { NextUpRow(it, st.multi) }
                    if (st.line.size > 5) Text("… und ${st.line.size - 5} weitere", style = MaterialTheme.typography.bodySmall)
                }
                st.preps.forEach { prep ->
                    HorizontalDivider()
                    Text(
                        "📅 ${prep.whenText} · ${prep.appointment.title}" + (if (st.multi && prep.personName.isNotBlank()) " (${prep.personName})" else ""),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (prep.hints.isEmpty()) {
                        Text("Ich sehe dafür nichts vorzubereiten.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        prep.hints.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        PrepPdfButtons(vm, prep.appointment.personId)
                    }
                }
                if (st.proposals.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Mein Vorschlag", style = MaterialTheme.typography.labelLarge)
                    st.proposals.take(3).forEach { ProposalBox(it, vm) }
                }
                SunnyUpdateStatus(vm, st.name)
                SunnyReportBox(vm, st.name)
                if (p != null) {
                    HorizontalDivider()
                    Text("Für dich", style = MaterialTheme.typography.labelLarge)
                    Text(p.sentence, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
                    selfHints.forEach { Text("⌚ $it", style = MaterialTheme.typography.bodyMedium) }
                    p.impulse?.let { Text("Dein Impuls (KI): $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                    MoodStrip(p)
                    TextButton(onClick = onMood) { Text(if (p.moodToday) "Stimmung nochmal eintragen" else "Wie geht es dir? Stimmung eintragen") }
                    if (p.achieved.isNotEmpty()) {
                        Text("Geschafft: " + p.achieved.take(3).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Vorhaben für heute", style = MaterialTheme.typography.labelLarge)
                    p.intentions.forEach { IntentionRow(it, vm) }
                    p.goals.take(2).forEach { g -> GoalMini(g) { onOpen(2) } }
                    if (p.support != SupportLevel.None) {
                        TextButton(onClick = { onOpen(0) }) { Text("Unterstützung, wenn du sie brauchst") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(shape = buttonShape(), onClick = { onOpenDailyStart(false) }, modifier = Modifier.weight(1f)) { FitText("Rückblick gestern", maxSize = 14.sp) }
                    OutlinedButton(shape = buttonShape(), onClick = { onOpenDailyStart(true) }, modifier = Modifier.weight(1f)) { FitText("Tagesplan", maxSize = 14.sp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(shape = buttonShape(), onClick = { onOpen(0) }, modifier = Modifier.weight(1f)) { FitText("Mehr", maxSize = 14.sp) }
                    OutlinedButton(shape = buttonShape(), onClick = { onOpen(2) }, modifier = Modifier.weight(1f)) { FitText("Ziele", maxSize = 14.sp) }
                }
                news?.let { n ->
                    HorizontalDivider()
                    Text("📰 Neu" + (if (n.source.isNotBlank()) " · ${n.source}" else ""), style = MaterialTheme.typography.labelLarge)
                    Text(n.title, style = MaterialTheme.typography.bodyMedium)
                    Text("Nur ein Anhaltspunkt – prüfe die Quelle, keine Beratung.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(n.link))) }
                    }) { Text("Lesen") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(shape = buttonShape(), modifier = Modifier.weight(1f), enabled = !newsLoading, onClick = {
                        if (speaking == "assistent") Speech.stop() else tell()
                    }) { FitText(if (speaking == "assistent") "⏹ Stopp" else if (newsLoading) "Moment …" else "🗣 Erzähl mal", maxSize = 14.sp) }
                    SpeakButton(owner = "assistent-alles", label = "🔊 Alles vorlesen", modifier = Modifier.weight(1f)) {
                        speech(st, p?.greeting, p?.sentence)
                    }
                }
            }
        }
    }
}

/** Unterlagen für den Termin gleich als PDF teilen: Medikamentenplan und Werte der letzten 14 Tage. */
@Composable
private fun PrepPdfButtons(vm: MainViewModel, personId: Long) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val persons by vm.persons.collectAsStateWithLifecycle()
    val meds by vm.allMedications.collectAsStateWithLifecycle()
    val doctors by vm.doctors.collectAsStateWithLifecycle()
    val duties by vm.duties.collectAsStateWithLifecycle()
    val vitals by vm.allVitals.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val person = persons.firstOrNull { it.id == personId }
    val mine = meds.filter { it.personId == personId }
    val values = vitals.filter { it.personId == personId && it.day > today - 14 }
    if (mine.isEmpty() && values.none { it.kind != VitalKind.Trinken.key }) return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (mine.isNotEmpty()) {
            TextButton(onClick = {
                scope.launch {
                    runCatching {
                        val f = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            de.meinhelfer.tag.export.CarePdfs.medPlan(context, person, mine, doctors, AppPrefs.caregiver(context), duties)
                        }
                        sharePdfFile(context, f, "Medikamentenplan teilen")
                    }
                }
            }) { Text("Medikamentenplan") }
        }
        if (values.any { it.kind != VitalKind.Trinken.key }) {
            TextButton(onClick = {
                scope.launch {
                    runCatching {
                        val f = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            recentVitalsPdf(context, person, values, 14)
                        }
                        sharePdfFile(context, f, "Werte teilen")
                    }
                }
            }) { Text("Werte (14 Tage)") }
        }
    }
}

// ---------------------------------------------------------------- Hilfe zur Seite und „Frag mich“

/** Welche Teile der Anleitung zu welcher Seite gehören (Abschnitt oder einzelne Punkte). */
private val PAGE_HELP: Map<String, Pair<List<String>, List<String>>> = mapOf(
    "heute" to (listOf("heute") to emptyList()),
    "medikamente" to (listOf("medikamente") to emptyList()),
    "medikamentenplan" to (listOf("medikamente") to emptyList()),
    "nachbestellung" to (listOf("nachbestellung") to emptyList()),
    "termine" to (listOf("termine") to emptyList()),
    "dokumente" to (listOf("dokumente") to emptyList()),
    "schreiben" to (emptyList<String>() to listOf("Schreiben und Vollmachten erstellen")),
    "kontakte" to (listOf("kontakte") to emptyList()),
    "infos" to (listOf("infos") to emptyList()),
    "wegweiser" to (listOf("wegweiser") to emptyList()),
    "werte" to (emptyList<String>() to listOf("Trinken und Werte", "Schnellknöpfe und Messzeiten", "Bericht über Messwerte: Zeitraum wählen, mailen, ablegen", "Werte von Uhr oder Messgerät (Beta)", "Trinken und Pflegetagebuch")),
    "tagebuch" to (emptyList<String>() to listOf("Pflegetagebuch", "Trinken und Pflegetagebuch")),
    "tagesstart" to (emptyList<String>() to listOf("Tagesstart: Rückblick und Tagesplan")),
    "budgets" to (emptyList<String>() to listOf("Budgets der Pflegekasse")),
    "uebergabe" to (emptyList<String>() to listOf("Übergabe-Bericht")),
    "assistent" to (listOf("motivator") to emptyList()),
    "auswertung" to (listOf("auswertung") to emptyList()),
    "einstellungen" to (listOf("einstellungen") to emptyList()),
    "anleitung" to (listOf("start") to emptyList()),
)

private val PAGE_NAMES = mapOf(
    "heute" to "Heute", "medikamente" to "Medikamente", "medikamentenplan" to "Medikamentenplan", "nachbestellung" to "Nachbestellung",
    "termine" to "Termine", "dokumente" to "Dokumente", "schreiben" to "Schreiben", "kontakte" to "Kontakte", "infos" to "Infos",
    "wegweiser" to "Wegweiser", "werte" to "Trinken und Werte", "tagebuch" to "Pflegetagebuch", "tagesstart" to "Tagesstart",
    "budgets" to "Budgets", "uebergabe" to "Übergabe", "assistent" to "Assistent", "auswertung" to "Auswertung",
    "einstellungen" to "Einstellungen", "anleitung" to "Anleitung",
)

internal fun guideFor(page: String): List<GuideItem> {
    val (sections, titles) = PAGE_HELP[page] ?: (listOf("start") to emptyList())
    val fromSections = GUIDE_SECTIONS.filter { it.id in sections }.flatMap { it.items }
    val fromTitles = GUIDE_SECTIONS.flatMap { it.items }.filter { it.title in titles }
    return (fromSections + fromTitles).distinctBy { it.title }
}

private val STOP = setOf(
    "wie", "was", "wo", "wer", "wann", "warum", "ich", "kann", "man", "der", "die", "das", "ein", "eine", "einen", "und", "oder", "mit",
    "für", "finde", "mache", "geht", "bei", "auf", "von", "den", "dem", "des", "ist", "sind", "mein", "meine", "meinen", "nicht", "auch",
    "hier", "dort", "gibt", "es", "zu", "im", "in", "an", "am", "wird", "werden", "soll", "will", "möchte", "bitte", "app",
)

/** Antworten aus der Anleitung zu einer Frage (ohne Internet): die passendsten Punkte. */
internal fun searchGuide(question: String, max: Int = 3): List<GuideItem> {
    val words = question.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 && it !in STOP }
    if (words.isEmpty()) return emptyList()
    val stems = words.map { it.take(if (it.length > 6) it.length - 2 else it.length) }
    return GUIDE_SECTIONS.flatMap { it.items }
        .map { item ->
            val title = item.title.lowercase()
            val text = item.text.lowercase()
            val score = stems.sumOf { st -> (if (st in title) 4 else 0) + (if (st in text) 1 else 0) } +
                (if (stems.all { it in title || it in text }) 3 else 0)
            item to score
        }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(max)
        .map { it.first }
}

/** Was der Assistent zur aktuellen Seite sagt (kurz, aus den Daten). */
@Composable
private fun pageNote(page: String, vm: MainViewModel, st: AssistantState): String {
    val meds by vm.medications.collectAsStateWithLifecycle()
    val appointments by vm.appointments.collectAsStateWithLifecycle()
    val documents by vm.documents.collectAsStateWithLifecycle()
    val doctors by vm.doctors.collectAsStateWithLifecycle()
    val vitals by vm.vitals.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val persons by vm.persons.collectAsStateWithLifecycle()
    val personId by vm.personId.collectAsStateWithLifecycle()
    val who = persons.firstOrNull { it.id == personId }?.name ?: "die Person"
    val zone = ZoneId.systemDefault()
    return when (page) {
        "medikamente", "medikamentenplan", "nachbestellung" -> {
            val low = lowStockMeds(meds, today)
            "Für $who sind ${meds.size} Medikamente eingetragen. " +
                if (low.isEmpty()) "Der Vorrat reicht überall länger als eine Woche (soweit bekannt)."
                else "Bald nachbestellen: " + low.joinToString(", ") { it.name } + "."
        }
        "termine" -> {
            val next = appointments.filter { it.whenMillis >= System.currentTimeMillis() }.minByOrNull { it.whenMillis }
            if (next == null) "Für $who ist kein Termin geplant."
            else {
                val d = Instant.ofEpochMilli(next.whenMillis).atZone(zone).toLocalDate().toEpochDay()
                "Nächster Termin für $who: ${Tageslage.dayWord(d, today)} um ${Tageslage.time(next.whenMillis, zone)} Uhr, ${next.title}." +
                    if (st.preps.isNotEmpty()) " Was vorzubereiten ist, steht auf der Heute-Seite bei mir." else ""
            }
        }
        "dokumente" -> "Für $who liegen ${documents.size} Dokumente sicher auf dem Handy. Entlassberichte und Medikamentenpläne erkenne ich beim Hinzufügen."
        "kontakte" -> "${doctors.size} Kontakte. Wer für Rezepte und Apotheke zuständig ist und wer vertritt, legst du beim Kontakt fest."
        "werte", "tagebuch" -> {
            val drunk = vitals.filter { it.kind == VitalKind.Trinken.key && it.day == today }.sumOf { it.value }.toInt()
            val measured = vitals.count { it.kind != VitalKind.Trinken.key && it.day == today }
            "Heute für $who: $drunk ml getrunken, $measured Messwerte eingetragen."
        }
        "infos", "wegweiser", "budgets" -> "Hier findest du Wissen und Leistungen. Was du dir merken willst, kannst du als Aufgabe übernehmen."
        else -> st.headline
    }
}

/** Blatt, das sich über das Gesicht in der unteren Leiste öffnet: Lage, Vorschläge, Hilfe zur Seite, „Frag mich“. */
@Composable
fun AssistantSheetContent(vm: MainViewModel, page: String, onOpenGuide: () -> Unit, onOpenAssistant: () -> Unit) {
    val st = rememberAssistantState(vm)
    val speaking by Speech.speaking.collectAsState()
    var question by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    var aiAnswer by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val help = remember(page) { guideFor(page) }
    val note = pageNote(page, vm, st)
    val answers = remember(question) { searchGuide(question) }
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AssistantFace(st.expression, size = 52.dp, speaking = speaking == "seite_$page", description = "Gesicht von ${st.name}")
            Column(Modifier.weight(1f)) {
                Text(st.name, style = MaterialTheme.typography.titleMedium)
                Text("Zur Seite „${PAGE_NAMES[page] ?: "Heute"}“", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(note, style = MaterialTheme.typography.bodyLarge)
        if (page != "heute" && st.line.isNotEmpty()) Text("Heute als Nächstes: " + st.line.first().title, style = MaterialTheme.typography.bodySmall)
        st.proposals.take(2).forEach { ProposalBox(it, vm) }
        // Sunny-Menü: von jeder Seite aus erreichbar
        HorizontalDivider()
        Text("Alles von ${st.name}", style = MaterialTheme.typography.labelLarge)
        HelperChips(vm)
        if (!AppPrefs.helpersBeta(context)) {
            Text("Alltagshelfer (Kochen, Einkauf, Arztfragen …): unter „⚙️ ${st.name} einstellen“ → Beta einschalten.", style = MaterialTheme.typography.bodySmall)
        }
        SunnyReportBox(vm, st.name)
        OutlinedButton(shape = buttonShape(), onClick = { vm.requestNav("settings:Assistent") }) { Text("⚙️ ${st.name} einstellen") }
        HorizontalDivider()
        SpeakButton(owner = "seite_$page", label = "🔊 Vorlesen") {
            note + " " + help.take(6).joinToString(" ") { it.title + ". " + it.text }
        }
        if (ScreenComment.allowed(context)) {
            val data = pageContext(page, vm, st)
            val last by ScreenComment.last.collectAsState()
            var asking by remember { mutableStateOf(false) }
            var failed by remember { mutableStateOf<String?>(null) }
            val sheetScope = androidx.compose.runtime.rememberCoroutineScope()
            OutlinedButton(shape = buttonShape(), enabled = !asking, modifier = Modifier.fillMaxWidth(), onClick = {
                asking = true
                failed = null
                sheetScope.launch {
                    ScreenComment.ask(context, st.name, page, data, note).onFailure { failed = (it.message ?: "Das hat nicht geklappt.") + " Ich habe dir vorgelesen, was ich selbst sehe." }
                    asking = false
                }
            }) { Text(if (asking) "${st.name} denkt nach …" else "💬 Was meinst du zu dieser Seite?") }
            last?.takeIf { it.first == page }?.let { Text("🤖 " + it.second, style = MaterialTheme.typography.bodyMedium) }
            failed?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Text("KI vollumfänglich: gesendet wird, was auf der Seite steht, ohne Namen. Nur ein Anhaltspunkt.", style = MaterialTheme.typography.bodySmall)
        }

        HorizontalDivider()
        Text("Frag mich", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            label = { Text("z. B. Wie drucke ich den Medikamentenplan?") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        if (question.isNotBlank()) {
            if (answers.isEmpty()) {
                Text("Dazu finde ich in der Anleitung nichts. Versuch es mit anderen Worten oder schau in die Anleitung.", style = MaterialTheme.typography.bodySmall)
            }
            answers.forEach { a ->
                Column(Modifier.fillMaxWidth().clickable { open = if (open == "f_" + a.title) null else "f_" + a.title }) {
                    Text("❓ " + a.title, style = MaterialTheme.typography.bodyLarge)
                    if (open == "f_" + a.title) Text(a.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
            // Freiwillig: Frage mit KI beantworten, nur mit Abschnitten der Anleitung, ohne Daten aus der App
            if (de.meinhelfer.tag.ai.AiConfig.isConfigured(context)) {
                AiActionButton(
                    label = "${st.name} mit KI fragen",
                    note = "Gesendet werden nur deine Frage, so wie du sie eingetippt hast, und passende Abschnitte der Anleitung. " +
                        "Keine Daten aus der App (keine Namen, Medikamente, Termine). Schreib in die Frage bitte keine Namen.",
                    system = assistantAiSystem(st.name),
                    prompt = { assistantAiPrompt(question, answers + searchGuide(question, 6) + help) },
                    task = de.meinhelfer.tag.ai.AiTask.Frage,
                    onAnswer = { a, _ ->
                        aiAnswer = a.trim().takeIf { it.isNotBlank() }
                        aiAnswer != null
                    },
                )
            } else {
                Text("Mit eingerichteter KI (Einstellungen → Persönlicher Assistent → KI-Dienst) kann ich Fragen auch frei beantworten.", style = MaterialTheme.typography.bodySmall)
            }
            aiAnswer?.let {
                Text("🤖 $it", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "KI-Antwort, nur ein Anhaltspunkt. Bei Gesundheit, Recht und Geld zählt, was Ärztin, Arzt, Apotheke, Pflegekasse oder eine Beratungsstelle sagen.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        HorizontalDivider()
        Text("So geht's hier", style = MaterialTheme.typography.titleSmall)
        help.forEach { item ->
            Column(Modifier.fillMaxWidth().clickable { open = if (open == item.title) null else item.title }) {
                Text((if (open == item.title) "▴ " else "▾ ") + item.title, style = MaterialTheme.typography.bodyLarge)
                if (open == item.title) Text(item.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(shape = buttonShape(), onClick = onOpenAssistant, modifier = Modifier.weight(1f)) { FitText("Zu ${st.name}", maxSize = 14.sp) }
            OutlinedButton(shape = buttonShape(), onClick = onOpenGuide, modifier = Modifier.weight(1f)) { FitText("Anleitung", maxSize = 14.sp) }
        }
        Text(
            "Ich rechne nur auf dem Handy und ändere nichts ohne deinen Tipp. Keine medizinischen Ratschläge.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Kleines Gesicht für die untere Leiste. */
@Composable
fun AssistantDockFace(vm: MainViewModel) {
    val st = rememberAssistantState(vm)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AssistantFace(st.expression, size = 26.dp, description = "${st.name} öffnen")
        Text(st.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        if (st.line.any { it.overdue } || st.proposals.isNotEmpty()) {
            Text("•", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ---------------------------------------------------------------- Einstellungen → Persönlicher Assistent

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AssistantSettingsCard(vm: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val on by vm.assistantOn.collectAsStateWithLifecycle()
    val name by vm.assistantName.collectAsStateWithLifecycle()
    var own by remember { mutableStateOf("") }
    var morning by remember { mutableStateOf(AppPrefs.assistantMorning(context)) }
    var evening by remember { mutableStateOf(AppPrefs.assistantEvening(context)) }
    var ownValues by remember { mutableStateOf(AppPrefs.assistantOwnHealth(context)) }
    val ownLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
    ) { vm.refreshOwnHealth(force = true) }
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AssistantFace(Expression.Friendly, size = 44.dp)
                Text("Persönlicher Assistent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = on, onCheckedChange = { vm.setAssistantOn(it) })
            }
            Text(
                "Dein Assistent ist für dich da: Er begleitet dich durch den Tag, sagt, was als Nächstes ansteht, bereitet Termine vor, " +
                    "schlägt Prioritäten und Nachbestellungen vor und hilft auf jeder Seite (Gesicht in der Leiste unten). " +
                    "Er übernimmt alles vom Tages-Motivator und ändert nichts ohne deinen Tipp. Aus: wieder der Tages-Motivator.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Name: $name", style = MaterialTheme.typography.labelLarge)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Assistant.NAME_SUGGESTIONS.forEach { n ->
                    FilterChip(selected = n == name, onClick = { vm.setAssistantName(n) }, label = { Text(n) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = own,
                    onValueChange = { own = it.take(24) },
                    label = { Text("Eigener Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(shape = buttonShape(), enabled = own.isNotBlank(), onClick = {
                    vm.setAssistantName(own)
                    own = ""
                }) { Text("Übernehmen") }
            }
            HorizontalDivider()
            Text("Aussehen und Sprechen", style = MaterialTheme.typography.labelLarge)
            var image by remember { mutableStateOf(AppPrefs.assistantImage(context)) }
            var talk by remember { mutableStateOf(AppPrefs.assistantTalk(context)) }
            var talkNews by remember { mutableStateOf(AppPrefs.assistantTalkNews(context)) }
            SwitchLine("Sunny-Bild statt gezeichnetem Gesicht", "Mit Gesichtsausdrücken passend zur Lage; beim Vorlesen blendet sich $name dezent als kleines Video ein.", image) {
                image = it
                AppPrefs.setAssistantImage(context, it)
                SunnyAvatar.useImage.value = it
            }
            var readHints by remember { mutableStateOf(AppPrefs.assistantReadHints(context)) }
            SwitchLine("Empfehlungen gleich vorlesen", "Erscheint eine Empfehlung (z. B. Unterstützung nach schlechter Stimmung oder Hinweise der KI), liest $name sie sofort vor. Einen Knopf „🔊 Vorlesen“ gibt es dort immer.", readHints) {
                readHints = it
                AppPrefs.setAssistantReadHints(context, it)
            }
            SwitchLine("$name erzählt von sich aus", "Beim Öffnen der Heute-Seite liest $name höchstens alle 3 Stunden kurz vor, was ansteht – nie in der Ruhezeit. Stumm geschaltet? Dann bleibt es still.", talk) {
                talk = it
                AppPrefs.setAssistantTalk(context, it)
            }
            SwitchLine("Neuigkeiten einbeziehen", "Eine aktuelle Schlagzeile zu Pflege, Gesetzen oder deinen Themen (Google News, nur das Suchwort wird gesendet). Nur ein Anhaltspunkt, keine Beratung.", talkNews) {
                talkNews = it
                AppPrefs.setAssistantTalkNews(context, it)
            }
            var sunnyUpd by remember { mutableStateOf(AppPrefs.sunnyUpdates(context)) }
            SwitchLine("$name kümmert sich um Updates", "Gibt es eine neue Version, sagt $name Bescheid und fragt, ob sie sie laden und installieren soll. Bei „Ja“ lädt sie selbst und startet die Installation – Android fragt zum Schluss immer noch einmal nach.", sunnyUpd) {
                sunnyUpd = it
                AppPrefs.setSunnyUpdates(context, it)
            }
            HorizontalDivider()
            SunnyModelSettings()
            HorizontalDivider()
            SunnyPersonalitySettings()
            HorizontalDivider()
            SteuerwerkSettings(vm, name)
            HorizontalDivider()
            var tourAuto by remember { mutableStateOf(AppPrefs.tourAutoRead(context)) }
            SwitchLine("Rundgang mit Vorlesen", "Beim Rundgang (Einstellungen → Hilfe) liest $name jede Seite vor und blättert von selbst weiter. Im Rundgang jederzeit „⏸ Anhalten“ oder „▶ Mit Vorlesen“.", tourAuto) {
                tourAuto = it
                AppPrefs.setTourAutoRead(context, it)
            }
            HorizontalDivider()
            AiModeChooser(name)
            HorizontalDivider()
            VoiceSettings(name)
            HorizontalDivider()
            PopupSettings(name, image)
            HorizontalDivider()
            WakeSettings(name)
            HorizontalDivider()
            LeaveSettings(name)
            HorizontalDivider()
            WeatherSettings(name)
            HorizontalDivider()
            BirthdaySettings(name)
            HorizontalDivider()
            Text("Benachrichtigungen von $name", style = MaterialTheme.typography.labelLarge)
            OptionalTimeRow(
                label = "Morgenüberblick",
                hint = "Was heute ansteht und was für Termine bereitliegen sollte",
                minute = morning,
                default = 7 * 60 + 30,
            ) {
                morning = it
                AppPrefs.setAssistantMorning(context, it)
                de.meinhelfer.tag.reminder.ReminderScheduler.scheduleAssistant(context)
            }
            OptionalTimeRow(
                label = "Abend",
                hint = "Was geschafft ist, was offen bleibt, was du für morgen bereitlegen kannst",
                minute = evening,
                default = 20 * 60,
            ) {
                evening = it
                AppPrefs.setAssistantEvening(context, it)
                de.meinhelfer.tag.reminder.ReminderScheduler.scheduleAssistant(context)
            }
            Text(
                "Bei Terminerinnerungen hängt $name eine kurze Checkliste an (z. B. Versichertenkarte, Medikamentenplan). Die Ruhezeit stellst du unter Erinnerungen ein.",
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Meine eigenen Werte nutzen (Beta)", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Schlaf, Schritte und Ruhepuls von deiner Uhr (Health Connect) für Hinweise nur für dich. Wird nur gelesen, nicht gespeichert.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = ownValues, onCheckedChange = {
                    ownValues = it
                    AppPrefs.setAssistantOwnHealth(context, it)
                    if (it && de.meinhelfer.tag.health.HealthConnectReader.available(context)) {
                        ownLauncher.launch(de.meinhelfer.tag.health.HealthConnectReader.OWN_PERMISSIONS)
                    }
                    vm.refreshOwnHealth(force = true)
                })
            }
            if (ownValues && !de.meinhelfer.tag.health.HealthConnectReader.available(context)) {
                Text("Health Connect ist auf diesem Handy nicht verfügbar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Uhrzeit mit Schalter (aus = -1). */
@Composable
internal fun OptionalTimeRow(label: String, hint: String, minute: Int, default: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label + if (minute >= 0) " um ${AssistantBrief.hm(minute)} Uhr" else " (aus)", style = MaterialTheme.typography.bodyLarge)
                Text(hint, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = minute >= 0, onCheckedChange = { onChange(if (it) default else -1) })
        }
        if (minute >= 0) {
            TimePickerField(
                label = "Uhrzeit",
                time = java.time.LocalTime.of(minute / 60, minute % 60),
                onChange = { t -> onChange(t.hour * 60 + t.minute) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Einstellungen → Erinnerungen: alle Erinnerungen auf einen Blick, dazu die Ruhezeit. */
@Composable
fun ReminderOverviewCard(vm: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val meds by vm.allMedications.collectAsStateWithLifecycle()
    val todos by vm.allTodos.collectAsStateWithLifecycle()
    val assistant by vm.assistantOn.collectAsStateWithLifecycle()
    val name by vm.assistantName.collectAsStateWithLifecycle()
    var quietOn by remember { mutableStateOf(AppPrefs.quietEnabled(context)) }
    var from by remember { mutableStateOf(AppPrefs.quietFrom(context)) }
    var to by remember { mutableStateOf(AppPrefs.quietTo(context)) }
    fun save() = AppPrefs.setQuiet(context, quietOn, from, to)
    val slots = meds.sumOf { it.timeList().size }
    val planned = todos.count { !it.done && !it.archived && (it.remindAt ?: 0L) > System.currentTimeMillis() }
    val drink = AppPrefs.drinkReminder(context)
    val diary = AppPrefs.diaryReminder(context)
    val morning = AppPrefs.assistantMorning(context)
    val evening = AppPrefs.assistantEvening(context)
    fun quietHit(m: Int) = quietOn && m >= 0 && AssistantBrief.inQuiet(m, from, to)
    val rows = listOf(
        "💊 Medikamente" to "$slots Einnahmezeiten, immer (auch in der Ruhezeit)" +
            if (AppPrefs.followupNotify(context)) "; Nachfrage nach ${AppPrefs.intakeWindow(context)} Min., wenn nicht abgehakt" else "",
        "📅 Termine" to "je Termin einstellbar, immer" + if (assistant) "; mit Checkliste von $name" else "",
        "✅ Aufgaben" to if (planned == 0) "keine Erinnerung geplant (beim Bearbeiten einer Aufgabe einstellbar)" else "$planned geplant",
        "🙂 Stimmungs-Check" to if (AppPrefs.moodNotify(context)) "an (Häufigkeit unter Heute-Seite, Motivator und Widget)" else "aus",
        "💧 Trinken" to when (drink) { 2 -> "alle 2 Stunden (10–18 Uhr)"; 3 -> "alle 3 Stunden (10–19 Uhr)"; else -> "aus" },
        "📓 Pflegetagebuch" to if (diary >= 0) "um ${AssistantBrief.hm(diary)} Uhr" else "aus",
        "🌅 $name morgens" to if (!assistant) "Assistent aus" else if (morning >= 0) "um ${AssistantBrief.hm(morning)} Uhr" + (if (quietHit(morning)) " – liegt in der Ruhezeit!" else "") else "aus",
        "🌙 $name abends" to if (!assistant) "Assistent aus" else if (evening >= 0) "um ${AssistantBrief.hm(evening)} Uhr" + (if (quietHit(evening)) " – liegt in der Ruhezeit!" else "") else "aus",
    )
    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Alle Erinnerungen auf einen Blick", style = MaterialTheme.typography.titleMedium)
            rows.forEach { (label, value) ->
                Column {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    Text(value, style = MaterialTheme.typography.bodySmall, color = if (value.endsWith("!")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ruhezeit", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "In dieser Zeit melden sich Trinken, Stimmungs-Check und $name nicht. Medikamente, Termine, Aufgaben und das Pflegetagebuch kommen immer.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = quietOn, onCheckedChange = {
                    quietOn = it
                    save()
                })
            }
            if (quietOn) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimePickerField(
                        label = "Von",
                        time = java.time.LocalTime.of(from / 60, from % 60),
                        onChange = { t ->
                            from = t.hour * 60 + t.minute
                            save()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    TimePickerField(
                        label = "Bis",
                        time = java.time.LocalTime.of(to / 60, to % 60),
                        onChange = { t ->
                            to = t.hour * 60 + t.minute
                            save()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Anweisung für die KI, wenn „Frag mich“ mit KI beantwortet wird. */
internal fun assistantAiSystem(name: String): String =
    "Du bist $name, der persönliche Assistent in der Android-App „Mein Helfer für den Tag“ für pflegende Angehörige. " +
        "Antworte freundlich, in einfacher Sprache, in der Du-Form und kurz (höchstens sechs Sätze). " +
        "Fragen zur Bedienung der App beantwortest du nur anhand der mitgeschickten Abschnitte der Anleitung; erfinde keine Funktionen. " +
        "Steht die Antwort nicht darin, sag das ehrlich und nenne, wo man in der App nachsehen kann. " +
        "Allgemeine Fragen zur Pflege darfst du kurz allgemein beantworten, ausdrücklich als Anhaltspunkt, und auf Pflegestützpunkt, Pflegekasse oder Beratungsstellen verweisen. " +
        "Gib keine medizinischen Ratschläge, keine Diagnosen und keine Rechts- oder Finanzberatung."

/** Frage und passende Abschnitte der Anleitung (ohne Daten aus der App). */
internal fun assistantAiPrompt(question: String, items: List<GuideItem>): String = buildString {
    appendLine("Frage: " + question.trim().take(500))
    appendLine()
    appendLine("Passende Abschnitte der Anleitung:")
    var size = 0
    items.distinctBy { it.title }.forEach { item ->
        val block = "- " + item.title + ": " + item.text
        if (size + block.length > 7000) return@forEach
        appendLine(block)
        size += block.length
    }
}

@Composable
private fun SwitchLine(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Stimme: Geschwindigkeit (Regler), Sprachausgabe und Stimme, Probe. Gilt für alles Vorlesen in der App. */
@Composable
private fun VoiceSettings(name: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var rate by remember { mutableStateOf(AppPrefs.speechRate(context)) }
    Text("Stimme", style = MaterialTheme.typography.labelLarge)
    Text(
        "Gilt für $name und für alles Vorlesen in der App (Tagesplan, Dokumente, Anleitung). Gesprochen wird mit der Sprachausgabe des Handys.",
        style = MaterialTheme.typography.bodySmall,
    )
    Text("Geschwindigkeit: " + rateLabel(rate) + " (" + "%.1f".format(java.util.Locale.GERMANY, rate) + "×)", style = MaterialTheme.typography.bodyMedium)
    androidx.compose.material3.Slider(
        value = rate,
        onValueChange = { rate = (kotlin.math.round(it * 20f) / 20f) },
        onValueChangeFinished = { AppPrefs.setSpeechRate(context, rate) },
        valueRange = 0.5f..2.5f,
    )
    Row {
        Text("langsam", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text("sehr schnell", style = MaterialTheme.typography.bodySmall)
    }
    VoiceChooser()
    SpeakButton(owner = "assistent-probe", label = "🔊 Probe hören", modifier = Modifier.fillMaxWidth()) {
        "Hallo, hier ist $name. So klinge ich in dieser Geschwindigkeit. Ich lese dir vor, was heute ansteht."
    }
}

private fun rateLabel(r: Float) = when {
    r < 0.8f -> "langsam"
    r < 1.1f -> "normal"
    r < 1.5f -> "schnell"
    r < 2.0f -> "sehr schnell"
    else -> "am schnellsten"
}

/** Einblendung beim Sprechen: wann, wo, wie groß, Deckkraft, Rand und Hintergrund, mit Vorschau. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PopupSettings(name: String, image: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val style by SunnyAvatar.popup.collectAsState()
    fun set(s: SunnyPopup) {
        SunnyAvatar.popup.value = s
        AppPrefs.setSunnyPopup(context, s)
    }
    Text("Einblendung beim Sprechen", style = MaterialTheme.typography.labelLarge)
    if (!image) {
        Text("Nur mit dem Sunny-Bild (Schalter oben).", style = MaterialTheme.typography.bodySmall)
        return
    }
    @Composable
    fun chips(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, o -> FilterChip(selected = selected == i, onClick = { onPick(i) }, label = { Text(o) }) }
        }
    }
    chips("Wann", SunnyPopup.SHOWS, style.show) { set(style.copy(show = it)) }
    Text(
        "Standard: $name erscheint bei allem, was vorgelesen wird – auch bei Tagesplan, Dokumenten, Anleitung und Empfehlungen – und spricht mit.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (style.show == 0) return
    chips("Wo", SunnyPopup.PLACES, style.place) { set(style.copy(place = it)) }
    Text("Größe: ${style.size} dp", style = MaterialTheme.typography.bodyMedium)
    var size by remember(style.size) { mutableStateOf(style.size.toFloat()) }
    androidx.compose.material3.Slider(
        value = size,
        onValueChange = { size = it },
        onValueChangeFinished = { set(style.copy(size = size.toInt())) },
        valueRange = SunnyPopup.MIN_SIZE.toFloat()..SunnyPopup.MAX_SIZE.toFloat(),
    )
    Text("Deckkraft: ${(style.alpha * 100).toInt()} %", style = MaterialTheme.typography.bodyMedium)
    var alpha by remember(style.alpha) { mutableStateOf(style.alpha) }
    androidx.compose.material3.Slider(
        value = alpha,
        onValueChange = { alpha = it },
        onValueChangeFinished = { set(style.copy(alpha = alpha)) },
        valueRange = 0.3f..1f,
    )
    chips("Rand", SunnyPopup.FRAMES, style.frame) { set(style.copy(frame = it)) }
    chips("Hintergrund", SunnyPopup.BACKS, style.back) { set(style.copy(back = it)) }
    Text("Vorschau", style = MaterialTheme.typography.bodyMedium)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        SunnyBubble(style.copy(size = size.toInt(), alpha = alpha), Modifier.padding(8.dp), video = false)
    }
    SpeakButton(owner = "assistent-vorschau", label = "▶ So sieht es beim Sprechen aus", modifier = Modifier.fillMaxWidth()) {
        "Hier bin ich. So blende ich mich ein, wenn ich spreche. Antippen hält mich an."
    }
}

/** Wecker bei frühen Terminen: an/aus, was „früh“ ist und wie viel Zeit vorher. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WakeSettings(name: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var on by remember { mutableStateOf(AppPrefs.wakeOn(context)) }
    var early by remember { mutableStateOf(AppPrefs.wakeEarly(context)) }
    var lead by remember { mutableStateOf(AppPrefs.wakeLead(context)) }
    fun save() = AppPrefs.setWake(context, on, early, lead)
    SwitchLine(
        "⏰ Wecker bei frühen Terminen",
        "Am Vortag ab 14 Uhr (und im Abendüberblick) erinnert $name an frühe Termine und bietet an, den Wecker zu stellen – mit einem Tipp in der Uhr-App des Handys.",
        on,
    ) {
        on = it
        save()
    }
    if (!on) return
    Text("Früh heißt: Beginn vor", style = MaterialTheme.typography.bodyMedium)
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(8, 9, 10, 11, 12).forEach { h ->
            FilterChip(selected = early == h * 60, onClick = { early = h * 60; save() }, label = { Text("$h Uhr") })
        }
    }
    Text("Wecken vor dem Termin", style = MaterialTheme.typography.bodyMedium)
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(45, 60, 90, 120, 150).forEach { m ->
            FilterChip(selected = lead == m, onClick = { lead = m; save() }, label = { Text(if (m % 60 == 0) "${m / 60} Std." else if (m > 60) "${m / 60}½ Std." else "$m Min.") })
        }
    }
}

/** Erinnerung ans Losfahren: an/aus, Standard-Wegzeit und Puffer. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LeaveSettings(name: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var on by remember { mutableStateOf(AppPrefs.leaveOn(context)) }
    var minutes by remember { mutableStateOf(AppPrefs.leaveMinutes(context)) }
    var buffer by remember { mutableStateOf(AppPrefs.leaveBuffer(context)) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun save() {
        AppPrefs.setLeave(context, on, minutes, buffer)
        scope.launch { de.meinhelfer.tag.reminder.ReminderScheduler.rescheduleAll(context) }
    }
    SwitchLine(
        "🚗 Erinnerung ans Losfahren",
        "Vor Terminen mit Ort oder Kontakt sagt $name rechtzeitig Bescheid, mit Wetter (wenn eingeschaltet) und „Route“ für die Karten-App. Die Wegzeit je Kontakt trägst du beim Kontakt ein (Bearbeiten → Wegzeit).",
        on,
    ) {
        on = it
        save()
    }
    if (!on) return
    Text("Wegzeit, wenn beim Kontakt nichts steht", style = MaterialTheme.typography.bodyMedium)
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(15, 20, 30, 45, 60).forEach { m -> FilterChip(selected = minutes == m, onClick = { minutes = m; save() }, label = { Text("$m Min.") }) }
    }
    Text("Erinnern so viel früher", style = MaterialTheme.typography.bodyMedium)
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(5, 10, 15, 20).forEach { m -> FilterChip(selected = buffer == m, onClick = { buffer = m; save() }, label = { Text("$m Min.") }) }
    }
}

/** Wetter vor Terminen: an/aus und Ort (nur der Ort wird an Open-Meteo gesendet). */
@Composable
private fun WeatherSettings(name: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var on by remember { mutableStateOf(AppPrefs.weatherOn(context)) }
    var place by remember { mutableStateOf(AppPrefs.weatherPlace(context)) }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    SwitchLine(
        "🌦 Wetter vor Terminen",
        "$name nennt bei Terminen Regen, Schnee, Glätte, Kälte oder Hitze und was mit sollte. Dafür wird nur der Ort an Open-Meteo gesendet (frei, ohne Konto), nie Termine oder Namen. Nur ein Anhaltspunkt.",
        on,
    ) {
        on = it
        AppPrefs.setWeather(context, it)
        if (it) scope.launch { de.meinhelfer.tag.info.Weather.refresh(context, force = true) }
    }
    if (!on) return
    Text(if (place.isBlank()) "Noch kein Ort gewählt." else "Ort: $place", style = MaterialTheme.typography.bodyMedium)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it.take(60) },
            label = { Text("Ort oder PLZ") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(shape = buttonShape(), enabled = input.isNotBlank(), onClick = {
            status = "Suche …"
            scope.launch {
                de.meinhelfer.tag.info.Weather.find(context, input).onSuccess { (label, lat, lon) ->
                    AppPrefs.setWeatherPlace(context, label, lat, lon)
                    place = label
                    input = ""
                    de.meinhelfer.tag.info.Weather.refresh(context, force = true)
                    status = if (AppPrefs.weatherData(context).isNotBlank()) "Vorhersage geladen." else "Ort gespeichert, Vorhersage folgt."
                }.onFailure { status = "Nicht gefunden oder keine Verbindung (" + (it.message ?: "") + "). Versuche den Ortsnamen ohne Zusatz oder die PLZ." }
            }
        }) { Text("Übernehmen") }
    }
    status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

/** Geburtstage: an/aus und eigene Einträge; die der betreuten Personen kommen aus dem Profil. */
@Composable
private fun BirthdaySettings(name: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var on by remember { mutableStateOf(AppPrefs.birthdaysOn(context)) }
    var list by remember { mutableStateOf(AppPrefs.birthdayList(context)) }
    SwitchLine(
        "🎂 Geburtstage",
        "Eine Woche vorher schlägt $name vor, Karte oder Geschenk zu besorgen, am Tag selbst erinnert $name ans Gratulieren. Die Geburtstage der betreuten Personen kommen aus ihrem Profil.",
        on,
    ) {
        on = it
        AppPrefs.setBirthdays(context, it, list)
    }
    if (!on) return
    OutlinedTextField(
        value = list,
        onValueChange = {
            list = it.take(2000)
            AppPrefs.setBirthdays(context, true, list)
        },
        label = { Text("Weitere Geburtstage (eine Zeile je Person)") },
        placeholder = { Text("Oma Erna, 14.03.1936\nNachbarin Gabi, 02.07.") },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
    val n = de.meinhelfer.tag.summary.Birthdays.parse(list).size
    val lines = list.lines().count { it.isNotBlank() }
    if (lines > n) Text("${lines - n} Zeile(n) nicht erkannt – Format: Name, TT.MM. oder TT.MM.JJJJ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

// ---------------------------------------------------------------- Stufe 3: Sunny sagt per KI etwas zur Seite

/** Was gerade auf der Seite zu sehen ist, als Text für die KI (Namen werden vor dem Senden entfernt). */
@Composable
private fun pageContext(page: String, vm: MainViewModel, st: AssistantState): String {
    val meds by vm.medications.collectAsStateWithLifecycle()
    val appointments by vm.appointments.collectAsStateWithLifecycle()
    val documents by vm.documents.collectAsStateWithLifecycle()
    val vitals by vm.vitals.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    val note = pageNote(page, vm, st)
    val detail = when (page) {
        "medikamente", "medikamentenplan", "nachbestellung" -> meds.take(15).joinToString("\n") { m ->
            "- " + m.name + (m.doseSummary()?.let { " ($it)" } ?: "") + (m.daysLeft(today)?.let { ", Vorrat noch etwa $it Tage" } ?: "")
        }
        "termine" -> appointments.filter { it.whenMillis >= System.currentTimeMillis() }.sortedBy { it.whenMillis }.take(6).joinToString("\n") { a ->
            val d = Instant.ofEpochMilli(a.whenMillis).atZone(zone).toLocalDate().toEpochDay()
            "- ${Tageslage.dayWord(d, today)} ${Tageslage.time(a.whenMillis, zone)} Uhr: ${a.title}"
        } + st.preps.take(2).joinToString("") { "\nVorbereitung ${it.appointment.title}: " + it.hints.joinToString("; ") }
        "dokumente" -> documents.sortedByDescending { it.createdMillis }.take(8).joinToString("\n") { "- ${it.category}: ${it.title}" }
        "werte", "tagebuch" -> VitalKind.MEASURES.mapNotNull { k ->
            val list = vitals.filter { it.kind == k.key && it.day > today - 14 }
            if (list.isEmpty()) null else "- ${k.label} (14 Tage): ${list.size} Werte, " +
                "min ${de.meinhelfer.tag.summary.Vitals.number(list.minOf { it.value }, k.decimals)}, max ${de.meinhelfer.tag.summary.Vitals.number(list.maxOf { it.value }, k.decimals)}, " +
                "zuletzt ${de.meinhelfer.tag.summary.Vitals.number(list.maxBy { it.atMillis }.value, k.decimals)} ${k.unit}"
        }.joinToString("\n")
        else -> (st.line.take(5).map { "- " + it.title } + st.proposals.take(3).map { "- Vorschlag: " + it.text }).joinToString("\n")
    }
    return "Seite: ${PAGE_NAMES[page] ?: page}\nKurzlage: $note" + if (detail.isNotBlank()) "\n$detail" else ""
}

internal fun screenSystem(name: String) =
    "Du bist $name, die persönliche Assistentin in der App „Mein Helfer für den Tag“ für pflegende Angehörige. " +
        "Du bekommst, was gerade auf einer Seite der App zu sehen ist (ohne Namen). Sag dazu in höchstens drei kurzen Sätzen etwas Hilfreiches: " +
        "eine Auffälligkeit, eine Erinnerung oder einen praktischen Tipp für den Alltag. Du-Form, freundlich, einfache Sprache, keine Aufzählung. " +
        "Keine Diagnosen, keine Dosierungen, rate nie, Medikamente zu ändern; bei Gesundheitsfragen auf Hausarzt oder Apotheke verweisen. " +
        "Wenn nichts auffällt, sag etwas kurz Ermutigendes. " +
        "Schreibe für eine Vorlesestimme: natürliche gesprochene Sprache, wie man es einem Menschen sagt; keine Abkürzungen, keine Klammern, " +
        "keine Emojis, keine Aufzählungszeichen; Uhrzeiten und Einheiten ausgeschrieben, z. B. „acht Uhr dreißig“, „Milligramm“. " +
        de.meinhelfer.tag.summary.SpeechVariants.FORMAT

object ScreenComment {
    private val asked = java.util.concurrent.ConcurrentHashMap<String, Long>()
    val last = kotlinx.coroutines.flow.MutableStateFlow<Pair<String, String>?>(null)

    fun allowed(context: android.content.Context) =
        de.meinhelfer.tag.ai.AiMode.skipConfirm(context) && AppPrefs.screenTalkOn(context) && de.meinhelfer.tag.ai.AiConfig.isConfigured(context) &&
            de.meinhelfer.tag.ai.AiTask.enabled(context, de.meinhelfer.tag.ai.AiTask.Seite)

    private val turns = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /**
     * KI fragen (gleiche Seite mit gleichen Daten kommt aus dem Zwischenspeicher), Kurzform reihum aus den Varianten
     * sprechen (oder die ausführliche Fassung), ausführliche Fassung im Blatt zeigen.
     */
    suspend fun ask(context: android.content.Context, name: String, page: String, data: String, fallback: String = ""): Result<String> {
        asked[page] = System.currentTimeMillis()
        val result = askAi(context, name, page, data)
        // Ohne Verbindung oder bei Limit: Sunny liest selbst vor, was sie auf der Seite sieht
        if (result.isFailure && fallback.isNotBlank() && !de.meinhelfer.tag.ai.AiMode.isOff(context)) {
            val text = de.meinhelfer.tag.ai.SunnyMemory.say(context, "offline", "Ohne Verbindung sage ich dir, was ich hier sehe.") + " " + fallback
            last.value = page to text
            SunnyAvatar.caption.value = fallback
            Speech.speak(context, "assistent-seite", text)
        }
        return result
    }

    private suspend fun askAi(context: android.content.Context, name: String, page: String, data: String): Result<String> {
        return de.meinhelfer.tag.ai.AiClient.ask(context, screenSystem(name), data, 700, de.meinhelfer.tag.ai.AiTask.Seite).map { answer ->
            val parsed = de.meinhelfer.tag.summary.SpeechVariants.parse(answer)
            val turn = turns.merge(page, 1, Int::plus)!! - 1
            val spoken = de.meinhelfer.tag.summary.SpeechVariants.pick(parsed, turn, de.meinhelfer.tag.ai.AiTask.speakLong(context))
            last.value = page to parsed.long
            SunnyAvatar.caption.value = spoken
            Speech.speak(context, "assistent-seite", spoken)
            spoken
        }
    }

    /** Automatisch höchstens alle 30 Minuten je Seite. */
    fun due(page: String) = System.currentTimeMillis() - (asked[page] ?: 0L) > 30 * 60 * 1000L
}

/** Ohne Oberfläche: kommentiert beim Seitenwechsel automatisch, wenn in Stufe 3 so eingestellt. */
@Composable
fun ScreenCommentAuto(vm: MainViewModel, page: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val st = rememberAssistantState(vm)
    val data = pageContext(page, vm, st)
    val note = pageNote(page, vm, st)
    androidx.compose.runtime.LaunchedEffect(page) {
        // Abends den Sprachschatz aus den Gesprächen des Tages erweitern (einmal am Tag, Stufe 3)
        runCatching { de.meinhelfer.tag.ai.SunnyMemory.consolidateIfDue(context) }
        if (!ScreenComment.allowed(context) || !AppPrefs.screenTalkAuto(context) || AppPrefs.isQuietNow(context)) return@LaunchedEffect
        kotlinx.coroutines.delay(2500)
        if (!ScreenComment.due(page) || Speech.speaking.value != null) return@LaunchedEffect
        // Tageslimit für automatische Kommentare (schont das freie Kontingent)
        if (de.meinhelfer.tag.ai.AiUsage.byTask(context, de.meinhelfer.tag.ai.AiTask.Seite) >= de.meinhelfer.tag.ai.AiTask.autoLimit(context)) return@LaunchedEffect
        ScreenComment.ask(context, st.name, page, data, note)
    }
}


/** Fortschritt, solange Sunny ein Update lädt. */
@Composable
private fun SunnyUpdateStatus(vm: MainViewModel, name: String) {
    val upd by vm.updateState.collectAsStateWithLifecycle()
    val mine by vm.sunnyInstall.collectAsStateWithLifecycle()
    if (!mine) return
    when (val u = upd) {
        is de.meinhelfer.tag.update.UpdateState.Downloading -> {
            HorizontalDivider()
            Text("⬇️ $name lädt Version ${u.info.version} … ${(u.progress * 100).toInt()} %", style = MaterialTheme.typography.bodyMedium)
            androidx.compose.material3.LinearProgressIndicator(progress = { u.progress }, modifier = Modifier.fillMaxWidth())
        }
        is de.meinhelfer.tag.update.UpdateState.Ready -> {
            HorizontalDivider()
            Text("✅ Version ${u.info.version} ist geladen. Android fragt gleich nach – „Installieren“ tippen.", style = MaterialTheme.typography.bodyMedium)
        }
        else -> Unit
    }
}

/**
 * Sunny kümmert sich um Updates (läuft überall in der App):
 * sagt eine neue Version einmal an und startet nach dem Laden selbst die Installation.
 */
@Composable
fun SunnyUpdateAgent(vm: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val upd by vm.updateState.collectAsStateWithLifecycle()
    val mine by vm.sunnyInstall.collectAsStateWithLifecycle()
    var resumeTick by remember { mutableStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    val name = AppPrefs.assistantName(context)
    // Steuerwerk: bei „Machen und Bescheid sagen“ / „Still erledigen“ lädt Sunny selbst (installieren bleibt deine Entscheidung)
    androidx.compose.runtime.LaunchedEffect(upd) {
        val u = upd as? de.meinhelfer.tag.update.UpdateState.Available ?: return@LaunchedEffect
        if (!AppPrefs.sunnyUpdates(context)) return@LaunchedEffect
        if (AppPrefs.autoLevel(context, de.meinhelfer.tag.summary.AutoArea.Updates) == de.meinhelfer.tag.summary.AutoLevel.Ask) return@LaunchedEffect
        if (AppPrefs.autoLoadedVersion(context) == u.info.version) return@LaunchedEffect
        AppPrefs.setAutoLoadedVersion(context, u.info.version)
        vm.downloadUpdate()
    }
    androidx.compose.runtime.LaunchedEffect(upd) {
        val u = upd as? de.meinhelfer.tag.update.UpdateState.Ready ?: return@LaunchedEffect
        if (AppPrefs.autoLoadedVersion(context) != u.info.version || mine) return@LaunchedEffect
        if (AppPrefs.sunnyUpdateAnnounced(context) == "geladen:" + u.info.version) return@LaunchedEffect
        AppPrefs.setSunnyUpdateAnnounced(context, "geladen:" + u.info.version)
        AppPrefs.addJournal(context, "update", "Version ${u.info.version} von selbst geladen – wartet auf „Jetzt installieren“.")
        vm.bumpAuto()
        if (AppPrefs.autoLevel(context, de.meinhelfer.tag.summary.AutoArea.Updates) == de.meinhelfer.tag.summary.AutoLevel.Report) {
            vm.reportAuto("Ich habe die neue Version ${u.info.version} schon geladen. Wenn du magst, tipp in meiner Karte auf „Jetzt installieren“.")
        }
    }
    // Berichte vorlesen (einmal je Bericht, nicht in der Ruhezeit, nicht bei stummem Handy)
    val report by vm.autoReport.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(report) {
        val r = report ?: return@LaunchedEffect
        if (AppPrefs.isQuietNow(context)) return@LaunchedEffect
        val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
        if (audio != null && audio.ringerMode != android.media.AudioManager.RINGER_MODE_NORMAL) return@LaunchedEffect
        kotlinx.coroutines.delay(3000)
        var waited = 0
        while (Speech.speaking.value != null && waited < 60) { kotlinx.coroutines.delay(1000); waited++ }
        Speech.speak(context, "assistent_steuer", r)
    }
    // Ansage: einmal pro Version, nicht in der Ruhezeit, nicht bei stummem Handy
    androidx.compose.runtime.LaunchedEffect(upd) {
        val u = upd as? de.meinhelfer.tag.update.UpdateState.Available ?: return@LaunchedEffect
        if (!AppPrefs.sunnyUpdates(context) || AppPrefs.sunnyUpdateAnnounced(context) == u.info.version) return@LaunchedEffect
        if (AppPrefs.autoLevel(context, de.meinhelfer.tag.summary.AutoArea.Updates) != de.meinhelfer.tag.summary.AutoLevel.Ask) return@LaunchedEffect
        if (AppPrefs.isQuietNow(context)) return@LaunchedEffect
        val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
        if (audio != null && audio.ringerMode != android.media.AudioManager.RINGER_MODE_NORMAL) return@LaunchedEffect
        kotlinx.coroutines.delay(2500)
        var waited = 0
        while (Speech.speaking.value != null && waited < 60) { kotlinx.coroutines.delay(1000); waited++ }
        AppPrefs.setSunnyUpdateAnnounced(context, u.info.version)
        Speech.speak(
            context, "assistent_update",
            "Es gibt eine neue Version von Mein Helfer, Version ${u.info.version}. Soll ich sie für dich herunterladen und installieren? Tipp dafür in meiner Karte auf „Ja, aktualisieren“.",
        )
    }
    // Nach dem Laden selbst installieren (auch nach Rückkehr aus der Freigabe)
    androidx.compose.runtime.LaunchedEffect(upd, mine, resumeTick) {
        if (!mine) return@LaunchedEffect
        when (val u = upd) {
            is de.meinhelfer.tag.update.UpdateState.Ready -> {
                if (de.meinhelfer.tag.update.UpdateChecker.canInstall(context)) {
                    vm.clearSunnyInstall()
                    AppPrefs.addJournal(context, "update", "Installation von Version ${u.info.version} gestartet.")
                    Speech.speak(context, "assistent_update", "Fertig geladen. Android fragt jetzt noch einmal – tipp bitte auf Installieren. Danach startet die App neu, deine Daten bleiben erhalten.")
                    kotlinx.coroutines.delay(1500)
                    runCatching { de.meinhelfer.tag.update.UpdateChecker.install(context, u.file) }
                } else if (AppPrefs.sunnyUpdateAnnounced(context) != "perm:" + u.info.version) {
                    AppPrefs.setSunnyUpdateAnnounced(context, "perm:" + u.info.version)
                    Speech.speak(context, "assistent_update", "Geladen. Android muss mir einmal erlauben, Updates zu installieren. Ich öffne die Einstellung – bitte „Zulassen“ einschalten und zurückkommen, dann mache ich weiter.")
                    kotlinx.coroutines.delay(2500)
                    de.meinhelfer.tag.update.UpdateChecker.openInstallPermissionSettings(context)
                }
            }
            is de.meinhelfer.tag.update.UpdateState.Error -> {
                vm.clearSunnyInstall()
                Speech.speak(context, "assistent_update", "Das Laden hat leider nicht geklappt: ${u.message} Du kannst es später in den Einstellungen unter App nochmal versuchen.")
            }
            else -> Unit
        }
    }
}


/** Bericht nach „Machen und Bescheid sagen“ und Zugang zum Protokoll. */
@Composable
private fun SunnyReportBox(vm: MainViewModel, name: String) {
    val report by vm.autoReport.collectAsStateWithLifecycle()
    var showJournal by remember { mutableStateOf(false) }
    report?.let { r ->
        HorizontalDivider()
        Text("📝 $r", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { vm.clearAutoReport() }) { Text("OK") }
    }
    TextButton(onClick = { showJournal = true }) { Text("📝 Was $name gemacht hat") }
    if (showJournal) SunnyJournalDialog(vm, name) { showJournal = false }
}

/** Protokoll der letzten 7 Tage. */
@Composable
fun SunnyJournalDialog(vm: MainViewModel, name: String, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tick by vm.autoTick.collectAsStateWithLifecycle()
    val entries = remember(tick) { AppPrefs.journal(context).reversed() }
    val fmt = remember { java.time.format.DateTimeFormatter.ofPattern("EE dd.MM. HH:mm", java.util.Locale.GERMAN) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Was $name gemacht hat") },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Die letzten 7 Tage. Bleibt nur auf diesem Handy.", style = MaterialTheme.typography.bodySmall)
                if (entries.isEmpty()) Text("Noch nichts.", style = MaterialTheme.typography.bodyMedium)
                entries.forEach { e ->
                    val t = java.time.Instant.ofEpochMilli(e.millis).atZone(java.time.ZoneId.systemDefault()).format(fmt)
                    Text(t, style = MaterialTheme.typography.labelSmall)
                    Text(e.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Schließen") } },
        dismissButton = {
            TextButton(onClick = {
                Speech.speak(context, "assistent_protokoll", if (entries.isEmpty()) "Ich habe noch nichts gemacht." else entries.take(8).joinToString(" ") { it.text })
            }) { Text("🔊 Vorlesen") }
        },
    )
}

/** Einstellungen: wie selbstständig Sunny je Bereich sein darf, was sie gelernt hat, Protokoll. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SteuerwerkSettings(vm: MainViewModel, name: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tick by vm.autoTick.collectAsStateWithLifecycle()
    var showJournal by remember { mutableStateOf(false) }
    Text("⚙️ Steuerwerk: Wie selbstständig darf $name sein?", style = MaterialTheme.typography.titleSmall)
    Text(
        "Je Bereich: nur fragen, machen und danach Bescheid sagen, oder still erledigen. Was Daten ändert oder etwas sendet, fragt $name immer. " +
            "Sagst du dreimal „Nein, danke“ zu einer Art Vorschlag, fragt sie eine Woche lang nicht mehr; sagst du immer Ja, bietet sie mehr Selbstständigkeit an.",
        style = MaterialTheme.typography.bodySmall,
    )
    de.meinhelfer.tag.summary.AutoArea.entries.forEach { area ->
        val current = remember(tick) { AppPrefs.autoLevel(context, area) }
        Text(area.title, style = MaterialTheme.typography.labelLarge)
        Text(area.text, style = MaterialTheme.typography.bodySmall)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            area.levels.forEach { l ->
                FilterChip(
                    selected = l == current,
                    onClick = {
                        AppPrefs.setAutoLevel(context, area, l)
                        AppPrefs.addJournal(context, area.key, "„${area.title}“ steht jetzt auf „${l.label}“ (von dir eingestellt).")
                        vm.bumpAuto()
                        if (area == de.meinhelfer.tag.summary.AutoArea.Cleanup) vm.refreshCleanup()
                    },
                    label = { Text(l.label) },
                )
            }
        }
    }
    val now = System.currentTimeMillis()
    val paused = remember(tick) { AppPrefs.autoStats(context).filter { de.meinhelfer.tag.summary.Steuerwerk.paused(it.value, now) } }
    if (paused.isNotEmpty()) {
        Text("Gerade Pause (zu oft abgelehnt): " + paused.keys.joinToString { de.meinhelfer.tag.summary.Steuerwerk.categoryName(it) }, style = MaterialTheme.typography.bodySmall)
    }
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(shape = buttonShape(), onClick = { showJournal = true }) { Text("📝 Protokoll") }
        TextButton(onClick = {
            AppPrefs.resetAutoStats(context)
            AppPrefs.addJournal(context, "steuer", "Gelerntes zurückgesetzt – ich frage wieder zu allem.")
            vm.bumpAuto()
        }) { Text("Gelerntes zurücksetzen") }
    }
    if (showJournal) SunnyJournalDialog(vm, name) { showJournal = false }
}


@Composable
private fun SunnyPersonalitySettings() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var profile by remember { mutableStateOf(de.meinhelfer.tag.ai.SunnyPreferences.load(context)) }
    fun save(value: de.meinhelfer.tag.ai.SunnyPreferences.Profile) {
        de.meinhelfer.tag.ai.SunnyPreferences.save(context, value)
        profile = de.meinhelfer.tag.ai.SunnyPreferences.load(context)
    }
    Text("Persönlichkeit und Gedächtnis", style = MaterialTheme.typography.labelLarge)
    SwitchLine("Vorlieben lokal merken", "Nur deine ausgewählten Einstellungen, keine Gesprächsprotokolle.", profile.enabled) {
        save(profile.copy(enabled = it))
    }
    if (profile.enabled) {
        SwitchLine("Motivierende Aussagen", "Persönliche Ermutigung nach der Stimmungsabfrage.", profile.encouragement) {
            save(profile.copy(encouragement = it))
        }
        SwitchLine("Entlastungsvorschläge", "Freiwillige Alltagstipps nach der Stimmungsabfrage.", profile.relief) {
            save(profile.copy(relief = it))
        }
        TextButton(onClick = { save(de.meinhelfer.tag.ai.SunnyPreferences.Profile()) }) {
            Text("Gespeicherte Vorlieben löschen")
        }
    }
}


@Composable
private fun SunnyModelSettings() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var bytes by remember { mutableStateOf(de.meinhelfer.tag.ai.SunnyModelStore.installedBytes(context)) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("Hallo Sunny, stell dich kurz vor.") }
    var answer by remember { mutableStateOf("") }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && !busy) {
            busy = true
            scope.launch {
                try {
                    bytes = de.meinhelfer.tag.ai.SunnyModelStore.importModel(context, uri)
                    status = "Modell gespeichert. Bitte lokal testen."
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { status = "Import fehlgeschlagen: " + (e.message ?: "") }
                finally { busy = false }
            }
        }
    }
    Text("Sunny Brain – lokales KI-Modell", style = MaterialTheme.typography.labelLarge)
    Text(if (bytes > 0) "Gespeichert: ${bytes / (1024 * 1024)} MB" else "Kein Modell gespeichert.",
        style = MaterialTheme.typography.bodySmall)
    Text("LiteRT-LM: lokaler Test ohne Internet. Bitte keine sensiblen Daten eingeben.", style = MaterialTheme.typography.bodySmall)
    OutlinedButton(enabled = !busy, onClick = { picker.launch(arrayOf("application/octet-stream", "*/*")) }) {
        Text(if (busy) "Bitte warten…" else "Modelldatei importieren")
    }
    if (bytes > 0) TextButton(enabled = !busy, onClick = {
        busy = true
        scope.launch {
            try {
                if (de.meinhelfer.tag.ai.SunnyModelStore.delete(context)) { bytes = 0L; status = "Modell gelöscht." }
                else status = "Löschen fehlgeschlagen."
            } finally { busy = false }
        }
    }) { Text("Modell löschen") }
    if (bytes > 0) {
        androidx.compose.material3.OutlinedTextField(
            value = question, onValueChange = { if (it.length <= 1000) question = it },
            enabled = !busy, label = { Text("Lokale Testfrage") }
        )
        OutlinedButton(enabled = !busy && question.isNotBlank(), onClick = {
            busy = true
            answer = ""
            status = "Lokales Modell wird geladen…"
            val prompt = question
            scope.launch {
                try {
                    answer = de.meinhelfer.tag.ai.SunnyLiteRtTest.answer(context, prompt)
                    status = "Lokale Antwort erhalten."
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { status = "KI-Test fehlgeschlagen: " + (e.message ?: "") }
                catch (e: LinkageError) { status = "KI-Laufzeit auf diesem Gerät nicht verfügbar." }
                finally { busy = false }
            }
        }) { Text("Offline-KI testen") }
        if (answer.isNotBlank()) Text(answer)
    }
    if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
}
