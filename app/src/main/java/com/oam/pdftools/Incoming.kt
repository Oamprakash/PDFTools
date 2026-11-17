package com.oam.pdftools

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Files shared into the app (Share sheet / Open with). Screens read this as their initial input. */
object Incoming { var uris: List<Uri> = emptyList() }

/** Copy shared URIs into cache: grants from other apps can expire before a background job runs. */
suspend fun Context.importShared(uris: List<Uri>): List<Uri> = withContext(Dispatchers.IO) {
    val dir = File(cacheDir, "incoming").apply { deleteRecursively(); mkdirs() }
    uris.mapIndexedNotNull { i, u ->
        runCatching {
            val name = fileMeta(u).first.ifBlank { "file$i" }.replace('/', '_')
            val f = File(File(dir, "$i").apply { mkdirs() }, name)
            contentResolver.openInputStream(u)!!.use { inp -> f.outputStream().use { inp.copyTo(it) } }
            Uri.fromFile(f)
        }.getOrNull()
    }
}
