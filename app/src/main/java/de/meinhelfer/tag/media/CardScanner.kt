package de.meinhelfer.tag.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Was auf einem Ausweis bzw. einer Gesundheitskarte erkannt wurde. Alles muss vom Nutzer geprueft werden. */
data class CardData(
    val givenNames: String? = null,
    val surname: String? = null,
    val birthDate: LocalDate? = null,
    val street: String? = null,
    val zip: String? = null,
    val city: String? = null,
    val insurer: String? = null,
    val insuranceNumber: String? = null,
) {
    val fullName: String? get() = listOfNotNull(givenNames, surname).joinToString(" ").takeIf { it.isNotBlank() }
    val isEmpty: Boolean get() = fullName == null && birthDate == null && street == null && zip == null && city == null && insurer == null && insuranceNumber == null

    /** Ergaenzt fehlende Angaben aus einem zweiten Foto (z. B. Rueckseite). */
    fun merge(other: CardData): CardData = CardData(
        givenNames ?: other.givenNames,
        surname ?: other.surname,
        birthDate ?: other.birthDate,
        street ?: other.street,
        zip ?: other.zip,
        city ?: other.city,
        insurer ?: other.insurer,
        insuranceNumber ?: other.insuranceNumber,
    )
}

enum class CardKind { Ausweis, Gesundheitskarte }

/**
 * Liest Personalausweis und elektronische Gesundheitskarte per Texterkennung direkt auf dem Handy.
 * Nichts wird gesendet; das Foto wird sofort nach dem Auslesen geloescht.
 */
object CardScanner {
    private const val DIR = "scan"
    private val DATE = Regex("""\b(\d{2})[./](\d{2})[./](\d{4})\b""")
    private val ZIP_CITY = Regex("""^(\d{5})\s+([A-Za-zÄÖÜäöüß][A-Za-zÄÖÜäöüß .\-/()]+)$""")
    private val STREET = Regex("""^([A-Za-zÄÖÜäöüßẞ][A-Za-zÄÖÜäöüßẞ .\-']{2,})\s+(\d{1,4}\s?[a-zA-Z]?(?:\s?[-/]\s?\d{1,4}\s?[a-zA-Z]?)?)$""")
    private val INSURANCE_NO = Regex("""\b([A-Z])\s?(\d{9})\b""")
    private val CARRIER = Regex("""\b\d{9}\s*[-–]\s*([A-Za-zÄÖÜäöüß][^\n]{1,60})""")
    private val MRZ_LINE = Regex("""^[A-Z0-9<]{28,32}$""")

    private val INSURERS = listOf(
        "AOK", "Barmer", "Techniker", "TK", "DAK", "IKK", "KKH", "hkk", "Knappschaft", "BKK", "Mobil", "SBK", "HEK",
        "Debeka", "Allianz", "Signal Iduna", "AXA", "HUK", "DKV", "Continentale", "Gothaer", "Hallesche", "Barmenia", "Viactiv", "Novitas",
    )

    // ---------------------------------------------------------------- Foto

    private fun dir(context: Context): File = File(context.cacheDir, DIR).apply { mkdirs() }

    fun newFile(context: Context): File = File(dir(context), "karte_${System.currentTimeMillis()}.jpg")

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Loescht alle uebrig gebliebenen Fotos (z. B. nach einem Absturz). */
    fun clear(context: Context) {
        File(context.cacheDir, DIR).listFiles()?.forEach { it.delete() }
    }

    /** Liest das Foto aus und loescht es danach in jedem Fall. */
    suspend fun read(context: Context, file: File, kind: CardKind): CardData = try {
        val bitmap = withContext(Dispatchers.IO) { PhotoStore.decodeSampled(file, 1600) }
        if (bitmap == null) CardData() else try {
            val lines = recognize(bitmap)
            if (kind == CardKind.Ausweis) parseId(lines) else parseInsuranceCard(lines)
        } finally {
            bitmap.recycle()
        }
    } finally {
        withContext(Dispatchers.IO) { file.delete() }
    }

    private suspend fun recognize(bitmap: Bitmap): List<String> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            return text.textBlocks.flatMap { it.lines }.map { it.text.trim() }.filter { it.isNotEmpty() }
        } finally {
            recognizer.close()
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
    }

    // ---------------------------------------------------------------- Auswerten

    /** "MUSTERMANN-MEIER" -> "Mustermann-Meier", "VON DER HEIDE" -> "von der Heide" */
    fun nameCase(raw: String): String {
        val small = setOf("von", "van", "der", "den", "de", "zu", "zum", "zur", "und", "di", "da", "la", "le")
        return raw.trim().lowercase().split(" ").filter { it.isNotBlank() }.mapIndexed { i, word ->
            word.split("-").joinToString("-") { part ->
                if (i > 0 && part in small) part else part.replaceFirstChar { it.titlecase() }
            }
        }.joinToString(" ")
    }

    private fun streetCase(raw: String): String =
        nameCase(raw).replace(Regex("(?i)strasse\\b"), "straße").replace(Regex("(?i)str\\.(?=\\s|$)"), "str.")

    private fun date(text: String): LocalDate? = DATE.find(text)?.let { m ->
        runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
    }

    private fun isLabel(line: String, vararg words: String): Boolean = words.any { line.contains(it, ignoreCase = true) }

    /** Wert nach einer Beschriftung: gleiche Zeile hinter dem Schraegstrich-Block oder die naechste Zeile, die kein Label ist. */
    private fun valueAfter(lines: List<String>, index: Int, accept: (String) -> Boolean): String? {
        for (j in index + 1..minOf(index + 3, lines.lastIndex)) {
            val l = lines[j]
            if (l.contains("/") && l.any { it.isLowerCase() }) continue
            if (accept(l)) return l
        }
        return null
    }

    private fun lettersOnly(l: String) = l.isNotBlank() && l.all { it.isLetter() || it == ' ' || it == '-' || it == '\'' } && l.count { it.isLetter() } >= 2

    /** Maschinenlesbare Zone (Rueckseite des Ausweises): Name und Geburtsdatum. */
    private fun parseMrz(lines: List<String>): CardData {
        val mrz = lines.map { it.replace(" ", "").uppercase() }.filter { MRZ_LINE.matches(it) && it.contains('<') }
        var data = CardData()
        mrz.firstOrNull { l -> l.contains("<<") && l.replace("<", "").all { it.isLetter() } }?.let { l ->
            val parts = l.trimEnd('<').split("<<").filter { it.isNotBlank() }
            if (parts.isNotEmpty()) {
                data = data.copy(
                    surname = nameCase(parts[0].replace('<', ' ')),
                    givenNames = parts.getOrNull(1)?.let { nameCase(it.replace('<', ' ')) },
                )
            }
        }
        mrz.firstOrNull { it.length >= 7 && it.take(7).all(Char::isDigit) }?.let { l ->
            val yy = l.substring(0, 2).toInt()
            val mm = l.substring(2, 4).toInt()
            val dd = l.substring(4, 6).toInt()
            val now = LocalDate.now().year % 100
            val year = if (yy > now) 1900 + yy else 2000 + yy
            runCatching { LocalDate.of(year, mm, dd) }.getOrNull()?.takeIf { it.isBefore(LocalDate.now()) }?.let { data = data.copy(birthDate = it) }
        }
        return data
    }

    /** Personalausweis, Vorder- oder Rueckseite. */
    fun parseId(lines: List<String>): CardData {
        var surname: String? = null
        var given: String? = null
        var birth: LocalDate? = null
        lines.forEachIndexed { i, l ->
            when {
                surname == null && isLabel(l, "Surname", "Name/") && !isLabel(l, "Geburtsname", "birth", "Künstler", "Ordens") ->
                    surname = valueAfter(lines, i, { lettersOnly(it) })?.let { nameCase(it) }
                given == null && isLabel(l, "Vornamen", "Given name", "Prénoms") ->
                    given = valueAfter(lines, i, { lettersOnly(it) })?.let { nameCase(it) }
                birth == null && isLabel(l, "Geburtstag", "Date of birth", "Geburtsdatum") ->
                    birth = date(l) ?: (i + 1..minOf(i + 3, lines.lastIndex)).firstNotNullOfOrNull { date(lines[it]) }
            }
        }
        // Anschrift (Rueckseite): "PLZ Ort" und "Strasse Nr." in der Naehe von "Anschrift"
        val start = lines.indexOfFirst { isLabel(it, "Anschrift", "Address", "Adresse") }.takeIf { it >= 0 } ?: 0
        val window = lines.drop(start).take(8)
        val zipCity = window.firstNotNullOfOrNull { ZIP_CITY.find(it.trim()) } ?: lines.firstNotNullOfOrNull { ZIP_CITY.find(it.trim()) }
        val street = window.map { it.trim() }.firstOrNull { STREET.matches(it) && !isLabel(it, "Größe", "Height", "Augen") }
        val base = CardData(
            givenNames = given,
            surname = surname,
            birthDate = birth,
            street = street?.let { streetCase(it) },
            zip = zipCity?.groupValues?.get(1),
            city = zipCity?.groupValues?.get(2)?.let { nameCase(it) },
        )
        // Fehlende Angaben aus der maschinenlesbaren Zone, notfalls das frueheste Datum als Geburtsdatum
        val merged = base.merge(parseMrz(lines))
        if (merged.birthDate != null) return merged
        val earliest = lines.mapNotNull { date(it) }.filter { it.isBefore(LocalDate.now().minusYears(1)) }.minOrNull()
        return merged.copy(birthDate = earliest)
    }

    /** Elektronische Gesundheitskarte: Vorderseite oder Rueckseite (Europaeische Krankenversicherungskarte). */
    fun parseInsuranceCard(lines: List<String>): CardData {
        val all = lines.joinToString("\n")
        val number = INSURANCE_NO.findAll(all).map { it.groupValues[1] + it.groupValues[2] }.firstOrNull()
        // Rueckseite: "7 Kennnummer des Traegers: 101575519 - AOK ..."
        val carrier = CARRIER.find(all)?.groupValues?.get(1)?.trim()?.trimEnd('.', ',')
        val insurer = carrier ?: lines.firstOrNull { l -> INSURERS.any { k -> Regex("\\b" + Regex.escape(k) + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(l) } }
        // Rueckseite: 3 Name, 4 Vornamen, 5 Geburtsdatum
        var surname: String? = null
        var given: String? = null
        var birth: LocalDate? = null
        lines.forEachIndexed { i, l ->
            when {
                surname == null && Regex("^3\\.?\\s").containsMatchIn(l) || (surname == null && isLabel(l, "Name") && !isLabel(l, "Vorname", "Given")) -> {
                    val inline = l.replace(Regex("^3\\.?\\s*"), "").replace(Regex("(?i)^name\\s*[:/]?\\s*"), "").trim()
                    surname = (inline.takeIf { lettersOnly(it) && !isLabel(it, "Name") } ?: valueAfter(lines, i, { lettersOnly(it) }))?.let { nameCase(it) }
                }
                given == null && (Regex("^4\\.?\\s").containsMatchIn(l) || isLabel(l, "Vorname", "Given")) -> {
                    val inline = l.replace(Regex("^4\\.?\\s*"), "").replace(Regex("(?i)^vornamen?\\s*[:/]?\\s*"), "").trim()
                    given = (inline.takeIf { lettersOnly(it) && !isLabel(it, "Vorname", "Given") } ?: valueAfter(lines, i, { lettersOnly(it) }))?.let { nameCase(it) }
                }
                birth == null && (Regex("^5\\.?\\s").containsMatchIn(l) || isLabel(l, "Geburtsdatum", "Date of birth")) ->
                    birth = date(l) ?: (i + 1..minOf(i + 2, lines.lastIndex)).firstNotNullOfOrNull { date(lines[it]) }
            }
        }
        return CardData(
            givenNames = given,
            surname = surname,
            birthDate = birth?.takeIf { it.isBefore(LocalDate.now()) },
            insurer = insurer?.let { matchInsurer(it) },
            insuranceNumber = number,
        )
    }

    /** Ordnet den erkannten Namen der Kasse der Auswahlliste zu, sonst bleibt er wie erkannt. */
    fun matchInsurer(raw: String): String {
        val r = raw.lowercase()
        return when {
            "techniker" in r || Regex("\\btk\\b").containsMatchIn(r) -> "Techniker Krankenkasse (TK)"
            "aok" in r -> "AOK"
            "barmer" in r -> "Barmer"
            "dak" in r -> "DAK-Gesundheit"
            "ikk classic" in r -> "IKK classic"
            "kkh" in r -> "KKH"
            "hkk" in r -> "hkk"
            "knappschaft" in r -> "Knappschaft"
            "mobil" in r -> "Mobil Krankenkasse"
            "sbk" in r -> "SBK"
            "hek" in r -> "HEK"
            "audi bkk" in r -> "Audi BKK"
            else -> raw.trim().take(60)
        }
    }

    val DISPLAY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
}
