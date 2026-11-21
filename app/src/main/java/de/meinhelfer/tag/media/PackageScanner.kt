package de.meinhelfer.tag.media

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Vorschlaege aus dem Packungsfoto. Alles muss vom Nutzer geprueft werden. */
data class ScanResult(
    /** Moegliche Namen, groesste Schrift zuerst. */
    val nameCandidates: List<String>,
    /** Erkannte Staerke, z. B. "500 mg". */
    val strength: String?,
    /** Erkannte Packungsgroesse, z. B. 20 (Tabletten). */
    val packSize: Int?,
    /** Erkannte PZN (nur mit gueltiger Pruefziffer), aus Barcode oder Text. */
    val pzn: String? = null,
)

/** Liest Text von Medikamentenpackungen direkt auf dem Handy (kein Internet, keine Datenuebertragung). */
object PackageScanner {
    private val STRENGTH = Regex("""\d+(?:[.,]\d+)?\s?(?:mg|µg|mcg|g|ml|IE)\b""", RegexOption.IGNORE_CASE)
    private val PACK_SIZE = Regex(
        """(\d{1,3})\s?(?:Tabletten|Filmtabletten|Tabl\.|Kapseln|Hartkapseln|Stück|Dragees|Beutel|Ampullen)""",
        RegexOption.IGNORE_CASE,
    )

    private val PZN_TEXT = Regex("""PZN\s*[:\-]?\s*(\d{8})""", RegexOption.IGNORE_CASE)

    suspend fun scan(bitmap: Bitmap): ScanResult {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            val parsed = parse(text)
            // Barcode zuerst (zuverlaessiger), sonst die aufgedruckte "PZN ..."
            val fromBarcode = runCatching { BarcodeReader.scan(bitmap) }.getOrNull()
            val fromText = PZN_TEXT.findAll(text.text).map { it.groupValues[1] }.firstOrNull { BarcodeReader.isValidPzn(it) }
            return parsed.copy(pzn = fromBarcode ?: fromText)
        } finally {
            recognizer.close()
        }
    }

    private fun parse(text: Text): ScanResult {
        val candidates = text.textBlocks
            .flatMap { it.lines }
            .map { line -> line.text.trim() to (line.boundingBox?.height() ?: 0) }
            .filter { (value, _) -> value.length in 3..40 && value.count { it.isLetter() } >= 3 }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.lowercase() }
            .take(6)

        val full = text.text
        return ScanResult(
            nameCandidates = candidates,
            strength = STRENGTH.find(full)?.value?.trim(),
            packSize = PACK_SIZE.find(full)?.groupValues?.get(1)?.toIntOrNull(),
        )
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
    }
}
