package com.oamsapps.pdfzy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

fun Context.fileMeta(uri: Uri): Pair<String, Long> =
    if (uri.scheme == "file") File(uri.path!!).let { it.name to it.length() }
    else contentResolver.query(uri, null, null, null, null)?.use { c ->
        c.moveToFirst()
        c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) to c.getLong(c.getColumnIndexOrThrow(OpenableColumns.SIZE))
    } ?: (uri.lastPathSegment.orEmpty() to 0L)

fun Context.openPdf(uri: Uri, mime: String = "application/pdf") = runCatching {
    startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
}

/** Share one or many PDFs; tries [pkg] (e.g. WhatsApp) first, falls back to the system chooser. */
fun Context.sharePdfs(uris: List<Uri>, pkg: String? = null, mime: String = "application/pdf") {
    if (uris.isEmpty()) return
    val i = (if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
             else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)))
        .setType(mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    if (pkg != null && runCatching { startActivity(Intent(i).setPackage(pkg)) }.isSuccess) return
    runCatching { startActivity(Intent.createChooser(i, "Share")) }
}

fun Long.kb() = if (this >= 1_048_576) "%.1f MB".format(this / 1_048_576f) else "${this / 1024} KB"

/** "4.8 MB → 420 KB (91% smaller)" */
fun Context.sizeChange(before: Long, after: Long) =
    if (before > 0 && after in 1 until before) getString(R.string.size_change, before.kb(), after.kb(), (100 - after * 100 / before).toInt())
    else after.kb()

fun kbLabel(kb: Int) = if (kb >= 1000 && kb % 1000 == 0) "${kb / 1000} MB" else "$kb KB"

/** Small preview bitmap (sampled decode). */
fun Context.thumb(uri: Uri, maxPx: Int = 256): android.graphics.Bitmap? {
    val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, o) }
    var s = 1; while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxPx) s *= 2
    return contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = s }) }
}

tailrec fun Context.findActivity(): android.app.Activity =
    this as? android.app.Activity ?: (this as android.content.ContextWrapper).baseContext.findActivity()

/** True if the file behind [uri] can still be opened (it may have been deleted or moved elsewhere). */
fun Context.canOpen(uri: Uri) = runCatching { contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false }.getOrDefault(false)

/** "1 page" / "3 pages" */
fun plural(n: Int, word: String) = "$n $word" + if (n == 1) "" else "s"

/**
 * Readable default names instead of timestamps: Resume_2026_merged.pdf, Scanned_documents_200KB.pdf,
 * Scan_2026-11-20_1432.pdf. The system file picker adds " (1)" if the name already exists.
 */
fun Context.outputName(op: String, sources: List<Uri>, tag: String? = null): String {
    val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.US).format(java.util.Date())
    val base = sources.firstOrNull()?.let { fileMeta(it).first.substringBeforeLast('.') }
        ?.replace(Regex("[^A-Za-z0-9._-]+"), "_")?.trim('_')?.takeIf { it.isNotEmpty() }
    val W = com.oamsapps.pdfzy.work.PdfWorker
    return when (op) {
        W.SCAN -> "Scan_$stamp"
        W.IMG -> "Images_$stamp"
        W.ID_CARD -> "ID_card_$stamp"
        else -> (base ?: "PDFzy_$stamp") + "_" + when (op) {
            W.COMPRESS -> tag ?: "compressed"; W.MERGE -> "merged"; W.EXTRACT -> "pages"; W.ROTATE -> "rotated"
            W.PROTECT -> "protected"; W.UNLOCK -> "unlocked"; else -> "edited"
        }
    } + ".pdf"
}
