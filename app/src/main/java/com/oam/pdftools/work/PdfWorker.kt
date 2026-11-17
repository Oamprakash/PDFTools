package com.oam.pdftools.work

import android.content.Context
import android.net.Uri
import androidx.work.*
import com.oam.pdftools.App
import com.oam.pdftools.data.Recent
import com.oam.pdftools.fileMeta
import com.oam.pdftools.pdf.PdfOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class PdfWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val op = inputData.getString(OP)!!
        val ins = inputData.getStringArray(IN)!!.map(Uri::parse)
        val out = Uri.parse(inputData.getString(OUT))
        val ops = PdfOps(applicationContext.contentResolver)
        fun single(block: () -> Unit) = block().let { listOf(out) }
        runCatching {
            val outs = when (op) {
                IMG -> single { ops.imagesToPdf(ins, out) }
                MERGE -> single { ops.merge(ins, out) }
                COMPRESS -> single { ops.compress(ins[0], out) }
                EXTRACT -> single { ops.extract(ins[0], pages(), out) }
                ROTATE -> single { ops.rotate(ins[0], pages().zip(inputData.getIntArray(DEGS)!!.toList()).toMap(), out) }
                SPLIT -> ops.split(ins[0], ranges(), out, applicationContext.fileMeta(ins[0]).first.substringBeforeLast('.'))
                else -> error("Unknown op $op")
            }
            val dao = (applicationContext as App).db.recents()
            outs.forEach { val (name, size) = applicationContext.fileMeta(it); dao.put(Recent(it.toString(), name, op, size)) }
            outs.size
        }.fold({ Result.success(workDataOf(COUNT to it)) }, { Result.failure(workDataOf("err" to it.message)) })
    }

    private fun pages() = inputData.getIntArray(PAGES)!!.toList()
    private fun ranges() = inputData.getIntArray(RANGES)!!.toList().chunked(2).map { it[0]..it[1] }

    companion object {
        const val OP = "op"; const val IN = "in"; const val OUT = "out"; const val COUNT = "count"
        const val IMG = "Images to PDF"; const val MERGE = "Merge PDF"; const val SPLIT = "Split PDF"; const val COMPRESS = "Compress PDF"
        const val EXTRACT = "Extract pages"; const val ROTATE = "Rotate pages"
        const val PAGES = "pages"; const val DEGS = "degs"; const val RANGES = "ranges"

        fun enqueue(ctx: Context, op: String, ins: List<Uri>, out: Uri, extra: Map<String, Any> = emptyMap()): UUID {
            val req = OneTimeWorkRequestBuilder<PdfWorker>()
                .setInputData(Data.Builder().putAll(extra).putAll(workDataOf(OP to op, IN to ins.map(Uri::toString).toTypedArray(), OUT to out.toString())).build())
                .build() // TODO: setForeground() + notification for 100+ MB jobs
            WorkManager.getInstance(ctx).enqueue(req)
            return req.id
        }
    }
}
