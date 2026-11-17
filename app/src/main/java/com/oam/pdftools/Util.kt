package com.oam.pdftools

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

fun Context.openPdf(uri: Uri) = runCatching {
    startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
}

/** Share one or many PDFs; tries [pkg] (e.g. WhatsApp) first, falls back to the system chooser. */
fun Context.sharePdfs(uris: List<Uri>, pkg: String? = null) {
    if (uris.isEmpty()) return
    val i = (if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
             else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)))
        .setType("application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    if (pkg != null && runCatching { startActivity(Intent(i).setPackage(pkg)) }.isSuccess) return
    runCatching { startActivity(Intent.createChooser(i, "Share PDF")) }
}

fun Long.kb() = if (this > 1_048_576) "%.1f MB".format(this / 1_048_576f) else "${this / 1024} KB"

/** "4.8 MB → 420 KB (91% smaller)" */
fun sizeChange(before: Long, after: Long) =
    if (before > 0 && after in 1 until before) "${before.kb()} → ${after.kb()} (${100 - after * 100 / before}% smaller)"
    else after.kb()
