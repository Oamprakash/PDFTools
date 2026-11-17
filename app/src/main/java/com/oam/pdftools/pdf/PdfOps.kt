package com.oam.pdftools.pdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject

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
    fun compress(src: Uri, out: Uri, maxPx: Int = 1500, quality: Float = 0.6f) = load(src).use { d ->
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
        save(d, out)
    }

    private fun decode(uri: Uri, maxPx: Int): Bitmap {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1; while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxPx) s *= 2
        return cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })!! }
    }
}
