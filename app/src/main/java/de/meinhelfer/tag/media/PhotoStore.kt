package de.meinhelfer.tag.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Fotos liegen ausschliesslich im privaten App-Ordner und verlassen das Geraet nicht. */
object PhotoStore {
    private fun dir(context: Context): File = File(context.filesDir, "photos").apply { mkdirs() }

    fun newFile(context: Context): File = File(dir(context), "med_${System.currentTimeMillis()}.jpg")

    fun file(context: Context, name: String): File = File(dir(context), name)

    fun delete(context: Context, name: String?) {
        if (name != null) file(context, name).delete()
    }

    /** Uri, die die Kamera-App als Speicherziel beschreiben darf. */
    fun uriForCamera(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /**
     * Verkleinert das Foto (ca. 1600 bis 2000 Pixel), dreht es richtig herum und speichert es neu als JPEG.
     * Gibt das Bild fuer die Texterkennung zurueck oder null, wenn es nicht lesbar ist.
     */
    suspend fun normalize(file: File): Bitmap? = withContext(Dispatchers.IO) {
        val bitmap = decodeSampled(file, 1600) ?: return@withContext null
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bitmap
    }

    /** Liest ein Bild herunterskaliert (Kantenlaenge zwischen [maxSize] und 2 x [maxSize]) mit korrekter Drehung. */
    fun decodeSampled(file: File, maxSize: Int): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize && bounds.outHeight / (sample * 2) >= maxSize) {
            sample *= 2
        }
        val raw = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null

        val rotation = try {
            when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: IOException) {
            0
        }
        if (rotation == 0) return raw
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    }
}
