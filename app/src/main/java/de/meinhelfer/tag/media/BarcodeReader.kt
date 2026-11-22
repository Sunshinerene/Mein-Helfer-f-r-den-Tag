package de.meinhelfer.tag.media

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Findet die PZN (Pharmazentralnummer) auf Packungsfotos, direkt auf dem Handy. */
object BarcodeReader {
    private val DATA_MATRIX = Regex("""9N(\d{8})""")
    private val EAN_PHARMA = Regex("""^4150(\d{8})\d$""")
    private val CODE39 = Regex("""^-?(\d{8})$""")

    /** Prueft die Pruefziffer einer 8-stelligen PZN. */
    fun isValidPzn(pzn: String): Boolean {
        if (pzn.length != 8 || !pzn.all { it.isDigit() }) return false
        val sum = (0 until 7).sumOf { (pzn[it] - '0') * (it + 1) }
        val check = sum % 11
        return check != 10 && check == pzn[7] - '0'
    }

    /** Liest eine PZN aus dem Text eines Barcodes oder null. */
    fun pznFrom(raw: String): String? {
        val candidates = listOfNotNull(
            DATA_MATRIX.find(raw)?.groupValues?.get(1),
            EAN_PHARMA.find(raw)?.groupValues?.get(1),
            CODE39.find(raw.trim())?.groupValues?.get(1),
        )
        return candidates.firstOrNull { isValidPzn(it) }
    }

    suspend fun scan(bitmap: Bitmap): String? {
        val scanner = BarcodeScanning.getClient()
        try {
            val codes = scanner.process(InputImage.fromBitmap(bitmap, 0)).await()
            return codes.mapNotNull { it.rawValue }.firstNotNullOfOrNull { pznFrom(it) }
        } finally {
            scanner.close()
        }
    }

    /** Alle Barcodes auf dem Bild als Rohtext (z. B. der Data-Matrix-Code eines Medikationsplans). */
    suspend fun scanAll(bitmap: Bitmap): List<String> {
        val scanner = BarcodeScanning.getClient()
        try {
            return scanner.process(InputImage.fromBitmap(bitmap, 0)).await().mapNotNull { it.rawValue }
        } finally {
            scanner.close()
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
    }
}
