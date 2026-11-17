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
        runCatching {
            when (op) {
                IMG -> ops.imagesToPdf(ins, out)
                MERGE -> ops.merge(ins, out)
                COMPRESS -> ops.compress(ins[0], out)
                EXTRACT -> ops.extract(ins[0], pages(), out)
                ROTATE -> ops.rotate(ins[0], pages().zip(inputData.getIntArray(DEGS)!!.toList()).toMap(), out)
                else -> error("Unknown op $op")
            }
            val (name, size) = applicationContext.fileMeta(out)
            (applicationContext as App).db.recents().put(Recent(out.toString(), name, op, size))
        }.fold({ Result.success() }, { Result.failure(workDataOf("err" to it.message)) })
    }

    private fun pages() = inputData.getIntArray(PAGES)!!.toList()

    companion object {
        const val OP = "op"; const val IN = "in"; const val OUT = "out"
        const val IMG = "Images to PDF"; const val MERGE = "Merge PDF"; const val COMPRESS = "Compress PDF"
        const val EXTRACT = "Extract pages"; const val ROTATE = "Rotate pages"
        const val PAGES = "pages"; const val DEGS = "degs"

        fun enqueue(ctx: Context, op: String, ins: List<Uri>, out: Uri, extra: Map<String, Any> = emptyMap()): UUID {
            val req = OneTimeWorkRequestBuilder<PdfWorker>()
                .setInputData(Data.Builder().putAll(extra).putAll(workDataOf(OP to op, IN to ins.map(Uri::toString).toTypedArray(), OUT to out.toString())).build())
                .build() // TODO: setForeground() + notification for 100+ MB jobs
            WorkManager.getInstance(ctx).enqueue(req)
            return req.id
        }
    }
}
