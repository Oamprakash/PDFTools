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
fun sizeChange(before: Long, after: Long) =
    if (before > 0 && after in 1 until before) "${before.kb()} → ${after.kb()} (${100 - after * 100 / before}% smaller)"
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
