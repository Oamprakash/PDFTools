package com.oam.pdftools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

fun Context.fileMeta(uri: Uri): Pair<String, Long> =
    contentResolver.query(uri, null, null, null, null)?.use { c ->
        c.moveToFirst()
        c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) to c.getLong(c.getColumnIndexOrThrow(OpenableColumns.SIZE))
    } ?: (uri.lastPathSegment.orEmpty() to 0L)

fun Context.openPdf(uri: Uri) = runCatching {
    startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
}

fun Long.kb() = if (this > 1_048_576) "%.1f MB".format(this / 1_048_576f) else "${this / 1024} KB"
