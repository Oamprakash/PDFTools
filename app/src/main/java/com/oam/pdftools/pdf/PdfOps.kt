package com.oam.pdftools.pdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.net.Uri
import android.provider.DocumentsContract
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
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
    fun compress(src: Uri, out: Uri) = load(src).use { deflateStreams(it); recompressImages(it, 1500, 0.6f); save(it, out) }

    enum class Mode { ORIGINAL, LOSSLESS, IMAGES, RASTER }
    data class TargetResult(val hit: Boolean, val mode: Mode)

    /**
     * Compress until the file fits [target] bytes, with few full passes:
     * 1. Try settings on 3 sample pages (first/middle/last) and predict the full size.
     * 2. Binary-search the ladder for the mildest setting predicted to fit (8% margin).
     * 3. Run it on the whole file; if it misses, correct the prediction and jump ahead.
     * Image recompression (text stays selectable) is tried before page rasterising.
     * Always writes the smallest result to [out]; hit=false means the target wasn't reachable.
     */
    fun compressToTarget(src: Uri, out: Uri, target: Long, tmp: File, onStep: (String) -> Unit = {}): TargetResult {
        tmp.deleteRecursively(); tmp.mkdirs()
        val orig = File(tmp, "orig.pdf").also { f -> cr.openInputStream(src)!!.use { i -> f.outputStream().use { i.copyTo(it) } } }
        var best = orig; var mode = Mode.ORIGINAL; var n = 0
        fun finish(hit: Boolean): TargetResult {
            cr.openOutputStream(out, "wt")!!.use { o -> best.inputStream().use { it.copyTo(o) } }
            tmp.deleteRecursively()
            return TargetResult(hit, mode)
        }
        if (orig.length() <= target) return finish(true)

        // Step 0: lossless. Many generated PDFs (FPDF, some scanners) store page streams uncompressed.
        onStep("Compressing without quality loss…")
        val lossless = File(tmp, "lossless.pdf")
        val (pages, hasImages) = PDDocument.load(orig, mem).use { d ->
            deflateStreams(d); d.save(lossless)
            d.numberOfPages to d.pages.any { p -> p.resources?.let { r -> r.xObjectNames.any { r.getXObject(it) is PDImageXObject } } == true }
        }
        if (lossless.length() < best.length()) { best = lossless; mode = Mode.LOSSLESS } else lossless.delete()
        if (best.length() <= target) return finish(true)
        val base = best

        onStep("Analysing sample pages…")
        val sample = if (pages <= 4) base else File(tmp, "sample.pdf").also { f ->
            PDDocument.load(base, mem).use { s ->
                PDDocument().use { d -> listOf(0, pages / 2, pages - 1).forEach { d.importPage(s.getPage(it)) }; d.save(f) }
            }
        }
        val goal = (target * 0.92).toLong()

        fun apply(m: Mode, s: Pair<Int, Float>, input: File, output: File) =
            if (m == Mode.IMAGES) PDDocument.load(input, mem).use { d -> recompressImages(d, s.first, s.second); d.save(output) }
            else rasterize(input, output, s.first, s.second)

        fun search(m: Mode, ladder: List<Pair<Int, Float>>, forceLast: Boolean): Boolean {
            val cache = HashMap<Int, Long>()
            fun predict(i: Int) = cache.getOrPut(i) {
                val f = File(tmp, "s.pdf"); apply(m, ladder[i], sample, f)
                (base.length() * f.length().toDouble() / sample.length()).toLong().also { f.delete() }
            }
            val last = ladder.lastIndex
            // Don't run a full pass that's predicted to be no smaller than what we already have (e.g. rasterising a text PDF)
            var i = if (predict(last) > goal) { if (!forceLast || predict(last) >= best.length()) return false; last } else {
                var lo = 0; var hi = last
                while (lo < hi) { val mid = (lo + hi) / 2; if (predict(mid) <= goal) hi = mid else lo = mid + 1 }
                lo
            }
            while (true) {
                n++
                onStep(if (m == Mode.RASTER) "Converting pages to images (pass $n)…" else "Compressing images (pass $n)…")
                val f = File(tmp, "full$n.pdf"); apply(m, ladder[i], base, f)
                val size = f.length()
                if (size < best.length()) { if (best != base) best.delete(); best = f; mode = m } else f.delete()
                if (size <= target) return true
                if (i == last) return false
                val factor = size / predict(i).toDouble() // how far off the sample prediction was
                i = ((i + 1)..last).firstOrNull { predict(it) * factor <= goal } ?: last
            }
        }

        val hit = (hasImages && search(Mode.IMAGES, LIGHT, forceLast = false)) || search(Mode.RASTER, RASTER, forceLast = true)
        return finish(hit)
    }

    /** Lossless: Flate-compress every stream stored without a filter. */
    private fun deflateStreams(d: PDDocument) {
        for (o in d.document.objects) {
            val s = o.`object` as? COSStream ?: continue
            if (s.filters != null || s.getCOSName(COSName.TYPE) == COSName.METADATA) continue
            val bytes = s.createInputStream().use { it.readBytes() }
            s.createOutputStream(COSName.FLATE_DECODE).use { it.write(bytes) }
        }
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
