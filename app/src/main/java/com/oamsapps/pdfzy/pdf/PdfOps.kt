package com.oamsapps.pdfzy.pdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
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
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import java.util.zip.Deflater

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

    /** Renders pages (null = all) to JPG/PNG files in a user-picked folder. Returns the new file URIs. */
    fun toImages(src: Uri, pages: List<Int>?, tree: Uri, base: String, dpi: Int, png: Boolean): List<Uri> {
        val dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val mime = if (png) "image/png" else "image/jpeg"
        return cr.openFileDescriptor(src, "r")!!.use { pfd ->
            PdfRenderer(pfd).use { r ->
                (pages ?: (0 until r.pageCount).toList()).map { i ->
                    r.openPage(i).use { p ->
                        var w = p.width * dpi / 72; var h = p.height * dpi / 72
                        val cap = Math.sqrt(25e6 / (w.toDouble() * h)) // max ~25 MP so 300 DPI posters don't OOM
                        if (cap < 1) { w = (w * cap).toInt(); h = (h * cap).toInt() }
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        val out = DocumentsContract.createDocument(cr, dir, mime, "${base}_page${i + 1}")!!
                        cr.openOutputStream(out, "wt")!!.use { bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, it) }
                        bmp.recycle()
                        out
                    }
                }
            }
        }
    }

    enum class Lock { NONE, OWNER_ONLY, PASSWORD }

    fun lockState(src: Uri): Lock = try {
        load(src).use { if (it.isEncrypted) Lock.OWNER_ONLY else Lock.NONE }
    } catch (e: InvalidPasswordException) { Lock.PASSWORD }

    /** AES-256; same password opens the file and unlocks all permissions. */
    fun protect(src: Uri, out: Uri, password: String) = load(src).use { d ->
        d.protect(StandardProtectionPolicy(password, password, AccessPermission()).apply { encryptionKeyLength = 256 })
        save(d, out)
    }

    /** Removes the password (blank works for PDFs that only restrict editing/printing). */
    fun unlock(src: Uri, out: Uri, password: String) {
        val d = try { cr.openInputStream(src)!!.use { PDDocument.load(it, password, mem) } }
                catch (e: InvalidPasswordException) { throw IllegalArgumentException("Wrong password") }
        d.use { it.isAllSecurityToBeRemoved = true; save(it, out) }
    }

    /** Front (+ optional back) of an ID card at real size, centred on one A4 page. */
    fun idCard(front: Uri, back: Uri?, out: Uri) = PDDocument().use { doc ->
        val page = PDPage(PDRectangle.A4).also(doc::addPage)
        val cw = 85.6f * 72 / 25.4f; val ch = 53.98f * 72 / 25.4f // ISO/IEC 7810 ID-1, in points
        val imgs = listOfNotNull(front, back).map { u -> decode(u, 1600).let { b -> JPEGFactory.createFromImage(doc, b, 0.9f).also { b.recycle() } } }
        // Fit inside the card box (portrait cards stay portrait), keeping aspect so uncropped photos aren't stretched
        val sizes = imgs.map { img ->
            val (bw, bh) = if (img.height > img.width) ch to cw else cw to ch
            val s = minOf(bw / img.width, bh / img.height)
            img.width * s to img.height * s
        }
        val gap = 36f; val box = page.mediaBox
        var y = (box.height + sizes.sumOf { it.second.toDouble() }.toFloat() + gap * (imgs.size - 1)) / 2
        PDPageContentStream(doc, page).use { cs ->
            imgs.zip(sizes).forEach { (img, s) -> y -= s.second; cs.drawImage(img, (box.width - s.first) / 2, y, s.first, s.second); y -= gap }
        }
        save(doc, out)
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

    data class Analysis(val pages: Int, val imageBytes: Long, val minLossless: Long) {
        val textOnly get() = imageBytes == 0L
    }

    /**
     * Quick pre-check (no full rewrite): finds image bytes and estimates the smallest
     * lossless size by deflating every 25th uncompressed stream and extrapolating.
     */
    fun analyze(src: Uri, size: Long): Analysis = load(src).use { d ->
        var raw = 0L; var img = 0L; var sIn = 0L; var sOut = 0L; var n = 0
        for (o in d.document.objects) {
            val s = o.`object` as? COSStream ?: continue
            when {
                s.getCOSName(COSName.SUBTYPE) == COSName.IMAGE -> img += s.length
                s.filters == null -> {
                    raw += s.length
                    if (n++ % 25 == 0) { val b = s.createInputStream().use { it.readBytes() }; sIn += b.size; sOut += deflatedSize(b) }
                }
            }
        }
        val ratio = if (sIn > 0) sOut.toDouble() / sIn else 1.0
        Analysis(d.numberOfPages, img, (size - raw + raw * ratio).toLong().coerceAtMost(size))
    }

    private fun deflatedSize(b: ByteArray): Long {
        val def = Deflater(Deflater.DEFAULT_COMPRESSION).apply { setInput(b); finish() }
        val buf = ByteArray(8192); var t = 0L
        while (!def.finished()) t += def.deflate(buf)
        def.end(); return t
    }

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
    fun compressToTarget(src: Uri, out: Uri, target: Long, tmp: File, allowRaster: Boolean = false, onStep: (String) -> Unit = {}): TargetResult {
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

        val hit = (hasImages && search(Mode.IMAGES, LIGHT, forceLast = false)) ||
            (allowRaster && search(Mode.RASTER, RASTER, forceLast = true)) // opt-in: turns text into pixels
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
                if (x.cosObject.containsKey(COSName.SMASK) || x.cosObject.containsKey(COSName.MASK)) continue // has transparency
                val bmp = x.image ?: continue
                if (bmp.hasAlpha()) continue
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
        /** Floor at 100 DPI: below that, normal-size text becomes unreadable. */
        val RASTER = listOf(150 to 0.7f, 130 to 0.6f, 115 to 0.55f, 100 to 0.5f)
    }

    private fun decode(uri: Uri, maxPx: Int): Bitmap {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1; while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxPx) s *= 2
        val bmp = cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })!! }
        // Phone photos are often stored sideways with an EXIF rotation flag
        val deg = runCatching {
            when (cr.openInputStream(uri)!!.use { ExifInterface(it) }.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90; ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270; else -> 0
            }
        }.getOrDefault(0)
        if (deg == 0) return bmp
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg.toFloat()) }, true)
            .also { if (it !== bmp) bmp.recycle() }
    }
}
