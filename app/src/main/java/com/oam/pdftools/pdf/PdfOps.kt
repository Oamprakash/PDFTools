package com.oam.pdftools.pdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.net.Uri
import android.provider.DocumentsContract
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File

/** All ops stream through temp files so big PDFs don't OOM on low-RAM phones. */
class PdfOps(private val cr: ContentResolver) {
    private val mem get() = MemoryUsageSetting.setupTempFileOnly()
    private fun load(uri: Uri) = cr.openInputStream(uri)!!.use { PDDocument.load(it, mem) }
    private fun save(doc: PDDocument, out: Uri) = cr.openOutputStream(out, "wt")!!.use { doc.save(it) }

    fun imagesToPdf(images: List<Uri>, out: Uri, quality: Float = 0.85f, margin: Float = 24f) =
        PDDocument().use { doc ->
            images.forEach { uri ->
                val bmp = decode(uri, 2000)
                val page = PDPage(PDRectangle.A4).also(doc::addPage)
                val img = JPEGFactory.createFromImage(doc, bmp, quality); bmp.recycle()
                val box = page.mediaBox
                val s = minOf((box.width - 2 * margin) / img.width, (box.height - 2 * margin) / img.height)
                val w = img.width * s; val h = img.height * s
                PDPageContentStream(doc, page).use { it.drawImage(img, (box.width - w) / 2, (box.height - h) / 2, w, h) }
            }
            save(doc, out)
        }

    fun merge(pdfs: List<Uri>, out: Uri) {
        val ins = pdfs.map { cr.openInputStream(it)!! }
        try {
            cr.openOutputStream(out, "wt")!!.use { os ->
                PDFMergerUtility().apply { ins.forEach(::addSource); destinationStream = os }.mergeDocuments(mem)
            }
        } finally { ins.forEach { it.close() } }
    }

    /** Writes one PDF per range into a user-picked folder (SAF tree). Returns the new file URIs. */
    fun split(src: Uri, ranges: List<IntRange>, tree: Uri, base: String): List<Uri> = load(src).use { s ->
        val dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        ranges.mapIndexed { i, r ->
            val out = DocumentsContract.createDocument(cr, dir, "application/pdf", "${base}_part${i + 1}_p${r.first + 1}-${r.last + 1}")!!
            PDDocument().use { d -> r.forEach { d.importPage(s.getPage(it)) }; save(d, out) }
            out
        }
    }

    /** pages are 0-based. */
    fun extract(src: Uri, pages: List<Int>, out: Uri) = load(src).use { s ->
        PDDocument().use { d -> pages.forEach { d.importPage(s.getPage(it)) }; save(d, out) }
    }

    /** rotations: 0-based page index -> degrees (multiple of 90). */
    fun rotate(src: Uri, rotations: Map<Int, Int>, out: Uri) = load(src).use { d ->
        rotations.forEach { (i, deg) -> val p = d.getPage(i); p.rotation = (p.rotation + deg) % 360 }
        save(d, out)
    }

    /** "Light" compression: recompress embedded images; text stays selectable. */
    fun compress(src: Uri, out: Uri) = load(src).use { recompressImages(it, 1500, 0.6f); save(it, out) }

    enum class Mode { ORIGINAL, IMAGES, RASTER }
    data class TargetResult(val hit: Boolean, val mode: Mode)

    /**
     * Tries progressively stronger settings until the file fits [target] bytes:
     * first image recompression (text stays selectable), then full-page rasterising.
     * Always writes the smallest result to [out]; hit=false means the target was not reachable.
     */
    fun compressToTarget(src: Uri, out: Uri, target: Long, tmp: File): TargetResult {
        tmp.deleteRecursively(); tmp.mkdirs()
        val orig = File(tmp, "orig.pdf").also { f -> cr.openInputStream(src)!!.use { i -> f.outputStream().use { i.copyTo(it) } } }
        var best = orig; var mode = Mode.ORIGINAL
        fun attempt(m: Mode, name: String, block: (File) -> Unit): Boolean {
            val f = File(tmp, name); block(f)
            if (f.length() < best.length()) { if (best != orig) best.delete(); best = f; mode = m } else f.delete()
            return best.length() <= target
        }
        val hasImages = PDDocument.load(orig, mem).use { d ->
            d.pages.any { p -> p.resources?.let { r -> r.xObjectNames.any { r.getXObject(it) is PDImageXObject } } == true }
        }
        val hit = best.length() <= target ||
            (hasImages && LIGHT.withIndex().any { (n, s) ->
                attempt(Mode.IMAGES, "l$n.pdf") { f -> PDDocument.load(orig, mem).use { d -> recompressImages(d, s.first, s.second); d.save(f) } }
            }) ||
            RASTER.withIndex().any { (n, s) -> attempt(Mode.RASTER, "r$n.pdf") { f -> rasterize(orig, f, s.first, s.second) } }
        cr.openOutputStream(out, "wt")!!.use { o -> best.inputStream().use { it.copyTo(o) } }
        tmp.deleteRecursively()
        return TargetResult(hit, mode)
    }

    private fun recompressImages(d: PDDocument, maxPx: Int, quality: Float) {
        for (page in d.pages) {
            val res = page.resources ?: continue
            for (name in res.xObjectNames) {
                val x = res.getXObject(name) as? PDImageXObject ?: continue
                val bmp = x.image ?: continue
                val sc = minOf(1f, maxPx.toFloat() / maxOf(bmp.width, bmp.height))
                val b = if (sc < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * sc).toInt(), (bmp.height * sc).toInt(), true) else bmp
                res.put(name, JPEGFactory.createFromImage(d, b, quality))
            }
        }
    }

    /** Re-renders every page as a JPEG. Smallest output, but text is no longer selectable. */
    private fun rasterize(src: File, dst: File, dpi: Int, quality: Float) =
        ParcelFileDescriptor.open(src, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { r ->
                PDDocument().use { d ->
                    for (i in 0 until r.pageCount) r.openPage(i).use { p ->
                        val bmp = Bitmap.createBitmap(p.width * dpi / 72, p.height * dpi / 72, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE); p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        val page = PDPage(PDRectangle(p.width.toFloat(), p.height.toFloat())).also(d::addPage)
                        val img = JPEGFactory.createFromImage(d, bmp, quality); bmp.recycle()
                        PDPageContentStream(d, page).use { it.drawImage(img, 0f, 0f, page.mediaBox.width, page.mediaBox.height) }
                    }
                    d.save(dst)
                }
            }
        }

    private companion object {
        /** (max image px, JPEG quality) */
        val LIGHT = listOf(2000 to 0.75f, 1400 to 0.6f, 1000 to 0.5f, 800 to 0.4f, 600 to 0.3f)
        /** (DPI, JPEG quality) */
        val RASTER = listOf(150 to 0.7f, 120 to 0.6f, 100 to 0.5f, 85 to 0.45f, 72 to 0.4f, 60 to 0.3f, 50 to 0.25f)
    }

    private fun decode(uri: Uri, maxPx: Int): Bitmap {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1; while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxPx) s *= 2
        return cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })!! }
    }
}
