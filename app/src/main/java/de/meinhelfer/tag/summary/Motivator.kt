package de.meinhelfer.tag.summary

import de.meinhelfer.tag.data.Appointment
import de.meinhelfer.tag.data.CaregiverProfile
import de.meinhelfer.tag.data.CaregiverRole
import de.meinhelfer.tag.data.Checkin
import de.meinhelfer.tag.data.Goal
import de.meinhelfer.tag.data.GoalStep
import de.meinhelfer.tag.data.MoodEntry
import de.meinhelfer.tag.data.Todo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Stimmung eines Tages (Durchschnitt aller Eintraege). */
data class MoodDay(val day: Long, val avg: Double?, val count: Int)

/** Wie der Weg zu einem Ziel laeuft (lokal berechnet). */
enum class Pace(val label: String) {
    NoSteps("Noch keine Schritte"),
    Ahead("Schneller als geplant"),
    OnTrack("Im Plan"),
    Behind("Etwas hinterher"),
    FarBehind("Deutlich hinterher"),
    Done("Alle Schritte erledigt"),
}

data class GoalProgress(
    val goal: Goal,
    val steps: List<GoalStep>,
    val done: Int,
    val total: Int,
    val next: GoalStep?,
    val overdue: Int,
    val daysSinceProgress: Int,
    val pace: Pace,
    val tip: String,
) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

/** Ein Vorhaben fuer heute. Schritte und Aufgaben werden beim Abhaken wirklich erledigt. */
data class Intention(
    val key: String,
    val text: String,
    val done: Boolean,
    val stepId: Long? = null,
    val todoId: Long? = null,
)

enum class SupportLevel { None, Relief, Strong }

data class MotivatorPlan(
    val day: Long,
    val greeting: String,
    val sentence: String,
    val achieved: List<String>,
    val moodDays: List<MoodDay>,
    val moodAvg: Double?,
    /** -1 = gesunken, 0 = gleich, 1 = gestiegen */
    val moodTrend: Int,
    val lastMood: MoodEntry?,
    val moodToday: Boolean,
    val intentions: List<Intention>,
    val tips: List<String>,
    val support: SupportLevel,
    val goals: List<GoalProgress>,
    /** Pflegekraft oder Betreuungskraft (beruflich): andere Entlastungs-Hinweise */
    val professional: Boolean,
) {
    val openIntentions: Int get() = intentions.count { !it.done }
}

/**
 * Tages-Motivator: wertet die letzten drei Tage aus (Stimmung, erledigte Aufgaben, Einnahmen, Termine, Ziele)
 * und macht daraus einen Satz fuer den Tag, kleine Vorhaben und Tipps. Alles lokal, nur Anhaltspunkte.
 */
object Motivator {
    val TAGS = listOf("erschöpft", "gestresst", "traurig", "allein", "unruhig", "Schmerzen", "zufrieden", "dankbar", "entspannt", "stolz")

    fun emoji(mood: Int): String = when (mood) {
        5 -> "😄"
        4 -> "🙂"
        3 -> "😐"
        2 -> "😕"
        else -> "😢"
    }

    fun label(mood: Int): String = when (mood) {
        5 -> "Sehr gut"
        4 -> "Gut"
        3 -> "Geht so"
        2 -> "Nicht gut"
        else -> "Schlecht"
    }

    fun emojiFor(avg: Double?): String = avg?.let { emoji(it.roundToInt().coerceIn(1, 5)) } ?: "·"

    private val GENERAL = listOf(
        "Du musst heute nicht alles schaffen. Das Wichtigste reicht.",
        "Kleine Schritte bringen dich auch ans Ziel.",
        "Was du tust, macht einen Unterschied, auch wenn es keiner laut sagt.",
        "Eine Pause ist kein Luxus, sondern gehört zur Pflege dazu.",
        "Heute zählt, was gelingt, nicht was liegen bleibt.",
        "Hilfe annehmen ist eine Stärke.",
        "Atme einmal tief durch. Du machst das gut.",
        "Ein Schritt nach dem anderen.",
        "Auch du verdienst heute etwas Gutes.",
        "Nicht perfekt, aber da. Das ist viel wert.",
        "Plane heute einen Moment nur für dich ein.",
        "Was gestern nicht ging, darf heute neu beginnen.",
        "Gut für dich zu sorgen hilft auch dem Menschen, den du begleitest.",
        "Du bist nicht allein. Es gibt Menschen und Stellen, die helfen.",
        "Freu dich über das, was heute gelingt, auch über Kleinigkeiten.",
        "Geduld mit anderen beginnt mit Geduld mit dir selbst.",
        "Ein Tee, ein Lächeln, ein Anruf: Kleine Dinge tragen durch den Tag.",
        "Ordnung im Kopf beginnt mit einem Punkt auf der Liste.",
        "Du darfst auch einmal Nein sagen.",
        "Jeder erledigte Schritt ist ein Stück Entlastung.",
        "Heute ist ein guter Tag, um einen Schritt weiterzukommen.",
        "Was du heute vorbereitest, macht morgen leichter.",
    )

    private val GENTLE = listOf(
        "Es ist in Ordnung, wenn heute ein schwerer Tag ist.",
        "Sei heute besonders freundlich zu dir.",
        "Nur das Nötigste. Der Rest darf warten.",
        "Du musst das nicht allein tragen.",
        "Schwere Tage gehen vorbei. Hol dir Unterstützung, wenn du sie brauchst.",
        "Ein kleiner Schritt reicht heute völlig.",
    )

    private val GOOD = listOf(
        "Schön, dass es dir gut geht. Nimm diese Kraft mit in den Tag.",
        "Gute Tage sind ein Geschenk. Genieße diesen bewusst.",
        "Läuft! Vergiss trotzdem deine Pausen nicht.",
        "Heute ist Rückenwind. Vielleicht der richtige Tag für einen größeren Schritt?",
    )

    private val PROFESSIONAL = listOf(
        "Gute Pflege braucht auch Pausen, für dich.",
        "Austausch im Team entlastet.",
        "Du gibst jeden Tag viel. Achte auch auf dich.",
    )

    private fun dayOf(millis: Long, zone: ZoneId): Long = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

    fun isProfessional(c: CaregiverProfile): Boolean =
        c.roleValue == CaregiverRole.Pflegedienst || c.roleValue == CaregiverRole.Betreuung

    /** Fortschritt eines Ziels mit Einschaetzung des Tempos. */
    fun progress(goal: Goal, allSteps: List<GoalStep>, today: Long, zone: ZoneId = ZoneId.systemDefault()): GoalProgress {
        val steps = allSteps.filter { it.goalId == goal.id }.sortedBy { it.position }
        val total = steps.size
        val done = steps.count { it.doneMillis != null }
        val next = steps.firstOrNull { it.doneMillis == null }
        val overdue = steps.count { it.doneMillis == null && it.dueDay != null && it.dueDay < today }
        val created = if (goal.createdMillis > 0) dayOf(goal.createdMillis, zone) else today
        val lastProgress = (steps.mapNotNull { it.doneMillis }.maxOrNull())?.let { dayOf(it, zone) } ?: created
        val since = (today - lastProgress).toInt().coerceAtLeast(0)
        val pace = when {
            total == 0 -> Pace.NoSteps
            done == total -> Pace.Done
            goal.targetDay != null && goal.targetDay < today -> Pace.FarBehind
            goal.targetDay != null && goal.targetDay > created -> {
                val elapsed = ((today - created).toFloat() / (goal.targetDay - created)).coerceIn(0f, 1f)
                val diff = done.toFloat() / total - elapsed
                when {
                    diff > 0.15f -> Pace.Ahead
                    diff >= -0.15f -> if (overdue > 0) Pace.Behind else Pace.OnTrack
                    diff >= -0.4f -> Pace.Behind
                    else -> Pace.FarBehind
                }
            }
            overdue >= 2 -> Pace.FarBehind
            overdue == 1 || since >= 14 -> Pace.Behind
            else -> Pace.OnTrack
        }
        val tip = when (pace) {
            Pace.NoSteps -> "Lege erste Schritte an oder lass dir einen Weg vorschlagen."
            Pace.Done -> "Alle Schritte erledigt. Ziel als erreicht markieren?"
            Pace.Ahead -> "Schneller als geplant. Stark!" + (next?.let { " Als Nächstes: ${it.title}." } ?: "")
            Pace.OnTrack -> "Du bist im Plan." + (next?.let { " Als Nächstes: ${it.title}." } ?: "")
            Pace.Behind -> "Etwas hinter dem Plan. Mach den nächsten Schritt kleiner oder hol dir Hilfe dazu."
            Pace.FarBehind ->
                if (goal.targetDay != null && goal.targetDay < today) "Der Wunschtermin ist vorbei. Neuen Termin festlegen oder den Weg anpassen?"
                else "Deutlich hinter dem Plan. Lohnt sich ein neuer Termin oder ein anderer Weg?"
        } + if (pace != Pace.Done && pace != Pace.NoSteps && since >= 7) " Seit $since Tagen kein Fortschritt." else ""
        return GoalProgress(goal, steps, done, total, next, overdue, since, pace, tip)
    }

    fun plan(
        today: Long,
        hour: Int,
        caregiver: CaregiverProfile,
        moods: List<MoodEntry>,
        todos: List<Todo>,
        checkins: List<Checkin>,
        appointments: List<Appointment>,
        goals: List<Goal>,
        steps: List<GoalStep>,
        doneKeys: Set<String>,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): MotivatorPlan {
        val from = today - 3
        val professional = isProfessional(caregiver)

        // ---- Stimmung der letzten drei Tage (heute eingeschlossen)
        val recent = moods.filter { it.day in (today - 2)..today }
        val moodDays = (today - 2..today).map { d ->
            val list = recent.filter { it.day == d }
            MoodDay(d, list.takeIf { it.isNotEmpty() }?.map { it.mood }?.average(), list.size)
        }
        val moodAvg = recent.takeIf { it.isNotEmpty() }?.map { it.mood }?.average()
        val withData = moodDays.filter { it.avg != null }
        val trend = if (withData.size >= 2) {
            val diff = withData.last().avg!! - withData.first().avg!!
            when {
                diff >= 0.75 -> 1
                diff <= -0.75 -> -1
                else -> 0
            }
        } else 0
        val lastMood = recent.maxByOrNull { it.atMillis }
        val moodToday = recent.any { it.day == today }
        val tags = recent.flatMap { it.tagList() }

        val support = when {
            lastMood?.mood == 1 || recent.count { it.mood == 1 } >= 2 -> SupportLevel.Strong
            (moodAvg != null && moodAvg <= 2.3 && recent.size >= 2) || (lastMood != null && lastMood.mood <= 2) ||
                tags.count { it == "erschöpft" } >= 2 -> SupportLevel.Relief
            else -> SupportLevel.None
        }
        val low = support != SupportLevel.None || (moodAvg != null && moodAvg < 2.8)
        val good = !low && moodAvg != null && moodAvg >= 4.0

        // ---- Satz fuer den Tag (wechselt taeglich)
        val pool = when {
            low -> GENTLE
            good -> GOOD + GENERAL
            professional -> PROFESSIONAL + GENERAL
            else -> GENERAL
        }
        val sentence = pool[(today % pool.size).toInt().coerceAtLeast(0)]
        val first = caregiver.name.trim().split(" ").firstOrNull().orEmpty()
        val hello = when {
            hour < 11 -> "Guten Morgen"
            hour < 17 -> "Hallo"
            else -> "Guten Abend"
        }
        val greeting = if (first.isNotBlank()) "$hello, $first" else hello

        // ---- Das hast du geschafft (letzte drei Tage und heute)
        val tasksDone = todos.count { it.done && it.doneMillis != null && dayOf(it.doneMillis, zone) >= from }
        val intakes = checkins.count { it.kind == Checkin.MED && it.done && it.day >= from }
        val apptsSkipped = checkins.filter { it.kind == Checkin.APPT && (it.status == Checkin.CANCELLED || it.status == Checkin.MOVED) }
            .map { it.refId to it.day }.toSet()
        val appts = appointments.count { a ->
            val d = dayOf(a.whenMillis, zone)
            d >= from && a.whenMillis <= nowMillis && (a.id to d) !in apptsSkipped
        }
        val stepsDone = steps.count { it.doneMillis != null && dayOf(it.doneMillis, zone) >= from }
        val reached = goals.filter { it.status == Goal.REACHED && it.doneMillis != null && dayOf(it.doneMillis, zone) >= from }
        val achieved = buildList {
            if (tasksDone > 0) add(if (tasksDone == 1) "1 Aufgabe erledigt" else "$tasksDone Aufgaben erledigt")
            if (intakes > 0) add(if (intakes == 1) "1 Einnahme gegeben" else "$intakes Einnahmen gegeben")
            if (appts > 0) add(if (appts == 1) "1 Termin begleitet" else "$appts Termine begleitet")
            if (stepsDone > 0) add(if (stepsDone == 1) "1 Schritt für deine Ziele" else "$stepsDone Schritte für deine Ziele")
            reached.forEach { add("Ziel erreicht: ${it.title} 🎉") }
            val checkDays = moods.filter { it.day >= from }.map { it.day }.distinct().size
            if (checkDays >= 2) add("An $checkDays Tagen auf dich selbst geachtet")
        }

        // ---- Ziele
        val active = goals.filter { it.status == Goal.ACTIVE }
        val progress = active.map { progress(it, steps, today, zone) }
            .sortedWith(compareByDescending<GoalProgress> { it.overdue }.thenBy { it.next?.dueDay ?: it.goal.targetDay ?: Long.MAX_VALUE })

        // ---- Vorhaben fuer heute
        val intentions = mutableListOf<Intention>()
        steps.filter { s -> s.doneMillis != null && dayOf(s.doneMillis, zone) == today && active.any { it.id == s.goalId } }
            .forEach { s ->
                val g = active.first { it.id == s.goalId }
                intentions += Intention("step:${s.id}", "„${g.title}“: ${s.title}", true, stepId = s.id)
            }
        progress.filter { it.next != null }.take(2).forEach { p ->
            val s = p.next!!
            intentions += Intention("step:${s.id}", "„${p.goal.title}“: ${s.title}", false, stepId = s.id)
        }
        val dueTodos = todos.filter { !it.archived && it.dueDay != null && it.dueDay <= today }
        val openDue = dueTodos.filter { !it.done }.sortedWith(compareBy<Todo> { it.dueDay }.thenByDescending { it.priority })
        val doneDueToday = dueTodos.filter { it.done && it.doneMillis != null && dayOf(it.doneMillis, zone) == today }
        doneDueToday.take(2).forEach { intentions += Intention("todo:${it.id}", it.text, true, todoId = it.id) }
        val openTodo = openDue.firstOrNull()
            ?: todos.filter { !it.done && !it.archived && it.priority >= 2 }.minByOrNull { it.createdMillis }
        openTodo?.let { intentions += Intention("todo:${it.id}", it.text, false, todoId = it.id) }
        if (openDue.size >= 3) {
            intentions += Intention("abgeben", "Eine Sache abgeben oder verschieben: Was kann warten, wer kann helfen?", "abgeben" in doneKeys)
        }
        val selfText = when {
            low -> "15 Minuten nur für dich: kurz raus, Tee, Musik oder ein Anruf bei einem vertrauten Menschen"
            good -> "Eine Sache heute bewusst genießen"
            else -> "Eine kleine Pause zwischendurch einplanen"
        }
        intentions += Intention("selbst", selfText, "selbst" in doneKeys)

        // ---- Tipps
        val tips = mutableListOf<String>()
        if (trend < 0) tips += "Deine Stimmung ist in den letzten Tagen gesunken. Plane bewusst Entlastung ein."
        if (trend > 0) tips += "Deine Stimmung ist gestiegen. Schön! Was hat dir geholfen?"
        val tagTips = linkedMapOf(
            "erschöpft" to "Erschöpfung ernst nehmen: Was kann diese Woche wegfallen oder wer kann etwas übernehmen?",
            "gestresst" to "Bei Stress hilft schon eine Minute ruhiges Atmen: langsam ein, noch langsamer aus.",
            "traurig" to "Sprich mit jemandem, dem du vertraust. Auch ein kurzes Gespräch kann entlasten.",
            "allein" to if (professional) "Austausch mit Kolleginnen und Kollegen oder der Leitung hilft." else "Austausch hilft: Angehörigen-Gruppen und Pflegestützpunkte gibt es an vielen Orten.",
            "unruhig" to "Schreib auf, was dich beschäftigt, als Aufgabe oder Bemerkung zum Tag. Dann muss es nicht im Kopf bleiben.",
            "Schmerzen" to "Wenn dir selbst etwas wehtut, lass es ärztlich abklären. Deine Gesundheit zählt genauso.",
        )
        tagTips.forEach { (tag, tip) -> if (tag in tags && tips.size < 3) tips += tip }
        if (tags.any { it in listOf("zufrieden", "dankbar", "entspannt", "stolz") } && tips.size < 3) {
            tips += "Merk dir, was dir gutgetan hat. Das hilft an schweren Tagen."
        }
        if (tasksDone + stepsDone + appts >= 10 && tips.size < 4) tips += "Du hast in den letzten Tagen viel geschafft. Denk auch an deine Pausen."
        progress.filter { it.pace == Pace.Behind || it.pace == Pace.FarBehind || it.pace == Pace.Done }.take(2).forEach {
            if (tips.size < 5) tips += "„${it.goal.title}“: ${it.tip}"
        }
        if (moods.none { it.day >= from } && tips.size < 5) {
            tips += "Mit dem Stimmungs-Check sieht der Motivator, wie es dir geht, und passt Vorhaben und Tipps an."
        }
        if (active.isEmpty() && tips.size < 5) {
            tips += "Setz dir ein Ziel, z. B. einen Antrag oder etwas für deine Gesundheit. Der Motivator begleitet dich Schritt für Schritt."
        }

        return MotivatorPlan(
            day = today,
            greeting = greeting,
            sentence = sentence,
            achieved = achieved,
            moodDays = moodDays,
            moodAvg = moodAvg,
            moodTrend = trend,
            lastMood = lastMood,
            moodToday = moodToday,
            intentions = intentions.distinctBy { it.key },
            tips = tips,
            support = support,
            goals = progress,
            professional = professional,
        )
    }

    fun dayLabel(day: Long, today: Long): String = when (day) {
        today -> "Heute"
        today - 1 -> "Gestern"
        else -> LocalDate.ofEpochDay(day).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.GERMAN)
    }
}
