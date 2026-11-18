package com.oamsapps.pdfzy.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable

/** Thumbnail renderer. PdfRenderer allows one open page at a time, hence the Mutex. */
class PdfThumbs(ctx: Context, uri: Uri) : Closeable {
    private val pfd = ctx.contentResolver.openFileDescriptor(uri, "r")!!
    private val r = PdfRenderer(pfd) // throws on password-protected PDFs
    private val lock = Mutex()
    private val cache = LruCache<Int, Bitmap>(40)
    val count = r.pageCount

    suspend fun page(i: Int, w: Int = 300): Bitmap = cache[i] ?: lock.withLock {
        withContext(Dispatchers.IO) {
            r.openPage(i).use { p ->
                Bitmap.createBitmap(w, w * p.height / p.width, Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(Color.WHITE); p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }.also { cache.put(i, it) }

    override fun close() { r.close(); pfd.close() }
}
