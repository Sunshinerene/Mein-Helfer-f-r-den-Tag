package de.meinhelfer.tag.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import de.meinhelfer.tag.data.Document
import de.meinhelfer.tag.letters.SignedCopies
import de.meinhelfer.tag.media.DocumentStore
import java.io.File

/** Text eines Schreibens fuer eine E-Mail: Bezug, Text, Gruss und Name(n). Bei Formularen nur ein kurzer Begleittext. */
internal fun letterMailText(subject: String, reference: String, body: String, signatures: List<String>, enclosures: List<String>, form: Boolean, sender: String): String =
    if (form) {
        "Sehr geehrte Damen und Herren,\n\nanbei erhalten Sie „$subject“ als PDF.\n\nMit freundlichen Grüßen\n$sender".trimEnd()
    } else {
        listOfNotNull(
            reference.trim().takeIf { it.isNotBlank() },
            body.trim(),
            "Mit freundlichen Grüßen\n" + signatures.joinToString("\n") { it.trim() },
            enclosures.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "Anlagen: "),
        ).joinToString("\n\n")
    }

/**
 * Oeffnet die E-Mail-App mit Empfaenger, Betreff und Text. Mit [attachment] wird die PDF angehaengt
 * (nur E-Mail-Apps werden angeboten). Gesendet wird erst in der E-Mail-App.
 */
internal fun openLetterMail(context: Context, to: String?, subject: String, text: String, attachment: File?) {
    val mailto = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
    val intent = if (attachment != null) {
        val uri = DocumentStore.uriFor(context, attachment)
        Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            selector = mailto
        }
    } else {
        mailto
    }
    intent.apply {
        to?.takeIf { it.isNotBlank() }?.let { putExtra(Intent.EXTRA_EMAIL, arrayOf(it.trim())) }
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Keine E-Mail-App gefunden.", Toast.LENGTH_LONG).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "Die E-Mail-App konnte nicht geöffnet werden.", Toast.LENGTH_LONG).show()
    }
}

/** Ist das Dokument ein Entwurf aus „Schreiben und Vollmachten“? */
internal fun isLetterDraft(d: Document): Boolean = d.title.contains(SignedCopies.DRAFT_MARK)

/**
 * Entwurf aus den Dokumenten per E-Mail: Betreff ist der erste Absatz des gespeicherten Textes, der Rest der Text.
 * Die PDF wird kurz entschluesselt und angehaengt.
 */
internal suspend fun mailLetterDraft(context: Context, doc: Document, to: String?, sender: String = "") {
    val parts = doc.text.split("\n\n", limit = 2)
    val subject = parts.firstOrNull()?.trim().orEmpty().ifBlank { SignedCopies.baseTitle(doc) }
    val rest = parts.getOrNull(1)?.trim().orEmpty()
    // Formulare (z. B. Vollmacht) gehen als Anhang mit kurzem Begleittext
    val text = if (rest.contains("Mit freundlichen Grüßen")) rest else letterMailText(subject, "", "", emptyList(), emptyList(), true, sender)
    val file = runCatching {
        DocumentStore.decryptToCache(context, doc.fileName, DocumentStore.safeName(SignedCopies.baseTitle(doc)) + ".pdf")
    }.getOrNull()
    openLetterMail(context, to, subject, text, file)
}
